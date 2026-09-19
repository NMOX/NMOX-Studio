package org.nmox.studio.infra;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.BoundedReads;
import org.nmox.studio.infra.model.DesignSync;

import static org.assertj.core.api.Assertions.assertThat;

/** PROOF HARNESS (temporary): the real designer, load → save, measured. */
class ReadFailureNeverClobbersTest {

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
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    @Test
    @DisplayName("PROOF: the designer's load → save overwrites an unread design")
    void measured(@TempDir File dir) throws Exception {
        File f = new File(dir, ".nmoxinfra.json");
        writeOverCap(f);
        long before = f.length();

        String home = System.getProperty("user.home");
        System.setProperty("user.home", dir.getAbsolutePath());
        Object tc;
        try {
            final Object[] made = new Object[1];
            SwingUtilities.invokeAndWait(() -> made[0] = new InfraDesignerTopComponent());
            tc = made[0];
            SwingUtilities.invokeAndWait(() -> {
                try {
                    call(made[0], "load");
                    call(made[0], "save");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } finally {
            System.setProperty("user.home", home);
        }
        java.lang.reflect.Field laneField =
                InfraDesignerTopComponent.class.getDeclaredField("SAVES");
        laneField.setAccessible(true);
        ((org.nmox.studio.core.util.SaveLane) laneField.get(null)).flush(5, TimeUnit.SECONDS);

        long after = f.length();
        DesignSync sync = (DesignSync) field(tc, "designSync");
        DesignSync.Verdict verdict = sync.check(DesignSync.Stamp.of(f), false);
        System.out.println("PROOF infra: before=" + before + " after=" + after
                + " stampVerdict=" + verdict);
        assertThat(verdict)
                .as("a file we never read is never ours — a NONE verdict means"
                        + " recordOwn stamped it and the never-clobber guard is disarmed")
                .isNotEqualTo(DesignSync.Verdict.NONE);
        assertThat(after).as("the user's design is untouched on disk").isEqualTo(before);
    }
}
