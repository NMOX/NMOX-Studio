package org.nmox.studio.apiclient.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.195.0 editor gesture, gated at two depths: the ANNOTATIONS on
 * the action (source) and the REGISTRATION the annotation processor
 * actually emitted (the generated layer in target/classes — the
 * v1.146.0 byte-verification discipline: a wrong path compiles fine
 * and simply never appears in any menu).
 */
class HttpEditorGestureTest {

    @Test
    @DisplayName("The action is registered on the .http editor popup and the file node menu")
    void generatedLayerCarriesBothHomes() throws Exception {
        Path layer = Path.of("target/classes/META-INF/generated-layer.xml");
        assertThat(layer).as("annotation processing ran (-proc:full is load-bearing)").exists();
        // the layer nests paths as <folder> elements — walk them, don't
        // grep: a flat-path substring never appears in layer XML
        var dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        org.w3c.dom.Document doc;
        try (var in = Files.newInputStream(layer)) {
            doc = dbf.newDocumentBuilder().parse(in);
        }
        assertThat(folderAt(doc, "Editors", "text", "x-http-request", "Popup"))
                .as("editor popup home").isNotNull();
        assertThat(folderAt(doc, "Loaders", "text", "x-http-request", "Actions"))
                .as("file node menu home").isNotNull();
        assertThat(Files.readString(layer)).contains("OpenInApiStudioAction");
    }

    private static org.w3c.dom.Element folderAt(org.w3c.dom.Document doc, String... path) {
        org.w3c.dom.Element cur = doc.getDocumentElement();
        for (String name : path) {
            org.w3c.dom.Element next = null;
            var kids = cur.getChildNodes();
            for (int i = 0; i < kids.getLength(); i++) {
                if (kids.item(i) instanceof org.w3c.dom.Element el
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

    @Test
    @DisplayName("The gesture reuses the chooser path: one importHttpFrom, both callers")
    void gestureReusesTheChooserPath() throws Exception {
        String tc = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/apiclient/ui/ApiClientTopComponent.java"));
        // the chooser path and the editor gesture must share ONE
        // implementation — a fix to either (off-EDT read, refusals,
        // secrets lift) reaches both by construction
        int first = tc.indexOf("importHttpFrom(");
        int last = tc.lastIndexOf("importHttpFrom(");
        assertThat(first).isGreaterThan(-1);
        assertThat(last).as("at least two call/definition sites share the method")
                .isGreaterThan(first);
        String action = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/apiclient/ui/OpenInApiStudioAction.java"));
        assertThat(action).contains("importHttpFileFromEditor(file)");
    }
}
