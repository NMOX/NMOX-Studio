package org.nmox.studio.editor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every editor-module registration in an ORDERED mime service folder
 * carries a {@code position} attribute (v2.28.0). The platform's
 * {@code org.openide.filesystems.Ordering} logs a WARNING on every
 * first use of a folder holding unpositioned children — the boot-log
 * lens found OUR completion providers named in it (the v2.18.0
 * layer-warning law: the product's own layer never warns). Derived
 * from the GENERATED layer, so any future unpositioned registration
 * fails the build instead of the boot log.
 */
class PositionedRegistrationsTest {

    /** The ordered folders this module registers into. */
    private static final List<String> ORDERED_FOLDERS =
            List.of("CompletionProviders", "HyperlinkProviders");

    @Test
    @DisplayName("every CompletionProviders/HyperlinkProviders row carries a position")
    void orderedFoldersArePositioned() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Document doc = dbf.newDocumentBuilder()
                .parse(Path.of("target/classes/META-INF/generated-layer.xml").toFile());
        List<String> unpositioned = new ArrayList<>();
        int[] seen = {0};
        walk(doc.getDocumentElement(), "", unpositioned, seen);
        assertThat(seen[0])
                .as("the layer parse found this module's ordered-folder rows")
                .isGreaterThan(70);
        assertThat(unpositioned)
                .as("unpositioned rows warn on every boot (Ordering WARNING)")
                .isEmpty();
    }

    private static void walk(Element el, String path,
            List<String> unpositioned, int[] seen) {
        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element child)) {
                continue;
            }
            if ("folder".equals(child.getTagName())) {
                walk(child, path + "/" + child.getAttribute("name"),
                        unpositioned, seen);
            } else if ("file".equals(child.getTagName())
                    && ORDERED_FOLDERS.stream().anyMatch(path::endsWith)
                    // OUR rows only: the CSL @LanguageRegistration
                    // processor also emits PLATFORM provider rows
                    // (GsfCompletionProvider, CodeTemplate, WaitScan)
                    // into these folders with no position — theirs to
                    // fix upstream, not expressible from our source
                    && child.getAttribute("name").startsWith("org-nmox-")) {
                seen[0]++;
                boolean positioned = false;
                NodeList attrs = child.getElementsByTagName("attr");
                for (int a = 0; a < attrs.getLength(); a++) {
                    if ("position".equals(((Element) attrs.item(a))
                            .getAttribute("name"))) {
                        positioned = true;
                    }
                }
                if (!positioned) {
                    unpositioned.add(path + "/" + child.getAttribute("name"));
                }
            }
        }
    }
}
