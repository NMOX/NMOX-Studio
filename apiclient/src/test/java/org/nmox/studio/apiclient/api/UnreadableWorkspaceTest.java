package org.nmox.studio.apiclient.api;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.BoundedReads;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A workspace we could not READ is not a workspace we may overwrite.
 *
 * <p>The corrupt-file law has held since v1.36.0: a {@code .nmoxapi.json}
 * that fails to PARSE is copied to {@code .bak} before the empty fallback
 * is returned, so the studio's next autosave can never destroy the only
 * copy. The READ failure had none of that protection — {@link WorkspaceIO}
 * simply threw, and {@code ApiClientTopComponent.readWorkspace} caught
 * {@code Exception} and substituted the starter workspace, which is what a
 * FRESH project gets. The studio then stamped the file as its own and the
 * next edit wrote three starter requests over every collection, every
 * environment and the whole send history.
 *
 * <p>{@link BoundedReads.TooLarge} is an {@link java.io.IOException}, so an
 * over-cap file took that path — the file the 8 MiB ceiling exists to
 * refuse was the file the refusal destroyed. So did a permissions error and
 * any transient fault.
 */
class UnreadableWorkspaceTest {

    /** Writes a file the 8 MiB cap refuses to read. */
    static void writeOverCap(File f) throws Exception {
        byte[] chunk = new byte[1024 * 1024];
        java.util.Arrays.fill(chunk, (byte) 'x');
        try (java.io.OutputStream out = Files.newOutputStream(f.toPath())) {
            for (int i = 0; i < 9; i++) {
                out.write(chunk);
            }
        }
        assertThat(f.length()).isGreaterThan(BoundedReads.DEFAULT_MAX_BYTES);
    }

    @Test
    @DisplayName("an over-cap workspace is refused as unreadable, not loaded as a starter")
    void overCapIsUnreadable(@TempDir File dir) throws Exception {
        writeOverCap(new File(dir, WorkspaceIO.FILENAME));
        WorkspaceIO.LoadOutcome outcome = WorkspaceIO.loadGuarded(dir);
        assertThat(outcome.unreadable())
                .as("the bytes are unknown — the studio must not own this file")
                .isTrue();
        assertThat(outcome.workspace())
                .as("nothing was read, so there is nothing to hand back")
                .isNull();
        assertThat(outcome.backup())
                .as("nothing will be written over it, so nothing needs copying aside")
                .isNull();
        assertThat(new File(dir, WorkspaceIO.FILENAME + ".bak")).doesNotExist();
    }

    @Test
    @DisplayName("an unreadable workspace refuses rather than throwing")
    void unreadableNeverThrows(@TempDir File dir) throws Exception {
        writeOverCap(new File(dir, WorkspaceIO.FILENAME));
        // the throw was the mechanism of the loss: a caller that must catch
        // it has no way to tell "could not read" from "nothing to read"
        assertThat(WorkspaceIO.loadGuarded(dir)).isNotNull();
    }

    @Test
    @DisplayName("absent and well-formed workspaces are never reported unreadable")
    void readableIsNotUnreadable(@TempDir File dir) throws Exception {
        WorkspaceIO.LoadOutcome absent = WorkspaceIO.loadGuarded(dir);
        assertThat(absent.unreadable())
                .as("no file at all is a fresh project, not a refusal")
                .isFalse();
        assertThat(absent.workspace()).isNull();

        org.nmox.studio.apiclient.model.ApiModel.Workspace w =
                org.nmox.studio.apiclient.model.ApiModel.Workspace.starter("c", "r", "e");
        WorkspaceIO.save(dir, w);
        WorkspaceIO.LoadOutcome good = WorkspaceIO.loadGuarded(dir);
        assertThat(good.unreadable()).isFalse();
        assertThat(good.workspace()).isNotNull();
        assertThat(good.backup()).isNull();
    }

    @Test
    @DisplayName("a corrupt workspace is readable-but-malformed: .bak, starter, and we own it")
    void corruptIsNotUnreadable(@TempDir File dir) throws Exception {
        Files.writeString(new File(dir, WorkspaceIO.FILENAME).toPath(), "{ not a workspace");
        WorkspaceIO.LoadOutcome outcome = WorkspaceIO.loadGuarded(dir);
        assertThat(outcome.unreadable())
                .as("we READ these bytes and kept them as .bak — the starter may"
                        + " replace them, which is the v1.36.0 law")
                .isFalse();
        assertThat(outcome.backup()).isNotNull().exists();
    }
}
