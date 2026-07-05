package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The startup-safety contract for the project file tree: listing a
 * directory must happen on a background thread, so {@code setRootDirectory}
 * returns to its caller (the EDT, during window restore) promptly even when
 * the directory is slow or permission-gated to enumerate.
 *
 * <p>{@code FileTreePanel} is pure-Swing and JaCoCo-excluded; this test
 * proves the load-bearing behavior — that the walk is off the EDT — by
 * injecting a deliberately slow directory-listing seam.
 */
class FileTreePanelEdtTest {

    @TempDir
    File dir;

    @Test
    @DisplayName("setRootDirectory returns before a slow directory listing finishes")
    void setRootDirectoryDoesNotBlockOnListing() throws Exception {
        assertThat(java.awt.GraphicsEnvironment.isHeadless())
                .as("test runs headless; panel is constructed but never shown").isTrue();

        CountDownLatch listingStarted = new CountDownLatch(1);
        CountDownLatch releaseListing = new CountDownLatch(1);
        AtomicBoolean listed = new AtomicBoolean(false);

        FileTreePanel.DirLister slow = d -> {
            listingStarted.countDown();
            try {
                // block the listing until the test releases it — if this ran on
                // the calling thread, setRootDirectory would hang here
                releaseListing.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            listed.set(true);
            return d.listFiles();
        };

        FileTreePanel panel = new FileTreePanel(slow);

        long start = System.nanoTime();
        panel.setRootDirectory(dir);   // must NOT block on the slow lister
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // setRootDirectory returned promptly...
        assertThat(elapsedMs).as("setRootDirectory must not block on listing").isLessThan(2_000);
        // ...even though the listing had not completed yet
        assertThat(listed.get()).as("listing runs in the background, not inline").isFalse();

        // the listing did get scheduled on a background thread
        assertThat(listingStarted.await(3, TimeUnit.SECONDS))
                .as("the walk was posted to a background thread").isTrue();

        releaseListing.countDown();
        panel.dispose();
    }

    @Test
    @DisplayName("a non-directory root shows 'No project' without any listing")
    void nonDirectoryRootNeverLists() throws Exception {
        AtomicBoolean listed = new AtomicBoolean(false);
        FileTreePanel.DirLister tripwire = d -> {
            listed.set(true);
            return d.listFiles();
        };
        FileTreePanel panel = new FileTreePanel(tripwire);
        File missing = new File(dir, "does-not-exist");

        panel.setRootDirectory(missing);
        Thread.sleep(200); // give any (wrongly) posted scan a chance to run

        assertThat(listed.get()).as("a missing root never triggers a directory walk").isFalse();
        assertThat(panel.getRootDirectory()).isEqualTo(missing);
        panel.dispose();
    }
}
