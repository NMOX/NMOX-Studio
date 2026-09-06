package org.nmox.studio.ui.shots;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.windows.TopComponent;

import static org.assertj.core.api.Assertions.assertThat;

class SaveEditorScreenshotActionTest {

    @Test
    @DisplayName("the activated window wins when it is an editor tab")
    void activatedEditorWins() {
        TopComponent activated = new TopComponent();
        TopComponent selected = new TopComponent();
        assertThat(SaveEditorScreenshotAction.selectedEditor(activated, true, selected)).isSameAs(activated);
    }

    @Test
    @DisplayName("focus in a tool window falls back to the editor area's selected tab — the tab you are looking at")
    void toolWindowFallsBackToSelection() {
        TopComponent navigator = new TopComponent();
        TopComponent selected = new TopComponent();
        assertThat(SaveEditorScreenshotAction.selectedEditor(navigator, false, selected)).isSameAs(selected);
        assertThat(SaveEditorScreenshotAction.selectedEditor(null, false, selected)).isSameAs(selected);
    }

    @Test
    @DisplayName("nothing in the editor area is null — the action refuses out loud rather than painting a blank")
    void nothingOpenIsNull() {
        assertThat(SaveEditorScreenshotAction.selectedEditor(new TopComponent(), false, null)).isNull();
        assertThat(SaveEditorScreenshotAction.selectedEditor(null, false, null)).isNull();
    }

    @Test
    @DisplayName("a tab without a document names the shot after itself; a nameless one is blank, never null")
    void documentNameFallsBackToTabName() {
        TopComponent studio = new TopComponent();
        studio.setName("Task Board");
        assertThat(SaveEditorScreenshotAction.documentName(studio)).isEqualTo("Task Board");
        assertThat(SaveEditorScreenshotAction.documentName(new TopComponent())).isEqualTo("");
    }
}
