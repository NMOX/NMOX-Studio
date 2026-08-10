package org.nmox.studio.ui.tasks;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Task Board's house-law wirings, pinned at the source (v1.323.0).
 * Each of these is a law with a recorded incident behind it, and each
 * would fail SILENTLY if unwired — the two-proof seam rule (v1.321.0)
 * says the call sites need gates, not just the seams.
 */
class TasksLawsGateTest {

    private static String tc() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/ui/tasks/TasksTopComponent.java"));
    }

    @Test
    @DisplayName("card text renders PLAIN — a cloned repo's board is external text")
    void plainRenderWired() throws Exception {
        assertThat(tc())
                .as("the card renderer and the column header both show text"
                        + " from a file a clone can carry; <html> titles must"
                        + " paint as characters, never fetch (v1.311.0)")
                .contains("PlainTables.plain(this)")
                .contains("PlainTables.plain(new JLabel(");
    }

    @Test
    @DisplayName("the card popup targets the clicked card (v1.270.0)")
    void clickedCardWins() throws Exception {
        assertThat(tc()).contains("Popups.selectOnTrigger(list)");
    }

    @Test
    @DisplayName("destructive confirms default to NO (v1.98.0)")
    void safeDefaults() throws Exception {
        // both delete dialogs use the full ctor with NO_OPTION as the
        // initial value — count the idiom, not just its presence
        assertThat(tc().split("NotifyDescriptor\\.NO_OPTION\\);", -1).length - 1)
                .as("card delete AND column delete both carry the safe default")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("all IO rides the single lane; the EDT never touches disk")
    void ioOffEdt() throws Exception {
        String src = tc();
        assertThat(src).contains("RequestProcessor(\"nmox-tasks-io\", 1)");
        assertThat(src.split("IO_RP\\.post\\(", -1).length - 1)
                .as("load and save both post to the lane")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("a foreign edit wins over a stale gesture (never-clobber)")
    void foreignEditGuard() throws Exception {
        assertThat(tc())
                .as("every mutation checks for an outside write FIRST and"
                        + " reloads instead of overwriting it")
                .contains("TasksIO.foreignEdit(");
    }

    @Test
    @DisplayName("the window builds nothing before first show (v1.38.0)")
    void zeroBootCost() throws Exception {
        String src = tc();
        // the constructor sets name/tooltip only; UI construction and the
        // first disk read hang off componentShowing
        int ctorStart = src.indexOf("public TasksTopComponent()");
        int ctorEnd = src.indexOf('}', ctorStart);
        String ctor = src.substring(ctorStart, ctorEnd);
        assertThat(ctor).doesNotContain("buildUi").doesNotContain("reload");
        assertThat(src).contains("protected void componentShowing()");
    }
}
