package org.nmox.studio.editor.editing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JTextArea;
import javax.swing.undo.UndoManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The action over an editor: it reads only the lines the range touches,
 * writes the answer, leaves the right selection, and says a refusal on the
 * status line instead of doing something else.
 */
class ToggleBlockCommentActionTest {

    private final List<String> said = new ArrayList<>();
    private Consumer<String> realStatus;

    @BeforeEach
    void captureStatus() {
        realStatus = ToggleBlockCommentAction.status;
        ToggleBlockCommentAction.status = said::add;
    }

    @AfterEach
    void restoreStatus() {
        ToggleBlockCommentAction.status = realStatus;
    }

    private static JTextArea editor(String mime, String text) {
        JTextArea area = new JTextArea(text);
        area.getDocument().putProperty("mimeType", mime);
        return area;
    }

    @Test
    @DisplayName("a selection in the middle of a document is wrapped where it is, and stays selected")
    void wrapsInPlace() {
        JTextArea area = editor("text/typescript", "const a = 1;\nconst b = 2;\nconst c = 3;");
        area.select(13, 25);
        ToggleBlockCommentAction.toggle(area);
        assertThat(area.getText()).isEqualTo("const a = 1;\n/* const b = 2; */\nconst c = 3;");
        assertThat(area.getSelectedText()).isEqualTo("/* const b = 2; */");
        assertThat(said).isEmpty();
        // the same press again
        ToggleBlockCommentAction.toggle(area);
        assertThat(area.getText()).isEqualTo("const a = 1;\nconst b = 2;\nconst c = 3;");
        assertThat(area.getSelectedText()).isEqualTo("const b = 2;");
    }

    @Test
    @DisplayName("with no selection the caret's line is toggled, on the last line of the document too")
    void caretLine() {
        JTextArea area = editor("text/css", "a { }\n  b { }");
        area.setCaretPosition(area.getDocument().getLength());
        ToggleBlockCommentAction.toggle(area);
        assertThat(area.getText()).isEqualTo("a { }\n  /* b { } */");
        assertThat(area.getSelectionStart()).isEqualTo(area.getSelectionEnd());
        ToggleBlockCommentAction.toggle(area);
        assertThat(area.getText()).isEqualTo("a { }\n  b { }");
    }

    @Test
    @DisplayName("a refusal names the delimiter and leaves the text exactly as it was")
    void refusalSpeaksAndWritesNothing() {
        String text = "a = 1; /* old */ b = 2;";
        JTextArea area = editor("text/javascript", text);
        area.selectAll();
        ToggleBlockCommentAction.toggle(area);
        assertThat(area.getText()).isEqualTo(text);
        assertThat(said).hasSize(1);
        assertThat(said.get(0)).contains("*/").startsWith("Toggle Block Comment");
    }

    @Test
    @DisplayName("a language with no block comment says so, in the words the product promised")
    void noBlockCommentSpeaks() {
        JTextArea area = editor("text/x-python", "x = 1");
        area.selectAll();
        ToggleBlockCommentAction.toggle(area);
        assertThat(area.getText()).isEqualTo("x = 1");
        assertThat(said).containsExactly("This language has no block comment");
    }

    @Test
    @DisplayName("a read-only editor is refused, not edited")
    void readOnlyIsRefused() {
        JTextArea area = editor("text/javascript", "x");
        area.setEditable(false);
        area.selectAll();
        ToggleBlockCommentAction.toggle(area);
        assertThat(area.getText()).isEqualTo("x");
        assertThat(said).hasSize(1);
        assertThat(said.get(0)).contains("read-only");
    }

    @Test
    @DisplayName("a selection past the bound is refused before it is copied")
    void tooLargeIsRefused() {
        String big = "x".repeat(ToggleBlockCommentAction.MAX_RANGE + 1);
        JTextArea area = editor("text/javascript", big);
        area.selectAll();
        ToggleBlockCommentAction.toggle(area);
        assertThat(area.getDocument().getLength()).isEqualTo(big.length());
        assertThat(said).hasSize(1);
        assertThat(said.get(0)).contains("too large");
    }

    @Test
    @DisplayName("in a Vue component the script block is commented as JavaScript, read from the text before the range")
    void markupBlockIsReadFromTheDocument() {
        JTextArea area = editor("text/x-vue", "<template>\n<p>a</p>\n</template>\n<script>\nconst n = 1;\n</script>\n");
        int line = area.getText().indexOf("const");
        area.select(line, line + "const n = 1;".length());
        ToggleBlockCommentAction.toggle(area);
        assertThat(area.getText()).contains("<script>\n/* const n = 1; */\n</script>");
        area.setCaretPosition(area.getText().indexOf("<p>") + 1);
        ToggleBlockCommentAction.toggle(area);
        assertThat(area.getText()).contains("<!-- <p>a</p> -->");
    }

    @Test
    @DisplayName("a wrap is undone by the edits it made and nothing else")
    void undoRestoresTheText() {
        JTextArea area = editor("text/javascript", "let a;");
        UndoManager undo = new UndoManager();
        area.getDocument().addUndoableEditListener(undo);
        area.selectAll();
        ToggleBlockCommentAction.toggle(area);
        assertThat(area.getText()).isEqualTo("/* let a; */");
        while (undo.canUndo()) {
            undo.undo();
        }
        assertThat(area.getText()).isEqualTo("let a;");
    }
}
