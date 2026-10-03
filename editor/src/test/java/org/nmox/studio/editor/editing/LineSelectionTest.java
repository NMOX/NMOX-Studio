package org.nmox.studio.editor.editing;

import javax.swing.JTextArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.editing.LineSelection.Lines;
import org.nmox.studio.editor.editing.LineSelection.Range;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Expand Line Selection: whole lines, the terminator included, one more
 * line per press, and never past the end of the text.
 */
class LineSelectionTest {

    private static String selected(String text, Range r) {
        return text.substring(r.start(), r.end());
    }

    @Test
    @DisplayName("the first press selects the caret's line with its terminator")
    void firstPress() {
        String text = "one\ntwo\nthree";
        Range r = LineSelection.expand(LineSelection.of(text), 5, 5);
        assertThat(selected(text, r)).isEqualTo("two\n");
    }

    @Test
    @DisplayName("pressed again it takes the next line, and again the one after")
    void eachPressAddsALine() {
        String text = "one\ntwo\nthree\nfour";
        Lines lines = LineSelection.of(text);
        Range first = LineSelection.expand(lines, 1, 1);
        Range second = LineSelection.expand(lines, first.start(), first.end());
        Range third = LineSelection.expand(lines, second.start(), second.end());
        assertThat(selected(text, first)).isEqualTo("one\n");
        assertThat(selected(text, second)).isEqualTo("one\ntwo\n");
        assertThat(selected(text, third)).isEqualTo("one\ntwo\nthree\n");
    }

    @Test
    @DisplayName("a selection inside several lines grows to all of them")
    void partialSelectionGrowsToWholeLines() {
        String text = "one\ntwo\nthree";
        Range r = LineSelection.expand(LineSelection.of(text), 2, 6);
        assertThat(selected(text, r)).isEqualTo("one\ntwo\n");
        // the direction the selection was made in does not matter
        assertThat(LineSelection.expand(LineSelection.of(text), 6, 2)).isEqualTo(r);
    }

    @Test
    @DisplayName("the last line has no terminator to take: the selection ends with the text, and stays there")
    void lastLineWithoutATerminator() {
        String text = "one\ntwo";
        // a Swing document's line table answers one PAST the text for its last line
        Lines swing = new Lines() {
            @Override
            public int length() {
                return text.length();
            }

            @Override
            public int lineStart(int offset) {
                return offset >= 4 ? 4 : 0;
            }

            @Override
            public int lineEnd(int offset) {
                return offset >= 4 ? text.length() + 1 : 4;
            }
        };
        Range r = LineSelection.expand(swing, 5, 5);
        assertThat(r).isEqualTo(new Range(4, 7));
        assertThat(selected(text, r)).isEqualTo("two");
        assertThat(LineSelection.expand(swing, r.start(), r.end())).isEqualTo(r);
    }

    @Test
    @DisplayName("CRLF is taken whole")
    void crlf() {
        String text = "one\r\ntwo\r\n";
        Range r = LineSelection.expand(LineSelection.of(text), 0, 0);
        assertThat(selected(text, r)).isEqualTo("one\r\n");
        assertThat(selected(text, LineSelection.expand(LineSelection.of(text), r.start(), r.end())))
                .isEqualTo("one\r\ntwo\r\n");
    }

    @Test
    @DisplayName("an empty text and offsets outside the text are answered, not thrown")
    void edges() {
        assertThat(LineSelection.expand(LineSelection.of(""), 0, 0)).isEqualTo(new Range(0, 0));
        assertThat(LineSelection.expand(LineSelection.of("ab"), -3, 40)).isEqualTo(new Range(0, 2));
    }

    @Test
    @DisplayName("in an editor: the line is selected with the caret at its end, and the last line stops at the text's end")
    void inAnEditor() {
        JTextArea area = new JTextArea("alpha\nbeta\ngamma");
        area.setCaretPosition(7);
        ExpandLineSelectionAction.expand(area);
        assertThat(area.getSelectedText()).isEqualTo("beta\n");
        // anchored at the line start, the caret after the terminator
        assertThat(area.getCaret().getMark()).isEqualTo(6);
        assertThat(area.getCaret().getDot()).isEqualTo(11);
        ExpandLineSelectionAction.expand(area);
        assertThat(area.getSelectedText()).isEqualTo("beta\ngamma");
        assertThat(area.getCaret().getDot()).isEqualTo(area.getDocument().getLength());
        ExpandLineSelectionAction.expand(area);
        assertThat(area.getSelectedText()).isEqualTo("beta\ngamma");
    }
}
