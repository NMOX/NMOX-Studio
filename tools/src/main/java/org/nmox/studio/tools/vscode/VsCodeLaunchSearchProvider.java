package org.nmox.studio.tools.vscode;

import java.awt.EventQueue;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;


import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.core.search.SearchTerms;
import org.nmox.studio.core.spi.DebugLauncher;
import org.nmox.studio.core.spi.ProjectAim;
import org.nmox.studio.core.util.PlainStatus;
import org.nmox.studio.rack.service.WorkspaceTrust;
import org.nmox.studio.tools.npm.search.NpmScriptSearchProvider;
import org.nmox.studio.tools.vscode.VsCodeLaunch.AttachNode;
import org.nmox.studio.tools.vscode.VsCodeLaunch.Config;
import org.nmox.studio.tools.vscode.VsCodeLaunch.DebugFile;
import org.nmox.studio.tools.vscode.VsCodeLaunch.DebugPage;
import org.nmox.studio.tools.vscode.VsCodeLaunch.Refused;
import org.nmox.studio.tools.vscode.VsCodeLaunch.Resolved;
import org.nmox.studio.tools.vscode.VsCodeTaskSearchProvider.RunEnd;
import org.nmox.studio.tools.vscode.VsCodeTasks.EditorContext;
import org.nmox.studio.tools.vscode.VsCodeTasks.TaskDef;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * Quick Search over the aimed project's {@code .vscode/launch.json}
 * (v3.1.0): a developer arriving from VS Code types the name of a debug
 * configuration into ⌘I (or ⇧⌘P) and expects it to debug. Each
 * configuration lists as {@code Debug: Launch server — ${workspaceFolder}/server.js};
 * Enter starts it. The shape is {@link VsCodeTaskSearchProvider}'s.
 *
 * <p><b>No new spawn site.</b> Enter hands the resolved file or page to
 * the editor's debugger through the {@link DebugLauncher} facade — the
 * same door Debug ▸ Debug File and the toolbar's bug button use, which
 * asks Workspace Trust itself before any adapter or browser is spawned.
 * This provider asks trust on the PROJECT first, so the one question names
 * the folder the launch.json came from rather than a subfolder, and a Keep
 * Safe answer reaches no launcher at all; the launcher's own question then
 * finds the project already trusted (a grant covers subfolders). Every
 * step rides this provider's lane, because the platform runs a result's
 * action on the EDT.
 *
 * <p><b>The file being looked at.</b> {@code ${file}} and its siblings
 * mean the file the editor shows, and the editor's state is read when
 * Enter is pressed — on the calling thread, before the lane — because that
 * is the moment the user means by "this file". It is read by the rule a
 * task's variables use ({@link VsCodeTaskEditor}), through {@link
 * #editorProbe}.
 *
 * <p><b>{@code preLaunchTask}.</b> A configuration that names a task of
 * {@code .vscode/tasks.json} has it run first, as Enter on that task would
 * run it — its dependencies, its questions, its Output tab — and the
 * debugger starts when the task has exited zero. A task that fails, is
 * stopped or is refused starts no debugger. A background task (a
 * watcher) is waited for as VS Code waits: until its problem matcher's
 * {@code background} block says a cycle has ended — the watcher then
 * runs on beside the debugger, and the toolbar ■ stops it; one with no
 * such matcher is refused by name, because nothing could say when it is
 * ready.
 *
 * <p><b>Refusals speak.</b> A configuration the debugger cannot start as
 * written — a field it cannot pass on, an attach to another machine, a
 * type it has no adapter for, a compound, a VS Code-only variable, a path
 * outside the project, an env file that is missing — is still LISTED and
 * on Enter says why on the status line, starting nothing and asking
 * nothing. A refusal about an env file names the file and a line number;
 * no variable's value is ever said.
 */
public class VsCodeLaunchSearchProvider implements SearchProvider {

    /** Programs and addresses longer than this (in code points) are clipped with an ellipsis. */
    static final int MAX_TARGET = NpmScriptSearchProvider.MAX_COMMAND;

    /** The words a user reaches for besides the configuration's own. */
    static final String VOCABULARY = "debug launch vscode";

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-quicksearch-vscode-launch", 1);

    /** The trust question, as a seam: Keep Safe must reach no launcher (tested). */
    static volatile Predicate<File> trustCheck = dir -> WorkspaceTrust.requestTrust(dir);

    /** The debugger, as a seam: tests prove Enter hands over exactly the resolved file or page. */
    static volatile Supplier<DebugLauncher> launcher = DebugLauncher::find;

    /** Where refusals and progress are said, as a seam. */
    static volatile Consumer<String> statusSink = VsCodeLaunchSearchProvider::status;

    /**
     * The editor when Enter is pressed, as a seam: {@code ${file}} is its
     * file and no other, and a {@code preLaunchTask} is handed the same
     * editor a task run from Quick Search would be. One rule for which
     * editor that is ({@link VsCodeTaskEditor}), so a task and a launch
     * configuration never name different files.
     */
    static volatile Supplier<EditorContext> editorProbe = VsCodeTaskEditor::snapshot;

