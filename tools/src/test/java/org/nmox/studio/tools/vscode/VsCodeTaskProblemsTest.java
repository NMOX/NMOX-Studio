package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.engine.DiagnosticsBus;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Applied;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Finding;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Severity;
import org.nmox.studio.tools.vscode.VsCodeTaskSearchProvider.Exit;
import org.nmox.studio.tools.vscode.VsCodeTasks.Os;
import org.nmox.studio.tools.vscode.VsCodeTasks.TaskDef;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A running task's output on its way to the diagnostics bus: what is
 * published and when, that a run replaces the run before it, what the
 * status line says, and when a watcher is ready — all without a process:
 * the lines are fed by hand.
 */
class VsCodeTaskProblemsTest {

    @TempDir
    Path project;

    private final BiConsumer<String, List<DiagnosticsBus.Problem>> realPublisher = VsCodeTaskProblems.publisher;
    private final Predicate<File> realOnDisk = VsCodeTaskProblems.onDisk;
    private final Consumer<String> realStatus = VsCodeTaskSearchProvider.statusSink;

    /** Every batch published, in order: "tool=[line:E|W text, …]". */
    private final List<String> published = Collections.synchronizedList(new ArrayList<>());
    private final List<List<DiagnosticsBus.Problem>> batches = Collections.synchronizedList(new ArrayList<>());
    private final List<String> said = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger ready = new AtomicInteger();

    @BeforeEach
    void seams() {
        VsCodeTaskProblems.clearForTest();
        VsCodeTaskProblems.publisher = (tool, problems) -> {
            batches.add(List.copyOf(problems));
            published.add(tool + "=" + problems.stream()
                    .map(p -> p.line() + ":" + (p.error() ? "E " : "W ") + p.message()).toList());
        };
        VsCodeTaskProblems.onDisk = file -> true;
        VsCodeTaskSearchProvider.statusSink = said::add;
    }

    @AfterEach
    void restore() {
        VsCodeTaskProblems.publisher = realPublisher;
        VsCodeTaskProblems.onDisk = realOnDisk;
        VsCodeTaskSearchProvider.statusSink = realStatus;
        VsCodeTaskProblems.clearForTest();
    }

    private Applied applied(Object written, boolean background) {
        JSONObject task = new JSONObject().put("label", "t").put("type", "process").put("command", "x")
                .put("isBackground", background).put("problemMatcher", written);
        TaskDef def = VsCodeTasks.parse(new JSONObject().put("tasks", new JSONArray().put(task)).toString(),
                Os.LINUX).get(0);
        File dir = project.toFile();
        return VsCodeProblemMatchers.apply(def.problemMatchers(), background, dir,
                s -> VsCodeTasks.substitute(s, dir, name -> null));
    }

    private VsCodeTaskProblems reader(String task, Object written, boolean background) {
        return VsCodeTaskProblems.of(project.toFile(), task, applied(written, background), ready::incrementAndGet);
    }

    private static void feed(VsCodeTaskProblems problems, String... lines) {
        for (String line : lines) {
            problems.line(line);
        }
    }

    private static final String ERROR_1 = "src/a.ts(3,7): error TS2322: Type 'string' is not assignable to type 'number'.";
    private static final String WARNING_2 = "src/b.ts(1,1): warning TS6133: 'x' is declared but never read.";

    /* --------------------------------------------------------- a task that ends */

    @Test
    @DisplayName("a task's findings are published once, when it ends, under the task's own tool name — and the count is said")
    void publishedWhenTheTaskEnds() {
        VsCodeTaskProblems problems = reader("build", "$tsc", false);
        problems.started();
        feed(problems, "> tsc --noEmit", ERROR_1, WARNING_2, "Found 2 errors.");
        assertThat(published).as("nothing per line: the pump only reads").isEmpty();
        assertThat(said).as("every matcher applies: nothing to say at the start").isEmpty();

        problems.exited(2);
        assertThat(published).containsExactly("task:build=[3:E Type 'string' is not assignable to type 'number'. "
                + "(ts 2322), 1:W 'x' is declared but never read. (ts 6133)]");
        assertThat(batches.get(0)).extracting(DiagnosticsBus.Problem::file).containsExactly(
                new File(project.toFile(), "src/a.ts"), new File(project.toFile(), "src/b.ts"));
        assertThat(said).containsExactly("Task \"build\": 2 problems found in its output.");
        assertThat(ready.get()).as("a task that ends is never \"ready\"").isZero();
    }

