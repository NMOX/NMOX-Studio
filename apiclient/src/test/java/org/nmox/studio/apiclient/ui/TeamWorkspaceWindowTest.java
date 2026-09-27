package org.nmox.studio.apiclient.ui;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.apiclient.api.WorkspaceIO;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Request;
import org.nmox.studio.apiclient.model.ApiModel.Workspace;
import org.nmox.studio.core.util.PersonalState;
import org.nmox.studio.core.util.SaveLane;
import org.nmox.studio.core.util.SelfWriteTracker;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A second developer's merge, driven through the REAL window (3.4).
 *
 * <p>Measured before 3.4: a conflicted {@code .nmoxapi.json} failed to
 * parse, was copied to {@code .bak}, replaced by the starter workspace and
 * stamped as the studio's own — and the next Send (its history row) saved
 * the starter over the conflicted file, which {@code git commit} then
 * recorded as the merge. And every Send rewrote the committed file.
 */
class TeamWorkspaceWindowTest {

    @TempDir
    Path personal;

    @BeforeEach
    void personalStateInTemp() {
        PersonalState.setBaseForTest(personal);
    }

    @AfterEach
    void restore() {
        PersonalState.setBaseForTest(null);
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

    /** Builds the window bound to {@code dir} ({@code user.home} is the seam) and loads it. */
    private static ApiClientTopComponent loaded(File dir) throws Exception {
        String home = System.getProperty("user.home");
        System.setProperty("user.home", dir.getAbsolutePath());
        try {
            final ApiClientTopComponent[] made = new ApiClientTopComponent[1];
            SwingUtilities.invokeAndWait(() -> made[0] = new ApiClientTopComponent());
            SwingUtilities.invokeAndWait(() -> {
                try {
                    call(made[0], "loadWorkspace");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            return made[0];
        } finally {
            System.setProperty("user.home", home);
        }
    }

    private static void onEdt(ApiClientTopComponent window, String method) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                call(window, method);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        flushSaveLane();
    }

    @Test
    @DisplayName("a conflicted workspace survives the window's own load → save, untouched")
    void conflictedWorkspaceSurvives(@TempDir File dir) throws Exception {
        File f = new File(dir, WorkspaceIO.FILENAME);
        Files.writeString(f.toPath(), org.nmox.studio.apiclient.api.TeamWorkspaceTest.conflicted(),
                StandardCharsets.UTF_8);
        byte[] before = Files.readAllBytes(f.toPath());

        ApiClientTopComponent window = loaded(dir);
        onEdt(window, "save");

        assertThat(Files.readAllBytes(f.toPath()))
                .as("git's conflict stays for git to resolve — the starter never lands on it")
                .isEqualTo(before);
        assertThat(dir.list()).as("and it is not corrupt, so nothing is moved aside")
                .containsExactly(WorkspaceIO.FILENAME);
        assertThat((boolean) field(window, "workspaceReadOnly")).isTrue();
        assertThat((String) field(window, "readOnlyText")).contains("merge conflicts");
        SelfWriteTracker tracker = (SelfWriteTracker) field(window, "selfWrites");
        assertThat(tracker.isForeign(f.lastModified(), f.length()))
                .as("the conflicted bytes are never stamped as the studio's own write")
                .isTrue();
    }

    @Test
    @DisplayName("resolving the conflict in git ends the read-only bind")
    void resolvedConflictReloads(@TempDir File dir) throws Exception {
        File f = new File(dir, WorkspaceIO.FILENAME);
        Files.writeString(f.toPath(), org.nmox.studio.apiclient.api.TeamWorkspaceTest.conflicted(),
                StandardCharsets.UTF_8);
        ApiClientTopComponent window = loaded(dir);
        assertThat((boolean) field(window, "workspaceReadOnly")).isTrue();

        WorkspaceIO.save(dir, Workspace.starter("Payments", "List all charges", "Staging"));
        String home = System.getProperty("user.home");
        System.setProperty("user.home", dir.getAbsolutePath());
        try {
            onEdt(window, "loadWorkspace");
        } finally {
            System.setProperty("user.home", home);
        }

        assertThat((boolean) field(window, "workspaceReadOnly")).isFalse();
        Workspace shown = (Workspace) field(window, "workspace");
        assertThat(shown.collections.get(0).requests.get(0).name).isEqualTo("List all charges");
    }

    @Test
    @DisplayName("a Send's history row never rewrites the shared file — it is this person's")
    void sendHistoryStaysOutOfTheSharedFile(@TempDir File dir) throws Exception {
        File f = new File(dir, WorkspaceIO.FILENAME);
        WorkspaceIO.save(dir, Workspace.starter("Payments", "List charges", "Staging"));
        long marker = f.lastModified() - 10_000L;
        assertThat(f.setLastModified(marker)).isTrue();
        ApiClientTopComponent window = loaded(dir);

        Request sent = new Request();
        sent.name = "Probe send";
        sent.url = "http://localhost:3000/health";
        Method record = ApiClientTopComponent.class.getDeclaredMethod(
                "recordHistory", Request.class, int.class, long.class);
        record.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                record.invoke(window, sent, 200, 12L);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        flushSaveLane();

        assertThat(f.lastModified()).as("the committed file was not rewritten by a Send")
                .isEqualTo(marker);
        assertThat(Files.readString(f.toPath())).doesNotContain("Probe send");
        assertThat(PersonalState.read(dir, WorkspaceIO.PERSONAL_STUDIO))
                .as("the history row went to this person's own state")
                .contains("Probe send");
    }

    @Test
    @DisplayName("a missing token is refused in words that say where tokens live")
    void missingTokenSpeaks() {
        Request r = new Request();
        r.authType = AuthType.BEARER;
        assertThat(ApiClientTopComponent.credentialRefusal(r))
                .contains("Bearer").contains("keychain").contains("Auth");
        r.authToken = "t";
        assertThat(ApiClientTopComponent.credentialRefusal(r)).isNull();
        r.foreignAuthType = "OAUTH2";
        assertThat(ApiClientTopComponent.credentialRefusal(r)).contains("OAUTH2");
    }
}
