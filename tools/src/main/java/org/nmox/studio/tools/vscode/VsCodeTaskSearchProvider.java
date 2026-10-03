package org.nmox.studio.tools.vscode;

import java.awt.EventQueue;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.core.search.SearchTerms;
import org.nmox.studio.core.spi.LiveRuns;
import org.nmox.studio.core.spi.ProjectAim;
import org.nmox.studio.core.util.PlainStatus;
import org.nmox.studio.rack.devices.ServeUrls;
import org.nmox.studio.rack.engine.CommandExecutor;
import org.nmox.studio.rack.service.ServingRegistry;
import org.nmox.studio.rack.service.WorkspaceTrust;
import org.nmox.studio.tools.npm.NpmLaneRun;
import org.nmox.studio.tools.npm.search.NpmScriptSearchProvider;
import org.nmox.studio.tools.vscode.VsCodeTaskPlan.Checked;
import org.nmox.studio.tools.vscode.VsCodeTaskPlan.Outcome;
import org.nmox.studio.tools.vscode.VsCodeTaskPlan.Prepared;
import org.nmox.studio.tools.vscode.VsCodeTaskPlan.Ready;
import org.nmox.studio.tools.vscode.VsCodeTaskPlan.Refusal;
import org.nmox.studio.tools.vscode.VsCodeTaskPlan.Step;
import org.nmox.studio.tools.vscode.VsCodeTasks.EditorContext;
import org.nmox.studio.tools.vscode.VsCodeTasks.Host;
import org.nmox.studio.tools.vscode.VsCodeTasks.InputDef;
import org.nmox.studio.tools.vscode.VsCodeTasks.Launch;
import org.nmox.studio.tools.vscode.VsCodeTasks.NpmLaunch;
import org.nmox.studio.tools.vscode.VsCodeTasks.Refused;
import org.nmox.studio.tools.vscode.VsCodeTasks.TaskDef;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * Quick Search over the aimed project's {@code .vscode/tasks.json}
 * (v3.1.0): a developer arriving from VS Code types "build" into ⌘I (or
 * ⇧⌘P) and expects the task their repository already declares. Each task
 * lists as {@code Run task: build — cargo build --release}; Enter runs it.
 * The shape is {@link NpmScriptSearchProvider}'s: a pure {@link #items}
 * half, HTML-escaped labels, the house matcher, a seam per side effect.
 *
 * <p><b>A new spawn site, so the house laws in full.</b> A task's command
 * is the repository's own text — attacker code in a cloned repo — so:
 * Workspace Trust on the project BEFORE the spawn ({@link #trustCheck});
 * the spawn through {@link CommandExecutor#run} so output streams to the
 * Output window and the process tree dies on stop; the run registered with
 * {@link LiveRuns} so the toolbar ■ stops it; a printed local address
 * announced as a serving (the rack's {@link ServeUrls#firstLocalUrl} rule,
 * withdrawn at exit — the v1.93.0 serving-truth law); and every step off
 * the EDT, because the platform runs a result's action on the EDT.
 * An {@code npm}-type task spawns nothing here: it goes to the NPM Service
 * lane, which carries its own trust gate, exactly as the npm scripts
 * category does — but the same question is asked HERE first, on the
 * folder that lane will ask about, so "Running task …" is said only once
 * the user has said yes (Keep Safe used to leave that sentence on the
 * status line over a task that never ran); the lane's own ask is then
 * silent, because the folder is trusted.
 *
 * <p><b>One Enter, one run — which may be several tasks.</b> A task's
 * {@code dependsOn} runs first, in the stages {@link VsCodeTaskPlan}
 * decides; its {@code ${file}} family is read from the editor on the
 * event thread at Enter ({@link VsCodeTaskEditor}); its {@code
 * ${input:…}} questions are put before anything runs ({@link
 * VsCodeTaskPrompts}), and Cancel on any of them runs nothing. The whole
 * run is decided first — one refused task refuses all of it — then
 * Workspace Trust is asked ONCE, then the questions, then stage by stage
 * each task goes down the same path a lone task takes: {@link #launch}
 * or the npm lane. A stage starts only when every task of the one before
 * it exited zero; a task that fails, or that the user stops, ends the
 * run there and the status line says which. While more than one task is
 * to run, the run itself is in {@link LiveRuns}, so the toolbar ■ ends
 * the chain and not just the task of the moment.
 *
 * <p><b>Refusals speak.</b> A task that uses a variable nothing here can
 * fill, needs a file or a selection the editor does not have, depends on
 * a task the file does not define (or on itself, or on a background
 * task), names a working folder outside the project, or has an
 * extension's type, is still LISTED (so a user can see we read their
 * file) and on Enter says why on the status line, spawning nothing and
 * asking nothing.
 */
public class VsCodeTaskSearchProvider implements SearchProvider {

    /** Commands longer than this (in code points) are clipped with an ellipsis. */
    static final int MAX_COMMAND = NpmScriptSearchProvider.MAX_COMMAND;

    /** The words a user reaches for besides the task's own. */
    static final String VOCABULARY = "task tasks vscode";

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-quicksearch-vscode-task", 1);

    private static final AtomicLong RUN_SEQ = new AtomicLong();

    /** The trust question, as a seam: Keep Safe must spawn nothing (tested). */
    static volatile Predicate<File> trustCheck = dir -> WorkspaceTrust.requestTrust(dir);

    /** The spawn, as a seam: tests prove Enter hands over exactly the resolved argv, dir and env. */
    static volatile Spawner spawner = VsCodeTaskSearchProvider::launch;

    /**
     * An npm-type task's hand-off, as a seam: the trust-gated NPM Service
     * lane, answering with the script's exit code ({@link
     * NpmLaneRun#NOT_RUN} when the lane refused it and said why).
     */
    static volatile BiFunction<File, String, CompletableFuture<Integer>> npmRunner = NpmLaneRun::runScript;

    /** Where refusals and progress are said, as a seam. */
    static volatile Consumer<String> statusSink = VsCodeTaskSearchProvider::status;

    /** The machine a task resolves against (OS, environment, shells), as a seam. */
    static volatile Host host = Host.system();

    /** The editor at Enter — its file, caret and selection — as a seam; the default reads it on the EDT. */
    static volatile Supplier<EditorContext> editorProbe = VsCodeTaskEditor::snapshot;

    /** One {@code ${input:…}} question put to the user, as a seam: (the task, the input) → the answer, empty for Cancel. */
    static volatile BiFunction<String, InputDef, Optional<String>> asker = VsCodeTaskPrompts::ask;

    /** How one task ended: its exit code (-1 when it did not start), and whether the user stopped it. */
    record Exit(int code, boolean stopped) {
    }

    /** Starts one resolved task; completes with how it ended. */
    @FunctionalInterface
    interface Spawner {
        CompletableFuture<Exit> spawn(String taskLabel, Launch launch, File project);
    }

    /** One listed task: its definition, the project it belongs to, and the label shown. */
    record Item(TaskDef task, File project, String label) {
    }

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        String needle = request.getText();
        if (needle == null || needle.isBlank()) {
            return;
        }
        ProjectAim aim = ProjectAim.find();
        File project = aim == null ? null : aim.projectDir();
        for (Item item : itemsFor(needle, project)) {
            if (!response.addResult(() -> run(item.project(), item.task()), item.label())) {
                return;
            }
        }
    }

    /** The tasks of {@code project} that match {@code query}, best first; empty without a tasks.json. */
    static List<Item> itemsFor(String query, File project) {
        if (project == null || query == null || query.isBlank()) {
            return List.of();
        }
        return items(query, VsCodeTasks.read(project), project);
    }

    /** The pure half of {@link #itemsFor}: match, rank and label. */
    static List<Item> items(String query, List<TaskDef> tasks, File project) {
        List<Item> hits = new ArrayList<>();
        for (TaskDef task : tasks) {
            String command = NpmScriptSearchProvider.oneLine(VsCodeTasks.display(task));
            if (SearchTerms.matches(query, task.label(), command, task.group(), VOCABULARY)) {
                hits.add(new Item(task, project, label(task.label(), command)));
            }
        }
        // a hit on the task's own LABEL outranks one on its command or group,
        // term by term; then the matcher's ranking; then the label, so the
        // order never depends on the file's order
        List<String> terms = SearchTerms.terms(query);
        hits.sort(Comparator
                .comparingInt((Item i) -> -NpmScriptSearchProvider.nameRank(terms, i.task().label()))
                .thenComparingInt(i -> -SearchTerms.score(query, i.task().label(),
                        VsCodeTasks.display(i.task()), i.task().group(), VOCABULARY))
                .thenComparing(i -> i.task().label()));
        return hits;
    }

    /** {@code Run task: build — cargo build}, escaped for the HTML renderer, command clipped. */
    static String label(String taskLabel, String command) {
        String shown = NpmScriptSearchProvider.clip(command, MAX_COMMAND);
        String name = NpmScriptSearchProvider.escape(NpmScriptSearchProvider.oneLine(taskLabel));
        return shown.isEmpty()
                ? NbBundle.getMessage(VsCodeTaskSearchProvider.class, "VsCodeTaskSearchProvider_runBare", name)
                : NbBundle.getMessage(VsCodeTaskSearchProvider.class, "VsCodeTaskSearchProvider_run",
                        name, NpmScriptSearchProvider.escape(shown));
    }

    /**
     * Enter: the editor is read here, where Enter arrives (the event
     * thread); everything else — resolving, the trust question, the
     * file's own questions, the spawn — rides the lane, never the EDT.
     */
    static RequestProcessor.Task run(File project, TaskDef task) {
        EditorContext editor = editorProbe.get();
        return RP.post(() -> execute(project, task, editor));
    }

    /**
     * The body of Enter, on the lane: decide the whole run, refuse out
     * loud, or trust-gate, ask, and start.
     */
    static void execute(File project, TaskDef task, EditorContext editor) {
        Outcome outcome = VsCodeTaskPlan.check(task, VsCodeTasks.readFile(project), project, host,
                editor, System.getProperty("user.home", ""));
        if (outcome instanceof Refusal refusal) {
            statusSink.accept(refusal(task.label(), refusal));
            return;
        }
        Checked checked = (Checked) outcome;
        Prepared prepared;
        if (checked.questions().isEmpty()) {
            prepared = VsCodeTaskPlan.finish(checked, Map.of());
            if (prepared instanceof Refusal refusal) {
                statusSink.accept(refusal(task.label(), refusal));
                return;
            }
            // Workspace Trust BEFORE the first spawn, once for the whole
            // run: a task's command is the repository's own text. Keep
            // Safe spawns nothing and says nothing more — the user just
            // answered the question themselves.
            for (File folder : trustFolders(project, (Ready) prepared)) {
                if (!trustCheck.test(folder)) {
                    return;
                }
            }
        } else {
            // the file's questions are the repository's own text too, and
            // an answer may be a password: trust first, so a workspace
            // nobody trusted never asks for one
            if (!trustCheck.test(project)) {
                return;
            }
            Map<String, String> answers = new LinkedHashMap<>();
            for (InputDef question : checked.questions()) {
                Optional<String> answer = asker.apply(task.label(), question);
                if (answer.isEmpty()) {
                    break; // Cancel: finish() refuses, naming the question
                }
                answers.put(question.id(), answer.get());
            }
            prepared = VsCodeTaskPlan.finish(checked, answers);
            if (prepared instanceof Refusal refusal) {
                statusSink.accept(refusal(task.label(), refusal));
                return;
            }
        }
        new Chain(task.label(), project, (Ready) prepared).start();
    }

    /**
     * The folders Workspace Trust is asked about before a run: the one
     * folder every task runs from when there is one (an npm-type task's
     * own — the folder its lane will ask about, so the lane's ask is then
     * silent), else the project, whose grant covers every folder in it.
     */
    static Set<File> trustFolders(File project, Ready ready) {
        Set<File> folders = new LinkedHashSet<>();
        for (List<Step> stage : ready.stages()) {
            for (Step step : stage) {
                if (step.resolved() instanceof NpmLaunch npm) {
                    folders.add(npm.dir());
                } else if (step.runs()) {
                    folders.add(project);
                }
            }
        }
        return folders.size() <= 1 ? folders : Set.of(project);
    }

    /**
     * A run under way: its stages, started one after another on the lane.
     * The only place a task is started.
     */
    private static final class Chain {

        private final String root;
        private final File project;
        private final List<List<Step>> stages;
        /** The run's own entry among the live runs while it has more than one task to start, else null. */
        private final String runId;
        /** Set by the toolbar ■ (or the run's own row): no further stage starts. */
        private final AtomicBoolean stopped = new AtomicBoolean();

        Chain(String root, File project, Ready ready) {
            this.root = root;
            this.project = project;
            this.stages = ready.stages();
            this.runId = ready.running() > 1
                    ? "vscode-task-chain:" + project.getAbsolutePath() + "#" + RUN_SEQ.incrementAndGet()
                    : null;
        }

        void start() {
            if (runId != null) {
                // the ■ stops every live run; this entry is how the CHAIN
                // hears it — a task that exits zero on its TERM would
                // otherwise hand over to the next stage
                LiveRuns.add(new LiveRuns.Run(runId,
                        message("VsCodeTaskSearchProvider_chain", root, project.getName()),
                        () -> stopped.set(true)));
            }
            stage(0);
        }

        private void stage(int index) {
            if (index >= stages.size()) {
                end();
                return;
            }
            List<Step> steps = stages.get(index);
            List<CompletableFuture<Exit>> exits = new ArrayList<>();
            for (Step step : steps) {
                CompletableFuture<Exit> exit;
                try {
                    exit = begin(step);
                } catch (RuntimeException failed) {
                    exit = CompletableFuture.failedFuture(failed);
                }
                exits.add(exit.exceptionally(failed -> new Exit(-1, false)));
            }
            // the next stage is decided on the lane, never on a process's pump thread
            CompletableFuture.allOf(exits.toArray(CompletableFuture[]::new))
                    .whenCompleteAsync((done, never) -> after(index, steps, exits), RP);
        }

        private CompletableFuture<Exit> begin(Step step) {
            String label = step.task().label();
            if (step.resolved() instanceof Launch launch) {
                statusSink.accept(message("VsCodeTaskSearchProvider_running", label));
                return spawner.spawn(label, launch, project);
            }
            if (step.resolved() instanceof NpmLaunch npm) {
                statusSink.accept(message("VsCodeTaskSearchProvider_running", label));
                return npmRunner.apply(npm.dir(), npm.script()).thenApply(code -> new Exit(code, false));
            }
            return CompletableFuture.completedFuture(new Exit(0, false)); // a group: nothing of its own
        }

        private void after(int index, List<Step> steps, List<CompletableFuture<Exit>> exits) {
            if (index == stages.size() - 1) {
                end(); // the task itself: nothing waits for it, and its own exit is in its Output tab
                return;
            }
            String failed = null;
            int code = 0;
            boolean userStopped = stopped.get();
            for (int i = 0; i < steps.size(); i++) {
                Exit exit = exits.get(i).join();
                userStopped |= exit.stopped();
                if (exit.code() != 0 && failed == null) {
                    failed = steps.get(i).task().label();
                    code = exit.code();
                }
            }
            if (userStopped) {
                statusSink.accept(message("VsCodeTaskSearchProvider_chainStopped", root));
            } else if (failed == null) {
                stage(index + 1);
                return;
            } else if (code == -1) {
                statusSink.accept(message("VsCodeTaskSearchProvider_chainNotStarted", root, failed));
            } else if (code != NpmLaneRun.NOT_RUN) {
                // (NOT_RUN: the npm lane refused the script and said why itself)
                statusSink.accept(message("VsCodeTaskSearchProvider_chainFailed", root, failed,
                        String.valueOf(code)));
            }
            end();
        }

        private void end() {
            if (runId != null) {
                LiveRuns.remove(runId);
            }
        }
    }

    /** The sentence for a run that was refused: about the task itself, or naming the task it depends on first. */
    static String refusal(String taskLabel, Refusal refusal) {
        String why = refusal(refusal.task(), refusal.why());
        return refusal.task().equals(taskLabel) ? why
                : message("VsCodeTaskSearchProvider_refuseDependency", taskLabel, refusal.task()) + " " + why;
    }

    /** The refusal sentence for {@code refused}, in the reader's language. */
    static String refusal(String taskLabel, Refused refused) {
        return switch (refused.reason()) {
            case VARIABLE -> message("VsCodeTaskSearchProvider_refuseVariable", refused.detail());
            case NEEDS_FILE -> message("VsCodeTaskSearchProvider_refuseNeedsFile", taskLabel, refused.detail());
            case NEEDS_SELECTION -> message("VsCodeTaskSearchProvider_refuseNeedsSelection",
                    taskLabel, refused.detail());
            case SELECTION_TOO_LONG -> message("VsCodeTaskSearchProvider_refuseSelectionTooLong",
                    taskLabel, refused.detail(), VsCodeTasks.MAX_SELECTED_TEXT);
            case INPUT_UNDEFINED -> message("VsCodeTaskSearchProvider_refuseInputUndefined",
                    taskLabel, refused.detail());
            case INPUT_TYPE -> message("VsCodeTaskSearchProvider_refuseInputType",
                    taskLabel, refused.detail(), refused.extra().isEmpty() ? "?" : refused.extra());
            case INPUT_INCOMPLETE -> message("VsCodeTaskSearchProvider_refuseInputIncomplete",
                    taskLabel, refused.detail(), refused.extra());
            case INPUT_UNANSWERED -> message("VsCodeTaskSearchProvider_cancelled", taskLabel, refused.detail());
            case PASSWORD_SHOWN -> message("VsCodeTaskSearchProvider_refusePasswordShown",
                    taskLabel, refused.detail());
            case DEPENDENCY_MISSING -> message("VsCodeTaskSearchProvider_refuseDependencyMissing",
                    taskLabel, refused.detail());
            case DEPENDENCY_AMBIGUOUS -> message("VsCodeTaskSearchProvider_refuseDependencyAmbiguous",
                    taskLabel, refused.detail());
            case DEPENDENCY_OBJECT -> message("VsCodeTaskSearchProvider_refuseDependencyObject",
                    taskLabel, refused.detail());
            case DEPENDENCY_CYCLE -> message("VsCodeTaskSearchProvider_refuseDependencyCycle",
                    taskLabel, refused.detail());
            case DEPENDENCY_BACKGROUND -> message("VsCodeTaskSearchProvider_refuseDependencyBackground",
                    taskLabel, refused.detail());
            case CHAIN_TOO_LONG -> message("VsCodeTaskSearchProvider_refuseChainTooLong",
                    taskLabel, VsCodeTaskPlan.MAX_TASKS);
            case CWD_OUTSIDE -> message("VsCodeTaskSearchProvider_refuseCwdOutside", taskLabel, refused.detail());
            case CWD_MISSING -> message("VsCodeTaskSearchProvider_refuseCwdMissing", taskLabel, refused.detail());
            case TYPE -> message("VsCodeTaskSearchProvider_refuseType", taskLabel, refused.detail());
            case NO_COMMAND -> message("VsCodeTaskSearchProvider_refuseNoCommand", taskLabel);
            case SHELL_MISSING -> message("VsCodeTaskSearchProvider_refuseShellMissing", taskLabel, refused.detail());
            case SHELL_UNSUPPORTED -> message("VsCodeTaskSearchProvider_refuseShellUnsupported",
                    taskLabel, refused.detail());
        };
    }

    private static String message(String key, Object... args) {
        return NbBundle.getMessage(VsCodeTaskSearchProvider.class, key, args);
    }

    /**
     * The spawn: the IDE Run lane's shape (WebProjectActionProvider.launch)
     * for a task — a progress bar whose Cancel is this run's stop, an Output
     * tab named for the task, the toolbar ■ through {@link LiveRuns}, and a
     * printed local address announced until the process ends. The header
     * of the Output tab and the line the flight recorder keeps are {@link
     * Launch#shown} when the launch has one: the argv of a task that was
     * handed a password, or the editor's selection, is for the process
     * alone.
     */
    static CompletableFuture<Exit> launch(String taskLabel, Launch launch, File project) {
        String label = taskLabel + " — " + project.getName();
        String runId = "vscode-task:" + project.getAbsolutePath() + "#" + RUN_SEQ.incrementAndGet();
        CompletableFuture<Exit> done = new CompletableFuture<>();
        AtomicReference<String> announced = new AtomicReference<>();
        ProgressHandle ph = ProgressHandle.createHandle(label, () -> {
            LiveRuns.stop(runId);
            return true;
        });
        ph.start();
        CommandExecutor.showOutput(label);
        CommandExecutor.Handle handle = CommandExecutor.run(label, launch.dir(), launch.env(), launch.argv(),
                launch.shown(),
                line -> {
                    // a printed local address IS a server (v2.69.16), registered
                    // only once the process has said so (v1.93.0)
                    String url = ServeUrls.firstLocalUrl(line);
                    if (url != null && !url.equals(announced.get())) {
                        announced.set(url);
                        ServingRegistry.getDefault().register(new ServingRegistry.Serving(
                                runId, label, url, ServingRegistry.Kind.WEB, launch.dir()));
                    }
                },
                exit -> {
                    ph.finish();
                    LiveRuns.remove(runId);
                    if (announced.get() != null) {
                        ServingRegistry.getDefault().deregister(runId);
                    }
                    if (exit == -1) {
                        // the tool is not on PATH: CommandExecutor printed the
                        // friendly reason in the Output tab; the status line points there
                        statusSink.accept(message("VsCodeTaskSearchProvider_failedToStart", taskLabel));
                    }
                    // a stop through any surface — the ■, the row, this run's
                    // Cancel — is the user's, whatever code the process left with
                    done.complete(new Exit(exit, LiveRuns.wasStoppedByUser(runId)));
                });
        LiveRuns.add(new LiveRuns.Run(runId, label, handle::kill));
        return done;
    }

    private static void status(String message) {
        if (EventQueue.isDispatchThread()) {
            StatusDisplayer.getDefault().setStatusText(PlainStatus.text(message));
        } else {
            EventQueue.invokeLater(() -> StatusDisplayer.getDefault().setStatusText(PlainStatus.text(message)));
        }
    }
}