    @Test
    @DisplayName("through the real bus: a run REPLACES the task's earlier findings, a clean run clears them, and no other tool's are touched")
    void aRunReplacesTheRunBefore() {
        VsCodeTaskProblems.publisher = realPublisher;
        File other = new File(project.toFile(), "other.js");
        DiagnosticsBus.publish("eslint-of-this-test", List.of(new DiagnosticsBus.Problem(other, 1, "kept", true)));
        try {
            VsCodeTaskProblems first = reader("build", "$tsc", false);
            feed(first, ERROR_1, WARNING_2);
            first.exited(2);
            assertThat(DiagnosticsBus.all().get("task:build")).hasSize(2);

            VsCodeTaskProblems second = reader("build", "$tsc", false);
            feed(second, WARNING_2);
            second.exited(0);
            assertThat(DiagnosticsBus.all().get("task:build")).as("the second run's batch, not both")
                    .extracting(DiagnosticsBus.Problem::message)
                    .containsExactly("'x' is declared but never read. (ts 6133)");
            assertThat(DiagnosticsBus.problemsFor(new File(project.toFile(), "src/a.ts"))).isEmpty();

            VsCodeTaskProblems clean = reader("build", "$tsc", false);
            feed(clean, "Found 0 errors.");
            clean.exited(0);
            assertThat(DiagnosticsBus.all().get("task:build")).as("a clean run clears").isEmpty();
            assertThat(DiagnosticsBus.all().get("eslint-of-this-test")).as("another tool's batch is its own").hasSize(1);
            assertThat(said).containsExactly("Task \"build\": 2 problems found in its output.",
                    "Task \"build\": 1 problem found in its output.", "Task \"build\": no problems found in its output.");

            VsCodeTaskProblems lint = reader("lint", "$tsc", false);
            feed(lint, ERROR_1);
            lint.exited(1);
            assertThat(DiagnosticsBus.all().get("task:build")).as("a different task is a different tool").isEmpty();
            assertThat(DiagnosticsBus.all().get("task:lint")).hasSize(1);
        } finally {
            DiagnosticsBus.publish("task:build", List.of());
            DiagnosticsBus.publish("task:lint", List.of());
            DiagnosticsBus.publish("eslint-of-this-test", List.of());
        }
    }

    @Test
    @DisplayName("a task that did not start says nothing about problems: what was there stays")
    void notStartedPublishesNothing() {
        VsCodeTaskProblems problems = reader("build", "$tsc", false);
        problems.started();
        feed(problems, "tsc is not installed");
        problems.exited(-1);
        assertThat(published).isEmpty();
        assertThat(said).isEmpty();
    }

    @Test
    @DisplayName("on the bus an error is an error and a warning or an info is not; the row is the message, then source and code")
    void severityAndTextOnTheBus() {
        File f = new File("/w/a.c");
        assertThat(VsCodeTaskProblems.text(new Finding(f, 1, 1, 0, 0, Severity.ERROR, "two\nlines ", "E1", "gcc")))
                .isEqualTo("two lines (gcc E1)");
        assertThat(VsCodeTaskProblems.text(new Finding(f, 1, 1, 0, 0, Severity.ERROR, "msg", "E1", null)))
                .isEqualTo("msg (E1)");
        assertThat(VsCodeTaskProblems.text(new Finding(f, 1, 1, 0, 0, Severity.ERROR, "msg", null, "gcc")))
                .isEqualTo("msg");

        JSONObject custom = new JSONObject().put("pattern", new JSONObject()
                .put("regexp", "^(\\S+):(\\d+): (\\w+): (.*)$").put("file", 1).put("line", 2).put("severity", 3)
                .put("message", 4));
        VsCodeTaskProblems problems = reader("check", custom, false);
        feed(problems, "a.c:1: error: e", "a.c:2: warning: w", "a.c:3: info: i");
        problems.exited(1);
        assertThat(published).containsExactly("task:check=[1:E e, 2:W w, 3:W i]");
    }

