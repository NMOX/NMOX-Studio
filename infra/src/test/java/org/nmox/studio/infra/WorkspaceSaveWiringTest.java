package org.nmox.studio.infra;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source gate for the debt-#16 wiring (the v1.38.0 idiom: a contract
 * the UI can silently drop is pinned against the source). SaveLaneTest
 * proves the lane's behavior; this proves the designer actually rides
 * it: the EDT half only snapshots and queues, the write and its
 * self-write stamp live in one lane task, the external-edit stat rides
 * the same lane, and componentClosed drains it. A future edit that
 * quietly reverts to a synchronous EDT write — or stamps outside the
 * write task — fails here by name.
 */
class WorkspaceSaveWiringTest {

    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/infra/InfraDesignerTopComponent.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("save() snapshots on the EDT and queues the write on the lane")
    void saveQueuesOnTheLane() throws Exception {
        String src = source();
        assertThat(src).contains("SAVES.save(() -> writeSnapshot(");
        assertThat(src)
                .as("no synchronous EDT write may remain — the lane is the only writer")
                .doesNotContain("GraphIO.save(");
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
                .contains("designSync.recordOwn(");
    }

    @Test
    @DisplayName("the external-edit stat queues behind writes on the same lane")
    void classificationRidesTheLane() throws Exception {
        assertThat(source()).contains("SAVES.classify(");
    }

    @Test
    @DisplayName("componentClosed drains the lane before teardown")
    void closeFlushesTheLane() throws Exception {
        String src = source();
        int start = src.indexOf("protected void componentClosed()");
        assertThat(start).as("componentClosed exists").isPositive();
        String body = src.substring(start, src.indexOf("\n    }", start));
        assertThat(body).contains("SAVES.flush(");
    }
}
