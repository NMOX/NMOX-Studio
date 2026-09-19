package org.nmox.studio.infra;

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
import org.nmox.studio.core.util.BoundedReads;
import org.nmox.studio.core.util.SaveLane;
import org.nmox.studio.infra.model.DesignSync;
import org.nmox.studio.infra.model.GraphIO;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The clobber itself, driven through the REAL designer.
 *
 * <p>{@code UnreadableDesignTest} proves the seam reports an unreadable
 * file; this is the other half the v1.321.0 law asks for — that the
 * designer CONSULTS it. Both halves are needed, and here they were BOTH
 * broken by the same three lines: the catch-all cleared the graph, and the
 * {@code finally} beneath it recorded the user's bytes as ours, so the
 * never-clobber guard that should have caught the next save was disarmed
 * by the failure it existed for.
 *
 * <p>The designer is headless-constructible (v2.77.0), so these tests build
 * it, point it at a temp project, and call its own load and its own save by
 * reflection — the same two methods the debounce and the re-aim call. On
 * the code this test was written against, both failed: <b>9,437,184 bytes
 * became 48</b>, with {@link DesignSync} answering NONE for the file's real
 * stamp.
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

    private static void call(Object target, String name) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(target);
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void flushSaveLane() throws Exception {
        Field f = InfraDesignerTopComponent.class.getDeclaredField("SAVES");
        f.setAccessible(true);
        ((SaveLane) f.get(null)).flush(5, TimeUnit.SECONDS);
    }

    /**
     * Builds the designer bound to {@code dir}, runs its real load and its
     * real save, and returns it. {@code user.home} is the seam: with no
     * rack aimed, {@code designFile()} falls back to it.
     */
    private static Object loadAndSave(File dir) throws Exception {
        String home = System.getProperty("user.home");
        System.setProperty("user.home", dir.getAbsolutePath());
        try {
            final Object[] made = new Object[1];
            SwingUtilities.invokeAndWait(() -> made[0] = new InfraDesignerTopComponent());
            SwingUtilities.invokeAndWait(() -> {
                try {
                    call(made[0], "load");
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
    @DisplayName("an over-cap design survives the designer's own load → save")
    void overCapDesignSurvives(@TempDir File dir) throws Exception {
        File f = new File(dir, GraphIO.DEFAULT_FILENAME);
        writeOverCap(f);
        long before = f.length();
        byte[] bytes = Files.readAllBytes(f.toPath());

        Object window = loadAndSave(dir);

        assertThat(f.length())
                .as("the user's design is untouched on disk"
                        + " (9,437,184 bytes became 48 before this fix)")
                .isEqualTo(before);
        assertThat(Files.readAllBytes(f.toPath())).isEqualTo(bytes);
        assertThat(new File(dir, GraphIO.DEFAULT_FILENAME + ".bak"))
                .as("nothing was written, so nothing needed backing up")
                .doesNotExist();

        DesignSync sync = (DesignSync) field(window, "designSync");
        assertThat(sync.check(DesignSync.Stamp.of(f), false))
                .as("a file we never read is never ours — NONE here means"
                        + " recordOwn ran on the catch path and disarmed the"
                        + " never-clobber guard")
                .isNotEqualTo(DesignSync.Verdict.NONE);
    }

    @Test
    @DisplayName("an unreadable design survives too (POSIX permissions)")
    @DisabledOnOs(OS.WINDOWS)
    void unreadableDesignSurvives(@TempDir File dir) throws Exception {
        File f = new File(dir, GraphIO.DEFAULT_FILENAME);
        InfraGraph written = new InfraGraph();
        written.addNode(NodeKind.DROPLET, 10, 20);
        GraphIO.save(written, f);
        long before = f.length();
        Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("---------"));
        try {
            loadAndSave(dir);
            // the directory is writable, so an atomic temp-and-rename would
            // have replaced this file however locked its own bits are
            assertThat(f.length())
                    .as("a design we cannot read is a design we cannot replace")
                    .isEqualTo(before);
        } finally {
            Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    @DisplayName("a readable design still saves — the guard refuses nothing else")
    void readableDesignStillSaves(@TempDir File dir) throws Exception {
        File f = new File(dir, GraphIO.DEFAULT_FILENAME);
        InfraGraph written = new InfraGraph();
        written.addNode(NodeKind.DROPLET, 10, 20);
        GraphIO.save(written, f);
        long marker = f.lastModified() - 10_000L;
        assertThat(f.setLastModified(marker)).isTrue();

        Object window = loadAndSave(dir);

        assertThat(f.lastModified())
                .as("an ordinary design is read, owned and written as before")
                .isNotEqualTo(marker);
        DesignSync sync = (DesignSync) field(window, "designSync");
        assertThat(sync.check(DesignSync.Stamp.of(f), false))
                .as("a file we DID read and write is ours")
                .isEqualTo(DesignSync.Verdict.NONE);
    }
}
