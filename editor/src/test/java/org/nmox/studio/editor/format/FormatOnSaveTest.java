package org.nmox.studio.editor.format;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import javax.swing.text.Position;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The minimal-edit apply is what keeps the caret in place across a
 * save; these tests pin its correctness on every shape of change and
 * the mime coverage of the on-save registration.
 */
class FormatOnSaveTest {

    private static PlainDocument doc(String text) throws BadLocationException {
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, text, null);
        return doc;
    }

    private static String text(PlainDocument doc) throws BadLocationException {
        return doc.getText(0, doc.getLength());
    }

    @Test
    @DisplayName("A middle-of-file change leaves surrounding Positions untouched")
    void middleChangeKeepsPositions() throws BadLocationException {
        PlainDocument doc = doc("aaa MIDDLE zzz");
        Position beforeChange = doc.createPosition(2);   // inside 'aaa'
        Position afterChange = doc.createPosition(12);   // inside 'zzz'

        FormatOnSave.applyMinimalEdit(doc, "aaa MIDDLE zzz", "aaa CENTER zzz");

        assertThat(text(doc)).isEqualTo("aaa CENTER zzz");
        assertThat(beforeChange.getOffset()).isEqualTo(2);
        assertThat(afterChange.getOffset()).isEqualTo(12);
    }

    @Test
    @DisplayName("Pure insertion and pure deletion both apply cleanly")
    void insertionAndDeletion() throws BadLocationException {
        PlainDocument doc = doc("const x=1\n");
        FormatOnSave.applyMinimalEdit(doc, "const x=1\n", "const x = 1;\n");
        assertThat(text(doc)).isEqualTo("const x = 1;\n");

        PlainDocument shrink = doc("a  =  1");
        FormatOnSave.applyMinimalEdit(shrink, "a  =  1", "a = 1");
        assertThat(text(shrink)).isEqualTo("a = 1");
    }

    @Test
    @DisplayName("Identical text is a strict no-op — no remove, no insert, no undo entry")
    void identicalTextIsNoOp() throws BadLocationException {
        PlainDocument doc = doc("same");
        boolean[] mutated = {false};
        doc.addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                mutated[0] = true;
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                mutated[0] = true;
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
            }
        });

        FormatOnSave.applyMinimalEdit(doc, "same", "same");

        assertThat(mutated[0]).isFalse();
    }

    @Test
    @DisplayName("Overlapping prefix and suffix (aaa → aa) does not double-count shared chars")
    void overlappingPrefixSuffix() throws BadLocationException {
        PlainDocument doc = doc("aaa");
        FormatOnSave.applyMinimalEdit(doc, "aaa", "aa");
        assertThat(text(doc)).isEqualTo("aa");

        PlainDocument grow = doc("aa");
        FormatOnSave.applyMinimalEdit(grow, "aa", "aaa");
        assertThat(text(grow)).isEqualTo("aaa");
    }

    @Test
    @DisplayName("A complete rewrite still lands exactly")
    void completeRewrite() throws BadLocationException {
        PlainDocument doc = doc("entirely old content");
        FormatOnSave.applyMinimalEdit(doc, "entirely old content", "brand new");
        assertThat(text(doc)).isEqualTo("brand new");
    }

    @Test
    @DisplayName("Empty-to-text and text-to-empty are handled")
    void emptyEdges() throws BadLocationException {
        PlainDocument doc = doc("");
        FormatOnSave.applyMinimalEdit(doc, "", "content\n");
        assertThat(text(doc)).isEqualTo("content\n");

        PlainDocument drain = doc("gone");
        FormatOnSave.applyMinimalEdit(drain, "gone", "");
        assertThat(text(drain)).isEmpty();
    }

    /**
     * Reads the annotation-processor-generated layer rather than the
     * (source-retention) annotations: this is the artifact the platform
     * actually loads, and the one that silently vanishes when annotation
     * processing is misconfigured.
     */
    @Test
    @DisplayName("The save hook is registered for every Prettier-formattable mime the IDE edits")
    void mimeCoverage() throws Exception {
        Set<String> mimes = new HashSet<>();
        try (InputStream layer = FormatOnSave.class
                .getResourceAsStream("/META-INF/generated-layer.xml")) {
            assertThat(layer).as("generated-layer.xml must exist (annotation processing ran)")
                    .isNotNull();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            collectOnSaveMimes(dbf.newDocumentBuilder().parse(layer).getDocumentElement(),
                    "", mimes);
        }

        assertThat(mimes).contains(
                "text/javascript", "text/typescript",
                "text/css", "text/x-scss", "text/x-less",
                "text/html", "text/x-json", "text/x-yaml",
                "text/x-markdown", "text/x-vue", "text/x-graphql");
    }

    /** Collects Editors/&lt;mime&gt;/OnSave paths that register our factory. */
    private static void collectOnSaveMimes(Element element, String path, Set<String> mimes) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element el)) {
                continue;
            }
            if ("folder".equals(el.getTagName())) {
                collectOnSaveMimes(el, path + "/" + el.getAttribute("name"), mimes);
            } else if ("file".equals(el.getTagName())
                    && el.getAttribute("name").contains("FormatOnSave")
                    && path.startsWith("/Editors/") && path.endsWith("/OnSave")) {
                mimes.add(path.substring("/Editors/".length(),
                        path.length() - "/OnSave".length()));
            }
        }
    }
}
