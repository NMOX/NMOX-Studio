package org.nmox.studio.infra.api;

import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The parse seams the sync/deploy machinery leans on, driven with JSON
 * strings and never a socket: pulling a created resource's id out of a
 * creation response per kind, the per-resource REST paths that drift
 * and destroy address, and the grid cursor that lays imported nodes out
 * without stacking. Complements CloudSyncTest (which covers the list
 * responses and provider iteration).
 */
class DigitalOceanParseTest {

    // ---- extractId: one response shape per kind ----

    @Test
    @DisplayName("Droplet-family ids come back as strings from a numeric JSON id")
    void extractsDropletId() {
        JSONObject response = new JSONObject().put("droplet", new JSONObject().put("id", 123456L));
        assertThat(DigitalOceanClient.extractId(NodeKind.DROPLET, response)).isEqualTo("123456");
        assertThat(DigitalOceanClient.extractId(NodeKind.GPU_DROPLET, response)).isEqualTo("123456");
    }

    @Test
    @DisplayName("String-id DO kinds surface their id verbatim")
    void extractsStringIds() {
        assertThat(DigitalOceanClient.extractId(NodeKind.VPC,
                wrap("vpc", "id", "vpc-uuid"))).isEqualTo("vpc-uuid");
        assertThat(DigitalOceanClient.extractId(NodeKind.LOAD_BALANCER,
                wrap("load_balancer", "id", "lb-1"))).isEqualTo("lb-1");
        assertThat(DigitalOceanClient.extractId(NodeKind.FIREWALL,
                wrap("firewall", "id", "fw-1"))).isEqualTo("fw-1");
        assertThat(DigitalOceanClient.extractId(NodeKind.VOLUME,
                wrap("volume", "id", "vol-1"))).isEqualTo("vol-1");
        assertThat(DigitalOceanClient.extractId(NodeKind.CDN,
                wrap("endpoint", "id", "cdn-1"))).isEqualTo("cdn-1");
        assertThat(DigitalOceanClient.extractId(NodeKind.KUBERNETES,
                wrap("kubernetes_cluster", "id", "k8s-1"))).isEqualTo("k8s-1");
        assertThat(DigitalOceanClient.extractId(NodeKind.APP_PLATFORM,
                wrap("app", "id", "app-1"))).isEqualTo("app-1");
        assertThat(DigitalOceanClient.extractId(NodeKind.CERTIFICATE,
                wrap("certificate", "id", "cert-1"))).isEqualTo("cert-1");
    }

    @Test
    @DisplayName("Kinds keyed by name rather than id: reserved IP, domain, registry, functions, alert")
    void extractsNaturalKeyIds() {
        assertThat(DigitalOceanClient.extractId(NodeKind.RESERVED_IP,
                wrap("reserved_ip", "ip", "203.0.113.9"))).isEqualTo("203.0.113.9");
        assertThat(DigitalOceanClient.extractId(NodeKind.DOMAIN,
                wrap("domain", "name", "example.com"))).isEqualTo("example.com");
        assertThat(DigitalOceanClient.extractId(NodeKind.CONTAINER_REGISTRY,
                wrap("registry", "name", "my-registry"))).isEqualTo("my-registry");
        assertThat(DigitalOceanClient.extractId(NodeKind.FUNCTIONS,
                wrap("namespace", "namespace", "fn-ns-1"))).isEqualTo("fn-ns-1");
        assertThat(DigitalOceanClient.extractId(NodeKind.MONITOR_ALERT,
                wrap("policy", "uuid", "alert-uuid"))).isEqualTo("alert-uuid");
    }

    @Test
    @DisplayName("SSH key id is numeric; database id is a string; both round to string")
    void extractsSshAndDatabaseIds() {
        assertThat(DigitalOceanClient.extractId(NodeKind.SSH_KEY,
                new JSONObject().put("ssh_key", new JSONObject().put("id", 987L))))
                .isEqualTo("987");
        assertThat(DigitalOceanClient.extractId(NodeKind.DB_POSTGRES,
                wrap("database", "id", "db-1"))).isEqualTo("db-1");
        assertThat(DigitalOceanClient.extractId(NodeKind.DB_KAFKA,
                wrap("database", "id", "db-2"))).isEqualTo("db-2");
    }

