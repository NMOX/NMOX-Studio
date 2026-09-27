package org.nmox.studio.web3.ui;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.SaveLane;
import org.nmox.studio.web3.io.Web3WorkspaceIO;
import org.nmox.studio.web3.model.DeploymentRecord;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract Studio re-checks the disk immediately before every write (3.4,
 * the hostile review's second round), and a deployment a refused save
 * could not write is never lost: it is a contract that exists on a chain.
 */
class Web3WriteRecheckTest {

    private String home;

    @BeforeEach
    void rememberHome() {
        home = System.getProperty("user.home");
    }

    @AfterEach
    void restoreHome() {
        System.setProperty("user.home", home);
    }

    private static final DeploymentRecord VAULT = new DeploymentRecord("Vault",
            "0x00000000000000000000000000000000000000aa", "anvil", "0xtx", 7, 1L);

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

    private static void onEdt(Web3StudioTopComponent w, String method, Class<?>[] types, Object... args)
            throws Exception {
        Method m = Web3StudioTopComponent.class.getDeclaredMethod(method, types);
        m.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                m.invoke(w, args);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /** A window bound to {@code dir} by a real load of its file. */
    private static Web3StudioTopComponent boundTo(File dir) throws Exception {
        System.setProperty("user.home", dir.getAbsolutePath());
        Web3WorkspaceIO.LoadOutcome outcome = Web3WorkspaceIO.loadGuarded(dir);
        final Web3StudioTopComponent[] made = new Web3StudioTopComponent[1];
        SwingUtilities.invokeAndWait(() -> made[0] = new Web3StudioTopComponent());
        onEdt(made[0], "applyReloadedWorkspace",
                new Class<?>[]{File.class, Web3WorkspaceIO.LoadOutcome.class}, dir, outcome);
        return made[0];
    }

    @SuppressWarnings("unchecked")
    private static List<DeploymentRecord> addressBook(Web3StudioTopComponent w) throws Exception {
        return (List<DeploymentRecord>) field(w, "deployments");
    }

    @Test
    @DisplayName("a conflict landing after a writable bind is not written over by the next save")
    void conflictLandingAfterTheBindSurvives(@TempDir File dir) throws Exception {
        File f = new File(dir, Web3WorkspaceIO.FILENAME);
        Web3WorkspaceIO.save(dir, Web3WorkspaceIO.Workspace.empty());
        Web3StudioTopComponent w = boundTo(dir);
        assertThat((boolean) field(w, "workspaceReadOnly")).isFalse();

        Files.writeString(f.toPath(), TeamWeb3WindowTest.conflicted(), StandardCharsets.UTF_8);
        byte[] conflicted = Files.readAllBytes(f.toPath());
        onEdt(w, "saveWorkspace", new Class<?>[0]);
        flushSaveLane();

        assertThat(Files.readAllBytes(f.toPath())).isEqualTo(conflicted);
    }

    @Test
    @DisplayName("the window's own saves still land, one after another")
    void ordinarySavesStillLand(@TempDir File dir) throws Exception {
        Web3WorkspaceIO.save(dir, Web3WorkspaceIO.Workspace.empty());
        Web3StudioTopComponent w = boundTo(dir);
        for (int n = 1; n <= 2; n++) {
            DeploymentRecord record = new DeploymentRecord("C" + n,
                    "0x00000000000000000000000000000000000000b" + n, "anvil", "0xtx", n, n);
            onEdt(w, "recordDeployment", new Class<?>[]{DeploymentRecord.class}, record);
            flushSaveLane();
            assertThat(Web3WorkspaceIO.load(dir).deployments()).as("save " + n + " lands").hasSize(n);
        }
        SwingUtilities.invokeAndWait(() -> { });
        assertThat((List<?>) field(w, "pendingDeployments"))
                .as("a deployment a write carried is no longer held").isEmpty();
    }

    @Test
    @DisplayName("a deployment made while the file is read-only stays in the address book and is said")
    void deploymentOnAReadOnlyFileIsKeptAndSaid(@TempDir File dir) throws Exception {
        File f = new File(dir, Web3WorkspaceIO.FILENAME);
        Files.writeString(f.toPath(), TeamWeb3WindowTest.conflicted(), StandardCharsets.UTF_8);
        byte[] conflicted = Files.readAllBytes(f.toPath());
        Web3StudioTopComponent w = boundTo(dir);

        onEdt(w, "recordDeployment", new Class<?>[]{DeploymentRecord.class}, VAULT);
        flushSaveLane();

        assertThat(Files.readAllBytes(f.toPath())).as("the conflict is not written over").isEqualTo(conflicted);
        assertThat(addressBook(w)).contains(VAULT);
        assertThat(((JLabel) field(w, "statusLabel")).getText())
                .as("the address of a live contract is said, not only listed")
                .contains(VAULT.address());

        // a later reload of the still-conflicted file keeps it on screen
        onEdt(w, "applyReloadedWorkspace", new Class<?>[]{File.class, Web3WorkspaceIO.LoadOutcome.class},
                dir, Web3WorkspaceIO.loadGuarded(dir));
        assertThat(addressBook(w)).contains(VAULT);

        // resolved in git (their side kept): the deployment is written onto it
        Files.writeString(f.toPath(), "{\"version\":1,\"networks\":[],\"deployments\":["
                + "{\"contractName\":\"Token\",\"address\":\"0x02\",\"networkName\":\"anvil\"}]}",
                StandardCharsets.UTF_8);
        onEdt(w, "applyReloadedWorkspace", new Class<?>[]{File.class, Web3WorkspaceIO.LoadOutcome.class},
                dir, Web3WorkspaceIO.loadGuarded(dir));
        flushSaveLane();
        List<DeploymentRecord> onDisk = Web3WorkspaceIO.load(dir).deployments();
        assertThat(onDisk).extracting(DeploymentRecord::address)
                .containsExactlyInAnyOrder(VAULT.address(), "0x02");
    }

    @Test
    @DisplayName("a deployment whose save met a teammate's change is written onto their version")
    void deploymentRefusedByAChangeIsWrittenOntoIt(@TempDir File dir) throws Exception {
        File f = new File(dir, Web3WorkspaceIO.FILENAME);
        Web3WorkspaceIO.save(dir, Web3WorkspaceIO.Workspace.empty());
        Web3StudioTopComponent w = boundTo(dir);

        // a pull lands a teammate's deployment before this one is saved
        Files.writeString(f.toPath(), "{\"version\":1,\"networks\":[],\"deployments\":["
                + "{\"contractName\":\"Token\",\"address\":\"0x02\",\"networkName\":\"anvil\"}]}\n",
                StandardCharsets.UTF_8);
        onEdt(w, "recordDeployment", new Class<?>[]{DeploymentRecord.class}, VAULT);
        flushSaveLane();
        // the refusal reloads off the EDT: wait for the read, its apply and the re-save
        for (int i = 0; i < 50 && Web3WorkspaceIO.load(dir).deployments().size() < 2; i++) {
            Thread.sleep(100);
            SwingUtilities.invokeAndWait(() -> { });
            flushSaveLane();
        }

        assertThat(Web3WorkspaceIO.load(dir).deployments()).extracting(DeploymentRecord::address)
                .as("neither the teammate's deployment nor this one is lost")
                .containsExactlyInAnyOrder(VAULT.address(), "0x02");
    }

    @Test
    @DisplayName("a file a newer version wrote binds read-only")
    void newerVersionIsReadOnly(@TempDir File dir) throws Exception {
        Files.writeString(new File(dir, Web3WorkspaceIO.FILENAME).toPath(),
                "{\"version\":2,\"networks\":[],\"deployments\":[],\"verifiedSources\":[]}",
                StandardCharsets.UTF_8);
        Web3WorkspaceIO.LoadOutcome o = Web3WorkspaceIO.loadGuarded(dir);
        assertThat(o.newerFormat()).isTrue();
        assertThat(o.readOnly()).isTrue();
        Files.writeString(new File(dir, Web3WorkspaceIO.FILENAME).toPath(),
                "{\"version\":1,\"networks\":[],\"deployments\":[]}", StandardCharsets.UTF_8);
        assertThat(Web3WorkspaceIO.loadGuarded(dir).readOnly()).isFalse();
    }
}
