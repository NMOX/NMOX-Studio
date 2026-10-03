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
 *
 * <p>The editing chords (3.2, {@link #EDITING}) are the census of VS Code's
 * everyday editing and navigation chords that this product binds: each is
 * resolved per profile and OS family - an editor binding the way the
 * editor settings storage merges them (per profile, with no inheritance
 * between profiles, a file skipped when its {@code nbeditor-settings-targetOS}
 * names another OS, a mime's own bindings over the base's) - and must fire
 * exactly its action where it is bound, leave every other profile its own
 * meaning, name an action that exists, and be the whole population of the
 * shipped {@code vscode} keybinding files. The census's first run found
 * 3.1.0's Cmd+D colliding with the Emacs profile's own M-D and C-D and the
 * Idea profile's C-D, which 3.1.0 had measured as free.
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
        CHORDS.put("C-BACK_QUOTE", "Actions/Window/org-nmox-studio-rack-projectstudio-ProjectTerminalAction.instance");
        CHORDS.put("DA-P", "Actions/File/org-nmox-studio-ui-actions-SwitchProjectAction.instance");
        CHORDS.put("DA-K", "Actions/File/org-nmox-studio-ui-actions-NewExperimentAction.instance");
        CHORDS.put("DAS-K", "Actions/File/org-nmox-studio-ui-actions-ManageExperimentsAction.instance");
        CHORDS.put("DA-C", "Actions/Edit/org-nmox-studio-editor-share-CopyFilePathAction-Absolute.instance");
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
            "Eclipse|DS-P|match-brace", "Eclipse|DS-X|to-upper-case",
            // Ctrl+Alt+C is IntelliJ's Extract Constant on Windows and Linux;
            // an Idea user keeps it in the editor, Copy Path outside it (3.2.0)
            "Idea|DA-C|introduce-constant");

    // ---- the merged layer model ------------------------------------------

    /** folder path -> file name -> originalFile (a methodvalue is kept as "method:<value>"). */
    private final Map<String, Map<String, String>> files = new LinkedHashMap<>();
    /** folder path -> masked file names. */
    private final Map<String, Set<String>> masks = new LinkedHashMap<>();
    /**
     * Editor keybinding records: profile, mime folder, key, action, file name,
     * the file's {@code nbeditor-settings-targetOS} attribute ("" for every
     * OS), and the jar that ships it.
     */
    private final List<String[]> editorBindings = new ArrayList<>();
    /**
     * Every bind of every editor keybinding file, removals included (action
     * null): profile, folder, key, action, file name, targetOS, read order.
     */
    private final List<String[]> editorRecords = new ArrayList<>();
    /** folder/file name of an editor keybinding file -> its position attribute (null when it has none). */
    private final Map<String, Integer> positions = new LinkedHashMap<>();

    private static Integer intAttr(Element file, String name) {
        NodeList attrs = file.getElementsByTagName("attr");
        for (int i = 0; i < attrs.getLength(); i++) {
            Element a = (Element) attrs.item(i);
            if (name.equals(a.getAttribute("name")) && a.hasAttribute("intvalue")) {
                return Integer.valueOf(a.getAttribute("intvalue"));
            }
        }
        return null;
    }

    private void load() throws Exception {
        if (!files.isEmpty()) {
            return;
        }
        assertThat(APP.resolve("nmoxstudio/modules")).as("the assembled cluster — run after package").isDirectory();
        List<Path> jars;
        try (Stream<Path> s = Files.walk(APP)) {
            // matched with forward slashes whatever the OS writes: on the Windows lane
            // Path.toString() says \modules\, and a "/modules/" test read no jar at all
            jars = s.filter(p -> {
                String n = p.toString().replace('\\', '/');
                return n.endsWith(".jar") && n.contains("/modules/") && !n.contains("/ext/")
                        && !n.contains("/locale/");
            }).sorted().toList();
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
                    positions.put(path + "/" + name, intAttr(e, "position"));
                    readKeybindings(jf, layer, path, name, e.getAttribute("url"), attr(e, "nbeditor-settings-targetOS"));
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

    private static String attr(Element file, String name) {
        NodeList attrs = file.getElementsByTagName("attr");
        for (int i = 0; i < attrs.getLength(); i++) {
            Element a = (Element) attrs.item(i);
            if (name.equals(a.getAttribute("name"))) {
                return a.getAttribute("stringvalue");
            }
        }
        return "";
    }

    private void readKeybindings(JarFile jf, String layer, String path, String name, String url, String targetOs)
            throws Exception {
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
            // every bind, removals included, in file order: what the VS Code census replays
            editorRecords.add(new String[] {profile, path, b.getAttribute("key"),
                    "true".equals(b.getAttribute("remove")) ? null : b.getAttribute("actionName"), name, targetOs,
                    String.valueOf(editorRecords.size())});
            if (b.hasAttribute("remove")) {
                continue;
            }
            editorBindings.add(new String[] {profile, path, b.getAttribute("key"), b.getAttribute("actionName"), name,
                    targetOs, Path.of(jf.getName()).getFileName().toString()});
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
                if (!appliesOn(b[5], os)) {
                    continue; // the storage skips a file whose targetOS is another OS
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

    // ---- the editing chords (3.2) ----------------------------------------

    /**
     * Whether a keybinding file applies on an OS family, read the way the
     * editor settings storage reads it: {@code SettingsType$DefaultLocator}
     * skips a file whose {@code nbeditor-settings-targetOS} attribute names
     * another OS (a {@code BaseUtilities} field such as {@code OS_MAC},
     * read from the bytecode: the file loads when that field's bits meet
     * {@code getOperatingSystem()}). The {@code -mac.xml} suffix is only the
     * platform's naming habit; the attribute is the switch. {@code OS_MAC}
     * and {@code OS_LINUX} are modelled, each one bit and one family here; a
     * value this replay does not model fails the census rather than being
     * guessed.
     */
    static boolean appliesOn(String targetOs, Os os) {
        if (targetOs == null || targetOs.isEmpty()) {
            return true;
        }
        if ("OS_MAC".equals(targetOs)) {
            return os == Os.MAC;
        }
        if ("OS_LINUX".equals(targetOs)) {
            // BaseUtilities.OS_LINUX is one bit, and getOperatingSystem() answers
            // it on Linux alone (Windows and macOS have their own)
            return os == Os.LINUX;
        }
        throw new AssertionError("a keybinding file targets " + targetOs
                + ", which this replay does not model - teach appliesOn before trusting the census");
    }

    enum Where { GLOBAL, EDITOR }

    private static final Set<String> ALL_PROFILES = Set.copyOf(PROFILES);
    private static final Set<Os> EVERY_OS = Set.of(Os.MAC, Os.WINDOWS, Os.LINUX);
    private static final Set<Os> MAC_ONLY = Set.of(Os.MAC);
    private static final Set<Os> LINUX_ONLY = Set.of(Os.LINUX);

    /**
     * One VS Code chord this product binds: where it lives (a global
     * Keymaps shadow, or an editor keybinding for {@code mime}, the base
     * when it is empty), what it must fire, and the profiles that carry it
     * with the OS families each carries it on. Every other profile - and a
     * carrying profile on an OS it leaves out - keeps its own meaning of
     * the chord, and the census checks that too.
     */
    record Chord(String name, Where where, String action, String mime, Map<String, Set<Os>> scope) {
        boolean bound(String profile, Os os) {
            return scope.getOrDefault(profile, Set.of()).contains(os);
        }

        Set<Os> oses() {
            Set<Os> out = new TreeSet<>();
            scope.values().forEach(out::addAll);
            return out;
        }
    }

    /** A scope: these profiles, on these OS families. */
    private static Map<String, Set<Os>> on(Set<Os> oses, String... profiles) {
        Map<String, Set<Os>> out = new LinkedHashMap<>();
        for (String p : profiles) {
            out.put(p, oses);
        }
        return out;
    }

    private static Map<String, Set<Os>> on(Set<Os> oses, Set<String> profiles) {
        return on(oses, profiles.toArray(String[]::new));
    }

    /** Two scopes joined (a profile named in both takes the second's OS families). */
    private static Map<String, Set<Os>> plus(Map<String, Set<Os>> a, Map<String, Set<Os>> b) {
        Map<String, Set<Os>> out = new LinkedHashMap<>(a);
        out.putAll(b);
        return out;
    }

    /**
     * The editing chords a VS Code user reaches for after the first four
     * (3.2's census, docs/coming-from-vscode.md "The editing chords"), plus
     * 3.1.0's Cmd+D. Each one's profiles are the ones where nothing else
     * claims the chord, measured in the assembled cluster; each file's own
     * comment names what the other profiles bind. The census corrected
     * 3.1.0's Cmd+D: Emacs binds it on every OS and Idea binds Ctrl+D off
     * macOS, so it rides NetBeans everywhere and Idea on macOS only.
     */
    static final List<Chord> EDITING = List.of(
            new Chord("D-D", Where.EDITOR, "addCaretSelectNext", "",
                    plus(on(EVERY_OS, "NetBeans"), on(MAC_ONLY, "Idea"))),
            new Chord("F12", Where.EDITOR, "goto-declaration", "", on(EVERY_OS, "NetBeans", "Idea")),
            new Chord("F12", Where.EDITOR, "ng-goto-declaration", "text/x-ng-template",
                    on(EVERY_OS, "NetBeans", "Idea")),
            new Chord("F2", Where.EDITOR, "in-place-refactoring", "", on(EVERY_OS, "NetBeans")),
            new Chord("D-CLOSE_BRACKET", Where.EDITOR, "shift-line-right", "", on(MAC_ONLY, ALL_PROFILES)),
            new Chord("DA-F", Where.EDITOR, "replace", "", on(MAC_ONLY, ALL_PROFILES)),
            new Chord("O-MINUS", Where.EDITOR, "jump-list-prev", "",
                    on(MAC_ONLY, "NetBeans", "Eclipse", "NetBeans55")),
            new Chord("OS-MINUS", Where.EDITOR, "jump-list-next", "",
                    on(MAC_ONLY, "NetBeans", "Eclipse", "NetBeans55")),
            // bound to the product's own action since 3.6.0: Shift+Opt+F also TYPES on macOS,
            // and editing/FormatChordAction swallows that character before running "format"
            new Chord("AS-F", Where.EDITOR, "nmox-format-on-option-chord", "",
                    on(MAC_ONLY, "NetBeans", "Eclipse", "Emacs", "NetBeans55")),
            // the editing gestures the product added (editor module, editing package).
            // Shift+Alt+A: Eclipse and NetBeans55 bind O-S-A, which is Alt+Shift+A
            // off macOS, so there the file rides macOS only
            new Chord("SA-A", Where.EDITOR, "nmox-toggle-block-comment", "",
                    plus(on(EVERY_OS, "NetBeans", "Emacs", "Idea"), on(MAC_ONLY, "Eclipse", "NetBeans55"))),
            // VS Code's Linux chord for the same action, in a file the layer targets at Linux
            new Chord("SC-A", Where.EDITOR, "nmox-toggle-block-comment", "", on(LINUX_ONLY, ALL_PROFILES)),
            new Chord("A-Z", Where.EDITOR, "nmox-toggle-word-wrap", "", on(EVERY_OS, ALL_PROFILES)),
            // Cmd+L / Ctrl+L: select-identifier in NetBeans, goto in Eclipse,
            // word-match-next in NetBeans55, adjust-caret-center in Emacs off macOS
            new Chord("D-L", Where.EDITOR, "nmox-expand-line-selection", "",
                    plus(on(EVERY_OS, "Idea"), on(MAC_ONLY, "Emacs"))),
            new Chord("S-F12", Where.GLOBAL,
                    "Actions/Refactoring/org-netbeans-modules-refactoring-api-ui-WhereUsedAction.instance", "",
                    on(EVERY_OS, "NetBeans", "Eclipse", "Idea")),
            new Chord("M-PERIOD", Where.GLOBAL,
                    "Actions/Source/org-netbeans-modules-editor-hints-FixAction.instance", "",
                    on(MAC_ONLY, ALL_PROFILES)));

    /** The actions an editor with focus fires for ks: a mime's own bindings win over the base's. */
    private Set<String> editorActions(String profile, String mime, String ks, Os os) {
        Set<String> base = editorClaims("Editors/Keybindings/" + profile + "/Defaults", ks, os);
        if (mime.isEmpty()) {
            return base;
        }
        Set<String> own = editorClaims("Editors/" + mime + "/Keybindings/" + profile + "/Defaults", ks, os);
        return own.isEmpty() ? base : own;
    }

    private Set<String> editorClaims(String folder, String ks, Os os) {
        Set<String> out = new TreeSet<>();
        for (String[] b : editorBindings) {
            if (b[1].equals(folder) && appliesOn(b[5], os) && ks.equals(keystroke(b[2], os))) {
                out.add(b[3]);
            }
        }
        return out;
    }

    /** Every editor binding of ks in the profile, in any mime: what shadows a global chord somewhere. */
    private Set<String> editorClaimsAnywhere(String profile, String ks, Os os) {
        Set<String> out = new TreeSet<>();
        for (String[] b : editorBindings) {
            if (b[0].equals(profile) && appliesOn(b[5], os) && ks.equals(keystroke(b[2], os))) {
                out.add(b[1] + "/" + b[4] + " -> " + b[3]);
            }
        }
        return out;
    }

    @Test
    @DisplayName("every editing chord fires exactly its action where it is bound, and leaves every other profile its own meaning")
    void eachEditingChordResolvesToExactlyItsAction() throws Exception {
        load();
        List<String> problems = new ArrayList<>();
        for (Chord c : EDITING) {
            for (String profile : PROFILES) {
                for (Os os : c.oses()) {
                    String ks = keystroke(c.name(), os);
                    String at = profile + "/" + os + " " + c.name() + " (" + ks + ")"
                            + (c.mime().isEmpty() ? "" : " in " + c.mime());
                    List<String> global = bindingsFor(profile, ks, os);
                    List<String> prefixes = prefixesFor(profile, ks, os);
                    boolean bound = c.bound(profile, os);
                    if (c.where() == Where.GLOBAL) {
                        Set<String> shadows = editorClaimsAnywhere(profile, ks, os);
                        if (bound && (global.isEmpty() || !global.stream().allMatch(c.action()::equals)
                                || !prefixes.isEmpty() || !shadows.isEmpty())) {
                            problems.add(at + " fires " + global + ", prefixes " + prefixes
                                    + ", shadowed in the editor by " + shadows + "; want only " + c.action());
                        }
                        if (!bound && (global.contains(c.action()) || (global.isEmpty() && shadows.isEmpty()))) {
                            problems.add(at + " is scoped out of " + profile + " for the profile's own meaning, but fires "
                                    + global + " / editor " + shadows);
                        }
                    } else {
                        Set<String> claims = editorActions(profile, c.mime(), ks, os);
                        if (bound && (!claims.equals(Set.of(c.action())) || !global.isEmpty() || !prefixes.isEmpty())) {
                            problems.add(at + " fires " + claims + " in the editor and " + global
                                    + " globally (prefixes " + prefixes + "); want only " + c.action());
                        }
                        if (!bound && (claims.contains(c.action()) || (claims.isEmpty() && global.isEmpty()))) {
                            problems.add(at + " is scoped out of " + profile + " for the profile's own meaning, but fires "
                                    + claims + " / globally " + global);
                        }
                    }
                }
            }
        }
        assertThat(problems).as("editing chords that fire something else, or were scoped out of a profile for nothing")
                .isEmpty();
    }

    @Test
    @DisplayName("every editing chord names a real action: a registered or platform-bound editor action, a registered global instance")
    void everyEditingChordNamesARealAction() throws Exception {
        load();
        Set<String> editorActionFiles = new TreeSet<>();
        files.forEach((folder, names) -> {
            if (folder.startsWith("Editors/") && folder.endsWith("/Actions")) {
                names.keySet().forEach(n -> editorActionFiles.add(n.replaceFirst("\\.instance$", "")));
            }
        });
        Set<String> platformBound = new TreeSet<>();
        for (String[] b : editorBindings) {
            if (!b[6].startsWith("org-nmox-")) {
                platformBound.add(b[3]);
            }
        }
        List<String> missing = new ArrayList<>();
        for (Chord c : EDITING) {
            if (c.where() == Where.EDITOR) {
                if (!editorActionFiles.contains(c.action()) && !platformBound.contains(c.action())) {
                    missing.add(c.name() + " -> " + c.action());
                }
            } else {
                String folder = c.action().substring(0, c.action().lastIndexOf('/'));
                String name = c.action().substring(c.action().lastIndexOf('/') + 1);
                if (!visible(folder).containsKey(name)) {
                    missing.add(c.name() + " -> " + c.action());
                }
            }
        }
        assertThat(platformBound).as("the platform's own keybinding files were read").hasSizeGreaterThan(100);
        assertThat(missing).as("chords bound to an action no module registers or binds - the key would do nothing")
                .isEmpty();
    }

    @Test
    @DisplayName("every binding in a shipped vscode keybinding file is in the census, so a new chord cannot ship unmeasured")
    void theCensusCoversEveryVsCodeKeybindingFile() throws Exception {
        load();
        // compared as keystrokes, not spellings: SA-A and AS-A are one chord (the
        // platform reads modifier letters in any order), and the files and the census
        // are written by different hands
        Set<String> census = new TreeSet<>();
        for (Chord c : EDITING) {
            if (c.where() == Where.EDITOR) {
                census.add(keystroke(c.name(), Os.MAC) + " -> " + c.action());
            }
        }
        Set<String> shipped = new TreeSet<>();
        for (String[] b : editorBindings) {
            // the VS Code PROFILE's generated files are not this census's: VsCodeProfileCensus holds them
            if (b[6].startsWith("org-nmox-") && b[4].contains("vscode") && !VSCODE.equals(b[0])) {
                shipped.add(keystroke(b[2], Os.MAC) + " -> " + b[3]);
            }
        }
        assertThat(shipped).as("the vscode keybinding files were read from the NMOX jars").isNotEmpty();
        assertThat(census).as("the census names exactly what the vscode keybinding files bind").isEqualTo(shipped);
    }

    @Test
    @DisplayName("Cmd+. is M-PERIOD, so Ctrl+. stays the NetBeans and Eclipse profiles' Jump Next on Windows and Linux")
    void quickFixLeavesCtrlPeriodAlone() throws Exception {
        load();
        String jumpNext = "Actions/System/org-netbeans-core-actions-JumpNextAction.instance";
        for (String profile : List.of("NetBeans", "Eclipse")) {
            for (Os os : List.of(Os.WINDOWS, Os.LINUX)) {
                assertThat(bindingsFor(profile, "ctrl|PERIOD", os))
                        .as(profile + "/" + os + ": Ctrl+. must still fire only Jump Next")
                        .containsExactly(jumpNext);
            }
        }
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

    /**
     * The one part of {@code stringToKey} this replay does not model:
     * {@code usableKeyOnMac} turns ⌘ into ⌃ for a chord macOS keeps for
     * itself — ⌘ alone with H, Space or Tab, any ⌘ with Q, ⌘⌥ with D (read
     * from the bytecode). None of our chords may be one of those, or the
     * macOS column of this gate would be checking a keystroke that never
     * fires.
     */
    @Test
    @DisplayName("no chord is one macOS takes for itself, so the replay's macOS keystrokes are the real ones")
    void noChordIsRemappedOnMac() {
        List<String> all = new ArrayList<>(CHORDS.keySet());
        EDITING.forEach(c -> all.add(c.name()));
        for (String chord : all) {
            int dash = chord.lastIndexOf('-');
            if (dash < 0) {
                continue; // a bare function key carries no modifier macOS could claim
            }
            String mods = chord.substring(0, dash);
            String key = chord.substring(dash + 1);
            boolean meta = mods.contains("D") || mods.contains("M");
            assertThat(meta && key.equals("Q")).as(chord).isFalse();
            assertThat(meta && mods.length() == 1 && List.of("H", "SPACE", "TAB").contains(key)).as(chord).isFalse();
            assertThat(meta && mods.contains("A") && key.equals("D")).as(chord).isFalse();
        }
    }

    // ---- the sixth profile, VS Code ---------------------------------------
    //
    // scripts/vscode-keymap/chords.txt is VS Code's default keymap as this
    // product answers it; VsCodeKeymapProfile generates the profile from it and
    // VsCodeKeymapProfileGateTest holds the committed files to the generator.
    // This census does not trust the generator: it replays the assembled
    // cluster with this test's own model of the platform - NbKeymap's
    // name-keyed merge for the global half, the editor storage's per-file
    // remove-then-add in position order with the targetOS switch for the
    // editor half - and asks of every row, on every OS family, the question a
    // keypress asks.

    static final String VSCODE = "VSCode";

    /** A whole key sequence ("D-K D-S", or the editor files' "M-K$M-X") on one OS: "meta|K meta|S". */
    static String sequence(String name, Os os) {
        List<String> out = new ArrayList<>();
        for (String token : name.replace('$', ' ').trim().split("\\s+")) {
            String ks = keystroke(token, os);
            if (ks == null) {
                return null;
            }
            out.add(ks);
        }
        return String.join(" ", out);
    }

    private static String sequence(List<String> strokes) {
        return String.join(" ", strokes);
    }

    /** Every global binding of the profile, merged the NbKeymap way: upper-cased bare name -> {bare, target}. */
    private Map<String, String[]> globalsOf(String profile) {
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
        return byName;
    }

    /** The targets the profile's global keymap fires for exactly this sequence on this OS. */
    private List<String> globalFires(String profile, String seq, Os os) {
        List<String> out = new ArrayList<>();
        for (String[] b : globalsOf(profile).values()) {
            if (seq.equals(sequence(b[0], os))) {
                String t = target(b[1], os);
                if (t != null) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    /** One editor keybinding folder resolved on an OS: sequence -> action, files in position order, each removing then adding. */
    private Map<String, String> editorResolved(String folder, Os os) {
        List<String> files = new ArrayList<>();
        for (String[] r : editorRecords) {
            if (r[1].equals(folder) && appliesOn(r[5], os) && !files.contains(r[4])) {
                files.add(r[4]);
            }
        }
        files.sort(java.util.Comparator.comparing((String f) -> {
            Integer p = positions.get(folder + "/" + f);
            return p == null ? Integer.MIN_VALUE : p;
        }));
        Map<String, String> out = new LinkedHashMap<>();
        for (String file : files) {
            for (String[] r : editorRecords) {
                if (r[1].equals(folder) && r[4].equals(file) && r[3] == null) {
                    out.remove(sequence(r[2], os));
                }
            }
            for (String[] r : editorRecords) {
                if (r[1].equals(folder) && r[4].equals(file) && r[3] != null && sequence(r[2], os) != null) {
                    out.put(sequence(r[2], os), r[3]);
                }
            }
        }
        return out;
    }

    /** What an editor of the mime ("" for the base every editor shares) resolves on an OS: its own bindings over the base's. */
    private Map<String, String> editorOf(String profile, String mime, Os os) {
        Map<String, String> out = new LinkedHashMap<>(editorResolved("Editors/Keybindings/" + profile + "/Defaults", os));
        if (!mime.isEmpty()) {
            out.putAll(editorResolved("Editors/" + mime + "/Keybindings/" + profile + "/Defaults", os));
        }
        return out;
    }

    /** The mimes with keybindings of their own in the profile, and the base "". */
    private Set<String> editorMimes(String profile) {
        Set<String> out = new TreeSet<>(Set.of(""));
        String tail = "/Keybindings/" + profile + "/Defaults";
        for (String[] r : editorRecords) {
            if (r[1].startsWith("Editors/") && r[1].endsWith(tail) && !r[1].equals("Editors" + tail)) {
                out.add(r[1].substring("Editors/".length(), r[1].length() - tail.length()));
            }
        }
        return out;
    }

    private static Os os(VsCodeChordTable.Os os) {
        return Os.valueOf(os.name());
    }

    @Test
    @DisplayName("VS Code profile: every row of chords.txt fires exactly its action, on every OS family, in every editor language - and an unbound row fires nothing")
    void vsCodeProfileResolvesEveryRow() throws Exception {
        load();
        List<String> problems = new ArrayList<>();
        Set<String> mimes = editorMimes(VSCODE);
        List<VsCodeChordTable.Row> rows = VsCodeChordTable.read();
        // a chord VS Code gives an editor meaning AND a global "nothing" (F7: the symbol
        // highlight in an editor, nothing elsewhere) is the editor row's to answer there
        Set<String> editorRowChords = new TreeSet<>();
        for (VsCodeChordTable.Row r : rows) {
            if (r.scope() == VsCodeChordTable.Scope.EDITOR) {
                r.chords().forEach((o, s) -> editorRowChords.add(o + " " + sequence(s)));
            }
        }
        for (VsCodeChordTable.Row r : rows) {
            for (Map.Entry<VsCodeChordTable.Os, List<String>> c : r.chords().entrySet()) {
                Os os = os(c.getKey());
                String seq = sequence(c.getValue());
                String at = r + " " + os + " " + seq;
                List<String> global = globalFires(VSCODE, seq, os);
                if (r.scope() == VsCodeChordTable.Scope.GLOBAL) {
                    // two files naming one action for one keystroke are one answer (the
                    // applemenu's Ctrl+G and the profile's both open Go to Line); a second
                    // action is a conflict unless the default profile has the very same one
                    // on that keystroke - the platform's own ambiguity, which the generator
                    // keeps rather than resolves (Terminal Find beside Find in Files)
                    Set<String> fired = new TreeSet<>(global);
                    Set<String> extra = new TreeSet<>(fired);
                    if (r.bound()) {
                        extra.remove(r.target());
                        extra.removeAll(globalFires("NetBeans", seq, os));
                    }
                    if ((r.bound() && !fired.contains(r.target())) || !extra.isEmpty()) {
                        problems.add(at + ": the global keymap fires " + global + ", want "
                                + (r.bound() ? List.of(r.target()) : List.of()));
                    }
                    if (editorRowChords.contains(c.getKey() + " " + seq)) {
                        continue;
                    }
                    for (String mime : mimes) {
                        String shadow = editorOf(VSCODE, mime, os).get(seq);
                        if (shadow != null) {
                            problems.add(at + ": an editor of " + (mime.isEmpty() ? "any language" : mime)
                                    + " answers it first with " + shadow);
                        }
                    }
                } else {
                    for (String mime : mimes) {
                        String fires = editorOf(VSCODE, mime, os).get(seq);
                        String want = r.bound() ? r.targetFor(mime) : null;
                        if (!java.util.Objects.equals(fires, want)) {
                            problems.add(at + (mime.isEmpty() ? "" : " in " + mime) + ": the editor fires " + fires
                                    + ", want " + want);
                        }
                    }
                    if (!r.bound() && !global.isEmpty()) {
                        problems.add(at + ": unbound in an editor, so a press falls through to the global " + global);
                    }
                }
            }
        }
        assertThat(rows).as("chords.txt was read").hasSizeGreaterThan(60);
        assertThat(problems).as("VS Code chords that do not do what chords.txt says").isEmpty();
    }

    @Test
    @DisplayName("VS Code profile: no binding sits on the first stroke of a two-stroke chord, globally or in an editor")
    void vsCodeProfileHasNoPrefixCollisions() throws Exception {
        load();
        List<String> problems = new ArrayList<>();
        for (Os os : Os.values()) {
            Set<String> globals = new TreeSet<>();
            for (String[] b : globalsOf(VSCODE).values()) {
                String s = sequence(b[0], os);
                if (s != null && target(b[1], os) != null) {
                    globals.add(s);
                }
            }
            problems.addAll(prefixClashes("global " + os, globals));
            for (String mime : editorMimes(VSCODE)) {
                problems.addAll(prefixClashes("editor " + (mime.isEmpty() ? "" : mime + " ") + os,
                        editorOf(VSCODE, mime, os).keySet()));
            }
        }
        assertThat(problems).isEmpty();
    }

    private static List<String> prefixClashes(String where, Set<String> sequences) {
        List<String> out = new ArrayList<>();
        for (String a : sequences) {
            for (String b : sequences) {
                if (!a.equals(b) && b.startsWith(a + " ")) {
                    out.add(where + ": " + a + " is bound and is also the start of " + b);
                }
            }
        }
        return out;
    }

    @Test
    @DisplayName("VS Code profile: every action it names exists - the global targets in the cluster's Actions folders, the editor actions registered or platform-bound")
    void vsCodeProfileNamesRealActions() throws Exception {
        load();
        Set<String> editorActionFiles = new TreeSet<>();
        files.forEach((folder, names) -> {
            if (folder.startsWith("Editors/") && folder.endsWith("/Actions")) {
                names.keySet().forEach(n -> editorActionFiles.add(n.replaceFirst("\\.instance$", "")));
            }
        });
        for (String[] b : editorBindings) {
            if (!VSCODE.equals(b[0])) {
                editorActionFiles.add(b[3]);
            }
        }
        List<String> missing = new ArrayList<>();
        visible("Keymaps/" + VSCODE).forEach((n, o) -> {
            if (n.endsWith(".shadow")) {
                String folder = o.substring(0, o.lastIndexOf('/'));
                if (o.startsWith("method:") || !visible(folder).containsKey(o.substring(o.lastIndexOf('/') + 1))) {
                    missing.add("Keymaps/" + VSCODE + "/" + n + " -> " + o);
                }
            }
        });
        for (String[] b : editorBindings) {
            if (VSCODE.equals(b[0]) && !editorActionFiles.contains(b[3])) {
                missing.add(b[1] + "/" + b[4] + ": " + b[2] + " -> " + b[3]);
            }
        }
        assertThat(visible("Keymaps/" + VSCODE)).as("the VS Code profile's global folder is in the cluster")
                .hasSizeGreaterThan(60);
        assertThat(editorMimes(VSCODE)).as("the VS Code profile's editor files are in the cluster").isNotEmpty();
        assertThat(missing).as("bindings to an action nothing registers - the key would do nothing").isEmpty();
    }

    /**
     * An Option chord on macOS also types a character ({@code editing.TypedEcho}):
     * every editor binding of the profile that holds Alt without Ctrl or Cmd on
     * a key that types must go to an action that swallows that character.
     */
    private static final Set<String> SWALLOWS_ITS_ECHO = Set.of(
            "nmox-format-on-option-chord", "nmox-toggle-block-comment", "nmox-toggle-word-wrap");

    @Test
    @DisplayName("VS Code profile: on macOS every Option-only chord on a typing key goes to an action that swallows the character it types")
    void vsCodeOptionChordsSwallowTheirEcho() throws Exception {
        load();
        List<String> typing = new ArrayList<>();
        for (String mime : editorMimes(VSCODE)) {
            editorOf(VSCODE, mime, Os.MAC).forEach((seq, action) -> {
                String first = seq.split(" ")[0];
                String mods = first.substring(0, first.indexOf('|'));
                String key = first.substring(first.indexOf('|') + 1);
                boolean optionOnly = mods.contains("alt") && !mods.contains("ctrl") && !mods.contains("meta");
                boolean types = key.length() == 1 || Set.of("MINUS", "EQUALS", "OPEN_BRACKET", "CLOSE_BRACKET",
                        "SEMICOLON", "QUOTE", "COMMA", "PERIOD", "SLASH", "BACK_SLASH", "BACK_QUOTE").contains(key);
                if (optionOnly && types && !SWALLOWS_ITS_ECHO.contains(action)) {
                    typing.add((mime.isEmpty() ? "" : mime + " ") + seq + " -> " + action);
                }
            });
        }
        assertThat(typing).as("Option chords that would also type a character into the file").isEmpty();
    }

    @Test
    @DisplayName("VS Code profile: the class and method the switch reaches by name exist in the cluster (Use the VS Code Keymap)")
    void theSwitchReachesARealKeymapManager() throws Exception {
        String src = Files.readString(Path.of("..", "ui", "src", "main", "java", "org", "nmox", "studio", "ui",
                "keymap", "KeymapProfiles.java"));
        java.util.regex.Matcher manager = java.util.regex.Pattern
                .compile("static final String MANAGER = \"([^\"]+)\"").matcher(src);
        java.util.regex.Matcher method = java.util.regex.Pattern
                .compile("static final String SET_CURRENT = \"([^\"]+)\"").matcher(src);
        assertThat(manager.find() && method.find()).as("KeymapProfiles names the class and the method").isTrue();
        java.util.regex.Matcher id = java.util.regex.Pattern
                .compile("static final String VSCODE = \"([^\"]+)\"").matcher(src);
        assertThat(id.find() && id.group(1).equals(VSCODE)).as("the switch names this profile's id").isTrue();
        String entry = manager.group(1).replace('.', '/') + ".class";
        byte[] bytes = null;
        try (Stream<Path> s = Files.walk(APP)) {
            for (Path jar : s.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (JarFile jf = new JarFile(jar.toFile())) {
                    var e = jf.getEntry(entry);
                    if (e != null) {
                        bytes = jf.getInputStream(e).readAllBytes();
                        break;
                    }
                } catch (java.util.zip.ZipException notAJar) {
                    // not this gate's business
                }
            }
        }
        assertThat(bytes).as(manager.group(1) + " ships in the cluster").isNotNull();
        String pool = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(pool).as(manager.group(1) + "." + method.group(1) + "(String)")
                .contains(method.group(1)).contains("(Ljava/lang/String;)V");
    }

    @Test
    @DisplayName("VS Code profile: Options lists it as \"VS Code\" - both halves carry the name bundle, and the bundle says so")
    void vsCodeProfileIsNamed() throws Exception {
        load();
        Path ui = APP.resolve("nmoxstudio/modules/org-nmox-NMOX-Studio-ui.jar");
        try (JarFile jf = new JarFile(ui.toFile())) {
            var e = jf.getEntry("org/nmox/studio/ui/keymap/names/Bundle.properties");
            assertThat(e).as("the profile-name bundle ships in the ui jar").isNotNull();
            java.util.Properties p = new java.util.Properties();
            try (InputStream in = jf.getInputStream(e)) {
                p.load(in);
            }
            assertThat(p.getProperty("Keymaps/" + VSCODE)).isEqualTo("VS Code");
            assertThat(p.getProperty("Editors/Keybindings/" + VSCODE)).isEqualTo("VS Code");
            String layer = new String(jf.getInputStream(jf.getEntry("org/nmox/studio/ui/layer.xml")).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertThat(layer.split("stringvalue=\"org.nmox.studio.ui.keymap.names.Bundle\"", -1))
                    .as("both profile folders, global and editor, name the bundle").hasSize(3);
        }
    }
}
