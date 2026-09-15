package org.nmox.studio.editor.debug;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every mime that registers DAP breakpoints can toggle one with a gutter click (v2.155.0).
 *
 * <p>{@code @RegisterDAPDebugger} generates two entries per mime: the
 * {@code RegisterDAPBreakpoints} instance and a
 * {@code GlyphGutterActions/…ToggleBreakpointAction.shadow} at position 500.
 * The editor layer registers the instance by hand and carried only that half,
 * so ⌘F8 set a breakpoint and a click in the gutter (the gesture the user guide
 * teaches in every language) did nothing. The population is derived: any
 * {@code Editors/<mime>} folder holding a breakpoints instance must hold the
 * gutter action too, so a language added tomorrow cannot repeat the gap.
 */
class DapGutterToggleRegisteredTest {

    private static final Path LAYER =
            Path.of("src/main/resources/org/nmox/studio/editor/layer.xml");
    private static final String INSTANCE =
            "org.netbeans.modules.lsp.client.debugger.api.RegisterDAPBreakpoints.newInstance";
    private static final String TOGGLE =
            "Actions/Debug/org-netbeans-modules-debugger-ui-actions-ToggleBreakpointAction.instance";

    @Test
    @DisplayName("a mime with DAP breakpoints toggles them from the gutter, at the processor's position")
    void everyDapMimeHasTheGutterToggle() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setExpandEntityReferences(false);
        Document doc;
        try (InputStream in = Files.newInputStream(LAYER)) {
            doc = f.newDocumentBuilder().parse(in);
        }
        List<String> dapMimes = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Element file : elements(doc.getDocumentElement(), "file")) {
            if (!INSTANCE.equals(attr(file, "instanceCreate"))) {
                continue;
            }
            Element mimeFolder = (Element) file.getParentNode();
            String mime = path(mimeFolder);
            dapMimes.add(mime);
            boolean toggle = false;
            for (Node n = mimeFolder.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element folder && "folder".equals(folder.getTagName())
                        && "GlyphGutterActions".equals(folder.getAttribute("name"))) {
                    for (Element shadow : elements(folder, "file")) {
                        if (TOGGLE.equals(attr(shadow, "originalFile"))
                                && "500".equals(attr(shadow, "position"))) {
                            toggle = true;
                        }
                    }
                }
            }
            if (!toggle) {
                missing.add(mime);
            }
        }
        assertThat(dapMimes).as("the DAP mimes the layer registers")
                .contains("Editors/text/javascript", "Editors/text/typescript",
                        "Editors/text/x-python", "Editors/text/x-go");
        assertThat(missing).as("a DAP mime whose gutter click cannot toggle a breakpoint").isEmpty();
    }

    private static List<Element> elements(Element root, String tag) {
        List<Element> out = new ArrayList<>();
        var list = root.getElementsByTagName(tag);
        for (int i = 0; i < list.getLength(); i++) {
            out.add((Element) list.item(i));
        }
        return out;
    }

    /** The value of a child {@code <attr name=…>}, whichever value attribute it uses. */
    private static String attr(Element file, String name) {
        for (Node n = file.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element a && "attr".equals(a.getTagName()) && name.equals(a.getAttribute("name"))) {
                for (String kind : List.of("stringvalue", "methodvalue", "intvalue")) {
                    if (a.hasAttribute(kind)) {
                        return a.getAttribute(kind);
                    }
                }
            }
        }
        return null;
    }

    private static String path(Element folder) {
        StringBuilder sb = new StringBuilder();
        for (Node n = folder; n instanceof Element e && "folder".equals(e.getTagName()); n = n.getParentNode()) {
            sb.insert(0, "/" + e.getAttribute("name"));
        }
        return sb.substring(1);
    }
}
