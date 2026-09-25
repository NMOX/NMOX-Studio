package org.nmox.studio.application;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every action the product looks up by id exists (3.1.0).
 * {@code Actions.forID(category, id)} answers null for an id nobody
 * registered, and the caller's fallback runs as if nothing were wrong:
 * Project Studio's Terminal button asked for
 * {@code Tools/org.netbeans.modules.terminal.nodes.OpenInTerminalAction}
 * from 1.212.0 to 3.1.0 - the real one is
 * {@code Window/org.netbeans.modules.dlight.terminal.action.OpenInTerminalAction}
 * - so "a terminal in the project" opened in the IDE's own directory for
 * four hundred releases, with a comment above it describing the feature.
 *
 * <p>The population is every {@code Actions.forID} in the product's main
 * sources whose two arguments are string literals or {@code static final
 * String} constants of the same file; each must name an
 * {@code Actions/<category>/<id with dots as dashes>.instance} in the
 * assembled cluster.
 */
class ActionIdsResolveTest {

    private static final Path CLUSTER = Path.of("target", "nmoxstudio");
    private static final Pattern CALL = Pattern.compile("Actions\\.forID\\s*\\(\\s*([^,()]+?)\\s*,\\s*([^,()]+?)\\s*\\)");
    /** The Welcome's door helper, {@code actionLink(label, category, id)}, which looks the id up for you. */
    private static final Pattern LINK = Pattern.compile("actionLink\\([^;]*?,\\s*(\"[^\"]+\")\\s*,\\s*(\"[^\"]+\")\\s*\\)");
    /**
     * Quick Search's VS Code table, {@code cmd("VS Code title", category, id)},
     * each row resolved through {@code Actions::forID} (3.1.0).
     */
    private static final Pattern CMD = Pattern.compile("\\bcmd\\(\\s*\"[^\"]*\"\\s*,\\s*(\"[^\"]+\")\\s*,\\s*(\"[^\"]+\")\\s*\\)");
    /**
     * A resolver seam fed {@code Actions::forID} — Getting Started's Point an
     * Agent, {@code resolver.resolve(category, id)} (6th review: the method
     * reference was invisible to a census that counted only calls).
     */
    private static final Pattern RESOLVE = Pattern.compile("\\bresolver\\.resolve\\(\\s*([^,()]+?)\\s*,\\s*([^,()]+?)\\s*\\)");
    private static final Pattern CONSTANT = Pattern.compile("static\\s+final\\s+String\\s+(\\w+)\\s*=\\s*\"([^\"]*)\"");

    @Test
    @DisplayName("every Actions.forID the product calls names an action the cluster registers")
    void everyLookedUpActionExists() throws Exception {
        Set<String> registered = registeredActions();
        assertThat(registered).as("the census read the cluster's Actions folder").hasSizeGreaterThan(300);
        List<String> missing = new ArrayList<>();
        int calls = 0;
        try (Stream<Path> walk = Files.walk(Path.of(".."))) {
            for (Path p : walk.filter(x -> x.toString().endsWith(".java") && x.toString().replace('\\', '/').contains("/src/main/java/")
                    && !x.toString().replace('\\', '/').contains("/.claude/")).toList()) {
                String src = Files.readString(p);
                if (!src.contains("Actions.forID") && !src.contains("Actions::forID")
                        && !src.matches("(?s).*import\\s+static\\s+org\\.openide\\.awt\\.Actions\\.\\*.*")) {
                    continue;
                }
                Map<String, String> constants = new HashMap<>();
                Matcher c = CONSTANT.matcher(src);
                while (c.find()) {
                    constants.put(c.group(1), c.group(2));
                }
                List<String[]> pairs = new ArrayList<>();
                int callShapes = 0;
                for (Pattern shape : new Pattern[] {CALL, LINK, CMD, RESOLVE}) {
                    Matcher m = shape.matcher(src);
                    while (m.find()) {
                        pairs.add(new String[] {m.group(1), m.group(2)});
                        if (shape == CALL) {
                            callShapes++;
                        }
                    }
                }
                // every raw Actions.forID( is either read here with literal
                // arguments or counted: a call whose arguments are method calls
                // (t.category()) is not matched by CALL at all (5th review)
                int raw = src.split("Actions\\.forID\\s*\\(", -1).length - 1;
                // a method reference hands the lookup to a seam: counted and
                // pinned like any lookup read elsewhere, its pairs read
                // through the seam's own call shape (RESOLVE)
                int refs = src.split("Actions::forID", -1).length - 1;
                int unreadable = raw - callShapes + refs;
                int seamReads = 0;
                Matcher sr = RESOLVE.matcher(src);
                while (sr.find()) {
                    seamReads++;
                }
                if (refs > 0 && seamReads < refs) {
                    // a method reference whose seam's call is not read here
                    // would meet its pin while nothing checks its id (7th review)
                    missing.add(p.getFileName() + ": " + refs + " Actions::forID handed to a seam, "
                            + seamReads + " read as resolver.resolve(category, id)");
                }
                if (src.matches("(?s).*import\\s+static\\s+org\\.openide\\.awt\\.Actions\\.(forID|\\*)\\s*;.*")) {
                    missing.add(p.getFileName() + ": a static import hides forID from this census — write Actions.forID");
                }
                int computed = 0;
                for (String[] pair : pairs) {
                    String category = value(pair[0], constants);
                    String id = value(pair[1], constants);
                    if (category == null || id == null) {
                        // computed at run time — a loop, a parameter — is out of
                        // this census's reach (4th review: the Workbench's
                        // Terminal row looped over two ids, the first of which
                        // never existed); counted, and held to the file's pin
                        computed++;
                        continue;
                    }
                    calls++;
                    String file = "Actions/" + category + "/" + id.replace('.', '-') + ".instance";
                    if (!registered.contains(file)) {
                        missing.add(p.getFileName() + ": " + category + " / " + id);
                    } else if (hiddenOnOneOs(file)) {
                        missing.add(p.getFileName() + ": " + category + " / " + id + " is hidden on one OS, unblessed");
                    }
                }
                int allowed = HELPERS.getOrDefault(p.getFileName().toString(), 0);
                if (computed + unreadable != allowed) {
                    missing.add(p.getFileName() + ": " + (computed + unreadable)
                            + " lookup(s) the census cannot read (pinned " + allowed
                            + ") — write the id literally or as a constant");
                }
            }
        }
        assertThat(calls).as("the census found the product's lookups").isGreaterThanOrEqualTo(11);
        assertThat(missing).as("action ids that answer null, so a fallback runs in silence").isEmpty();
    }

