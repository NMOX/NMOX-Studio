package org.nmox.studio.infra.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "Sync from cloud" is true for all three clouds: Hetzner and
 * Cloudflare list responses parse into importable nodes exactly like
 * DigitalOcean's always have, known ids refresh in place instead of
 * duplicating, a synced CF record carries the zoneId that makes drift
 * and destroy addressable, and one provider's failure never aborts
 * the others' sweep. All parsing seams take JSON strings - no network.
 */
class CloudSyncTest {

    // ---- Hetzner parsing ----

    @Test
    @DisplayName("Hetzner sync covers the six list endpoints, each mapped to its HZ_* kind")
    void hetznerSyncCoversItsEndpoints() {
        assertThat(DigitalOceanClient.hetznerSources())
                .extracting(DigitalOceanClient.Source::path, DigitalOceanClient.Source::kind)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("/servers", NodeKind.HZ_SERVER),
                        org.assertj.core.groups.Tuple.tuple("/networks", NodeKind.HZ_NETWORK),
                        org.assertj.core.groups.Tuple.tuple("/load_balancers", NodeKind.HZ_LB),
                        org.assertj.core.groups.Tuple.tuple("/volumes", NodeKind.HZ_VOLUME),
                        org.assertj.core.groups.Tuple.tuple("/firewalls", NodeKind.HZ_FIREWALL),
                        org.assertj.core.groups.Tuple.tuple("/floating_ips", NodeKind.HZ_FLOATING_IP));
    }

    @Test
    @DisplayName("Every Hetzner list shape parses: the array rides under its wrap key")
    void hetznerListShapesParse() {
        for (DigitalOceanClient.Source source : DigitalOceanClient.hetznerSources()) {
            String json = "{\"" + source.listKey() + "\":[{\"id\":7,\"name\":\"alpha\"}]}";

            List<DigitalOceanClient.Imported> found =
                    DigitalOceanClient.parseListResponse(source, json);

            assertThat(found).as(source.listKey()).hasSize(1);
            assertThat(found.get(0).kind()).isEqualTo(source.kind());
            assertThat(found.get(0).doId()).isEqualTo("7");
            assertThat(found.get(0).label()).isEqualTo("alpha");
        }
    }

    @Test
    @DisplayName("A Hetzner server brings its public IPv4 along (public_net.ipv4.ip)")
    void hetznerServerCarriesItsPublicIp() {
        String json = """
                {"servers":[
                  {"id":42,"name":"web-1","public_net":{"ipv4":{"ip":"203.0.113.7"}}},
                  {"id":43,"name":"ip-less"}]}""";

        List<DigitalOceanClient.Imported> found =
                DigitalOceanClient.parseListResponse(hz("servers"), json);

        assertThat(found).hasSize(2);
        assertThat(found.get(0).ip()).isEqualTo("203.0.113.7");
        assertThat(found.get(1).ip()).isNull(); // no IP is fine, never a throw
    }

    // ---- DigitalOcean parity (the seam did not change DO behavior) ----

    @Test
    @DisplayName("DigitalOcean parsing keeps its quirks: droplet public IP, app names from the spec")
    void digitalOceanParsingUnchanged() {
        String droplets = """
                {"droplets":[{"id":99,"name":"web","networks":{"v4":[
                  {"type":"private","ip_address":"10.0.0.5"},
                  {"type":"public","ip_address":"198.51.100.4"}]}}]}""";
        DigitalOceanClient.Imported droplet =
                DigitalOceanClient.parseListResponse(dob("droplets"), droplets).get(0);
        assertThat(droplet.doId()).isEqualTo("99");
        assertThat(droplet.label()).isEqualTo("web");
        assertThat(droplet.ip()).isEqualTo("198.51.100.4");

        String apps = """
                {"apps":[{"id":"a1","spec":{"name":"storefront"}}]}""";
        DigitalOceanClient.Imported app =
                DigitalOceanClient.parseListResponse(dob("apps"), apps).get(0);
        assertThat(app.label()).isEqualTo("storefront");
    }

    // ---- Cloudflare parsing ----

    @Test
    @DisplayName("Cloudflare zones and DNS records parse; every record carries its zoneId prop")
    void cloudflareRecordsCarryTheirZone() {
        String zones = """
                {"success":true,"result":[{"id":"zone9","name":"example.com"}]}""";
        assertThat(DigitalOceanClient.parseZoneIds(zones)).containsExactly("zone9");

        String records = """
                {"success":true,"result":[
                  {"id":"rec1","type":"A","name":"app.example.com",
                   "content":"203.0.113.7","proxied":true}]}""";
        List<DigitalOceanClient.Imported> found =
                DigitalOceanClient.parseDnsRecords("zone9", records);

        assertThat(found).hasSize(1);
        DigitalOceanClient.Imported record = found.get(0);
        assertThat(record.kind()).isEqualTo(NodeKind.CF_DNS_RECORD);
        assertThat(record.doId()).isEqualTo("rec1");
        assertThat(record.props())
                .containsEntry("zoneId", "zone9")
                .containsEntry("recordType", "A")
                .containsEntry("proxied", "true");
        assertThat(record.label()).isEqualTo("A app.example.com → 203.0.113.7");
    }

    @Test
    @DisplayName("Verbose DNS content truncates to a readable node label")
    void longDnsLabelsTruncate() {
        String records = """
                {"result":[{"id":"rec2","type":"TXT","name":"example.com",
                  "content":"v=spf1 include:_spf.example.com include:mailgun.org ~all"}]}""";

        String label = DigitalOceanClient.parseDnsRecords("z", records).get(0).label();

        assertThat(label).hasSize(46).endsWith("…").startsWith("TXT example.com");
    }

    @Test
    @DisplayName("A synced CF record is addressable: its zoneId resolves the drift/destroy path")
    void syncedRecordIsAddressable() {
        InfraGraph graph = new InfraGraph();
        String records = """
                {"result":[{"id":"rec1","type":"A","name":"app.example.com","content":"203.0.113.7"}]}""";
        DigitalOceanClient.Imported record =
                DigitalOceanClient.parseDnsRecords("zone9", records).get(0);

        DigitalOceanClient.place(graph, new DigitalOceanClient.Grid(graph), record);

        InfraNode node = graph.getNodes().get(0);
        assertThat(node.doId).isEqualTo("rec1");
        assertThat(node.status).isEqualTo("live");
        assertThat(DigitalOceanClient.resourcePath(node.kind, node.doId, node.props))
                .isEqualTo("/zones/zone9/dns_records/rec1");
    }

    // ---- dedupe by id ----

    @Test
    @DisplayName("A resource already on the canvas refreshes in place - never duplicates")
    void knownIdsRefreshInsteadOfDuplicating() {
        InfraGraph graph = new InfraGraph();
        InfraNode designed = graph.addNode(NodeKind.HZ_SERVER, 300, 200);
        designed.doId = "42";
        designed.status = "check failed: timeout";
        DigitalOceanClient.Imported remote = new DigitalOceanClient.Imported(
                NodeKind.HZ_SERVER, "42", "web-1", "203.0.113.7");

        boolean placedNew = DigitalOceanClient.place(
                graph, new DigitalOceanClient.Grid(graph), remote);

        assertThat(placedNew).isFalse();
        assertThat(graph.getNodes()).hasSize(1);
        assertThat(designed.ip).isEqualTo("203.0.113.7"); // refreshed, not duplicated
        assertThat(designed.status).isEqualTo("live");
    }

    @Test
    @DisplayName("Refresh fills a blank prop (a lost zoneId) but never overwrites a set value")
    void refreshFillsBlankPropsOnly() {
        InfraGraph graph = new InfraGraph();
        InfraNode node = graph.addNode(NodeKind.CF_DNS_RECORD, 0, 0);
        node.doId = "rec1";
        node.props.put("zoneId", ""); // catalog default: blank
        node.props.put("name", "app.example.com"); // deliberately set
        DigitalOceanClient.Imported remote = new DigitalOceanClient.Imported(
                NodeKind.CF_DNS_RECORD, "rec1", "A app → 1.2.3.4", null,
                Map.of("zoneId", "zone9", "name", "other.example.com"));

        DigitalOceanClient.place(graph, new DigitalOceanClient.Grid(graph), remote);

        assertThat(node.props)
                .containsEntry("zoneId", "zone9")
                .containsEntry("name", "app.example.com");
    }

    // ---- placement ----

    @Test
    @DisplayName("Synced nodes land on a grid below the existing design - never stacked")
    void placementNeverStacks() {
        InfraGraph graph = new InfraGraph();
        InfraNode existing = graph.addNode(NodeKind.DROPLET, 80, 60);
        DigitalOceanClient.Grid grid = new DigitalOceanClient.Grid(graph);

        for (int i = 0; i < 6; i++) {
            DigitalOceanClient.place(graph, grid, new DigitalOceanClient.Imported(
                    NodeKind.HZ_SERVER, "id-" + i, "s" + i, null));
        }

        List<String> positions = graph.getNodes().stream()
                .map(n -> n.x + "," + n.y).toList();
        assertThat(positions).doesNotHaveDuplicates();
        graph.getNodes().stream().filter(n -> n != existing)
                .forEach(n -> assertThat(n.y).isGreaterThan(existing.y));
    }

    // ---- per-provider isolation ----

    @Test
    @DisplayName("One provider's failure never aborts the sweep - the others still report counts")
    void sweepIsolatesProviderFailures() {
        var outcomes = DigitalOceanClient.sweep(
                List.of(CloudProvider.DIGITALOCEAN, CloudProvider.HETZNER, CloudProvider.CLOUDFLARE),
                new InfraGraph(), null, (provider, graph) -> {
                    if (provider == CloudProvider.HETZNER) {
                        throw new IOException("HTTP 401: bad token");
                    }
                    return provider == CloudProvider.DIGITALOCEAN ? 3 : 5;
                });

        assertThat(outcomes.keySet()).containsExactly(
                CloudProvider.DIGITALOCEAN, CloudProvider.HETZNER, CloudProvider.CLOUDFLARE);
        assertThat(outcomes.get(CloudProvider.DIGITALOCEAN).imported()).isEqualTo(3);
        assertThat(outcomes.get(CloudProvider.DIGITALOCEAN).failed()).isFalse();
        assertThat(outcomes.get(CloudProvider.HETZNER).failed()).isTrue();
        assertThat(outcomes.get(CloudProvider.HETZNER).error()).contains("401");
        assertThat(outcomes.get(CloudProvider.CLOUDFLARE).imported()).isEqualTo(5);
    }

    @Test
    @DisplayName("A malformed provider response fails that provider alone")
    void malformedResponseFailsOneProviderOnly() {
        // the parse seam throws on garbage...
        assertThatThrownBy(() -> DigitalOceanClient.parseListResponse(hz("servers"), "boom"))
                .isInstanceOf(RuntimeException.class);

        // ...and the sweep contains that throw to its own provider
        var outcomes = DigitalOceanClient.sweep(
                List.of(CloudProvider.HETZNER, CloudProvider.CLOUDFLARE),
                new InfraGraph(), null, (provider, graph) -> {
                    if (provider == CloudProvider.HETZNER) {
                        return DigitalOceanClient.parseListResponse(hz("servers"), "boom").size();
                    }
                    return 2;
                });

        assertThat(outcomes.get(CloudProvider.HETZNER).failed()).isTrue();
        assertThat(outcomes.get(CloudProvider.CLOUDFLARE).imported()).isEqualTo(2);
    }

    @Test
    @DisplayName("The sweep targets exactly the tokened providers, in stable order")
    void sweepTargetsTokenedProviders() {
        assertThat(DigitalOceanClient.providersToSync(p -> p != CloudProvider.DIGITALOCEAN))
                .containsExactly(CloudProvider.HETZNER, CloudProvider.CLOUDFLARE);
        assertThat(DigitalOceanClient.providersToSync(p -> false)).isEmpty();
    }

    // ---- helpers ----

    private static DigitalOceanClient.Source hz(String listKey) {
        return DigitalOceanClient.hetznerSources().stream()
                .filter(s -> s.listKey().equals(listKey)).findFirst().orElseThrow();
    }

    private static DigitalOceanClient.Source dob(String listKey) {
        return DigitalOceanClient.digitalOceanSources().stream()
                .filter(s -> s.listKey().equals(listKey)).findFirst().orElseThrow();
    }
}