    @Test
    @DisplayName("the overflow, the lines too long to read, the files that are not there and a matcher switched off are all said")
    void whatWasLeftOutIsSaid() {
        VsCodeTaskProblems.onDisk = file -> !file.getName().equals("gone.ts");
        JSONObject slow = new JSONObject().put("pattern", new JSONObject()
                .put("regexp", "^(.*a){12}$").put("file", 1).put("message", 1).put("line", 1));
        VsCodeTaskProblems problems = reader("build", new JSONArray().put("$tsc").put(slow), false);
        for (int i = 1; i <= VsCodeProblemMatchers.MAX_FINDINGS + 3; i++) {
            problems.line("src/a.ts(" + i + ",1): error TS1: x");
        }
        feed(problems, "gone.ts(1,1): error TS1: " + "y".repeat(VsCodeProblemMatchers.MAX_LINE));
        for (int i = 0; i < VsCodeProblemMatchers.MAX_STRIKES; i++) {
            problems.line("a".repeat(40) + "!");
        }
        problems.exited(1);
        assertThat(batches.get(0)).hasSize(VsCodeProblemMatchers.MAX_FINDINGS);
        assertThat(said).containsExactly("Task \"build\": 2,000 problems found in its output. "
                + "3 more were over the limit of 2,000 and not kept. "
                + "1 line over the 2,000-character limit was not read. "
                + "Problem matcher #2 was switched off: the regular expression took too long on too many lines.");

        said.clear();
        VsCodeTaskProblems missing = reader("other", "$tsc", false);
        feed(missing, "gone.ts(1,1): error TS1: a", "gone.ts(2,1): error TS1: b", "here.ts(1,1): error TS1: c");
        missing.exited(1);
        assertThat(said).containsExactly("Task \"other\": 3 problems found in its output. "
                + "2 of them name a file that is not on disk; the matcher’s fileLocation may not fit this project.");
    }

    /* ------------------------------------------------------- what is not applied */

    @Test
    @DisplayName("a matcher that is not applied is named once, with the reason, when the task starts — and the task's other matchers still publish")
    void notAppliedIsSaidOnce() {
        VsCodeTaskProblems problems = reader("build", new JSONArray().put("$rustc-watch").put("$tsc"), false);
        problems.started();
        assertThat(said).containsExactly("Task \"build\" runs without its problem matcher $rustc-watch: "
                + "a VS Code extension provides that one, and NMOX Studio has no copy of it.");
        feed(problems, ERROR_1);
        problems.exited(1);
        assertThat(published).hasSize(1);

        said.clear();
        JSONObject broken = new JSONObject().put("pattern", new JSONObject().put("regexp", "(unclosed"));
        JSONObject dialect = new JSONObject().put("pattern", new JSONObject().put("regexp", "^\\h+(.*)$"));
        VsCodeTaskProblems several = reader("lint", new JSONArray().put(broken).put(dialect).put("eslint"), false);
        several.started();
        assertThat(said).containsExactly("Task \"lint\" runs without its problem matcher #1: its regular expression "
                + "(unclosed does not compile here. 2 more of its matchers are not applied either.");
        several.exited(0);
        assertThat(published).as("nothing applied, nothing published: no batch claims the task is clean").hasSize(1);

        said.clear();
        reader("a", dialect, false).started();
        reader("b", new JSONObject().put("owner", "x"), false).started();
        reader("c", new JSONObject().put("base", "$tsc").put("fileLocation", "search"), false).started();
        reader("d", new JSONObject().put("pattern", new JSONObject().put("regexp", "a".repeat(2_001))), false).started();
        JSONArray many = new JSONArray();
        for (int i = 0; i <= VsCodeProblemMatchers.MAX_MATCHERS; i++) {
            many.put("$tsc");
        }
        reader("e", many, false).started();
        assertThat(said).containsExactly(
                "Task \"a\" runs without its problem matcher #1: its regular expression uses \\h, "
                        + "which means one thing in VS Code and another here.",
                "Task \"b\" runs without its problem matcher #1: its \"pattern\" is missing, "
                        + "or is not written the way VS Code reads it.",
                "Task \"c\" runs without its problem matcher #1: NMOX Studio cannot resolve its fileLocation (search).",
                "Task \"d\" runs without its problem matcher #1: it has a regular expression over the "
                        + "2,000-character limit, or more patterns than the limit of 16.",
                "Task \"e\" runs without its problem matcher $tsc and any after it: at most 16 are read.");
    }

    @Test
    @DisplayName("a background task whose matchers have no background block is told so, and its findings come when it ends")
    void backgroundWithoutABackgroundPattern() {
        VsCodeTaskProblems problems = reader("watch", "$tsc", true);
        problems.started();
        assertThat(said).containsExactly("Task \"watch\" is a background task, and none of its problem matchers "
                + "has a background pattern: its problems are listed when it ends.");
        feed(problems, ERROR_1, "[10:32:17 AM] Found 1 error. Watching for file changes.");
        assertThat(published).isEmpty();
        assertThat(ready.get()).isZero();
        assertThat(VsCodeTaskProblems.alreadyWatching(project.toFile(), "watch")).as("not a watcher").isNull();
        problems.exited(143);
        assertThat(published).hasSize(1);
    }

