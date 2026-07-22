package org.nmox.studio.project;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.util.NbPreferences;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The persisted end of the recent-files trail after the v1.111.0 EDT-I/O
 * fix: {@code record} rides the trail lane (stat + pref write + flush all
 * off the caller's thread — the tracker calls it from the EDT on every tab
 * switch), {@code listRaw} is a pure pref parse with no filesystem stats,
 * and {@code pruneAsync} sweeps vanished files off-EDT, firing its callback
 * only when something actually dropped so prune → refresh → prune converges.
 * {@link RecentFilesTest} covers push's ordering discipline separately.
 */
class RecentFilesStoreTest {

    private static Preferences prefs() {
        return NbPreferences.forModule(RecentFiles.class);
    }

    @BeforeEach
    @AfterEach
    void clearTrail() {
        RecentFiles.awaitIdle(); // no stale lane task may rewrite the pref later
        prefs().remove("recentFiles");
    }

    @Test
    @DisplayName("record then listRaw round-trips real files, newest first")
    void recordThenList(@TempDir Path dir) throws Exception {
        File a = Files.writeString(dir.resolve("a.txt"), "a").toFile();
        File b = Files.writeString(dir.resolve("b.txt"), "b").toFile();

        RecentFiles.record(a);
        RecentFiles.record(b);
        RecentFiles.awaitIdle(); // the writes ride the lane, not this thread

        assertThat(RecentFiles.listRaw()).containsExactly(b, a);
    }

    @Test
    @DisplayName("record ignores null and non-existent paths")
    void recordIgnoresBadInput(@TempDir Path dir) {
        RecentFiles.record(null);
        RecentFiles.record(new File(dir.toFile(), "does-not-exist.txt"));
        RecentFiles.awaitIdle();
        assertThat(RecentFiles.listRaw()).isEmpty();
    }

    @Test
    @DisplayName("record ignores directories — only real files join the trail")
    void recordIgnoresDirectories(@TempDir Path dir) {
        RecentFiles.record(dir.toFile());
        RecentFiles.awaitIdle();
        assertThat(RecentFiles.listRaw()).isEmpty();
    }

    @Test
    @DisplayName("listRaw never stats: a vanished file still lists until the prune sweeps it")
    void listRawIsStatFree(@TempDir Path dir) throws Exception {
        File gone = Files.writeString(dir.resolve("gone.txt"), "gone").toFile();
        RecentFiles.record(gone);
        RecentFiles.awaitIdle();

        assertThat(gone.delete()).isTrue();
        // the raw view is a pure pref parse — the vanished entry is still
        // there, which is exactly why a hung mount can't freeze a refresh
        assertThat(RecentFiles.listRaw()).containsExactly(gone);
    }

    @Test
    @DisplayName("pruneAsync drops vanished files and fires the callback once, on the EDT")
    void pruneSweepsAndNotifies(@TempDir Path dir) throws Exception {
        File keep = Files.writeString(dir.resolve("keep.txt"), "keep").toFile();
        File gone = Files.writeString(dir.resolve("gone.txt"), "gone").toFile();
        RecentFiles.record(gone);
        RecentFiles.record(keep);
        RecentFiles.awaitIdle();
        assertThat(gone.delete()).isTrue();

        CountDownLatch changed = new CountDownLatch(1);
        AtomicInteger onEdt = new AtomicInteger();
        RecentFiles.pruneAsync(() -> {
            if (javax.swing.SwingUtilities.isEventDispatchThread()) {
                onEdt.incrementAndGet();
            }
            changed.countDown();
        });

        assertThat(changed.await(10, TimeUnit.SECONDS))
                .as("a sweep that dropped something notifies").isTrue();
        assertThat(onEdt.get()).as("the callback lands on the EDT").isEqualTo(1);
        assertThat(RecentFiles.listRaw()).containsExactly(keep);
    }

    @Test
    @DisplayName("A clean sweep stays silent — prune → refresh → prune converges")
    void cleanPruneDoesNotNotify(@TempDir Path dir) throws Exception {
        File keep = Files.writeString(dir.resolve("keep.txt"), "keep").toFile();
        RecentFiles.record(keep);
        RecentFiles.awaitIdle();

        AtomicInteger fired = new AtomicInteger();
        RecentFiles.pruneAsync(fired::incrementAndGet);
        RecentFiles.awaitIdle(); // sweep done; any invokeLater would be queued
        javax.swing.SwingUtilities.invokeAndWait(() -> { }); // drain the EDT

        assertThat(fired.get())
                .as("nothing vanished → no callback → no refresh storm")
                .isZero();
        assertThat(RecentFiles.listRaw()).containsExactly(keep);
    }

    @Test
    @DisplayName("listRaw on a fresh (empty) store is empty, not a one-element blank")
    void listEmptyStore() {
        assertThat(RecentFiles.listRaw()).isEmpty();
    }

    @Test
    @DisplayName("push skips blank existing entries in the stored value")
    void pushSkipsBlankEntries() {
        // a stored value with stray blank lines must not leak empties forward
        String result = RecentFiles.push("\n/a\n\n/b\n", "/c", 5);
        assertThat(result).isEqualTo("/c\n/a\n/b");
    }

    @Test
    @DisplayName("push stops appending once the cap is reached mid-scan")
    void pushHonoursCapMidScan() {
        String result = RecentFiles.push("/x\n/y\n/z", "/new", 2);
        assertThat(result).isEqualTo("/new\n/x");
    }

    @Test
    @DisplayName("push with cap of 1 keeps only the new head")
    void pushCapOne() {
        assertThat(RecentFiles.push("/old", "/new", 1)).isEqualTo("/new");
    }
}
