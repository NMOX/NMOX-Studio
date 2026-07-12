package org.nmox.studio.rack.service;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The selection publisher (ledger 29, v1.45.0): resolves the aimed
 * directory's node off the EDT, delivers it on the EDT, and holds the
 * two laws every listener in this codebase holds — equality-guarded
 * (a storm of same-dir events costs one resolution) and bounded (one
 * shared lane, superseded requests deliver nothing).
 */
class AimNodePublisherTest {

    private static void settle() throws Exception {
        Thread.sleep(150);
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline && !condition.getAsBoolean()) {
            Thread.sleep(20);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    @Test
    @DisplayName("100 same-dir events resolve once and deliver once — the storm law")
    void stormOfSameDirEventsResolvesOnce(@TempDir Path dir) throws Exception {
        AtomicInteger resolved = new AtomicInteger();
        List<Node> delivered = new CopyOnWriteArrayList<>();
        AimNodePublisher publisher = new AimNodePublisher(delivered::add);
        Node node = new AbstractNode(Children.LEAF);
        publisher.resolver = d -> {
            resolved.incrementAndGet();
            return node;
        };

        for (int i = 0; i < 100; i++) {
            publisher.publish(dir.toFile());
        }
        await(() -> !delivered.isEmpty());
        settle();

        assertThat(resolved.get()).as("one resolution for the whole storm").isEqualTo(1);
        assertThat(delivered).containsExactly(node);
    }

    @Test
    @DisplayName("a re-aim delivers the new dir's node; resolution off-EDT, delivery on it")
    void reAimUpdatesAndThreadsAreRight(@TempDir Path a, @TempDir Path b) throws Exception {
        AtomicBoolean resolvedOnEdt = new AtomicBoolean();
        AtomicBoolean deliveredOffEdt = new AtomicBoolean();
        List<File> delivered = new CopyOnWriteArrayList<>();
        AimNodePublisher publisher = new AimNodePublisher(node -> {
            if (!SwingUtilities.isEventDispatchThread()) {
                deliveredOffEdt.set(true);
            }
            delivered.add((File) node.getValue("dir"));
        });
        publisher.resolver = d -> {
            if (SwingUtilities.isEventDispatchThread()) {
                resolvedOnEdt.set(true); // filesystem work on the paint thread
            }
            AbstractNode n = new AbstractNode(Children.LEAF);
            n.setValue("dir", d);
            return n;
        };

        publisher.publish(a.toFile());
        publisher.publish(b.toFile());
        await(() -> delivered.contains(b.toFile()));
        settle();

        assertThat(delivered).as("the newest aim always lands last")
                .endsWith(b.toFile());
        assertThat(resolvedOnEdt).as("resolution never runs on the EDT").isFalse();
        assertThat(deliveredOffEdt).as("delivery always lands on the EDT").isFalse();
    }

    @Test
    @DisplayName("reset forgets the guard: a reopened window re-resolves the same aim")
    void resetAllowsRepublish(@TempDir Path dir) throws Exception {
        AtomicInteger resolved = new AtomicInteger();
        AimNodePublisher publisher = new AimNodePublisher(node -> { });
        publisher.resolver = d -> {
            resolved.incrementAndGet();
            return new AbstractNode(Children.LEAF);
        };

        publisher.publish(dir.toFile());
        await(() -> resolved.get() == 1);
        publisher.publish(dir.toFile());
        settle();
        assertThat(resolved.get()).as("guarded while open").isEqualTo(1);

        publisher.reset();
        publisher.publish(dir.toFile());
        await(() -> resolved.get() == 2);
    }

    @Test
    @DisplayName("a dir that resolves to nothing delivers nothing — the old selection survives")
    void nullResolutionDeliversNothing(@TempDir Path dir) throws Exception {
        AtomicInteger deliveries = new AtomicInteger();
        AimNodePublisher publisher = new AimNodePublisher(node -> deliveries.incrementAndGet());
        publisher.resolver = d -> null;

        publisher.publish(dir.toFile());
        settle();
        assertThat(deliveries.get()).isZero();
    }

    @Test
    @DisplayName("the real resolver yields a DataFolder node carrying the dir's DataObject")
    void realResolverYieldsFolderNode(@TempDir Path dir) {
        Node node = AimNodePublisher.resolveNode(dir.toFile());

        assertThat(node).isNotNull();
        DataObject dob = node.getLookup().lookup(DataObject.class);
        assertThat(dob).as("git/context actions read the DataObject from the node lookup")
                .isNotNull();
        assertThat(FileUtil.toFile(dob.getPrimaryFile()))
                .isEqualTo(FileUtil.normalizeFile(dir.toFile()));
    }

    @Test
    @DisplayName("a plain file resolves too — the v1.48.0 tree-selection generalization")
    void realResolverYieldsFileNode(@TempDir Path dir) throws Exception {
        File file = dir.resolve("index.js").toFile();
        assertThat(file.createNewFile()).isTrue();

        Node node = AimNodePublisher.resolveNode(file);

        assertThat(node).isNotNull();
        DataObject dob = node.getLookup().lookup(DataObject.class);
        assertThat(dob).as("per-file context actions (Annotate) need the file's DataObject")
                .isNotNull();
        assertThat(FileUtil.toFile(dob.getPrimaryFile()))
                .isEqualTo(FileUtil.normalizeFile(file));
    }

    @Test
    @DisplayName("a distinct-target storm on a busy lane resolves twice, not N times")
    void distinctTargetStormCoalesces(@TempDir Path dir) throws Exception {
        // Arrow-key held down a file tree: many DIFFERENT targets arrive
        // faster than resolution. The single lane plus the supersede check
        // must skip every intermediate — resolve the first (already
        // running) and the last (the only one still current), nothing else.
        java.util.concurrent.CountDownLatch firstResolveStarted =
                new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch releaseFirstResolve =
                new java.util.concurrent.CountDownLatch(1);
        AtomicInteger resolved = new AtomicInteger();
        List<Node> delivered = new CopyOnWriteArrayList<>();
        AimNodePublisher publisher = new AimNodePublisher(delivered::add);
        publisher.resolver = d -> {
            if (resolved.incrementAndGet() == 1) {
                firstResolveStarted.countDown();
                try {
                    releaseFirstResolve.await(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            AbstractNode n = new AbstractNode(Children.LEAF);
            n.setValue("dir", d);
            return n;
        };

        File first = dir.resolve("f0").toFile();
        publisher.publish(first);
        assertThat(firstResolveStarted.await(5, java.util.concurrent.TimeUnit.SECONDS))
                .as("the first resolution is underway, blocking the lane").isTrue();
        File last = null;
        for (int i = 1; i < 100; i++) {
            last = dir.resolve("f" + i).toFile();
            publisher.publish(last); // queues behind the blocked resolve
        }
        releaseFirstResolve.countDown();
        File expected = last;
        await(() -> delivered.stream().anyMatch(n -> expected.equals(n.getValue("dir"))));
        settle();

        assertThat(resolved.get())
                .as("only the in-flight and the final target resolve; 98 superseded "
                        + "requests never touch the resolver")
                .isEqualTo(2);
        assertThat(delivered)
                .as("nothing but the final target is delivered — the first was "
                        + "superseded before its EDT hop")
                .hasSize(1);
    }
}
