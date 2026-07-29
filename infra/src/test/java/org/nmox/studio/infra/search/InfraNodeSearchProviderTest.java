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

    // ---- the evaluate seam: what Quick Search actually receives ----

    @Test
    @DisplayName("Evaluate lists matching nodes, marking deployed ones (live)")
    void evaluateListsMatches() {
        InfraGraph graph = new InfraGraph();
        InfraNode designed = graph.addNode(NodeKind.DROPLET, 0, 0);
        designed.label = "api-server";
        InfraNode deployed = graph.addNode(NodeKind.DROPLET, 100, 0);
        deployed.label = "web-server";
        deployed.doId = "123";
        graph.addNode(NodeKind.VPC, 200, 0).label = "backbone";

        var displays = new java.util.ArrayList<String>();
        new InfraNodeSearchProvider().evaluate("server", graph, (action, display) -> {
            displays.add(display);
            return true;
        });

        assertThat(displays).hasSize(2);
        assertThat(displays.get(0)).contains("api-server").doesNotContain("(live)");
        assertThat(displays.get(1)).contains("web-server").contains("(live)");
    }

    @Test
    @DisplayName("Evaluate stops as soon as the response refuses more results")
    void evaluateHonorsStop() {
        InfraGraph graph = new InfraGraph();
        graph.addNode(NodeKind.DROPLET, 0, 0).label = "web-1";
        graph.addNode(NodeKind.DROPLET, 100, 0).label = "web-2";

        var displays = new java.util.ArrayList<String>();
        new InfraNodeSearchProvider().evaluate("web", graph, (action, display) -> {
            displays.add(display);
            return false; // the platform saying "list is full"
        });

        assertThat(displays).hasSize(1);
    }

    @Test
    @DisplayName("A blank or null query yields nothing, not everything")
    void evaluateBlankIsQuiet() {
        InfraGraph graph = new InfraGraph();
        graph.addNode(NodeKind.DROPLET, 0, 0).label = "web-1";

        var displays = new java.util.ArrayList<String>();
        InfraNodeSearchProvider provider = new InfraNodeSearchProvider();
        provider.evaluate("   ", graph, (a, d) -> displays.add(d));
        provider.evaluate(null, graph, (a, d) -> displays.add(d));

        assertThat(displays).isEmpty();
    }
}
