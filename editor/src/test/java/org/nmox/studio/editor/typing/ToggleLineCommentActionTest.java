package org.nmox.studio.editor.typing;

import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toggle-line-comment is symmetric: comment a block, toggle again, and
 * the original text returns. The tricky parts are the uniform indent
 * column, blank-line skipping, and the "all commented?" decision that
 * chooses direction. These pin all of it against a plain document.
 */
class ToggleLineCommentActionTest {

    private static PlainDocument doc(String text) throws BadLocationException {
        PlainDocument d = new PlainDocument();
        d.insertString(0, text, null);
        return d;
    }

    private static String text(PlainDocument d) throws BadLocationException {
        return d.getText(0, d.getLength());
    }

    /** Toggle over the whole buffer. */
    private static void toggleAll(PlainDocument d, String prefix) throws BadLocationException {
        ToggleLineCommentAction.toggle(d, 0, d.getLength(), prefix);
    }

    @Test
    @DisplayName("A single uncommented line gains 'prefix ' at its indent")
    void commentsOne() throws BadLocationException {
        PlainDocument d = doc("  const x = 1;");
        toggleAll(d, "//");
        assertThat(text(d)).isEqualTo("  // const x = 1;");
    }

    @Test
    @DisplayName("Toggling a commented line removes the prefix and its following space")
    void uncommentsOne() throws BadLocationException {
        PlainDocument d = doc("  // const x = 1;");
        toggleAll(d, "//");
        assertThat(text(d)).isEqualTo("  const x = 1;");
    }

    @Test
    @DisplayName("Comment then toggle again round-trips to the original")
    void roundTrips() throws BadLocationException {
        String original = "function f() {\n    return 1;\n}";
        PlainDocument d = doc(original);
        toggleAll(d, "//");
        // the prefix lands at each line's first non-space column, so the
        // indented body keeps its indent with '// ' after it
        assertThat(text(d)).isEqualTo("// function f() {\n    // return 1;\n// }");
        toggleAll(d, "//");
        assertThat(text(d)).isEqualTo(original);
    }

    @Test
    @DisplayName("A mixed block (some lines commented) comments the rest — not uncomment")
    void mixedBlockComments() throws BadLocationException {
        // one line already commented, one not: the block is not "all commented",
        // so the whole block gets commented (the uncommented line gains a prefix,
        // the commented line gains a second prefix)
        PlainDocument d = doc("// a\nb");
        toggleAll(d, "//");
        assertThat(text(d)).isEqualTo("// // a\n// b");
    }

    @Test
    @DisplayName("Blank lines are skipped entirely — no stray prefixes")
    void blankLinesSkipped() throws BadLocationException {
        PlainDocument d = doc("a\n\nb");
        toggleAll(d, "//");
        assertThat(text(d)).isEqualTo("// a\n\n// b");
    }

    @Test
    @DisplayName("Uncomment tolerates a prefix with no following space")
    void uncommentWithoutSpace() throws BadLocationException {
        PlainDocument d = doc("//tight");
        toggleAll(d, "//");
        assertThat(text(d)).isEqualTo("tight");
    }

    @Test
    @DisplayName("Hash-comment languages use their own prefix")
    void hashPrefix() throws BadLocationException {
        PlainDocument d = doc("x = 1");
        toggleAll(d, "#");
        assertThat(text(d)).isEqualTo("# x = 1");
        toggleAll(d, "#");
        assertThat(text(d)).isEqualTo("x = 1");
    }

    @Test
    @DisplayName("A selection ending exactly at a line start does not include that line")
    void selectionBoundaryExcludesNextLine() throws BadLocationException {
        PlainDocument d = doc("first\nsecond\n");
        // select through the newline after 'first' (offset 6 = start of 'second')
        ToggleLineCommentAction.toggle(d, 0, 6, "//");
        assertThat(text(d)).isEqualTo("// first\nsecond\n");
    }

    @Test
    @DisplayName("Indentation is preserved and the prefix lands at the first non-space column")
    void preservesIndent() throws BadLocationException {
        PlainDocument d = doc("        deeplyIndented();");
        toggleAll(d, "//");
        assertThat(text(d)).isEqualTo("        // deeplyIndented();");
    }
}
