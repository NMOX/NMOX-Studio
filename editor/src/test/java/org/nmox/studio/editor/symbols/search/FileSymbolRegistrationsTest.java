package org.nmox.studio.editor.symbols.search;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wiring of "Symbols in This File" that a headless test cannot press:
 * the Quick Search category the platform reads from the layer, and the
 * Navigate-menu row.
 */
class FileSymbolRegistrationsTest {

    private static Element parse(String layer) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return f.newDocumentBuilder().parse(Path.of(layer).toFile()).getDocumentElement();
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
        NodeList kids = file.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n instanceof Element e && e.getTagName().equals("attr") && name.equals(e.getAttribute("name"))) {
                return e.hasAttribute("stringvalue") ? e.getAttribute("stringvalue") : e.getAttribute("intvalue");
            }
        }
        return null;
    }

    @Test
    @DisplayName("the category is a ROOT QuickSearch folder with a position, a name and a one-word command")
    void quickSearchCategory() throws Exception {
        Element root = parse("src/main/resources/org/nmox/studio/editor/layer.xml");
        Element quickSearch = child(root, "QuickSearch");
        assertThat(quickSearch).as("QuickSearch at the layer's root: the framework reads no other").isNotNull();
        Element category = child(quickSearch, "FileSymbols");
        assertThat(category).as("the category folder").isNotNull();
        assertThat(attr(category, "position")).isEqualTo("220");
        // the platform's command is a word and a space; one letter, like its own a, o, p, r, s and t
        assertThat(attr(category, "command")).matches("\\w").isNotIn("a", "o", "p", "r", "s", "t");
        assertThat(attr(category, "SystemFileSystem.localizingBundle"))
                .isEqualTo("org.nmox.studio.editor.symbols.search.Bundle");
        assertThat(child(category, "org-nmox-studio-editor-symbols-search-FileSymbolSearchProvider.instance"))
                .as("the provider instance, named after the class the platform will construct").isNotNull();
        assertThat(new FileSymbolSearchProvider()).as("and it has the public no-argument constructor that needs")
                .isNotNull();
        assertThat(Files.readString(Path.of(
                "src/main/resources/org/nmox/studio/editor/symbols/search/Bundle.properties")))
                .as("the category's name, in the bundle the layer names").contains("QuickSearch/FileSymbols=");
    }

    @Test
    @DisplayName("Go to Symbol in This File is a Navigate-menu row right after the platform's Go to Symbol")
    void navigateMenuRow() throws Exception {
        Element menu = child(child(parse("target/classes/META-INF/generated-layer.xml"), "Menu"), "GoTo");
        Element row = child(menu, "org-nmox-studio-editor-symbols-search-GoToSymbolInFileAction.shadow");
        assertThat(row).as("the Navigate menu row").isNotNull();
        // jumpto's Go to File, Go to Type and Go to Symbol sit at 100, 150 and 151
        assertThat(attr(row, "position")).isEqualTo("152");
        assertThat(attr(row, "originalFile")).isEqualTo(
                "Actions/Edit/org-nmox-studio-editor-symbols-search-GoToSymbolInFileAction.instance");
    }
}
