package org.nmox.studio.rack.mcp;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Renders a tool's structured object to the human-readable text face —
 * the single-source-of-truth rule (v2.55.0): the structured JSON is the
 * truth, this derives the prose from it, so an agent's typed read and a
 * human's text read can never disagree. Pure and total: it dispatches
 * on the keys the structured object carries.
 */
final class Texts {

    private Texts() {
    }

    static String of(JSONObject s) {
        if (s.has("serverCount") && s.has("diagnosticCount")) {
            return ideContext(s);
        }
        if (s.has("directory")) {
            return projectState(s);
        }
        if (s.has("servers")) {
            return servers(s.getJSONArray("servers"));
        }
        if (s.has("runs")) {
            return runs(s.getJSONArray("runs"));
        }
        if (s.has("hits")) {
            return symbols(s);
        }
        if (s.has("events")) {
            return history(s);
        }
        if (s.has("openFiles")) {
            return editor(s);
        }
        if (s.has("items")) {
            return outline(s);
        }
        if (s.has("matches")) {
            return search(s);
        }
        if (s.has("failed")) {
            return failure(s);
        }
        if (s.has("totalFindings")) {
            return diagnostics(s);
        }
        if (s.has("devices")) {
            return devices(s.getJSONArray("devices"));
        }
        return s.toString();
    }

    private static String projectState(JSONObject s) {
        if (s.isNull("project")) {
            return "No project is aimed.";
        }
        String kind = s.isNull("kind") ? "unknown" : s.getString("kind");
        if (!s.isNull("packageManager")) {
            kind += " (" + s.getString("packageManager") + ")";
        }
        return "Project: " + s.getString("project") + '\n'
                + "Directory: " + s.getString("directory") + '\n'
                + "Kind: " + kind + '\n'
                + "Git branch: " + (s.isNull("gitBranch")
                ? "(not a git repository)" : s.getString("gitBranch"));
    }

    private static String servers(JSONArray servers) {
        if (servers.isEmpty()) {
            return "Nothing is serving.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < servers.length(); i++) {
            JSONObject srv = servers.getJSONObject(i);
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(srv.getString("title")).append(" — ").append(srv.getString("url"));
        }
        return sb.toString();
    }

    private static String runs(JSONArray runs) {
        if (runs.isEmpty()) {
            return "Nothing is running.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < runs.length(); i++) {
            JSONObject r = runs.getJSONObject(i);
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(r.getString("label"));
            if (!r.getString("since").isEmpty()) {
                sb.append(" (").append(r.getString("since")).append(')');
            }
        }
        return sb.toString();
    }

