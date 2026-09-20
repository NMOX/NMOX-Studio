package org.nmox.studio.infra.model;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.BoundedReads;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A design we could not READ is not a design we may overwrite.
 *
 * <p>The corrupt-file law has held since v1.36.0: a {@code .nmoxinfra.json}
 * that fails to PARSE is copied to {@code .bak} before the empty fallback,
 * so the next autosave can never destroy the only copy. The READ failure
 * had none of that protection — {@link GraphIO#loadGuarded} simply threw,
 * {@code InfraDesignerTopComponent.load} caught {@code Exception} and
 * cleared the graph, and its {@code finally} stamped the file as OURS on
 * the catch path too. The next debounced save wrote an empty design over
 * every node, every wire and every {@code doId} naming a live billed cloud
 * resource — with the never-clobber guard disarmed.
 *
 * <p>{@link BoundedReads.TooLarge} is an {@link java.io.IOException}, so an
 * over-cap file took that path; so did a permissions error.
 */
class UnreadableDesignTest {

    /** Writes a file the 8 MiB cap refuses to read. */
    static void writeOverCap(File f) throws Exception {
        byte[] chunk = new byte[1024 * 1024];
        java.util.Arrays.fill(chunk, (byte) 'x');
        try (java.io.OutputStream out = Files.newOutputStream(f.toPath())) {
            for (int i = 0; i < 9; i++) {
                out.write(chunk);
            }
        }
        assertThat(f.length()).isGreaterThan(BoundedReads.DEFAULT_MAX_BYTES);
    }

    @Test
    @DisplayName("an over-cap design is refused as unreadable, not loaded as empty")
    void overCapIsUnreadable(@TempDir File dir) throws Exception {
        File f = new File(dir, GraphIO.DEFAULT_FILENAME);
        writeOverCap(f);
        InfraGraph graph = new InfraGraph();
        GraphIO.LoadOutcome outcome = GraphIO.loadGuarded(graph, f);
        assertThat(outcome.unreadable())
                .as("the bytes are unknown — the designer must not own this file")
                .isTrue();
        assertThat(outcome.backup())
                .as("nothing will be written over it, so nothing needs copying aside")
                .isNull();
        assertThat(new File(dir, GraphIO.DEFAULT_FILENAME + ".bak")).doesNotExist();
        assertThat(graph.getNodes()).isEmpty();
    }

    @Test
    @DisplayName("an unreadable design refuses rather than throwing")
    void unreadableNeverThrows(@TempDir File dir) throws Exception {
        File f = new File(dir, GraphIO.DEFAULT_FILENAME);
        writeOverCap(f);
        // the throw was the mechanism of the loss: the designer's catch-all
        // could not tell "could not read" from "nothing worth keeping"
        assertThat(GraphIO.loadGuarded(new InfraGraph(), f)).isNotNull();
    }

    @Test
    @DisplayName("a well-formed design is never reported unreadable")
    void readableIsNotUnreadable(@TempDir File dir) throws Exception {
        File f = new File(dir, GraphIO.DEFAULT_FILENAME);
        InfraGraph written = new InfraGraph();
        written.addNode(NodeKind.DROPLET, 10, 20);
        GraphIO.save(written, f);

        InfraGraph graph = new InfraGraph();
        GraphIO.LoadOutcome outcome = GraphIO.loadGuarded(graph, f);
        assertThat(outcome.unreadable()).isFalse();
        assertThat(outcome.backup()).isNull();
        assertThat(graph.getNodes()).hasSize(1);
    }

    @Test
    @DisplayName("a corrupt design is readable-but-malformed: .bak, empty, and we own it")
    void corruptIsNotUnreadable(@TempDir File dir) throws Exception {
        File f = new File(dir, GraphIO.DEFAULT_FILENAME);
        Files.writeString(f.toPath(), "{ not a design");
        GraphIO.LoadOutcome outcome = GraphIO.loadGuarded(new InfraGraph(), f);
        assertThat(outcome.unreadable())
                .as("we READ these bytes and kept them as .bak — the empty graph"
                        + " may replace them, which is the v1.36.0 law")
                .isFalse();
        assertThat(outcome.backup()).isNotNull().exists();
    }
}
