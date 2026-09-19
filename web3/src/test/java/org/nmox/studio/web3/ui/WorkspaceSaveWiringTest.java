package org.nmox.studio.web3.ui;

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
 * stamp live in one lane task, pulse verdicts ride the same lane, and
 * componentClosed drains it. A future edit that quietly reverts to a
 * synchronous EDT write — or stamps outside the write task — fails
 * here by name.
 */
class WorkspaceSaveWiringTest {

    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/web3/ui/Web3StudioTopComponent.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("saveWorkspace() snapshots on the EDT and queues the write on the lane")
    void saveQueuesOnTheLane() throws Exception {
        String src = source();
        assertThat(src).contains("SAVES.save(() -> writeSnapshot(");
        assertThat(src)
                .as("no synchronous EDT write may remain — the lane is the only writer")
                .doesNotContain("Web3WorkspaceIO.save(");
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
                .contains("selfWrites.noteSync(");
    }

    @Test
    @DisplayName("pulse verdicts queue behind writes on the same lane")
    void classificationRidesTheLane() throws Exception {
        assertThat(source()).contains("SAVES.classify(");
    }

    @Test
    @DisplayName("a file we never read is never ours, and never written over")
    void unreadableWorkspaceIsBoundReadOnly() throws Exception {
        String src = source();
        // The seam (loadGuarded reporting unreadable) has its own tests; this
        // is the OTHER half the v1.321.0 law asks for — that the studio
        // consults it. The two halves are independent here, because
        // saveWorkspace() writes without consulting the stamp at all:
        // skipping noteSync alone would NOT have stopped the loss.
        int apply = src.indexOf("private void applyReloadedWorkspace(");
        assertThat(apply).isPositive();
        String applyBody = src.substring(apply, src.indexOf("\n    }", apply));
        assertThat(applyBody)
                .as("ownership is recorded for a read that HAPPENED")
                .contains("workspaceReadOnly = outcome.unreadable()");
        assertThat(applyBody.indexOf("workspaceReadOnly = outcome.unreadable()"))
                .as("the verdict is taken before the stamp it guards")
                .isLessThan(applyBody.indexOf("selfWrites.noteSync("));

        int save = src.indexOf("private void saveWorkspace()");
        assertThat(save).isPositive();
        String saveBody = src.substring(save, src.indexOf("\n    }", save));
        assertThat(saveBody)
                .as("the write refuses OUT LOUD on a read-only workspace:"
                        + " writing the stand-in lists would replace every"
                        + " network and the whole deployment address book"
                        + " with nothing (9,437,184 bytes → 75, measured)")
                .contains("if (workspaceReadOnly) {")
                .contains("Bundle.Web3StudioTopComponent_workspaceReadOnly(");
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
