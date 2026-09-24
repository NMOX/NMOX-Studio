package org.nmox.studio.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The chords a VS Code user presses first (3.1.0, dx-plan row 7). Before
 * this release ⇧⌘P switched projects, ⇧⌘E started an experiment and ⇧⌘X
 * managed experiments — the first three things a switcher presses each
 * opened the wrong thing. Each chord is pinned here to the EXACT action it
 * invokes in every keymap profile, so a later edit that points one at a
 * neighbour fails by name; the assembled-cluster half (does anything ELSE
 * claim the keystroke, on either OS family) is
 * {@code VsCodeKeymapResolutionTest} in the application module.
 */
class VsCodeChordsTest {

    private static final List<String> PROFILES =
            List.of("NetBeans", "Eclipse", "Emacs", "Idea", "NetBeans55");

    static final String QUICK_SEARCH = "Actions/Edit/org-netbeans-modules-quicksearch-QuickSearchAction.instance";
    static final String PROJECT_STUDIO = "Actions/Window/org-nmox-studio-rack-projectstudio-ProjectStudioTopComponent.instance";
    static final String PLUGINS = "Actions/System/org-netbeans-modules-autoupdate-ui-actions-PluginManagerAction.instance";
    static final String TERMINAL = "Actions/Window/org-nmox-studio-rack-projectstudio-ProjectTerminalAction.instance";
    static final String SWITCH_PROJECT = "Actions/File/org-nmox-studio-ui-actions-SwitchProjectAction.instance";
    static final String NEW_EXPERIMENT = "Actions/File/org-nmox-studio-ui-actions-NewExperimentAction.instance";
    static final String EXPERIMENTS = "Actions/File/org-nmox-studio-ui-actions-ManageExperimentsAction.instance";

    /** chord -> the action it must invoke, in every profile unless scoped below. */
    private static final Map<String, String> CHORDS = new LinkedHashMap<>();
    static {
        CHORDS.put("DS-P", QUICK_SEARCH);        // Command Palette
        CHORDS.put("DS-E", PROJECT_STUDIO);      // Explorer
        CHORDS.put("DS-X", PLUGINS);             // Extensions
        CHORDS.put("C-BACK_QUOTE", TERMINAL);    // Toggle Terminal (Ctrl+` on every OS)
        CHORDS.put("DA-P", SWITCH_PROJECT);      // moved from DS-P
        CHORDS.put("DA-K", NEW_EXPERIMENT);      // moved from DS-E
        CHORDS.put("DAS-K", EXPERIMENTS);        // moved from DS-X
    }

    /** Platform actions whose shadow must carry the sheet flag. */
    private static final List<String> FLAGGED = List.of("DS-P", "DS-X", "C-BACK_QUOTE");

    private static Element profile(String name) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setValidating(false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Document doc = f.newDocumentBuilder().parse(new InputSource(Files.newBufferedReader(
                Path.of("src/main/resources/org/nmox/studio/ui/layer.xml"))));
        Element keymaps = child(doc.getDocumentElement(), "Keymaps");
        assertThat(keymaps).isNotNull();
        return child(keymaps, name);
    }

