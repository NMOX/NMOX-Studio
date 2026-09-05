package org.nmox.studio.rack.mcp;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.core.spi.LiveRuns;
import org.nmox.studio.core.spi.SymbolIndex;
import org.nmox.studio.core.util.GitFacts;
import org.nmox.studio.rack.engine.DiagnosticsBus;
import org.nmox.studio.rack.engine.FlightRecorder;
import org.nmox.studio.rack.engine.OracleClient.FailureContext;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.service.RackService;
import org.nmox.studio.rack.service.ServingRegistry;

/**
 * The Agent Port's tool roster — READ-ONLY BY CONSTRUCTION, the law
 * this arc stands on: a caller that is not the user at the keyboard
 * gets to ASK the IDE things, never to make it DO things. Nothing here
 * may touch CommandExecutor, ProcessSupport, or a file write, and
 * {@code McpReadOnlyLedgerTest} pins that structurally.
 *
 * <p>Every tool is built for an AGENT, not just a human reader (the
 * "best MCP server for developers" pass, v2.55.0): each returns
 * BOTH a human-readable text summary AND a typed {@code structuredContent}
 * object validated by a declared {@code outputSchema}, carries an
 * {@code inputSchema} for its arguments, and is annotated
 * {@code readOnlyHint:true} so an agent framework can TRUST the
 * read-only guarantee from the protocol itself, not our prose. The
 * structured shape is the single source of truth; the text is rendered
 * from it, so the two can never disagree.
 */
public final class McpTools {

    /** A tool's answer: the human text AND the typed structured object. */
    public record ToolResult(String text, JSONObject structured) {
    }

    /**
     * One read-only tool. {@code inputSchema}/{@code outputSchema} are
     * JSON Schema objects; {@code handler} takes the call's arguments
     * (never null — an empty object when none) and returns both faces.
     */
    public record Tool(String name, String title, String description,
            JSONObject inputSchema, JSONObject outputSchema,
            Function<JSONObject, ToolResult> handler) {
    }

    private final List<Tool> tools;

    public McpTools(List<Tool> tools) {
        this.tools = List.copyOf(tools);
    }

    public List<Tool> all() {
        return tools;
    }

    public Tool byName(String name) {
        for (Tool t : tools) {
            if (t.name().equals(name)) {
                return t;
            }
        }
        return null;
    }

    // ---- schema helpers ----------------------------------------------------
    // Every schema below is the CONTRACT for exactly what the matching
    // builder emits — McpSchemaContractTest validates each builder's real
    // output against its declared schema, because a schema that was never
    // checked against its own output is a claim, not a contract (the
    // v2.56.1 review find: four of six declared additionalProperties:false
    // while emitting undeclared keys, and three arrays were declared as a
    // bare [] — not a JSON Schema at all).

    static JSONObject objectSchema(JSONObject properties) {
        return objectSchema(properties, null);
    }

    static JSONObject objectSchema(JSONObject properties, JSONArray required) {
        JSONObject o = new JSONObject()
                .put("type", "object")
                .put("properties", properties == null ? new JSONObject() : properties)
                .put("additionalProperties", false);
        if (required != null && !required.isEmpty()) {
            o.put("required", required);
        }
        return o;
    }

    private static JSONObject noArgs() {
        return objectSchema(new JSONObject());
    }

    private static JSONObject nullableString() {
        return new JSONObject().put("type", new JSONArray().put("string").put("null"));
    }

    private static JSONObject type(String t) {
        return new JSONObject().put("type", t);
    }

    private static JSONObject stringType(String description) {
        return type("string").put("description", description);
    }

    private static JSONObject arrayOf(JSONObject items) {
        return type("array").put("items", items);
    }

    private static JSONArray req(String... names) {
        JSONArray a = new JSONArray();
        for (String n : names) {
            a.put(n);
        }
        return a;
    }

    /** The live_servers array — shared with ide_context so the two
     *  can never drift. */
    private static JSONObject serversArray() {
        return arrayOf(objectSchema(new JSONObject()
                .put("title", type("string"))
                .put("url", type("string"))
                .put("kind", type("string").put("enum",
                        new JSONArray().put("WEB").put("CHAIN"))),
                req("title", "url", "kind")));
    }

    /** The live_runs array — shared with ide_context (v2.77.0): every
     *  command the IDE is running, with when it started. */
    private static JSONObject runsArray() {
        return arrayOf(objectSchema(new JSONObject()
                .put("label", type("string"))
                .put("since", type("string"))
                .put("startedAt", type("integer")),
                req("label", "since", "startedAt")));
    }

