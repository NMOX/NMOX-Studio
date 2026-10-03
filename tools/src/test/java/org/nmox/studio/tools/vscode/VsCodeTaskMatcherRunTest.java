package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.spi.DebugLauncher;
import org.nmox.studio.core.spi.LiveRuns;
import org.nmox.studio.rack.engine.DiagnosticsBus;
import org.nmox.studio.tools.npm.NpmLaneRun;
import org.nmox.studio.tools.vscode.VsCodeTaskSearchProvider.Exit;
import org.nmox.studio.tools.vscode.VsCodeTasks.EditorContext;
import org.nmox.studio.tools.vscode.VsCodeTasks.TaskDef;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A task's {@code problemMatcher} through one Enter, end to end: its
 * output becomes a batch on the bus, a background task with a matcher
 * that can say "ready" is waited for until it says so and then runs on,
 * and one without is still refused by name.
 */
class VsCodeTaskMatcherRunTest {

    private static final String TASKS = """
            {
              "version": "2.0.0",
              "tasks": [
                { "label": "serve", "type": "process", "command": "serve", "dependsOn": ["watch"] },
                { "label": "watch", "type": "process", "command": "tsc", "args": ["-w"], "isBackground": true,
                  "problemMatcher": "$tsc-watch" },
                { "label": "serve blind", "type": "process", "command": "serve", "dependsOn": ["watch blind"] },
                { "label": "watch blind", "type": "process", "command": "tsc", "args": ["-w"], "isBackground": true,
                  "problemMatcher": "$tsc" },
                { "label": "serve deaf", "type": "process", "command": "serve", "dependsOn": ["watch deaf"] },
                { "label": "watch deaf", "type": "process", "command": "tsc", "args": ["-w"], "isBackground": true },
                { "label": "serve broken", "type": "process", "command": "serve", "dependsOn": ["watch broken"] },
                { "label": "watch broken", "type": "process", "command": "tsc", "isBackground": true,
                  "problemMatcher": { "base": "$tsc", "background": { "beginsPattern": "(", "endsPattern": "x" } } },
                { "label": "app", "type": "process", "command": "node", "dependsOn": ["npm: watch"] },
                { "label": "npm: watch", "type": "npm", "script": "watch", "isBackground": true,
                  "problemMatcher": ["$tsc-watch"] },
                { "label": "npm: lint", "type": "npm", "script": "lint", "problemMatcher": "$eslint-stylish" },
                { "label": "npm: plain", "type": "npm", "script": "plain" },
                { "label": "real", "type": "process", "command": "sh",
                  "args": ["-c", "echo 'src/a.ts(3,7): error TS2322: nope'; echo 'src/a.ts(9,1): warning TS6133: unused' >&2; exit 2"],
                  "problemMatcher": "$tsc" },
                { "label": "real watch", "type": "process", "command": "sh",
                  "args": ["-c", "echo '[1:00:00 AM] Starting compilation in watch mode...'; echo 'src/a.ts(1,1): error TS1: x'; echo '[1:00:01 AM] Found 1 error. Watching for file changes.'; exec sleep 60"],
                  "isBackground": true, "problemMatcher": "$tsc-watch" },
                { "label": "real serve", "type": "process", "command": "sh", "args": ["-c", "echo served"],
                  "dependsOn": ["real watch"] }
              ]
            }
            """;

    @TempDir
    Path project;

    private final Predicate<File> realTrust = VsCodeTaskSearchProvider.trustCheck;
    private final VsCodeTaskSearchProvider.Spawner realSpawner = VsCodeTaskSearchProvider.spawner;
    private final BiFunction<File, String, CompletableFuture<Integer>> realNpm = VsCodeTaskSearchProvider.npmRunner;
    private final VsCodeTaskSearchProvider.NpmReader realReader = VsCodeTaskSearchProvider.npmReader;
    private final Consumer<String> realStatus = VsCodeTaskSearchProvider.statusSink;
    private final Supplier<EditorContext> realEditor = VsCodeTaskSearchProvider.editorProbe;
    private final BiConsumer<String, List<DiagnosticsBus.Problem>> realPublisher = VsCodeTaskProblems.publisher;
    private final Predicate<File> realOnDisk = VsCodeTaskProblems.onDisk;

    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, CompletableFuture<Exit>> exits = new ConcurrentHashMap<>();
    /** The npm scripts being read: their line listeners and how they end. */
    private final Map<String, Consumer<String>> npmLines = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Integer>> npmExits = new ConcurrentHashMap<>();
    private final Map<String, List<DiagnosticsBus.Problem>> published = new ConcurrentHashMap<>();