    /** A path to an action's .instance file, written out whole in product source. */
    private static final Pattern INSTANCE_PATH = Pattern.compile("\"(Actions/[^\"]+\\.instance)\"");

    /** An action path BUILT from pieces, which the census above could not read. */
    private static final Pattern BUILT_PATH = Pattern.compile("\"Actions/[^\"]*\"\\s*\\+");

    @Test
    @DisplayName("every Actions/…/….instance path the product names is one the cluster registers")
    void everyNamedInstancePathExists() throws Exception {
        // the git chip reaches the git module's own actions by their config
        // path (Show Changes, Diff, Annotate, and 3.2.0's Switch Branch and
        // Commit); a renamed action would degrade to the Team-menu message
        // in silence, so each path is held to the assembled cluster
        Set<String> registered = registeredActions();
        List<String> missing = new ArrayList<>();
        int named = 0;
        try (Stream<Path> walk = Files.walk(Path.of(".."))) {
            for (Path p : walk.filter(x -> x.toString().endsWith(".java") && x.toString().replace('\\', '/').contains("/src/main/java/")
                    && !x.toString().replace('\\', '/').contains("/.claude/")).toList()) {
                String src = Files.readString(p);
                if (BUILT_PATH.matcher(src).find()) {
                    // a path this census cannot read is a path it cannot hold:
                    // write it whole (3.2 review)
                    missing.add(p.getFileName() + ": an Actions/ path built by concatenation");
                }
                Matcher m = INSTANCE_PATH.matcher(src);
                while (m.find()) {
                    named++;
                    if (!registered.contains(m.group(1))) {
                        missing.add(p.getFileName() + ": " + m.group(1));
                    } else if (hiddenOnOneOs(m.group(1))) {
                        missing.add(p.getFileName() + ": " + m.group(1) + " is hidden on one OS, unblessed");
                    }
                }
            }
        }
        assertThat(named).as("the census found the product's named instance paths").isGreaterThanOrEqualTo(5);
        assertThat(missing).as("instance paths no module registers").isEmpty();
    }

    private static String value(String expr, Map<String, String> constants) {
        expr = expr.strip();
        if (expr.startsWith("\"") && expr.endsWith("\"")) {
            return expr.substring(1, expr.length() - 1);
        }
        return constants.get(expr);
    }

