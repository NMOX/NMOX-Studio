package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.spi.LiveRuns;
import org.nmox.studio.rack.engine.RackBus;
import org.nmox.studio.tools.npm.NpmLaneRun;
import org.nmox.studio.tools.vscode.VsCodeTaskSearchProvider.Exit;
import org.nmox.studio.tools.vscode.VsCodeTaskSearchProvider.RunEnd;
import org.nmox.studio.tools.vscode.VsCodeTasks.EditorContext;
import org.nmox.studio.tools.vscode.VsCodeTasks.InputDef;
import org.nmox.studio.tools.vscode.VsCodeTasks.Launch;
import org.nmox.studio.tools.vscode.VsCodeTasks.TaskDef;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One Enter on a VS Code task, end to end through the provider: the tasks
 * it depends on run first and it stops at the first that fails; the
 * editor fills {@code ${file}}; the file's questions are asked before
 * anything runs, and a password's answer reaches the process and nothing
 * that is read.
 */
class VsCodeTaskRunTest {

    private static final String TASKS = """
            {
              "version": "2.0.0",
              "tasks": [
                { "label": "release", "type": "process", "command": "ship", "dependsOn": ["build", "test"],
                  "dependsOrder": "sequence" },
                { "label": "build", "type": "process", "command": "make", "dependsOn": ["gen"] },
                { "label": "test", "type": "process", "command": "check", "dependsOn": ["gen"] },
                { "label": "gen", "type": "process", "command": "codegen" },
                { "label": "all", "dependsOn": ["build", "test"] },
                { "label": "site", "type": "process", "command": "publish", "dependsOn": ["npm: docs"] },
                { "label": "npm: docs", "type": "npm", "script": "docs" },
                { "label": "broken", "type": "process", "command": "x", "dependsOn": ["gen", "gulp it"] },
                { "label": "gulp it", "type": "gulp" },
                { "label": "loop", "command": "x", "dependsOn": ["loop"] },
                { "label": "open", "type": "process", "command": "edit",
                  "args": ["${relativeFile}", "+${lineNumber}", "${selectedText}"] },
                { "label": "after open", "type": "process", "command": "x", "dependsOn": ["open"] },
                { "label": "deploy", "type": "process", "command": "deploy",
                  "args": ["${input:env}", "${input:token}"], "dependsOn": ["prepare"] },
                { "label": "prepare", "type": "process", "command": "prep", "args": ["${input:name}", "${input:env}"] },
                { "label": "secret", "type": "process", "command": "sh",
                  "args": ["-c", "test \\"$(printf %s \\"$1\\" | tr a-z b-za)\\" = ivoufs2 && echo got-it",
                           "sh", "${input:token}"] }
              ],
              "inputs": [
                { "id": "env", "type": "pickString", "description": "Environment", "options": ["dev", "prod"] },
                { "id": "token", "type": "promptString", "description": "Token", "password": true },
                { "id": "name", "type": "promptString", "description": "Name", "default": "x" }
              ]
            }
            """;

    @TempDir
    Path project;

    private final Predicate<File> realTrust = VsCodeTaskSearchProvider.trustCheck;
    private final VsCodeTaskSearchProvider.Spawner realSpawner = VsCodeTaskSearchProvider.spawner;
    private final BiFunction<File, String, CompletableFuture<Integer>> realNpm = VsCodeTaskSearchProvider.npmRunner;
    private final Consumer<String> realStatus = VsCodeTaskSearchProvider.statusSink;
    private final Supplier<EditorContext> realEditor = VsCodeTaskSearchProvider.editorProbe;
    private final BiFunction<String, InputDef, Optional<String>> realAsker = VsCodeTaskSearchProvider.asker;

    /** Everything that happened, in order: "trust?", "ask env", "start build", "say …". */
    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    private final List<Launch> launches = Collections.synchronizedList(new ArrayList<>());
    /** How each task ends when started; a task not listed exits 0 at once. */
    private final Map<String, CompletableFuture<Exit>> exits = new ConcurrentHashMap<>();

