package org.nmox.studio.dbstudio.ui;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.PersonalState;
import org.nmox.studio.core.util.SaveLane;
import org.nmox.studio.dbstudio.engine.Passwords;
import org.nmox.studio.dbstudio.io.DbWorkspaceIO;
import org.nmox.studio.dbstudio.io.ExternalEdits;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Remove Connection deletes the keychain password only once the save that
 * drops the connection has landed (3.4). It used to delete it at once; when
 * that save found a changed file it wrote nothing, the workspace was read
 * again, the file still named the connection — and it came back without
 * its password.
 */
class RemovalKeepsPasswordTest {

    @TempDir
    Path personal;
    private String home;
    private boolean keyringWas;

    private static final ConnectionSpec LOCAL = new ConnectionSpec("c1", "local", DbEngine.SQLITE,
            "", -1, "", "", "app.db", false);
    private static final ConnectionSpec REPORTS = new ConnectionSpec("r2", "reports", DbEngine.POSTGRES,
            "db.example", 5432, "reports", "analyst", "", false);
    private static final ConnectionSpec ORDERS = new ConnectionSpec("c9", "orders", DbEngine.SQLITE,
            "", -1, "", "", "orders.db", false);

    private static Field keyringUsable() throws Exception {
        Field f = Passwords.class.getDeclaredField("keyringUsable");
        f.setAccessible(true);
        return f;
    }

    @BeforeEach
    void setUp() throws Exception {
        PersonalState.setBaseForTest(personal);
        home = System.getProperty("user.home");
        keyringWas = keyringUsable().getBoolean(null);
        keyringUsable().setBoolean(null, false); // the in-memory store: no real keychain touched
    }

    @AfterEach
    void restore() throws Exception {
        Passwords.delete(REPORTS.id());
        keyringUsable().setBoolean(null, keyringWas);
        PersonalState.setBaseForTest(null);
        System.setProperty("user.home", home);
    }