    @BeforeEach
    void seams() throws Exception {
        VsCodeTasks.clearCache();
        VsCodeTaskProblems.clearForTest();
        Files.createDirectories(project.resolve(".vscode"));
        Files.writeString(project.resolve(".vscode/tasks.json"), TASKS);
        VsCodeTaskSearchProvider.statusSink = s -> events.add("say " + s);
        VsCodeTaskSearchProvider.spawner = (label, launch, dir) -> {
            events.add("start " + label);
            return exits.getOrDefault(label, CompletableFuture.completedFuture(new Exit(0, false)));
        };
        VsCodeTaskSearchProvider.npmRunner = (dir, script) -> {
            events.add("npm " + script);
            return CompletableFuture.completedFuture(0);
        };
        VsCodeTaskSearchProvider.npmReader = (dir, script, lines) -> {
            events.add("npm-read " + script);
            npmLines.put(script, lines);
            return npmExits.computeIfAbsent(script, s -> new CompletableFuture<>());
        };
        VsCodeTaskSearchProvider.trustCheck = dir -> {
            events.add("trust?");
            return true;
        };
        VsCodeTaskSearchProvider.editorProbe = () -> EditorContext.NONE;
        VsCodeTaskProblems.onDisk = file -> true;
        VsCodeTaskProblems.publisher = (tool, problems) -> {
            published.put(tool, List.copyOf(problems));
            events.add("publish " + tool + " " + problems.size());
        };
    }

    @AfterEach
    void restore() {
        VsCodeTaskSearchProvider.trustCheck = realTrust;
        VsCodeTaskSearchProvider.spawner = realSpawner;
        VsCodeTaskSearchProvider.npmRunner = realNpm;
        VsCodeTaskSearchProvider.npmReader = realReader;
        VsCodeTaskSearchProvider.statusSink = realStatus;
        VsCodeTaskSearchProvider.editorProbe = realEditor;
        VsCodeTaskProblems.publisher = realPublisher;
        VsCodeTaskProblems.onDisk = realOnDisk;
        LiveRuns.stopAll();
        LiveRuns.clearForTest();
        VsCodeTaskProblems.clearForTest();
    }

    private TaskDef task(String label) {
        return VsCodeTasks.read(project.toFile()).stream()
                .filter(t -> t.label().equals(label)).findFirst().orElseThrow();
    }

    private void enter(String label) {
        VsCodeTaskSearchProvider.run(project.toFile(), task(label)).waitFinished();
    }

