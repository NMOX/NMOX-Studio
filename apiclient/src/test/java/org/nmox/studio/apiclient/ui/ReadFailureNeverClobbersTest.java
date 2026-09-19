package org.nmox.studio.apiclient.ui;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.apiclient.api.WorkspaceIO;
import org.nmox.studio.core.util.BoundedReads;
import org.nmox.studio.core.util.SaveLane;
import org.nmox.studio.core.util.SelfWriteTracker;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The clobber itself, driven through the REAL window.
 *
 * <p>{@code UnreadableWorkspaceTest} proves the seam reports an unreadable
 * file; this is the other half the v1.321.0 law asks for — that the studio
 * CONSULTS it, and that consulting it is enough. Both halves are needed
 * here because {@link ApiClientTopComponent#save} writes without asking the
 * self-write tracker at all: withholding the ownership stamp alone would
 * NOT have stopped the loss, and refusing the save alone would leave the
 * file falsely marked as ours.
 *
 * <p>The window is headless-constructible (v2.77.0), so these tests build
 * it, point it at a temp project, and call its own load and its own save by
 * reflection — the same two methods the debounce and the re-aim call. On
 * the code this test was written against, both failed:
 * <b>9,437,184 bytes became 550</b>.
 */
class ReadFailureNeverClobbersTest {

    /** Writes a file the 8 MiB cap refuses to read. */
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

    private static Object call(Object target, String name) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void flushSaveLane() throws Exception {
        Field f = ApiClientTopComponent.class.getDeclaredField("SAVES");
        f.setAccessible(true);
        ((SaveLane) f.get(null)).flush(5, TimeUnit.SECONDS);
    }

    /**
     * Builds the window bound to {@code dir}, runs its real load and its
     * real save, and returns it. {@code user.home} is the seam: with no
     * rack aimed, {@code projectDir()} falls back to it, which is exactly
     * how the window binds when it opens outside a project.
     */
    private static Object loadAndSave(File dir) throws Exception {
        String home = System.getProperty("user.home");
        System.setProperty("user.home", dir.getAbsolutePath());
        try {
            final Object[] made = new Object[1];
            SwingUtilities.invokeAndWait(() -> made[0] = new ApiClientTopComponent());
            SwingUtilities.invokeAndWait(() -> {
                try {
                    call(made[0], "loadWorkspace");
                    call(made[0], "save");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            flushSaveLane();
            return made[0];
        } finally {
            System.setProperty("user.home", home);
        }
    }

    @Test
    @DisplayName("an over-cap workspace survives the window's own load → save")
    void overCapWorkspaceSurvives(@TempDir File dir) throws Exception {
        File f = new File(dir, WorkspaceIO.FILENAME);
        writeOverCap(f);
        long before = f.length();
        byte[] bytes = Files.readAllBytes(f.toPath());

        Object window = loadAndSave(dir);

        assertThat(f.length())
                .as("the user's workspace is untouched on disk"
                        + " (9,437,184 bytes became 550 before this fix)")
                .isEqualTo(before);
        assertThat(Files.readAllBytes(f.toPath())).isEqualTo(bytes);
        assertThat(new File(dir, WorkspaceIO.FILENAME + ".bak"))
                .as("nothing was written, so nothing needed backing up")
                .doesNotExist();

        SelfWriteTracker tracker = (SelfWriteTracker) field(window, "selfWrites");
        assertThat(tracker.isForeign(f.lastModified(), f.length()))
                .as("a file we never read is never ours — a stamp here disarms"
                        + " the never-clobber guard as well as losing the bytes")
                .isTrue();
    }

    @Test
    @DisplayName("an unreadable workspace survives too (POSIX permissions)")
    @DisabledOnOs(OS.WINDOWS)
    void unreadableWorkspaceSurvives(@TempDir File dir) throws Exception {
        File f = new File(dir, WorkspaceIO.FILENAME);
        org.nmox.studio.apiclient.model.ApiModel.Workspace w =
                org.nmox.studio.apiclient.model.ApiModel.Workspace.starter(
                        "Payments", "List charges", "Staging");
        WorkspaceIO.save(dir, w);
        long before = f.length();
        Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("---------"));
        try {
            loadAndSave(dir);
            // the directory is writable, so an atomic temp-and-rename would
            // have replaced this file however locked its own bits are
            assertThat(f.length())
                    .as("a workspace we cannot read is a workspace we cannot replace")
                    .isEqualTo(before);
        } finally {
            Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    @DisplayName("a readable workspace still saves — the guard refuses nothing else")
    void readableWorkspaceStillSaves(@TempDir File dir) throws Exception {
        File f = new File(dir, WorkspaceIO.FILENAME);
        org.nmox.studio.apiclient.model.ApiModel.Workspace w =
                org.nmox.studio.apiclient.model.ApiModel.Workspace.starter(
                        "Payments", "List charges", "Staging");
        WorkspaceIO.save(dir, w);
        long before = f.lastModified();
        // make a rewrite observable even on a coarse filesystem clock
        assertThat(f.setLastModified(before - 10_000L)).isTrue();

        Object window = loadAndSave(dir);

        assertThat(f.lastModified())
                .as("an ordinary workspace is read, owned and written as before")
                .isNotEqualTo(before - 10_000L);
        SelfWriteTracker tracker = (SelfWriteTracker) field(window, "selfWrites");
        assertThat(tracker.isForeign(f.lastModified(), f.length()))
                .as("a file we DID read and write is ours")
                .isFalse();
    }
}