    private static Element child(Element parent, String name) {
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element e && name.equals(e.getAttribute("name"))) {
                return e;
            }
        }
        return null;
    }

    private static String attr(Element file, String name) {
        NodeList attrs = file.getElementsByTagName("attr");
        for (int i = 0; i < attrs.getLength(); i++) {
            Element a = (Element) attrs.item(i);
            if (name.equals(a.getAttribute("name"))) {
                String s = a.getAttribute("stringvalue");
                return s.isEmpty() ? a.getAttribute("boolvalue") : s;
            }
        }
        return null;
    }

    @Test
    @DisplayName("each VS Code chord invokes exactly its action, in every profile that carries it")
    void chordsPointAtTheirActions() throws Exception {
        for (String prof : PROFILES) {
            Element folder = profile(prof);
            assertThat(folder).as("Keymaps/" + prof).isNotNull();
            for (Map.Entry<String, String> c : CHORDS.entrySet()) {
                Element file = child(folder, c.getKey() + ".shadow");
                if ("Eclipse".equals(prof) && "DS-E".equals(c.getKey())) {
                    assertThat(file)
                            .as("Eclipse keeps its own Ctrl+Shift+E (Switch to Editor): the defaults module "
                                    + "registers Keymaps/Eclipse/DS-E.shadow, and a second layer defining the "
                                    + "same file would leave the winner to merge order")
                            .isNull();
                    continue;
                }
                assertThat(file).as(prof + " carries " + c.getKey()).isNotNull();
                assertThat(attr(file, "originalFile"))
                        .as(prof + ": " + c.getKey() + " must invoke " + c.getValue())
                        .isEqualTo(c.getValue());
                if (FLAGGED.contains(c.getKey())) {
                    assertThat(attr(file, "nmoxShortcut"))
                            .as(prof + ": " + c.getKey() + " binds a PLATFORM action, so it carries the flag "
                                    + "that puts it on Help ▸ Keyboard Shortcuts")
                            .isEqualTo("true");
                }
            }
        }
    }

    @Test
    @DisplayName("the old chords are gone from the three moved actions, and each moved action's Shortcuts chord matches its Keymaps shadow")
    void movedActionsOwnTheirNewChords() throws Exception {
        Pattern shortcut = Pattern.compile("path\\s*=\\s*\"Shortcuts\"\\s*,\\s*name\\s*=\\s*\"([^\"]+)\"");
        Map<String, String> actions = Map.of(
                "SwitchProjectAction", "DA-P",
                "NewExperimentAction", "DA-K",
                "ManageExperimentsAction", "DAS-K");
        for (Map.Entry<String, String> a : actions.entrySet()) {
            String src = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/actions/" + a.getKey() + ".java"),
                    StandardCharsets.UTF_8);
            Matcher m = shortcut.matcher(src);
            assertThat(m.find()).as(a.getKey() + " registers a Shortcuts chord").isTrue();
            assertThat(m.group(1))
                    .as(a.getKey() + ": the Shortcuts chord and the Keymaps shadow must agree, or the menu "
                            + "shows one chord while the key does another")
                    .isEqualTo(a.getValue());
            assertThat(m.group(1)).as(a.getKey() + " must not hold a VS Code chord")
                    .isNotIn("DS-P", "DS-E", "DS-X");
        }
    }

    @Test
    @DisplayName("the Welcome advertises New Experiment on its new chord")
    void welcomeAdvertisesTheMovedChord() throws Exception {
        // the English lives in MainWindow's @Messages; read the source, not
        // target/classes, whose merged bundle a resources-only rebuild
        // overwrites with the hand-written file (the v2.102.1 trap)
        Matcher english = Pattern.compile("\"MainWindow_newExperiment=([^\"]*)\"").matcher(Files.readString(
                Path.of("src/main/java/org/nmox/studio/ui/MainWindow.java"), StandardCharsets.UTF_8));
        assertThat(english.find()).as("MainWindow declares the New Experiment label").isTrue();
        assertThat(english.group(1)).endsWith("⌥⌘K").doesNotContain("⇧⌘E");
        List<String> langs = org.nmox.studio.core.util.UiLocale.SUPPORTED.stream()
                .map(org.nmox.studio.core.util.UiLocale.Choice::code)
                .filter(c -> !c.isEmpty() && !"en".equals(c)).toList();
        assertThat(langs).as("the shipped translations, derived").hasSizeGreaterThanOrEqualTo(14);
        for (String lang : langs) {
            String bundle = Files.readString(Path.of("src/main/resources/org/nmox/studio/ui/Bundle_" + lang + ".properties"),
                    StandardCharsets.UTF_8);
            assertThat(bundle).as(lang + " Welcome label").contains("⌥⌘K").doesNotContain("⇧⌘E");
        }
    }
}