    /** The find_symbol object (v2.78.0): hits over the Go to Symbol index. */
    private static JSONObject symbolsObject() {
        JSONObject hit = objectSchema(new JSONObject()
                .put("name", type("string"))
                .put("kind", type("string"))
                .put("file", type("string"))
                .put("line", type("integer")),
                req("name", "kind", "file", "line"));
        return objectSchema(new JSONObject()
                .put("query", type("string"))
                .put("hits", arrayOf(hit))
                .put("truncated", type("boolean"))
                .put("available", type("boolean")),
                req("query", "hits", "truncated", "available"));
    }

    /** The editor_state object (v2.78.0): the active file and the open tabs. */
    private static JSONObject editorObject() {
        JSONObject open = objectSchema(new JSONObject()
                .put("file", type("string"))
                .put("modified", type("boolean"))
                .put("active", type("boolean")),
                req("file", "modified", "active"));
        return objectSchema(new JSONObject()
                .put("activeFile", nullableString())
                .put("openCount", type("integer"))
                .put("openFiles", arrayOf(open))
                // present only when the state could not be read — "nothing
                // open" and "could not look" are different truths
                .put("note", type("string")),
                req("activeFile", "openCount", "openFiles"));
    }

    /** The last_failure object — shared with ide_context. Only
     *  {@code failed} is required: a clean record carries nothing else. */
    private static JSONObject failureObject() {
        return objectSchema(new JSONObject()
                .put("failed", type("boolean"))
                .put("device", type("string"))
                .put("command", type("string"))
                .put("exitCode", type("integer"))
                .put("errorLines", arrayOf(type("string"))),
                req("failed"));
    }

    private static JSONObject projectProperties() {
        return new JSONObject()
                .put("project", nullableString())
                .put("directory", nullableString())
                .put("gitBranch", nullableString());
    }

    private static JSONObject diagnosticsObject() {
        JSONObject finding = objectSchema(new JSONObject()
                .put("file", type("string"))
                .put("line", type("integer"))
                .put("severity", type("string").put("enum",
                        new JSONArray().put("error").put("warning")))
                .put("message", type("string")),
                req("file", "line", "severity", "message"));
        JSONObject perTool = objectSchema(new JSONObject()
                .put("tool", type("string"))
                .put("count", type("integer"))
                .put("findings", arrayOf(finding)),
                req("tool", "count", "findings"));
        return objectSchema(new JSONObject()
                .put("totalFindings", type("integer"))
                .put("tools", arrayOf(perTool))
                // present only when a filter was applied — "nothing
                // matches THIS filter" is a different truth than "clean"
                .put("filter", type("string")),
                req("totalFindings", "tools"));
    }

    // ---- production wiring -------------------------------------------------

    /** The shipped roster over the rack's real state. */
    public static McpTools production() {
        JSONObject ideContextSchema = objectSchema(projectProperties()
                .put("serverCount", type("integer"))
                .put("servers", serversArray())
                .put("runCount", type("integer"))
                .put("runs", runsArray())
                .put("activeFile", nullableString())
                .put("lastFailureDevice", nullableString())
                .put("lastFailure", failureObject())
                .put("diagnosticCount", type("integer")),
                req("project", "directory", "gitBranch", "serverCount",
                        "servers", "runCount", "runs", "activeFile",
                        "lastFailureDevice", "lastFailure", "diagnosticCount"));
        return new McpTools(List.of(
                new Tool("ide_context",
                        "IDE context",
                        "The whole orienting snapshot in one call: the aimed "
                        + "project, everything serving, everything running, the "
                        + "file being edited, the last failure, and a diagnostic "
                        + "summary. Start here.",
                        noArgs(),
                        ideContextSchema,
                        args -> renderIdeContext()),
                new Tool("project_state",
                        "Project state",
                        "The aimed project: name, directory, and git branch.",
                        noArgs(),
                        objectSchema(projectProperties(),
                                req("project", "directory", "gitBranch")),
                        args -> render(projectState(defaultAim()))),
                new Tool("live_servers",
                        "Live servers",
                        "Every dev server the IDE knows is serving right now, with its URL.",
                        noArgs(),
                        objectSchema(new JSONObject().put("servers", serversArray()),
                                req("servers")),
                        args -> render(liveServers(defaultServings()))),
                new Tool("live_runs",
                        "Live runs",
                        "Every command the IDE is running right now (the toolbar "
                        + "\u25a0 would stop these), each with when it started. "
                        + "Lists only — nothing here stops a run.",
                        noArgs(),
                        objectSchema(new JSONObject().put("runs", runsArray()),
                                req("runs")),
                        args -> render(liveRuns(defaultRuns()))),
                new Tool("last_failure",
                        "Last failure",
                        "The most recent failed run: device, command, exit code, "
                        + "and up to five error lines.",
                        noArgs(),
                        failureObject(),
                        args -> render(lastFailure(defaultFailure()))),
                new Tool("diagnostics",
                        "Diagnostics",
                        "What the linters and checkers currently report. Pass "
                        + "\"file\" to filter to findings whose path contains that "
                        + "substring.",
                        objectSchema(new JSONObject().put("file",
                                stringType("Only findings whose file path contains this substring."))),
                        diagnosticsObject(),
                        args -> render(diagnostics(defaultDiagnostics(),
                                args == null ? null : args.optString("file", null)))),
                new Tool("find_symbol",
                        "Find symbol",
                        "Where a name is declared in the aimed project — functions, "
                        + "classes, routes, selectors — from the IDE's own Go to Symbol "
                        + "index. Prefix matches lead. Pass \"query\"; \"limit\" caps "
                        + "the answer (default 20, max 100).",
                        objectSchema(new JSONObject()
                                .put("query", stringType("The name, or its start, to look for."))
                                .put("limit", type("integer").put("description",
                                        "Most hits to return (default 20, max 100).")),
                                req("query")),
                        symbolsObject(),
                        args -> render(findSymbol(SymbolIndex.find(), defaultAim().get(),
                                args == null ? "" : args.optString("query", ""),
                                args == null ? 20 : args.optInt("limit", 20)))),
                new Tool("editor_state",
                        "Editor state",
                        "What the user has open: the file being edited and every open "
                        + "editor tab, with unsaved ones flagged.",
                        noArgs(),
                        editorObject(),
                        args -> render(EditorState.live())),
                new Tool("rack_devices",
                        "Rack devices",
                        "The devices mounted on the task rack, in rack order.",
                        noArgs(),
                        objectSchema(new JSONObject()
                                .put("devices", arrayOf(type("string"))),
                                req("devices")),
                        args -> render(rackDevices(defaultDevices())))));
    }

