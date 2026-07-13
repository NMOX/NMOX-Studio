package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The startup-safety contract for the project file tree: resolving the
 * root directory must happen on a background thread, so
 * {@code setRootDirectory} returns to its caller (the EDT, during window
 * restore) promptly even when the filesystem is slow or permission-gated
 * (the v1.33.1 TCC storm). Child listing is the platform's own lazy
 * machinery since the ledger-36 rewrite; the root resolve is the one
 * filesystem touch this panel still owns, so it is the one under test.
 *
 * <p>{@code FileTreePanel} is pure-Swing and JaCoCo-excluded; this test
 * proves the load-bearing law through the injected {@code RootResolver}
 * seam.
 */
class FileTreePanelEdtTest {

    @TempDir
    File dir;

    private static Node stub(String name) {
        AbstractNode n = new AbstractNode(Children.LEAF);
        n.setDisplayName(name);
        return n;
    }

    @Test
    @DisplayName("setRootDirectory returns before a slow root resolve finishes")
    void setRootDirectoryDoesNotBlockOnResolve() throws Exception {
        assertThat(java.awt.GraphicsEnvironment.isHeadless())
                .as("test runs headless; panel is constructed but never shown").isTrue();

        CountDownLatch resolveStarted = new CountDownLatch(1);
        CountDownLatch releaseResolve = new CountDownLatch(1);
        AtomicBoolean resolved = new AtomicBoolean(false);

        FileTreePanel.RootResolver slow = d -> {
            resolveStarted.countDown();
            try {
                // block the resolve until the test releases it — if this ran
                // on the calling thread, setRootDirectory would hang here
                releaseResolve.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            resolved.set(true);
            return stub(d.getName());
        };

        FileTreePanel panel = new FileTreePanel(slow);

        long start = System.nanoTime();
        panel.setRootDirectory(dir);   // must NOT block on the slow resolver
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // setRootDirectory returned promptly...
        assertThat(elapsedMs).as("setRootDirectory must not block on resolve").isLessThan(2_000);
        // ...even though the resolve had not completed yet
        assertThat(resolved.get()).as("resolve runs in the background, not inline").isFalse();

        // the resolve did get scheduled on a background thread
        assertThat(resolveStarted.await(3, TimeUnit.SECONDS))
                .as("the resolve was posted to a background thread").isTrue();

        releaseResolve.countDown();
        panel.dispose();
    }

    @Test
    @DisplayName("a re-aim during a slow resolve is not clobbered by the stale result")
    void staleResolveLosesToNewerAim() throws Exception {
        File first = new File(dir, "first");
        File second = new File(dir, "second");
        assertThat(first.mkdirs()).isTrue();
        assertThat(second.mkdirs()).isTrue();

        CountDownLatch firstResolveRunning = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        FileTreePanel.RootResolver gated = d -> {
            if (d.equals(first)) {
                firstResolveRunning.countDown();
                try {
                    releaseFirst.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return stub(d.getName());
        };

        FileTreePanel panel = new FileTreePanel(gated);
        panel.setRootDirectory(first);
        assertThat(firstResolveRunning.await(3, TimeUnit.SECONDS)).isTrue();

        // the user re-aims while the first resolve is still stuck
        panel.setRootDirectory(second);
        releaseFirst.countDown();

        // both resolves ride the single-lane scanner; wait then drain the EDT
        drain(2_000);

        assertThat(panel.getExplorerManager().getRootContext().getDisplayName())
                .as("the newer aim owns the tree; the stale resolve must not clobber it")
                .isEqualTo("second");
        panel.dispose();
    }

    @Test
    @DisplayName("after dispose (tab close) a reopen still resolves the root — the scanner survives")
    void reopenAfterDisposeStillResolves() throws Exception {
        FileTreePanel panel = new FileTreePanel(d -> stub(d.getName()));
        panel.setRootDirectory(dir);
        drain(500);
        assertThat(panel.getExplorerManager().getRootContext().getDisplayName())
                .isEqualTo(dir.getName());

        // simulate the user closing the Project Studio tab...
        panel.dispose();
        // ...then reopening it: the SAME panel instance (PERSISTENCE_ALWAYS)
        // gets a fresh setRootDirectory. A stopped scanner would drop this
        // post and leave the tree stuck at "No project".
        File reopened = new File(dir, "sub");
        assertThat(reopened.mkdirs()).isTrue();
        panel.setRootDirectory(reopened);
        drain(1_000);
        assertThat(panel.getExplorerManager().getRootContext().getDisplayName())
                .as("reopen resolves through the surviving scanner")
                .isEqualTo("sub");
        panel.dispose();
    }

    @Test
    @DisplayName("a null root shows 'No project' without ever touching the resolver")
    void nullRootNeverResolves() throws Exception {
        AtomicBoolean touched = new AtomicBoolean(false);
        FileTreePanel.RootResolver tripwire = d -> {
            touched.set(true);
            return stub(d.getName());
        };
        FileTreePanel panel = new FileTreePanel(tripwire);
        panel.setRootDirectory(null);
        drain(500);
        assertThat(panel.getExplorerManager().getRootContext().getDisplayName())
                .isEqualTo("No project");
        assertThat(touched.get()).as("no resolve for a null root").isFalse();
        panel.dispose();
    }

    private static void drain(long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            try {
                javax.swing.SwingUtilities.invokeAndWait(() -> { });
            } catch (Exception ignored) {
                // interrupted — not relevant to the assertions
            }
            Thread.sleep(50);
        }
    }
}
