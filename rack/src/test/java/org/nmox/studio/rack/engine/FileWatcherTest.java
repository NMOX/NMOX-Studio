package org.nmox.studio.rack.engine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class FileWatcherTest {

    @TempDir
    Path projectDir;

    private FileWatcher watcher;

    @AfterEach
    void tearDown() {
        if (watcher != null) {
            watcher.stop();
        }
    }

    @Test
    @DisplayName("Should report a newly created source file")
    void shouldReportNewFile() throws Exception {
        CountDownLatch fired = new CountDownLatch(1);
        List<Path> seen = new CopyOnWriteArrayList<>();
        watcher = new FileWatcher(projectDir.toFile(), 200, null, changed -> {
            seen.addAll(changed);
            fired.countDown();
        });
        watcher.start();
        Thread.sleep(400); // let the baseline scan settle

        Path file = projectDir.resolve("index.js");
        Files.writeString(file, "console.log('hi')");

        assertThat(fired.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(seen).contains(file);
    }

    @Test
    @DisplayName("Should ignore changes inside node_modules")
    void shouldIgnoreNodeModules() throws Exception {
        File nm = new File(projectDir.toFile(), "node_modules/leftpad");
        assertThat(nm.mkdirs()).isTrue();

        CountDownLatch fired = new CountDownLatch(1);
        watcher = new FileWatcher(projectDir.toFile(), 200, null, changed -> fired.countDown());
        watcher.start();
        Thread.sleep(400);

        Files.writeString(nm.toPath().resolve("index.js"), "module.exports = x => x");

        assertThat(fired.await(1500, TimeUnit.MILLISECONDS))
                .as("node_modules changes must not fire")
                .isFalse();
    }

    @Test
    @DisplayName("Should respect the extension filter")
    void shouldFilterByExtension() throws Exception {
        CountDownLatch fired = new CountDownLatch(1);
        List<Path> seen = new CopyOnWriteArrayList<>();
        watcher = new FileWatcher(projectDir.toFile(), 200, Set.of("css"), changed -> {
            seen.addAll(changed);
            fired.countDown();
        });
        watcher.start();
        Thread.sleep(400);

        Files.writeString(projectDir.resolve("notes.txt"), "ignored");
        Files.writeString(projectDir.resolve("style.css"), "body { margin: 0 }");

        assertThat(fired.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(seen).containsExactly(projectDir.resolve("style.css"));
    }

    @Test
    @DisplayName("Should stop cleanly and fire nothing afterwards")
    void shouldStopCleanly() throws Exception {
        CountDownLatch fired = new CountDownLatch(1);
        watcher = new FileWatcher(projectDir.toFile(), 200, null, changed -> fired.countDown());
        watcher.start();
        Thread.sleep(300);
        watcher.stop();

        Files.writeString(projectDir.resolve("late.js"), "too late");

        assertThat(watcher.isRunning()).isFalse();
        assertThat(fired.await(1, TimeUnit.SECONDS)).isFalse();
    }
}
