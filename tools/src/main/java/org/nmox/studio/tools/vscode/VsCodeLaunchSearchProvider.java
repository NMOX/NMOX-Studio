package org.nmox.studio.tools.vscode;

import java.awt.EventQueue;
import java.io.File;
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
import org.nmox.studio.tools.vscode.VsCodeLaunch.Config;
import org.nmox.studio.tools.vscode.VsCodeLaunch.DebugFile;
import org.nmox.studio.tools.vscode.VsCodeLaunch.DebugPage;
import org.nmox.studio.tools.vscode.VsCodeLaunch.Refused;
import org.nmox.studio.tools.vscode.VsCodeLaunch.Resolved;
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
 * <p><b>Refusals speak.</b> A configuration the debugger cannot start as
 * written — a field it cannot pass on, an attach, a type it has no adapter
 * for, a compound, a VS Code-only variable, a path outside the project —
 * is still LISTED and on Enter says why on the status line, starting
 * nothing and asking nothing.
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

    /** Enter: resolving, the trust question and the hand-off all ride the lane, never the EDT. */
    static RequestProcessor.Task run(File project, Config config) {
        return RP.post(() -> execute(project, config));
    }

    /** The body of Enter, on the lane: resolve, refuse out loud, or trust-gate then hand to the debugger. */
    static void execute(File project, Config config) {
        Resolved resolved = VsCodeLaunch.resolve(config, project, System::getenv);
        if (resolved instanceof Refused refused) {
            statusSink.accept(refusal(config.name(), refused));
            return;
        }
        DebugLauncher debugger = launcher.get();
        if (debugger == null) {
            // the editor module is absent: say so before asking any question
            statusSink.accept(message("VsCodeLaunchSearchProvider_noDebugger", config.name()));
            return;
        }
        // Workspace Trust BEFORE the hand-off: the program is the
        // repository's own code. Keep Safe starts nothing and says nothing
        // more — the user just answered the question themselves.
        if (!trustCheck.test(project)) {
            return;
        }
        boolean started = resolved instanceof DebugFile file
                ? debugger.debug(file.program(), file.cwd())
                : debugger.debugPage(((DebugPage) resolved).url(), ((DebugPage) resolved).webRoot());
        statusSink.accept(started
                ? message("VsCodeLaunchSearchProvider_starting", config.name())
                : message("VsCodeLaunchSearchProvider_noDebugger", config.name()));
    }

    /** The refusal sentence for {@code refused}, in the reader's language. */
    static String refusal(String name, Refused refused) {
        return switch (refused.reason()) {
            case TYPE -> message("VsCodeLaunchSearchProvider_refuseType", name, refused.detail());
            case REQUEST -> message("VsCodeLaunchSearchProvider_refuseRequest", name, refused.detail());
            case FIELDS -> message("VsCodeLaunchSearchProvider_refuseFields", name, refused.detail());
            case VARIABLE -> message("VsCodeLaunchSearchProvider_refuseVariable", name, refused.detail());
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
