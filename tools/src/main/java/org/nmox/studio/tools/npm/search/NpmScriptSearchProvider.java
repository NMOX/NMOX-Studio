package org.nmox.studio.tools.npm.search;

import java.awt.EventQueue;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.core.search.SearchTerms;
import org.nmox.studio.core.spi.ProjectAim;
import org.nmox.studio.rack.devices.ProjectInspector;
import org.nmox.studio.tools.npm.NpmService;
import org.nmox.studio.tools.npm.RunScriptAction;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * Quick Search over the aimed project's package.json scripts (v3.1.0,
 * dx-plan row 9): a web developer types "dev" or "test" into ⌘I and
 * expects {@code npm run dev}. Each script lists as
 * {@code Run script: dev — vite}; Enter runs it.
 *
 * <p><b>No new spawn site.</b> Enter hands the script to
 * {@link NpmService#runScript}, the lane NPM Explorer's double-click and
 * the package.json Run Script gesture already ride. That lane asks
 * Workspace Trust BEFORE the spawn, uses the project's own package
 * manager (corepack pin, then lockfile), joins {@code LiveRuns} so the
 * toolbar ■ stops it, and announces the server a dev script prints.
 * The spawn-site ledger therefore has nothing new to classify.
 *
 * <p><b>What it reads.</b> The script table comes from
 * {@link ProjectInspector#scripts}, the one package.json parse the rack
 * shares: bounded ({@code BoundedReads}) and cached by mtime, from the
 * project's Node lane directory — the same directory NPM Explorer shows,
 * so the two surfaces can never disagree about which package.json is
 * meant. The platform evaluates providers on its own RequestProcessor
 * ({@code CommandEvaluator.runEvaluation}, read from the RELEASE310
 * bytecode), so the read never touches the EDT.
 *
 * <p><b>Silence is correct here.</b> No aimed project, or no
 * package.json, adds nothing: an empty category is not a refusal.
 *
 * <p><b>Display names are HTML.</b> The platform's result renderer is an
 * {@code HtmlRenderer} label with {@code setHtml(true)}, so a script
 * command such as {@code echo <b>} would render as markup. Names and
 * commands are the project's text, not ours, so both are escaped.
 */
public class NpmScriptSearchProvider implements SearchProvider {

    /** Commands longer than this (in code points) are clipped with an ellipsis. */
    static final int MAX_COMMAND = 48;

    /**
     * The words a user reaches for besides the script's own: the verb,
     * the noun and the ecosystem. The detected package manager's own
     * command joins them per project, so "pnpm dev" finds the script too.
     */
    static final String VOCABULARY = "npm run script scripts";

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-quicksearch-npm-script", 1);

    /**
     * The run, as a seam: the default is the trust-gated NPM Service lane.
     * Tests replace it to prove Enter hands over exactly the script and
     * the directory it was listed from.
     */
    static volatile BiConsumer<File, String> runner = NpmScriptSearchProvider::runOnLane;

    /** One listed script: what it is, where it runs, and the label shown. */
    record Item(String name, String command, File dir, String label) {
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
            if (!response.addResult(() -> run(item.dir(), item.name()), item.label())) {
                return;
            }
        }
    }

    /**
     * The scripts of {@code project}'s Node lane that match {@code query},
     * best first. Empty when there is no project, no package.json, or no
     * scripts block.
     */
    static List<Item> itemsFor(String query, File project) {
        if (project == null || query == null || query.isBlank()) {
            return List.of();
        }
        File dir = ProjectInspector.kindDir(project, ProjectInspector.ProjectKind.NODE);
        if (!new File(dir, "package.json").isFile()) {
            return List.of();
        }
        Map<String, String> scripts = ProjectInspector.scripts(project);
        if (scripts.isEmpty()) {
            return List.of();
        }
        NpmService npm = NpmService.getDefault();
        String manager = npm == null ? "" : npm.getCommand(npm.detectPackageManager(dir));
        return items(query, scripts, manager, dir);
    }

    /** The pure half of {@link #itemsFor}: match, rank and label. */
    static List<Item> items(String query, Map<String, String> scripts, String manager, File dir) {
        String vocabulary = VOCABULARY + " " + manager;
        List<Item> hits = new ArrayList<>();
        for (Map.Entry<String, String> e : scripts.entrySet()) {
            String name = e.getKey();
            String command = oneLine(e.getValue());
            if (SearchTerms.matches(query, name, command, vocabulary)) {
                hits.add(new Item(name, command, dir, label(name, command)));
            }
        }
        // A hit on the script's NAME outranks a hit only on its command
        // ("dev" lists the dev script before a build whose command says
        // --mode dev), scored term by term so "pnpm dev" still ranks by
        // "dev"; then the matcher's own ranking; then the name, so the
        // order never depends on package.json's hash order.
        List<String> terms = SearchTerms.terms(query);
        hits.sort(Comparator
                .comparingInt((Item i) -> -nameRank(terms, i.name()))
                .thenComparingInt(i -> -SearchTerms.score(query, i.name(), i.command(), vocabulary))
                .thenComparing(Item::name));
        return hits;
    }

    /** How strongly the query's terms, one by one, name the script itself. */
    static int nameRank(List<String> terms, String name) {
        int rank = 0;
        for (String term : terms) {
            rank += SearchTerms.score(term, name);
        }
        return rank;
    }

    /** {@code Run script: dev — vite}, escaped for the HTML renderer, command clipped. */
    static String label(String name, String command) {
        String shown = clip(command, MAX_COMMAND);
        return shown.isEmpty()
                ? NbBundle.getMessage(NpmScriptSearchProvider.class,
                        "NpmScriptSearchProvider_runBare", escape(name))
                : NbBundle.getMessage(NpmScriptSearchProvider.class,
                        "NpmScriptSearchProvider_run", escape(name), escape(shown));
    }

    /** Folds every run of whitespace or control characters to one space. */
    static String oneLine(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        boolean gap = false;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isISOControl(cp)) {
                gap = out.length() > 0;
                continue;
            }
            if (gap) {
                out.append(' ');
                gap = false;
            }
            out.appendCodePoint(cp);
        }
        return out.toString();
    }

    /**
     * At most {@code max} code points, the last replaced by an ellipsis
     * when anything was cut. Counting code points, never UTF-16 units, so
     * a cut can never strand half of a surrogate pair (the v1.149.0 class).
     */
    static String clip(String s, int max) {
        if (s.codePointCount(0, s.length()) <= max) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, max - 1)) + "…";
    }

    /** The three characters the platform's HtmlRenderer would read as markup. */
    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Enter: say so on the status line and hand the script to the lane
     * off the EDT. The platform runs a result's action on the EDT, and
     * the lane's trust prompt marshals its own dialog, so the hop is safe.
     */
    static RequestProcessor.Task run(File dir, String script) {
        status(NbBundle.getMessage(RunScriptAction.class, "RunScriptAction_running", script));
        return RP.post(() -> runner.accept(dir, script));
    }

    private static void runOnLane(File dir, String script) {
        NpmService npm = NpmService.getDefault();
        npm.runScript(dir, script, npm.detectPackageManager(dir));
    }

    private static void status(String message) {
        if (EventQueue.isDispatchThread()) {
            StatusDisplayer.getDefault().setStatusText(org.nmox.studio.core.util.PlainStatus.text(message));
        } else {
            EventQueue.invokeLater(() -> StatusDisplayer.getDefault()
                    .setStatusText(org.nmox.studio.core.util.PlainStatus.text(message)));
        }
    }
}