    /**
     * Runs a configuration's {@code preLaunchTask} and reports how it ended
     * (a seam; the real one is the task provider's own Enter, with its
     * refusals, its trust question and its Output tab).
     */
    interface TaskRunner {
        void run(File project, TaskDef task, EditorContext editor, Consumer<RunEnd> then);
    }

    static volatile TaskRunner taskRunner = VsCodeTaskSearchProvider::executeThen;

    /** One listed configuration: its definition, the project it belongs to, and the label shown. */
    record Item(Config config, File project, String label) {
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
            if (!response.addResult(() -> run(item.project(), item.config()), item.label())) {
                return;
            }
        }
    }

    /** The configurations of {@code project} that match {@code query}, best first; empty without a launch.json. */
    static List<Item> itemsFor(String query, File project) {
        if (project == null || query == null || query.isBlank()) {
            return List.of();
        }
        return items(query, VsCodeLaunch.read(project), project);
    }

    /** The pure half of {@link #itemsFor}: match, rank and label. */
    static List<Item> items(String query, List<Config> configs, File project) {
        List<Item> hits = new ArrayList<>();
        for (Config config : configs) {
            String target = NpmScriptSearchProvider.oneLine(VsCodeLaunch.display(config));
            if (SearchTerms.matches(query, config.name(), target, config.type(), VOCABULARY)) {
                hits.add(new Item(config, project, label(config.name(), target)));
            }
        }
        List<String> terms = SearchTerms.terms(query);
        hits.sort(Comparator
                .comparingInt((Item i) -> -NpmScriptSearchProvider.nameRank(terms, i.config().name()))
                .thenComparingInt(i -> -SearchTerms.score(query, i.config().name(),
                        VsCodeLaunch.display(i.config()), i.config().type(), VOCABULARY))
                .thenComparing(i -> i.config().name()));
        return hits;
    }

    /** {@code Debug: Launch — server.js}, escaped for the HTML renderer, target clipped. */
    static String label(String name, String target) {
        String shown = NpmScriptSearchProvider.clip(target, MAX_TARGET);
        String escapedName = NpmScriptSearchProvider.escape(NpmScriptSearchProvider.oneLine(name));
        return shown.isEmpty()
                ? message("VsCodeLaunchSearchProvider_debugBare", escapedName)
                : message("VsCodeLaunchSearchProvider_debug", escapedName,
                        NpmScriptSearchProvider.escape(shown));
    }

    /**
     * Enter: resolving, the trust question and the hand-off all ride the
     * lane, never the EDT. The one thing read here, on the caller's thread,
     * is which file the editor shows — the answer at the moment of the
     * keypress, not whenever the lane gets to it.
     */
    static RequestProcessor.Task run(File project, Config config) {
        return run(project, config, editorProbe.get());
    }

    /** {@link #run(File, Config)} for a caller that read the editor itself, before its own dialog took the focus. */
    static RequestProcessor.Task run(File project, Config config, EditorContext editor) {
        return RP.post(() -> execute(project, config, editor));
    }

    /**
     * The body of Enter, on the lane: resolve, refuse out loud, or
     * trust-gate, run the configuration's {@code preLaunchTask} when it
     * names one, and hand to the debugger.
     *
     * <p><b>A program the task builds.</b> {@code "program":
     * "${workspaceFolder}/dist/server.js"} with {@code "preLaunchTask":
     * "build"} names a file that may not exist until the task has run, so
     * with a task to run a missing path is not a refusal yet: the
     * configuration is resolved again once the task has ended, and what is
     * handed on is what that second look found.
     */
    static void execute(File project, Config config, EditorContext editor) {
        Path file = editor.file();
        Resolved resolved = VsCodeLaunch.resolve(config, project, System::getenv, file, true);
        String preLaunch = VsCodeLaunch.preLaunchTask(config);
        boolean builtLater = preLaunch != null && resolved instanceof Refused refused
                && refused.reason() == VsCodeLaunch.Reason.MISSING;
        if (resolved instanceof Refused refused && !builtLater) {
            statusSink.accept(refusal(config.name(), refused));
            return;
        }
        DebugLauncher debugger = launcher.get();
        if (debugger == null) {
            // the editor module is absent: say so before asking any question
            statusSink.accept(message("VsCodeLaunchSearchProvider_noDebugger", config.name()));
            return;
        }
        TaskDef task = null;
        if (preLaunch != null) {
            List<TaskDef> named = VsCodeTasks.readFile(project).tasks().stream()
                    .filter(t -> t.label().equals(preLaunch)).toList();
            if (named.size() != 1) {
                statusSink.accept(message(named.isEmpty()
                        ? "VsCodeLaunchSearchProvider_refusePreLaunchMissing"
                        : "VsCodeLaunchSearchProvider_refusePreLaunchAmbiguous", config.name(), preLaunch));
                return;
            }
            task = named.get(0);
            if (task.background() && !VsCodeProblemMatchers.read(task.problemMatchers()).watching()) {
                // VS Code waits for a background task's problem matcher to
                // say "ready"; this one has none that could, and waiting
                // for the task to EXIT would wait for a watcher forever
                statusSink.accept(message("VsCodeLaunchSearchProvider_refusePreLaunchBackground",
                        config.name(), preLaunch));
                return;
            }
        }
        // Workspace Trust BEFORE the task and the hand-off: both are the
        // repository's own code. Keep Safe starts nothing and says nothing
        // more — the user just answered the question themselves.
        if (!trustCheck.test(project)) {
            return;
        }
        if (task == null) {
            handOver(project, config, resolved, debugger);
            return;
        }
        statusSink.accept(message("VsCodeLaunchSearchProvider_preLaunchRunning", config.name(), preLaunch));
        taskRunner.run(project, task, editor, end -> {
            switch (end) {
                case DONE -> {
                    Resolved built = VsCodeLaunch.resolve(config, project, System::getenv, file, true);
                    if (built instanceof Refused refused) {
                        statusSink.accept(refusal(config.name(), refused));
                    } else {
                        handOver(project, config, built, debugger);
                    }
                }
                case FAILED -> statusSink.accept(message("VsCodeLaunchSearchProvider_preLaunchFailed",
                        config.name(), preLaunch));
                case STOPPED -> statusSink.accept(message("VsCodeLaunchSearchProvider_preLaunchStopped",
                        config.name(), preLaunch));
                case NOT_STARTED -> {
                    // the task's own refusal is on the status line (or Keep Safe was answered): nothing to add
                }
            }
        });
    }

    private static void handOver(File project, Config config, Resolved resolved, DebugLauncher debugger) {
        boolean started;
        if (resolved instanceof DebugFile program) {
            started = debugger.debug(new DebugLauncher.Launch(
                    program.kind() == VsCodeLaunch.Kind.PYTHON
                            ? DebugLauncher.Language.PYTHON : DebugLauncher.Language.NODE,
                    config.name(), program.program(), program.cwd(), project,
                    program.args(), program.env(), program.runtime(), program.runtimeArgs()));
        } else if (resolved instanceof AttachNode node) {
            started = debugger.attachNode(config.name(), node.address(), node.port(), node.cwd(), project);
        } else {
            started = debugger.debugPage(((DebugPage) resolved).url(), ((DebugPage) resolved).webRoot());
        }
        statusSink.accept(started
                ? message(resolved instanceof AttachNode
                        ? "VsCodeLaunchSearchProvider_attaching" : "VsCodeLaunchSearchProvider_starting",
                        config.name())
                : message("VsCodeLaunchSearchProvider_noDebugger", config.name()));
    }

    /** The refusal sentence for {@code refused}, in the reader's language. */
    static String refusal(String name, Refused refused) {
        return switch (refused.reason()) {
            case TYPE -> message("VsCodeLaunchSearchProvider_refuseType", name, refused.detail());
            case REQUEST -> message("VsCodeLaunchSearchProvider_refuseRequest", name, refused.detail());
            case FIELDS -> message("VsCodeLaunchSearchProvider_refuseFields", name, refused.detail());
            case VARIABLE -> message("VsCodeLaunchSearchProvider_refuseVariable", name, refused.detail());
            case NO_FILE -> message("VsCodeLaunchSearchProvider_refuseNoFile", name, refused.detail());
            case UNREADABLE -> message("VsCodeLaunchSearchProvider_refuseUnreadable", name, refused.detail());
            case ENV_LINE -> message("VsCodeLaunchSearchProvider_refuseEnvLine", name, refused.detail());
            case ADDRESS -> message("VsCodeLaunchSearchProvider_refuseAddress", name, refused.detail());
            case COMPOUND -> message("VsCodeLaunchSearchProvider_refuseCompound", name);
            case NO_TARGET -> message("VsCodeLaunchSearchProvider_refuseNoTarget", name);
            case OUTSIDE -> message("VsCodeLaunchSearchProvider_refuseOutside", name, refused.detail());
            case MISSING -> message("VsCodeLaunchSearchProvider_refuseMissing", name, refused.detail());
            case PROGRAM_KIND -> message("VsCodeLaunchSearchProvider_refuseProgramKind", name, refused.detail());
            case URL -> message("VsCodeLaunchSearchProvider_refuseUrl", name, refused.detail());
        };
    }

    private static String message(String key, Object... args) {
        return NbBundle.getMessage(VsCodeLaunchSearchProvider.class, key, args);
    }

    private static void status(String message) {
        if (EventQueue.isDispatchThread()) {
            StatusDisplayer.getDefault().setStatusText(PlainStatus.text(message));
        } else {
            EventQueue.invokeLater(() -> StatusDisplayer.getDefault().setStatusText(PlainStatus.text(message)));
        }
    }
}
