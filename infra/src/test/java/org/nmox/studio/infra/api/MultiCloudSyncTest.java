package org.nmox.studio.infra.api;

import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-provider sync (ledger 0b): the same "Sync from cloud" button
 * that imports DigitalOcean resources now pulls existing Hetzner
 * servers/networks/etc. and best-effort Cloudflare DNS records. These
 * exercise the parsing seam and the provider-iteration decision without
 * a live API.
 */
class MultiCloudSyncTest {

    // ---- Hetzner list-response parsing ----

    @Test
    @DisplayName("Hetzner /servers list maps to HZ_SERVER nodes with id, name and public IPv4")
    void hetznerServersParseToNodes() {
        JSONObject response = new JSONObject().put("servers", new JSONArray()
                .put(new JSONObject()
                        .put("id", 4242L)
                        .put("name", "web-1")
                        .put("public_net", new JSONObject()
                                .put("ipv4", new JSONObject().put("ip", "203.0.113.9"))))
                .put(new JSONObject()
                        .put("id", 4243L)
                        .put("name", "web-2")
                        .put("public_net", new JSONObject()
                                .put("ipv4", new JSONObject().put("ip", "203.0.113.10")))));

        DigitalOceanClient.Source source = hetznerSource(NodeKind.HZ_SERVER, "servers");
        List<DigitalOceanClient.Imported> imported =
                DigitalOceanClient.parseListResponse(source, response.toString());

        assertThat(imported).hasSize(2);
        assertThat(imported.get(0).kind()).isEqualTo(NodeKind.HZ_SERVER);
        assertThat(imported.get(0).doId()).isEqualTo("4242");
        assertThat(imported.get(0).label()).isEqualTo("web-1");
        assertThat(imported.get(0).ip()).isEqualTo("203.0.113.9");
        assertThat(imported.get(1).doId()).isEqualTo("4243");
        assertThat(imported.get(1).ip()).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("Hetzner networks/volumes/firewalls carry no IP but do carry id + name")
    void hetznerNonComputeParsesWithoutIp() {
        JSONObject response = new JSONObject().put("networks", new JSONArray()
                .put(new JSONObject().put("id", 7L).put("name", "backbone")));

        List<DigitalOceanClient.Imported> imported = DigitalOceanClient.parseListResponse(
                hetznerSource(NodeKind.HZ_NETWORK, "networks"), response.toString());

        assertThat(imported).singleElement()
                .satisfies(rec -> {
                    assertThat(rec.doId()).isEqualTo("7");
                    assertThat(rec.label()).isEqualTo("backbone");
                    assertThat(rec.ip()).isNull();
                });
    }

    @Test
    @DisplayName("A missing or empty list key yields no records, never a throw")
    void absentListKeyIsEmpty() {
        assertThat(DigitalOceanClient.parseListResponse(
                hetznerSource(NodeKind.HZ_SERVER, "servers"), "{}")).isEmpty();
        assertThat(DigitalOceanClient.parseListResponse(
                hetznerSource(NodeKind.HZ_SERVER, "servers"),
                new JSONObject().put("servers", new JSONArray()).toString())).isEmpty();
    }

    @Test
    @DisplayName("DigitalOcean droplet list still parses its nested public IPv4")
    void digitalOceanDropletsParsePublicIp() {
        JSONObject response = new JSONObject().put("droplets", new JSONArray()
                .put(new JSONObject()
                        .put("id", 99L)
                        .put("name", "api")
                        .put("networks", new JSONObject().put("v4", new JSONArray()
                                .put(new JSONObject().put("type", "private").put("ip_address", "10.0.0.2"))
                                .put(new JSONObject().put("type", "public").put("ip_address", "198.51.100.7"))))));

        List<DigitalOceanClient.Imported> imported = DigitalOceanClient.parseListResponse(
                new DigitalOceanClient.Source("/v2/droplets", "droplets", NodeKind.DROPLET, "id", "name"),
                response.toString());

        assertThat(imported).singleElement()
                .satisfies(rec -> {
                    assertThat(rec.doId()).isEqualTo("99");
                    assertThat(rec.ip()).isEqualTo("198.51.100.7");
                });
    }

    @Test
    @DisplayName("A Cloudflare zone's dns_records parse to CF_DNS_RECORD records")
    void cloudflareRecordsParse() {
        JSONObject response = new JSONObject().put("result", new JSONArray()
                .put(new JSONObject().put("id", "rec-1").put("name", "app.example.com"))
                .put(new JSONObject().put("id", "rec-2").put("name", "api.example.com")));

        List<DigitalOceanClient.Imported> imported = DigitalOceanClient.parseListResponse(
                new DigitalOceanClient.Source("", "result", NodeKind.CF_DNS_RECORD, "id", "name"),
                response.toString());

        assertThat(imported).extracting(DigitalOceanClient.Imported::doId)
                .containsExactly("rec-1", "rec-2");
        assertThat(imported).extracting(DigitalOceanClient.Imported::kind)
                .containsOnly(NodeKind.CF_DNS_RECORD);
    }

    // ---- catalog coverage ----

    @Test
    @DisplayName("Every Hetzner list source maps to a Hetzner-provider kind")
    void hetznerSourcesAreAllHetzner() {
        assertThat(DigitalOceanClient.hetznerSources())
                .isNotEmpty()
                .allSatisfy(s -> assertThat(s.kind().provider()).isEqualTo(CloudProvider.HETZNER));
        // the five networking/compute Hetzner kinds are all covered
        assertThat(DigitalOceanClient.hetznerSources())
                .extracting(DigitalOceanClient.Source::kind)
                .contains(NodeKind.HZ_SERVER, NodeKind.HZ_NETWORK, NodeKind.HZ_LB,
                        NodeKind.HZ_VOLUME, NodeKind.HZ_FIREWALL, NodeKind.HZ_FLOATING_IP);
    }

    // ---- provider iteration ----

    @Test
    @DisplayName("Sync iterates exactly the providers that have a token, in enum order")
    void providersToSyncFollowsTokens() {
        assertThat(DigitalOceanClient.providersToSync(p -> true))
                .containsExactly(CloudProvider.DIGITALOCEAN, CloudProvider.HETZNER, CloudProvider.CLOUDFLARE);

        assertThat(DigitalOceanClient.providersToSync(p -> p == CloudProvider.HETZNER))
                .containsExactly(CloudProvider.HETZNER);

        assertThat(DigitalOceanClient.providersToSync(
                p -> p == CloudProvider.DIGITALOCEAN || p == CloudProvider.CLOUDFLARE))
                .containsExactly(CloudProvider.DIGITALOCEAN, CloudProvider.CLOUDFLARE);
    }

    @Test
    @DisplayName("No token means no provider synced - the button stays a no-op")
    void noTokenNoSync() {
        Set<CloudProvider> none = DigitalOceanClient.providersToSync(p -> false);
        assertThat(none).isEmpty();
    }

    private static DigitalOceanClient.Source hetznerSource(NodeKind kind, String listKey) {
        return new DigitalOceanClient.Source("/" + listKey, listKey, kind, "id", "name");
    }
}
