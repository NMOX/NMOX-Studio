package org.nmox.studio.rack.engine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real repos are big. The watcher must stay cheap on a tree with
 * thousands of files - and a node_modules an order of magnitude
 * bigger - while still noticing the one file that changed.
 */
class BigTreeWatcherTest {

    @TempDir
    Path root;

    private FileWatcher watcher;

    @AfterEach
    void tearDown() {
        if (watcher != null) {
            watcher.stop();
        }
    }

    @Test
    @DisplayName("5k-file tree: change detection stays prompt, node_modules stays invisible")
    void bigTreeStaysFast() throws Exception {
        // 2,000 source files across 100 dirs
        for (int d = 0; d < 100; d++) {
            Path dir = root.resolve("src/pkg" + d);
            Files.createDirectories(dir);
            for (int f = 0; f < 20; f++) {
                Files.writeString(dir.resolve("file" + f + ".js"), "export const x = " + f + ";");
            }
        }
        // 3,000 dependency files the watcher must not even glance at
        for (int d = 0; d < 100; d++) {
            Path dir = root.resolve("node_modules/dep" + d + "/lib");
            Files.createDirectories(dir);
            for (int f = 0; f < 30; f++) {
                Files.writeString(dir.resolve("m" + f + ".js"), "module.exports = " + f + ";");
            }
        }

        CountDownLatch fired = new CountDownLatch(1);
        long startNanos = System.nanoTime();
        watcher = new FileWatcher(root.toFile(), 300, null, changed -> fired.countDown());
        watcher.start();
        Thread.sleep(900); // baseline scan + one idle poll

        long baselineMs = (System.nanoTime() - startNanos) / 1_000_000;
        assertThat(baselineMs).as("watcher startup on a 5k tree must not block").isLessThan(10_000);

        // a single real change must surface within a few polls
        Files.writeString(root.resolve("src/pkg42/file7.js"), "export const x = 'changed';");
        assertThat(fired.await(6, TimeUnit.SECONDS))
                .as("one changed file in a big tree must be noticed").isTrue();
    }

    @Test
    @DisplayName("Storms inside node_modules never wake the rack")
    void nodeModulesStormIsSilent() throws Exception {
        File nm = new File(root.toFile(), "node_modules/busy");
        assertThat(nm.mkdirs()).isTrue();
        Files.writeString(root.resolve("package.json"), "{}");

        CountDownLatch fired = new CountDownLatch(1);
        watcher = new FileWatcher(root.toFile(), 200, null, changed -> fired.countDown());
        watcher.start();
        Thread.sleep(500);

        // an install-like write storm
        for (int i = 0; i < 200; i++) {
            Files.writeString(nm.toPath().resolve("pkg" + i + ".js"), "x");
        }

        assertThat(fired.await(2, TimeUnit.SECONDS))
                .as("node_modules churn must not fire the watcher").isFalse();
    }
}
