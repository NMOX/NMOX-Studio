package org.nmox.studio.infra.model;

import java.io.File;
import java.nio.file.Path;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Design persistence: nodes with positions, props, deploy ids and IPs
 * round-trip through JSON and the on-disk file, and artifacts from a
 * newer catalog degrade to a skip instead of a crash.
 */
class GraphIORoundTripTest {

    private static InfraGraph deployedDesign() {
        InfraGraph graph = new InfraGraph();
        var vpc = graph.addNode(NodeKind.VPC, 40, 60);
        var droplet = graph.addNode(NodeKind.DROPLET, 240, 80);
        droplet.label = "web-1";
        droplet.props.put("size", "s-2vcpu-4gb");
        droplet.doId = "999";
        droplet.ip = "203.0.113.7";
        graph.connect(vpc, droplet);
        return graph;
    }

    @Test
    @DisplayName("Nodes round-trip with kind, position, label, props, doId and ip intact")
    void fullRoundTrip() {
        InfraGraph restored = new InfraGraph();
        GraphIO.fromJson(restored, GraphIO.toJson(deployedDesign()));

        assertThat(restored.getNodes()).hasSize(2);
        var droplet = restored.node("droplet-2");
        assertThat(droplet).isNotNull();
        assertThat(droplet.kind).isEqualTo(NodeKind.DROPLET);
        assertThat(droplet.x).isEqualTo(240);
        assertThat(droplet.y).isEqualTo(80);
        assertThat(droplet.label).isEqualTo("web-1");
        assertThat(droplet.props).containsEntry("size", "s-2vcpu-4gb");
        assertThat(droplet.doId).isEqualTo("999");
        assertThat(droplet.ip).isEqualTo("203.0.113.7");
        assertThat(restored.getWires())
                .containsExactly(new InfraGraph.Wire("vpc-1", "droplet-2"));
    }

    @Test
    @DisplayName("Undeployed nodes serialize without doId or ip keys and restore as null")
    void undeployedNodesCarryNoLiveState() {
        InfraGraph graph = new InfraGraph();
        graph.addNode(NodeKind.DROPLET, 0, 0);

        JSONObject json = GraphIO.toJson(graph);
        JSONObject nodeJson = json.getJSONArray("nodes").getJSONObject(0);
        assertThat(nodeJson.has("doId")).isFalse();
        assertThat(nodeJson.has("ip")).isFalse();

        InfraGraph restored = new InfraGraph();
        GraphIO.fromJson(restored, json);
        assertThat(restored.getNodes().get(0).doId).isNull();
        assertThat(restored.getNodes().get(0).ip).isNull();
    }

    @Test
    @DisplayName("A design saves to disk and loads back identical")
    void saveAndLoad(@TempDir Path dir) throws Exception {
        File file = dir.resolve(GraphIO.DEFAULT_FILENAME).toFile();
        GraphIO.save(deployedDesign(), file);
        assertThat(file).exists();

        InfraGraph restored = new InfraGraph();
        GraphIO.load(restored, file);

        assertThat(restored.getNodes()).hasSize(2);
        assertThat(restored.node("droplet-2").ip).isEqualTo("203.0.113.7");
        assertThat(restored.getWires()).hasSize(1);
    }

    @Test
    @DisplayName("Loading replaces whatever the graph held before")
    void loadingReplacesExistingContent() {
        InfraGraph graph = new InfraGraph();
        graph.addNode(NodeKind.KUBERNETES, 0, 0);

        GraphIO.fromJson(graph, GraphIO.toJson(deployedDesign()));

        assertThat(graph.getNodes()).extracting(n -> n.kind)
                .containsExactlyInAnyOrder(NodeKind.VPC, NodeKind.DROPLET);
    }

    @Test
    @DisplayName("A node kind from the future is skipped and its wires dropped, not fatal")
    void futureKindIsSkipped() {
        String futureJson = """
            {
              "version": 2,
              "nodes": [
                {"id": "quantum_tunnel-1", "kind": "QUANTUM_TUNNEL", "x": 0, "y": 0,
                 "label": "wormhole", "props": {}},
                {"id": "droplet-2", "kind": "DROPLET", "x": 10, "y": 20,
                 "label": "web", "props": {}}
              ],
              "wires": [{"from": "quantum_tunnel-1", "to": "droplet-2"}]
            }
            """;

        InfraGraph graph = new InfraGraph();
        GraphIO.fromJson(graph, new JSONObject(futureJson));

        assertThat(graph.getNodes()).hasSize(1);
        assertThat(graph.getNodes().get(0).kind).isEqualTo(NodeKind.DROPLET);
        assertThat(graph.getWires()).as("a wire into a skipped node is dropped").isEmpty();
    }

    @Test
    @DisplayName("The written JSON is versioned and a nodeless document loads as empty")
    void versionAndEmptyDocument() {
        assertThat(GraphIO.toJson(new InfraGraph()).getInt("version")).isEqualTo(1);

        InfraGraph graph = deployedDesign();
        GraphIO.fromJson(graph, new JSONObject("{\"version\": 1}"));
        assertThat(graph.getNodes()).as("missing nodes array clears the design").isEmpty();
        assertThat(graph.getWires()).isEmpty();
    }

    @Test
    @DisplayName("Sequencing continues past restored ids, so a loaded design never collides")
    void sequencingSurvivesALoad() {
        InfraGraph restored = new InfraGraph();
        GraphIO.fromJson(restored, GraphIO.toJson(deployedDesign()));

        var next = restored.addNode(NodeKind.DOMAIN, 0, 0);

        assertThat(next.id).isEqualTo("domain-3");
        assertThat(restored.getNodes()).extracting(n -> n.id).doesNotHaveDuplicates();
    }
}
