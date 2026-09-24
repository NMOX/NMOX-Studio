package org.nmox.studio.application;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The VS Code chords (3.1.0, dx-plan row 7), resolved through the ASSEMBLED
 * cluster the way the running keymap resolves them. A GUI keypress is the
 * one proof this automation cannot make, so this gate does the next best
 * thing: it reads every shipped module's layers, merges them the way the
 * system filesystem does ({@code _hidden} masks), and replays
 * {@code org.netbeans.core.NbKeymap.bindings()} (read from its bytecode):
 * the {@code Shortcuts} folder, then {@code Keymaps/<profile>}, keyed by
 * FILE NAME so a profile shadow replaces a Shortcuts one of the same name,
 * and only then turned into keystrokes — two DIFFERENT names that resolve
 * to one keystroke both survive, and which one fires is left to folder
 * order. That is the collision this gate exists to see.
 *
 * <p>Keystrokes are resolved per OS family, because the notation is not
 * portable ({@code org.openide.util.Utilities.stringToKey}, read from its
 * bytecode): {@code D} is ⌘ on macOS and Ctrl elsewhere, {@code O} is Ctrl
 * on macOS and Alt elsewhere, {@code C} is Ctrl everywhere. And Linux is
 * its own family for one binding: the defaults module's
 * {@code D-BACK_QUOTE} names its action through
 * {@code RecentViewListAction.getStringRep4Unixes}, which answers null
 * except on a non-mac Unix — so Ctrl+` collides on Linux only.
 *
 * <p>Editor keybindings are checked too, conservatively: any binding in
 * any editor keybinding file of the profile (or of the NetBeans base the
 * other profiles build on) that claims the keystroke shadows the global
 * chord while an editor has focus, and must be one this gate blesses.
 */
class VsCodeKeymapResolutionTest {

    private static final Path APP = Path.of("target/nmoxstudio");
    private static final List<String> PROFILES = List.of("NetBeans", "Eclipse", "Emacs", "Idea", "NetBeans55");

    enum Os { MAC, WINDOWS, LINUX }

    /** chord (the name NMOX registers) -> the action it must fire. */
    private static final Map<String, String> CHORDS = new LinkedHashMap<>();
    static {
        CHORDS.put("DS-P", "Actions/Edit/org-netbeans-modules-quicksearch-QuickSearchAction.instance");
        CHORDS.put("DS-E", "Actions/Window/org-nmox-studio-rack-projectstudio-ProjectStudioTopComponent.instance");
        CHORDS.put("DS-X", "Actions/System/org-netbeans-modules-autoupdate-ui-actions-PluginManagerAction.instance");
        CHORDS.put("C-BACK_QUOTE", "Actions/Window/ShowTerminalTCAction.instance");
        CHORDS.put("DA-P", "Actions/File/org-nmox-studio-ui-actions-SwitchProjectAction.instance");
        CHORDS.put("DA-K", "Actions/File/org-nmox-studio-ui-actions-NewExperimentAction.instance");
        CHORDS.put("DAS-K", "Actions/File/org-nmox-studio-ui-actions-ManageExperimentsAction.instance");
    }

    /** The Eclipse profile keeps its own Ctrl+Shift+E (Switch to Editor). */
    private static final String ECLIPSE_DOCUMENTS = "Actions/Window/org-netbeans-core-windows-actions-DocumentsAction.instance";

    /**
     * Editor keybindings allowed to shadow a global chord while an editor
     * has focus: only the Eclipse profile's own editor chords, which a user
     * who picked Eclipse expects in its editor (Ctrl+Shift+P jumps to the
     * matching brace, Ctrl+Shift+X upper-cases). Keyed "profile|chord".
     */
    private static final Set<String> BLESSED_EDITOR = Set.of(
            "Eclipse|DS-P|match-brace", "Eclipse|DS-X|to-upper-case");

    // ---- the merged layer model ------------------------------------------

    /** folder path -> file name -> originalFile (a methodvalue is kept as "method:<value>"). */
    private final Map<String, Map<String, String>> files = new LinkedHashMap<>();
    /** folder path -> masked file names. */
    private final Map<String, Set<String>> masks = new LinkedHashMap<>();
    /** Editor keybinding records: profile, mime folder, key, action, file name. */
    private final List<String[]> editorBindings = new ArrayList<>();

    private void load() throws Exception {
        if (!files.isEmpty()) {
            return;
        }
        assertThat(APP.resolve("nmoxstudio/modules")).as("the assembled cluster — run after package").isDirectory();
        List<Path> jars;
        try (Stream<Path> s = Files.walk(APP)) {
            jars = s.filter(p -> p.toString().endsWith(".jar") && p.toString().contains("/modules/")
                    && !p.toString().contains("/ext/") && !p.toString().contains("/locale/")).sorted().toList();
        }
        for (Path jar : jars) {
            try (JarFile jf = new JarFile(jar.toFile())) {
                List<String> layers = new ArrayList<>();
                if (jf.getEntry("META-INF/generated-layer.xml") != null) {
                    layers.add("META-INF/generated-layer.xml");
                }
                String declared = jf.getManifest() == null ? null
                        : jf.getManifest().getMainAttributes().getValue("OpenIDE-Module-Layer");
                if (declared != null && jf.getEntry(declared) != null) {
                    layers.add(declared);
                }
                for (String layer : layers) {
                    Element root = parse(jf, layer);
                    if (root != null) {
                        walk(jf, layer, root, "");
                    }
                }
            } catch (java.util.zip.ZipException notAJar) {
                // a non-jar artefact in a modules dir is not this gate's business
            }
        }
        assertThat(files).as("the merged layers were read").containsKeys("Shortcuts", "Keymaps/NetBeans");
    }

    private static DocumentBuilder builder(JarFile jf, String layer) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setValidating(false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder b = f.newDocumentBuilder();
        // the defaults module pulls its Eclipse keybindings in through an
        // external entity beside the layer — resolve it from the jar, and
        // refuse every other system id rather than fetching anything
        b.setEntityResolver((publicId, systemId) -> {
            String name = systemId.substring(systemId.lastIndexOf('/') + 1);
            String dir = layer.contains("/") ? layer.substring(0, layer.lastIndexOf('/') + 1) : "";
            var entry = jf.getEntry(dir + name);
            if (entry != null && name.endsWith(".xml") && !name.endsWith(".dtd")) {
                return new InputSource(jf.getInputStream(entry));
            }
            return new InputSource(new ByteArrayInputStream(new byte[0]));
        });
        return b;
    }

    private static Element parse(JarFile jf, String entry) throws Exception {
        try (InputStream in = jf.getInputStream(jf.getEntry(entry))) {
            return builder(jf, entry).parse(in).getDocumentElement();
        } catch (org.xml.sax.SAXException unreadable) {
            return null;
        }
    }

    private void walk(JarFile jf, String layer, Element folder, String path) throws Exception {
        NodeList kids = folder.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (!(n instanceof Element e)) {
                continue;
            }
            String name = e.getAttribute("name");
            if ("folder".equals(e.getTagName())) {
                walk(jf, layer, e, path.isEmpty() ? name : path + "/" + name);
            } else if ("file".equals(e.getTagName())) {
                if (name.endsWith("_hidden")) {
                    masks.computeIfAbsent(path, k -> new LinkedHashSet<>())
                            .add(name.substring(0, name.length() - "_hidden".length()));
                    continue;
                }
                files.computeIfAbsent(path, k -> new LinkedHashMap<>()).put(name, original(e));
                if (path.contains("/Keybindings/") && !e.getAttribute("url").isEmpty()) {
                    readKeybindings(jf, layer, path, name, e.getAttribute("url"));
                }
            }
        }
    }

    private static String original(Element file) {
        NodeList attrs = file.getElementsByTagName("attr");
        for (int i = 0; i < attrs.getLength(); i++) {
            Element a = (Element) attrs.item(i);
            if ("originalFile".equals(a.getAttribute("name"))) {
                return a.hasAttribute("methodvalue") ? "method:" + a.getAttribute("methodvalue")
                        : a.getAttribute("stringvalue");
            }
        }
        return "";
    }

    private void readKeybindings(JarFile jf, String layer, String path, String name, String url) throws Exception {
        String entry;
        if (url.startsWith("nbres:") || url.startsWith("nbresloc:")) {
            entry = url.substring(url.indexOf(':') + 1).replaceFirst("^/+", "");
        } else {
            String dir = layer.contains("/") ? layer.substring(0, layer.lastIndexOf('/') + 1) : "";
            entry = Path.of(dir + url).normalize().toString().replace('\\', '/');
        }
        if (jf.getEntry(entry) == null) {
            return;
        }
        Element root = parse(jf, entry);
        if (root == null) {
            return;
        }
        String after = path.substring(path.indexOf("/Keybindings/") + "/Keybindings/".length());
        String profile = after.contains("/") ? after.substring(0, after.indexOf('/')) : after;
        NodeList binds = root.getElementsByTagName("bind");
        for (int i = 0; i < binds.getLength(); i++) {
            Element b = (Element) binds.item(i);
            if (b.hasAttribute("remove")) {
                continue;
            }
            editorBindings.add(new String[] {profile, path, b.getAttribute("key"), b.getAttribute("actionName"), name});
        }
    }

    private Map<String, String> visible(String folder) {
        Map<String, String> out = new LinkedHashMap<>(files.getOrDefault(folder, Map.of()));
        out.keySet().removeAll(masks.getOrDefault(folder, Set.of()));
        return out;
    }

    // ---- the keymap replay -----------------------------------------------

    /** Utilities.stringToKey's modifier law, for one OS family: "ctrl+shift|P". */
    static String keystroke(String name, Os os) {
        String first = name.split(" ")[0];
        String base = first.endsWith(".shadow") ? first.substring(0, first.length() - 7) : first;
        int dash = base.lastIndexOf('-');
        String mods = dash > 0 ? base.substring(0, dash) : "";
        String key = dash > 0 ? base.substring(dash + 1) : base;
        boolean mac = os == Os.MAC;
        Set<String> m = new TreeSet<>();
        for (char c : mods.toCharArray()) {
            switch (c) {
                case 'C' -> m.add("ctrl");
                case 'A' -> m.add("alt");
                case 'S' -> m.add("shift");
                case 'M' -> m.add("meta");
                case 'D' -> m.add(mac ? "meta" : "ctrl");
                case 'O' -> m.add(mac ? "ctrl" : "alt");
                default -> { return null; } // not a keystroke name
            }
        }
        return String.join("+", m) + "|" + key.toUpperCase(java.util.Locale.ROOT);
    }

    /** A shadow whose target only exists on some OS answers null elsewhere (getStringRep4Unixes). */
    private static String target(String original, Os os) {
        if (original.startsWith("method:")) {
            return original.contains("getStringRep4Unixes") && os == Os.LINUX
                    ? "Actions/Window/org-netbeans-core-windows-actions-RecentViewListAction.instance" : null;
        }
        return original;
    }

    /**
     * Every surviving binding of a keystroke in a profile, replayed the
     * NbKeymap way: Shortcuts then Keymaps/profile, keyed by upper-cased
     * file name so the profile replaces a same-name Shortcuts entry.
     */
    private List<String> bindingsFor(String profile, String ks, Os os) {
        // NbKeymap keys this map by FileObject.getName() — the name WITHOUT
        // its extension — upper-cased; a ".removed" file drops the name
        Map<String, String[]> byName = new LinkedHashMap<>();
        for (String folder : List.of("Shortcuts", "Keymaps/" + profile)) {
            visible(folder).forEach((n, o) -> {
                int dot = n.lastIndexOf('.');
                String bare = dot > 0 ? n.substring(0, dot) : n;
                String key = bare.toUpperCase(java.util.Locale.ROOT);
                if (n.endsWith(".removed")) {
                    byName.remove(key);
                } else {
                    byName.put(key, new String[] {bare, o.isEmpty() ? folder + "/" + n : o});
                }
            });
        }
        List<String> out = new ArrayList<>();
        for (String[] b : byName.values()) {
            if (b[0].contains(" ")) {
                continue; // a multi-keystroke sequence: prefixesFor sees those
            }
            if (ks.equals(keystroke(b[0], os))) {
                String t = target(b[1], os);
                if (t != null) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    /** Multi-keystroke sequences whose FIRST stroke is ks: a prefix swallows the chord. */
    private List<String> prefixesFor(String profile, String ks, Os os) {
        List<String> out = new ArrayList<>();
        for (String folder : List.of("Shortcuts", "Keymaps/" + profile)) {
            visible(folder).forEach((n, o) -> {
                if (n.contains(" ") && ks.equals(keystroke(n, os))) {
                    out.add(folder + "/" + n);
                }
            });
        }
        return out;
    }

    @Test
    @DisplayName("every VS Code chord and every moved NMOX chord fires exactly its action, in every profile, on every OS family")
    void eachChordResolvesToExactlyItsAction() throws Exception {
        load();
        List<String> problems = new ArrayList<>();
        for (String profile : PROFILES) {
            for (Os os : Os.values()) {
                for (Map.Entry<String, String> c : CHORDS.entrySet()) {
                    String ks = keystroke(c.getKey(), os);
                    List<String> fire = bindingsFor(profile, ks, os);
                    boolean eclipseKeepsItsOwn = "Eclipse".equals(profile) && "DS-E".equals(c.getKey());
                    String want = eclipseKeepsItsOwn ? ECLIPSE_DOCUMENTS : c.getValue();
                    if (fire.isEmpty() || !fire.stream().allMatch(want::equals)) {
                        problems.add(profile + "/" + os + " " + c.getKey() + " (" + ks + ") fires " + fire
                                + ", want only " + want);
                    }
                    List<String> prefixes = prefixesFor(profile, ks, os);
                    if (!prefixes.isEmpty()) {
                        problems.add(profile + "/" + os + " " + c.getKey() + " is the first stroke of " + prefixes);
                    }
                }
            }
        }
        assertThat(problems).as("chords that would fire something else, or fire by folder-order luck").isEmpty();
    }

    @Test
    @DisplayName("no editor keybinding shadows one of these chords while an editor has focus (Eclipse's own editor chords blessed)")
    void noEditorKeybindingShadowsTheChords() throws Exception {
        load();
        List<String> problems = new ArrayList<>();
        for (String[] b : editorBindings) {
            String profile = b[0];
            for (Os os : Os.values()) {
                if (b[4].endsWith("-mac.xml") && os != Os.MAC) {
                    continue; // the storage reads *-mac files on macOS only
                }
                String ks = keystroke(b[2], os);
                for (String chord : CHORDS.keySet()) {
                    if (ks != null && ks.equals(keystroke(chord, os))
                            && !BLESSED_EDITOR.contains(profile + "|" + chord + "|" + b[3])) {
                        problems.add(profile + "/" + os + " " + chord + " shadowed in " + b[1] + "/" + b[4]
                                + " by " + b[2] + " -> " + b[3]);
                    }
                }
            }
        }
        assertThat(problems).isEmpty();
        assertThat(editorBindings).as("the editor keybinding files were read, including the defaults module's")
                .hasSizeGreaterThan(300);
    }

    @Test
    @DisplayName("the notation law this gate replays: D and O are the OS-dependent modifiers, C is Ctrl everywhere")
    void notationLaw() {
        assertThat(keystroke("DS-P", Os.MAC)).isEqualTo("meta+shift|P");
        assertThat(keystroke("DS-P", Os.WINDOWS)).isEqualTo("ctrl+shift|P");
        assertThat(keystroke("C-BACK_QUOTE", Os.MAC)).isEqualTo("ctrl|BACK_QUOTE");
        assertThat(keystroke("D-BACK_QUOTE", Os.LINUX)).isEqualTo(keystroke("C-BACK_QUOTE", Os.LINUX));
        assertThat(keystroke("D-BACK_QUOTE", Os.MAC)).isNotEqualTo(keystroke("C-BACK_QUOTE", Os.MAC));
        assertThat(keystroke("O-R", Os.MAC)).isEqualTo(keystroke("C-R", Os.MAC));
        assertThat(keystroke("O-R", Os.LINUX)).isEqualTo("alt|R");
    }
}