    @BeforeEach
    void seams() throws Exception {
        VsCodeTasks.clearCache();
        Files.createDirectories(project.resolve(".vscode"));
        Files.writeString(project.resolve(".vscode/tasks.json"), TASKS);
        VsCodeTaskSearchProvider.statusSink = s -> events.add("say " + s);
        VsCodeTaskSearchProvider.spawner = (label, launch, dir) -> {
            events.add("start " + label);
            launches.add(launch);
            return exits.getOrDefault(label, CompletableFuture.completedFuture(new Exit(0, false)));
        };
        VsCodeTaskSearchProvider.npmRunner = (dir, script) -> {
            events.add("npm " + script);
            return CompletableFuture.completedFuture(0);
        };
        VsCodeTaskSearchProvider.trustCheck = dir -> {
            events.add("trust?");
            return true;
        };
        VsCodeTaskSearchProvider.editorProbe = () -> EditorContext.NONE;
        VsCodeTaskSearchProvider.asker = (task, input) -> {
            events.add("ask " + input.id());
            return Optional.of("answer-" + input.id());
        };
    }

    @AfterEach
    void restore() {
        VsCodeTaskSearchProvider.trustCheck = realTrust;
        VsCodeTaskSearchProvider.spawner = realSpawner;
        VsCodeTaskSearchProvider.npmRunner = realNpm;
        VsCodeTaskSearchProvider.statusSink = realStatus;
        VsCodeTaskSearchProvider.editorProbe = realEditor;
        VsCodeTaskSearchProvider.asker = realAsker;
        LiveRuns.stopAll();
        LiveRuns.clearForTest();
    }

    private TaskDef task(String label) {
        return VsCodeTasks.read(project.toFile()).stream()
                .filter(t -> t.label().equals(label)).findFirst().orElseThrow();
    }

    /** Enter, and the lane's first pass done: the run is decided and its first stage started (or it was refused). */
    private void enter(String label) {
        VsCodeTaskSearchProvider.run(project.toFile(), task(label)).waitFinished();
    }

