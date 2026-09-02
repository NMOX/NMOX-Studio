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
        return "Project: " + s.getString("project") + '\n'
                + "Directory: " + s.getString("directory") + '\n'
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
        sb.append("\nLast failure: ").append(s.isNull("lastFailureDevice")
                ? "none on record" : "on " + s.getString("lastFailureDevice"));
        sb.append("\nDiagnostics: ").append(s.getInt("diagnosticCount"))
                .append(" finding").append(s.getInt("diagnosticCount") == 1 ? "" : "s");
        return sb.toString();
    }
}
