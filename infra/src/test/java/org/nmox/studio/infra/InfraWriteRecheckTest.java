package org.nmox.studio.infra;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.SaveLane;
import org.nmox.studio.infra.model.GraphIO;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Infra Designer re-checks the disk immediately before every write (3.4,
 * the hostile review's second round): its CONFLICT verdict held the pending
 * save only until the next canvas edit, whose save then wrote over git's
 * markers and a teammate's {@code doId} links to live billed resources.
 */
class InfraWriteRecheckTest {

    private String home;

    @BeforeEach
    void rememberHome() {
        home = System.getProperty("user.home");
    }

    @AfterEach
    void restoreHome() {
        System.setProperty("user.home", home);
    }

    private static void onEdt(Object target, String name) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                m.invoke(target);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void flushSaveLane() throws Exception {
        Field lane = InfraDesignerTopComponent.class.getDeclaredField("SAVES");
        lane.setAccessible(true);
        ((SaveLane) lane.get(null)).flush(5, TimeUnit.SECONDS);
    }

    /** A designer that loaded {@code dir}'s design, writable. */
    private static InfraDesignerTopComponent loaded(File dir) throws Exception {
        System.setProperty("user.home", dir.getAbsolutePath());
        final InfraDesignerTopComponent[] made = new InfraDesignerTopComponent[1];
        SwingUtilities.invokeAndWait(() -> made[0] = new InfraDesignerTopComponent());
        onEdt(made[0], "load");
        return made[0];
    }

    private static void writeDesign(File dir) throws Exception {
        InfraGraph graph = new InfraGraph();
        graph.addNode(NodeKind.DROPLET, 10, 20).doId = "111";
        GraphIO.save(graph, new File(dir, GraphIO.DEFAULT_FILENAME));
    }

    @Test
    @DisplayName("a conflict that lands after a writable load is not written over by the next save")
    void conflictLandingAfterTheLoadSurvives(@TempDir File dir) throws Exception {
        writeDesign(dir);
        InfraDesignerTopComponent designer = loaded(dir);
        assertThat((boolean) field(designer, "designReadOnly")).isFalse();

        File f = new File(dir, GraphIO.DEFAULT_FILENAME);
        Files.writeString(f.toPath(), TeamDesignTest.conflicted(), StandardCharsets.UTF_8);
        byte[] conflicted = Files.readAllBytes(f.toPath());
        onEdt(designer, "save"); // the next canvas edit's debounced save
        flushSaveLane();

        assertThat(Files.readAllBytes(f.toPath()))
                .as("both people's doId links stay for git to merge")
                .isEqualTo(conflicted);
        SwingUtilities.invokeAndWait(() -> { });
        assertThat((boolean) field(designer, "designReadOnly"))
                .as("the refusal reloads, and the reload binds the conflict read-only").isTrue();
    }

    @Test
    @DisplayName("a teammate's clean change is reloaded, not written over")
    void aTeammatesChangeIsNotWrittenOver(@TempDir File dir) throws Exception {
        writeDesign(dir);
        InfraDesignerTopComponent designer = loaded(dir);

        File f = new File(dir, GraphIO.DEFAULT_FILENAME);
        InfraGraph theirs = new InfraGraph();
        theirs.addNode(NodeKind.DROPLET, 10, 20).doId = "111";
        theirs.addNode(NodeKind.DROPLET, 90, 20).doId = "222";
        GraphIO.save(theirs, f);
        String their = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        onEdt(designer, "save");
        flushSaveLane();

        assertThat(Files.readString(f.toPath(), StandardCharsets.UTF_8)).isEqualTo(their);
        SwingUtilities.invokeAndWait(() -> { });
        InfraGraph shown = (InfraGraph) field(designer, "graph");
        assertThat(shown.getNodes()).as("the canvas follows their version").hasSize(2);
    }

    @Test
    @DisplayName("a design whose version is above this build's binds read-only, even with known kinds only")
    void higherVersionIsReadOnly(@TempDir File dir) throws Exception {
        File f = new File(dir, GraphIO.DEFAULT_FILENAME);
        Files.writeString(f.toPath(), "{\"version\": 2, \"nodes\": [{\"id\": \"droplet-2\", \"kind\": \"DROPLET\","
                + " \"x\": 10, \"y\": 20, \"monitoring\": true}], \"wires\": []}", StandardCharsets.UTF_8);
        assertThat(GraphIO.loadGuarded(new InfraGraph(), f).readOnly()).isTrue();

        Files.writeString(f.toPath(), "{\"version\": " + GraphIO.FORMAT_VERSION + ", \"nodes\": [], \"wires\": []}",
                StandardCharsets.UTF_8);
        assertThat(GraphIO.loadGuarded(new InfraGraph(), f).readOnly()).isFalse();
    }
}
