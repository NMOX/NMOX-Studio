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
 */
class DocsMenuDoorsTest {

    private static final Path CLUSTER = Path.of("target", "nmoxstudio");
    private static final String ARROW = " ▸ ";

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
        List<Path> docs = new ArrayList<>(List.of(
                Path.of("..", "README.md"),
                Path.of("..", "docs", "user-guide.md"),
                Path.of("..", "docs", "kitchen-sink.md"),
                Path.of("..", "docs", "demo-script.md"),
                Path.of("..", "docs", "a-day-at-meridian.md")));
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
        Map<String, Door> bar = menuBar();
        assertThat(bar).as("top menus read from the assembled cluster").containsKeys("File", "Tools", "Window");
        assertThat(bar.get("Tools").children).as("Tools rows").isNotEmpty();

        List<String> wrong = new ArrayList<>();
        for (Path doc : documents()) {
            String text = normalized(Files.readString(doc, StandardCharsets.UTF_8));
            Matcher top = Pattern.compile("(?<![\\p{L}\\p{N}])(\\p{Lu}[\\p{L} ]{1,20}?)" + Pattern.quote(ARROW)).matcher(text);
            int from = 0;
            while (top.find(from)) {
                from = top.end();
                Door menu = bar.get(top.group(1).trim());
                if (menu == null) {
                    continue; // prose with an arrow, or a platform menu we do not document
                }
                String problem = walk(menu, text, top.end());
                if (problem != null) {
                    wrong.add(doc.getFileName() + ": " + snippet(text, top.start()) + "   (" + problem + ")");
                }
            }
        }
        assertThat(wrong).as("a documented menu path that names a door the menu bar does not have").isEmpty();
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
            if (hop.children.isEmpty() || !text.startsWith(ARROW, after)) {
                return null;
            }
            current = hop;
            at = after + ARROW.length();
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
                if (end >= text.length() || !Character.isLetterOrDigit(text.charAt(end))) {
                    return name.length();
                }
            }
        }
        return 0;
    }

    /** Emphasis and code marks removed, line wraps joined, so a path reads as it renders. */
    static String normalized(String markdown) {
        return markdown.replace("\r\n", "\n").replace("**", "").replace("`", "")
                .replaceAll("\\s*\\n\\s*", " ").replaceAll("[ \\t]{2,}", " ");
    }

    private static String snippet(String text, int at) {
        return text.substring(at, Math.min(text.length(), at + 60));
    }

    // ---- the menu bar, read from the assembled cluster ------------------

    private static Map<String, Door> cached;

    private static synchronized Map<String, Door> menuBar() throws IOException {
        if (cached != null) {
            return cached;
        }
        assertThat(CLUSTER).as("the assembled cluster").exists();
        Map<String, String> displayName = new LinkedHashMap<>();
        Map<String, String> originalFile = new LinkedHashMap<>();
        Set<String> files = new HashSet<>();
        Set<String> folders = new HashSet<>();
        Set<String> hidden = new HashSet<>();
        Set<String> hiddenOnOneOs = new HashSet<>();
        Map<String, String> bundleValues = new LinkedHashMap<>(); // "pkg/path#key" -> English
        Map<String, String> folderNames = new LinkedHashMap<>();  // "Menu/File/AddToProject" -> display

        javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        try {
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (javax.xml.parsers.ParserConfigurationException unsupported) {
            throw new IOException(unsupported);
        }
        Map<String, String> branded = new LinkedHashMap<>();
        try (Stream<Path> all = Files.walk(CLUSTER)) {
            for (Path jar : all.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (JarFile jf = new JarFile(jar.toFile())) {
                    for (JarEntry e : jf.stream().toList()) {
                        String n = e.getName();
                        boolean base = n.endsWith("/Bundle.properties");
                        boolean brand = n.endsWith("/Bundle_nmoxstudio.properties");
                        if (base || brand) {
                            Properties p = new Properties();
                            try (InputStream in = jf.getInputStream(e)) {
                                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                            }
                            String pkg = n.substring(0, n.lastIndexOf('/'));
                            for (String k : p.stringPropertyNames()) {
                                String v = p.getProperty(k);
                                (brand ? branded : bundleValues).putIfAbsent(pkg + "#" + k, v);
                                if (k.startsWith("Menu/")) {
                                    if (brand) {
                                        folderNames.put(k, plain(v));
                                    } else {
                                        folderNames.putIfAbsent(k, plain(v));
                                    }
                                }
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
        bundleValues.putAll(branded); // the branding overlay is what the product paints

        Map<String, Door> bar = new TreeMap<>();
        for (String folder : folders) {
            if (!folder.startsWith("Menu/") || hidden.contains(folder)) {
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
        for (String file : files) {
            if (!file.startsWith("Menu/") || hidden.contains(file)) {
                continue;
            }
            String spec = displayName.get(file);
            if (spec == null && originalFile.containsKey(file)) {
                spec = displayName.get(originalFile.get(file));
            }
            if (spec == null && NAMED_IN_CODE.containsKey(file)) {
                spec = NAMED_IN_CODE.get(file).replace('/', '.').replace("#", ".Bundle#");
            }
            String english = spec == null ? null : resolve(spec, bundleValues);
            if (english == null || english.contains("{")) {
                continue;
            }
            place(bar, file, english, folderNames).notEverywhere |= hiddenOnOneOs.contains(file);
        }
        try (InputStream in = DocsMenuDoorsTest.class.getResourceAsStream("code-named-menu-rows.txt")) {
            assertThat(in).as("the code-named menu rows ledger").isNotNull();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String[] f = line.split("\\|");
                if (line.startsWith("#") || f.length < 5 || f[4].contains("{")) {
                    continue;
                }
                place(bar, "Menu/" + f[0] + "/ledger", plain(f[4]), folderNames);
            }
        }
        for (Map.Entry<String, String> named : NAMED_IN_CODE.entrySet()) {
            assertThat(files).as("a code-named platform row this gate relies on").contains(named.getKey());
            assertThat(hidden).as("a code-named platform row this gate relies on is hidden").doesNotContain(named.getKey());
            assertThat(bundleValues).as("its name").containsKey(named.getValue());
        }
        cached = bar;
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

    /** {@code org.nmox.x.Bundle#KEY} (or a plain string) to its English value. */
    private static String resolve(String spec, Map<String, String> bundleValues) {
        int hash = spec.indexOf('#');
        if (hash < 0) {
            return spec;
        }
        String pkg = spec.substring(0, hash);
        if (pkg.endsWith(".Bundle")) {
            pkg = pkg.substring(0, pkg.length() - ".Bundle".length());
        }
        return bundleValues.get(pkg.replace('.', '/') + "#" + spec.substring(hash + 1));
    }

    /** {@code &File} and {@code Chec&k File} name the same row; the platform writes {@code ...} for {@code …}. */
    private static String plain(String raw) {
        return raw.replaceAll("\\(&.\\)", "").replace("&", "").replace("...", "…").trim();
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