    @Test
    @DisplayName("Gradient AI reports its uuid, or null when the agent block omits one")
    void extractsGradientAiId() {
        assertThat(DigitalOceanClient.extractId(NodeKind.GRADIENT_AI,
                wrap("agent", "uuid", "agent-uuid"))).isEqualTo("agent-uuid");
        assertThat(DigitalOceanClient.extractId(NodeKind.GRADIENT_AI,
                new JSONObject().put("agent", new JSONObject()))).isNull();
    }

    @Test
    @DisplayName("Hetzner creation responses carry a numeric id under the resource key")
    void extractsHetznerIds() {
        assertThat(DigitalOceanClient.extractId(NodeKind.HZ_SERVER,
                new JSONObject().put("server", new JSONObject().put("id", 42L)))).isEqualTo("42");
        assertThat(DigitalOceanClient.extractId(NodeKind.HZ_NETWORK,
                new JSONObject().put("network", new JSONObject().put("id", 7L)))).isEqualTo("7");
        assertThat(DigitalOceanClient.extractId(NodeKind.HZ_LB,
                new JSONObject().put("load_balancer", new JSONObject().put("id", 9L)))).isEqualTo("9");
        assertThat(DigitalOceanClient.extractId(NodeKind.HZ_VOLUME,
                new JSONObject().put("volume", new JSONObject().put("id", 10L)))).isEqualTo("10");
        assertThat(DigitalOceanClient.extractId(NodeKind.HZ_FIREWALL,
                new JSONObject().put("firewall", new JSONObject().put("id", 11L)))).isEqualTo("11");
        assertThat(DigitalOceanClient.extractId(NodeKind.HZ_FLOATING_IP,
                new JSONObject().put("floating_ip", new JSONObject().put("id", 12L)))).isEqualTo("12");
    }

    @Test
    @DisplayName("Cloudflare id lives under result; R2 with no parseable id is null")
    void extractsCloudflareId() {
        assertThat(DigitalOceanClient.extractId(NodeKind.CF_DNS_RECORD,
                wrap("result", "id", "rec-1"))).isEqualTo("rec-1");
        assertThat(DigitalOceanClient.extractId(NodeKind.CF_R2_BUCKET,
                new JSONObject().put("result", new JSONObject()))).isNull();
    }

    @Test
    @DisplayName("A null kind or a response missing the expected block yields null, never a throw")
    void extractIdIsNullSafe() {
        assertThat(DigitalOceanClient.extractId(null, new JSONObject())).isNull();
        assertThat(DigitalOceanClient.extractId(NodeKind.DROPLET, new JSONObject())).isNull();
        assertThat(DigitalOceanClient.extractId(NodeKind.VPC,
                new JSONObject().put("vpc", new JSONObject()))).isNull();
    }

    // ---- resourcePath: the endpoints drift/destroy address ----

