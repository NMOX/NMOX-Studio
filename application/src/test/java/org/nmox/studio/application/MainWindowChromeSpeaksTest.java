package org.nmox.studio.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first window a translated build shows speaks the reader's language.
 *
 * <p>A German walk of 3.2 (a text file open in the editor) read four English
 * strings on the main window itself, in a product whose localization arc is
 * recorded as closed: the editor's {@code Source} and {@code History} tabs,
 * the {@code Favorites} tab and the Quick Search hint. Every gate was green,
 * because none of them looks where these are declared:
 *
 * <ul>
 *   <li>an editor's tabs are {@code Editors/<mime>/MultiView} registrations,
 *       one per file type, each naming its OWN bundle key — the text loader's
 *       {@code CTL_Source} paints every file with no DataObject of its own,
 *       which is every TextMate-grammar language this product adds, and the
 *       local-history module adds {@code History} beside every one of them;
 *   <li>Quick Search's categories are {@code QuickSearch/<name>} folders with
 *       a localizing bundle, again one per module;
 *   <li>the Favorites tab, the Quick Search field and the progress and editor
 *       parts of the status line are named in platform CODE, where no layer
 *       says anything. The Window menu read {@code Favoriten} while the tab
 *       read {@code Favorites}: the row paints {@code ACT_View} and the tab
 *       paints {@code Favorites} (v2.143.0: translating the key a surface
 *       declares is not translating the key it paints).
 * </ul>
 *
 * <p>So the first two populations are DERIVED from the assembled cluster's
 * layers — a platform upgrade or a new language module that adds an editor
 * tab or a search category fails here by name — and the third is a checked
 * ledger ({@code main-window-chrome.txt}), the {@code PopupRowsSpeakTest}
 * shape: every recorded key must still exist saying exactly what the ledger
 * records, and every one must be overlaid in every shipped language.
 *
 * <p>The editor's toolbar (Back, Forward, Find Next, the bookmarks, Shift
 * Line, Comment, and the SQL and XML buttons) painted every tooltip in
 * English. Which buttons exist is DERIVED from every
 * {@code Editors/**&#47;Toolbars/Default} registration; a button whose action
 * the layer registers with a {@code ShortDescription}
 * ({@code Editors/**&#47;Actions/<name>.instance}) has its key derived too, and
 * every other button must have rows in a second checked ledger
 * ({@code editor-toolbar.txt}), read from the running toolbar and the
 * bytecode of its action. A button the layers add that neither the layer nor
 * the ledger names fails here by name.
 *
 * <p>A platform key is translated by a branding overlay
 * ({@code branding/src/main/nbm-branding/modules/<jar>/<pkg>/Bundle_<lang>});
 * a key in one of OUR jars must carry {@code Bundle_<lang>} in that jar.
 */
class MainWindowChromeSpeaksTest {

    private static final Path CLUSTER = Path.of("target", "nmoxstudio");
    private static final Path OVERLAYS = Path.of("..", "branding", "src", "main",
            "nbm-branding", "modules");

    private static final List<String> LANGS = ShippedLocales.TRANSLATED;

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+\\}");

    /** One key a surface paints: which jar ships it, where, and what it says in English. */
    private record Row(String jar, String pkg, String key, String english) {

        boolean ours() {
            return jar.startsWith("org-nmox-");
        }

        boolean hasMnemonic() {
            String e = english.replace("&lt;", "").replace("&gt;", "").replace("&amp;", "");
            int i = e.indexOf('&');
            return i >= 0 && i < e.length() - 1;
        }
    }

    /** Every base bundle in the cluster, by package: jar name → properties. */
    private static Map<String, Map<String, Properties>> bundles;
    /** layer displayName specs ({@code pkg.Bundle#key}) found under Editors/**&#47;MultiView. */
    private static Set<String> multiViewSpecs;
    /** QuickSearch/&lt;name&gt; → the localizing bundle package that names it. */
    private static Map<String, String> quickSearchCategories;
    /** Every editor-toolbar button the layers register (the layer file's name), separators excepted. */
    private static Set<String> toolbarItems;
    /** Action name to the ShortDescription specs its {@code Editors/**&#47;Actions/<name>.instance} declares. */
    private static Map<String, Set<String>> actionShortDescriptions;
    /** Layers the parser refused although they speak about a surface this test reads. */
    private static List<String> unreadable;

    private static synchronized void scan() throws IOException {
        if (bundles != null) {
            return;
        }
        assertThat(CLUSTER).as("the assembled cluster (mvn package first)").exists();
        Map<String, Map<String, Properties>> found = new LinkedHashMap<>();
        Set<String> mv = new TreeSet<>();
        Map<String, String> qs = new LinkedHashMap<>();
        Set<String> tb = new TreeSet<>();
        Map<String, Set<String>> sd = new LinkedHashMap<>();
        List<String> refused = new ArrayList<>();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException unsupported) {
            throw new IOException(unsupported);
        }
        try (Stream<Path> all = Files.walk(CLUSTER)) {
            for (Path jar : all.filter(p -> p.toString().endsWith(".jar")
                    && !p.getParent().getFileName().toString().equals("locale")).toList()) {
                String jarName = jar.getFileName().toString();
                try (JarFile jf = new JarFile(jar.toFile())) {
                    for (JarEntry e : jf.stream().toList()) {
                        String n = e.getName();
                        if (n.endsWith("/Bundle.properties")) {
                            Properties p = new Properties();
                            try (InputStream in = jf.getInputStream(e)) {
                                p.load(in);
                            }
                            found.computeIfAbsent(n.substring(0, n.lastIndexOf('/')),
                                    k -> new LinkedHashMap<>()).putIfAbsent(jarName, p);
                        } else if (n.endsWith("layer.xml")) {
                            byte[] raw;
                            try (InputStream in = jf.getInputStream(e)) {
                                raw = in.readAllBytes();
                            }
                            org.w3c.dom.Document doc;
                            try {
                                doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(raw));
                            } catch (Exception malformed) {
                                String text = new String(raw, StandardCharsets.UTF_8);
                                if (text.contains("MultiView") || text.contains("\"QuickSearch\"")
                                        || text.contains("\"Toolbars\"")) {
                                    refused.add(jarName + "!" + n);
                                }
                                continue;
                            }
                            walk(doc.getDocumentElement(), new ArrayList<>(), mv, qs, tb, sd);
                        }
                    }
                } catch (IOException unreadableJar) {
                    // a jar the JDK cannot open contributes no surface
                }
            }
        }
        bundles = found;
        multiViewSpecs = mv;
        quickSearchCategories = qs;
        toolbarItems = tb;
        actionShortDescriptions = sd;
        unreadable = refused;
    }

    private static void walk(Element folder, List<String> path, Set<String> mv, Map<String, String> qs,
            Set<String> tb, Map<String, Set<String>> sd) {
        for (Node c = folder.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (!(c instanceof Element el)) {
                continue;
            }
            String name = el.getAttribute("name");
            if ("folder".equals(el.getTagName())) {
                List<String> next = new ArrayList<>(path);
                next.add(name);
                if (path.size() == 1 && "QuickSearch".equals(path.get(0))) {
                    String bundle = attr(el, "SystemFileSystem.localizingBundle");
                    if (bundle != null) {
                        qs.put(name, bundle);
                    }
                }
                walk(el, next, mv, qs, tb, sd);
            } else if ("file".equals(el.getTagName()) && !path.isEmpty()
                    && "Editors".equals(path.get(0)) && path.contains("MultiView")
                    && !name.endsWith("_hidden")) {
                String spec = attr(el, "displayName");
                if (spec != null && spec.contains("#")) {
                    mv.add(spec);
                }
            } else if ("file".equals(el.getTagName()) && path.size() >= 3 && "Editors".equals(path.get(0))
                    && "Toolbars".equals(path.get(path.size() - 2)) && "Default".equals(path.get(path.size() - 1))
                    && !name.endsWith("_hidden")
                    && !"javax.swing.JSeparator".equals(attr(el, "instanceClass"))) {
                tb.add(name);
            } else if ("file".equals(el.getTagName()) && path.size() >= 2 && "Editors".equals(path.get(0))
                    && "Actions".equals(path.get(path.size() - 1)) && name.endsWith(".instance")) {
                String spec = attr(el, "ShortDescription");
                if (spec != null && spec.contains("#")) {
                    sd.computeIfAbsent(name.substring(0, name.length() - ".instance".length()),
                            k -> new TreeSet<>()).add(spec);
                }
            }
        }
    }

    private static String attr(Element owner, String name) {
        for (Node c = owner.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof Element a && "attr".equals(a.getTagName()) && name.equals(a.getAttribute("name"))) {
                for (String kind : List.of("bundlevalue", "stringvalue")) {
                    if (a.hasAttribute(kind)) {
                        return a.getAttribute(kind);
                    }
                }
            }
        }
        return null;
    }

    /** {@code org.foo.Bundle#key} → the row the cluster ships for it, or null. */
    private static Row resolve(String bundleClass, String key) {
        String pkg = bundleClass.endsWith(".Bundle")
                ? bundleClass.substring(0, bundleClass.length() - ".Bundle".length())
                : bundleClass;
        pkg = pkg.replace('.', '/');
        Map<String, Properties> byJar = bundles.getOrDefault(pkg, Map.of());
        for (Map.Entry<String, Properties> e : byJar.entrySet()) {
            String v = e.getValue().getProperty(key);
            if (v != null) {
                return new Row(e.getKey(), pkg, key, v.trim());
            }
        }
        return null;
    }

    private static List<Row> multiViewRows() throws IOException {
        scan();
        List<Row> rows = new ArrayList<>();
        for (String spec : multiViewSpecs) {
            int hash = spec.indexOf('#');
            Row r = resolve(spec.substring(0, hash), spec.substring(hash + 1));
            rows.add(r != null ? r : new Row("?", spec.substring(0, hash), spec.substring(hash + 1), null));
        }
        return rows;
    }

    private static List<Row> quickSearchRows() throws IOException {
        scan();
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, String> e : quickSearchCategories.entrySet()) {
            String key = "QuickSearch/" + e.getKey();
            Row r = resolve(e.getValue(), key);
            rows.add(r != null ? r : new Row("?", e.getValue(), key, null));
        }
        return rows;
    }

    /**
     * The editor-toolbar buttons whose key the layer states: a button named like an
     * action {@code Editors/**&#47;Actions/<name>.instance} registers with a
     * {@code ShortDescription}. The toolbar paints that SHORT_DESCRIPTION
     * (NbEditorToolBar: {@code JToolBar.add(Action)}, the shortcut appended).
     */
    private static Map<String, List<Row>> toolbarDerived() throws IOException {
        scan();
        Map<String, List<Row>> byItem = new LinkedHashMap<>();
        for (String item : toolbarItems) {
            Set<String> specs = actionShortDescriptions.get(item);
            if (specs == null) {
                continue;
            }
            for (String spec : specs) {
                int hash = spec.indexOf('#');
                Row r = resolve(spec.substring(0, hash), spec.substring(hash + 1));
                byItem.computeIfAbsent(item, k -> new ArrayList<>()).add(
                        r != null ? r : new Row("?", spec.substring(0, hash), spec.substring(hash + 1), null));
            }
        }
        return byItem;
    }

    /** The editor-toolbar ledger: toolbar item to the rows its button can paint. */
    private static Map<String, List<Row>> toolbarLedger() {
        Map<String, List<Row>> byItem = new LinkedHashMap<>();
        for (String[] p : ledgerLines("editor-toolbar.txt", 5)) {
            byItem.computeIfAbsent(p[0], k -> new ArrayList<>())
                    .add(new Row(p[1], p[2], p[3], p[4].replace("\\n", "\n")));
        }
        return byItem;
    }

    private static List<Row> toolbarRows() throws IOException {
        List<Row> rows = new ArrayList<>();
        toolbarDerived().values().forEach(rows::addAll);
        toolbarLedger().values().forEach(rows::addAll);
        return rows;
    }

    private static List<Row> ledger() {
        List<Row> rows = new ArrayList<>();
        for (String[] p : ledgerLines("main-window-chrome.txt", 4)) {
            rows.add(new Row(p[0], p[1], p[2], p[3].replace("\\n", "\n")));
        }
        return rows;
    }

    private static List<String[]> ledgerLines(String resource, int fields) {
        List<String[]> rows = new ArrayList<>();
        try (InputStream in = MainWindowChromeSpeaksTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("the ledger " + resource).isNotNull();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").split("\n")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\|", fields);
                assertThat(p).as("ledger line: " + line).hasSize(fields);
                rows.add(p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }

    /** The translated value a reader of {@code lang} meets for this row, or null. */
    private static String translated(Row row, String lang) throws IOException {
        if (row.ours()) {
            Path jar;
            try (Stream<Path> all = Files.walk(CLUSTER)) {
                jar = all.filter(p -> p.getFileName().toString().equals(row.jar())).findFirst().orElse(null);
            }
            if (jar == null) {
                return null;
            }
            try (JarFile jf = new JarFile(jar.toFile())) {
                JarEntry e = jf.getJarEntry(row.pkg() + "/Bundle_" + lang + ".properties");
                if (e == null) {
                    return null;
                }
                Properties p = new Properties();
                try (InputStream in = jf.getInputStream(e)) {
                    p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
                }
                return p.getProperty(row.key());
            }
        }
        Path file = OVERLAYS.resolve(row.jar()).resolve(row.pkg()).resolve("Bundle_" + lang + ".properties");
        if (!Files.exists(file)) {
            return null;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        }
        return p.getProperty(row.key());
    }

    private static List<String> speaks(List<Row> rows) throws IOException {
        List<String> problems = new ArrayList<>();
        for (Row row : rows) {
            if (row.english() == null) {
                problems.add(row.pkg() + "#" + row.key() + ": declared by a layer but no shipped "
                        + "bundle defines it, so the surface paints the raw key");
                continue;
            }
            for (String lang : LANGS) {
                String v = translated(row, lang);
                if (v == null) {
                    problems.add(row.key() + " [" + lang + "] (" + row.jar() + "/" + row.pkg()
                            + "): paints \"" + row.english() + "\" in English");
                } else if (v.isBlank()) {
                    problems.add(row.key() + " [" + lang + "]: overlaid with nothing");
                }
            }
        }
        return problems;
    }

    private static List<String> wellFormed(List<Row> rows) throws IOException {
        List<String> problems = new ArrayList<>();
        for (Row row : rows) {
            if (row.english() == null) {
                continue;
            }
            Set<String> expected = new LinkedHashSet<>();
            for (Matcher m = PLACEHOLDER.matcher(row.english()); m.find(); ) {
                expected.add(m.group());
            }
            for (String lang : LANGS) {
                String v = translated(row, lang);
                if (v == null) {
                    continue;   // the speaks law owns that verdict
                }
                if (v.indexOf('\'') >= 0) {
                    problems.add(lang + " " + row.key() + ": a straight apostrophe opens a "
                            + "MessageFormat quote; use ’");
                }
                String bare = v.replace("&lt;", "").replace("&gt;", "").replace("&amp;", "");
                int amp = bare.indexOf('&');
                if (amp >= 0 && amp < bare.length() - 1 && !row.hasMnemonic()) {
                    problems.add(lang + " " + row.key() + ": English assigns no mnemonic and "
                            + "this invents one (\"" + v + "\")");
                }
                Set<String> got = new LinkedHashSet<>();
                for (Matcher m = PLACEHOLDER.matcher(v); m.find(); ) {
                    got.add(m.group());
                }
                if (!got.equals(expected)) {
                    problems.add(lang + " " + row.key() + ": placeholders " + got
                            + " where English carries " + expected);
                }
            }
        }
        return problems;
    }

    @Test
    @DisplayName("the editor-tab population is derived from the layers and includes the tabs a plain file shows")
    void theTabPopulationIsDerived() throws IOException {
        List<Row> rows = multiViewRows();
        assertThat(unreadable).as("layers this test could not parse although they name a "
                + "MultiView or QuickSearch surface").isEmpty();
        assertThat(rows).as("editor tab names declared in the cluster's layers").hasSizeGreaterThan(20);
        assertThat(rows).as("the text loader's Source tab (every file with no DataObject of its own)")
                .anyMatch(r -> r.pkg().equals("org/openide/loaders") && r.key().equals("CTL_Source"));
        assertThat(rows).as("local history's History tab (beside every editor)")
                .anyMatch(r -> r.pkg().equals("org/netbeans/modules/versioning/ui/history")
                        && r.key().equals("CTL_SourceTabCaption"));
        assertThat(quickSearchRows()).as("Quick Search categories declared in the cluster's layers")
                .hasSizeGreaterThan(10);
    }

    @Test
    @DisplayName("every editor tab speaks every shipped language")
    void everyEditorTabSpeaks() throws IOException {
        assertThat(speaks(multiViewRows())).as("editor tabs a reader would meet in English").isEmpty();
    }

    @Test
    @DisplayName("every Quick Search category speaks every shipped language")
    void everyQuickSearchCategorySpeaks() throws IOException {
        assertThat(speaks(quickSearchRows())).as("Quick Search categories a reader would meet in English")
                .isEmpty();
    }

    @Test
    @DisplayName("every ledger key still exists, still saying what the ledger records")
    void theLedgerTellsTheTruthAboutTheCluster() throws IOException {
        scan();
        List<Row> rows = ledger();
        assertThat(rows).as("recorded main-window rows").hasSizeGreaterThan(30);
        assertThat(truthProblems(rows)).as("ledger claims the shipped cluster contradicts").isEmpty();
    }

    private static List<String> truthProblems(List<Row> rows) {
        List<String> problems = new ArrayList<>();
        for (Row row : rows) {
            Properties base = bundles.getOrDefault(row.pkg(), Map.of()).get(row.jar());
            if (base == null) {
                problems.add(row.key() + ": " + row.jar() + " ships no bundle at " + row.pkg());
                continue;
            }
            String actual = base.getProperty(row.key());
            if (actual == null) {
                problems.add(row.key() + " [" + row.jar() + "]: the key is gone; the surface it "
                        + "names is painted from somewhere else now");
            } else if (!actual.trim().equals(row.english())) {
                problems.add(row.key() + ": the platform now says \"" + actual.trim()
                        + "\", the ledger records \"" + row.english() + "\"");
            }
        }
        return problems;
    }

    @Test
    @DisplayName("every ledger row speaks every shipped language")
    void everyLedgerRowSpeaks() throws IOException {
        assertThat(speaks(ledger())).as("main-window chrome a reader would meet in English").isEmpty();
    }

    @Test
    @DisplayName("no value invents a mnemonic, drops a placeholder or opens a quote")
    void valuesAreWellFormed() throws IOException {
        List<Row> all = new ArrayList<>(multiViewRows());
        all.addAll(quickSearchRows());
        all.addAll(ledger());
        all.addAll(toolbarRows());
        assertThat(wellFormed(all)).as("values the renderer would mangle").isEmpty();
    }

    @Test
    @DisplayName("the editor-toolbar population is derived from the layers and includes a plain file's buttons")
    void theToolbarPopulationIsDerived() throws IOException {
        scan();
        assertThat(toolbarItems).as("editor-toolbar buttons declared in the cluster's layers")
                .hasSizeGreaterThan(25)
                .contains("jump-list-prev", "find-next", "bookmark-toggle.shadow", "shift-line-left",
                        "start-macro-recording", "comment", "uncomment");
        Map<String, List<Row>> derived = toolbarDerived();
        assertThat(derived).as("buttons whose key the layer states (Editors/Actions ShortDescription)")
                .containsKeys("find-next", "find-previous", "find-selection", "toggle-highlight-search",
                        "jump-list-last-edit", "shift-line-left", "shift-line-right");
        assertThat(derived.get("find-next")).as("Find Next paints editor-search's find-next")
                .anyMatch(r -> r.pkg().equals("org/netbeans/modules/editor/search/actions")
                        && r.key().equals("find-next"));
    }

    @Test
    @DisplayName("every editor-toolbar button's key is derived or in the ledger, and no ledger row is stale")
    void everyToolbarButtonIsAccountedFor() throws IOException {
        scan();
        Map<String, List<Row>> derived = toolbarDerived();
        Map<String, List<Row>> ledger = toolbarLedger();
        List<String> problems = new ArrayList<>();
        for (String item : toolbarItems) {
            if (!derived.containsKey(item) && !ledger.containsKey(item)) {
                problems.add(item + ": a toolbar button whose tooltip key no layer states and the "
                        + "ledger does not name; read it from the running toolbar and record it");
            }
        }
        for (String item : ledger.keySet()) {
            if (!toolbarItems.contains(item)) {
                problems.add(item + ": in the ledger but no layer registers it on an editor toolbar now");
            } else if (derived.containsKey(item)) {
                problems.add(item + ": the layer already states this key; the ledger row is a second home");
            }
        }
        assertThat(problems).as("editor-toolbar buttons with no known key").isEmpty();
    }

    @Test
    @DisplayName("every editor-toolbar ledger key still exists, still saying what the ledger records")
    void theToolbarLedgerTellsTheTruth() throws IOException {
        scan();
        List<Row> rows = new ArrayList<>();
        toolbarLedger().values().forEach(rows::addAll);
        assertThat(rows).as("recorded editor-toolbar rows").hasSizeGreaterThan(20);
        assertThat(truthProblems(rows)).as("editor-toolbar ledger claims the cluster contradicts").isEmpty();
    }

    @Test
    @DisplayName("every editor-toolbar tooltip speaks every shipped language")
    void everyToolbarTooltipSpeaks() throws IOException {
        assertThat(speaks(toolbarRows())).as("editor-toolbar tooltips a reader would meet in English").isEmpty();
    }
}
