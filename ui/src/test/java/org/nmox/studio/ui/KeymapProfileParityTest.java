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
 * <p>Deliberate exception: {@code D-O.shadow_hidden} (the v1.11-era
 * jumpto mask) stays NetBeans-only — upstream's Keymaps claim is
 * commented out in the shipped jumpto, and a shadow_hidden of a
 * nonexistent file masks nothing (the v1.216.0 lesson), so replicating
 * the mask would be cargo cult.
 *
 * <p>Also pinned here: the ui layer's {@code QuickSearch} folder is a
 * ROOT folder — it sat NESTED inside {@code Keymaps/NetBeans} from
 * v1.323.0 until this gate's first run, where the QuickSearch
 * framework never looks (the v1.324.0 walk's unverifiable ⌘I reach).
 */
class KeymapProfileParityTest {

    private static final List<String> PROFILES =
            List.of("NetBeans", "Eclipse", "Emacs", "Idea", "NetBeans55");

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
                // the jumpto mask is the one blessed NetBeans-only entry
                files.removeIf(s -> s.startsWith("D-O.shadow_hidden|"));
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
                perProfile.put(prof, fileSet(pf));
            }
            Set<String> reference = perProfile.get("NetBeans");
            for (String prof : PROFILES) {
                assertThat(perProfile.get(prof))
                        .as("editor Keybindings block #" + keybindingsBlocks
                                + ": " + prof + " diverges from NetBeans")
                        .isEqualTo(reference);
            }
        }
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
}
