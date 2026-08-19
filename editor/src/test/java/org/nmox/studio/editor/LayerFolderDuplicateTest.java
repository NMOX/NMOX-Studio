package org.nmox.studio.editor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The module layer must not declare the same folder twice under one
 * parent: the platform MERGES duplicate siblings, but it logs a
 * "contains duplicate folders" WARNING on EVERY boot of every install
 * — the v2.18.0 polish pass found {@code Editors/text} declared three
 * times (two of ours plus growth by accretion) warning since v2.14.0.
 * Duplicates are also a merge hazard: two same-named folders reading
 * as independent blocks invites a third copy, and attribute conflicts
 * between them resolve in file order nobody chose.
 */
class LayerFolderDuplicateTest {

    @Test
    @DisplayName("no folder is declared twice under one parent in the editor layer")
    void noDuplicateSiblingFolders() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        // the DTD lives on the network; the shape check needs none of it
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Element root = f.newDocumentBuilder()
                .parse(Path.of("src/main/resources/org/nmox/studio/editor/layer.xml").toFile())
                .getDocumentElement();
        List<String> duplicates = new ArrayList<>();
        walk(root, "", duplicates);
        assertThat(duplicates)
                .as("duplicate sibling folders — merge each into the one"
                        + " declaration or the platform warns on every boot")
                .isEmpty();
    }

    private static void walk(Element parent, String path, List<String> duplicates) {
        Set<String> seen = new HashSet<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element e && "folder".equals(e.getTagName())) {
                String name = path + "/" + e.getAttribute("name");
                if (!seen.add(name)) {
                    duplicates.add(name);
                }
                walk(e, name, duplicates);
            }
        }
    }
}
