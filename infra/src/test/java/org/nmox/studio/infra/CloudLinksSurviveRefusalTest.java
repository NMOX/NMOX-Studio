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
import org.nmox.studio.infra.model.PendingCloudLinks;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A deploy writes a node's {@code doId} and then saves; when that save finds
 * somebody else's bytes on disk it writes nothing and the design reloads
 * (3.4). Before this, the reload replaced the canvas and every {@code doId}
 * the deploy had just written with it — the resources went on existing and
 * billing with nothing left in the design to find or destroy them by.
 */
class CloudLinksSurviveRefusalTest {

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

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** The save lane drained, then the EDT news it posted — twice, for a refusal's reload-and-save. */
    private static void settle() throws Exception {
        Field lane = InfraDesignerTopComponent.class.getDeclaredField("SAVES");
        lane.setAccessible(true);
        for (int round = 0; round < 3; round++) {
            ((SaveLane) lane.get(null)).flush(5, TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(() -> { });
        }
        InfraDesignerTopComponent.awaitDeployLog();
    }

    private static InfraDesignerTopComponent loaded(File dir) throws Exception {
        System.setProperty("user.home", dir.getAbsolutePath());
        final InfraDesignerTopComponent[] made = new InfraDesignerTopComponent[1];
        SwingUtilities.invokeAndWait(() -> made[0] = new InfraDesignerTopComponent());
        onEdt(made[0], "load");
        return made[0];
    }

    /** One droplet, not yet deployed; its node id. */
    private static String writeUndeployed(File dir) throws Exception {
        InfraGraph graph = new InfraGraph();
        InfraGraph.InfraNode node = graph.addNode(NodeKind.DROPLET, 10, 20);
        node.label = "web-1";
        GraphIO.save(graph, new File(dir, GraphIO.DEFAULT_FILENAME));
        return node.id;
    }

    /** What a deploy's onModel does on the EDT: the resource exists now. */
    private static void deployed(InfraDesignerTopComponent designer, String nodeId, String doId)
            throws Exception {
        InfraGraph graph = (InfraGraph) field(designer, "graph");
        SwingUtilities.invokeAndWait(() -> {
            graph.node(nodeId).doId = doId;
            graph.node(nodeId).ip = "203.0.113.7";
        });
    }

    private static InfraGraph onDisk(File dir) {
        InfraGraph graph = new InfraGraph();
        GraphIO.loadGuarded(graph, new File(dir, GraphIO.DEFAULT_FILENAME));
        return graph;
    }

    private static String deployLog(File dir) throws Exception {
        File log = new File(dir, ".nmox/deploy-log");
        return log.isFile() ? Files.readString(log.toPath(), StandardCharsets.UTF_8) : "";
    }

    private static PendingCloudLinks pending(InfraDesignerTopComponent designer) throws Exception {
        return (PendingCloudLinks) field(designer, "pendingLinks");
    }

    @Test
    @DisplayName("a teammate's version that still has the node gets the deploy's doId, and the next write carries it")
    void theLinkGoesBackOntoItsNodeAndIsWritten(@TempDir File dir) throws Exception {
        String web = writeUndeployed(dir);
        InfraDesignerTopComponent designer = loaded(dir);
        deployed(designer, web, "777");

        // a teammate adds a node meanwhile; their file knows nothing of 777
        InfraGraph theirs = onDisk(dir);
        theirs.addNode(NodeKind.DROPLET, 200, 20).label = "worker";
        GraphIO.save(theirs, new File(dir, GraphIO.DEFAULT_FILENAME));

        onEdt(designer, "save"); // the deploy's own save: refused, the design reloads
        settle();

        InfraGraph saved = onDisk(dir);
        assertThat(saved.getNodes()).as("their node stays").hasSize(2);
        assertThat(saved.node(web).doId).as("the deploy's link is written onto their version").isEqualTo("777");
        assertThat(saved.node(web).ip).isEqualTo("203.0.113.7");
        assertThat(pending(designer).pending()).as("a write carried it, so it is no longer pending").isEmpty();
    }

    @Test
    @DisplayName("a link whose node is gone from the reloaded design is held and said, with its name and id")
    void aLinkWithNoNodeIsHeldAndSaid(@TempDir File dir) throws Exception {
        String web = writeUndeployed(dir);
        InfraDesignerTopComponent designer = loaded(dir);
        deployed(designer, web, "777");

        InfraGraph theirs = new InfraGraph(); // their version dropped web-1
        theirs.addNode(NodeKind.DROPLET, 200, 20).label = "worker";
        File f = new File(dir, GraphIO.DEFAULT_FILENAME);
        GraphIO.save(theirs, f);
        String their = Files.readString(f.toPath(), StandardCharsets.UTF_8);

        onEdt(designer, "save");
        settle();

        assertThat(Files.readString(f.toPath(), StandardCharsets.UTF_8))
                .as("their version is not written over").isEqualTo(their);
        assertThat(pending(designer).pending()).extracting(PendingCloudLinks.Link::doId)
                .as("held, never dropped with the canvas").containsExactly("777");
        assertThat(deployLog(dir)).as("the deploy log names the resource and its id")
                .contains("web-1").contains("777").contains(GraphIO.DEFAULT_FILENAME);
        assertThat(org.openide.awt.StatusDisplayer.getDefault().getStatusText())
                .as("and so does the status line").contains("777");
    }

    @Test
    @DisplayName("a conflicted reload keeps the deploy's link pending, says it, and the save after the merge writes it")
    void aConflictHoldsTheLinkUntilAWriteCanCarryIt(@TempDir File dir) throws Exception {
        String web = writeUndeployed(dir);
        File f = new File(dir, GraphIO.DEFAULT_FILENAME);
        byte[] beforeMerge = Files.readAllBytes(f.toPath());
        InfraDesignerTopComponent designer = loaded(dir);
        deployed(designer, web, "999");

        Files.writeString(f.toPath(), TeamDesignTest.conflicted(), StandardCharsets.UTF_8);
        byte[] conflicted = Files.readAllBytes(f.toPath());
        onEdt(designer, "save");
        settle();

        assertThat(Files.readAllBytes(f.toPath())).as("git's markers stay").isEqualTo(conflicted);
        assertThat((boolean) field(designer, "designReadOnly")).isTrue();
        assertThat(pending(designer).pending()).extracting(PendingCloudLinks.Link::doId)
                .as("a read-only design takes no link; it waits").containsExactly("999");
        assertThat(deployLog(dir)).contains("999").contains("web-1");

        // the conflict is resolved in git; the designer reads the file again
        Files.write(f.toPath(), beforeMerge);
        onEdt(designer, "load");
        settle();

        assertThat(onDisk(dir).node(web).doId).as("the later save carries it").isEqualTo("999");
        assertThat(pending(designer).pending()).isEmpty();
    }

    @Test
    @DisplayName("a reload asked for while a cloud op runs waits for the op, so the op's nodes stay the shown ones")
    void aReloadDuringAnOpWaits(@TempDir File dir) throws Exception {
        String web = writeUndeployed(dir);
        InfraDesignerTopComponent designer = loaded(dir);
        InfraGraph graph = (InfraGraph) field(designer, "graph");
        InfraGraph.InfraNode before = graph.node(web);
        SwingUtilities.invokeAndWait(() -> {
            try {
                setField(designer, "opsInFlight", 1);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        onEdt(designer, "reloadWhenIdle");

        assertThat((boolean) field(designer, "pendingReaim")).isTrue();
        assertThat(graph.node(web)).as("the op still writes onto the node that is shown").isSameAs(before);
    }
}
