package org.nmox.studio.tools.vscode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.nmox.studio.rack.engine.DiagnosticsBus;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Applied;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Finding;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Report;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Session;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Severity;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Signal;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Skipped;
import org.nmox.studio.tools.vscode.VsCodeTaskSearchProvider.Exit;
import org.openide.util.NbBundle;

/**
 * One running task's output, read for problems (3.6.0): the thin half
 * over {@link VsCodeProblemMatchers}. The provider hands it every line
 * the process prints and the process's exit; it hands the findings to the
 * diagnostics bus — where Action Items, the editor's squiggles, the
 * status line's count and the Agent Port already read — and says on the
 * status line what it did.
 *
 * <p><b>A batch replaces the task's batch before it.</b> The bus keeps
 * one batch per tool, and the tool here is the task ({@link #tool}): a
 * second run of {@code build} replaces the first run's findings, and a
 * run that finds nothing clears them. A task that did not start says
 * nothing about problems, so it publishes nothing and what was there
 * stays.
 *
 * <p><b>When.</b> A task that ends: once, when it has ended. A
 * background task whose matcher has a {@code background} block: at every
 * {@link Signal#ENDED} — each cycle of a watcher is a whole answer, and
 * replaces the cycle before — and that same moment is when {@code ready}
 * is run: VS Code starts what waits for a background task when its
 * matcher first goes idle, whatever it found. When such a watcher is
 * then stopped, its last whole cycle stands; nothing is published for
 * the half a cycle it was stopped in. A background task whose matchers
 * have no {@code background} block has no cycles: it is said so at the
 * start, and its findings are published when it ends.
 *
 * <p><b>One watcher per task.</b> A background task that can say it is
 * ready is, while its process lives, in {@link #alreadyWatching}: a
 * second run that would start it again — Debug pressed a second time,
 * whose {@code preLaunchTask} is the watcher — is handed the one that
 * runs instead, ready at once when it is idle and at the end of its
 * cycle when it is busy, as VS Code hands on an active background task.
 *
 * <p><b>The pump stays cheap.</b> {@link #line} runs on the process's
 * output thread: a few bounded regular expressions per line and nothing
 * else. Publishing happens at an end, never per line.
 */
final class VsCodeTaskProblems {

    /** The bus's name for a task's findings is this and the task's label: {@code task:build}. */
    static final String TOOL_PREFIX = "task:";

    /**
     * Where a batch goes, as a seam: (the tool, its problems). The real
     * one is the diagnostics bus.
     */
    static volatile BiConsumer<String, List<DiagnosticsBus.Problem>> publisher = DiagnosticsBus::publish;

    /** Whether a finding's file is there, as a seam (asked once per file, at an end). */
    static volatile Predicate<File> onDisk = File::isFile;

    /** The watchers whose processes live, by project and task. */
    private static final Map<String, VsCodeTaskProblems> WATCHERS = new ConcurrentHashMap<>();

    private final String task;
    private final Applied applied;
    private final Session session;
    private final Runnable ready;
    /** This run's place among the watchers, or null when it is not one. */
    private final String watcher;
    /** A watcher between cycles: its last cycle has ended and no new one has begun. Guarded by this. */
    private boolean idle;
    /** The process has ended. Guarded by this. */
    private boolean over;
    /** Runs that found this watcher busy and wait for its cycle to end. Guarded by this. */
    private final List<CompletableFuture<Exit>> waiting = new ArrayList<>();

    private VsCodeTaskProblems(File project, String task, Applied applied, Runnable ready) {
        this.task = task;
        this.applied = applied;
        this.session = applied.matchers().isEmpty() ? null : new Session(applied);
        this.ready = ready;
        this.watcher = applied.watches() ? key(project, task) : null;
    }

    /**
     * The reader for one run of {@code task} in {@code project}; every
     * one made must hear {@link #exited}.
     *
     * @param ready run (once or more) when a background task's matcher
     *        reports the task ready; never run for a task without one
     */
    static VsCodeTaskProblems of(File project, String task, Applied applied, Runnable ready) {
        VsCodeTaskProblems problems = new VsCodeTaskProblems(project, task, applied, ready);
        if (problems.watcher != null) {
            WATCHERS.put(problems.watcher, problems);
        }
        return problems;
    }

    private static String key(File project, String task) {
        return project.getAbsolutePath() + '\n' + task;
    }

    /**
     * When the watcher of {@code task} that already runs in {@code
     * project} is next ready — at once when it is between cycles — or
     * null when none runs, and the task is to be started. The future
     * completes with the watcher's own exit should it end first.
     */
    static CompletableFuture<Exit> alreadyWatching(File project, String task) {
        VsCodeTaskProblems live = WATCHERS.get(key(project, task));
        return live == null ? null : live.whenReady();
    }

    private synchronized CompletableFuture<Exit> whenReady() {
        if (over) {
            return null;
        }
        if (idle) {
            return CompletableFuture.completedFuture(Exit.READY);
        }
        CompletableFuture<Exit> next = new CompletableFuture<>();
        waiting.add(next);
        return next;
    }

    /** Hands {@code how} to every run that waits for this watcher. */
    private void release(Exit how) {
        List<CompletableFuture<Exit>> waiters;
        synchronized (this) {
            waiters = new ArrayList<>(waiting);
            waiting.clear();
        }
        waiters.forEach(w -> w.complete(how));
    }

