package org.nmox.studio.tools.vscode;

import java.nio.file.Path;
import java.util.List;
import javax.swing.JTextArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.tools.vscode.VsCodeTasks.EditorContext;
import org.nmox.studio.tools.vscode.VsCodeTasks.InputDef;
import org.nmox.studio.tools.vscode.VsCodeTasks.InputOption;
import org.openide.windows.TopComponent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two thin Swing halves beside the task resolver: what is read from
 * an editor pane for {@code ${lineNumber}} and {@code ${selectedText}},
 * and what a question's dialog shows.
 */
class VsCodeTaskEditorTest {

    private static final Path FILE = Path.of("src", "a.js").toAbsolutePath();

    @Test
    @DisplayName("the line and the column are the caret's, counted from 1; the selection is the pane's")
    void caretAndSelection() {
        JTextArea pane = new JTextArea("first\nsecond line\nthird");
        pane.setCaretPosition(0);
        assertThat(VsCodeTaskEditor.context(FILE, pane)).isEqualTo(new EditorContext(FILE, 1, 1, null));

        pane.setCaretPosition("first\nsec".length());
        assertThat(VsCodeTaskEditor.context(FILE, pane)).isEqualTo(new EditorContext(FILE, 2, 4, null));

        pane.select("first\n".length(), "first\nsecond".length());
        EditorContext selected = VsCodeTaskEditor.context(FILE, pane);
        assertThat(selected.selectedText()).isEqualTo("second");
        assertThat(selected.line()).as("the caret is the end of a forward selection").isEqualTo(2);
        assertThat(selected.column()).isEqualTo(7);

        pane.setCaretPosition(pane.getDocument().getLength());
        assertThat(VsCodeTaskEditor.context(null, pane))
                .as("a buffer with no file on disk still has a caret")
                .isEqualTo(new EditorContext(null, 3, 6, null));
    }

    @Test
    @DisplayName("a selection is read only as far as it takes to know it is too long")
    void selectionIsBounded() {
        JTextArea pane = new JTextArea("x".repeat(VsCodeTasks.MAX_SELECTED_TEXT * 3));
        pane.selectAll();
        assertThat(VsCodeTaskEditor.context(FILE, pane).selectedText())
                .hasSize(VsCodeTasks.MAX_SELECTED_TEXT + 1);
        pane.select(0, VsCodeTasks.MAX_SELECTED_TEXT);
        assertThat(VsCodeTaskEditor.context(FILE, pane).selectedText()).hasSize(VsCodeTasks.MAX_SELECTED_TEXT);
    }

    @Test
    @DisplayName("a tab with no text editor gives its file and no caret; no tab gives nothing")
    void noPane() {
        assertThat(VsCodeTaskEditor.context(FILE, null)).isEqualTo(new EditorContext(FILE, 0, 0, null));
        assertThat(VsCodeTaskEditor.context(null, null)).isEqualTo(EditorContext.NONE);
    }

    @Test
    @DisplayName("with no editor tab open the snapshot is no editor, from the event thread or any other")
    void nothingOpen() throws Exception {
        assertThat(VsCodeTaskEditor.snapshot()).as("off the event thread: read there and waited for")
                .isEqualTo(EditorContext.NONE);
        EditorContext[] onEdt = new EditorContext[1];
        java.awt.EventQueue.invokeAndWait(() -> onEdt[0] = VsCodeTaskEditor.snapshot());
        assertThat(onEdt[0]).isEqualTo(EditorContext.NONE);
    }

    @Test
    @DisplayName("the editor is the focused tab when it is an editor tab, else the editor area's selected tab")
    void whichTab() {
        TopComponent focused = new TopComponent();
        TopComponent showing = new TopComponent();
        assertThat(VsCodeTaskEditor.activeEditor(focused, true, showing)).isSameAs(focused);
        assertThat(VsCodeTaskEditor.activeEditor(focused, false, showing))
                .as("focus in the Projects window: the file showing in the editor area").isSameAs(showing);
        assertThat(VsCodeTaskEditor.activeEditor(null, false, showing)).isSameAs(showing);
        assertThat(VsCodeTaskEditor.activeEditor(focused, false, null)).isNull();
    }

    @Test
    @DisplayName("a question shows its description (its id when there is none), VS Code's label: value, the default chosen — all as text")
    void whatAQuestionShows() {
        InputDef pick = new InputDef("env", "pickString", "Which environment?", "prod", false,
                List.of(new InputOption(null, "dev"), new InputOption("Production", "prod"),
                        new InputOption("<html><img src='http://x/'>", "<html>evil")), null);
        assertThat(VsCodeTaskPrompts.question(pick)).isEqualTo("Which environment?");
        assertThat(VsCodeTaskPrompts.question(new InputDef("env", "pickString", " ", null, false, List.of(), null)))
                .isEqualTo("env");
        assertThat(VsCodeTaskPrompts.shown(pick.options()))
                .as("an option that begins like markup is set off, so the list paints it as characters")
                .containsExactly("dev", "Production: prod", " <html><img src='http://x/'>: <html>evil");
        assertThat(VsCodeTaskPrompts.defaultIndex(pick)).isEqualTo(1);
        assertThat(VsCodeTaskPrompts.defaultIndex(new InputDef("env", "pickString", "x", "staging", false,
                pick.options(), null))).as("a default that is not an option: the first").isZero();
        assertThat(VsCodeTaskPrompts.defaultIndex(new InputDef("env", "pickString", "x", null, false,
                pick.options(), null))).isZero();
    }
}