    /** Wraps a structured object into a ToolResult, rendering the text. */
    private static ToolResult render(JSONObject structured) {
        return new ToolResult(Texts.of(structured), structured);
    }

    // ---- the pure, structured builders (one source of truth) ---------------

    static JSONObject projectState(Supplier<File> aim) {
        File dir = aim.get();
        JSONObject o = new JSONObject();
        if (dir == null) {
            return o.put("project", JSONObject.NULL)
                    .put("directory", JSONObject.NULL)
                    .put("gitBranch", JSONObject.NULL);
        }
        File repo = GitFacts.repoRoot(dir);
        String branch = repo == null ? null : GitFacts.branch(repo);
        return o.put("project", dir.getName())
                .put("directory", dir.getAbsolutePath())
                .put("gitBranch", branch == null ? JSONObject.NULL : branch);
    }

    static JSONObject liveServers(List<ServingRegistry.Serving> snapshot) {
        JSONArray servers = new JSONArray();
        for (ServingRegistry.Serving s : snapshot) {
            servers.put(new JSONObject()
                    .put("title", s.deviceTitle())
                    .put("url", s.url())
                    .put("kind", s.kind().name()));
        }
        return new JSONObject().put("servers", servers);
    }

    /** The live runs, in registration order — the same population the
     *  \u25a0 tooltip and the Workbench's RUNNING section read. */
    static JSONObject liveRuns(List<LiveRuns.Run> live) {
        JSONArray runs = new JSONArray();
        for (LiveRuns.Run r : live) {
            runs.put(new JSONObject()
                    .put("label", r.label())
                    .put("since", LiveRuns.since(r.id()))
                    .put("startedAt", LiveRuns.startedAt(r.id())));
        }
        return new JSONObject().put("runs", runs);
    }

    /** find_symbol over the seam: a missing provider or no aim says so. */
    static JSONObject findSymbol(SymbolIndex index, File root, String query, int limit) {
        String q = query == null ? "" : query.strip();
        JSONObject out = new JSONObject().put("query", q).put("hits", new JSONArray());
        if (index == null || root == null) {
            return out.put("truncated", false).put("available", false);
        }
        int cap = Math.max(1, Math.min(100, limit));
        SymbolIndex.Answer a = q.isEmpty()
                ? new SymbolIndex.Answer(List.of(), false)
                : index.search(root, q, cap);
        JSONArray hits = new JSONArray();
        for (SymbolIndex.Hit h : a.hits()) {
            hits.put(new JSONObject()
                    .put("name", h.name())
                    .put("kind", h.kind())
                    .put("file", h.file())
                    .put("line", h.line()));
        }
        return out.put("hits", hits).put("truncated", a.truncated()).put("available", true);
    }

