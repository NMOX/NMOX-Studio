package org.nmox.studio.web3.ui;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.SaveLane;
import org.nmox.studio.core.util.SelfWriteTracker;
import org.nmox.studio.web3.io.Web3WorkspaceIO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A second developer's merge of {@code .nmoxweb3.json}, through the REAL
 * Contract Studio window (3.4). Before, git's conflict failed the parse,
 * was copied to {@code .bak} and replaced by an empty workspace stamped as
 * the studio's own — and the next save (a network added, a deployment
 * recorded) wrote that over both people's address books.
 */
class TeamWeb3WindowTest {

    /** Git's markers, built so no tracked line of this file starts with one. */
    static String conflicted() {
        return "{\n  \"version\": 1,\n  \"networks\": [],\n  \"deployments\": [\n"
                + "<".repeat(7) + " HEAD\n"
                + "    {\"contractName\": \"Vault\", \"address\": \"0x01\", \"networkName\": \"anvil\"}\n"
                + "=".repeat(7) + "\n"
                + "    {\"contractName\": \"Token\", \"address\": \"0x02\", \"networkName\": \"anvil\"}\n"
                + ">".repeat(7) + " feature/bob\n"
                + "  ]\n}\n";
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void flushSaveLane() throws Exception {
        Field f = Web3StudioTopComponent.class.getDeclaredField("SAVES");
        f.setAccessible(true);
        ((SaveLane) f.get(null)).flush(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("the seam: a conflicted file is left as it is and reported, not backed up")
    void seamReportsTheConflict(@TempDir File dir) throws Exception {
        File f = new File(dir, Web3WorkspaceIO.FILENAME);
        Files.writeString(f.toPath(), conflicted(), StandardCharsets.UTF_8);
        Web3WorkspaceIO.LoadOutcome outcome = Web3WorkspaceIO.loadGuarded(dir);
        assertThat(outcome.conflicted()).isTrue();
        assertThat(outcome.readOnly()).isTrue();
        assertThat(outcome.backup()).isNull();
        assertThat(dir.list()).containsExactly(Web3WorkspaceIO.FILENAME);
    }

    @Test
    @DisplayName("a conflicted workspace survives the window's own apply → save, untouched")
    void conflictedWorkspaceSurvives(@TempDir File dir) throws Exception {
        File f = new File(dir, Web3WorkspaceIO.FILENAME);
        Files.writeString(f.toPath(), conflicted(), StandardCharsets.UTF_8);
        byte[] before = Files.readAllBytes(f.toPath());
        Web3WorkspaceIO.LoadOutcome outcome = Web3WorkspaceIO.loadGuarded(dir);

        Method apply = Web3StudioTopComponent.class.getDeclaredMethod(
                "applyReloadedWorkspace", File.class, Web3WorkspaceIO.LoadOutcome.class);
        apply.setAccessible(true);
        Method save = Web3StudioTopComponent.class.getDeclaredMethod("saveWorkspace");
        save.setAccessible(true);
        String home = System.getProperty("user.home");
        System.setProperty("user.home", dir.getAbsolutePath());
        final Web3StudioTopComponent[] made = new Web3StudioTopComponent[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                made[0] = new Web3StudioTopComponent();
                try {
                    apply.invoke(made[0], dir, outcome);
                    save.invoke(made[0]);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            flushSaveLane();
        } finally {
            System.setProperty("user.home", home);
        }

        assertThat(Files.readAllBytes(f.toPath())).isEqualTo(before);
        assertThat(dir.list()).contains(Web3WorkspaceIO.FILENAME)
                .noneMatch(name -> name.endsWith(".bak"));
        assertThat((boolean) field(made[0], "workspaceReadOnly")).isTrue();
        assertThat((String) field(made[0], "readOnlyText")).contains("merge conflicts");
        SelfWriteTracker tracker = (SelfWriteTracker) field(made[0], "selfWrites");
        assertThat(tracker.isForeign(f.lastModified(), f.length()))
                .as("the conflicted bytes are never stamped as the studio's own").isTrue();
    }
}
