package org.nmox.studio.apiclient.ui;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.apiclient.api.TeamWorkspaceTest;
import org.nmox.studio.apiclient.api.WorkspaceIO;
import org.nmox.studio.apiclient.model.ApiModel.Workspace;
import org.nmox.studio.core.util.PersonalState;
import org.nmox.studio.core.util.SaveLane;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A writable-bound API Studio re-checks the disk immediately before every
 * write (3.4; the hostile review's probe, kept): a conflict or a teammate's
 * change that lands AFTER the bind is never replaced by this window's copy.
 */
class WriteRecheckTest {

    @TempDir
    Path personal;

    @BeforeEach
    void before() {
        PersonalState.setBaseForTest(personal);
    }

    @AfterEach
    void after() {
        PersonalState.setBaseForTest(null);
    }

    private static Object call(Object t, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = t.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(t, args);
    }

    private static Object field(Object t, String name) throws Exception {
        Field f = t.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(t);
    }

    private static void flushSaveLane() throws Exception {
        Field f = ApiClientTopComponent.class.getDeclaredField("SAVES");
        f.setAccessible(true);
        ((SaveLane) f.get(null)).flush(5, TimeUnit.SECONDS);
    }

    private static ApiClientTopComponent loaded(File dir) throws Exception {
        String home = System.getProperty("user.home");
        System.setProperty("user.home", dir.getAbsolutePath());
        try {
            final ApiClientTopComponent[] made = new ApiClientTopComponent[1];
            SwingUtilities.invokeAndWait(() -> made[0] = new ApiClientTopComponent());
            SwingUtilities.invokeAndWait(() -> {
                try {
                    call(made[0], "loadWorkspace", new Class<?>[0]);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            return made[0];
        } finally {
            System.setProperty("user.home", home);
        }
    }

    @Test
    void conflictLandingBeforeThePulseTicksIsOverwrittenByTheNextEdit(@TempDir File dir) throws Exception {
        File f = new File(dir, WorkspaceIO.FILENAME);
        WorkspaceIO.save(dir, Workspace.starter("Payments", "List charges", "Staging"));
        ApiClientTopComponent window = loaded(dir);
        assertThat((boolean) field(window, "workspaceReadOnly")).isFalse();

        // git pull in the terminal: the file now holds both people's work
        Files.writeString(f.toPath(), TeamWorkspaceTest.conflicted(), StandardCharsets.UTF_8);
        byte[] conflicted = Files.readAllBytes(f.toPath());

        // any edit's debounce fires before the 1.5 s pulse has noticed
        SwingUtilities.invokeAndWait(() -> {
            try {
                call(window, "save", new Class<?>[0]);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        flushSaveLane();

        assertThat(Files.readAllBytes(f.toPath()))
                .as("the conflicted merge must survive a save from a window bound before it landed")
                .isEqualTo(conflicted);
    }

    @Test
    void pendingEditStillSavesAfterTheForeignConflictWasDetected(@TempDir File dir) throws Exception {
        File f = new File(dir, WorkspaceIO.FILENAME);
        WorkspaceIO.save(dir, Workspace.starter("Payments", "List charges", "Staging"));
        ApiClientTopComponent window = loaded(dir);

        // an edit arms the 800 ms debounce
        SwingUtilities.invokeAndWait(() -> {
            try {
                call(window, "touch", new Class<?>[0]);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        Files.writeString(f.toPath(), TeamWorkspaceTest.conflicted(), StandardCharsets.UTF_8);
        byte[] conflicted = Files.readAllBytes(f.toPath());
        long mtime = f.lastModified();
        long size = f.length();

        // the pulse tick sees it as foreign; with edits pending, the window only balloons
        SwingUtilities.invokeAndWait(() -> {
            try {
                call(window, "onForeignWorkspaceEdit", new Class<?>[]{long.class, long.class}, mtime, size);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(((Timer) field(window, "saveDebounce")).isRunning())
                .as("the pending save is stopped when a foreign change arrives").isFalse();
        Thread.sleep(1500); // the debounce fires on the EDT
        SwingUtilities.invokeAndWait(() -> { });
        flushSaveLane();

        assertThat(Files.readAllBytes(f.toPath()))
                .as("a detected conflict must not be written over by the save that was pending")
                .isEqualTo(conflicted);
    }

    @Test
    void aTeammatesChangeLandingBeforeThePulseIsNotWrittenOver(@TempDir File dir) throws Exception {
        File f = new File(dir, WorkspaceIO.FILENAME);
        WorkspaceIO.save(dir, Workspace.starter("Payments", "List charges", "Staging"));
        ApiClientTopComponent window = loaded(dir);

        // a pull brings a teammate's clean edit — no conflict, just other bytes
        Workspace theirs = Workspace.starter("Payments", "List refunds", "Staging");
        Files.writeString(f.toPath(), WorkspaceIO.toJson(theirs) + "\n", StandardCharsets.UTF_8);
        byte[] changed = Files.readAllBytes(f.toPath());

        SwingUtilities.invokeAndWait(() -> {
            try {
                call(window, "save", new Class<?>[0]);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        flushSaveLane();
        assertThat(Files.readAllBytes(f.toPath()))
                .as("a changed file is reloaded, not overwritten by the copy read before it")
                .isEqualTo(changed);

        // the refusal reloads on the EDT: the window now holds THEIR workspace
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        Workspace now = (Workspace) field(window, "workspace");
        assertThat(now.collections.get(0).requests.get(0).name).isEqualTo("List refunds");
        assertThat((boolean) field(window, "workspaceReadOnly")).isFalse();
    }
}
