package org.nmox.studio.infra;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.SaveLane;
import org.nmox.studio.infra.model.DesignSync;
import org.nmox.studio.infra.model.GraphIO;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a second developer does to the shared {@code .nmoxinfra.json}
 * (3.4, question 1): a merge conflict, a newer version's node kind, and a
 * teammate's cloud token that sees a different account.
 */
class TeamDesignTest {

    /** Git's markers, built so no tracked line of this file starts with one. */
    static String conflicted() {
        return "{\n  \"version\": 1,\n  \"nodes\": [\n"
                + "<".repeat(7) + " HEAD\n"
                + "    {\"id\": \"droplet-3\", \"kind\": \"DROPLET\", \"x\": 1, \"y\": 1, \"doId\": \"111\"}\n"
                + "=".repeat(7) + "\n"
                + "    {\"id\": \"droplet-3\", \"kind\": \"DROPLET\", \"x\": 9, \"y\": 9, \"doId\": \"222\"}\n"
                + ">".repeat(7) + " feature/bob\n"
                + "  ],\n  \"wires\": []\n}\n";
    }

    private static void call(Object target, String name) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(target);
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    @Test
    @DisplayName("the seam: a conflicted design is left as it is, cleared from the canvas, read-only")
    void seamReportsTheConflict(@TempDir File dir) throws Exception {
        File f = new File(dir, GraphIO.DEFAULT_FILENAME);
        Files.writeString(f.toPath(), conflicted(), StandardCharsets.UTF_8);
        InfraGraph graph = new InfraGraph();

        GraphIO.LoadOutcome outcome = GraphIO.loadGuarded(graph, f);

        assertThat(outcome.conflicted()).isTrue();
        assertThat(outcome.readOnly()).isTrue();
        assertThat(outcome.backup()).as("not corrupt, so nothing is moved aside").isNull();
        assertThat(dir.list()).containsExactly(GraphIO.DEFAULT_FILENAME);
    }

