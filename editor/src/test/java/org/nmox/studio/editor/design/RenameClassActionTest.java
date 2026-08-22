package org.nmox.studio.editor.design;

import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rename's caret-identity rule (v2.30.1 review): the caret must
 * come from the file the popup was invoked on —
 * {@code lastFocusedComponent} can belong to the OTHER half of a split
 * editor, and a rename computed on the wrong file's token is the
 * v1.270.0 clicked-item-wins class one gesture over. The rule is pure
 * given the document's stream description; the wiring consults it for
 * the focused component and every fallback candidate.
 */
class RenameClassActionTest {

    @Test
    @DisplayName("a document with no stream description never matches")
    void noStreamDescription() {
        assertThat(RenameClassAction.documentBelongsTo(new PlainDocument(), null))
                .isFalse();
        assertThat(RenameClassAction.documentBelongsTo(null, null)).isFalse();
    }

    @Test
    @DisplayName("the wiring consults the identity rule at both resolution sites")
    void wiringConsultsTheRule() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/editor/design/RenameClassAction.java"))
                .replace("\r\n", "\n");
        int actionAt = src.indexOf("public void actionPerformed");
        String body = src.substring(actionAt, src.indexOf("private static int[] spanInMarkup"));
        assertThat(body)
                .as("the focused component is identity-checked")
                .contains("!documentBelongsTo(comp.getDocument(), context)");
        assertThat(body)
                .as("fallback candidates are identity-checked")
                .contains("documentBelongsTo(candidate.getDocument(), context)");
    }

    @Test
    @DisplayName("a foreign stream description refuses (the split-editor case, reduced)")
    void foreignDescriptionRefuses() {
        Document doc = new PlainDocument();
        doc.putProperty(Document.StreamDescriptionProperty, "not-a-dataobject");
        assertThat(RenameClassAction.documentBelongsTo(doc, null)).isFalse();
    }
}
