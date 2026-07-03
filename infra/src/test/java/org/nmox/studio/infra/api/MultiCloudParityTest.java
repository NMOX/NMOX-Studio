package org.nmox.studio.infra.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-cloud parity: Hetzner and Cloudflare nodes get the same drift,
 * destroy, cloud-init and live-gate treatment DigitalOcean nodes have
 * always had. A pure-Hetzner stack deploys live on a Hetzner token
 * alone - the gate asks the clouds the plan actually calls, not one
 * hardcoded provider.
 */
class MultiCloudParityTest {

    // ---- drift / destroy paths ----

    @Test
    @DisplayName("Every Hetzner kind resolves its v1 per-resource path for drift and destroy")
    void hetznerResourcePathsAreReal() {
        assertThat(DigitalOceanClient.resourcePath(NodeKind.HZ_SERVER, "7"))
                .isEqualTo("/servers/7");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.HZ_NETWORK, "8"))
                .isEqualTo("/networks/8");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.HZ_LB, "9"))
                .isEqualTo("/load_balancers/9");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.HZ_VOLUME, "10"))
                .isEqualTo("/volumes/10");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.HZ_FIREWALL, "11"))
                .isEqualTo("/firewalls/11");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.HZ_FLOATING_IP, "12"))
                .isEqualTo("/floating_ips/12");
    }

    @Test
    @DisplayName("A Cloudflare DNS record resolves its path only with zone context")
    void cloudflareRecordPathNeedsItsZone() {
        assertThat(DigitalOceanClient.resourcePath(NodeKind.CF_DNS_RECORD, "rec1",
                Map.of("zoneId", "zone9")))
                .isEqualTo("/zones/zone9/dns_records/rec1");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.CF_DNS_RECORD, "rec1", Map.of()))
                .isNull();
        assertThat(DigitalOceanClient.resourcePath(NodeKind.CF_DNS_RECORD, "rec1",
                Map.of("zoneId", "  ")))
                .isNull();
        // R2 buckets stay honestly unaddressable: their created id is not parsed
        assertThat(DigitalOceanClient.resourcePath(NodeKind.CF_R2_BUCKET, "b",
                Map.of("accountId", "acc"))).isNull();
    }

    @Test
    @DisplayName("A deployed Hetzner node reaches drift and destroy through props-aware paths")
    void hetznerNodePathFromProps() {
        InfraGraph graph = new InfraGraph();
        InfraNode server = graph.addNode(NodeKind.HZ_SERVER, 0, 0);
        server.doId = "4242";
        assertThat(DigitalOceanClient.resourcePath(server.kind, server.doId, server.props))
                .isEqualTo("/servers/4242");
    }

    // ---- cloud-init ----

    @Test
    @DisplayName("Cloud-init user_data reaches the Hetzner server create body — only when set")
    void hetznerUserDataFlowsWhenSet() {
        InfraGraph graph = new InfraGraph();
        InfraNode server = graph.addNode(NodeKind.HZ_SERVER, 0, 0);

        String bare = plannedBody(graph, server);
        assertThat(bare).doesNotContain("user_data");

        server.props.put("userData", "#cloud-config\npackages: [nginx]\n");
        String configured = plannedBody(graph, server);
        assertThat(configured).contains("user_data").contains("cloud-config");
    }

    @Test
    @DisplayName("Cloud-init is a real Hetzner server property in the catalog")
    void hetznerUserDataIsACatalogProp() {
        assertThat(NodeKind.HZ_SERVER.getProps())
                .anyMatch(p -> p.key().equals("userData"));
    }

    // ---- live gate ----

    @Test
    @DisplayName("A pure-Hetzner plan goes live on a Hetzner token alone")
    void pureHetznerStackNeedsNoDigitalOceanKey() {
        InfraGraph graph = new InfraGraph();
        InfraNode network = graph.addNode(NodeKind.HZ_NETWORK, 0, 0);
        InfraNode server = graph.addNode(NodeKind.HZ_SERVER, 100, 0);
        graph.connect(network, server);
        List<DoRequest> plan = DeployPlanner.plan(graph);

        Set<CloudProvider> used = DeployPlanner.providersUsed(plan, graph);

        assertThat(used).containsExactly(CloudProvider.HETZNER);
        assertThat(DeployPlanner.liveEligible(used, p -> p == CloudProvider.HETZNER)).isTrue();
        assertThat(DeployPlanner.liveEligible(used, p -> p == CloudProvider.DIGITALOCEAN)).isFalse();
    }

    @Test
    @DisplayName("A mixed stack needs every touched cloud's token before going live")
    void mixedStackNeedsAllItsTokens() {
        InfraGraph graph = new InfraGraph();
        InfraNode droplet = graph.addNode(NodeKind.DROPLET, 0, 0);
        InfraNode server = graph.addNode(NodeKind.HZ_SERVER, 100, 0);
        InfraNode record = graph.addNode(NodeKind.CF_DNS_RECORD, 200, 0);
        record.props.put("zoneId", "zone1");
        graph.connect(server, record);
        List<DoRequest> plan = DeployPlanner.plan(graph);

        Set<CloudProvider> used = DeployPlanner.providersUsed(plan, graph);

        assertThat(used).containsExactlyInAnyOrder(
                CloudProvider.DIGITALOCEAN, CloudProvider.HETZNER, CloudProvider.CLOUDFLARE);
        assertThat(DeployPlanner.liveEligible(used, p -> true)).isTrue();
        assertThat(DeployPlanner.liveEligible(used, p -> p != CloudProvider.CLOUDFLARE)).isFalse();
    }

    @Test
    @DisplayName("Skipped steps demand no token: a Spaces bucket never gates the deploy")
    void skippedStepsDoNotGate() {
        InfraGraph graph = new InfraGraph();
        graph.addNode(NodeKind.SPACES, 0, 0); // planned as SKIP (S3 protocol)
        InfraNode server = graph.addNode(NodeKind.HZ_SERVER, 100, 0);
        List<DoRequest> plan = DeployPlanner.plan(graph);

        Set<CloudProvider> used = DeployPlanner.providersUsed(plan, graph);

        assertThat(used).containsExactly(CloudProvider.HETZNER);
    }

    // ---- ssh parity ----

    @Test
    @DisplayName("Drift GET responses yield the public IP: Hetzner and DO shapes alike")
    void publicIpExtractionPerProviderShape() {
        JSONObject hetzner = new JSONObject().put("server", new JSONObject()
                .put("public_net", new JSONObject()
                        .put("ipv4", new JSONObject().put("ip", "203.0.113.9"))));
        assertThat(DigitalOceanClient.extractPublicIp(NodeKind.HZ_SERVER, hetzner))
                .isEqualTo("203.0.113.9");

        JSONObject droplet = new JSONObject().put("droplet", new JSONObject()
                .put("networks", new JSONObject().put("v4", new JSONArray()
                        .put(new JSONObject().put("type", "private").put("ip_address", "10.0.0.5"))
                        .put(new JSONObject().put("type", "public").put("ip_address", "198.51.100.4")))));
        assertThat(DigitalOceanClient.extractPublicIp(NodeKind.DROPLET, droplet))
                .isEqualTo("198.51.100.4");

        // kinds without an address, and odd shapes, yield null - never a throw
        assertThat(DigitalOceanClient.extractPublicIp(NodeKind.VPC, hetzner)).isNull();
        assertThat(DigitalOceanClient.extractPublicIp(NodeKind.HZ_SERVER, new JSONObject())).isNull();
    }

    private static String plannedBody(InfraGraph graph, InfraNode node) {
        return DeployPlanner.plan(graph).stream()
                .filter(r -> r.nodeId().equals(node.id))
                .findFirst().orElseThrow().body().toString();
    }
}
