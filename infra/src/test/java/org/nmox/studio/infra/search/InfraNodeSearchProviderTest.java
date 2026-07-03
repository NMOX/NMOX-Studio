package org.nmox.studio.infra.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quick Search over infra nodes (ledger 0a, infra half): the matcher
 * finds a node by its label, its kind's display name, or the enum name
 * - all case-insensitive - so "droplet", "Droplet" and a renamed
 * "web-1" all reach the same node.
 */
class InfraNodeSearchProviderTest {

    @Test
    @DisplayName("Matches on the node's label, case-insensitively")
    void matchesLabel() {
        InfraNode node = node(NodeKind.DROPLET, "web-frontend");
        assertThat(InfraNodeSearchProvider.matches(node, "web")).isTrue();
        assertThat(InfraNodeSearchProvider.matches(node, "FRONT")).isTrue();
        assertThat(InfraNodeSearchProvider.matches(node, "database")).isFalse();
    }

    @Test
    @DisplayName("Matches on the kind's display name, so 'load balancer' finds the LB")
    void matchesDisplayName() {
        InfraNode node = node(NodeKind.LOAD_BALANCER, "lb-1");
        assertThat(InfraNodeSearchProvider.matches(node, "load balancer")).isTrue();
        assertThat(InfraNodeSearchProvider.matches(node, "balancer")).isTrue();
    }

    @Test
    @DisplayName("Matches on the enum name, so 'hz_server' and 'hetzner-ish' kinds are findable")
    void matchesEnumName() {
        InfraNode node = node(NodeKind.HZ_SERVER, "app");
        assertThat(InfraNodeSearchProvider.matches(node, "hz_server")).isTrue();
        assertThat(InfraNodeSearchProvider.matches(node, "hz")).isTrue();
    }

    @Test
    @DisplayName("A CF DNS record is findable by 'cf', 'dns', or its record name")
    void matchesCloudflareRecord() {
        InfraNode node = node(NodeKind.CF_DNS_RECORD, "app.example.com");
        assertThat(InfraNodeSearchProvider.matches(node, "cf")).isTrue();
        assertThat(InfraNodeSearchProvider.matches(node, "dns")).isTrue();
        assertThat(InfraNodeSearchProvider.matches(node, "example.com")).isTrue();
        assertThat(InfraNodeSearchProvider.matches(node, "nonexistent")).isFalse();
    }

    @Test
    @DisplayName("Searching a graph's nodes returns only the matching ones")
    void filtersAcrossAGraph() {
        InfraGraph graph = new InfraGraph();
        InfraNode droplet = graph.addNode(NodeKind.DROPLET, 0, 0);
        droplet.label = "api-server";
        InfraNode server = graph.addNode(NodeKind.HZ_SERVER, 100, 0);
        server.label = "worker";
        graph.addNode(NodeKind.VPC, 200, 0);

        long serverMatches = graph.getNodes().stream()
                .filter(n -> InfraNodeSearchProvider.matches(n, "server"))
                .count();
        // "api-server" (label) + "HZ Server" (display name) both hit
        assertThat(serverMatches).isEqualTo(2);

        long vpcMatches = graph.getNodes().stream()
                .filter(n -> InfraNodeSearchProvider.matches(n, "vpc"))
                .count();
        assertThat(vpcMatches).isEqualTo(1);
    }

    private static InfraNode node(NodeKind kind, String label) {
        InfraNode node = new InfraGraph().addNode(kind, 0, 0);
        node.label = label;
        return node;
    }
}
