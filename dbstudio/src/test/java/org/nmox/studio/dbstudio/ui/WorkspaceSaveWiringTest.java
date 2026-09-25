package org.nmox.studio.dbstudio.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source gate for the debt-#16 wiring (the v1.38.0 idiom: a contract
 * the UI can silently drop is pinned against the source). SaveLaneTest
 * proves the lane's behavior; this proves the studio actually rides it:
 * the EDT half only snapshots and queues, the write and its self-write
 * stamp live in one lane task, the watcher's stat rides the same lane,
 * and componentClosed drains it. A future edit that quietly reverts to
 * a synchronous EDT write — or stamps outside the write task — fails
 * here by name.
 */
class WorkspaceSaveWiringTest {

    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/dbstudio/ui/DbStudioTopComponent.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("saveWorkspace() snapshots on the EDT and queues the write on the lane")
    void saveQueuesOnTheLane() throws Exception {
        String src = source();
        assertThat(src).contains("SAVES.save(() -> writeSnapshot(");
        assertThat(src)
                .as("no synchronous EDT write may remain — the lane is the only writer")
                .doesNotContain("DbWorkspaceIO.save(");
    }

    @Test
    @DisplayName("the write and its self-write stamp are one lane task")
    void writeAndStampAreOneTask() throws Exception {
        String src = source();
        int start = src.indexOf("private void writeSnapshot");
        assertThat(start).as("writeSnapshot exists").isPositive();
        String body = src.substring(start, src.indexOf("\n    private", start + 1));
        assertThat(body).contains("AtomicFiles.writeString(");
        assertThat(body)
                .as("the stamp must be taken by the SAME task that writes")
                .contains("externalEdits.recordOwn(");
    }

    @Test
    @DisplayName("one file is watched with one stat a poll, not a walk of the whole project")
    void workspaceFileIsPulsedNotWalked() throws Exception {
        String src = source();
        assertThat(src).contains("new org.nmox.studio.core.util.FilePulse(workspaceFile")
                .doesNotContain("FileWatcher.forFilenames(");
    }

    @Test
    @DisplayName("the watcher's stat and the deferred re-check queue behind writes on the same lane")
    void classificationRidesTheLane() throws Exception {
        String src = source();
        // both foreign-edit stats: the FileWatcher report and the
        // deferred re-check after a dialog/run releases the studio
        assertThat(src.split("SAVES\\.classify\\(", -1).length - 1)
                .as("both stat paths must ride the lane")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("a file we never read is never ours, and never written over")
    void unreadableWorkspaceIsBoundReadOnly() throws Exception {
        String src = source();
        // The seam (loadWorkspaceGuarded reporting unreadable) has its own
        // tests; this is the OTHER half the v1.321.0 law asks for — that the
        // studio consults it. Here the two halves are especially independent,
        // because saveWorkspace() writes without consulting the stamp at all:
        // skipping recordOwn alone would NOT have stopped the loss.
        int apply = src.indexOf("private void applyReloadedWorkspace(");
        assertThat(apply).isPositive();
        String applyBody = src.substring(apply, src.indexOf("\n    }", apply));
        assertThat(applyBody)
                .as("ownership is recorded for a read that HAPPENED")
                .contains("workspaceReadOnly = outcome.unreadable()");
        assertThat(applyBody.indexOf("workspaceReadOnly = outcome.unreadable()"))
                .as("the verdict is taken before the stamp it guards")
                .isLessThan(applyBody.indexOf("externalEdits.recordOwn("));

        int save = src.indexOf("private void saveWorkspace()");
        assertThat(save).isPositive();
        String saveBody = src.substring(save, src.indexOf("\n    }", save));
        assertThat(saveBody)
                .as("the write refuses OUT LOUD on a read-only workspace:"
                        + " writing the stand-in lists would replace every"
                        + " connection, saved query and history row with"
                        + " nothing (9,437,184 bytes → 71, measured)")
                .contains("if (workspaceReadOnly) {")
                .contains("Bundle.DbStudioTopComponent_workspaceReadOnly(");
        assertThat(saveBody.indexOf("if (workspaceReadOnly) {"))
                .as("the refusal comes before the snapshot is even taken")
                .isLessThan(saveBody.indexOf("SAVES.save("));
    }

    @Test
    @DisplayName("componentClosed drains the lane before teardown")
    void closeFlushesTheLane() throws Exception {
        String src = source();
        int start = src.indexOf("public void componentClosed()");
        assertThat(start).as("componentClosed exists").isPositive();
        String body = src.substring(start, src.indexOf("\n    }", start));
        assertThat(body).contains("SAVES.flush(");
    }
}
