package org.nmox.studio.apiclient.ui;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.BoundedReads;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PROOF HARNESS (temporary): drives the REAL window through load → save
 * on an unreadable {@code .nmoxapi.json} and measures the file.
 */
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

    @Test
    @DisplayName("PROOF: the window's load → save overwrites an unread workspace")
    void measured(@TempDir File dir) throws Exception {
        File f = new File(dir, "/.nmoxapi.json".substring(1));
        writeOverCap(f);
        long before = f.length();

        String home = System.getProperty("user.home");
        System.setProperty("user.home", dir.getAbsolutePath());
        try {
            final Object[] tc = new Object[1];
            SwingUtilities.invokeAndWait(() -> tc[0] = new ApiClientTopComponent());
            SwingUtilities.invokeAndWait(() -> {
                try {
                    call(tc[0], "loadWorkspace");
                    call(tc[0], "save");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } finally {
            System.setProperty("user.home", home);
        }
        org.nmox.studio.core.util.SaveLane lane = lane();
        if (lane != null) {
            lane.flush(5, TimeUnit.SECONDS);
        }
        long after = f.length();
        System.out.println("PROOF apiclient: before=" + before + " after=" + after);
        assertThat(after).as("the user's workspace is untouched on disk").isEqualTo(before);
    }

    private static org.nmox.studio.core.util.SaveLane lane() throws Exception {
        java.lang.reflect.Field field = ApiClientTopComponent.class.getDeclaredField("SAVES");
        field.setAccessible(true);
        return (org.nmox.studio.core.util.SaveLane) field.get(null);
    }
}
