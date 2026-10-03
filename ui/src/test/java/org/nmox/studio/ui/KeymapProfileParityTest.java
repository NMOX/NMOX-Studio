package org.nmox.studio.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The keymap-profile parity law. The platform ships FIVE keymap
 * profiles (NetBeans, NetBeans 5.5, Eclipse, Emacs, IDEA — the ide
 * cluster's defaults module registers them all, and Options ▸ Keymap
 * switches between them). Profile registrations are SCOPED: a
 * {@code Keymaps/NetBeans/…} shadow and an editor
 * {@code Keybindings/NetBeans/Defaults/…} file simply vanish when the
 * user selects Emacs. Every NMOX chord therefore registers in ALL five
 * profiles, and this gate keeps the five sets in lockstep — a new
 * chord added to one profile fails the build until it rides them all.
 *
 * <p>Deliberate exceptions live in {@link #PROFILE_SCOPED}, each pinned to
 * exactly the profiles it belongs in. A mask belongs only where the file
 * it masks exists (a shadow_hidden of a nonexistent file masks nothing —
 * the v1.216.0 lesson). The 3.1.0 census corrected this javadoc's older
 * claim that the D-O mask masks nothing: the defaults module DOES bind
 * Keymaps/NetBeans/D-O (and Keymaps/Emacs/D-O) to Go to Type.
 *
 * <p>Also pinned here: the ui layer's {@code QuickSearch} folder is a
 * ROOT folder — it sat NESTED inside {@code Keymaps/NetBeans} from
 * v1.323.0 until this gate's first run, where the QuickSearch
 * framework never looks (the v1.324.0 walk's unverifiable ⌘I reach).
 */
class KeymapProfileParityTest {

    private static final List<String> PROFILES =
            List.of("NetBeans", "Eclipse", "Emacs", "Idea", "NetBeans55");

    /**
     * The entries that deliberately live in only SOME profiles, each with
     * the profiles it belongs to. A mask is scoped to the profiles where
     * the defaults module ships the file it masks (measured from the
     * assembled cluster); a chord is scoped out of a profile only where
     * that profile's own identity claims it under the same file name.
     * <ul>
     * <li>{@code D-O.shadow_hidden}: the jumpto-era mask. The defaults
     *     module binds Keymaps/NetBeans/D-O to Go to Type, so ⌘O would not
     *     open files without it.</li>
     * <li>{@code D-BACK_QUOTE.shadow_hidden} (3.1.0): the defaults module's
     *     Linux-only Ctrl+` Recent View List, shipped in NetBeans, Emacs and
     *     NetBeans55 — masked so ⌃` reaches the Terminal. Eclipse and Idea
     *     ship no such file, so a mask there would mask nothing.</li>
     * <li>{@code DS-E.shadow} (3.1.0): VS Code's Explorer chord, everywhere
     *     but Eclipse, where Ctrl+Shift+E is Eclipse's own Switch to Editor
     *     under the same file name.</li>
     * <li>{@code S-F12.shadow} (3.2): VS Code's Go to References (Find
     *     Usages) everywhere but Emacs and NetBeans55, whose defaults ship
     *     their own S-F12.shadow (Jump Prev) under the same file name.</li>
     * </ul>
     */
    private static final Map<String, Set<String>> PROFILE_SCOPED = Map.of(
            "D-O.shadow_hidden|", Set.of("NetBeans"),
            "D-BACK_QUOTE.shadow_hidden|", Set.of("NetBeans", "Emacs", "NetBeans55"),
            "DS-E.shadow|", Set.of("NetBeans", "Emacs", "Idea", "NetBeans55"),
            "S-F12.shadow|", Set.of("NetBeans", "Eclipse", "Idea"));

    /**
     * The editor Keybindings files that deliberately ride only SOME
     * profiles, by the same rule as {@link #PROFILE_SCOPED}: a chord is
     * scoped out of a profile only where that profile binds it itself.
     * {@code vscode-keybindings.xml} (3.1.0) puts Cmd+D on add-next-occurrence;
     * the defaults module binds D-D in Eclipse (remove-line) and NetBeans55
     * (shift-line-left), measured in the assembled cluster. 3.2 corrected
     * 3.1.0's Emacs and Idea scope: Emacs binds the chord on every OS (M-D
     * kill word on macOS, C-D delete character elsewhere), so the file left
     * it; Idea binds Ctrl+D (Duplicate Line) off macOS, so its registration
     * carries the macOS-only targetOS attribute, which the resolution test
     * reads.
     *
     * <p>The 3.2 editing chords, each scoped by the same measurement (every
     * file's own comment names what the other profiles bind):
     * {@code vscode-f12-keybindings.xml} and its Angular-template twin
     * (F12) in NetBeans and Idea; {@code vscode-f2-keybindings.xml} (F2) in
     * NetBeans alone; the macOS-only {@code vscode-history-keybindings-mac.xml}
     * (Ctrl+- / Ctrl+Shift+-) in NetBeans, Eclipse and NetBeans55; the
     * macOS-only {@code vscode-format-keybindings-mac.xml} (Shift+Opt+F) in
     * every profile but Idea. {@code vscode-editing-keybindings-mac.xml}
     * (Cmd+] and Opt+Cmd+F) is free everywhere, so it rides all five and is
     * held by the plain parity law.
     *
     * <p>The editing gestures the product added for VS Code hands, by the same
     * measurement: {@code vscode-line-keybindings.xml} (Cmd+L, Expand Line
     * Selection) in Idea and, on macOS, Emacs, the only profiles that leave
     * the chord free. The other files of that family are registered in all
     * five and are held by the plain parity law; where a profile carries a
     * file on one OS only, its registration says so with the targetOS
     * attribute, which {@code EditingRegistrationsTest} (editor module) pins
     * and {@code VsCodeKeymapResolutionTest} resolves.
     */
    private static final Map<String, Set<String>> EDITOR_PROFILE_SCOPED = Map.of(
            "vscode-keybindings.xml|", Set.of("NetBeans", "Idea"),
            "vscode-f12-keybindings.xml|", Set.of("NetBeans", "Idea"),
            "ng-template-vscode-keybindings.xml|", Set.of("NetBeans", "Idea"),
            "vscode-f2-keybindings.xml|", Set.of("NetBeans"),
            "vscode-history-keybindings-mac.xml|", Set.of("NetBeans", "Eclipse", "NetBeans55"),
            "vscode-format-keybindings-mac.xml|", Set.of("NetBeans", "Eclipse", "Emacs", "NetBeans55"),
            "vscode-line-keybindings.xml|", Set.of("Emacs", "Idea"));

    /** module dir -> its layer path, relative to the ui module's cwd. */
    private static final Map<String, String> KEYMAP_LAYERS = Map.of(
            "ui", "src/main/resources/org/nmox/studio/ui/layer.xml",
            "infra", "../infra/src/main/resources/org/nmox/studio/infra/layer.xml",
            "apiclient", "../apiclient/src/main/resources/org/nmox/studio/apiclient/layer.xml",
            "web3", "../web3/src/main/resources/org/nmox/studio/web3/layer.xml",
            "project", "../project/src/main/resources/org/nmox/studio/project/layer.xml",
            "dbstudio", "../dbstudio/src/main/resources/org/nmox/studio/dbstudio/layer.xml");

    private static Document parse(String path) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        // the layer DTD lives on netbeans.org — never fetch it in a test
        f.setValidating(false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return f.newDocumentBuilder().parse(new InputSource(
                Files.newBufferedReader(Path.of(path))));
    }

    private static List<Element> childFolders(Element parent) {
        List<Element> out = new ArrayList<>();
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element e && e.getTagName().equals("folder")) {
                out.add(e);
            }
        }
        return out;
    }

    private static Element folder(Element parent, String name) {
        for (Element e : childFolders(parent)) {
            if (name.equals(e.getAttribute("name"))) {
                return e;
            }
        }
        return null;
    }

    /** Every file registration under e, as "name|originalFile|url" keys. */
    private static Set<String> fileSet(Element e) {
        Set<String> out = new TreeSet<>();
        NodeList files = e.getElementsByTagName("file");
        for (int i = 0; i < files.getLength(); i++) {
            Element file = (Element) files.item(i);
            String orig = "";
            NodeList attrs = file.getElementsByTagName("attr");
            for (int j = 0; j < attrs.getLength(); j++) {
                Element a = (Element) attrs.item(j);
                if ("originalFile".equals(a.getAttribute("name"))) {
                    orig = a.getAttribute("stringvalue");
                }
            }
            out.add(file.getAttribute("name") + "|" + orig + "|" + file.getAttribute("url"));
        }
        return out;
    }

    @Test
    @DisplayName("Every module's Keymaps shadows are identical across all five profiles")
    void keymapShadowsRideEveryProfile() throws Exception {
        for (Map.Entry<String, String> entry : KEYMAP_LAYERS.entrySet()) {
            Document doc = parse(entry.getValue());
            Element keymaps = folder(doc.getDocumentElement(), "Keymaps");
            assertThat(keymaps).as(entry.getKey() + " has a Keymaps folder").isNotNull();
            Map<String, Set<String>> perProfile = new LinkedHashMap<>();
            for (String prof : PROFILES) {
                Element pf = folder(keymaps, prof);
                assertThat(pf)
                        .as(entry.getKey() + ": profile " + prof + " must be registered — "
                                + "a chord only in Keymaps/NetBeans dies the moment the "
                                + "user picks another keymap in Options")
                        .isNotNull();
                Set<String> files = fileSet(pf);
                // the blessed profile-scoped entries: each must sit in exactly
                // its profiles, and is then set aside for the parity compare
                for (Map.Entry<String, Set<String>> scoped : PROFILE_SCOPED.entrySet()) {
                    boolean present = files.stream().anyMatch(s -> s.startsWith(scoped.getKey()));
                    if ("ui".equals(entry.getKey())) {
                        assertThat(present)
                                .as("ui: " + scoped.getKey() + " belongs in exactly "
                                        + scoped.getValue() + " — checked in " + prof)
                                .isEqualTo(scoped.getValue().contains(prof));
                    }
                    files.removeIf(s -> s.startsWith(scoped.getKey()));
                }
                perProfile.put(prof, files);
            }
            Set<String> reference = perProfile.get("NetBeans");
            assertThat(reference).as(entry.getKey() + " NetBeans shadows").isNotEmpty();
            for (String prof : PROFILES) {
                assertThat(perProfile.get(prof))
                        .as(entry.getKey() + ": " + prof + " must carry the exact "
                                + "NetBeans shadow set")
                        .isEqualTo(reference);
            }
        }
    }

    @Test
    @DisplayName("The editor's Keybindings registrations are identical across all five profiles")
    void editorKeybindingsRideEveryProfile() throws Exception {
        Document doc = parse("../editor/src/main/resources/org/nmox/studio/editor/layer.xml");
        NodeList folders = doc.getElementsByTagName("folder");
        int keybindingsBlocks = 0;
        Set<String> scopedSeen = new TreeSet<>();
        for (int i = 0; i < folders.getLength(); i++) {
            Element e = (Element) folders.item(i);
            if (!"Keybindings".equals(e.getAttribute("name"))) {
                continue;
            }
            keybindingsBlocks++;
            Map<String, Set<String>> perProfile = new LinkedHashMap<>();
            for (String prof : PROFILES) {
                Element pf = folder(e, prof);
                assertThat(pf)
                        .as("editor Keybindings block #" + keybindingsBlocks
                                + ": profile " + prof + " missing — the Emmet chords "
                                + "and the Cmd+P unbind must survive a profile switch")
                        .isNotNull();
                Set<String> files = fileSet(pf);
                for (Map.Entry<String, Set<String>> scoped : EDITOR_PROFILE_SCOPED.entrySet()) {
                    if (files.stream().anyMatch(s -> s.startsWith(scoped.getKey()))) {
                        scopedSeen.add(scoped.getKey() + prof);
                    }
                    files.removeIf(s -> s.startsWith(scoped.getKey()));
                }
                perProfile.put(prof, files);
            }
            Set<String> reference = perProfile.get("NetBeans");
            for (String prof : PROFILES) {
                assertThat(perProfile.get(prof))
                        .as("editor Keybindings block #" + keybindingsBlocks
                                + ": " + prof + " diverges from NetBeans")
                        .isEqualTo(reference);
            }
        }
        Set<String> expectedScoped = new TreeSet<>();
        EDITOR_PROFILE_SCOPED.forEach((file, profs) -> profs.forEach(p -> expectedScoped.add(file + p)));
        assertThat(scopedSeen)
                .as("each profile-scoped editor keybinding file sits in exactly its profiles")
                .isEqualTo(expectedScoped);
        assertThat(keybindingsBlocks)
                .as("the editor layer's Keybindings blocks were all visited")
                .isGreaterThanOrEqualTo(9);
    }

    @Test
    @DisplayName("QuickSearch is a root folder, never nested inside Keymaps")
    void quickSearchIsNotNestedInKeymaps() throws Exception {
        Document doc = parse("src/main/resources/org/nmox/studio/ui/layer.xml");
        Element keymaps = folder(doc.getDocumentElement(), "Keymaps");
        assertThat(keymaps).isNotNull();
        NodeList nested = keymaps.getElementsByTagName("folder");
        for (int i = 0; i < nested.getLength(); i++) {
            assertThat(((Element) nested.item(i)).getAttribute("name"))
                    .as("a QuickSearch folder inside Keymaps is invisible to the "
                            + "QuickSearch framework — it reads the ROOT folder only")
                    .isNotEqualTo("QuickSearch");
        }
        // and the root registration exists
        Element root = folder(doc.getDocumentElement(), "QuickSearch");
        assertThat(root).as("the root QuickSearch registration").isNotNull();
        Element tasks = folder(root, "Tasks");
        assertThat(tasks).as("Tasks provider under root QuickSearch").isNotNull();
    }

    // ---- the sixth profile, VS Code ----------------------------------------
    //
    // The five laws above hold the product's chords to the platform's five
    // profiles, each registered by hand. The VS Code profile is not a sixth
    // hand-kept copy: it is GENERATED from the default profile with VS Code's
    // chords laid over it (scripts/generate-vscode-keymap.sh), so a product
    // chord reaches it by being in the NetBeans profile. What parity means
    // there is therefore different, and is held here: every action the
    // product binds in the NetBeans profile still has a chord in the VS Code
    // profile - on its own key where VS Code does not claim it, on VS Code's
    // key where it does - except where VS Code's own chord for something else
    // took its only key, and those are listed below with the reason.

    /**
     * Product actions the VS Code profile leaves without any chord, each
     * because VS Code's default keymap gives its only key to something else.
     * Empty today: every product chord either survives on its own key or is
     * VS Code's own action on VS Code's key.
     */
    private static final Map<String, String> VSCODE_TOOK = Map.of();

    private static final String VSCODE = "VSCode";
    private static final String VSCODE_FILES = "src/main/resources/org/nmox/studio/ui/keymap";

    @Test
    @DisplayName("Every product action bound in the NetBeans profile keeps a chord in the generated VS Code profile")
    void productGlobalChordsReachTheVsCodeProfile() throws Exception {
        Element keymaps = folder(parse("src/main/resources/org/nmox/studio/ui/layer.xml").getDocumentElement(), "Keymaps");
        Element vscode = folder(keymaps, VSCODE);
        assertThat(vscode).as("the generated Keymaps/VSCode folder in the ui layer").isNotNull();
        Set<String> targets = new TreeSet<>();
        for (String entry : fileSet(vscode)) {
            targets.add(entry.split("\\|", -1)[1]);
        }
        assertThat(targets).as("the VS Code profile carries the platform's own chords too, not only the product's")
                .hasSizeGreaterThan(60);
        List<String> lost = new ArrayList<>();
        for (Map.Entry<String, String> layer : KEYMAP_LAYERS.entrySet()) {
            Element pf = folder(folder(parse(layer.getValue()).getDocumentElement(), "Keymaps"), "NetBeans");
            for (String entry : fileSet(pf)) {
                String[] f = entry.split("\\|", -1);
                if (f[0].endsWith(".shadow") && !targets.contains(f[1]) && !VSCODE_TOOK.containsKey(f[1])) {
                    lost.add(layer.getKey() + ": " + f[0] + " -> " + f[1]);
                }
            }
        }
        assertThat(lost).as("product chords with no key in the VS Code profile - regenerate it, or list the action "
                + "in VSCODE_TOOK with the VS Code chord that took its key").isEmpty();
    }

    @Test
    @DisplayName("Every product editor action bound in the NetBeans profile keeps a chord in the VS Code profile's files")
    void productEditorChordsReachTheVsCodeProfile() throws Exception {
        Set<String> vscodeActions = new TreeSet<>();
        try (var files = Files.list(Path.of(VSCODE_FILES))) {
            for (Path p : files.filter(p -> p.toString().endsWith(".xml")).toList()) {
                vscodeActions.addAll(boundActions(p));
            }
        }
        assertThat(vscodeActions).as("the generated VS Code keybinding files were read").hasSizeGreaterThan(100);
        Document editor = parse("../editor/src/main/resources/org/nmox/studio/editor/layer.xml");
        List<String> lost = new ArrayList<>();
        NodeList folders = editor.getElementsByTagName("folder");
        for (int i = 0; i < folders.getLength(); i++) {
            Element e = (Element) folders.item(i);
            if (!"Keybindings".equals(e.getAttribute("name")) || folder(e, "NetBeans") == null) {
                continue;
            }
            for (String entry : fileSet(folder(e, "NetBeans"))) {
                String url = entry.split("\\|", -1)[2];
                if (url.isEmpty()) {
                    continue;
                }
                for (String action : boundActions(Path.of("../editor/src/main/resources/org/nmox/studio/editor", url))) {
                    if (!vscodeActions.contains(action) && !VSCODE_TOOK.containsKey(action)) {
                        lost.add(url + ": " + action);
                    }
                }
            }
        }
        assertThat(lost).as("product editor actions with no key in the VS Code profile").isEmpty();
    }

    private static Set<String> boundActions(Path keybindings) throws Exception {
        Set<String> out = new TreeSet<>();
        NodeList binds = parse(keybindings.toString()).getElementsByTagName("bind");
        for (int i = 0; i < binds.getLength(); i++) {
            Element b = (Element) binds.item(i);
            if (b.hasAttribute("actionName") && !"true".equals(b.getAttribute("remove"))) {
                out.add(b.getAttribute("actionName"));
            }
        }
        return out;
    }
}
