package org.nmox.studio.tools.npm;

import org.nmox.studio.core.spi.LiveRuns;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.nmox.studio.rack.engine.CommandExecutor;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;
import org.openide.windows.OutputWriter;

/**
 * The one place the IDE talks to Node package managers. Detects the
 * project's own manager (corepack pin, then lockfile — delegated to the
 * rack's {@code ProjectInspector} so every lane agrees), runs
 * install/script commands, and lists globally installed packages for the
 * no-project NPM Explorer view.
 *
 * <p>Registered as a {@code @ServiceProvider}, so consumers resolve the
 * Lookup-owned instance via {@link #getDefault()} — the platform idiom,
 * not a hand-rolled singleton. All subprocess work rides the module's
 * own {@link RequestProcessor} (never the JVM-shared commonPool, never
 * the EDT), and anything that executes PROJECT-controlled code — npm
 * lifecycle scripts, package.json script bodies — asks Workspace Trust
 * first; the fixed-tool probes ({@code npm --version}, {@code npm ls -g})
 * deliberately do not prompt. Output accumulators are capped so a
 * runaway build cannot OOM the IDE.
 */
@ServiceProvider(service = NpmService.class)
public class NpmService {

    // package-manager runs block on a subprocess for up to a minute; they
    // must never occupy the JVM-shared ForkJoinPool.commonPool
    private static final RequestProcessor RP = new RequestProcessor("NPM Service", 3);

    private static final String NPM_COMMAND = "npm";
    private static final String YARN_COMMAND = "yarn";
    private static final String PNPM_COMMAND = "pnpm";

    public enum PackageManager {
        NPM, YARN, PNPM
    }

    /** One globally installed package, as reported by {@code npm ls -g}. */
    public record GlobalPackage(String name, String version) {
    }

