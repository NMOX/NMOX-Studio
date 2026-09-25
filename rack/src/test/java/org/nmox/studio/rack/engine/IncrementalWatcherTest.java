package org.nmox.studio.rack.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The watcher is incremental (after 3.2.0): a poll stats the directories it
 * knows, relists the ones whose time moved and, when asked, stats the
 * tracked files. Driven through {@code baseline()} and {@code poll()} on
 * this thread, so nothing here waits on a clock.
 */
class IncrementalWatcherTest {

    @TempDir
    Path root;

    private FileWatcher watcher() {
        FileWatcher w = new FileWatcher(root.toFile(), 1000, null, changed -> { });
        w.baseline();
        return w;
    }

    /** A directory's time is millisecond-grained here; step past the baseline's. */
    private static void tick() throws InterruptedException {
        Thread.sleep(15);
    }

    private static List<String> names(List<Path> changed) {
        return changed.stream().map(p -> p.getFileName().toString()).sorted().toList();
    }

    @Test
    @DisplayName("a new directory's files are added on a structure poll")
    void newNestedDirectory() throws Exception {
        Files.createDirectories(root.resolve("src"));
        FileWatcher w = watcher();
        tick();
        Files.createDirectories(root.resolve("src/feature/deep"));
        Files.writeString(root.resolve("src/feature/a.js"), "a");
        Files.writeString(root.resolve("src/feature/deep/b.js"), "b");
        assertThat(names(w.poll(false))).containsExactly("a.js", "b.js");
        assertThat(w.poll(false)).as("and only once").isEmpty();
    }

    @Test
    @DisplayName("a deleted directory reports every file it held, once")
    void deletedDirectory() throws Exception {
        Files.createDirectories(root.resolve("gone/inner"));
        Files.writeString(root.resolve("gone/x.js"), "x");
        Files.writeString(root.resolve("gone/inner/y.js"), "y");
        Files.writeString(root.resolve("stays.js"), "s");
        FileWatcher w = watcher();
        tick();
        Files.delete(root.resolve("gone/inner/y.js"));
        Files.delete(root.resolve("gone/inner"));
        Files.delete(root.resolve("gone/x.js"));
        Files.delete(root.resolve("gone"));
        assertThat(names(w.poll(true))).containsExactly("x.js", "y.js");
        assertThat(w.poll(true)).isEmpty();
    }

    @Test
    @DisplayName("a renamed directory is its files removed under the old name and added under the new")
    void renamedDirectory() throws Exception {
        Files.createDirectories(root.resolve("old"));
        Files.writeString(root.resolve("old/m.js"), "m");
        FileWatcher w = watcher();
        tick();
        Files.move(root.resolve("old"), root.resolve("new"));
        List<Path> changed = w.poll(false);
        assertThat(changed).containsExactlyInAnyOrder(root.resolve("old/m.js"), root.resolve("new/m.js"));
    }

    @Test
    @DisplayName("an in-place edit moves no directory: it is seen on a content poll, not a structure one")
    void inPlaceEditNeedsContentPoll() throws Exception {
        Path f = Files.writeString(root.resolve("app.js"), "1");
        // a settled directory: one written in the last two seconds is listed
        // again anyway (the racily-clean rule), which would see the edit
        FileTime dirTime = FileTime.fromMillis(System.currentTimeMillis() - 60_000);
        Files.setLastModifiedTime(root, dirTime);
        FileWatcher w = watcher();
        Files.writeString(f, "2", java.nio.file.StandardOpenOption.APPEND);
        Files.setLastModifiedTime(f, FileTime.fromMillis(Files.getLastModifiedTime(f).toMillis() + 5_000));
        Files.setLastModifiedTime(root, dirTime);   // an append touches no directory
        assertThat(w.poll(false)).as("a structure poll cannot see it").isEmpty();
        assertThat(w.poll(true)).containsExactly(f);
        assertThat(w.poll(true)).isEmpty();
    }

    @Test
    @DisplayName("an atomic save (temp file renamed over) is seen on a structure poll")
    void atomicReplaceIsStructure() throws Exception {
        Path f = Files.writeString(root.resolve("app.js"), "1");
        FileWatcher w = watcher();
        tick();
        Path tmp = Files.writeString(root.resolve("app.js.tmp~"), "2");
        Files.setLastModifiedTime(tmp, FileTime.fromMillis(Files.getLastModifiedTime(f).toMillis() + 5_000));
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        assertThat(w.poll(false)).contains(f);
    }

    @Test
    @DisplayName("heavy and hidden directories stay invisible when they appear later")
    void skippedDirectoriesStayInvisible() throws Exception {
        FileWatcher w = watcher();
        tick();
        Files.createDirectories(root.resolve("node_modules/dep"));
        Files.writeString(root.resolve("node_modules/dep/i.js"), "i");
        Files.createDirectories(root.resolve(".cache"));
        Files.writeString(root.resolve(".cache/c.js"), "c");
        Files.writeString(root.resolve("real.js"), "r");
        assertThat(names(w.poll(true))).containsExactly("real.js");
    }