    @Test
    @DisplayName("the designer's own load → save leaves a conflicted design untouched")
    void conflictedDesignSurvivesTheDesigner(@TempDir File dir) throws Exception {
        File f = new File(dir, GraphIO.DEFAULT_FILENAME);
        Files.writeString(f.toPath(), conflicted(), StandardCharsets.UTF_8);
        byte[] before = Files.readAllBytes(f.toPath());

        String home = System.getProperty("user.home");
        System.setProperty("user.home", dir.getAbsolutePath());
        final Object[] made = new Object[1];
        try {
            SwingUtilities.invokeAndWait(() -> made[0] = new InfraDesignerTopComponent());
            SwingUtilities.invokeAndWait(() -> {
                try {
                    call(made[0], "load");
                    call(made[0], "save");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            Field lane = InfraDesignerTopComponent.class.getDeclaredField("SAVES");
            lane.setAccessible(true);
            ((SaveLane) lane.get(null)).flush(5, TimeUnit.SECONDS);
        } finally {
            System.setProperty("user.home", home);
        }

        assertThat(Files.readAllBytes(f.toPath()))
                .as("both people's links to live billed resources stay for git to merge")
                .isEqualTo(before);
        assertThat(dir.list()).containsExactly(GraphIO.DEFAULT_FILENAME);
        assertThat((boolean) field(made[0], "designReadOnly")).isTrue();
        assertThat((String) field(made[0], "readOnlyText")).contains("merge conflicts");
        DesignSync sync = (DesignSync) field(made[0], "designSync");
        assertThat(sync.check(DesignSync.Stamp.of(f), false))
                .as("the conflicted bytes are never recorded as the designer's own")
                .isNotEqualTo(DesignSync.Verdict.NONE);
    }

    @Test
    @DisplayName("a node kind a newer NMOX Studio wrote binds read-only instead of being dropped")
    void newerKindIsReadOnly(@TempDir File dir) throws Exception {
        File f = new File(dir, GraphIO.DEFAULT_FILENAME);
        Files.writeString(f.toPath(), """
            {"version": 2, "nodes": [
              {"id": "quantum-1", "kind": "QUANTUM_TUNNEL", "x": 0, "y": 0, "doId": "billed-9"},
              {"id": "droplet-2", "kind": "DROPLET", "x": 10, "y": 20}],
             "wires": [{"from": "quantum-1", "to": "droplet-2"}]}
            """, StandardCharsets.UTF_8);

        GraphIO.LoadOutcome outcome = GraphIO.loadGuarded(new InfraGraph(), f);

        assertThat(outcome.newerFormat()).isTrue();
        assertThat(outcome.readOnly())
                .as("the next save used to drop the node, its doId and its wire").isTrue();
    }

    @Test
    @DisplayName("a second corrupt design never overwrites the first rescue")
    void secondRescueKeepsTheFirst(@TempDir File dir) throws Exception {
        File f = new File(dir, GraphIO.DEFAULT_FILENAME);
        Files.writeString(f.toPath(), "{ first", StandardCharsets.UTF_8);
        File first = GraphIO.loadGuarded(new InfraGraph(), f).backup();
        Files.writeString(f.toPath(), "{ second", StandardCharsets.UTF_8);
        File second = GraphIO.loadGuarded(new InfraGraph(), f).backup();

        assertThat(Files.readString(first.toPath())).isEqualTo("{ first");
        assertThat(Files.readString(second.toPath())).isEqualTo("{ second");
    }

    /**
     * The other half of the two-proof law: the seam returns what it did not
     * find instead of severing it ({@code DriftNeverSeversTest}); this pins
     * that the designer forgets only behind the safe-default question
     * ({@code confirm} defaults to No — {@code DialogSafetyTest}).
     */
    @Test
    @DisplayName("Refresh forgets a link only behind the safe-default question")
    void refreshForgetsOnlyOnYes() throws Exception {
        String src = Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/infra/InfraDesignerTopComponent.java"),
                StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        int start = src.indexOf("private void refreshDrift()");
        String body = src.substring(start, src.indexOf("static String notFoundQuestion(", start));
        assertThat(body).contains("if (!notFound.isEmpty() && confirm(notFoundQuestion(notFound), "
                + "Bundle.InfraDesigner_notFoundTitle())) { forgetCloudLinks(notFound); }");
        assertThat(body.split("forgetCloudLinks\\(", -1).length - 1)
                .as("no second, unguarded forget").isEqualTo(1);
        assertThat(body).doesNotContain("doId = null");
    }

    /**
     * A read-only bind may save nothing, so a resolution in git must be
     * FOLLOWED, not met with "keep your unsaved canvas edits?" — edits the
     * designer could never have written. (The check reacts only in an open
     * tab, which a headless test cannot give it; the order is pinned here.)
     */
    @Test
    @DisplayName("a read-only design follows the file when it changes, pending edits or not")
    void readOnlyFollowsTheFile() throws Exception {
        String src = Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/infra/InfraDesignerTopComponent.java"),
                StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        int start = src.indexOf("private void handleExternalStamp(");
        String body = src.substring(start, src.indexOf("switch (designSync.check(", start));
        assertThat(body).contains("if (designReadOnly) {");
        assertThat(body.indexOf("saveDebounce.stop();"))
                .as("the pending edits are dropped before the check decides")
                .isGreaterThan(body.indexOf("if (designReadOnly) {"));
    }

    @Test
    @DisplayName("the forget question says how many, which, and that it is shared")
    void forgetQuestionSpeaks() {
        InfraGraph graph = new InfraGraph();
        InfraNode web = graph.addNode(NodeKind.DROPLET, 0, 0);
        web.label = "web";
        InfraNode db = graph.addNode(NodeKind.DB_POSTGRES, 0, 0);
        db.label = "orders-db";

        assertThat(InfraDesignerTopComponent.notFoundQuestion(List.of(web)))
                .startsWith("One deployed resource was not found").contains("web")
                .contains("teammate");
        assertThat(InfraDesignerTopComponent.notFoundQuestion(List.of(web, db)))
                .startsWith("2 deployed resources were not found").contains("web, orders-db");

        web.doId = "1";
        db.doId = "2";
        InfraDesignerTopComponent.forgetCloudLinks(List.of(web));
        assertThat(web.doId).isNull();
        assertThat(db.doId).as("only the nodes the user agreed to forget").isEqualTo("2");
    }
}
