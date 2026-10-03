package org.nmox.studio.editor.editing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wiring a headless test cannot press: that the editing actions
 * are registered where EVERY editor kit finds them, that each keybinding
 * file binds an action that exists, and that each file rides exactly the
 * keymap profiles (and OS families) its chord was measured free in. The
 * measurement itself is replayed against the assembled cluster by
 * {@code VsCodeKeymapResolutionTest}; this holds the source to the same
 * table so the two cannot drift apart unnoticed.
 */
class EditingRegistrationsTest {

    private static final Path GENERATED = Path.of("target/classes/META-INF/generated-layer.xml");
    private static final Path HAND = Path.of("src/main/resources/org/nmox/studio/editor/layer.xml");
    private static final Path RESOURCES = Path.of("src/main/resources/org/nmox/studio/editor");

    private static Element parse(Path layer) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return f.newDocumentBuilder().parse(layer.toFile()).getDocumentElement();
    }

    /** The element at a layer path below {@code root}, or null. */
    private static Element at(Element root, String... path) {
        Element here = root;
        for (String name : path) {
            Element next = null;
            NodeList kids = here.getChildNodes();
            for (int i = 0; i < kids.getLength(); i++) {
                if (kids.item(i) instanceof Element e && name.equals(e.getAttribute("name"))) {
                    next = e;
                    break;
                }
            }
            if (next == null) {
                return null;
            }
            here = next;
        }
        return here;
    }

    private static String attr(Element file, String name) {
        NodeList kids = file.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n instanceof Element e && e.getTagName().equals("attr") && name.equals(e.getAttribute("name"))) {
                return e.hasAttribute("stringvalue") ? e.getAttribute("stringvalue") : e.getAttribute("intvalue");
            }
        }
        return null;
    }

    private static final List<String> PROFILES = List.of("NetBeans", "Eclipse", "Emacs", "Idea", "NetBeans55");

    /** file -> profile -> the OS it is limited to ("" for every OS); a profile that is absent does not carry it. */
    private static final Map<String, Map<String, String>> WHERE = new LinkedHashMap<>();
    /** file -> the one binding it holds. */
    private static final Map<String, String> BINDS = new LinkedHashMap<>();
    /** The kit actions of this package. */
    private static final List<String> ACTIONS = new ArrayList<>();
    static {
        ACTIONS.add(ToggleBlockCommentAction.NAME);
        BINDS.put("vscode-block-comment-keybindings.xml", "SA-A -> " + ToggleBlockCommentAction.NAME);
        WHERE.put("vscode-block-comment-keybindings.xml", Map.of(
                "NetBeans", "", "Emacs", "", "Idea", "", "Eclipse", "OS_MAC", "NetBeans55", "OS_MAC"));
        BINDS.put("vscode-block-comment-keybindings-linux.xml", "SC-A -> " + ToggleBlockCommentAction.NAME);
        WHERE.put("vscode-block-comment-keybindings-linux.xml", Map.of(
                "NetBeans", "OS_LINUX", "Emacs", "OS_LINUX", "Idea", "OS_LINUX",
                "Eclipse", "OS_LINUX", "NetBeans55", "OS_LINUX"));
        ACTIONS.add(ExpandLineSelectionAction.NAME);
        BINDS.put("vscode-line-keybindings.xml", "D-L -> " + ExpandLineSelectionAction.NAME);
        WHERE.put("vscode-line-keybindings.xml", Map.of("Idea", "", "Emacs", "OS_MAC"));
        ACTIONS.add(ToggleWordWrapKeyAction.NAME);
        BINDS.put("vscode-word-wrap-keybindings-also-mac.xml", "A-Z -> " + ToggleWordWrapKeyAction.NAME);
        WHERE.put("vscode-word-wrap-keybindings-also-mac.xml", Map.of(
                "NetBeans", "", "Emacs", "", "Idea", "", "Eclipse", "", "NetBeans55", ""));
    }

    @Test
    @DisplayName("the editing actions are registered at the root of Editors/Actions, where every kit inherits them")
    void actionsAreRootRegistered() throws Exception {
        Element actions = at(parse(GENERATED), "Editors", "Actions");
        assertThat(actions).as("the root Editors/Actions folder of the generated layer").isNotNull();
        for (String name : ACTIONS) {
            Element file = at(actions, name + ".instance");
            assertThat(file).as(name + " at the ROOT, not under one mime").isNotNull();
            assertThat(attr(file, "Name")).isEqualTo(name);
        }
    }

    @Test
    @DisplayName("each keybinding file holds exactly its chord, on an action that is registered")
    void filesBindRegisteredActions() throws Exception {
        Element actions = at(parse(GENERATED), "Editors", "Actions");
        Pattern bind = Pattern.compile("<bind\\s+actionName=\"([^\"]+)\"\\s+key=\"([^\"]+)\"\\s*/>");
        for (Map.Entry<String, String> e : BINDS.entrySet()) {
            String xml = Files.readString(RESOURCES.resolve(e.getKey())).replaceAll("(?s)<!--.*?-->", "");
            List<String> found = new ArrayList<>();
            Matcher m = bind.matcher(xml);
            while (m.find()) {
                found.add(m.group(2) + " -> " + m.group(1));
                assertThat(at(actions, m.group(1) + ".instance"))
                        .as(e.getKey() + " binds " + m.group(1) + ", which no kit would have").isNotNull();
            }
            assertThat(found).as(e.getKey()).containsExactly(e.getValue());
        }
    }

    @Test
    @DisplayName("each file rides exactly the profiles its chord was measured free in, limited to the OS it was free on")
    void filesRideTheirProfiles() throws Exception {
        Element root = parse(HAND);
        for (Map.Entry<String, Map<String, String>> e : WHERE.entrySet()) {
            Set<Integer> positions = new TreeSet<>();
            for (String profile : PROFILES) {
                Element file = at(root, "Editors", "Keybindings", profile, "Defaults", e.getKey());
                String want = e.getValue().get(profile);
                if (want == null) {
                    assertThat(file).as(e.getKey() + " must not ride " + profile
                            + ": the profile binds the chord itself").isNull();
                    continue;
                }
                assertThat(file).as(e.getKey() + " in " + profile).isNotNull();
                assertThat(file.getAttribute("url")).isEqualTo(e.getKey());
                String os = attr(file, "nbeditor-settings-targetOS");
                assertThat(os == null ? "" : os).as(e.getKey() + " in " + profile + ": target OS").isEqualTo(want);
                positions.add(Integer.valueOf(attr(file, "position")));
            }
            assertThat(positions).as(e.getKey() + " sits at one position in every profile").hasSize(1);
        }
    }

    @Test
    @DisplayName("no two keybinding files of a profile share a position")
    void positionsAreUniquePerProfile() throws Exception {
        Element root = parse(HAND);
        for (String profile : PROFILES) {
            Element defaults = at(root, "Editors", "Keybindings", profile, "Defaults");
            Map<String, String> byPosition = new LinkedHashMap<>();
            NodeList kids = defaults.getChildNodes();
            for (int i = 0; i < kids.getLength(); i++) {
                if (kids.item(i) instanceof Element file && file.getTagName().equals("file")) {
                    String position = attr(file, "position");
                    assertThat(position).as(profile + "/" + file.getAttribute("name") + " is positioned").isNotNull();
                    String other = byPosition.put(position, file.getAttribute("name"));
                    assertThat(other).as(profile + ": " + file.getAttribute("name") + " shares position "
                            + position).isNull();
                }
            }
            assertThat(byPosition).as(profile + " keybinding files were read").hasSizeGreaterThanOrEqualTo(4);
        }
    }

    @Test
    @DisplayName("Word Wrap is a View-menu row between Minimap and Sticky Scroll, on the action Quick Search names")
    void wordWrapMenuRow() throws Exception {
        Element generated = parse(GENERATED);
        Element row = at(generated, "Menu", "View",
                "org-nmox-studio-editor-editing-ToggleWordWrapAction.shadow");
        assertThat(row).as("the View menu row").isNotNull();
        assertThat(attr(row, "position")).isEqualTo("1155");
        assertThat(attr(row, "originalFile"))
                .isEqualTo("Actions/View/org-nmox-studio-editor-editing-ToggleWordWrapAction.instance");
    }
}
