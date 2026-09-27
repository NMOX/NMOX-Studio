package org.nmox.studio.infra.model;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The rules a refused save's links follow, without a window (3.4). */
class PendingCloudLinksTest {

    private static final File A = new File("a/.nmoxinfra.json");
    private static final File B = new File("b/.nmoxinfra.json");

    private static InfraGraph.InfraNode droplet(InfraGraph graph, String doId) {
        InfraGraph.InfraNode node = graph.addNode(NodeKind.DROPLET, 0, 0);
        node.doId = doId;
        return node;
    }

    @Test
    @DisplayName("only the links the file is not known to hold are unsaved")
    void unsavedIsTheDifference() {
        InfraGraph graph = new InfraGraph();
        InfraGraph.InfraNode saved = droplet(graph, "1");
        InfraGraph.InfraNode fresh = droplet(graph, "2");
        droplet(graph, null);

        List<PendingCloudLinks.Link> unsaved =
                PendingCloudLinks.unsaved(graph, Map.of(saved.id, "1"), A);

        assertThat(unsaved).extracting(PendingCloudLinks.Link::nodeId).containsExactly(fresh.id);
    }

    @Test
    @DisplayName("a read-only design takes no link: it stays pending and is said once")
    void readOnlyTakesNothing() {
        InfraGraph graph = new InfraGraph();
        InfraGraph.InfraNode node = droplet(graph, "7");
        PendingCloudLinks pending = new PendingCloudLinks();
        pending.add(PendingCloudLinks.unsaved(graph, Map.of(), A));
        node.doId = null;

        PendingCloudLinks.Reapplied first = pending.reapply(graph, A, false);
        PendingCloudLinks.Reapplied second = pending.reapply(graph, A, false);

        assertThat(node.doId).isNull();
        assertThat(first.applied()).isEmpty();
        assertThat(first.toSay()).hasSize(1);
        assertThat(second.toSay()).as("said once, not once per reload").isEmpty();
        assertThat(pending.pending()).hasSize(1);
    }

    @Test
    @DisplayName("a node linked to a different resource is not overwritten; the link is said instead")
    void neverOverwritesAnotherLink() {
        InfraGraph graph = new InfraGraph();
        InfraGraph.InfraNode node = droplet(graph, "7");
        PendingCloudLinks pending = new PendingCloudLinks();
        pending.add(PendingCloudLinks.unsaved(graph, Map.of(), A));
        node.doId = "8"; // their version linked it to another resource

        PendingCloudLinks.Reapplied result = pending.reapply(graph, A, true);

        assertThat(node.doId).isEqualTo("8");
        assertThat(result.toSay()).extracting(PendingCloudLinks.Link::doId).containsExactly("7");
    }

    @Test
    @DisplayName("a node already carrying the link settles it; a write of the file lands the rest")
    void settledAndLanded() {
        InfraGraph graph = new InfraGraph();
        InfraGraph.InfraNode kept = droplet(graph, "7");
        InfraGraph.InfraNode other = droplet(graph, "9");
        PendingCloudLinks pending = new PendingCloudLinks();
        pending.add(PendingCloudLinks.unsaved(graph, Map.of(), A));

        pending.reapply(graph, A, true);
        assertThat(pending.pending()).as("both nodes already carry theirs").isEmpty();

        other.doId = null;
        pending.add(List.of(new PendingCloudLinks.Link(A, other.id, "Droplet", "x", "DigitalOcean", "9", null),
                new PendingCloudLinks.Link(B, kept.id, "Droplet", "y", "DigitalOcean", "5", null)));
        pending.landed(A, List.of("9"));
        assertThat(pending.pending()).as("a write of A never settles B's link")
                .extracting(PendingCloudLinks.Link::doId).containsExactly("5");
    }

    @Test
    @DisplayName("a link goes back onto its node only in its own file's design")
    void onlyItsOwnFile() {
        InfraGraph graph = new InfraGraph();
        InfraGraph.InfraNode node = droplet(graph, "7");
        PendingCloudLinks pending = new PendingCloudLinks();
        pending.add(PendingCloudLinks.unsaved(graph, Map.of(), A));
        node.doId = null;

        PendingCloudLinks.Reapplied elsewhere = pending.reapply(graph, B, true);
        assertThat(node.doId).isNull();
        assertThat(elsewhere.toSay()).hasSize(1);

        PendingCloudLinks.Reapplied home = pending.reapply(graph, A, true);
        assertThat(node.doId).isEqualTo("7");
        assertThat(home.applied()).hasSize(1);
        assertThat(pending.pending()).as("pending until a write lands").hasSize(1);
    }
}
