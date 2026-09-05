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

    /** The outline object (v2.79.0): one file's Navigator items, or a refusal. */
    private static JSONObject outlineObject() {
        JSONObject node = objectSchema(new JSONObject()
                .put("name", type("string"))
                .put("kind", type("string"))
                .put("detail", type("string"))
                .put("line", type("integer"))
                .put("depth", type("integer")),
                req("name", "kind", "detail", "line", "depth"));
        return objectSchema(new JSONObject()
                .put("file", type("string"))
                .put("items", arrayOf(node))
                .put("available", type("boolean"))
                // present only when the file could not be outlined
                .put("refusal", type("string")),
                req("file", "items", "available"));
    }

    /** The search_text object (v2.79.0): bounded literal hits with every cap reported. */
    private static JSONObject searchObject() {
        JSONObject hit = objectSchema(new JSONObject()
                .put("file", type("string"))
                .put("line", type("integer"))
                .put("text", type("string")),
                req("file", "line", "text"));
        return objectSchema(new JSONObject()
                .put("query", type("string"))
                .put("matches", arrayOf(hit))
                .put("filesScanned", type("integer"))
                .put("truncated", type("boolean"))
                .put("available", type("boolean")),
                req("query", "matches", "filesScanned", "truncated", "available"));
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
                .put("gitBranch", nullableString())
                // v2.81.0: the toolchain the IDE detected (a ProjectKind name)
                // and, for the Node family, the package manager the project's
                // own contract names — null where nothing is aimed
                .put("kind", nullableString())
                .put("packageManager", nullableString());
    }

    /** The run_history object (v2.81.0): the flight recorder's launches and exits, newest first. */
    private static JSONObject historyObject() {
        JSONObject event = objectSchema(new JSONObject()
                .put("at", type("integer"))
                .put("device", type("string"))
                .put("kind", type("string").put("enum",
                        new JSONArray().put("launched").put("ok").put("failed")))
                .put("text", type("string"))
                .put("durationMs", new JSONObject().put("type", new JSONArray().put("integer").put("null")))
                .put("exitCode", new JSONObject().put("type", new JSONArray().put("integer").put("null"))),
                req("at", "device", "kind", "text", "durationMs", "exitCode"));
        return objectSchema(new JSONObject()
                .put("events", arrayOf(event))
                .put("truncated", type("boolean")),
                req("events", "truncated"));
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
                req("project", "directory", "gitBranch", "kind", "packageManager",
                        "serverCount", "servers", "runCount", "runs", "activeFile",
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
                                req("project", "directory", "gitBranch", "kind", "packageManager")),
                        args -> render(projectState(defaultAim()))),
                new Tool("run_history",
                        "Run history",
                        "What ran lately: the flight recorder's launches and exits "
                        + "(device, command, exit code, duration), newest first. Pass "
                        + "\"limit\" (default 20, max 100).",
                        objectSchema(new JSONObject().put("limit", type("integer").put("description",
                                "Most events to return (default 20, max 100)."))),
                        historyObject(),
                        args -> render(runHistory(FlightRecorder.getDefault().timeline(),
                                args == null ? 20 : args.optInt("limit", 20)))),
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
                new Tool("outline",
                        "Outline a file",
                        "The structure of one file in the aimed project — the same "
                        + "items the Navigator shows: classes, functions, routes, "
                        + "selectors, headings — with nesting depth. Pass \"file\" "
                        + "relative to the project (or absolute, inside it).",
                        objectSchema(new JSONObject()
                                .put("file", stringType("The file to outline, relative to the aimed project.")),
                                req("file")),
                        outlineObject(),
                        args -> render(outline(SymbolIndex.find(), defaultAim().get(),
                                args == null ? "" : args.optString("file", "")))),
                new Tool("search_text",
                        "Search text",
                        "Lines in the aimed project containing a literal, case-insensitive; "
                        + "heavy directories and binaries skipped, at most 50 hits, lines "
                        + "clipped — every cap reported. Pass \"query\"; \"limit\" caps "
                        + "the hits (default 20).",
                        objectSchema(new JSONObject()
                                .put("query", stringType("The literal text to look for (not a regex)."))
                                .put("limit", type("integer").put("description",
                                        "Most hits to return (default 20, max 50).")),
                                req("query")),
                        searchObject(),
                        args -> render(searchText(defaultAim().get(),
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
        return projectState(aim, McpTools::kindOf, McpTools::packageManagerOf);
    }

    /** The project fields with the detectors as seams (v2.81.0): the real
     *  ones walk the directory's manifests, the tests hand in answers. */
    static JSONObject projectState(Supplier<File> aim,
            Function<File, String> kindOf, Function<File, String> packageManagerOf) {
        File dir = aim.get();
        JSONObject o = new JSONObject();
        if (dir == null) {
            return o.put("project", JSONObject.NULL)
                    .put("directory", JSONObject.NULL)
                    .put("gitBranch", JSONObject.NULL)
                    .put("kind", JSONObject.NULL)
                    .put("packageManager", JSONObject.NULL);
        }
        File repo = GitFacts.repoRoot(dir);
        String branch = repo == null ? null : GitFacts.branch(repo);
        String kind = kindOf.apply(dir);
        String pm = packageManagerOf.apply(dir);
        return o.put("project", dir.getName())
                .put("directory", dir.getAbsolutePath())
                .put("gitBranch", branch == null ? JSONObject.NULL : branch)
                .put("kind", kind == null ? JSONObject.NULL : kind)
                .put("packageManager", pm == null ? JSONObject.NULL : pm);
    }

    private static String kindOf(File dir) {
        org.nmox.studio.rack.devices.ProjectInspector.ProjectKind k =
                org.nmox.studio.rack.devices.ProjectInspector.detectKind(dir);
        return k == null ? null : k.name();
    }

    /** The package manager only where a Node contract exists — a package.json under the aim. */
    private static String packageManagerOf(File dir) {
        return new File(dir, "package.json").isFile()
                ? org.nmox.studio.rack.devices.ProjectInspector.nodePackageManager(dir) : null;
    }

    /** run_history: launches and exits newest first, ERROR lines left out (they belong to last_failure). */
    static JSONObject runHistory(List<FlightRecorder.Event> timeline, int limit) {
        int cap = Math.max(1, Math.min(100, limit));
        JSONArray events = new JSONArray();
        boolean truncated = false;
        for (int i = timeline.size() - 1; i >= 0; i--) {
            FlightRecorder.Event e = timeline.get(i);
            if (e.kind() == FlightRecorder.Kind.ERROR) {
                continue;
            }
            if (events.length() >= cap) {
                truncated = true;
                break;
            }
            boolean exit = e.kind() != FlightRecorder.Kind.LAUNCH;
            // an exit row names the COMMAND that exited — the device's latest
            // launch at or before it (last_failure's own rule) — not the
            // recorder's "[exit N]" line; the exit code has its own field
            String text = e.text();
            if (exit) {
                text = "";
                for (int j = i - 1; j >= 0; j--) {
                    FlightRecorder.Event l = timeline.get(j);
                    if (l.device().equals(e.device()) && l.kind() == FlightRecorder.Kind.LAUNCH) {
                        text = l.text();
                        break;
                    }
                }
            }
            events.put(new JSONObject()
                    .put("at", e.at())
                    .put("device", e.device())
                    .put("kind", switch (e.kind()) {
                        case LAUNCH -> "launched";
                        case EXIT_OK -> "ok";
                        default -> "failed";
                    })
                    .put("text", text)
                    .put("durationMs", exit && e.durationMs() >= 0 ? (Object) e.durationMs() : JSONObject.NULL)
                    .put("exitCode", exit ? (Object) FlightRecorder.parseExit(e.text()) : JSONObject.NULL));
        }
        return new JSONObject().put("events", events).put("truncated", truncated);
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

    /** outline over the seam: no provider or no aim says so; a refusal is carried. */
    static JSONObject outline(SymbolIndex index, File root, String file) {
        String f = file == null ? "" : file.strip();
        JSONObject out = new JSONObject().put("file", f).put("items", new JSONArray());
        if (index == null || root == null) {
            return out.put("available", false).put("refusal", "no symbol index: aim a project first");
        }
        SymbolIndex.Outline o = index.outline(root, f);
        if (o.refusal() != null) {
            return out.put("available", false).put("refusal", o.refusal());
        }
        JSONArray items = new JSONArray();
        for (SymbolIndex.Node n : o.nodes()) {
            items.put(new JSONObject()
                    .put("name", n.name())
                    .put("kind", n.kind())
                    .put("detail", n.detail())
                    .put("line", n.line())
                    .put("depth", n.depth()));
        }
        return out.put("items", items).put("available", true);
    }

    /** search_text over the aimed project: no aim says so. */
    static JSONObject searchText(File root, String query, int limit) {
        if (root == null) {
            return TextSearch.toJson(query, new TextSearch.Answer(List.of(), 0, false)).put("available", false);
        }
        return TextSearch.toJson(query, TextSearch.search(root.toPath(), query, Math.max(1, limit)))
                .put("available", true);
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
                .put("kind", project.get("kind"))
                .put("packageManager", project.get("packageManager"))
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
