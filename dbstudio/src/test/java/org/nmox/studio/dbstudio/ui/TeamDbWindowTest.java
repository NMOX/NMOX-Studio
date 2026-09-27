package org.nmox.studio.dbstudio.ui;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.PersonalState;
import org.nmox.studio.core.util.SaveLane;
import org.nmox.studio.dbstudio.io.DbWorkspaceIO;
import org.nmox.studio.dbstudio.io.ExternalEdits;
import org.nmox.studio.dbstudio.io.TeamDbWorkspaceTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A second developer's merge, through the REAL DB Studio window (3.4).
 * Measured before: a conflicted {@code .nmoxdb.json} was backed up and
 * replaced by an empty workspace, and the next query Run — whose history
 * row saved the whole workspace — wrote that empty workspace over the
 * conflict, which {@code git commit} then recorded as the merge.
 */
class TeamDbWindowTest {

    @TempDir
    Path personal;

    private String home;

    @BeforeEach
    void setUp() {
        PersonalState.setBaseForTest(personal);
        home = System.getProperty("user.home");
    }

    @AfterEach
    void restore() {
        PersonalState.setBaseForTest(null);
        System.setProperty("user.home", home);
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void flushSaveLane() throws Exception {
        Field f = DbStudioTopComponent.class.getDeclaredField("SAVES");
        f.setAccessible(true);
        ((SaveLane) f.get(null)).flush(5, TimeUnit.SECONDS);
    }

    /** Builds the window bound to {@code dir} and applies the guarded load of its file. */
    private DbStudioTopComponent bound(File dir) throws Exception {
        System.setProperty("user.home", dir.getAbsolutePath());
        DbWorkspaceIO.LoadOutcome outcome = DbWorkspaceIO.loadWorkspaceGuarded(dir);
        List<DbWorkspaceIO.HistoryEntry> mine = DbWorkspaceIO.personalHistory(
                PersonalState.read(dir, DbWorkspaceIO.PERSONAL_STUDIO));
        ExternalEdits.Stamp stamp = ExternalEdits.Stamp.of(new File(dir, DbWorkspaceIO.FILENAME));
        Method apply = DbStudioTopComponent.class.getDeclaredMethod("applyReloadedWorkspace",
                DbWorkspaceIO.LoadOutcome.class, ExternalEdits.Stamp.class, List.class);
        apply.setAccessible(true);
        final DbStudioTopComponent[] made = new DbStudioTopComponent[1];
        SwingUtilities.invokeAndWait(() -> {
            made[0] = new DbStudioTopComponent();
            try {
                apply.invoke(made[0], outcome, stamp, mine);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        return made[0];
    }

    private static void run(DbStudioTopComponent window, String sql) throws Exception {
        Method record = DbStudioTopComponent.class.getDeclaredMethod(
                "recordRun", String.class, String.class);
        record.setAccessible(true);
        Method save = DbStudioTopComponent.class.getDeclaredMethod("saveWorkspace");
        save.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                record.invoke(window, sql, "SQLite");
                save.invoke(window);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        flushSaveLane();
    }

    @Test
    @DisplayName("a conflicted workspace survives a query Run and a save, untouched")
    void conflictedWorkspaceSurvives(@TempDir File dir) throws Exception {
        File f = new File(dir, DbWorkspaceIO.FILENAME);
        Files.writeString(f.toPath(), TeamDbWorkspaceTest.conflicted(), StandardCharsets.UTF_8);
        byte[] before = Files.readAllBytes(f.toPath());

        DbStudioTopComponent window = bound(dir);
        run(window, "SELECT 1;");

        assertThat(Files.readAllBytes(f.toPath())).isEqualTo(before);
        assertThat(dir.list()).containsExactly(DbWorkspaceIO.FILENAME);
        assertThat((boolean) field(window, "workspaceReadOnly")).isTrue();
        assertThat((String) field(window, "readOnlyText")).contains("merge conflicts");
        assertThat(PersonalState.read(dir, DbWorkspaceIO.PERSONAL_STUDIO))
                .as("the Run is still remembered — in this person's own state")
                .contains("SELECT 1;");
    }

    @Test
    @DisplayName("a query Run never rewrites the shared file; its SQL stays this person's")
    void runStaysPersonal(@TempDir File dir) throws Exception {
        File f = new File(dir, DbWorkspaceIO.FILENAME);
        DbWorkspaceIO.save(dir, new DbWorkspaceIO.Workspace(List.of(), List.of(), List.of(
                new DbWorkspaceIO.SavedQuery("report", "SELECT 1;", "SQLite"))));
        long marker = f.lastModified() - 10_000L;
        assertThat(f.setLastModified(marker)).isTrue();
        DbStudioTopComponent window = bound(dir);

        Method record = DbStudioTopComponent.class.getDeclaredMethod(
                "recordRun", String.class, String.class);
        record.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                record.invoke(window, "SELECT salary FROM payroll;", "SQLite");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        flushSaveLane();

        assertThat(f.lastModified()).as("a Run no longer rewrites the committed file")
                .isEqualTo(marker);
        assertThat(PersonalState.read(dir, DbWorkspaceIO.PERSONAL_STUDIO)).contains("payroll");
    }
}
