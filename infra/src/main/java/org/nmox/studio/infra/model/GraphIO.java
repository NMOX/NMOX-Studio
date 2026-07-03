package org.nmox.studio.infra.model;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Persists an infrastructure design as JSON in the project directory -
 * the design travels with the repo, exactly like the rack patch.
 */
public final class GraphIO {

    public static final String DEFAULT_FILENAME = ".nmoxinfra.json";

    private GraphIO() {
    }

    public static JSONObject toJson(InfraGraph graph) {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        JSONArray nodeArr = new JSONArray();
        for (InfraGraph.InfraNode node : graph.getNodes()) {
            JSONObject nj = new JSONObject();
            nj.put("id", node.id);
            nj.put("kind", node.kind.name());
            nj.put("x", node.x);
            nj.put("y", node.y);
            nj.put("label", node.label);
            nj.put("props", new JSONObject(node.props));
            if (node.doId != null) {
                nj.put("doId", node.doId);
            }
            if (node.ip != null) {
                nj.put("ip", node.ip);
            }
            nodeArr.put(nj);
        }
        root.put("nodes", nodeArr);
        JSONArray wireArr = new JSONArray();
        for (InfraGraph.Wire wire : graph.getWires()) {
            wireArr.put(new JSONObject().put("from", wire.fromId()).put("to", wire.toId()));
        }
        root.put("wires", wireArr);
        return root;
    }

    public static void fromJson(InfraGraph graph, JSONObject root) {
        graph.clear();
        JSONArray nodeArr = root.optJSONArray("nodes");
        if (nodeArr == null) {
            return;
        }
        for (int i = 0; i < nodeArr.length(); i++) {
            JSONObject nj = nodeArr.getJSONObject(i);
            NodeKind kind;
            try {
                kind = NodeKind.valueOf(nj.getString("kind"));
            } catch (IllegalArgumentException ex) {
                continue; // kind from a future version; skip rather than fail
            }
            InfraGraph.InfraNode node = graph.restoreNode(
                    nj.getString("id"), kind, nj.getInt("x"), nj.getInt("y"));
            node.label = nj.optString("label", node.label);
            node.doId = nj.has("doId") ? nj.getString("doId") : null;
            node.ip = nj.has("ip") ? nj.getString("ip") : null;
            JSONObject props = nj.optJSONObject("props");
            if (props != null) {
                for (String key : props.keySet()) {
                    node.props.put(key, props.getString(key));
                }
            }
        }
        JSONArray wireArr = root.optJSONArray("wires");
        if (wireArr != null) {
            for (int i = 0; i < wireArr.length(); i++) {
                JSONObject wj = wireArr.getJSONObject(i);
                InfraGraph.InfraNode from = graph.node(wj.getString("from"));
                InfraGraph.InfraNode to = graph.node(wj.getString("to"));
                if (from != null && to != null) {
                    graph.connect(from, to);
                }
            }
        }
        graph.fireChanged();
    }

    public static void save(InfraGraph graph, File file) throws IOException {
        Files.writeString(file.toPath(), toJson(graph).toString(2), StandardCharsets.UTF_8);
    }

    public static void load(InfraGraph graph, File file) throws IOException {
        fromJson(graph, new JSONObject(Files.readString(file.toPath(), StandardCharsets.UTF_8)));
    }
}
