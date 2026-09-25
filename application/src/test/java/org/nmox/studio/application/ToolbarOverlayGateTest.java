package org.nmox.studio.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
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
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The main window's toolbar speaks the user's language (ledger 90, v2.102.0;
 * the population derived in 3.2.0).
 *
 * <p>These are branding+locale overlays over the PLATFORM's own bundles, so
 * nothing in our own source can hold them honest — the only proof is reading
 * the files and rendering them. Two things can go wrong and neither shows up
 * as a compile error: a language can be missed, and a {@code choice} pattern
 * can be malformed, which throws at PAINT time in the one locale nobody on
 * this team reads.
 *
 * <p>The patterns are the platform's own shape and it is a strange one — the
 * count argument is {@code -1} when no main project is set, which is what
 * renders "Run Main Project" on a fresh start. Slavic languages carry two
 * extra branches (2 and 5) because they inflect the noun across 1 / 2–4 / 5+;
 * the others carry {@code 1<}.
 *
 * <p><b>3.2.0: the population.</b> Until then this gate checked a hand-kept
 * list of five overlay packages, and a German build's main toolbar still
 * read {@code New File... (⌘N)}, {@code Open Project... (⇧⌘O)} and
 * {@code Pause I/O Checks} with the gate green: none of the three keys was
 * in any package it listed, because v2.102.0 chose its keys from the MENU
 * rows of the same actions — and the toolbar does not paint the menu's key.
 * {@code Actions.ButtonBridge} (openide-awt, read from the bytecode) sets a
 * button's tooltip to the action's {@code SHORT_DESCRIPTION}, and only when
 * that is null to its {@code NAME} with the ampersand cut, then wraps it in
 * {@code FMT_ButtonHint} with the shortcut. {@code NewFile} and
 * {@code OpenProject} put their OWN {@code LBL_*_Tooltip} there; the menu
 * row reads {@code LBL_*_Name}. An overlay covers a bundle, not a surface.
 *
 * <p>So which buttons exist is now DERIVED from the assembled cluster: every
 * {@code Toolbars/<toolbar>/<item>} registration (merged across layers,
 * {@code _hidden} masks honoured) of every toolbar that a shipped toolbar
 * configuration ({@code Toolbars/*.xml} — Standard, and Debugging, which the
 * debugger switches to while a session runs) shows. A button whose action
 * the layer builds with one of the platform's generic factories
 * ({@code Actions.alwaysEnabled/context/callback/checkbox}) paints the key
 * its layer states ({@code ShortDescription}, else {@code displayName}), so
 * that key is derived too. Every other button's action is built in code,
 * and its keys are rows of a checked ledger ({@code main-toolbar.txt}), read
 * from the bytecode that sets them. A button the layers add that neither
 * the layer nor the ledger names fails here by name, and so does any key
 * not translated in every shipped language.
 */
class ToolbarOverlayGateTest {

    private static final Path BRANDING =
            Path.of("..", "branding", "src", "main", "nbm-branding", "modules");

    private static final Path CLUSTER = Path.of("target", "nmoxstudio");

    private static final List<String> LOCALES =
            ShippedLocales.TRANSLATED;

    /** Every overlay package the choice-pattern law reads, by jar and package. */
    private static final List<String[]> OVERLAYS = List.of(
            new String[] {"org-netbeans-modules-projectui.jar", "org/netbeans/modules/project/ui/actions"},
            new String[] {"org-netbeans-spi-debugger-ui.jar", "org/netbeans/modules/debugger/ui/actions"},
            new String[] {"org-netbeans-modules-versioning-util.jar", "org/netbeans/modules/versioning/diff"},
            new String[] {"org-openide-actions.jar", "org/openide/actions"},
            new String[] {"org-netbeans-core-ui.jar", "org/netbeans/core/ui/sampler"},
            new String[] {"org-netbeans-modules-masterfs-ui.jar", "org/netbeans/modules/masterfs/ui/suspend"});

    /**
     * The layer factories that read a button's text from the layer itself
     * ({@code AlwaysEnabledAction.extractCommonAttribute}: {@code NAME} from
     * {@code displayName}, any other key from the attribute of that name).
     */
    private static final Set<String> GENERIC_FACTORIES = Set.of(
            "org.openide.awt.Actions.alwaysEnabled",
            "org.openide.awt.Actions.context",
            "org.openide.awt.Actions.callback",
            "org.openide.awt.Actions.checkbox");

    /**
     * Toolbars every shipped configuration hides. A reader turns them on
     * from View ▸ Toolbars, so they are not nothing, but they are not the
     * window a reader meets either — and one of them is not ours to half
     * translate: the Git toolbar's buttons paint the Team ▸ Git menu's own
     * {@code LBL_*_Name} keys, and that menu is English as a whole in every
     * translated build (none of its rows is layer-declared, so no menu gate
     * derives them). Translating the fourteen the toolbar shares would leave
     * one menu in two languages. Stated here so a platform change that shows
     * one of them, or hides one of the shown ones, fails by name.
     */
    private static final Set<String> HIDDEN_BY_EVERY_CONFIGURATION = Set.of("Clipboard", "Git", "Remote");

    /** An argument reference, simple ({@code {1}}) or formatted ({@code {0,choice,…}}): its index. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)[,}]");

    @Test
    @DisplayName("every toolbar overlay exists in all twelve languages, with the same keys")
    void everyLanguageIsCovered() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (String[] o : OVERLAYS) {
            List<String> reference = null;
            for (String locale : LOCALES) {
                Path p = BRANDING.resolve(o[0]).resolve(o[1]).resolve("Bundle_" + locale + ".properties");
                if (!Files.isRegularFile(p)) {
                    wrong.add(o[0] + " [" + locale + "]: missing");
                    continue;
                }
                List<String> keys = new ArrayList<>(load(p).stringPropertyNames());
                keys.sort(String::compareTo);
                if (reference == null) {
                    reference = keys;
                } else if (!reference.equals(keys)) {
                    wrong.add(o[0] + " [" + locale + "]: keys " + keys + " != " + reference);
                }
            }
            assertThat(reference).as("%s should declare keys", o[0]).isNotNull().isNotEmpty();
        }
        assertThat(wrong).as("toolbar overlays that do not cover every language identically").isEmpty();
    }

    @Test
    @DisplayName("every choice pattern renders for -1 (no main project) through many, in every language")
    void everyPatternRenders() throws IOException {
        List<String> broken = new ArrayList<>();
        int rendered = 0;
        for (String[] o : OVERLAYS) {
            for (String locale : LOCALES) {
                Path p = BRANDING.resolve(o[0]).resolve(o[1]).resolve("Bundle_" + locale + ".properties");
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                Properties props = load(p);
                for (String key : props.stringPropertyNames()) {
                    String value = props.getProperty(key);
                    // a bare ASCII apostrophe opens a MessageFormat quote and
                    // eats the rest of the message (the v2.98.0 rule)
                    if (value.contains("'")) {
                        broken.add(key + " [" + locale + "]: bare ASCII apostrophe");
                    }
                    if (!value.contains("{0,choice")) {
                        continue;
                    }
                    for (int n : new int[] {-1, 0, 1, 2, 3, 5, 11}) {
                        try {
                            String out = new MessageFormat(value, Locale.of(locale))
                                    .format(new Object[] {n, "demo"});
                            rendered++;
                            if (out.contains("{") || out.contains("}")) {
                                broken.add(key + " [" + locale + "] n=" + n + ": unresolved placeholder in " + out);
                            }
                        } catch (RuntimeException malformed) {
                            broken.add(key + " [" + locale + "] n=" + n + ": " + malformed);
                        }
                    }
                }
            }
        }
        assertThat(broken).as("overlay values that would throw or misrender at paint time").isEmpty();
        assertThat(rendered).as("choice patterns should actually have been exercised").isGreaterThan(200);
    }

    @Test
    @DisplayName("the no-main-project branch is the one the toolbar shows on a fresh start, and it is translated")
    void theMinusOneBranchIsTranslated() throws IOException {
        // -1 is what a fresh IDE renders, and it is the branch that read
        // "&Run Main Project" in English in every translated build
        Path p = BRANDING.resolve("org-netbeans-modules-projectui.jar")
                .resolve("org/netbeans/modules/project/ui/actions").resolve("Bundle_uk.properties");
        String run = load(p).getProperty("LBL_RunMainProjectAction_Name");
        String out = new MessageFormat(run, Locale.of("uk")).format(new Object[] {-1, ""});
        assertThat(out).as("Ukrainian toolbar Run, with no main project set")
                .doesNotContain("Main Project").doesNotContain("Run").contains("Запустити");
    }

    // ---------------------------------------------------------------------
    // 3.2.0: the population, derived from the assembled cluster
    // ---------------------------------------------------------------------

    /** One key a button paints: which jar ships it, where, and what it says in English. */
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

    /** One layer attribute: its kind ({@code bundlevalue}, {@code stringvalue}, …) and value. */
    private record Attr(String kind, String value) {
    }

    /** A layer file as the merged filesystem sees it: its attributes, and where it was declared. */
    private record LayerFile(String jar, String layer, String url, Map<String, Attr> attrs) {
    }

    private static Map<String, Map<String, Properties>> bundles;
    private static Map<String, LayerFile> layerFiles;
    private static Set<String> hiddenPaths;
    /** Toolbar name to whether any shipped configuration shows it. */
    private static Map<String, Boolean> configured;
    private static List<String> unreadable;

    private static synchronized void scan() throws IOException {
        if (bundles != null) {
            return;
        }
        assertThat(CLUSTER).as("the assembled cluster (mvn package first)").exists();
        Map<String, Map<String, Properties>> found = new LinkedHashMap<>();
        Map<String, LayerFile> files = new TreeMap<>();
        Set<String> hidden = new TreeSet<>();
        Map<String, Boolean> shown = new TreeMap<>();
        List<String> refused = new ArrayList<>();
        List<String[]> configurations = new ArrayList<>();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException unsupported) {
            throw new IOException(unsupported);
        }
        List<Path> jars;
        try (Stream<Path> all = Files.walk(CLUSTER)) {
            jars = all.filter(p -> p.toString().endsWith(".jar")
                    && !p.getParent().getFileName().toString().equals("locale")).sorted().toList();
        }
        for (Path jar : jars) {
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
                            if (new String(raw, StandardCharsets.UTF_8).contains("\"Toolbars\"")) {
                                refused.add(jarName + "!" + n);
                            }
                            continue;
                        }
                        walk(doc.getDocumentElement(), new ArrayList<>(), jarName, n, files, hidden);
                    }
                }
            } catch (IOException unreadableJar) {
                // a jar the JDK cannot open contributes no surface
            }
        }
        // the toolbar configurations: Toolbars/<name>.xml, content at the
        // file's url, relative to the layer that declares it
        for (Map.Entry<String, LayerFile> f : files.entrySet()) {
            String[] parts = f.getKey().split("/");
            if (parts.length != 2 || !"Toolbars".equals(parts[0]) || !parts[1].endsWith(".xml")
                    || f.getValue().url() == null || hidden.contains(f.getKey())) {
                continue;
            }
            LayerFile lf = f.getValue();
            String dir = lf.layer().contains("/") ? lf.layer().substring(0, lf.layer().lastIndexOf('/') + 1) : "";
            configurations.add(new String[] {lf.jar(), dir + lf.url(), f.getKey()});
        }
        for (String[] c : configurations) {
            Path jar = jars.stream().filter(j -> j.getFileName().toString().equals(c[0])).findFirst().orElseThrow();
            try (JarFile jf = new JarFile(jar.toFile())) {
                JarEntry e = jf.getJarEntry(c[1]);
                assertThat(e).as("the toolbar configuration " + c[2] + " at " + c[0] + "!" + c[1]).isNotNull();
                org.w3c.dom.Document doc;
                try (InputStream in = jf.getInputStream(e)) {
                    doc = dbf.newDocumentBuilder().parse(in);
                } catch (Exception malformed) {
                    refused.add(c[0] + "!" + c[1]);
                    continue;
                }
                NodeList bars = doc.getElementsByTagName("Toolbar");
                for (int i = 0; i < bars.getLength(); i++) {
                    Element bar = (Element) bars.item(i);
                    boolean visible = !"false".equals(bar.getAttribute("visible"));
                    shown.merge(bar.getAttribute("name"), visible, Boolean::logicalOr);
                }
            }
        }
        bundles = found;
        layerFiles = files;
        hiddenPaths = hidden;
        configured = shown;
        unreadable = refused;
    }

    private static void walk(Element folder, List<String> path, String jar, String layer,
            Map<String, LayerFile> files, Set<String> hidden) {
        for (Node c = folder.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (!(c instanceof Element el)) {
                continue;
            }
            String name = el.getAttribute("name");
            List<String> next = new ArrayList<>(path);
            next.add(name);
            if ("folder".equals(el.getTagName())) {
                walk(el, next, jar, layer, files, hidden);
            } else if ("file".equals(el.getTagName())) {
                String p = String.join("/", next);
                if (name.endsWith("_hidden")) {
                    hidden.add(p.substring(0, p.length() - "_hidden".length()));
                    continue;
                }
                Map<String, Attr> attrs = new LinkedHashMap<>();
                for (Node a = el.getFirstChild(); a != null; a = a.getNextSibling()) {
                    if (a instanceof Element at && "attr".equals(at.getTagName())) {
                        for (String kind : List.of("bundlevalue", "stringvalue", "methodvalue", "newvalue")) {
                            if (at.hasAttribute(kind)) {
                                attrs.put(at.getAttribute("name"), new Attr(kind, at.getAttribute(kind)));
                            }
                        }
                    }
                }
                String url = el.hasAttribute("url") ? el.getAttribute("url") : null;
                LayerFile prev = files.get(p);
                if (prev == null) {
                    files.put(p, new LayerFile(jar, layer, url, attrs));
                } else {
                    attrs.forEach(prev.attrs()::putIfAbsent);
                }
            }
        }
    }

    /** Every button on a toolbar some configuration shows: {@code <toolbar>/<file>} to its action's layer file. */
    private static Map<String, LayerFile> items() throws IOException {
        scan();
        Map<String, LayerFile> items = new TreeMap<>();
        for (Map.Entry<String, LayerFile> f : layerFiles.entrySet()) {
            String[] parts = f.getKey().split("/");
            if (parts.length != 3 || !"Toolbars".equals(parts[0]) || hiddenPaths.contains(f.getKey())
                    || !Boolean.TRUE.equals(configured.get(parts[1]))) {
                continue;
            }
            LayerFile target = f.getValue();
            String at = f.getKey();
            for (int hops = 0; at.endsWith(".shadow") && hops < 8; hops++) {
                Attr original = target.attrs().get("originalFile");
                if (original == null) {
                    break;
                }
                at = original.value();
                target = layerFiles.get(at);
                if (target == null) {
                    break;
                }
            }
            Attr cls = target == null ? null : target.attrs().get("instanceClass");
            if (cls != null && "javax.swing.JSeparator".equals(cls.value())) {
                continue;
            }
            items.put(parts[1] + "/" + parts[2], target);
        }
        return items;
    }

    /** The buttons whose key the layer states: the generic factories read the text from the layer. */
    private static Map<String, Row> derived() throws IOException {
        Map<String, Row> byItem = new TreeMap<>();
        for (Map.Entry<String, LayerFile> e : items().entrySet()) {
            LayerFile f = e.getValue();
            Attr create = f == null ? null : f.attrs().get("instanceCreate");
            if (create == null || !GENERIC_FACTORIES.contains(create.value())) {
                continue;
            }
            Attr text = f.attrs().getOrDefault("ShortDescription", f.attrs().get("displayName"));
            if (text == null || !"bundlevalue".equals(text.kind()) || !text.value().contains("#")) {
                byItem.put(e.getKey(), new Row("?", "?", String.valueOf(text), null));
                continue;
            }
            int hash = text.value().indexOf('#');
            String cls = text.value().substring(0, hash);
            String key = text.value().substring(hash + 1);
            Row r = resolve(cls, key);
            byItem.put(e.getKey(), r != null ? r : new Row("?", cls, key, null));
        }
        return byItem;
    }

    /** {@code org.foo.Bundle} or {@code org/foo/Bundle}, and a key → the row the cluster ships, or null. */
    private static Row resolve(String bundleClass, String key) {
        String pkg = bundleClass.replace('.', '/');
        if (pkg.endsWith("/Bundle")) {
            pkg = pkg.substring(0, pkg.length() - "/Bundle".length());
        }
        for (Map.Entry<String, Properties> e : bundles.getOrDefault(pkg, Map.of()).entrySet()) {
            String v = e.getValue().getProperty(key);
            if (v != null) {
                return new Row(e.getKey(), pkg, key, v.trim());
            }
        }
        return null;
    }

    /** The main-toolbar ledger: toolbar item to the rows its button can paint. */
    private static Map<String, List<Row>> ledger() {
        Map<String, List<Row>> byItem = new LinkedHashMap<>();
        try (InputStream in = ToolbarOverlayGateTest.class.getResourceAsStream("main-toolbar.txt")) {
            assertThat(in).as("the ledger main-toolbar.txt").isNotNull();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").split("\n")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\|", 5);
                assertThat(p).as("ledger line: " + line).hasSize(5);
                byItem.computeIfAbsent(p[0], k -> new ArrayList<>())
                        .add(new Row(p[1], p[2], p[3], p[4].replace("\\n", "\n")));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return byItem;
    }

    private static List<Row> rows() throws IOException {
        List<Row> rows = new ArrayList<>(derived().values());
        ledger().values().forEach(rows::addAll);
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
                    p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
                return p.getProperty(row.key());
            }
        }
        Path file = BRANDING.resolve(row.jar()).resolve(row.pkg()).resolve("Bundle_" + lang + ".properties");
        return Files.exists(file) ? load(file).getProperty(row.key()) : null;
    }

    @Test
    @DisplayName("the main-toolbar population is derived from the cluster and includes the buttons a reader meets")
    void theToolbarPopulationIsDerived() throws IOException {
        Map<String, LayerFile> items = items();
        assertThat(unreadable).as("layers or toolbar configurations this test could not parse").isEmpty();
        Set<String> hiddenEverywhere = new TreeSet<>();
        configured.forEach((bar, shown) -> {
            if (!shown) {
                hiddenEverywhere.add(bar);
            }
        });
        assertThat(hiddenEverywhere).as("toolbars no shipped configuration shows")
                .isEqualTo(HIDDEN_BY_EVERY_CONFIGURATION);
        assertThat(configured).as("toolbars a shipped configuration shows")
                .containsEntry("File", true).containsEntry("Build", true).containsEntry("Memory", true)
                .containsEntry("Debug", true).containsEntry("UndoRedo", true).containsEntry("QuickSearch", true);
        assertThat(items.keySet()).as("main-toolbar buttons derived from the cluster's layers")
                .hasSizeGreaterThan(20)
                .contains("File/org-netbeans-modules-project-ui-NewFile.shadow",
                        "File/org-netbeans-modules-project-ui-OpenProject.shadow",
                        "Memory/org-netbeans-modules-masterfs-ui-suspend-PauseAction.shadow",
                        "Debug/org-netbeans-modules-debugger-ui-actions-StepOverAction.shadow")
                .doesNotContain("Build/org-netbeans-modules-project-ui-actions-ActiveConfigAction.shadow");
        assertThat(derived()).as("buttons whose key the layer states")
                .containsKeys("File/org-openide-actions-SaveAllAction.shadow",
                        "Memory/org-netbeans-modules-profiler-actions-SelfSamplerAction.shadow");
    }

    @Test
    @DisplayName("every main-toolbar button's key is derived or in the ledger, and no ledger row is stale")
    void everyButtonIsAccountedFor() throws IOException {
        Map<String, LayerFile> items = items();
        Map<String, Row> derived = derived();
        Map<String, List<Row>> ledger = ledger();
        List<String> problems = new ArrayList<>();
        for (String item : items.keySet()) {
            if (!derived.containsKey(item) && !ledger.containsKey(item)) {
                problems.add(item + ": a toolbar button built in code whose tooltip key the ledger does "
                        + "not name; read it from the action's bytecode and record it in main-toolbar.txt");
            }
        }
        for (Map.Entry<String, List<Row>> e : ledger.entrySet()) {
            if (!items.containsKey(e.getKey())) {
                problems.add(e.getKey() + ": in the ledger but no shown toolbar registers it now");
                continue;
            }
            Row d = derived.get(e.getKey());
            for (Row r : e.getValue()) {
                if (d != null && d.pkg().equals(r.pkg()) && d.key().equals(r.key())) {
                    problems.add(e.getKey() + " " + r.key() + ": the layer already states this key; "
                            + "the ledger row is a second home");
                }
            }
        }
        assertThat(problems).as("main-toolbar buttons with no known key").isEmpty();
    }

    @Test
    @DisplayName("every main-toolbar ledger key still exists, still saying what the ledger records")
    void theLedgerTellsTheTruthAboutTheCluster() throws IOException {
        scan();
        List<String> problems = new ArrayList<>();
        int rows = 0;
        for (List<Row> byItem : ledger().values()) {
            for (Row row : byItem) {
                rows++;
                Properties base = bundles.getOrDefault(row.pkg(), Map.of()).get(row.jar());
                if (base == null) {
                    problems.add(row.key() + ": " + row.jar() + " ships no bundle at " + row.pkg());
                    continue;
                }
                String actual = base.getProperty(row.key());
                if (actual == null) {
                    problems.add(row.key() + " [" + row.jar() + "]: the key is gone; the button it "
                            + "names is painted from somewhere else now");
                } else if (!actual.trim().equals(row.english())) {
                    problems.add(row.key() + ": the platform now says \"" + actual.trim()
                            + "\", the ledger records \"" + row.english() + "\"");
                }
            }
        }
        assertThat(rows).as("recorded main-toolbar rows").isGreaterThan(20);
        assertThat(problems).as("main-toolbar ledger claims the cluster contradicts").isEmpty();
    }

    @Test
    @DisplayName("every main-toolbar tooltip speaks every shipped language")
    void everyToolbarTooltipSpeaks() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Row row : rows()) {
            if (row.english() == null) {
                problems.add(row.pkg() + "#" + row.key() + ": declared by a layer but no shipped "
                        + "bundle defines it, so the button paints the raw key");
                continue;
            }
            for (String lang : LOCALES) {
                String v = translated(row, lang);
                if (v == null) {
                    problems.add(row.key() + " [" + lang + "] (" + row.jar() + "/" + row.pkg()
                            + "): paints \"" + row.english() + "\" in English");
                } else if (v.isBlank()) {
                    problems.add(row.key() + " [" + lang + "]: overlaid with nothing");
                }
            }
        }
        assertThat(problems).as("main-toolbar tooltips a reader would meet in English").isEmpty();
    }

    @Test
    @DisplayName("no main-toolbar value invents a mnemonic, drops a placeholder or opens a quote")
    void toolbarValuesAreWellFormed() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Row row : rows()) {
            if (row.english() == null) {
                continue;
            }
            Set<String> expected = new TreeSet<>();
            for (Matcher m = PLACEHOLDER.matcher(row.english()); m.find(); ) {
                expected.add(m.group(1));
            }
            for (String lang : LOCALES) {
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
                // a language may inflect a count English leaves bare
                // ({0} → {0,choice,…}); what it may not do is drop an argument
                Set<String> got = new TreeSet<>();
                for (Matcher m = PLACEHOLDER.matcher(v); m.find(); ) {
                    got.add(m.group(1));
                }
                if (!got.equals(expected)) {
                    problems.add(lang + " " + row.key() + ": arguments " + got
                            + " where English carries " + expected);
                }
            }
        }
        assertThat(problems).as("main-toolbar values the renderer would mangle").isEmpty();
    }

    private static Properties load(Path p) throws IOException {
        Properties props = new Properties();
        // overlays are \\uXXXX-escaped ASCII or plain UTF-8; reading as UTF-8
        // decodes both (an escape is ASCII), which is how the platform's
        // PropertyResourceBundle reads them
        props.load(new StringReader(Files.readString(p, StandardCharsets.UTF_8)));
        return props;
    }
}