    private static void await(String what, BooleanSupplier ok) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!ok.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(ok.getAsBoolean()).as(what).isTrue();
    }

    private List<String> started() {
        synchronized (events) {
            return events.stream().filter(e -> e.startsWith("start ") || e.startsWith("npm"))
                    .map(e -> e.substring(e.indexOf(' ') + 1)).toList();
        }
    }

    private List<String> said() {
        synchronized (events) {
            return events.stream().filter(e -> e.startsWith("say ")).map(e -> e.substring(4)).toList();
        }
    }

    private static boolean posix() {
        return !System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    /* ------------------------------------------------- a watcher as a dependency */

    @Test
    @DisplayName("a background dependency whose matcher can say \"ready\" is run, and what waits for it starts when it is ready — not before")
    void aWatcherDependencyIsWaitedForUntilReady() throws Exception {
        CompletableFuture<Exit> watch = new CompletableFuture<>();
        exits.put("watch", watch);
        enter("serve");
        await("the watcher starts", () -> started().contains("watch"));
        Thread.sleep(150);
        assertThat(started()).as("still compiling: serve waits").containsExactly("watch");
        assertThat(said()).as("no refusal").containsExactly("Running task \"watch\"…");

        watch.complete(Exit.READY);
        await("serve starts once the watcher is ready", () -> started().contains("serve"));
        assertThat(started()).containsExactly("watch", "serve");
    }

    @Test
    @DisplayName("a background dependency with no matcher, or none with a background block, is refused by name: nothing runs, nothing is asked")
    void stillRefusedWithoutABackgroundMatcher() {
        enter("serve deaf");
        enter("serve blind");
        enter("serve broken");
        assertThat(events).containsExactly(
                "say Task \"serve deaf\" was not run because of \"watch deaf\", which runs before it. "
                + "Task \"watch deaf\" is a background task that \"serve deaf\" waits for, and it has no problem matcher "
                + "that can tell NMOX Studio when it is ready; nothing was run.",
                "say Task \"serve blind\" was not run because of \"watch blind\", which runs before it. "
                + "Task \"watch blind\" is a background task that \"serve blind\" waits for, and it has no problem "
                + "matcher that can tell NMOX Studio when it is ready; nothing was run.",
                "say Task \"serve broken\" was not run because of \"watch broken\", which runs before it. "
                + "Task \"watch broken\" is a background task that \"serve broken\" waits for, and it has no problem "
                + "matcher that can tell NMOX Studio when it is ready; nothing was run.");
    }

    @Test
    @DisplayName("a watcher that already runs is not started a second time: the run is handed it — at once when it is idle, at its next end when it is busy")
    void aRunningWatcherIsNotStartedTwice() throws Exception {
        TaskDef watch = task("watch");
        VsCodeTaskProblems live = VsCodeTaskProblems.of(project.toFile(), "watch",
                VsCodeProblemMatchers.apply(watch.problemMatchers(), true, project.toFile(), s -> s), () -> { });
        live.line("[10:32:15 AM] Starting compilation in watch mode...");
        live.line("[10:32:17 AM] Found 0 errors. Watching for file changes.");
        events.clear();

        enter("serve");
        await("serve starts", () -> started().contains("serve"));
        assertThat(started()).as("the watcher was not spawned again").containsExactly("serve");
        assertThat(said()).containsExactly("Task \"watch\" is already running and watching; it was not started again.",
                "Running task \"serve\"…");

        events.clear();
        live.line("[10:33:02 AM] File change detected. Starting incremental compilation...");
        enter("serve");
        Thread.sleep(150);
        assertThat(started()).as("busy: serve waits for the cycle to end").isEmpty();
        live.line("[10:33:03 AM] Found 0 errors. Watching for file changes.");
        await("serve starts when the cycle ends", () -> started().contains("serve"));

        events.clear();
        live.exited(143);
        CompletableFuture<Exit> again = new CompletableFuture<>();
        exits.put("watch", again);
        enter("serve");
        await("the watcher is gone: it is started", () -> started().contains("watch"));
        again.complete(Exit.READY);
    }

    /* ----------------------------------------------------------- npm-type tasks */

    @Test
    @DisplayName("an npm-type task with a matcher runs on the same lane with its lines read; one without goes the way it always did")
    void npmTaskWithAMatcherIsRead() throws Exception {
        enter("npm: plain");
        assertThat(started()).containsExactly("plain");
        assertThat(events).contains("npm plain").doesNotContain("npm-read plain");

        events.clear();
        enter("npm: lint");
        assertThat(events).contains("npm-read lint").doesNotContain("npm lint");
        Consumer<String> lines = npmLines.get("lint");
        lines.accept("/w/src/cart.js");
        lines.accept("  12:5  warning  Unexpected console statement  no-console");
        lines.accept("");
        assertThat(published).as("published when the script ends, not per line").isEmpty();
        npmExits.get("lint").complete(1);
        await("published", () -> published.containsKey("task:npm: lint"));
        assertThat(published.get("task:npm: lint")).extracting(DiagnosticsBus.Problem::line,
                DiagnosticsBus.Problem::error, DiagnosticsBus.Problem::message).containsExactly(
                org.assertj.core.groups.Tuple.tuple(12, false, "Unexpected console statement (eslint no-console)"));
        assertThat(said()).last().isEqualTo("Task \"npm: lint\": 1 problem found in its output.");
    }

    @Test
    @DisplayName("an npm watch script with $tsc-watch: what waits for it starts when tsc says it is watching, and the script runs on")
    void npmWatcherIsReady() throws Exception {
        enter("app");
        await("the script starts", () -> events.contains("npm-read watch"));
        Consumer<String> lines = npmLines.get("watch");
        lines.accept("> app@1.0.0 watch");
        lines.accept("> tsc -w");
        lines.accept("10:32:15 - Starting compilation in watch mode...");
        lines.accept("src/a.ts(3,7): error TS2322: nope");
        Thread.sleep(150);
        assertThat(started()).as("still compiling").containsExactly("watch");

        lines.accept("10:32:17 - Found 1 error. Watching for file changes.");
        await("app starts", () -> started().contains("app"));
        assertThat(npmExits.get("watch")).as("the script is still running").isNotDone();
        assertThat(published.get("task:npm: watch")).hasSize(1);
        assertThat(VsCodeTaskProblems.alreadyWatching(project.toFile(), "npm: watch")).isNotNull();

        npmExits.get("watch").complete(143);
        await("its watcher entry goes with it",
                () -> VsCodeTaskProblems.alreadyWatching(project.toFile(), "npm: watch") == null);
    }

    @Test
    @DisplayName("an npm script the lane refused did not run: nothing is published and it leaves no watcher behind")
    void npmNotRun() throws Exception {
        npmExits.put("watch", CompletableFuture.completedFuture(NpmLaneRun.NOT_RUN));
        enter("app");
        await("the lane was asked", () -> events.contains("npm-read watch"));
        Thread.sleep(150);
        assertThat(started()).as("app does not start").containsExactly("watch");
        assertThat(published).isEmpty();
        assertThat(VsCodeTaskProblems.alreadyWatching(project.toFile(), "npm: watch")).isNull();
    }

    /* ---------------------------------------------------------- the real spawn */

    @Test
    @DisplayName("the real spawn: a task's stdout and stderr are both read, and the batch is published when it exits")
    void realTaskIsRead() throws Exception {
        Assumptions.assumeTrue(posix(), "the fixture is a POSIX sh");
        VsCodeTaskSearchProvider.spawner = realSpawner;
        enter("real");
        await("published at exit", () -> published.containsKey("task:real"));
        assertThat(published.get("task:real")).extracting(DiagnosticsBus.Problem::line, DiagnosticsBus.Problem::error)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(3, true),
                        org.assertj.core.groups.Tuple.tuple(9, false));
        assertThat(published.get("task:real")).extracting(DiagnosticsBus.Problem::file)
                .containsOnly(new File(project.toFile(), "src/a.ts"));
        await("said", () -> said().stream().anyMatch(s -> s.startsWith("Task \"real\": 2 problems found")));
    }

    @Test
    @DisplayName("the real spawn: a watcher reports ready, what waits for it runs, the watcher stays alive for the ■ — and a second run does not start it again")
    void realWatcherStaysAlive() throws Exception {
        Assumptions.assumeTrue(posix(), "the fixture is a POSIX sh");
        VsCodeTaskSearchProvider.spawner = realSpawner;
        enter("real serve");
        await("the watcher's first cycle is published", () -> published.containsKey("task:real watch"));
        await("what waited for it runs", () -> said().contains("Running task \"real serve\"…"));
        assertThat(published.get("task:real watch")).hasSize(1);
        BooleanSupplier watcherLive = () -> LiveRuns.live().stream()
                .filter(r -> r.id().startsWith("vscode-task:") && r.label().startsWith("real watch")).count() == 1;
        assertThat(watcherLive.getAsBoolean()).as("the watcher runs on, where the ■ can reach it").isTrue();
        await("the first serve has ended", () -> LiveRuns.live().stream()
                .noneMatch(r -> r.label().startsWith("real serve")));

        events.clear();
        enter("real serve");
        await("the second serve runs", () -> said().contains("Running task \"real serve\"…"));
        assertThat(said()).first().isEqualTo("Task \"real watch\" is already running and watching; it was not started again.");
        assertThat(watcherLive.getAsBoolean()).as("still exactly one watcher").isTrue();

        LiveRuns.stopAll();
        await("the ■ stops the watcher", () -> LiveRuns.live().stream()
                .noneMatch(r -> r.id().startsWith("vscode-task:")));
        await("and it is no longer a watcher to hand on",
                () -> VsCodeTaskProblems.alreadyWatching(project.toFile(), "real watch") == null);
    }

    /* -------------------------------------------------------------- launch.json */

    @Test
    @DisplayName("a preLaunchTask that is a watcher: the debugger starts when the watcher is ready; one that cannot say so is still refused")
    void preLaunchWatcher() throws Exception {
        Files.writeString(project.resolve("server.js"), "require('http');\n", StandardCharsets.UTF_8);
        Files.writeString(project.resolve(".vscode/launch.json"), """
                {"version":"0.2.0","configurations":[
                  {"type":"node","request":"launch","name":"Debug","program":"${workspaceFolder}/server.js",
                   "preLaunchTask":"watch"},
                  {"type":"node","request":"launch","name":"Debug deaf","program":"${workspaceFolder}/server.js",
                   "preLaunchTask":"watch deaf"}]}""", StandardCharsets.UTF_8);
        VsCodeLaunch.clearCache();
        Predicate<File> launchTrust = VsCodeLaunchSearchProvider.trustCheck;
        Supplier<DebugLauncher> launchLauncher = VsCodeLaunchSearchProvider.launcher;
        Consumer<String> launchStatus = VsCodeLaunchSearchProvider.statusSink;
        Supplier<EditorContext> launchEditor = VsCodeLaunchSearchProvider.editorProbe;
        List<String> debugged = Collections.synchronizedList(new ArrayList<>());
        try {
            VsCodeLaunchSearchProvider.trustCheck = dir -> true;
            VsCodeLaunchSearchProvider.statusSink = s -> events.add("say " + s);
            VsCodeLaunchSearchProvider.editorProbe = () -> EditorContext.NONE;
            VsCodeLaunchSearchProvider.launcher = () -> new DebugLauncher() {
                @Override
                public boolean supports(File file) {
                    return true;
                }

                @Override
                public void debug(File file) {
                    debugged.add(file.getName());
                }

                @Override
                public boolean debug(Launch launch) {
                    debugged.add(launch.program().getName());
                    return true;
                }
            };
            CompletableFuture<Exit> watch = new CompletableFuture<>();
            exits.put("watch", watch);
            VsCodeLaunchSearchProvider.run(project.toFile(), VsCodeLaunch.read(project.toFile()).get(0)).waitFinished();
            await("the watcher starts", () -> started().contains("watch"));
            Thread.sleep(150);
            assertThat(debugged).as("the watcher is still compiling").isEmpty();
            watch.complete(Exit.READY);
            await("the debugger starts when the watcher is ready", () -> !debugged.isEmpty());
            assertThat(debugged).containsExactly("server.js");
            await("and says so", () -> said().stream().anyMatch(said -> said.startsWith("Starting the debugger")));

            events.clear();
            VsCodeLaunchSearchProvider.run(project.toFile(), VsCodeLaunch.read(project.toFile()).get(1)).waitFinished();
            assertThat(events).containsExactly("say Configuration \"Debug deaf\" names the background task "
                    + "\"watch deaf\" as its preLaunchTask, and that task has no problem matcher that can tell "
                    + "NMOX Studio when it is ready; nothing was started.");
            assertThat(debugged).hasSize(1);
        } finally {
            VsCodeLaunchSearchProvider.trustCheck = launchTrust;
            VsCodeLaunchSearchProvider.launcher = launchLauncher;
            VsCodeLaunchSearchProvider.statusSink = launchStatus;
            VsCodeLaunchSearchProvider.editorProbe = launchEditor;
            VsCodeLaunch.clearCache();
        }
    }
}