    @Test
    @DisplayName("a quiet tree costs a structure poll nothing to report")
    void quietTree() throws Exception {
        for (int d = 0; d < 20; d++) {
            Files.createDirectories(root.resolve("p" + d));
            for (int f = 0; f < 20; f++) {
                Files.writeString(root.resolve("p" + d + "/f" + f + ".js"), "x");
            }
        }
        FileWatcher w = watcher();
        tick();
        assertThat(w.poll(false)).isEmpty();
        assertThat(w.poll(true)).isEmpty();
    }

    private static boolean posix() {
        return !System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    @Test
    @DisplayName("a file written in the same tick as a relist is found: a fresh directory is listed again")
    void raciliyCleanDirectoryIsListedAgain() throws Exception {
        FileWatcher w = watcher();
        tick();
        Path x = Files.createDirectories(root.resolve("x"));
        Files.writeString(x.resolve("a.js"), "a");
        assertThat(names(w.poll(false))).containsExactly("a.js");
        FileTime listed = Files.getLastModifiedTime(x);
        Files.writeString(x.resolve("b.js"), "b");
        Files.setLastModifiedTime(x, listed);   // the write landed in the tick the relist saw
        assertThat(names(w.poll(false))).as("the directory was too fresh to trust").containsExactly("b.js");
    }

    @Test
    @DisplayName("what the incremental poll lost, the reconcile finds: a listing that failed, a root that came back")
    void reconcileHeals() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(posix(), "chmod and moving a watched root are POSIX walks");
        Path proj = Files.createDirectories(root.resolve("proj/src"));
        Files.writeString(proj.resolve("k.js"), "k");
        FileWatcher w = new FileWatcher(root.resolve("proj").toFile(), 1000, null, changed -> { });
        w.baseline();
        tick();
        Files.writeString(proj.resolve("l.js"), "l");
        java.nio.file.attribute.PosixFileAttributeView view =
                Files.getFileAttributeView(proj, java.nio.file.attribute.PosixFileAttributeView.class);
        var perms = Files.getPosixFilePermissions(proj);
        view.setPermissions(java.util.Set.of());
        try {
            w.poll(false);   // the listing fails: src is dropped
        } finally {
            view.setPermissions(perms);
        }
        tick();
        Files.writeString(proj.resolve("m.js"), "m");
        w.expireReconcile();
        assertThat(names(w.poll(false))).as("the reconcile brings src back").contains("l.js", "m.js");

        Files.move(root.resolve("proj"), root.resolve("away"));
        assertThat(w.poll(false)).isNotEmpty();
        Files.move(root.resolve("away"), root.resolve("proj"));
        w.expireReconcile();
        assertThat(names(w.poll(false))).as("a root that came back is watched again").contains("k.js");
    }

    @Test
    @DisplayName("a directory swapped for a link is dropped, and the link's target is not walked")
    void linkSwappedInIsNotFollowed() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(posix(), "symbolic links need privileges on Windows");
        Path outside = Files.createDirectories(root.resolve("outside"));
        Files.writeString(outside.resolve("secret.js"), "s");
        Path proj = Files.createDirectories(root.resolve("proj/shared"));
        Files.writeString(proj.resolve("own.js"), "o");
        // the parent settled, and its time put back after the swap: only the
        // poll's own look at "shared" can see that it stopped being a directory
        Path parent = root.resolve("proj");
        FileTime settled = FileTime.fromMillis(System.currentTimeMillis() - 60_000);
        Files.setLastModifiedTime(parent, settled);
        FileWatcher w = new FileWatcher(parent.toFile(), 1000, null, changed -> { });
        w.baseline();
        tick();
        Files.delete(proj.resolve("own.js"));
        Files.delete(proj);
        Files.createSymbolicLink(proj, outside);
        Files.setLastModifiedTime(parent, settled);
        List<Path> changed = w.poll(true);
        assertThat(names(changed)).contains("own.js").doesNotContain("secret.js");
        assertThat(w.poll(true)).as("the target stays unwalked").extracting(p -> p.getFileName().toString())
                .doesNotContain("secret.js");
    }

    @Test
    @DisplayName("a file and a directory that swap places are sorted out, once")
    void fileAndDirectorySwap() throws Exception {
        Path x = Files.writeString(root.resolve("x.js"), "file");
        FileWatcher w = watcher();
        tick();
        Files.delete(x);
        Files.createDirectories(x);
        Files.writeString(x.resolve("y.js"), "y");
        // structure polls: the relist itself must sort the swap out
        assertThat(names(w.poll(false))).containsExactlyInAnyOrder("x.js", "y.js");
        assertThat(w.poll(false)).isEmpty();
    }

    @Test
    @DisplayName("past the cap, a watcher of every file still reports that a directory changed")
    void capStillReportsTheDirectory() throws Exception {
        Files.writeString(root.resolve("a.js"), "a");
        Files.writeString(root.resolve("b.js"), "b");
        FileWatcher w = new FileWatcher(root.toFile(), 1000, null, changed -> { });
        w.maxFiles = 2;
        w.baseline();
        tick();
        Files.writeString(root.resolve("c.js"), "c");
        assertThat(w.poll(false)).as("c.js cannot be tracked; the tree must still refresh").contains(root);
    }
}