    private static DbStudioTopComponent boundTo(File dir) throws Exception {
        System.setProperty("user.home", dir.getAbsolutePath());
        // the reload after a refused write reads the AIM, and the rack is on this
        // test path: an aim left at a directory that still exists (a Windows
        // @TempDir whose delete a locked file blocked) makes it read elsewhere
        org.nmox.studio.rack.service.RackService.getDefault().getRack().setProjectDir(dir);
        DbWorkspaceIO.LoadOutcome outcome = DbWorkspaceIO.loadWorkspaceGuarded(dir);
        ExternalEdits.Stamp stamp = ExternalEdits.Stamp.of(new File(dir, DbWorkspaceIO.FILENAME));
        Method apply = DbStudioTopComponent.class.getDeclaredMethod("applyReloadedWorkspace",
                File.class, DbWorkspaceIO.LoadOutcome.class, ExternalEdits.Stamp.class, List.class);
        apply.setAccessible(true);
        final DbStudioTopComponent[] w = new DbStudioTopComponent[1];
        SwingUtilities.invokeAndWait(() -> {
            w[0] = new DbStudioTopComponent();
            try {
                apply.invoke(w[0], dir, outcome, stamp, null);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        return w[0];
    }

    /** What Remove Connection does once its confirm is answered OK. */
    private static void removeConfirmed(DbStudioTopComponent w, ConnectionSpec spec) throws Exception {
        Method remove = DbStudioTopComponent.class.getDeclaredMethod("removeConfirmed", ConnectionSpec.class);
        remove.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                remove.invoke(w, spec);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static List<String> shownIds(DbStudioTopComponent w) throws Exception {
        Field f = DbStudioTopComponent.class.getDeclaredField("specs");
        f.setAccessible(true);
        final Object[] ids = new Object[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                ids[0] = ((List<ConnectionSpec>) f.get(w)).stream().map(ConnectionSpec::id).toList();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        return (List<String>) ids[0];
    }

    private static List<String> idsOnDisk(File dir) {
        try {
            return DbWorkspaceIO.fromJson(Files.readString(new File(dir, DbWorkspaceIO.FILENAME).toPath()))
                    .stream().map(ConnectionSpec::id).toList();
        } catch (Exception unreadable) {
            return List.of();
        }
    }

    /** The save lane, the reload's read, the EDT and the password lane — until {@code done} or 10 s. */
    private static void until(BooleanSupplier done) throws Exception {
        Field lane = DbStudioTopComponent.class.getDeclaredField("SAVES");
        lane.setAccessible(true);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            ((SaveLane) lane.get(null)).flush(5, TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(() -> { });
            if (done.getAsBoolean()) {
                break;
            }
            Thread.sleep(20);
        }
        // whatever the RP lanes were given to delete has had its chance
        for (int i = 0; i < 3; i++) {
            DbStudioTopComponent.RP.post(() -> { }).waitFinished();
        }
        Thread.sleep(50);
    }

    @Test
    @DisplayName("a removal whose save is refused leaves the password with the connection the reload brings back")
    void aRefusedRemovalKeepsThePassword(@TempDir File dir) throws Exception {
        DbWorkspaceIO.save(dir, List.of(LOCAL, REPORTS));
        Passwords.save(REPORTS.id(), "s3cret".toCharArray());
        DbStudioTopComponent w = boundTo(dir);
        // a pull lands meanwhile; the file still names reports
        Files.writeString(new File(dir, DbWorkspaceIO.FILENAME).toPath(),
                DbWorkspaceIO.toJson(List.of(LOCAL, REPORTS, ORDERS)), StandardCharsets.UTF_8);

        removeConfirmed(w, REPORTS);
        until(() -> {
            try {
                return shownIds(w).contains("r2");
            } catch (Exception e) {
                return false;
            }
        });

        assertThat(idsOnDisk(dir)).as("the refused save wrote nothing").contains("r2");
        assertThat(shownIds(w)).as("the reload brought the connection back").contains("r2");
        assertThat(Passwords.read(REPORTS.id())).as("and its password with it")
                .isEqualTo("s3cret".toCharArray());
    }

    @Test
    @DisplayName("a removal whose save lands deletes the password")
    void aLandedRemovalDeletesThePassword(@TempDir File dir) throws Exception {
        DbWorkspaceIO.save(dir, List.of(LOCAL, REPORTS));
        Passwords.save(REPORTS.id(), "s3cret".toCharArray());
        DbStudioTopComponent w = boundTo(dir);

        removeConfirmed(w, REPORTS);
        until(() -> !idsOnDisk(dir).contains("r2") && Passwords.read(REPORTS.id()) == null);

        assertThat(idsOnDisk(dir)).containsExactly("c1");
        assertThat(Passwords.read(REPORTS.id())).as("nothing names it any more").isNull();
    }

    @Test
    @DisplayName("a refused removal the teammate's version agrees with deletes the password on the read")
    void aRefusedRemovalTheirVersionAgreesWith(@TempDir File dir) throws Exception {
        DbWorkspaceIO.save(dir, List.of(LOCAL, REPORTS));
        Passwords.save(REPORTS.id(), "s3cret".toCharArray());
        DbStudioTopComponent w = boundTo(dir);
        // their version dropped reports too, and added orders
        Files.writeString(new File(dir, DbWorkspaceIO.FILENAME).toPath(),
                DbWorkspaceIO.toJson(List.of(LOCAL, ORDERS)), StandardCharsets.UTF_8);

        removeConfirmed(w, REPORTS);
        until(() -> {
            try {
                return shownIds(w).contains("c9") && Passwords.read(REPORTS.id()) == null;
            } catch (Exception e) {
                return false;
            }
        });

        assertThat(shownIds(w)).doesNotContain("r2");
        assertThat(Passwords.read(REPORTS.id())).as("no file names it, so nothing needs it").isNull();
    }
}