    @Test
    @DisplayName("Every DigitalOcean kind resolves its per-resource path")
    void digitalOceanResourcePaths() {
        assertThat(DigitalOceanClient.resourcePath(NodeKind.GPU_DROPLET, "1")).isEqualTo("/v2/droplets/1");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.LOAD_BALANCER, "2")).isEqualTo("/v2/load_balancers/2");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.FIREWALL, "3")).isEqualTo("/v2/firewalls/3");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.VOLUME, "4")).isEqualTo("/v2/volumes/4");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.RESERVED_IP, "5")).isEqualTo("/v2/reserved_ips/5");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.DOMAIN, "example.com"))
                .isEqualTo("/v2/domains/example.com");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.CDN, "6")).isEqualTo("/v2/cdn/endpoints/6");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.KUBERNETES, "7"))
                .isEqualTo("/v2/kubernetes/clusters/7");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.APP_PLATFORM, "8")).isEqualTo("/v2/apps/8");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.FUNCTIONS, "9"))
                .isEqualTo("/v2/functions/namespaces/9");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.CERTIFICATE, "10"))
                .isEqualTo("/v2/certificates/10");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.MONITOR_ALERT, "11"))
                .isEqualTo("/v2/monitoring/alerts/11");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.GRADIENT_AI, "12"))
                .isEqualTo("/v2/gen-ai/agents/12");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.VPC, "13")).isEqualTo("/v2/vpcs/13");
    }

    @Test
    @DisplayName("Container registry addresses the account-wide singleton, ignoring the id")
    void registryPathIsAccountWide() {
        assertThat(DigitalOceanClient.resourcePath(NodeKind.CONTAINER_REGISTRY, "anything"))
                .isEqualTo("/v2/registry");
    }

    @Test
    @DisplayName("R2 buckets have no per-resource read path — honestly null")
    void r2HasNoPath() {
        assertThat(DigitalOceanClient.resourcePath(NodeKind.CF_R2_BUCKET, "b", Map.of())).isNull();
    }

    // ---- Grid layout: imported nodes never overlap ----

    @Test
    @DisplayName("A fresh grid seeds at the default corner; advance walks right then wraps")
    void gridAdvancesAndWraps() {
        DigitalOceanClient.Grid grid = new DigitalOceanClient.Grid(new InfraGraph());
        assertThat(grid.x).isEqualTo(80);
        assertThat(grid.y).isEqualTo(60);

        grid.advance();
        assertThat(grid.x).isEqualTo(270); // 80 + 190
        assertThat(grid.y).isEqualTo(60);

        // walk right until it must wrap (x > 900 resets to 80 and drops a row)
        int guard = 0;
        while (grid.x > 80 && guard++ < 20) {
            grid.advance();
        }
        assertThat(grid.x).isEqualTo(80);
        assertThat(grid.y).isEqualTo(150); // 60 + 90
    }

    @Test
    @DisplayName("newBand drops a fresh row only after a source placed something")
    void newBandOnlyAfterPlacement() {
        DigitalOceanClient.Grid grid = new DigitalOceanClient.Grid(new InfraGraph());

        // nothing placed yet: newBand is a no-op
        grid.newBand();
        assertThat(grid.x).isEqualTo(80);
        assertThat(grid.y).isEqualTo(60);

        grid.advance();     // placedInBand = true, x -> 270
        grid.newBand();     // resets x, drops a row
        assertThat(grid.x).isEqualTo(80);
        assertThat(grid.y).isEqualTo(150);

        // and the band flag clears, so a second newBand is again a no-op
        grid.newBand();
        assertThat(grid.y).isEqualTo(150);
    }

    @Test
    @DisplayName("A grid seeds below the lowest existing node so imports never land on the design")
    void gridSeedsBelowExistingDesign() {
        InfraGraph graph = new InfraGraph();
        InfraNode low = graph.addNode(NodeKind.DROPLET, 0, 400);

        DigitalOceanClient.Grid grid = new DigitalOceanClient.Grid(graph);

        assertThat(grid.y).isEqualTo(low.y + 90);
    }

    // ---- parseZoneIds / parseDnsRecords edge cases ----

    @Test
    @DisplayName("Zone parsing skips blank ids and tolerates a missing result array")
    void zoneParsingSkipsBlanksAndAbsence() {
        String json = new JSONObject().put("result", new JSONArray()
                .put(new JSONObject().put("id", "zone1"))
                .put(new JSONObject().put("id", ""))
                .put(new JSONObject())).toString();
        assertThat(DigitalOceanClient.parseZoneIds(json)).containsExactly("zone1");
        assertThat(DigitalOceanClient.parseZoneIds("{}")).isEmpty();
    }

    @Test
    @DisplayName("DNS record parsing skips records with a blank id")
    void dnsParsingSkipsBlankIds() {
        String json = new JSONObject().put("result", new JSONArray()
                .put(new JSONObject().put("id", "rec1").put("type", "A").put("name", "a.example.com"))
                .put(new JSONObject().put("type", "A").put("name", "no-id.example.com"))).toString();

        List<DigitalOceanClient.Imported> records =
                DigitalOceanClient.parseDnsRecords("z1", json);

        assertThat(records).singleElement()
                .satisfies(r -> assertThat(r.doId()).isEqualTo("rec1"));
    }

    @Test
    @DisplayName("A short DNS label is left intact, right at the 46-char boundary")
    void shortLabelsAreNotTruncated() {
        assertThat(DigitalOceanClient.truncateLabel("A example.com → 1.2.3.4"))
                .isEqualTo("A example.com → 1.2.3.4");
        String exactly46 = "x".repeat(46);
        assertThat(DigitalOceanClient.truncateLabel(exactly46)).isEqualTo(exactly46);
    }

    private static JSONObject wrap(String block, String key, String value) {
        return new JSONObject().put(block, new JSONObject().put(key, value));
    }
}
