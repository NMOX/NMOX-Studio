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
import javax.swing.JLabel;
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
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A connection confirmed in a dialog whose save found a changed file (3.4).
 * The save writes nothing and the workspace is read again; before this, the
 * read replaced the list the connection had been added to, so the connection
 * was gone while its password stayed in the keychain under an id no file
 * named. It is kept instead and written onto the version on disk — the
 * choice that never loses the user's work without telling them.
 */
class HeldConnectionTest {

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
    private static final ConnectionSpec REPORTS = new ConnectionSpec("c2", "reports", DbEngine.SQLITE,
            "", -1, "", "", "reports.db", false);
    private static final ConnectionSpec ORDERS = new ConnectionSpec("c9", "orders", DbEngine.SQLITE,
            "", -1, "", "", "orders.db", false);

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

    /** What a confirmed Add Connection… does after its dialog closes. */
    @SuppressWarnings("unchecked")
    private static void confirmed(DbStudioTopComponent w, ConnectionSpec spec) throws Exception {
        Field specs = DbStudioTopComponent.class.getDeclaredField("specs");
        specs.setAccessible(true);
        Method hold = DbStudioTopComponent.class.getDeclaredMethod("holdUntilSaved", ConnectionSpec.class);
        hold.setAccessible(true);
        Method save = DbStudioTopComponent.class.getDeclaredMethod("saveWorkspace");
        save.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                ((List<ConnectionSpec>) specs.get(w)).add(spec);
                hold.invoke(w, spec);
                save.invoke(w);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static List<?> held(DbStudioTopComponent w) throws Exception {
        Field f = DbStudioTopComponent.class.getDeclaredField("heldConnections");
        f.setAccessible(true);
        final Object[] copy = new Object[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                copy[0] = List.copyOf((List<?>) f.get(w));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        return (List<?>) copy[0];
    }

    private static String statusText(DbStudioTopComponent w) throws Exception {
        Field f = DbStudioTopComponent.class.getDeclaredField("statusLabel");
        f.setAccessible(true);
        return ((JLabel) f.get(w)).getText();
    }

    /** The save lane, the reload's RP read and the EDT news, until {@code done} or 10 s. */
    private static void until(BooleanSupplier done) throws Exception {
        Field lane = DbStudioTopComponent.class.getDeclaredField("SAVES");
        lane.setAccessible(true);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            ((SaveLane) lane.get(null)).flush(5, TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(() -> { });
            if (done.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
    }

    private static List<String> idsOnDisk(File dir) {
        try {
            return DbWorkspaceIO.fromJson(Files.readString(new File(dir, DbWorkspaceIO.FILENAME).toPath()))
                    .stream().map(ConnectionSpec::id).toList();
        } catch (Exception unreadable) {
            return List.of();
        }
    }

    @Test
    @DisplayName("a connection confirmed onto a changed file is written onto the version on disk")
    void theConnectionIsWrittenOntoTheirVersion(@TempDir File dir) throws Exception {
        DbWorkspaceIO.save(dir, List.of(LOCAL));
        DbStudioTopComponent w = boundTo(dir);
        // a pull lands a teammate's connection while Add Connection… is open
        Files.writeString(new File(dir, DbWorkspaceIO.FILENAME).toPath(),
                DbWorkspaceIO.toJson(List.of(LOCAL, REPORTS)), StandardCharsets.UTF_8);

        confirmed(w, ORDERS);
        until(() -> idsOnDisk(dir).contains("c9"));

        assertThat(idsOnDisk(dir)).as("theirs kept, the confirmed one written onto it")
                .containsExactlyInAnyOrder("c1", "c2", "c9");
        until(() -> {
            try {
                return held(w).isEmpty();
            } catch (Exception e) {
                return false;
            }
        });
        assertThat(held(w)).as("a write carried it, so nothing is held").isEmpty();
        assertThat(statusText(w)).as("the change is not called lost").doesNotContain("make the change again");
    }

    @Test
    @DisplayName("a conflicted file keeps the connection held and says so; the save after the merge writes it")
    void aConflictHoldsTheConnectionUntilAWriteCanCarryIt(@TempDir File dir) throws Exception {
        File f = new File(dir, DbWorkspaceIO.FILENAME);
        DbWorkspaceIO.save(dir, List.of(LOCAL));
        DbStudioTopComponent w = boundTo(dir);
        Files.writeString(f.toPath(), TeamDbWorkspaceTest.conflicted(), StandardCharsets.UTF_8);
        byte[] conflicted = Files.readAllBytes(f.toPath());

        confirmed(w, ORDERS);
        until(() -> {
            try {
                return statusText(w).contains("orders");
            } catch (Exception e) {
                return false;
            }
        });

        assertThat(Files.readAllBytes(f.toPath())).as("git's markers stay").isEqualTo(conflicted);
        assertThat(held(w)).as("held, never dropped with the list it was added to").hasSize(1);
        assertThat(statusText(w)).as("said by name, with its password's fate")
                .contains("orders").contains("keychain");

        // the conflict is resolved in git; the window reads the file again
        Files.writeString(f.toPath(), DbWorkspaceIO.toJson(List.of(LOCAL)), StandardCharsets.UTF_8);
        Method reload = DbStudioTopComponent.class.getDeclaredMethod("reloadWorkspace");
        reload.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                reload.invoke(w);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        until(() -> idsOnDisk(dir).contains("c9"));

        assertThat(idsOnDisk(dir)).containsExactlyInAnyOrder("c1", "c9");
    }

    @Test
    @DisplayName("every dialog that confirms a connection holds it until its save lands")
    void everyConfirmHolds() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/dbstudio/ui/DbStudioTopComponent.java"),
                StandardCharsets.UTF_8);
        int dialogs = src.split("showConnectionDialog\\(\\(\\) ->", -1).length - 1;
        int holds = src.split("\n        holdUntilSaved\\(", -1).length - 1;
        assertThat(dialogs).as("add, edit, from .env, from Docker").isEqualTo(4);
        assertThat(holds).as("one hold per confirmed dialog").isEqualTo(dialogs);
    }
}
