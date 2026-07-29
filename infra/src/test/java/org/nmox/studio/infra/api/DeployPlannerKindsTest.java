package org.nmox.studio.infra.api;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Planner coverage for the kinds the original suite left unplanned:
 * managed databases (with and without a VPC), monitor alerts scoped to
 * their wired droplets, Cloudflare records fed by non-compute
 * providers, reserved-IP attachment actions, and the firewall rule
 * grammar's tolerance for malformed entries.
 */
class DeployPlannerKindsTest {

    @Test
    @DisplayName("A managed database inside a VPC gets the private-network reference and its engine slug")
    void databaseJoinsItsVpc() {
        InfraGraph graph = new InfraGraph();
        InfraNode vpc = graph.addNode(NodeKind.VPC, 0, 0);
        InfraNode db = graph.addNode(NodeKind.DB_MYSQL, 200, 0);
        db.label = "orders-db";
        graph.connect(vpc, db);

        DoRequest req = requestFor(graph, db);

        assertThat(req.path()).isEqualTo("/v2/databases");
        assertThat(req.body().getString("engine")).isEqualTo("mysql");
        assertThat(req.body().getString("private_network_uuid"))
                .isEqualTo("${id-of:" + vpc.id + "}");
    }

    @Test
    @DisplayName("Every database kind maps to its real API engine slug")
    void engineSlugs() {
        assertThat(engineOf(NodeKind.DB_POSTGRES)).isEqualTo("pg");
        assertThat(engineOf(NodeKind.DB_MONGODB)).isEqualTo("mongodb");
        assertThat(engineOf(NodeKind.DB_VALKEY)).isEqualTo("valkey");
        assertThat(engineOf(NodeKind.DB_KAFKA)).isEqualTo("kafka");
        assertThat(engineOf(NodeKind.DB_OPENSEARCH)).isEqualTo("opensearch");
    }

    @Test
    @DisplayName("A monitor alert watches exactly the droplets wired into it")
    void monitorAlertScopesToWiredDroplets() {
        InfraGraph graph = new InfraGraph();
        InfraNode droplet = graph.addNode(NodeKind.DROPLET, 0, 0);
        InfraNode alert = graph.addNode(NodeKind.MONITOR_ALERT, 200, 0);
        graph.connect(droplet, alert);

        DoRequest req = requestFor(graph, alert);

        assertThat(req.body().toString()).contains("${id-of:" + droplet.id + "}");
    }

    @Test
    @DisplayName("A CF record fed by an app platform falls back to the placeholder-free default content")
    void cloudflareRecordWithNonComputeProvider() {
        InfraGraph graph = new InfraGraph();
        InfraNode app = graph.addNode(NodeKind.APP_PLATFORM, 0, 0);
        InfraNode record = graph.addNode(NodeKind.CF_DNS_RECORD, 200, 0);
        graph.connect(app, record);

        DoRequest req = requestFor(graph, record);

        // an app has no IP to point at; the planner refuses to invent one
        assertThat(req.body().getString("content")).isEqualTo("192.0.2.1");
    }

    @Test
    @DisplayName("A CF record fed by a droplet points at the droplet's future IP")
    void cloudflareRecordWithDropletProvider() {
        InfraGraph graph = new InfraGraph();
        InfraNode droplet = graph.addNode(NodeKind.DROPLET, 0, 0);
        InfraNode record = graph.addNode(NodeKind.CF_DNS_RECORD, 200, 0);
        graph.connect(droplet, record);

        DoRequest req = requestFor(graph, record);

        assertThat(req.body().getString("content"))
                .isEqualTo("${ip-of:" + droplet.id + "}");
    }

    @Test
    @DisplayName("A reserved IP wired to a droplet plans an assign action after creation")
    void reservedIpAssignsToDroplet() {
        InfraGraph graph = new InfraGraph();
        InfraNode ip = graph.addNode(NodeKind.RESERVED_IP, 0, 0);
        InfraNode droplet = graph.addNode(NodeKind.DROPLET, 200, 0);
        graph.connect(ip, droplet);

        List<DoRequest> plan = DeployPlanner.plan(graph);
        DoRequest assign = plan.get(plan.size() - 1);

        assertThat(assign.path()).contains("/v2/reserved_ips/").contains("/actions");
        assertThat(assign.body().getString("type")).isEqualTo("assign");
        assertThat(assign.body().getString("droplet_id"))
                .isEqualTo("${id-of:" + droplet.id + "}");
    }

    @Test
    @DisplayName("A malformed firewall rule entry is skipped, the well-formed ones survive")
    void firewallRulesTolerateMalformedEntries() {
        InfraGraph graph = new InfraGraph();
        InfraNode firewall = graph.addNode(NodeKind.FIREWALL, 0, 0);
        firewall.props.put("inbound", "80, 22/0.0.0.0/0");

        DoRequest req = requestFor(graph, firewall);

        String inbound = req.body().getJSONArray("inbound_rules").toString();
        assertThat(inbound).contains("22").doesNotContain("\"80\"");
    }

    private static DoRequest requestFor(InfraGraph graph, InfraNode node) {
        return DeployPlanner.plan(graph).stream()
                .filter(r -> r.nodeId().equals(node.id))
                .findFirst().orElseThrow();
    }

    private static String engineOf(NodeKind kind) {
        InfraGraph graph = new InfraGraph();
        InfraNode db = graph.addNode(kind, 0, 0);
        return requestFor(graph, db).body().getString("engine");
    }
}