    static JSONObject lastFailure(java.util.Optional<FailureContext> failure) {
        if (failure.isEmpty()) {
            return new JSONObject().put("failed", false);
        }
        FailureContext ctx = failure.get();
        JSONArray lines = new JSONArray();
        ctx.errorLines().forEach(lines::put);
        return new JSONObject()
                .put("failed", true)
                .put("device", ctx.device())
                .put("command", ctx.command())
                .put("exitCode", ctx.exitCode())
                .put("errorLines", lines);
    }

    static JSONObject diagnostics(Map<String, List<DiagnosticsBus.Problem>> byTool,
            String fileFilter) {
        JSONArray tools = new JSONArray();
        int total = 0;
        for (Map.Entry<String, List<DiagnosticsBus.Problem>> e : byTool.entrySet()) {
            List<DiagnosticsBus.Problem> matches = e.getValue().stream()
                    .filter(p -> fileFilter == null || fileFilter.isBlank()
                            || p.file().getPath().contains(fileFilter))
                    .toList();
            if (matches.isEmpty()) {
                continue;
            }
            total += matches.size();
            JSONArray findings = new JSONArray();
            // bounded: five per tool — an agent that wants the rest reads
            // the files; this tool answers "what is failing"
            matches.stream().limit(5).forEach(p -> findings.put(new JSONObject()
                    .put("file", p.file().getName())
                    .put("line", p.line())
                    .put("severity", p.error() ? "error" : "warning")
                    .put("message", p.message())));
            tools.put(new JSONObject()
                    .put("tool", e.getKey())
                    .put("count", matches.size())
                    .put("findings", findings));
        }
        JSONObject out = new JSONObject().put("totalFindings", total).put("tools", tools);
        // record the applied filter so an empty result reads honestly:
        // "nothing matches THIS filter" is a different truth than "clean"
        if (fileFilter != null && !fileFilter.isBlank()) {
            out.put("filter", fileFilter);
        }
        return out;
    }

    static JSONObject rackDevices(List<RackDevice> devices) {
        JSONArray names = new JSONArray();
        for (RackDevice d : devices) {
            names.put(d.getTitle());
        }
        return new JSONObject().put("devices", names);
    }

    static JSONObject ideContext(Supplier<File> aim,
            List<ServingRegistry.Serving> servings,
            List<LiveRuns.Run> runs,
            String activeFile,
            java.util.Optional<FailureContext> failure,
            Map<String, List<DiagnosticsBus.Problem>> diags) {
        JSONObject project = projectState(aim);
        JSONObject diagnostics = diagnostics(diags, null);
        JSONObject failObj = lastFailure(failure);
        return new JSONObject()
                .put("project", project.get("project"))
                .put("directory", project.get("directory"))
                .put("gitBranch", project.get("gitBranch"))
                .put("serverCount", servings.size())
                .put("servers", liveServers(servings).getJSONArray("servers"))
                .put("runCount", runs.size())
                .put("runs", liveRuns(runs).getJSONArray("runs"))
                .put("activeFile", activeFile == null ? JSONObject.NULL : activeFile)
                .put("lastFailureDevice",
                        failObj.optBoolean("failed") ? failObj.get("device") : JSONObject.NULL)
                .put("lastFailure", failObj)
                .put("diagnosticCount", diagnostics.getInt("totalFindings"));
    }

    // ---- default (live) suppliers ------------------------------------------

    private static Supplier<File> defaultAim() {
        return () -> RackService.getDefault().getRack().getProjectDir();
    }

    private static List<ServingRegistry.Serving> defaultServings() {
        return ServingRegistry.getDefault().snapshot();
    }

    private static List<LiveRuns.Run> defaultRuns() {
        return LiveRuns.live();
    }

    private static java.util.Optional<FailureContext> defaultFailure() {
        File dir = RackService.getDefault().getRack().getProjectDir();
        return FailureContext.fromRecorder(FlightRecorder.getDefault(),
                dir == null ? "(no project)" : dir.getName());
    }

    private static Map<String, List<DiagnosticsBus.Problem>> defaultDiagnostics() {
        return DiagnosticsBus.all();
    }

    private static List<RackDevice> defaultDevices() {
        return RackService.getDefault().getRack().getDevices();
    }

    private static ToolResult renderIdeContext() {
        JSONObject editor = EditorState.live();
        String active = editor.isNull("activeFile") ? null : editor.getString("activeFile");
        JSONObject structured = ideContext(defaultAim(), defaultServings(),
                defaultRuns(), active, defaultFailure(), defaultDiagnostics());
        return new ToolResult(Texts.of(structured), structured);
    }
}