    private static void await(String what, BooleanSupplier ok) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!ok.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(ok.getAsBoolean()).as(what).isTrue();
    }

    private List<String> started() {
        synchronized (events) {
            return events.stream().filter(e -> e.startsWith("start ") || e.startsWith("npm "))
                    .map(e -> e.substring(e.indexOf(' ') + 1)).toList();
        }
    }

    private List<String> said() {
        synchronized (events) {
            return events.stream().filter(e -> e.startsWith("say ")).map(e -> e.substring(4)).toList();
        }
    }

    private static boolean chainIsLive() {
        return LiveRuns.live().stream().anyMatch(r -> r.id().startsWith("vscode-task-chain:"));
    }

    /* ------------------------------------------------------------- dependsOn */

    @Test
    @DisplayName("dependsOn runs first, in order, the shared dependency once, after ONE trust question")
    void dependenciesRunFirst() throws Exception {
        enter("release");
        await("the task itself starts last", () -> started().contains("release"));
        assertThat(started()).as("gen is depended on twice and runs once; build before test (sequence)")
                .containsExactly("gen", "build", "test", "release");
        synchronized (events) {
            assertThat(events.stream().filter("trust?"::equals).count()).as("one question for the chain").isEqualTo(1);
            assertThat(events.indexOf("trust?")).as("asked before the first spawn")
                    .isLessThan(events.indexOf("start gen"));
        }
        await("the run leaves the live runs when it ends", () -> !chainIsLive());
        assertThat(said()).containsExactly("Running task \"gen\"…", "Running task \"build\"…",
                "Running task \"test\"…", "Running task \"release\"…");
    }

    @Test
    @DisplayName("parallel dependencies start together: the second does not wait for the first to end")
    void parallelStartsTogether() throws Exception {
        CompletableFuture<Exit> build = new CompletableFuture<>();
        exits.put("build", build);
        enter("all");
        await("test starts while build is still running", () -> started().contains("test"));
        assertThat(started()).containsExactly("gen", "build", "test");
        assertThat(chainIsLive()).as("the run is live while a stage is").isTrue();
        build.complete(new Exit(0, false));
        await("a group ends when its tasks do", () -> !chainIsLive());
        assertThat(started()).as("the group itself starts nothing").containsExactly("gen", "build", "test");
    }

    @Test
    @DisplayName("a dependency that exits non-zero stops the chain: the status line says which, and nothing after it runs")
    void failedDependencyStopsTheChain() throws Exception {
        exits.put("build", CompletableFuture.completedFuture(new Exit(2, false)));
        enter("release");
        await("the failure is said", () -> said().stream().anyMatch(s -> s.contains("exit code")));
        assertThat(started()).as("test and release never start").containsExactly("gen", "build");
        assertThat(said()).last().isEqualTo(
                "Task \"release\" was not run: \"build\", which runs before it, ended with exit code 2.");
        await("the run is over", () -> !chainIsLive());
        Thread.sleep(150);
        assertThat(started()).containsExactly("gen", "build");
    }

    @Test
    @DisplayName("a dependency that does not start stops the chain and points at the Output window")
    void dependencyThatDidNotStart() throws Exception {
        exits.put("gen", CompletableFuture.completedFuture(new Exit(-1, false)));
        enter("release");
        await("said", () -> said().stream().anyMatch(s -> s.contains("did not start")));
        assertThat(started()).containsExactly("gen");
        assertThat(said()).last().isEqualTo("Task \"release\" was not run: \"gen\", which runs before it, "
                + "did not start — the Output window says why.");
    }

    @Test
    @DisplayName("the ■ stops the whole chain: a stage that then exits 0 does not hand over to the next")
    void stopStopsTheChain() throws Exception {
        CompletableFuture<Exit> gen = new CompletableFuture<>();
        exits.put("gen", gen);
        enter("release");
        await("the first stage is running", () -> started().contains("gen"));
        assertThat(LiveRuns.live()).as("the chain is a run the ■ can see, named for its task")
                .anyMatch(r -> r.id().startsWith("vscode-task-chain:")
                        && r.label().equals("Task \"release\" and the tasks before it — " + project.getFileName()));

        LiveRuns.stopAll();
        assertThat(chainIsLive()).as("stopping, and still listed until its stage has ended (the 3.4 law)").isTrue();
        gen.complete(new Exit(0, false)); // a process that caught the signal and left cleanly

        await("the run ends", () -> !chainIsLive());
        assertThat(started()).as("nothing after the stop").containsExactly("gen");
        assertThat(said()).last().isEqualTo("Task \"release\" was not run: the tasks before it were stopped.");
    }

    @Test
    @DisplayName("stopping one task of a chain — its own row, its own Cancel — ends the chain as a stop, not as a failure")
    void stoppingOneStepStopsTheChain() throws Exception {
        exits.put("build", CompletableFuture.completedFuture(new Exit(143, true)));
        enter("release");
        await("said", () -> said().stream().anyMatch(s -> s.contains("stopped")));
        assertThat(started()).containsExactly("gen", "build");
        assertThat(said()).last().isEqualTo("Task \"release\" was not run: the tasks before it were stopped.");
    }

    @Test
    @DisplayName("Keep Safe on a chain spawns nothing and says nothing; a single task is no chain in the live runs")
    void keepSafeAndSingleTask() throws Exception {
        VsCodeTaskSearchProvider.trustCheck = dir -> {
            events.add("trust?");
            return false;
        };
        enter("release");
        Thread.sleep(100);
        assertThat(events).containsExactly("trust?");
        assertThat(chainIsLive()).isFalse();

        events.clear();
        VsCodeTaskSearchProvider.trustCheck = dir -> true;
        CompletableFuture<Exit> gen = new CompletableFuture<>();
        exits.put("gen", gen);
        enter("gen");
        assertThat(started()).containsExactly("gen");
        assertThat(chainIsLive()).as("one task: its own run is the only row").isFalse();
        gen.complete(new Exit(0, false));
    }

    @Test
    @DisplayName("a refused dependency refuses the whole run before anything starts: no trust question, no spawn, both tasks named")
    void refusedDependencyRefusesTheRun() {
        enter("broken");
        enter("loop");
        assertThat(events).containsExactly(
                "say Task \"broken\" was not run because of \"gulp it\", which runs before it. "
                + "Task \"gulp it\" is a gulp task, which a VS Code extension provides; NMOX Studio cannot run it.",
                "say Task \"loop\" depends on itself (loop, loop); nothing was run.");
    }

    @Test
    @DisplayName("an npm-type dependency runs on the npm lane; its failure stops the chain, and a run the lane refused stops it quietly")
    void npmDependency() throws Exception {
        enter("site");
        await("the task after the script", () -> started().contains("site"));
        assertThat(started()).containsExactly("docs", "site");

        events.clear();
        VsCodeTaskSearchProvider.npmRunner = (dir, script) -> {
            events.add("npm " + script);
            return CompletableFuture.completedFuture(1);
        };
        enter("site");
        await("said", () -> said().stream().anyMatch(s -> s.contains("exit code")));
        assertThat(started()).containsExactly("docs");
        assertThat(said()).last().isEqualTo(
                "Task \"site\" was not run: \"npm: docs\", which runs before it, ended with exit code 1.");

        events.clear();
        VsCodeTaskSearchProvider.npmRunner = (dir, script) -> {
            events.add("npm " + script);
            return CompletableFuture.completedFuture(NpmLaneRun.NOT_RUN);
        };
        enter("site");
        await("the run is over", () -> !chainIsLive());
        Thread.sleep(100);
        assertThat(started()).as("the lane said why itself; the chain goes no further").containsExactly("docs");
        assertThat(said()).containsExactly("Running task \"npm: docs\"…");
    }

    /* ------------------------------------------------------------ the editor */

    @Test
    @DisplayName("no file open: a task that uses ${relativeFile} is refused naming it — alone, or as a dependency")
    void noFileOpenRefuses() {
        enter("open");
        enter("after open");
        assertThat(events).containsExactly(
                "say Task \"open\" uses ${relativeFile}, which needs a file open in the editor; nothing was run.",
                "say Task \"after open\" was not run because of \"open\", which runs before it. "
                + "Task \"open\" uses ${relativeFile}, which needs a file open in the editor; nothing was run.");
    }

    @Test
    @DisplayName("the editor is read at Enter, on the caller's thread, and its file, line and selection fill the task")
    void editorFillsTheTask() {
        List<String> probedOn = new ArrayList<>();
        VsCodeTaskSearchProvider.editorProbe = () -> {
            probedOn.add(Thread.currentThread().getName());
            return new EditorContext(project.resolve("src").resolve("app.js"), 12, 3, "two words");
        };
        enter("open");
        assertThat(probedOn).as("where Enter arrives — the event thread in the product").containsExactly(
                Thread.currentThread().getName());
        assertThat(launches).hasSize(1);
        assertThat(launches.get(0).argv()).containsExactly("edit", "src" + File.separator + "app.js", "+12", "two words");
        assertThat(launches.get(0).shown()).as("the selection is the user's own text: shown as the file wrote it")
                .isEqualTo("edit src" + File.separator + "app.js +12 ${selectedText}");

        events.clear();
        VsCodeTaskSearchProvider.editorProbe = () ->
                new EditorContext(project.resolve("a.js"), 1, 1, "x".repeat(VsCodeTasks.MAX_SELECTED_TEXT + 1));
        enter("open");
        assertThat(events).containsExactly("say Task \"open\" uses ${selectedText}, and the selection is over the "
                + java.text.NumberFormat.getIntegerInstance().format(VsCodeTasks.MAX_SELECTED_TEXT)
                + "-character limit; nothing was run.");
    }

    /* ---------------------------------------------------------------- inputs */

    @Test
    @DisplayName("the questions are asked once each, in order of first use, after trust and before anything starts")
    void questionsBeforeTheRun() throws Exception {
        VsCodeTaskSearchProvider.asker = (task, input) -> {
            events.add("ask " + input.id() + " for " + task);
            return Optional.of(input.pick() ? "prod" : "typed-" + input.id());
        };
        enter("deploy");
        await("deploy starts", () -> started().contains("deploy"));
        synchronized (events) {
            assertThat(events.subList(0, 5)).containsExactly("trust?", "ask name for deploy", "ask env for deploy",
                    "ask token for deploy", "say Running task \"prepare\"…");
        }
        assertThat(launches.get(0).argv()).containsExactly("prep", "typed-name", "prod");
        assertThat(launches.get(1).argv()).containsExactly("deploy", "prod", "typed-token");
    }

    @Test
    @DisplayName("Cancel on any question runs nothing, and the status line says which question")
    void cancelRunsNothing() throws Exception {
        VsCodeTaskSearchProvider.asker = (task, input) -> {
            events.add("ask " + input.id());
            return "env".equals(input.id()) ? Optional.empty() : Optional.of("x");
        };
        enter("deploy");
        Thread.sleep(100);
        assertThat(events).as("the question after the cancelled one is not put").containsExactly("trust?", "ask name",
                "ask env", "say Task \"deploy\" was not run: the question for \"env\" was cancelled.");
        assertThat(started()).isEmpty();
        assertThat(chainIsLive()).isFalse();
    }

    @Test
    @DisplayName("an untrusted workspace is never asked the file's questions")
    void noTrustNoQuestions() {
        VsCodeTaskSearchProvider.trustCheck = dir -> {
            events.add("trust?");
            return false;
        };
        enter("deploy");
        assertThat(events).containsExactly("trust?");
    }

    @Test
    @DisplayName("the real spawn: a password reaches the process, and neither the Output header nor the bus ever carries it")
    void passwordNeverReachesTheHeaderOrTheLog() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(
                System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"),
                "the fixture is a POSIX sh");
        VsCodeTaskSearchProvider.spawner = realSpawner;
        // the task's own script knows the password only shifted by one letter, so
        // "got-it" proves it arrived and the file's text cannot be what leaks it
        VsCodeTaskSearchProvider.asker = (task, input) -> Optional.of("hunter2");
        List<String> bus = Collections.synchronizedList(new ArrayList<>());
        RackBus.Listener tap = (device, line, err) -> bus.add(line);
        RackBus.subscribe(tap);
        try {
            enter("secret");
            await("the process ran to its end", () -> bus.stream().anyMatch(l -> l.startsWith("[exit ")));
            await("its run is gone", () -> LiveRuns.live().stream().noneMatch(r -> r.id().startsWith("vscode-task:")));
        } finally {
            RackBus.unsubscribe(tap);
        }
        synchronized (bus) {
            assertThat(bus).as("the process was handed the password: its own test of it passed")
                    .contains("got-it", "[exit 0]");
            assertThat(bus).as("the launch line names the variable, as the file wrote it")
                    .anyMatch(l -> l.startsWith("$ sh -c ") && l.endsWith(" sh ${input:token}"));
            assertThat(bus).as("and nothing that is read or recorded carries the answer")
                    .noneMatch(l -> l.contains("hunter2"));
        }
        assertThat(said()).noneMatch(s -> s.contains("hunter2"));
    }
    /* --------------------------------------------- a run somebody waits for */

    private RunEnd waited(String label) throws InterruptedException {
        java.util.concurrent.atomic.AtomicReference<RunEnd> end = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger told = new java.util.concurrent.atomic.AtomicInteger();
        VsCodeTaskSearchProvider.executeThen(project.toFile(), task(label), EditorContext.NONE, how -> {
            end.set(how);
            told.incrementAndGet();
        });
        await("the waiter hears how \"" + label + "\" ended", () -> end.get() != null);
        Thread.sleep(100);
        assertThat(told.get()).as("told once").isEqualTo(1);
        assertThat(chainIsLive()).as("the run has left the live runs by the time its waiter hears").isFalse();
        return end.get();
    }

    @Test
    @DisplayName("a waiter hears DONE only after the task itself exited zero, dependencies first")
    void aWaiterHearsTheEnd() throws Exception {
        CompletableFuture<Exit> gen = new CompletableFuture<>();
        exits.put("gen", gen);
        java.util.concurrent.atomic.AtomicReference<RunEnd> end = new java.util.concurrent.atomic.AtomicReference<>();
        VsCodeTaskSearchProvider.executeThen(project.toFile(), task("gen"), EditorContext.NONE, end::set);
        await("the task starts", () -> started().contains("gen"));
        Thread.sleep(100);
        assertThat(end.get()).as("nobody is told while the task runs").isNull();
        assertThat(chainIsLive()).as("a run somebody waits for is in the live runs, so the \u25a0 reaches its waiter").isTrue();
        gen.complete(new Exit(0, false));
        await("told when it ends", () -> end.get() == RunEnd.DONE);

        exits.clear();
        assertThat(waited("build")).isEqualTo(RunEnd.DONE);
        assertThat(started()).containsExactly("gen", "gen", "build");
    }

    @Test
    @DisplayName("a waiter hears FAILED for the task's own non-zero exit, a dependency's, and a task that did not start")
    void aWaiterHearsFailure() throws Exception {
        exits.put("build", CompletableFuture.completedFuture(new Exit(3, false)));
        assertThat(waited("build")).as("the task itself").isEqualTo(RunEnd.FAILED);
        assertThat(waited("release")).as("its dependency").isEqualTo(RunEnd.FAILED);
        exits.put("gen", CompletableFuture.completedFuture(new Exit(-1, false)));
        assertThat(waited("gen")).as("never started").isEqualTo(RunEnd.FAILED);
        assertThat(started()).doesNotContain("release");
    }

    @Test
    @DisplayName("a waiter hears STOPPED when the user stopped the task, even one that exits zero on its TERM")
    void aWaiterHearsStopped() throws Exception {
        exits.put("gen", CompletableFuture.completedFuture(new Exit(0, true)));
        assertThat(waited("gen")).isEqualTo(RunEnd.STOPPED);

        CompletableFuture<Exit> slow = new CompletableFuture<>();
        exits.put("gen", slow);
        java.util.concurrent.atomic.AtomicReference<RunEnd> end = new java.util.concurrent.atomic.AtomicReference<>();
        VsCodeTaskSearchProvider.executeThen(project.toFile(), task("gen"), EditorContext.NONE, end::set);
        await("the task starts", () -> started().size() == 2);
        LiveRuns.stopAll();
        slow.complete(new Exit(0, false)); // a process that answers its TERM with a clean exit
        await("the waiter is told", () -> end.get() != null);
        assertThat(end.get()).isEqualTo(RunEnd.STOPPED);
    }

    @Test
    @DisplayName("a waiter hears NOT_STARTED for a refusal and for Keep Safe, and nothing was spawned")
    void aWaiterHearsNotStarted() throws Exception {
        assertThat(waited("loop")).isEqualTo(RunEnd.NOT_STARTED);
        assertThat(waited("broken")).isEqualTo(RunEnd.NOT_STARTED);
        VsCodeTaskSearchProvider.trustCheck = dir -> false;
        assertThat(waited("gen")).isEqualTo(RunEnd.NOT_STARTED);
        assertThat(waited("deploy")).as("Keep Safe before the file's questions").isEqualTo(RunEnd.NOT_STARTED);
        assertThat(started()).isEmpty();
    }
}
