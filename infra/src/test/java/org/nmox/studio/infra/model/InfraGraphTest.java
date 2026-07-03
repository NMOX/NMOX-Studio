package org.nmox.studio.infra.model;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The canvas graph model: id sequencing that never collides across
 * restores, catalog-checked wiring, and listeners told exactly once
 * per real mutation.
 */
class InfraGraphTest {

    /** Counts what the graph claims happened. */
    private static final class CountingListener implements InfraGraph.Listener {

        final AtomicInteger changes = new AtomicInteger();
        final AtomicInteger statusChanges = new AtomicInteger();

        @Override
        public void graphChanged() {
            changes.incrementAndGet();
        }

        @Override
        public void nodeStatusChanged(InfraGraph.InfraNode node) {
            statusChanges.incrementAndGet();
        }
    }

    @Test
    @DisplayName("Node ids are kind-prefixed and strictly sequential across kinds")
    void idsAreSequential() {
        InfraGraph graph = new InfraGraph();

        var vpc = graph.addNode(NodeKind.VPC, 0, 0);
        var droplet = graph.addNode(NodeKind.DROPLET, 100, 0);
        var second = graph.addNode(NodeKind.DROPLET, 200, 0);

        assertThat(vpc.id).isEqualTo("vpc-1");
        assertThat(droplet.id).isEqualTo("droplet-2");
        assertThat(second.id).isEqualTo("droplet-3");
        assertThat(graph.node("droplet-2")).isSameAs(droplet);
        assertThat(graph.node("droplet-99")).as("stranger id").isNull();
    }

    @Test
    @DisplayName("Restoring a high-numbered node bumps the sequence so new nodes never collide")
    void restoreBumpsSequence() {
        InfraGraph graph = new InfraGraph();

        graph.restoreNode("droplet-7", NodeKind.DROPLET, 0, 0);
        var next = graph.addNode(NodeKind.VPC, 0, 0);

        assertThat(next.id).isEqualTo("vpc-8");
    }

    @Test
    @DisplayName("A restored id without a numeric suffix leaves sequencing untouched")
    void nonNumericRestoredIdIsHarmless() {
        InfraGraph graph = new InfraGraph();

        graph.restoreNode("imported-legacy", NodeKind.VPC, 0, 0);
        var next = graph.addNode(NodeKind.DROPLET, 0, 0);

        assertThat(next.id).isEqualTo("droplet-1");
        assertThat(graph.node("imported-legacy")).isNotNull();
    }

    @Test
    @DisplayName("Wire validation refuses nulls, self-wires and catalog-forbidden pairs")
    void wireValidation() {
        InfraGraph graph = new InfraGraph();
        var vpc = graph.addNode(NodeKind.VPC, 0, 0);
        var droplet = graph.addNode(NodeKind.DROPLET, 100, 0);
        var domain = graph.addNode(NodeKind.DOMAIN, 200, 0);

        assertThat(graph.canConnect(null, droplet)).isFalse();
        assertThat(graph.canConnect(vpc, null)).isFalse();
        assertThat(graph.canConnect(vpc, vpc)).as("self-wire").isFalse();
        assertThat(graph.canConnect(vpc, domain)).as("catalog forbids vpc->domain").isFalse();
        assertThat(graph.canConnect(vpc, droplet)).isTrue();
        assertThat(graph.canConnect(droplet, vpc)).as("direction matters").isFalse();
    }

    @Test
    @DisplayName("connect refuses duplicates and invalid wires without firing listeners")
    void connectRefusalsAreSilent() {
        InfraGraph graph = new InfraGraph();
        var vpc = graph.addNode(NodeKind.VPC, 0, 0);
        var droplet = graph.addNode(NodeKind.DROPLET, 100, 0);
        CountingListener listener = new CountingListener();
        graph.addListener(listener);

        assertThat(graph.connect(vpc, droplet)).isTrue();
        assertThat(listener.changes).hasValue(1);

        assertThat(graph.connect(vpc, droplet)).as("duplicate").isFalse();
        assertThat(graph.connect(droplet, vpc)).as("invalid direction").isFalse();
        assertThat(listener.changes).as("refusals stay silent").hasValue(1);
        assertThat(graph.getWires()).hasSize(1);
    }

