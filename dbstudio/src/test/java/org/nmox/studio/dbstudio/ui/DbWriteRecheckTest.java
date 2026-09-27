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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.PersonalState;
import org.nmox.studio.core.util.SaveLane;
import org.nmox.studio.dbstudio.io.DbWorkspaceIO;
import org.nmox.studio.dbstudio.io.ExternalEdits;
import org.nmox.studio.dbstudio.io.TeamDbWorkspaceTest;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB Studio re-checks the disk immediately before every write (3.4; the
 * hostile review's probe, kept), and writes where the loaded workspace came
 * from rather than wherever the aim points by then.
 */
class DbWriteRecheckTest {

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

    private static final ConnectionSpec LOCAL = new ConnectionSpec("c1", "local", DbEngine.SQLITE,
            "", -1, "", "", "app.db", false);

    /** A window bound to {@code dir}, as a load of its file leaves it (the live aim is user.home in tests). */
    private static DbStudioTopComponent boundTo(File dir) throws Exception {
        System.setProperty("user.home", dir.getAbsolutePath());
        DbWorkspaceIO.LoadOutcome outcome = DbWorkspaceIO.loadWorkspaceGuarded(dir);
        ExternalEdits.Stamp stamp = ExternalEdits.Stamp.of(new File(dir, DbWorkspaceIO.FILENAME));
        Method apply = DbStudioTopComponent.class.getDeclaredMethod("applyReloadedWorkspace",
                DbWorkspaceIO.LoadOutcome.class, ExternalEdits.Stamp.class, List.class);
        apply.setAccessible(true);
        final DbStudioTopComponent[] w = new DbStudioTopComponent[1];
        SwingUtilities.invokeAndWait(() -> {
            w[0] = new DbStudioTopComponent();
            try {
                apply.invoke(w[0], outcome, stamp, null);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        return w[0];
    }

    private static void save(DbStudioTopComponent w) throws Exception {
        Method save = DbStudioTopComponent.class.getDeclaredMethod("saveWorkspace");
        save.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                save.invoke(w);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        Field lane = DbStudioTopComponent.class.getDeclaredField("SAVES");
        lane.setAccessible(true);
        ((SaveLane) lane.get(null)).flush(5, TimeUnit.SECONDS);
    }

    @Test
    void confirmedConnectionDialogDoesNotWriteOverAConflictThatLandedWhileItWasOpen(@TempDir File dir)
            throws Exception {
        File f = new File(dir, DbWorkspaceIO.FILENAME);
        DbWorkspaceIO.save(dir, List.of(LOCAL));
        DbStudioTopComponent w = boundTo(dir);

        // while "Add Connection…" is open the watcher DEFERS; git pull lands the conflict
        Files.writeString(f.toPath(), TeamDbWorkspaceTest.conflicted(), StandardCharsets.UTF_8);
        byte[] conflicted = Files.readAllBytes(f.toPath());
        // the dialog is confirmed: the caller's save runs first
        save(w);

        assertThat(Files.readAllBytes(f.toPath()))
                .as("git's conflict must not be replaced by this window's pre-merge connections")
                .isEqualTo(conflicted);
    }

    @Test
    void aTeammatesChangeIsNotWrittenOver(@TempDir File dir) throws Exception {
        File f = new File(dir, DbWorkspaceIO.FILENAME);
        DbWorkspaceIO.save(dir, List.of(LOCAL));
        DbStudioTopComponent w = boundTo(dir);

        String theirs = DbWorkspaceIO.toJson(List.of(LOCAL, new ConnectionSpec("c2", "reports",
                DbEngine.SQLITE, "", -1, "", "", "reports.db", false)));
        Files.writeString(f.toPath(), theirs, StandardCharsets.UTF_8);
        save(w);

        assertThat(Files.readString(f.toPath(), StandardCharsets.UTF_8))
                .as("a clean change a pull brought is kept, not replaced by the copy read before it")
                .isEqualTo(theirs);
    }

    @Test
    void aSaveGoesWhereTheWorkspaceCameFromNotWhereTheAimMovedTo(@TempDir File a, @TempDir File b)
            throws Exception {
        DbWorkspaceIO.save(a, List.of(LOCAL));
        DbStudioTopComponent w = boundTo(a);

        // a re-aim to B is reading B's file while A's edit is saved
        System.setProperty("user.home", b.getAbsolutePath());
        save(w);

        assertThat(new File(b, DbWorkspaceIO.FILENAME))
                .as("A's connections must never land in B's file").doesNotExist();
        assertThat(DbWorkspaceIO.fromJson(Files.readString(new File(a, DbWorkspaceIO.FILENAME).toPath())))
                .extracting(ConnectionSpec::id).containsExactly("c1");
    }
}