    /** Forgets every watcher (tests). */
    static void clearForTest() {
        WATCHERS.clear();
    }

    /** The bus's name for {@code task}'s findings: the same for every run of it, so a run replaces the last. */
    static String tool(String task) {
        return TOOL_PREFIX + task;
    }

    /**
     * The run is starting: says, once, which of the task's matchers are
     * not applied and why — or that a background task has no matcher
     * that could say when a cycle ends. Says nothing for a task whose
     * matchers are all applied.
     */
    void started() {
        List<Skipped> skipped = applied.skipped();
        if (!skipped.isEmpty()) {
            String first = skipSentence(skipped.get(0));
            VsCodeTaskSearchProvider.statusSink.accept(skipped.size() == 1 ? first
                    : first + " " + message("VsCodeTaskProblems_skipMore", skipped.size() - 1));
        } else if (session != null && applied.background() && !applied.watches()) {
            VsCodeTaskSearchProvider.statusSink.accept(message("VsCodeTaskProblems_backgroundBlind", task));
        }
    }

    private String skipSentence(Skipped skipped) {
        return switch (skipped.why()) {
            case UNKNOWN -> message("VsCodeTaskProblems_skipUnknown", task, skipped.name());
            case REGEXP -> message("VsCodeTaskProblems_skipRegexp", task, skipped.name(), skipped.detail());
            case DIALECT -> message("VsCodeTaskProblems_skipDialect", task, skipped.name(), skipped.detail());
            case INVALID -> message("VsCodeTaskProblems_skipInvalid", task, skipped.name(), skipped.detail());
            case FILE_LOCATION -> message("VsCodeTaskProblems_skipFileLocation", task, skipped.name(),
                    skipped.detail());
            case TOO_LARGE -> message("VsCodeTaskProblems_skipTooLarge", task, skipped.name(),
                    VsCodeProblemMatchers.MAX_REGEXP, VsCodeProblemMatchers.MAX_PATTERNS);
            case TOO_MANY -> message("VsCodeTaskProblems_skipTooMany", task, skipped.name(),
                    VsCodeProblemMatchers.MAX_MATCHERS);
        };
    }

    /** One line of the process's output, on its pump thread. */
    void line(String line) {
        if (session == null) {
            return;
        }
        Signal signal = session.line(line);
        if (signal == Signal.BEGAN) {
            synchronized (this) {
                idle = false;
            }
        } else if (signal == Signal.ENDED) {
            // the findings first: what starts now may read them
            publish(session.lastCycle());
            synchronized (this) {
                idle = true;
            }
            ready.run();
            release(Exit.READY);
        }
    }

    /**
     * The process has ended with {@code code} (-1: it did not start).
     * Publishes what was found — unless nothing ran, or a watcher's last
     * whole cycle already stands.
     */
    void exited(int code) {
        synchronized (this) {
            over = true;
        }
        if (watcher != null) {
            WATCHERS.remove(watcher, this);
            release(new Exit(code, false));
        }
        if (session == null || code == -1) {
            return;
        }
        Report report = session.report();
        if (applied.watches() && report.cycles() > 0) {
            return;
        }
        publish(report);
    }

    private void publish(Report report) {
        List<DiagnosticsBus.Problem> problems = new ArrayList<>();
        Map<File, Boolean> there = new HashMap<>();
        int missing = 0;
        for (Finding finding : report.findings()) {
            problems.add(new DiagnosticsBus.Problem(finding.file(), finding.line(), text(finding),
                    finding.severity() == Severity.ERROR));
            if (!there.computeIfAbsent(finding.file(), onDisk::test)) {
                missing++;
            }
        }
        publisher.accept(tool(task), problems);
        StringBuilder said = new StringBuilder(message("VsCodeTaskProblems_found", task, problems.size()));
        if (report.dropped() > 0) {
            said.append(' ').append(message("VsCodeTaskProblems_dropped", report.dropped(),
                    VsCodeProblemMatchers.MAX_FINDINGS));
        }
        if (missing > 0) {
            said.append(' ').append(message("VsCodeTaskProblems_missing", missing));
        }
        if (report.longLines() > 0) {
            said.append(' ').append(message("VsCodeTaskProblems_longLines", report.longLines(),
                    VsCodeProblemMatchers.MAX_LINE));
        }
        if (!report.switchedOff().isEmpty()) {
            said.append(' ').append(message("VsCodeTaskProblems_switchedOff",
                    String.join(", ", report.switchedOff()), report.switchedOff().size()));
        }
        VsCodeTaskSearchProvider.statusSink.accept(said.toString());
    }

    /**
     * A finding as one row's text: its message on one line, then — as VS
     * Code's Problems panel shows it — the matcher's source and the
     * tool's own code for it: {@code Type 'string' is not assignable to
     * type 'number'. (ts 2322)}.
     */
    static String text(Finding finding) {
        String message = finding.message().replace('\n', ' ').replace('\r', ' ').strip();
        if (finding.code() == null) {
            return message;
        }
        return message + " (" + (finding.source() == null ? "" : finding.source() + " ") + finding.code() + ")";
    }

    private static String message(String key, Object... args) {
        return NbBundle.getMessage(VsCodeTaskProblems.class, key, args);
    }
}
