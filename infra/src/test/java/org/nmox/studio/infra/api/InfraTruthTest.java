package org.nmox.studio.infra.api;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Infra truth and teardown: the stack comes down in reverse dependency
 * order, only deployed nodes are touched, and every destroyable or
 * checkable kind resolves to a real per-resource API path.
 */
class InfraTruthTest {

    @Test
    @DisplayName("Teardown reverses creation order: domain -> LB -> droplet -> VPC")
    void teardownReversesDependencies() {
        InfraGraph graph = new InfraGraph();
        var vpc = graph.addNode(NodeKind.VPC, 0, 0);
        var droplet = graph.addNode(NodeKind.DROPLET, 200, 0);
        var lb = graph.addNode(NodeKind.LOAD_BALANCER, 400, 0);
        var domain = graph.addNode(NodeKind.DOMAIN, 600, 0);
        graph.connect(vpc, droplet);
        graph.connect(droplet, lb);
        graph.connect(lb, domain);
        for (var node : List.of(vpc, droplet, lb, domain)) {
            node.doId = "id-" + node.id;
        }

        var order = DeployPlanner.teardownOrder(graph);

        assertThat(order.indexOf(domain)).isLessThan(order.indexOf(lb));
        assertThat(order.indexOf(lb)).isLessThan(order.indexOf(droplet));
        assertThat(order.indexOf(droplet)).isLessThan(order.indexOf(vpc));
    }

    @Test
    @DisplayName("Teardown touches only deployed nodes; design-only stays designed")
    void teardownSkipsDesignOnly() {
        InfraGraph graph = new InfraGraph();
        var deployed = graph.addNode(NodeKind.DROPLET, 0, 0);
        var designed = graph.addNode(NodeKind.VOLUME, 200, 0);
        deployed.doId = "42";

        var order = DeployPlanner.teardownOrder(graph);

        assertThat(order).containsExactly(deployed);
    }

    @Test
    @DisplayName("Every DigitalOcean kind resolves a per-resource path; CF/HZ honestly do not")
    void resourcePathsAreReal() {
        assertThat(DigitalOceanClient.resourcePath(NodeKind.DROPLET, "7"))
                .isEqualTo("/v2/droplets/7");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.DB_POSTGRES, "abc"))
                .isEqualTo("/v2/databases/abc");
        assertThat(DigitalOceanClient.resourcePath(NodeKind.SSH_KEY, "9"))
                .isEqualTo("/v2/account/keys/9");
        // no per-record read endpoint: drift check must say so, not guess
        assertThat(DigitalOceanClient.resourcePath(NodeKind.CF_DNS_RECORD, "x")).isNull();
    }

    @Test
    @DisplayName("Empty designs tear down to an empty plan, never an error")
    void emptyDesignIsEmptyTeardown() {
        assertThat(DeployPlanner.teardownOrder(new InfraGraph())).isEmpty();
    }
}
