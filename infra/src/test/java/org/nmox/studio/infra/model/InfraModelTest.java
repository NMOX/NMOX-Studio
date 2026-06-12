package org.nmox.studio.infra.model;

import java.util.HashSet;
import java.util.Set;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class InfraModelTest {

    @ParameterizedTest
    @EnumSource(NodeKind.class)
    @DisplayName("Catalog contract: display name, category, unique prop keys, sane cost")
    void catalogContract(NodeKind kind) {
        assertThat(kind.getDisplayName()).isNotBlank();
        assertThat(kind.getCategory()).isNotNull();
        Set<String> keys = new HashSet<>();
        for (NodeKind.Prop prop : kind.getProps()) {
            assertThat(keys.add(prop.key())).as(kind + " duplicate prop " + prop.key()).isTrue();
            assertThat(prop.defaultValue()).as(kind + "." + prop.key() + " default").isNotNull();
            if ("choice".equals(prop.type())) {
                assertThat(prop.choices()).as(kind + "." + prop.key() + " choices")
                        .contains(prop.defaultValue());
            }
        }
        assertThat(kind.estimateMonthlyUsd(java.util.Map.of()))
                .as(kind + " cost").isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("Wire rules form a DAG - no relationship cycles are possible")
    void wireRulesAreAcyclic() {
        // DFS over kind->kind creation edges
        for (NodeKind start : NodeKind.values()) {
            assertThat(reachable(start, start, new HashSet<>()))
                    .as(start + " participates in a relationship cycle").isFalse();
        }
    }

    private boolean reachable(NodeKind from, NodeKind target, Set<NodeKind> seen) {
        for (NodeKind next : from.wiresInto()) {
            if (next == target) {
                return true;
            }
            if (seen.add(next) && reachable(next, target, seen)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("Graph enforces wire rules and cleans up on node removal")
    void graphRules() {
        InfraGraph graph = new InfraGraph();
        var vpc = graph.addNode(NodeKind.VPC, 0, 0);
        var droplet = graph.addNode(NodeKind.DROPLET, 100, 0);
        var domain = graph.addNode(NodeKind.DOMAIN, 200, 0);

        assertThat(graph.connect(vpc, droplet)).isTrue();
        assertThat(graph.connect(vpc, droplet)).as("duplicate").isFalse();
        assertThat(graph.connect(droplet, vpc)).as("reverse not allowed").isFalse();
        assertThat(graph.connect(vpc, domain)).as("vpc->domain invalid").isFalse();
        assertThat(graph.connect(droplet, domain)).isTrue();

        graph.removeNode(droplet);
        assertThat(graph.getWires()).isEmpty();
    }

    @Test
    @DisplayName("Cost: droplet size and k8s node count drive the estimate")
    void costEstimates() {
        InfraGraph graph = new InfraGraph();
        var droplet = graph.addNode(NodeKind.DROPLET, 0, 0);
        assertThat(droplet.monthlyUsd()).isEqualTo(6.0);
        droplet.props.put("size", "s-4vcpu-8gb");
        assertThat(droplet.monthlyUsd()).isEqualTo(48.0);

        var k8s = graph.addNode(NodeKind.KUBERNETES, 0, 0);
        k8s.props.put("nodeCount", "5");
        k8s.props.put("ha", "true");
        assertThat(k8s.monthlyUsd()).isEqualTo(24.0 * 5 + 40.0);

        assertThat(graph.totalMonthlyUsd()).isEqualTo(48.0 + 160.0);
    }

    @Test
    @DisplayName("Designs round-trip through JSON with props, positions and live ids")
    void jsonRoundTrip() {
        InfraGraph graph = new InfraGraph();
        var vpc = graph.addNode(NodeKind.VPC, 40, 60);
        var droplet = graph.addNode(NodeKind.DROPLET, 240, 60);
        droplet.label = "web-1";
        droplet.props.put("size", "s-2vcpu-4gb");
        droplet.doId = "12345";
        graph.connect(vpc, droplet);

        JSONObject json = GraphIO.toJson(graph);
        InfraGraph restored = new InfraGraph();
        GraphIO.fromJson(restored, json);

        assertThat(restored.getNodes()).hasSize(2);
        var restoredDroplet = restored.getNodes().stream()
                .filter(n -> n.kind == NodeKind.DROPLET).findFirst().orElseThrow();
        assertThat(restoredDroplet.label).isEqualTo("web-1");
        assertThat(restoredDroplet.props).containsEntry("size", "s-2vcpu-4gb");
        assertThat(restoredDroplet.doId).isEqualTo("12345");
        assertThat(restoredDroplet.x).isEqualTo(240);
        assertThat(restored.getWires()).hasSize(1);
    }
}
