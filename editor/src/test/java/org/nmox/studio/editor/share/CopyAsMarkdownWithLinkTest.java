package org.nmox.studio.editor.share;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.text.PlainDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CopyAsMarkdownWithLinkTest {

    @Test
    @DisplayName("a selection's line range is 1-based inclusive; a selection ending on a newline does not claim the next line")
    void lineRange() throws Exception {
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, "a\nbb\nccc\ndddd\n", null);
        assertThat(CopyAsMarkdown.lineRange(doc, 0, 0)).containsExactly(0, 0);           // nothing selected: whole file
        assertThat(CopyAsMarkdown.lineRange(doc, 2, 4)).containsExactly(2, 2);           // "bb"
        assertThat(CopyAsMarkdown.lineRange(doc, 2, 5)).containsExactly(2, 2);           // "bb\n" — the newline is line 2's
        assertThat(CopyAsMarkdown.lineRange(doc, 2, 6)).containsExactly(2, 3);           // "bb\nc"
        assertThat(CopyAsMarkdown.lineRange(doc, 0, doc.getLength())).containsExactly(1, 4);
    }

    @Test
    @DisplayName("the git reads ride the RP and the clipboard write follows them; the action is on the popup AND the Edit menu")
    void wiring() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/share/CopyAsMarkdownWithLinkAction.java"));
        assertThat(src).contains("path = \"Editors/Popup\"").contains("path = \"Menu/Edit\"").contains("RP.post(");
        assertThat(src.indexOf("resolve(file")).isGreaterThan(src.indexOf("RP.post("));
        assertThat(src.indexOf("setContents(")).isGreaterThan(src.indexOf("resolve(file"));
        assertThat(src).doesNotContain("ProcessBuilder").doesNotContain("Runtime.getRuntime");
    }

    @Test
    @DisplayName("an unsaved buffer refuses — the block would be the buffer while the link names the committed file")
    void unsavedBufferRefuses() throws Exception {
        assertThat(CopyAsMarkdownWithLinkAction.unsaved("not a DataObject")).isFalse();
        assertThat(CopyAsMarkdownWithLinkAction.unsaved(null)).isFalse();
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/share/CopyAsMarkdownWithLinkAction.java"));
        // the refusal sits BEFORE any clipboard write and reads the DataObject's own modified flag
        assertThat(src).contains("dob.isModified()").contains("has unsaved changes");
        assertThat(src.indexOf("if (unsaved(sd))")).isLessThan(src.indexOf("RP.post("));
    }
}
