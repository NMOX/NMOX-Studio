package org.nmox.studio.tools.vscode;

import java.awt.EventQueue;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
import org.nmox.studio.tools.npm.NpmService;
import org.nmox.studio.tools.npm.search.NpmScriptSearchProvider;
import org.nmox.studio.tools.vscode.VsCodeTasks.Launch;
import org.nmox.studio.tools.vscode.VsCodeTasks.NpmLaunch;
import org.nmox.studio.tools.vscode.VsCodeTasks.Os;
import org.nmox.studio.tools.vscode.VsCodeTasks.Refused;
import org.nmox.studio.tools.vscode.VsCodeTasks.Resolved;
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
 * category does.
 *
 * <p><b>Refusals speak.</b> A task that uses a variable only VS Code can
 * fill, depends on another task, names a working folder outside the
 * project, or has an extension's type, is still LISTED (so a user can see
 * we read their file) and on Enter says why on the status line, spawning
 * nothing and asking nothing.
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

    /** An npm-type task's hand-off, as a seam; the default is the trust-gated NPM Service lane. */
    static volatile BiConsumer<File, String> npmRunner = VsCodeTaskSearchProvider::runOnNpmLane;

    /** Where refusals and progress are said, as a seam. */
    static volatile Consumer<String> statusSink = VsCodeTaskSearchProvider::status;

    /** Starts one resolved task; completes with its exit code. */
    @FunctionalInterface
    interface Spawner {
        CompletableFuture<Integer> spawn(String taskLabel, Launch launch, File project);
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

    /** Enter: everything — resolving, the trust question, the spawn — rides the lane, never the EDT. */
    static RequestProcessor.Task run(File project, TaskDef task) {
        return RP.post(() -> execute(project, task));
    }

    /** The body of Enter, on the lane: resolve, refuse out loud, or trust-gate then spawn. */
    static void execute(File project, TaskDef task) {
        Resolved resolved = VsCodeTasks.resolve(task, project, Os.current(), System::getenv);
        if (resolved instanceof Refused refused) {
            statusSink.accept(refusal(task.label(), refused));
            return;
        }
        if (resolved instanceof NpmLaunch npm) {
            // the NPM Service lane asks Workspace Trust itself (v1.103.0)
            statusSink.accept(message("VsCodeTaskSearchProvider_running", task.label()));
            npmRunner.accept(npm.dir(), npm.script());
            return;
        }
        Launch launch = (Launch) resolved;
        // Workspace Trust BEFORE the spawn: the task's command is the
        // repository's own text. Keep Safe spawns nothing and says nothing
        // more — the user just answered the question themselves.
        if (!trustCheck.test(project)) {
            return;
        }
        statusSink.accept(message("VsCodeTaskSearchProvider_running", task.label()));
        spawner.spawn(task.label(), launch, project);
    }

    /** The refusal sentence for {@code refused}, in the reader's language. */
    static String refusal(String taskLabel, Refused refused) {
        return switch (refused.reason()) {
            case VARIABLE -> message("VsCodeTaskSearchProvider_refuseVariable", refused.detail());
            case DEPENDS_ON -> message("VsCodeTaskSearchProvider_refuseDependsOn", taskLabel, refused.detail());
            case CWD_OUTSIDE -> message("VsCodeTaskSearchProvider_refuseCwdOutside", taskLabel, refused.detail());
            case CWD_MISSING -> message("VsCodeTaskSearchProvider_refuseCwdMissing", taskLabel, refused.detail());
            case TYPE -> message("VsCodeTaskSearchProvider_refuseType", taskLabel, refused.detail());
            case NO_COMMAND -> message("VsCodeTaskSearchProvider_refuseNoCommand", taskLabel);
        };
    }

    private static String message(String key, Object... args) {
        return NbBundle.getMessage(VsCodeTaskSearchProvider.class, key, args);
    }

    /**
     * The spawn: the IDE Run lane's shape (WebProjectActionProvider.launch)
     * for a task — a progress bar whose Cancel is this run's stop, an Output
     * tab named for the task, the toolbar ■ through {@link LiveRuns}, and a
     * printed local address announced until the process ends.
     */
    static CompletableFuture<Integer> launch(String taskLabel, Launch launch, File project) {
        String label = taskLabel + " — " + project.getName();
        String runId = "vscode-task:" + project.getAbsolutePath() + "#" + RUN_SEQ.incrementAndGet();
        CompletableFuture<Integer> done = new CompletableFuture<>();
        AtomicReference<String> announced = new AtomicReference<>();
        ProgressHandle ph = ProgressHandle.createHandle(label, () -> {
            LiveRuns.stop(runId);
            return true;
        });
        ph.start();
        CommandExecutor.showOutput(label);
        CommandExecutor.Handle handle = CommandExecutor.run(label, launch.dir(), launch.env(), launch.argv(),
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
                    done.complete(exit);
                });
        LiveRuns.add(new LiveRuns.Run(runId, label, handle::kill));
        return done;
    }

    private static void runOnNpmLane(File dir, String script) {
        NpmService npm = NpmService.getDefault();
        npm.runScript(dir, script, npm.detectPackageManager(dir));
    }

    private static void status(String message) {
        String plain = PlainStatus.text(message);
        if (EventQueue.isDispatchThread()) {
            StatusDisplayer.getDefault().setStatusText(plain);
        } else {
            EventQueue.invokeLater(() -> StatusDisplayer.getDefault().setStatusText(plain));
        }
    }
}
