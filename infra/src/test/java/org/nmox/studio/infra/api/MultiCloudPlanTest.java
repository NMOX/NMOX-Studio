package org.nmox.studio.infra.api;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;

class MultiCloudPlanTest {

    @Test
    @DisplayName("Every kind maps to exactly one provider, and categories route correctly")
    void providersCoverTheCatalog() {
        for (NodeKind kind : NodeKind.values()) {
            assertThat(kind.provider()).as(kind.name()).isNotNull();
        }
        assertThat(NodeKind.HZ_SERVER.provider()).isEqualTo(CloudProvider.HETZNER);
        assertThat(NodeKind.CF_DNS_RECORD.provider()).isEqualTo(CloudProvider.CLOUDFLARE);
        assertThat(NodeKind.DROPLET.provider()).isEqualTo(CloudProvider.DIGITALOCEAN);
    }

    @Test
    @DisplayName("Hetzner stack plans in dependency order with attachment steps last")
    void hetznerPlanOrdering() {
        InfraGraph graph = new InfraGraph();
        InfraNode network = graph.addNode(NodeKind.HZ_NETWORK, 0, 0);
        InfraNode server = graph.addNode(NodeKind.HZ_SERVER, 100, 0);
        InfraNode volume = graph.addNode(NodeKind.HZ_VOLUME, 200, 0);
        InfraNode lb = graph.addNode(NodeKind.HZ_LB, 300, 0);
        assertThat(graph.connect(network, server)).isTrue();
        assertThat(graph.connect(volume, server)).isTrue();   // attachment
        assertThat(graph.connect(server, lb)).isTrue();

        List<DoRequest> plan = DeployPlanner.plan(graph);
        List<String> paths = plan.stream().map(DoRequest::path).toList();

        int networkAt = indexOfPrefix(paths, "/networks");
        int serverAt = indexOfPrefix(paths, "/servers");
        int lbAt = indexOfPrefix(paths, "/load_balancers");
        int attachAt = indexOfContaining(paths, "/actions/attach");

        assertThat(networkAt).isLessThan(serverAt);
        assertThat(serverAt).isLessThan(lbAt);
        assertThat(attachAt).isGreaterThan(serverAt);
        // server create embeds the network reference
        DoRequest serverReq = plan.get(serverAt);
        assertThat(serverReq.body().toString()).contains("${id-of:" + network.id + "}");
    }

    @Test
    @DisplayName("A Cloudflare record can front a Hetzner server across providers")
    void crossProviderDns() {
        InfraGraph graph = new InfraGraph();
        InfraNode server = graph.addNode(NodeKind.HZ_SERVER, 0, 0);
        InfraNode record = graph.addNode(NodeKind.CF_DNS_RECORD, 100, 0);
        record.props.put("zoneId", "zone123");
        assertThat(graph.connect(server, record)).isTrue();

        List<DoRequest> plan = DeployPlanner.plan(graph);
        DoRequest recordReq = plan.stream()
                .filter(r -> r.path().contains("/dns_records")).findFirst().orElseThrow();

        assertThat(recordReq.path()).isEqualTo("/zones/zone123/dns_records");
        assertThat(recordReq.body().getString("content")).isEqualTo("${ip-of:" + server.id + "}");
    }

    @Test
    @DisplayName("Cross-provider wires only where it makes sense")
    void wireRulesAcrossClouds() {
        InfraGraph graph = new InfraGraph();
        InfraNode droplet = graph.addNode(NodeKind.DROPLET, 0, 0);
        InfraNode hzServer = graph.addNode(NodeKind.HZ_SERVER, 0, 0);
        InfraNode cfRecord = graph.addNode(NodeKind.CF_DNS_RECORD, 0, 0);
        InfraNode vpc = graph.addNode(NodeKind.VPC, 0, 0);

        assertThat(graph.canConnect(droplet, cfRecord)).isTrue();
        assertThat(graph.canConnect(hzServer, cfRecord)).isTrue();
        assertThat(graph.canConnect(vpc, hzServer)).isFalse();      // DO VPC can't place a Hetzner server
        assertThat(graph.canConnect(hzServer, droplet)).isFalse();
    }

    private static int indexOfPrefix(List<String> paths, String prefix) {
        for (int i = 0; i < paths.size(); i++) {
            if (paths.get(i).startsWith(prefix) && !paths.get(i).contains("/actions")) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfContaining(List<String> paths, String fragment) {
        for (int i = 0; i < paths.size(); i++) {
            if (paths.get(i).contains(fragment)) {
                return i;
            }
        }
        return -1;
    }
}