    /* ------------------------------------------------------------- a watcher */

    private static final String BEGINS = "[10:32:15 AM] Starting compilation in watch mode...";
    private static final String CHANGED = "[10:33:02 AM] File change detected. Starting incremental compilation...";

    private static String ends(int errors) {
        return "[10:32:17 AM] Found " + errors + " error" + (errors == 1 ? "" : "s") + ". Watching for file changes.";
    }

    @Test
    @DisplayName("a watcher is READY at the end of its first cycle, not before — and each cycle's findings replace the last")
    void aWatcherIsReadyAtItsFirstEnd() {
        VsCodeTaskProblems problems = reader("watch", "$tsc-watch", true);
        problems.started();
        feed(problems, BEGINS, "", ERROR_1, WARNING_2);
        assertThat(ready.get()).as("still compiling: nothing that waits may start").isZero();
        assertThat(published).isEmpty();

        problems.line(ends(2));
        assertThat(ready.get()).isEqualTo(1);
        assertThat(batches).hasSize(1);
        assertThat(batches.get(0)).hasSize(2);

        feed(problems, CHANGED, "", WARNING_2, "", ends(1));
        assertThat(ready.get()).as("told again at each end; the first is what a waiter hears").isEqualTo(2);
        assertThat(batches.get(1)).as("the second cycle's own findings")
                .extracting(DiagnosticsBus.Problem::message).containsExactly("'x' is declared but never read. (ts 6133)");

        feed(problems, CHANGED, "", ends(0));
        assertThat(batches.get(2)).as("a clean cycle clears").isEmpty();

        feed(problems, CHANGED, ERROR_1);
        problems.exited(143);
        assertThat(batches).as("stopped mid-cycle: the last whole cycle stands, half a cycle is not published")
                .hasSize(3);
        assertThat(said).containsExactly("Task \"watch\": 2 problems found in its output.",
                "Task \"watch\": 1 problem found in its output.", "Task \"watch\": no problems found in its output.");
    }

    @Test
    @DisplayName("a watcher that ends before it finished a cycle publishes what it found: its exit is its answer")
    void aWatcherThatDiesEarly() {
        VsCodeTaskProblems problems = reader("watch", "$tsc-watch", true);
        feed(problems, BEGINS, ERROR_1);
        problems.exited(1);
        assertThat(ready.get()).isZero();
        assertThat(batches).hasSize(1);
        assertThat(batches.get(0)).hasSize(1);
    }

    @Test
    @DisplayName("one watcher per task: while it lives a second run is handed it — ready at once when idle, at its next end when busy, its exit when it dies")
    void alreadyWatching() throws Exception {
        File dir = project.toFile();
        assertThat(VsCodeTaskProblems.alreadyWatching(dir, "watch")).as("none runs: start it").isNull();

        VsCodeTaskProblems problems = reader("watch", "$tsc-watch", true);
        CompletableFuture<Exit> whileCompiling = VsCodeTaskProblems.alreadyWatching(dir, "watch");
        assertThat(whileCompiling).as("busy with its first cycle").isNotNull().isNotDone();
        assertThat(VsCodeTaskProblems.alreadyWatching(dir, "other")).as("another task").isNull();
        assertThat(VsCodeTaskProblems.alreadyWatching(Files.createDirectories(project.resolve("sub")).toFile(),
                "watch")).as("another project").isNull();

        feed(problems, BEGINS, ends(0));
        assertThat(whileCompiling).isCompletedWithValue(Exit.READY);
        assertThat(VsCodeTaskProblems.alreadyWatching(dir, "watch")).as("idle: ready at once")
                .isCompletedWithValue(Exit.READY);

        problems.line(CHANGED);
        CompletableFuture<Exit> duringRebuild = VsCodeTaskProblems.alreadyWatching(dir, "watch");
        assertThat(duringRebuild).as("busy again").isNotDone();
        problems.exited(137);
        assertThat(duringRebuild).as("it died before it was ready again: its exit is the answer")
                .isCompletedWithValue(new Exit(137, false));
        assertThat(VsCodeTaskProblems.alreadyWatching(dir, "watch")).as("gone: the next run starts it").isNull();
    }
}