    @Test
    @DisplayName("Removing a node severs its wires; a second removal fires nothing")
    void removalSeversWires() {
        InfraGraph graph = new InfraGraph();
        var vpc = graph.addNode(NodeKind.VPC, 0, 0);
        var droplet = graph.addNode(NodeKind.DROPLET, 100, 0);
        graph.connect(vpc, droplet);
        CountingListener listener = new CountingListener();
        graph.addListener(listener);

        graph.removeNode(droplet);
        assertThat(graph.getNodes()).containsExactly(vpc);
        assertThat(graph.getWires()).isEmpty();
        assertThat(listener.changes).hasValue(1);

        graph.removeNode(droplet);
        assertThat(listener.changes).as("removing a ghost is a no-op").hasValue(1);
    }

    @Test
    @DisplayName("Every real mutation notifies listeners exactly once; removed listeners go silent")
    void listenerAccounting() {
        InfraGraph graph = new InfraGraph();
        CountingListener listener = new CountingListener();
        graph.addListener(listener);

        var vpc = graph.addNode(NodeKind.VPC, 0, 0);        // +1
        var droplet = graph.addNode(NodeKind.DROPLET, 0, 0); // +1
        graph.connect(vpc, droplet);                          // +1
        var wire = graph.getWires().get(0);
        graph.disconnect(wire);                               // +1
        graph.disconnect(wire);                               // +0, already gone
        graph.touch();                                        // +1
        graph.clear();                                        // +1

        assertThat(listener.changes).as("two adds, connect, one real disconnect, touch, clear")
                .hasValue(6);

        graph.removeListener(listener);
        graph.addNode(NodeKind.VPC, 0, 0);
        assertThat(listener.changes).as("removed listener hears nothing").hasValue(6);
    }

    @Test
    @DisplayName("setStatus updates the node and tells status listeners, not graph listeners")
    void statusChangesAreSeparate() {
        InfraGraph graph = new InfraGraph();
        var droplet = graph.addNode(NodeKind.DROPLET, 0, 0);
        CountingListener listener = new CountingListener();
        graph.addListener(listener);

        graph.setStatus(droplet, "deploying");

        assertThat(droplet.status).isEqualTo("deploying");
        assertThat(listener.statusChanges).hasValue(1);
        assertThat(listener.changes).as("status is not a topology change").hasValue(0);
    }

    @Test
    @DisplayName("providersOf lists exactly the nodes wired into the target")
    void providersAreTracked() {
        InfraGraph graph = new InfraGraph();
        var vpc = graph.addNode(NodeKind.VPC, 0, 0);
        var droplet = graph.addNode(NodeKind.DROPLET, 100, 0);
        var domain = graph.addNode(NodeKind.DOMAIN, 200, 0);
        graph.connect(vpc, droplet);
        graph.connect(droplet, domain);

        assertThat(graph.providersOf(droplet)).containsExactly(vpc);
        assertThat(graph.providersOf(domain)).containsExactly(droplet);
        assertThat(graph.providersOf(vpc)).as("nothing wires into the vpc").isEmpty();
    }

    @Test
    @DisplayName("clear empties nodes and wires in one announced sweep")
    void clearEmptiesEverything() {
        InfraGraph graph = new InfraGraph();
        var vpc = graph.addNode(NodeKind.VPC, 0, 0);
        graph.connect(vpc, graph.addNode(NodeKind.DROPLET, 100, 0));
        CountingListener listener = new CountingListener();
        graph.addListener(listener);

        graph.clear();

        assertThat(graph.getNodes()).isEmpty();
        assertThat(graph.getWires()).isEmpty();
        assertThat(listener.changes).hasValue(1);
    }
}
