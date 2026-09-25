package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A menu path in the documentation names a door that exists.
 *
 * <p>{@code WayfindingVocabularyTest} holds the directions the product itself
 * gives. Nothing held the directions its documents give, and they drifted: at
 * v2.152.0 the tutorials sent readers to {@code Tools ▸ PWA Kit…} two releases
 * after the kits moved to {@code File ▸ Add to Project}, and to
 * {@code Window ▸ Rack} for a window the menu calls Task Rack.
 *
 * <p>The population is derived, both halves. The paths come from the English
 * documents a new user reads first; the doors come from the assembled cluster:
 * every visible {@code Menu/**} row and folder, named the way the layer names
 * it (a {@code displayName} bundle key, on the shadow or on the action it
 * points at), plus the rows named in action code that
 * {@code code-named-menu-rows.txt} records. A path is checked hop by hop while
 * it is still inside the menu bar: the top menu, its row, and a submenu's row.
 * What follows a leaf ({@code ▸ Start}, {@code ▸ Angular (standalone)}) is a
 * dialog's own control and is not a menu door.
 *
 * <p>The translated documents are held the same way, in their own language
 * (v2.153.0): the doors are named from the same layer keys through the chain
 * the platform itself uses ({@code Bundle_nmoxstudio_xx}, {@code Bundle_nmoxstudio},
 * {@code Bundle_xx}, {@code Bundle}). The translators of the tutorials found the
 * translated user guides naming menus the product never had: German sent readers
 * to {@code Werkzeuge ▸} for a menu that reads {@code Extras}, and several guides
 * named the Screenshot and Keystrokes rows by a translator's paraphrase.
 */
class DocsMenuDoorsTest {

    private static final Path CLUSTER = Path.of("target", "nmoxstudio");
    /** A right-to-left language points its menu paths the way it reads (conventions.md). */
    private static final List<String> ARROWS = List.of(" ▸ ", " ◂ ");

    /**
     * Platform rows whose names live in action code, not in the layer, and are
     * not in the code-named ledger because no translation overlay touches them.
     * Each is checked, not trusted: the shadow must be visible in the cluster and
     * the key must resolve in its package's bundle.
     */
    private static final Map<String, String> NAMED_IN_CODE = Map.of(
            "Menu/GoTo/org-netbeans-modules-jumpto-symbol-GoToSymbol.shadow",
            "org/netbeans/modules/jumpto/symbol#TXT_GoToSymbol",
            "Menu/Tools/org-netbeans-modules-options-OptionsWindowAction.shadow",
            "org/netbeans/modules/options#CTL_Options_Window_Action");

    /** The English documents a reader follows step by step. */
    private static List<Path> documents() throws IOException {
        return documents("");
    }

    /** The documents in one language: English's own list, or every translation of it that exists. */
    private static List<Path> documents(String lang) throws IOException {
        if (!lang.isEmpty()) {
            List<Path> docs = new ArrayList<>();
            for (Path english : documents()) {
                String name = english.getFileName().toString();
                Path translated = english.resolveSibling(name.substring(0, name.length() - 3) + "." + lang + ".md");
                if (Files.isRegularFile(translated)) {
                    docs.add(translated);
                }
            }
            return docs;
        }
        List<Path> docs = new ArrayList<>(List.of(
                Path.of("..", "README.md"),
                Path.of("..", "docs", "user-guide.md"),
                Path.of("..", "docs", "kitchen-sink.md"),
                Path.of("..", "docs", "demo-script.md"),
                Path.of("..", "docs", "a-day-at-meridian.md"),
                Path.of("..", "docs", "coming-from-vscode.md"),
                Path.of("..", "docs", "quickstart.md"),
                Path.of("..", "docs", "glossary.md")));
        try (Stream<Path> s = Files.list(Path.of("..", "docs", "tutorials"))) {
            // English only: a translated sibling (name.de.md) carries its own language's names
            s.filter(p -> p.getFileName().toString().matches("[A-Za-z0-9-]+\\.md")).sorted().forEach(docs::add);
        }
        return docs;
    }

    /** A door: its display name, and its children when it is a submenu. */
    private static final class Door {
        final String name;
        final Map<String, Door> children = new TreeMap<>();
        /** Hidden by a module that loads on one OS only: the path is not in that OS's menu bar. */
        boolean notEverywhere;

        Door(String name) {
            this.name = name;
        }
    }

    @Test
    @DisplayName("every menu path in the English documents names a row the menu bar has")
    void everyDocumentedMenuPathExists() throws IOException {
        Map<String, Door> bar = menuBar("");
        assertThat(bar).as("top menus read from the assembled cluster").containsKeys("File", "Tools", "Window");
        assertThat(bar.get("Tools").children).as("Tools rows").isNotEmpty();
        assertThat(wrongPaths(bar, documents(""))).as("a documented menu path that names a door the menu bar does not have").isEmpty();
    }

    static List<String> translated() {
        return ShippedLocales.TRANSLATED;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("translated")
    @DisplayName("every menu path in a translated document names a row that language's menu bar has")
    void everyTranslatedMenuPathExists(String lang) throws IOException {
        Map<String, Door> bar = menuBar(lang);
        assertThat(bar).as("top menus in " + lang).hasSizeGreaterThanOrEqualTo(8);
        List<Path> docs = documents(lang);
        assertThat(docs).as("the " + lang + " user guide at least").isNotEmpty();
        assertThat(wrongPaths(bar, docs)).as("a " + lang + " menu path that names a door the " + lang
                + " menu bar does not have").isEmpty();
    }

    /**
     * Every path in the documents that starts at a top menu. A path is found from
     * its arrow backwards, not from a capital letter forwards: most of the scripts
     * the product speaks have no capitals, and a capital-led phrase ("Open the
     * File ▸") had been hiding the menu name inside it.
     */
    private static List<String> wrongPaths(Map<String, Door> bar, List<Path> docs) throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Path doc : docs) {
            String text = normalized(Files.readString(doc, StandardCharsets.UTF_8));
            for (int at = 0; at < text.length(); at++) {
                String arrow = arrowAt(text, at);
                if (arrow == null) {
                    continue;
                }
                Door menu = null;
                for (Door d : bar.values()) {
                    int start = at - d.name.length();
                    if (start >= 0 && text.startsWith(d.name, start)
                            && (start == 0 || !joins(text.charAt(start - 1), d.name.charAt(0)))
                            && (menu == null || d.name.length() > menu.name.length())) {
                        menu = d;
                    }
                }
                if (menu == null) {
                    // not a menu name: a dialog's tab, a submenu hop already walked,
                    // or a WRONG menu name (ledger 119: "Editar ▸" where the Spanish
                    // menu reads "Edición"). The last shows itself when the row after
                    // the arrow is a row a menu HAS, reached from another name
                    String misroot = misrooted(bar, text, at, arrow);
                    if (misroot != null) {
                        wrong.add(doc.getFileName() + ": " + snippet(text, Math.max(0, at - 20)) + "   (" + misroot + ")");
                    }
                    continue;
                }
                String problem = walk(menu, text, at + arrow.length());
                if (problem != null) {
                    wrong.add(doc.getFileName() + ": " + snippet(text, at - menu.name.length()) + "   (" + problem + ")");
                }
            }
        }
        return wrong;
    }

    /**
     * Ledger 119, closed in 3.2.0: a path whose first segment is not a menu of
     * the language, but whose next segment IS a row of one — at least two
     * words long, so a lone word a dialog tab shares with a menu row is not
     * mistaken — names that row under the wrong door. Null when the arrow
     * follows a real hop (a submenu inside a walked path) or leads nowhere a
     * menu goes.
     */
    private static String misrooted(Map<String, Door> bar, String text, int at, String arrow) {
        int after = at + arrow.length();
        Door best = null;
        Door under = null;
        for (Door menu : bar.values()) {
            Door row = longestPrefix(menu, text, after);
            if (row != null && (best == null || row.name.length() > best.name.length())) {
                best = row;
                under = menu;
            }
        }
        if (best == null || best.name.strip().split("\\s+").length < 2) {
            return null;
        }
        // the menu IS named, with a one-letter conjunction joined on
        // (Arabic وتعديل, Hebrew ועריכה — "and Edit ▸")
        if (endsWith(text, at, under.name)) {
            return null;
        }
        // a hop inside a path already walked, or a dialog's own tab after a
        // leaf row (Options ▸ Keyboard Shortcuts, Plugins ▸ Check for Updates):
        // the text before the arrow is some door's row, or the macOS app
        // menu's Settings…
        if (endsWith(text, at, "Settings…") || endsWith(text, at, "Settings")) {
            return null;
        }
        for (Door menu : bar.values()) {
            if (endsWithRow(menu, text, at)) {
                return null;
            }
        }
        // a context menu's path starts at a right-click, which no menu bar has
        String before = text.substring(Math.max(0, at - 40), at).toLowerCase(java.util.Locale.ROOT);
        if (before.contains("click") || before.contains("editor")) {
            return null;
        }
        return best.name + " is a row of " + under.name + ", not of the menu named before it";
    }

    private static boolean endsWith(String text, int at, String name) {
        int start = at - name.length();
        return start >= 0 && text.startsWith(name, start);
    }

    /** Whether the text just before {@code at} is the name of some row under {@code door}, at any depth. */
    private static boolean endsWithRow(Door door, String text, int at) {
        for (Door row : door.children.values()) {
            String n = row.name.endsWith("…") ? row.name.substring(0, row.name.length() - 1) : row.name;
            if (endsWith(text, at, row.name) || (!n.isEmpty() && endsWith(text, at, n)) || endsWithRow(row, text, at)) {
                return true;
            }
        }
        return false;
    }

    private static String arrowAt(String text, int at) {
        for (String a : ARROWS) {
            if (text.startsWith(a, at)) {
                return a;
            }
        }
        return null;
    }

    /** Two letters that belong to one word; a Han character is a word of its own. */
    private static boolean joins(char a, char b) {
        return Character.isLetterOrDigit(a) && Character.isLetterOrDigit(b)
                && !cjk(a) && !cjk(b);
    }

    private static boolean cjk(char c) {
        Character.UnicodeScript s = Character.UnicodeScript.of(c);
        return s == Character.UnicodeScript.HAN || s == Character.UnicodeScript.HIRAGANA
                || s == Character.UnicodeScript.KATAKANA;
    }

    /** Follow one path through a menu; null when every menu hop resolves. */
    private static String walk(Door menu, String text, int at) {
        Door current = menu;
        while (true) {
            if (text.startsWith("…", at)) {
                return null; // `Window ▸ …` means "any row of this menu", not a door
            }
            Door hop = longestPrefix(current, text, at);
            if (hop == null) {
                return "no row in " + current.name + " starts here";
            }
            // measured v2.153.0: on macOS the applemenu module hides Tools ▸ Options
            // and the app menu offers Settings… instead, so a path to such a row
            // must tell a Mac reader where the door is
            if (hop.notEverywhere && !text.substring(Math.max(0, at - 200),
                    Math.min(text.length(), at + 200)).contains("macOS")) {
                return current.name + " ▸ " + hop.name + " is not in every OS's menu bar; say where it is on macOS";
            }
            int after = at + matchedLength(hop, text, at);
            String arrow = arrowAt(text, after);
            if (hop.children.isEmpty() || arrow == null) {
                return null;
            }
            current = hop;
            at = after + arrow.length();
        }
    }

    private static Door longestPrefix(Door menu, String text, int at) {
        Door best = null;
        int bestLength = 0;
        for (Door d : menu.children.values()) {
            int n = matchedLength(d, text, at);
            if (n > bestLength) {
                best = d;
                bestLength = n;
            }
        }
        return best;
    }

    /** A row may be written with or without its ellipsis; a partial word does not count. */
    private static int matchedLength(Door d, String text, int at) {
        for (String name : List.of(d.name, d.name.endsWith("…") ? d.name.substring(0, d.name.length() - 1) : d.name)) {
            if (!name.isEmpty() && text.startsWith(name, at)) {
                int end = at + name.length();
                if (end >= text.length() || !joins(name.charAt(name.length() - 1), text.charAt(end))) {
                    return name.length();
                }
            }
        }
        return 0;
    }

    /**
     * Emphasis and code marks removed, line wraps joined, so a path reads as it
     * renders. The invisible marks a right-to-left path carries (RLM, embeddings)
     * and the no-break spaces French sets are layout, not name; a typographic
     * apostrophe and the ASCII one name the same row.
     */
    static String normalized(String markdown) {
        return markdown.replace("\r\n", "\n").replace("**", "").replace("`", "")
                .replaceAll("[\u200E\u200F\u202A-\u202E\u2066-\u2069]", "")
                .replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2019', '\'')
                .replaceAll("\\s*\\n\\s*", " ").replaceAll("[ \\t]{2,}", " ");
    }

    private static String snippet(String text, int at) {
        return text.substring(at, Math.min(text.length(), at + 60));
    }

    // ---- the menu bar, read from the assembled cluster ------------------

    /** What the cluster says once, for every language: the layers, and every bundle by its suffix. */
    private record Cluster(Map<String, String> displayName, Map<String, String> originalFile, Set<String> files,
            Set<String> folders, Set<String> hidden, Set<String> hiddenOnOneOs,
            Map<String, Map<String, String>> bundles) {

        /** {@code pkg/path#key} through the platform's chain for a branded build in {@code lang}. */
        String value(String lang, String pkgKey) {
            List<String> chain = lang.isEmpty() ? List.of("_nmoxstudio", "")
                    : List.of("_nmoxstudio_" + lang, "_nmoxstudio", "_" + lang, "");
            for (String suffix : chain) {
                String v = bundles.getOrDefault(suffix, Map.of()).get(pkgKey);
                if (v != null) {
                    return v;
                }
            }
            return null;
        }
    }

    private static final Pattern BUNDLE = Pattern.compile(".*/Bundle((?:_nmoxstudio)?(?:_[a-z]{2})?)\\.properties");
    private static Cluster cluster;
    private static final Map<String, Map<String, Door>> CACHED = new java.util.HashMap<>();

    private static synchronized Cluster cluster() throws IOException {
        if (cluster != null) {
            return cluster;
        }
        assertThat(CLUSTER).as("the assembled cluster").exists();
        Map<String, String> displayName = new LinkedHashMap<>();
        Map<String, String> originalFile = new LinkedHashMap<>();
        Set<String> files = new HashSet<>();
        Set<String> folders = new HashSet<>();
        Set<String> hidden = new HashSet<>();
        Set<String> hiddenOnOneOs = new HashSet<>();
        Map<String, Map<String, String>> bundles = new java.util.HashMap<>(); // suffix -> "pkg/path#key" -> value

        javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        try {
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (javax.xml.parsers.ParserConfigurationException unsupported) {
            throw new IOException(unsupported);
        }
        try (Stream<Path> all = Files.walk(CLUSTER)) {
            for (Path jar : all.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (JarFile jf = new JarFile(jar.toFile())) {
                    for (JarEntry e : jf.stream().toList()) {
                        String n = e.getName();
                        Matcher b = BUNDLE.matcher(n);
                        if (b.matches()) {
                            Properties p = new Properties();
                            try (InputStream in = jf.getInputStream(e)) {
                                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                            }
                            String pkg = n.substring(0, n.lastIndexOf('/'));
                            Map<String, String> values = bundles.computeIfAbsent(b.group(1), k -> new java.util.HashMap<>());
                            for (String k : p.stringPropertyNames()) {
                                values.putIfAbsent(pkg + "#" + k, p.getProperty(k));
                            }
                            continue;
                        }
                        if (!n.endsWith("layer.xml")) {
                            continue;
                        }
                        org.w3c.dom.Document doc;
                        try (InputStream in = jf.getInputStream(e)) {
                            doc = dbf.newDocumentBuilder().parse(in);
                        } catch (Exception malformed) {
                            continue;
                        }
                        // a module that loads on one OS only (applemenu requires
                        // org.openide.modules.os.MacOSX) hides rows on that OS only
                        String requires = jf.getManifest() == null ? null
                                : jf.getManifest().getMainAttributes().getValue("OpenIDE-Module-Requires");
                        boolean osConditional = requires != null && requires.contains("org.openide.modules.os.");
                        collect(doc.getDocumentElement(), new ArrayList<>(), displayName, originalFile,
                                files, folders, osConditional ? hiddenOnOneOs : hidden);
                    }
                } catch (IOException unreadable) {
                    // a jar we cannot open contributes nothing
                }
            }
        }
        for (Map.Entry<String, String> named : NAMED_IN_CODE.entrySet()) {
            assertThat(files).as("a code-named platform row this gate relies on").contains(named.getKey());
            assertThat(hidden).as("a code-named platform row this gate relies on is hidden").doesNotContain(named.getKey());
            assertThat(bundles.get("")).as("its name").containsKey(named.getValue());
        }
        cluster = new Cluster(displayName, originalFile, files, folders, hidden, hiddenOnOneOs, bundles);
        return cluster;
    }

    /** The menu bar a build in {@code lang} paints ("" for English). */
    private static synchronized Map<String, Door> menuBar(String lang) throws IOException {
        Map<String, Door> known = CACHED.get(lang);
        if (known != null) {
            return known;
        }
        Cluster c = cluster();
        Map<String, String> folderNames = new LinkedHashMap<>();  // "Menu/File/AddToProject" -> display
        Set<String> folderKeys = new HashSet<>();
        for (Map<String, String> values : c.bundles().values()) {
            for (String pkgKey : values.keySet()) {
                if (pkgKey.substring(pkgKey.indexOf('#') + 1).startsWith("Menu/")) {
                    folderKeys.add(pkgKey);
                }
            }
        }
        for (String pkgKey : folderKeys) {
            String key = pkgKey.substring(pkgKey.indexOf('#') + 1);
            String v = c.value(lang, pkgKey);
            if (v != null) {
                // the branding overlay is what the product paints; a folder named in two
                // packages takes the branded one
                boolean branded = c.bundles().getOrDefault("_nmoxstudio_" + lang, Map.of()).containsKey(pkgKey)
                        || c.bundles().getOrDefault("_nmoxstudio", Map.of()).containsKey(pkgKey);
                if (branded) {
                    folderNames.put(key, plain(v));
                } else {
                    folderNames.putIfAbsent(key, plain(v));
                }
            }
        }

        Map<String, Door> bar = new TreeMap<>();
        for (String folder : c.folders()) {
            if (!folder.startsWith("Menu/") || c.hidden().contains(folder)) {
                continue;
            }
            String[] parts = folder.substring("Menu/".length()).split("/");
            Map<String, Door> level = bar;
            StringBuilder path = new StringBuilder("Menu");
            for (String part : parts) {
                path.append('/').append(part);
                String name = folderNames.getOrDefault(path.toString(), part);
                Door d = level.computeIfAbsent(name, Door::new);
                level = d.children;
            }
        }
        for (String file : c.files()) {
            if (!file.startsWith("Menu/") || c.hidden().contains(file)) {
                continue;
            }
            String spec = c.displayName().get(file);
            if (spec == null && c.originalFile().containsKey(file)) {
                spec = c.displayName().get(c.originalFile().get(file));
            }
            if (spec == null && NAMED_IN_CODE.containsKey(file)) {
                spec = NAMED_IN_CODE.get(file).replace('/', '.').replace("#", ".Bundle#");
            }
            String name = painted(spec == null ? null : resolve(spec, pkgKey -> c.value(lang, pkgKey)));
            if (name == null) {
                continue;
            }
            place(bar, file, name, folderNames).notEverywhere |= c.hiddenOnOneOs().contains(file);
        }
        try (InputStream in = DocsMenuDoorsTest.class.getResourceAsStream("code-named-menu-rows.txt")) {
            assertThat(in).as("the code-named menu rows ledger").isNotNull();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String[] f = line.split("\\|");
                if (line.startsWith("#") || f.length < 5) {
                    continue;
                }
                String v = c.value(lang, f[2] + "#" + f[3]);
                String name = painted(v != null ? v : f[4]);
                if (name == null) {
                    continue;
                }
                place(bar, "Menu/" + f[0] + "/ledger", plain(name), folderNames);
            }
        }
        CACHED.put(lang, bar);
        return bar;
    }

    private static Door place(Map<String, Door> bar, String file, String english, Map<String, String> folderNames) {
        String[] parts = file.substring("Menu/".length()).split("/");
        Map<String, Door> level = bar;
        StringBuilder path = new StringBuilder("Menu");
        for (int i = 0; i < parts.length - 1; i++) {
            path.append('/').append(parts[i]);
            String name = folderNames.getOrDefault(path.toString(), parts[i]);
            level = level.computeIfAbsent(name, Door::new).children;
        }
        String name = plain(english);
        return level.computeIfAbsent(name, Door::new);
    }

    /** {@code org.nmox.x.Bundle#KEY} (or a plain string) to its value. */
    private static String resolve(String spec, java.util.function.Function<String, String> bundleValue) {
        int hash = spec.indexOf('#');
        if (hash < 0) {
            return spec;
        }
        String pkg = spec.substring(0, hash);
        if (pkg.endsWith(".Bundle")) {
            pkg = pkg.substring(0, pkg.length() - ".Bundle".length());
        }
        return bundleValue.apply(pkg.replace('.', '/') + "#" + spec.substring(hash + 1));
    }

    /**
     * {@code &File} and {@code Chec&k File} name the same row; the platform writes {@code ...} for {@code …};
     * the RLM a right-to-left bundle sets between two Latin runs is layout, as it is in the document.
     */
    /**
     * A row whose label is a MessageFormat choice ({@code Debu&g {0,choice,0#File|1#File|1<Files}})
     * paints per selection; the documents name it the way the bar reads with
     * NOTHING selected and no main project — argument −1, the state a fresh
     * window is in — which is also the singular a reader sees: "Debug File",
     * "Test File", "Close Project", "Debug Main Project". Until v2.157.0 every
     * such row was skipped, so a document naming one was refused as a door
     * the bar does not have (the Debug ▸ Debug File path, failing-first).
     * Text MessageFormat cannot render is skipped as before.
     */
    private static String painted(String name) {
        if (name == null || !name.contains("{")) {
            return name;
        }
        try {
            return new java.text.MessageFormat(name).format(new Object[]{-1, ""}).trim();
        } catch (IllegalArgumentException unrenderable) {
            return null;
        }
    }

    private static String plain(String raw) {
        return raw.replaceAll("\\(&.\\)", "").replace("&", "").replace("...", "…").replace('\u2019', '\'')
                .replaceAll("[\u200E\u200F\u202A-\u202E\u2066-\u2069]", "").trim();
    }

    private static void collect(org.w3c.dom.Element elem, List<String> path, Map<String, String> displayName,
            Map<String, String> originalFile, Set<String> files, Set<String> folders, Set<String> hidden) {
        org.w3c.dom.NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof org.w3c.dom.Element child)) {
                continue;
            }
            String name = child.getAttribute("name");
            List<String> parts = new ArrayList<>(path);
            if (name.endsWith("_hidden")) {
                parts.add(name.substring(0, name.length() - "_hidden".length()));
                hidden.add(String.join("/", parts));
                continue;
            }
            parts.add(name);
            String full = String.join("/", parts);
            if ("folder".equals(child.getTagName())) {
                folders.add(full);
                collect(child, parts, displayName, originalFile, files, folders, hidden);
                continue;
            }
            if (!"file".equals(child.getTagName())) {
                continue;
            }
            files.add(full);
            org.w3c.dom.NodeList attrs = child.getChildNodes();
            for (int j = 0; j < attrs.getLength(); j++) {
                if (!(attrs.item(j) instanceof org.w3c.dom.Element a) || !"attr".equals(a.getTagName())) {
                    continue;
                }
                String value = a.hasAttribute("bundlevalue") ? a.getAttribute("bundlevalue")
                        : a.hasAttribute("stringvalue") ? a.getAttribute("stringvalue") : null;
                if (value == null) {
                    continue;
                }
                if ("displayName".equals(a.getAttribute("name"))) {
                    displayName.put(full, value);
                } else if ("originalFile".equals(a.getAttribute("name"))) {
                    originalFile.put(full, value);
                }
            }
        }
    }
}