    private static Set<String> registeredActions() throws Exception {
        Set<String> out = new HashSet<>();
        try (Stream<Path> jars = Files.walk(CLUSTER)) {
            for (Path jar : jars.filter(p -> p.toString().endsWith(".jar") && p.toString().contains("modules")).toList()) {
                try (JarFile jf = new JarFile(jar.toFile())) {
                    String declared = jf.getManifest() == null ? null
                            : jf.getManifest().getMainAttributes().getValue("OpenIDE-Module-Layer");
                    String requires = jf.getManifest() == null ? null
                            : jf.getManifest().getMainAttributes().getValue("OpenIDE-Module-Requires");
                    // a module that loads on one OS only (applemenu on macOS)
                    // masks nothing on the others, so its masks do not remove
                    // a path; they are recorded instead, and a path the
                    // product names that one OS hides must be blessed by name
                    boolean oneOs = requires != null && requires.contains("org.openide.modules.os.");
                    Set<String> into = oneOs ? new HashSet<>() : out;
                    for (String name : new String[] {declared, "META-INF/generated-layer.xml"}) {
                        ZipEntry e = name == null ? null : jf.getEntry(name);
                        if (e != null) {
                            collect(parse(jf.getInputStream(e).readAllBytes()), "", into);
                        }
                    }
                    if (oneOs) {
                        for (String f : into) {
                            if (f.endsWith("_hidden")) {
                                ONE_OS_MASKS.add(f.substring(0, f.length() - "_hidden".length()));
                            }
                        }
                        // and what it ADDS exists on that OS alone (applemenu's
                        // MinimizeWindowAction): not counted as registered (5th review)
                    }
                }
            }
        }
        // a layer that hides another's file (name.instance_hidden) removes
        // it from what the running IDE finds: the ui layer alone carries two
        // dozen, so a mask over a path the product names would otherwise
        // pass here and fall back in silence at run time (3.2 review)
        Set<String> masks = new HashSet<>();
        for (String f : out) {
            if (f.endsWith("_hidden")) {
                masks.add(f.substring(0, f.length() - "_hidden".length()));
            }
        }
        // a mask can hide a whole folder (Actions/Git_hidden): everything under it goes
        out.removeIf(f -> f.endsWith("_hidden") || masks.contains(f)
                || masks.stream().anyMatch(m -> f.startsWith(m + "/")));
        return out;
    }

    /**
     * How many {@code Actions.forID} calls in a file this census cannot read,
     * pinned per file with where their inputs are checked instead; any other
     * count, anywhere, fails.
     * <ul>
     * <li>VsCodeCommandSearchProvider: the resolver behind {@code cmd(...)} rows, each censused by CMD.</li>
     * <li>MainWindow: the resolver behind {@code actionLink(...)} doors (censused by LINK) and the
     *     Getting Started targets ({@code t.category(), t.id()}, held by GettingStartedTest).</li>
     * <li>DocsShots: the docs forge's {@code -Dnmox.shots.dialogs} spec, a build-time argument.</li>
     * </ul>
     */
    private static final Map<String, Integer> HELPERS = Map.of(
            "VsCodeCommandSearchProvider.java", 1,
            "MainWindow.java", 2,
            "DocsShots.java", 1,
            "PointAnAgentAction.java", 1);

    /** Paths a module that loads on one OS only hides there (filled by {@link #registeredActions}). */
    private static final Set<String> ONE_OS_MASKS = new HashSet<>();

    /**
     * Paths the product names that one OS hides, each with the reason that is
     * acceptable. The full-screen action: applemenu hides it on macOS, where
     * the window's own green button and ^⌘F do it, so Quick Search's "View:
     * Toggle Full Screen" row is absent on a Mac and present elsewhere.
     */
    private static final Set<String> BLESSED_ONE_OS = Set.of(
            "Actions/Window/org-netbeans-core-windows-actions-ToggleFullScreenAction.instance");

    /** Whether a path the product names is hidden on one OS without a blessing. */
    private static boolean hiddenOnOneOs(String path) {
        return !BLESSED_ONE_OS.contains(path)
                && (ONE_OS_MASKS.contains(path) || ONE_OS_MASKS.stream().anyMatch(m -> path.startsWith(m + "/")));
    }

    private static void collect(Element el, String path, Set<String> out) {
        for (Node n = el.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element c && ("folder".equals(c.getTagName()) || "file".equals(c.getTagName()))) {
                String p = path + c.getAttribute("name") + ("folder".equals(c.getTagName()) ? "/" : "");
                if ("file".equals(c.getTagName()) && p.startsWith("Actions/")) {
                    out.add(p);
                } else if ("folder".equals(c.getTagName()) && p.startsWith("Actions/")
                        && c.getAttribute("name").endsWith("_hidden")) {
                    // a mask written as a folder hides the folder all the same (5th review)
                    out.add(p.substring(0, p.length() - 1));
                }
                collect(c, p, out);
            }
        }
    }

    private static Element parse(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setValidating(false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setExpandEntityReferences(false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
    }
}
