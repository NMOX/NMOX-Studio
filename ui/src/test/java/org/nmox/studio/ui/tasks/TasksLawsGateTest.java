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
                        + " paint as characters, never fetch (v1.311.0). The"
                        + " renderer sets its text per paint, so the property"
                        + " on `this` is right; the column header is a one-shot"
                        + " JLabel, where the property lands too late (v2.86.0)"
                        + " — its text rides PlainText.plain instead.")
                .contains("PlainTables.plain(this)")
                .contains("new JLabel(PlainText.plain(");
    }

    @Test
    @DisplayName("the card popup targets the clicked card (v1.270.0)")
    void clickedCardWins() throws Exception {
        // v1.326.0: the listener form is INERT on a drag-enabled list
        // (measured in shipped 1.325.0 — an empty-space right-click did
        // not clear the selection, so Edit…/Delete… were dead on a card
        // the user had not left-clicked). The popup-path hook replaces it.
        assertThat(tc()).contains("Popups.popupTargetList(model)");
        assertThat(tc())
                .as("the inert form must not creep back onto this list")
                .doesNotContain("Popups.selectOnTrigger(list)");
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
    @DisplayName("the live pulse reloads FOREIGN edits only, and dies with the tab (v2.7.0)")
    void livePulseLaw() throws Exception {
        String src = tc();
        assertThat(src)
                .as("the Tasks window watches its file with the promoted core"
                        + " pulse — the v1.323.0 'no live watcher' limit is closed")
                .contains("new FilePulse(TasksIO.fileFor(boundDir)");
        assertThat(src)
                .as("the pulse callback consults the tracker FIRST: the"
                        + " studio's own atomic saves change mtime+size too,"
                        + " and reloading on a self-write would drop the"
                        + " user's selection for no reason")
                .contains("if (tracker.isForeign(mtime, size))");
        int hidden = src.indexOf("protected void componentHidden()");
        assertThat(hidden).isPositive();
        assertThat(src.substring(hidden, src.indexOf('}', hidden)))
                .as("the pulse stops when the tab hides — no daemon poll"
                        + " over a file nobody is looking at")
                .contains("stopFilePulse()");
    }

    @Test
    @DisplayName("a foreign edit wins over a stale gesture (never-clobber)")
    void foreignEditGuard() throws Exception {
        String src = tc();
        assertThat(src)
                .as("every mutation checks for an outside write and reloads"
                        + " instead of overwriting it")
                .contains("TasksIO.foreignEdit(");
        // v2.18.0: the check STATS THE DISK, so it lives on the IO lane
        // with the save, not on the EDT with the gesture (v1.108.0) —
        // the class javadoc promised this for two releases while the
        // code did three filesystem calls per card drag on the EDT
        int post = src.indexOf("IO_RP.post(() -> {",
                src.indexOf("private boolean mutate("));
        assertThat(post).as("mutate() posts its save to the IO lane").isPositive();
        assertThat(src.indexOf("TasksIO.foreignEdit(",
                src.indexOf("private boolean mutate(")))
                .as("the foreign-edit stat rides the IO lane INSIDE the"
                        + " posted save, off the EDT")
                .isGreaterThan(post);
    }

    @Test
    @DisplayName("a REFUSED mutation writes nothing and the gesture can say so (v2.18.0)")
    void refusedMutationLaw() throws Exception {
        String src = tc();
        int m = src.indexOf("private boolean mutate(");
        assertThat(m)
                .as("mutate() returns the board's verdict so each gesture"
                        + " can report a refusal instead of going mute")
                .isPositive();
        String body = src.substring(m, src.indexOf("\n    }", m));
        assertThat(body)
                .as("the refusal gate sits BEFORE the rebuild and the save:"
                        + " an edge move or double clock-in must not bump"
                        + " the checked-in file's mtime for a no-op")
                .contains("if (!mutation.getAsBoolean()) {");
        assertThat(body.indexOf("if (!mutation.getAsBoolean()) {"))
                .isLessThan(body.indexOf("rebuild()"));
        // and the refusals SPEAK: the destructive-by-side-effect clock
        // gestures each carry their outcome line (a deleted sub-minute
        // session and a silently-moved clock were both mute before)
        assertThat(src).contains("dropped as a blip")
                .contains("one clock per board")
                .contains("No clock running on that card")
                .contains("clock is already running");
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

    @Test
    @DisplayName("EVERY board mutation rides mutate() — the save path is the only path")
    void everyMutationRidesTheSavePath() throws Exception {
        // v1.324.0 review: Delete Column called board.removeColumn() DIRECTLY,
        // so the column vanished from the model while the screen kept showing
        // it and the file kept it — and because no rebuild ran, the header
        // popups still carried their OLD indices, so a second click on the
        // same "dead" menu deleted a DIFFERENT column. mutate() is what
        // repaints, saves, and checks for a foreign edit; a mutation outside
        // it is invisible until some unrelated later gesture persists it.
        // The house form is one line: mutate(() -> board.x(...)).
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "board\\.(addCard|editCard|removeCard|moveCard|addColumn"
                + "|renameColumn|setWipLimit|removeColumn|moveColumn)\\(")
                .matcher(tc());
        int checked = 0;
        while (m.find()) {
            String line = lineAt(tc(), m.start());
            assertThat(line)
                    .as("this board mutation is outside the save path — put it"
                            + " on a mutate(() -> ...) line: %s", line.strip())
                    .contains("mutate(");
            checked++;
        }
        assertThat(checked).as("the mutators are still named as written")
                .isGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("a moved card keeps focus so the keyboard gesture can repeat")
    void keyboardMoveKeepsSelection() throws Exception {
        // rebuild() discards every JList, so without this the first ⌘↓ moved
        // the card and dropped the selection — the gesture worked exactly
        // once and then needed the mouse again.
        assertThat(tc())
                .contains("focusCardId")
                .contains("requestFocusInWindow");
    }

    private static String lineAt(String src, int offset) {
        int from = src.lastIndexOf('\n', offset) + 1;
        int to = src.indexOf('\n', offset);
        return src.substring(from, to < 0 ? src.length() : to);
    }
}
