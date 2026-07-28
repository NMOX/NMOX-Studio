package org.nmox.studio.editor.format;

import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The action registration, byte-verified in the GENERATED layer — the
 * v1.195.0 idiom: a wrong {@code @ActionReference} path compiles fine
 * and simply appears in no menu, so the gate walks the artifact the
 * platform actually loads, as nested folder elements (a flat path
 * string never appears in layer XML).
 */
class FormatWithPrettierRegistrationTest {

    /** One per Prettier-formattable family; the annotation lists all 14. */
    private static final List<String> REPRESENTATIVE_MIMES = List.of(
            "text/javascript", "text/css", "text/x-json", "text/x-svelte");

    @Test
    @DisplayName("Format with Prettier sits on the editor popup of the Prettier mimes")
    void popupRegistrationsExist() throws Exception {
        var dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Document doc;
        try (InputStream layer = FormatWithPrettierAction.class
                .getResourceAsStream("/META-INF/generated-layer.xml")) {
            assertThat(layer).isNotNull();
            doc = dbf.newDocumentBuilder().parse(layer);
        }
        for (String mime : REPRESENTATIVE_MIMES) {
            String[] path = ("Editors/" + mime + "/Popup").split("/");
            Element popup = folderAt(doc, path);
            assertThat(popup).as("popup folder for %s", mime).isNotNull();
            assertThat(childFileNames(popup))
                    .as("Format with Prettier on the %s popup", mime)
                    .anyMatch(n -> n.contains("FormatWithPrettierAction"));
        }
    }

    private static java.util.List<String> childFileNames(Element folder) {
        java.util.List<String> names = new java.util.ArrayList<>();
        var kids = folder.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element el && "file".equals(el.getTagName())) {
                names.add(el.getAttribute("name"));
            }
        }
        return names;
    }

    private static Element folderAt(Document doc, String... path) {
        Element cur = doc.getDocumentElement();
        for (String name : path) {
            Element next = null;
            var kids = cur.getChildNodes();
            for (int i = 0; i < kids.getLength(); i++) {
                if (kids.item(i) instanceof Element el
                        && "folder".equals(el.getTagName())
                        && name.equals(el.getAttribute("name"))) {
                    next = el;
                    break;
                }
            }
            if (next == null) {
                return null;
            }
            cur = next;
        }
        return cur;
    }
}
