package org.nmox.studio.editor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No two of this module's popup entries share a position within one
 * {@code Editors/&lt;mime&gt;/Popup} folder (ledger 80, closed
 * structurally v2.36.4): the platform logs "Found same position N for
 * both X and Y" and orders the pair nondeterministically. The ledger's
 * one recorded pair had already been moved when this gate was written
 * — and the gate's first run then named FIVE more pairs the ledger
 * never saw (Emmet's popupPosition 95 colliding with the Angular
 * switcher's 95 across four mimes). Derived from the GENERATED layer
 * so a future collision fails the build, not the boot log.
 */
class PopupPositionUniquenessTest {

    @Test
    @DisplayName("within each Editors/*/Popup folder, every position is claimed once")
    void popupPositionsUniquePerFolder() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Document doc = dbf.newDocumentBuilder()
                .parse(Path.of("target/classes/META-INF/generated-layer.xml").toFile());
        List<String> collisions = new ArrayList<>();
        int[] seen = {0};
        walk(doc.getDocumentElement(), "", collisions, seen);
        assertThat(seen[0])
                .as("the layer parse found this module's popup rows")
                .isGreaterThan(20);
        assertThat(collisions)
                .as("each collision logs an Ordering WARNING and orders nondeterministically")
                .isEmpty();
    }

    private static void walk(Element el, String path, List<String> collisions, int[] seen) {
        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element child)) {
                continue;
            }
            if ("folder".equals(child.getTagName())) {
                String name = child.getAttribute("name");
                String next = path.isEmpty() ? name : path + "/" + name;
                if (next.startsWith("Editors") && next.endsWith("/Popup")) {
                    checkFolder(child, next, collisions, seen);
                } else {
                    walk(child, next, collisions, seen);
                }
            }
        }
    }

    private static void checkFolder(Element folder, String path,
            List<String> collisions, int[] seen) {
        Map<String, String> positions = new HashMap<>();
        NodeList children = folder.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element file) || !"file".equals(file.getTagName())) {
                continue;
            }
            String fileName = file.getAttribute("name");
            String pos = null;
            NodeList attrs = file.getChildNodes();
            for (int j = 0; j < attrs.getLength(); j++) {
                if (attrs.item(j) instanceof Element attr
                        && "attr".equals(attr.getTagName())
                        && "position".equals(attr.getAttribute("name"))) {
                    pos = attr.getAttribute("intvalue");
                }
            }
            if (pos == null) {
                continue; // unpositioned rows are the OTHER gate's business
            }
            seen[0]++;
            String previous = positions.put(pos, fileName);
            if (previous != null) {
                collisions.add(path + ": position " + pos + " claimed by both "
                        + previous + " and " + fileName);
            }
        }
    }
}
