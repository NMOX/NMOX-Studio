package org.nmox.studio.infra.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.model.GraphIO;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deeper DigitalOcean: cloud-init reaches the create body, and a
 * droplet's public IP survives save/reload so the designer can hand
 * you an ssh command in a later session.
 */
class DigitalOceanDeepTest {

    @Test
    @DisplayName("Cloud-init user_data reaches the droplet create body — only when set")
    void userDataFlowsWhenSet() {
        InfraGraph graph = new InfraGraph();
        InfraNode droplet = graph.addNode(NodeKind.DROPLET, 0, 0);

        String bare = plannedBody(graph, droplet);
        assertThat(bare).doesNotContain("user_data");

        droplet.props.put("userData", "#cloud-config\npackages: [nginx]\n");
        String configured = plannedBody(graph, droplet);
        assertThat(configured).contains("user_data").contains("cloud-config");
    }

    @Test
    @DisplayName("A droplet's public IP round-trips through save/reload")
    void ipPersists() {
        InfraGraph graph = new InfraGraph();
        InfraNode droplet = graph.addNode(NodeKind.DROPLET, 40, 40);
        droplet.doId = "123456";
        droplet.ip = "203.0.113.7";

        InfraGraph reloaded = new InfraGraph();
        GraphIO.fromJson(reloaded, GraphIO.toJson(graph));

        InfraNode back = reloaded.getNodes().get(0);
        assertThat(back.doId).isEqualTo("123456");
        assertThat(back.ip).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("Cloud-init is a real droplet property in the catalog")
    void userDataIsACatalogProp() {
        assertThat(NodeKind.DROPLET.getProps())
                .anyMatch(p -> p.key().equals("userData"));
    }

    private static String plannedBody(InfraGraph graph, InfraNode droplet) {
        return DeployPlanner.plan(graph).stream()
                .filter(r -> r.nodeId().equals(droplet.id))
                .findFirst().orElseThrow().body().toString();
    }
}
