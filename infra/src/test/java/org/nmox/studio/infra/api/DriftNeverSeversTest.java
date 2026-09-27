package org.nmox.studio.infra.api;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refresh never severs a link to a live resource on its own (3.4). A 404
 * means "not in the account these tokens reach" — with a teammate's token
 * for a different account that is every resource in the shared design, and
 * the drift check used to null each {@code doId} and let the designer save.
 */
class DriftNeverSeversTest {

    @Test
    @DisplayName("a 404'd resource is reported with its status and KEPT; the rest stay live")
    void notFoundIsReportedNotForgotten() throws Exception {
        InfraGraph graph = new InfraGraph();
        InfraNode gone = graph.addNode(NodeKind.DROPLET, 0, 0);
        gone.doId = "111";
        InfraNode live = graph.addNode(NodeKind.DROPLET, 0, 100);
        live.doId = "222";
        InfraNode designed = graph.addNode(NodeKind.DROPLET, 0, 200); // never deployed

        DigitalOceanClient.ResourceReader cloud = (node, path) -> {
            if (node == gone) {
                throw new IOException("HTTP 404: The resource you were accessing could not be found.");
            }
            return new JSONObject("{\"droplet\": {\"networks\": {\"v4\": []}}}");
        };
        Map<String, String> statuses = new LinkedHashMap<>();
        List<InfraNode> notFound = new DigitalOceanClient().refreshDrift(graph,
                (node, status) -> statuses.put(node.id, status), cloud);

        assertThat(notFound).containsExactly(gone);
        assertThat(gone.doId).as("forgotten only when the user says so").isEqualTo("111");
        assertThat(live.doId).isEqualTo("222");
        assertThat(statuses.get(gone.id)).startsWith("drifted");
        assertThat(statuses.get(live.id)).isEqualTo("live");
        assertThat(statuses).doesNotContainKey(designed.id);
    }

    @Test
    @DisplayName("a failed check is not a not-found: nothing is offered for forgetting")
    void failureIsNotNotFound() throws Exception {
        InfraGraph graph = new InfraGraph();
        InfraNode node = graph.addNode(NodeKind.DROPLET, 0, 0);
        node.doId = "9";
        List<InfraNode> notFound = new DigitalOceanClient().refreshDrift(graph, (n, s) -> { },
                (n, path) -> {
                    throw new IOException("HTTP 500: upstream returned 404 page");
                });
        assertThat(notFound).isEmpty();
        assertThat(node.doId).isEqualTo("9");
    }
}