    /**
     * Lists globally installed packages ({@code npm ls -g --depth=0 --json}),
     * quietly: no output window, and tolerant of npm's non-zero exits — npm
     * returns 1 for benign "extraneous"/"invalid" findings while still
     * printing perfectly usable JSON, so the STDOUT is parsed regardless of
     * the exit code. runBounded drains npm's chatty stderr warnings on their
     * own thread while the timeout clock runs, so a full pipe can never
     * stall the listing. Completes exceptionally only when npm itself can't
     * be launched (not installed / not on PATH).
     */
    public CompletableFuture<List<GlobalPackage>> listGlobalPackages() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                org.nmox.studio.core.process.ProcessSupport.BoundedResult result =
                        org.nmox.studio.core.process.ProcessSupport.runBounded(
                                List.of(NPM_COMMAND, "ls", "-g", "--depth=0", "--json"),
                                new File(System.getProperty("user.home")),
                                java.time.Duration.ofSeconds(30));
                // timeout leaves partial (or no) stdout; the tolerant parser
                // turns that into the empty list, matching failure behavior
                return parseGlobalList(result.stdout());
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, RP);
    }

    /**
     * The pure heart of the global listing: {@code npm ls -g --json} prints
     * {@code {"name": "lib", "dependencies": {"npm": {"version": "10.9.2"}, …}}}.
     * Tolerant: malformed JSON or a missing dependencies object → empty list;
     * a package without a version gets "". Sorted by name.
     */
    static List<GlobalPackage> parseGlobalList(String json) {
        List<GlobalPackage> packages = new ArrayList<>();
        try {
            org.json.JSONObject root = new org.json.JSONObject(json);
            org.json.JSONObject deps = root.optJSONObject("dependencies");
            if (deps != null) {
                for (String name : deps.keySet()) {
                    org.json.JSONObject info = deps.optJSONObject(name);
                    packages.add(new GlobalPackage(name,
                            info == null ? "" : info.optString("version", "")));
                }
            }
        } catch (org.json.JSONException malformed) {
            return List.of();
        }
        packages.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return packages;
    }

    public CompletableFuture<String> install(File projectDir, PackageManager manager) {
        return runCommand(projectDir, getCommand(manager), "install");
    }

    public CompletableFuture<String> runScript(File projectDir, String scriptName, PackageManager manager) {
        return runCommand(projectDir, getCommand(manager), "run", scriptName);
    }

    public boolean isAvailable(PackageManager manager) {
        try {
            Process process = org.nmox.studio.core.process.ProcessSupport
                    .builder(java.util.List.of(getCommand(manager), "--version"))
                    .redirectErrorStream(true)
                    .start();
            // bound the wait: a wedged tool must not hang the calling thread
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    public PackageManager detectPackageManager(File projectDir) {
        // one canonical detection (corepack pin, then lockfile) shared
        // with the rack's AUTO lanes — see ProjectInspector
        switch (org.nmox.studio.rack.devices.ProjectInspector.nodePackageManager(projectDir)) {
            case "yarn":
                return PackageManager.YARN;
            case "pnpm":
                return PackageManager.PNPM;
            default:
                return PackageManager.NPM;
        }
    }

    public String getCommand(PackageManager manager) {
        switch (manager) {
            case YARN:
                return YARN_COMMAND;
            case PNPM:
                return PNPM_COMMAND;
            default:
                return NPM_COMMAND;
        }
    }

    /** Cap on the in-memory output accumulator — a runaway build can't OOM. */
    private static final int MAX_OUTPUT_CHARS = 4 * 1024 * 1024;

    CompletableFuture<String> runCommand(File workingDir, String... command) {
        // npm install runs pre/postinstall lifecycle scripts and
        // `npm run <script>` runs the package.json script body — all
        // PROJECT-controlled, i.e. attacker code in a cloned repo.
        // ProcessSupport.builder is a deliberately un-gated primitive;
        // this caller must ask before running a stranger's scripts.
        // (listGlobalPackages/isAvailable run fixed IDE tools and do NOT
        // route through here, so they never prompt.)
        if (!org.nmox.studio.rack.service.WorkspaceTrust.requestTrust(workingDir)) {
            return CompletableFuture.completedFuture("");
        }
        // Route through CommandExecutor: its named daemon pump threads
        // stream to the Output window and the future completes from onExit —
        // no "NPM Service" RP thread sits draining stdout, so a long-running
        // script (npm run dev / start / serve, one double-click away in NPM
        // Explorer) can no longer pin the throughput-3 lane until the app
        // exits, and there is no drain-to-EOF-before-waitFor unreachable
        // timeout (the EnvironmentDoctor bug class, v1.106.0). The process
        // rides CommandExecutor's kill/orphan guarantee at shutdown.
        CompletableFuture<String> done = new CompletableFuture<>();
        StringBuilder output = new StringBuilder();
        // The run is a citizen of the same two stop surfaces as the ▶ (v2.70.0;
        // v2.69.10 wired the toolbar's Run and this path — NPM Explorer's
        // double-click, Run Script on a package.json line — was its sibling
        // with the handle dropped on the floor: nothing on screen could stop
        // `npm run dev` started here) and announces the server its script
        // prints (the v1.212.0 law's sibling: the ▶ announced, this lane
        // didn't — no ⇄ chip, no Live Servers, no VITALS target).
        String label = String.join(" ", command) + " — " + workingDir.getName();
        String runId = "npm-run:" + workingDir.getAbsolutePath() + "#" + RUN_SEQ.incrementAndGet();
        java.util.concurrent.atomic.AtomicReference<CommandExecutor.Handle> proc =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> announced =
                new java.util.concurrent.atomic.AtomicReference<>();
        boolean serves = announcesServer(command);
        String[] again = command.clone();
        IdeRunItem item = new IdeRunItem(command.length > 1 ? command[1] : command[0],
                org.openide.filesystems.FileUtil.toFileObject(
                        org.openide.filesystems.FileUtil.normalizeFile(workingDir)),
                label, proc::get, () -> runCommand(workingDir, again));
        // Double-clicking a script in NPM Explorer used to look broken:
        // CommandExecutor.getIO deliberately never steals focus, so the
        // run happened in a tab you had to know existed. Raise it — the
        // user just asked for this command, so its output is what they
        // are waiting to see (Run Focused Test has always done this).
        CommandExecutor.showOutput("NPM Output");
        CommandExecutor.Handle handle = CommandExecutor.run("NPM Output", workingDir, java.util.Map.of(),
                java.util.List.of(command),
                line -> {
                    synchronized (output) {
                        // bound the returned accumulator so a runaway/verbose
                        // build can't OOM; the window itself streams unbounded
                        if (output.length() < MAX_OUTPUT_CHARS) {
                            output.append(line).append('\n');
                        }
                    }
                    if (!serves) {
                        return;
                    }
                    String url = WebProjectActionProvider.servingUrlFor(line);
                    if (url != null && !url.equals(announced.get())) {
                        announced.set(url);
                        org.nmox.studio.rack.service.ServingRegistry.getDefault().register(
                                new org.nmox.studio.rack.service.ServingRegistry.Serving(
                                        runId, label, url,
                                        org.nmox.studio.rack.service.ServingRegistry.Kind.WEB,
                                        workingDir));
                    }
                },
                exit -> {
                    item.finished();
                    LiveRuns.remove(runId);
                    org.netbeans.spi.project.ui.support.BuildExecutionSupport.registerFinishedItem(item);
                    if (announced.get() != null) {
                        org.nmox.studio.rack.service.ServingRegistry.getDefault().deregister(runId);
                    }
                    String text;
                    synchronized (output) {
                        text = output.toString();
                    }
                    if (exit == 0) {
                        done.complete(text);
                    } else {
                        done.completeExceptionally(new RuntimeException(
                                "Command failed with exit code: " + exit
                                        + "\nOutput: " + text));
                    }
                });
        proc.set(handle);
        LiveRuns.add(new LiveRuns.Run(runId, label, handle::kill));
        org.netbeans.spi.project.ui.support.BuildExecutionSupport.registerRunningItem(item);
        return done;
    }

    private static final java.util.concurrent.atomic.AtomicLong RUN_SEQ =
            new java.util.concurrent.atomic.AtomicLong();

    /**
     * Which NPM Service runs may announce a printed local URL as a serving:
     * `<pm> run <script>` and `<pm> start` — the verbs whose body is a user
     * script that can start a server. An install's output is lifecycle
     * noise (a postinstall may print a URL nothing listens on), so it never
     * announces (the v1.93.0 serving-truth law).
     */
    static boolean announcesServer(String... command) {
        if (command.length < 2) {
            return false;
        }
        return "run".equals(command[1]) || "start".equals(command[1]);
    }
    
    /**
     * Run a simple command string (for compatibility with NpmExplorerTopComponent)
     */
    public void runCommand(File projectDir, String command) {
        // dispatch off the caller's thread: detectPackageManager stats
        // lockfiles and NPM Explorer calls this from the EDT (ledger 62c);
        // requestTrust marshals its own dialog, so the RP hop is safe
        RP.post(() -> {
            List<String> parts = parseArguments(command);
            List<String> cmdList = new ArrayList<>();
            cmdList.add(getCommand(detectPackageManager(projectDir)));
            cmdList.addAll(parts);
            runCommand(projectDir, cmdList.toArray(new String[0]));
        });
    }

    static List<String> parseArguments(String commandLine) {
        List<String> list = new ArrayList<>();
        if (commandLine == null || commandLine.trim().isEmpty()) {
            return list;
        }
        
        StringBuilder current = new StringBuilder();
        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;
        
        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);
            if (c == '\"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            } else if (Character.isWhitespace(c) && !inDoubleQuotes && !inSingleQuotes) {
                if (current.length() > 0) {
                    list.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        
        if (current.length() > 0) {
            list.add(current.toString());
        }
        
        return list;
    }

    // getDefault, not getInstance: this is the platform Lookup idiom, not
    // a lazily-constructed singleton — @ServiceProvider owns the instance
    public static NpmService getDefault() {
        return Lookup.getDefault().lookup(NpmService.class);
    }
}