    private static String history(JSONObject s) {
        JSONArray events = s.getJSONArray("events");
        if (events.isEmpty()) {
            return "Nothing has run yet.";
        }
        StringBuilder sb = new StringBuilder();
        java.time.format.DateTimeFormatter clock = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.getJSONObject(i);
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(java.time.Instant.ofEpochMilli(e.getLong("at")).atZone(java.time.ZoneId.systemDefault()).format(clock))
                    .append(' ').append(e.getString("device")).append(' ').append(e.getString("kind"));
            if (!e.isNull("exitCode")) {
                sb.append(" [").append(e.getInt("exitCode")).append(']');
            }
            sb.append(' ').append(e.getString("text"));
            if (!e.isNull("durationMs")) {
                sb.append(" (").append(String.format(java.util.Locale.ROOT, "%.1f", e.getLong("durationMs") / 1000.0)).append(" s)");
            }
        }
        if (s.getBoolean("truncated")) {
            sb.append("\n(older events not shown)");
        }
        return sb.toString();
    }

    private static String symbols(JSONObject s) {
        if (!s.getBoolean("available")) {
            return "No symbol index: aim a project first.";
        }
        JSONArray hits = s.getJSONArray("hits");
        if (hits.isEmpty()) {
            return s.getString("query").isEmpty()
                    ? "Pass a name to look for."
                    : "No symbol matches \"" + s.getString("query") + "\".";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.length(); i++) {
            JSONObject h = hits.getJSONObject(i);
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(h.getString("name")).append(" (").append(h.getString("kind").toLowerCase(java.util.Locale.ROOT))
                    .append(") \u2014 ").append(h.getString("file")).append(':').append(h.getInt("line"));
        }
        if (s.getBoolean("truncated")) {
            sb.append("\n(the index is partial: the project passed the walk's file cap)");
        }
        return sb.toString();
    }

    private static String outline(JSONObject s) {
        if (!s.getBoolean("available")) {
            return "No outline: " + s.optString("refusal", "unavailable") + '.';
        }
        JSONArray items = s.getJSONArray("items");
        if (items.isEmpty()) {
            return "No structure to show in " + s.getString("file") + '.';
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.length(); i++) {
            JSONObject n = items.getJSONObject(i);
            if (i > 0) {
                sb.append('\n');
            }
            sb.append("  ".repeat(Math.max(0, n.getInt("depth"))))
                    .append(n.getString("name")).append(" (").append(n.getString("kind").toLowerCase(java.util.Locale.ROOT))
                    .append(") :").append(n.getInt("line"));
        }
        return sb.toString();
    }

    private static String search(JSONObject s) {
        if (!s.getBoolean("available")) {
            return "No project is aimed.";
        }
        JSONArray m = s.getJSONArray("matches");
        if (m.isEmpty()) {
            return s.getString("query").isEmpty()
                    ? "Pass text to look for."
                    : "No line contains \"" + s.getString("query") + "\" (" + s.getInt("filesScanned") + " files read).";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < m.length(); i++) {
            JSONObject h = m.getJSONObject(i);
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(h.getString("file")).append(':').append(h.getInt("line")).append(' ').append(h.getString("text"));
        }
        if (s.getBoolean("truncated")) {
            sb.append("\n(more matches than shown, or the walk hit a cap \u2014 narrow the query)");
        }
        return sb.toString();
    }

    private static String editor(JSONObject s) {
        if (s.has("note")) {
            return s.getString("note");
        }
        JSONArray open = s.getJSONArray("openFiles");
        if (open.isEmpty()) {
            return "No editor is open.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < open.length(); i++) {
            JSONObject f = open.getJSONObject(i);
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(f.getBoolean("active") ? "* " : "  ").append(f.getString("file"));
            if (f.getBoolean("modified")) {
                sb.append("  (unsaved changes)");
            }
        }
        return sb.toString();
    }

    private static String failure(JSONObject s) {
        if (!s.getBoolean("failed")) {
            return "Nothing has failed — no failed run on record.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Device: ").append(s.getString("device")).append('\n');
        sb.append("Command: ").append(s.getString("command")).append('\n');
        sb.append("Exit code: ").append(s.getInt("exitCode"));
        JSONArray lines = s.optJSONArray("errorLines");
        if (lines != null && !lines.isEmpty()) {
            sb.append("\nError output:");
            for (int i = 0; i < lines.length(); i++) {
                sb.append("\n  ").append(lines.getString(i));
            }
        }
        return sb.toString();
    }

    private static String diagnostics(JSONObject s) {
        JSONArray tools = s.getJSONArray("tools");
        if (tools.isEmpty()) {
            return s.has("filter")
                    ? "No findings match that filter."
                    : "No findings — every tool that has run is clean.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tools.length(); i++) {
            JSONObject t = tools.getJSONObject(i);
            int count = t.getInt("count");
            sb.append('[').append(t.getString("tool")).append("] ")
                    .append(count).append(" finding").append(count == 1 ? "" : "s")
                    .append('\n');
            JSONArray findings = t.getJSONArray("findings");
            for (int j = 0; j < findings.length(); j++) {
                JSONObject f = findings.getJSONObject(j);
                sb.append("  ").append(f.getString("file")).append(':')
                        .append(f.getInt("line")).append(' ')
                        .append(f.getString("severity")).append(" — ")
                        .append(f.getString("message")).append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }

    private static String devices(JSONArray devices) {
        if (devices.isEmpty()) {
            return "The rack is empty.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < devices.length(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(devices.getString(i));
        }
        return sb.toString();
    }

    private static String ideContext(JSONObject s) {
        StringBuilder sb = new StringBuilder();
        sb.append(s.isNull("project")
                ? "No project is aimed." : "Project: " + s.getString("project"));
        sb.append("\nServing: ").append(s.getInt("serverCount"))
                .append(s.getInt("serverCount") == 1 ? " server" : " servers");
        sb.append("\nRunning: ").append(s.getInt("runCount"))
                .append(s.getInt("runCount") == 1 ? " command" : " commands");
        if (!s.isNull("activeFile")) {
            sb.append("\nEditing: ").append(s.getString("activeFile"));
        }
        sb.append("\nLast failure: ").append(s.isNull("lastFailureDevice")
                ? "none on record" : "on " + s.getString("lastFailureDevice"));
        sb.append("\nDiagnostics: ").append(s.getInt("diagnosticCount"))
                .append(" finding").append(s.getInt("diagnosticCount") == 1 ? "" : "s");
        return sb.toString();
    }
}
