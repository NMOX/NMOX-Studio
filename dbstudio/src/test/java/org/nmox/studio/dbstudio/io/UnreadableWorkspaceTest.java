package org.nmox.studio.dbstudio.io;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.BoundedReads;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A workspace we could not READ is not a workspace we may overwrite.
 *
 * <p>The corrupt-file law has held since v1.36.0: a {@code .nmoxdb.json}
 * that fails to PARSE is copied to {@code .bak} before the empty fallback
 * is returned, so the studio's next save can never destroy the only copy.
 * The READ failure shared that fallback and had none of its protection —
 * it returned the same empty workspace a fresh project gets, with a null
 * backup, so {@code DbStudioTopComponent} recorded the file as its own and
 * the next change wrote an empty workspace over every connection, every
 * saved query and the whole history.
 *
 * <p>{@link BoundedReads.TooLarge} is an {@link java.io.IOException}, so
 * an over-cap file took that path: measured before the fix, 9,437,184
 * bytes became 71. So did a permissions error and any transient fault.
 */
class UnreadableWorkspaceTest {

    private static void writeOverCap(File f) throws Exception {
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
    @DisplayName("an over-cap workspace is refused as unreadable, not loaded as empty")
    void overCapIsUnreadable(@TempDir File dir) throws Exception {
        writeOverCap(new File(dir, DbWorkspaceIO.FILENAME));
        DbWorkspaceIO.LoadOutcome outcome = DbWorkspaceIO.loadWorkspaceGuarded(dir);
        assertThat(outcome.unreadable())
                .as("the bytes are unknown — the studio must not own this file")
                .isTrue();
        assertThat(outcome.backup())
                .as("nothing will be written over it, so nothing needs copying aside")
                .isNull();
    }

    @Test
    @DisplayName("absent, blank and well-formed workspaces are never reported unreadable")
    void readableIsNotUnreadable(@TempDir File dir) throws Exception {
        assertThat(DbWorkspaceIO.loadWorkspaceGuarded(dir).unreadable())
                .as("no file at all is a fresh project, not a refusal")
                .isFalse();
        File f = new File(dir, DbWorkspaceIO.FILENAME);
        Files.writeString(f.toPath(), "   ");
        assertThat(DbWorkspaceIO.loadWorkspaceGuarded(dir).unreadable()).isFalse();
        DbWorkspaceIO.save(dir, new DbWorkspaceIO.Workspace(
                List.of(new ConnectionSpec("id", "local", DbEngine.SQLITE,
                        "", -1, "", "", "db.sqlite")),
                List.of(), List.of()));
        assertThat(DbWorkspaceIO.loadWorkspaceGuarded(dir).unreadable()).isFalse();
    }

    @Test
    @DisplayName("a corrupt workspace is readable-but-malformed: .bak, empty, and we own it")
    void corruptIsNotUnreadable(@TempDir File dir) throws Exception {
        Files.writeString(new File(dir, DbWorkspaceIO.FILENAME).toPath(), "{ not json");
        DbWorkspaceIO.LoadOutcome outcome = DbWorkspaceIO.loadWorkspaceGuarded(dir);
        assertThat(outcome.unreadable())
                .as("we READ these bytes and kept them as .bak — the empty"
                        + " workspace may replace them, which is the v1.36.0 law")
                .isFalse();
        assertThat(outcome.backup()).isNotNull().exists();
    }
}
