package org.nmox.studio.rack.mcp;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
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
 * gets to ASK the IDE things, never to make it DO things. Every
 * handler is a {@code Supplier<String>} over already-bounded state
 * (the failure disclosure is ORACLE's own {@link FailureContext} — the
 * shape a consent dialog already describes); nothing here may touch
 * CommandExecutor, ProcessSupport, or a file write, and
 * {@code McpReadOnlyLedgerTest} pins that structurally. Execution
 * verbs are a RECORDED v2 with the consent design as the entry fee
 * (docs/engineering/futures-2031.md F4).
 */
public final class McpTools {

    /** One read-only tool: a name, its story, and a state read. */
    public record Tool(String name, String description,
            Supplier<String> handler) {
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

    // ---- production wiring -------------------------------------------------

    /** The shipped roster over the rack's real state. */
    public static McpTools production() {
        return new McpTools(List.of(
                new Tool("project_state",
                        "The aimed project: name, directory, and git branch.",
                        McpTools::projectState),
                new Tool("live_servers",
                        "Every dev server the IDE knows is serving right now, with its URL.",
                        McpTools::liveServers),
                new Tool("last_failure",
                        "The most recent failed run: device, command, exit code, and up to five error lines.",
                        McpTools::lastFailure),
                new Tool("diagnostics",
                        "What the linters and checkers currently report, per tool.",
                        McpTools::diagnostics),
                new Tool("rack_devices",
                        "The devices mounted on the task rack, in rack order.",
                        McpTools::rackDevices)));
    }

    static String projectState(Supplier<File> aim) {
        File dir = aim.get();
        if (dir == null) {
            return "No project is aimed.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(dir.getName()).append('\n');
        sb.append("Directory: ").append(dir.getAbsolutePath()).append('\n');
        File repo = GitFacts.repoRoot(dir);
        String branch = repo == null ? null : GitFacts.branch(repo);
        sb.append("Git branch: ").append(branch == null
                ? "(not a git repository)" : branch);
        return sb.toString();
    }

    private static String projectState() {
        return projectState(() -> RackService.getDefault().getRack().getProjectDir());
    }

    static String liveServers(List<ServingRegistry.Serving> snapshot) {
        if (snapshot.isEmpty()) {
            return "Nothing is serving.";
        }
        StringBuilder sb = new StringBuilder();
        for (ServingRegistry.Serving s : snapshot) {
            sb.append(s.deviceTitle()).append(" — ").append(s.url()).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static String liveServers() {
        return liveServers(ServingRegistry.getDefault().snapshot());
    }

    static String lastFailure(java.util.Optional<FailureContext> failure) {
        if (failure.isEmpty()) {
            return "Nothing has failed — no failed run on record.";
        }
        FailureContext ctx = failure.get();
        StringBuilder sb = new StringBuilder();
        sb.append("Device: ").append(ctx.device()).append('\n');
        sb.append("Command: ").append(ctx.command()).append('\n');
        sb.append("Exit code: ").append(ctx.exitCode()).append('\n');
        if (ctx.errorLines().isEmpty()) {
            sb.append("Error output: (none captured)");
        } else {
            sb.append("Error output:\n");
            for (String line : ctx.errorLines()) {
                sb.append("  ").append(line).append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }

    private static String lastFailure() {
        File dir = RackService.getDefault().getRack().getProjectDir();
        return lastFailure(FailureContext.fromRecorder(
                FlightRecorder.getDefault(),
                dir == null ? "(no project)" : dir.getName()));
    }

    static String diagnostics(Map<String, List<DiagnosticsBus.Problem>> byTool) {
        if (byTool.isEmpty()) {
            return "No findings — every tool that has run is clean.";
        }
        StringBuilder sb = new StringBuilder();
        byTool.forEach((tool, problems) -> {
            sb.append('[').append(tool).append("] ")
                    .append(problems.size()).append(" finding")
                    .append(problems.size() == 1 ? "" : "s").append('\n');
            // bounded: five per tool — an agent that wants the rest can
            // read the files; this tool answers "what is failing"
            problems.stream().limit(5).forEach(p -> sb.append("  ")
                    .append(p.file().getName()).append(':').append(p.line())
                    .append(' ').append(p.error() ? "error" : "warning")
                    .append(" — ").append(p.message()).append('\n'));
        });
        return sb.toString().stripTrailing();
    }

    private static String diagnostics() {
        return diagnostics(DiagnosticsBus.all());
    }

    static String rackDevices(List<RackDevice> devices) {
        if (devices.isEmpty()) {
            return "The rack is empty.";
        }
        StringBuilder sb = new StringBuilder();
        for (RackDevice d : devices) {
            sb.append(d.getTitle()).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static String rackDevices() {
        return rackDevices(RackService.getDefault().getRack().getDevices());
    }
}
