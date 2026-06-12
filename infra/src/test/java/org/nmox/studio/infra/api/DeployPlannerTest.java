package org.nmox.studio.infra.api;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;

class DeployPlannerTest {

    @Test
    @DisplayName("Plan orders providers before consumers: VPC -> droplet -> LB -> domain")
    void planRespectsDependencies() {
        InfraGraph graph = new InfraGraph();
        var domain = graph.addNode(NodeKind.DOMAIN, 600, 0); // deliberately added first
        var lb = graph.addNode(NodeKind.LOAD_BALANCER, 400, 0);
        var droplet = graph.addNode(NodeKind.DROPLET, 200, 0);
        var vpc = graph.addNode(NodeKind.VPC, 0, 0);
        graph.connect(vpc, droplet);
        graph.connect(droplet, lb);
        graph.connect(lb, domain);

        List<DoRequest> plan = DeployPlanner.plan(graph);
        List<String> order = plan.stream().map(DoRequest::nodeId).toList();

        assertThat(order.indexOf(vpc.id)).isLessThan(order.indexOf(droplet.id));
        assertThat(order.indexOf(droplet.id)).isLessThan(order.indexOf(lb.id));
        assertThat(order.indexOf(lb.id)).isLessThan(order.indexOf(domain.id));
    }

    @Test
    @DisplayName("Creation requests embed provider references as placeholders")
    void placeholdersEmbedded() {
        InfraGraph graph = new InfraGraph();
        var vpc = graph.addNode(NodeKind.VPC, 0, 0);
        var key = graph.addNode(NodeKind.SSH_KEY, 0, 100);
        var droplet = graph.addNode(NodeKind.DROPLET, 200, 0);
        graph.connect(vpc, droplet);
        graph.connect(key, droplet);

        DoRequest dropletReq = DeployPlanner.plan(graph).stream()
                .filter(r -> r.nodeId().equals(droplet.id)).findFirst().orElseThrow();

        String body = dropletReq.body().toString();
        assertThat(body).contains("${id-of:" + vpc.id + "}");
        assertThat(body).contains("${id-of:" + key.id + "}");
        assertThat(dropletReq.path()).isEqualTo("/v2/droplets");
    }

    @Test
    @DisplayName("Volume/firewall/reserved-IP wires become post-create attachment actions")
    void attachmentsComeLast() {
        InfraGraph graph = new InfraGraph();
        var droplet = graph.addNode(NodeKind.DROPLET, 0, 0);
        var volume = graph.addNode(NodeKind.VOLUME, 0, 100);
        var firewall = graph.addNode(NodeKind.FIREWALL, 0, 200);
        graph.connect(volume, droplet);
        graph.connect(firewall, droplet);

        List<DoRequest> plan = DeployPlanner.plan(graph);

        // three creations + two attachments
        assertThat(plan).hasSize(5);
        assertThat(plan.get(3).path()).contains("/actions");
        assertThat(plan.get(3).body().getString("type")).isEqualTo("attach");
        assertThat(plan.get(4).path()).contains("/droplets");
        // attachments reference both endpoints
        assertThat(plan.get(3).path()).contains("${id-of:" + volume.id + "}");
        assertThat(plan.get(3).body().toString()).contains("${id-of:" + droplet.id + "}");
    }

    @Test
    @DisplayName("Spaces buckets are planned as explicit skips (S3 protocol)")
    void spacesSkipped() {
        InfraGraph graph = new InfraGraph();
        graph.addNode(NodeKind.SPACES, 0, 0);

        List<DoRequest> plan = DeployPlanner.plan(graph);

        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).skipped()).isTrue();
        assertThat(plan.get(0).description()).contains("S3");
    }

    @Test
    @DisplayName("Already-live nodes are not re-created")
    void liveNodesSkipCreation() {
        InfraGraph graph = new InfraGraph();
        var vpc = graph.addNode(NodeKind.VPC, 0, 0);
        vpc.doId = "existing-vpc-uuid";
        var droplet = graph.addNode(NodeKind.DROPLET, 200, 0);
        graph.connect(vpc, droplet);

        List<DoRequest> plan = DeployPlanner.plan(graph);

        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).nodeId()).isEqualTo(droplet.id);
        // and the droplet references the REAL id, not a placeholder
        assertThat(plan.get(0).body().getString("vpc_uuid")).isEqualTo("existing-vpc-uuid");
    }

    @Test
    @DisplayName("Database creation carries engine, version and size")
    void databasePayload() {
        InfraGraph graph = new InfraGraph();
        var db = graph.addNode(NodeKind.DB_POSTGRES, 0, 0);

        DoRequest request = DeployPlanner.plan(graph).get(0);

        assertThat(request.path()).isEqualTo("/v2/databases");
        assertThat(request.body().getString("engine")).isEqualTo("pg");
        assertThat(request.body().getString("version")).isEqualTo("16");
        assertThat(request.body().getString("size")).isEqualTo("db-s-1vcpu-1gb");
    }
}
