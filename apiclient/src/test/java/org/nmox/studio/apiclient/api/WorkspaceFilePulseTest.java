package org.nmox.studio.apiclient.api;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The single-file stat poller behind the .nmoxapi.json reload. tick()
 * is synchronous, so these tests drive it directly; one thread smoke
 * test proves the loop loops.
 */
class WorkspaceFilePulseTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("first tick primes; a stamp change fires once with the new stamp")
    void primeThenFireOnce() throws Exception {
        Path file = dir.resolve(".nmoxapi.json");
        Files.writeString(file, "{}");
        List<long[]> events = new ArrayList<>();
        WorkspaceFilePulse pulse = new WorkspaceFilePulse(file.toFile(),
                (m, s) -> events.add(new long[]{m, s}));

        pulse.tick(); // prime
        assertThat(events).isEmpty();

        Files.writeString(file, "{\"collections\": []}");
        Files.setLastModifiedTime(file,
                FileTime.fromMillis(System.currentTimeMillis() + 5_000));
        pulse.tick();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)[0]).isEqualTo(file.toFile().lastModified());
        assertThat(events.get(0)[1]).isEqualTo(file.toFile().length());

        pulse.tick(); // settled — silence
        pulse.tick();
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("the file vanishing reports -1/-1; reappearing reports the stamp")
    void vanishAndReappear() throws Exception {
        Path file = dir.resolve(".nmoxapi.json");
        Files.writeString(file, "{}");
        List<long[]> events = new ArrayList<>();
        WorkspaceFilePulse pulse = new WorkspaceFilePulse(file.toFile(),
                (m, s) -> events.add(new long[]{m, s}));
        pulse.tick(); // prime

        Files.delete(file);
        pulse.tick();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).containsExactly(-1L, -1L);

        Files.writeString(file, "{}");
        pulse.tick();
        assertThat(events).hasSize(2);
        assertThat(events.get(1)[1]).isEqualTo(2L); // "{}"
    }

    @Test
    @DisplayName("a missing file that stays missing never fires")
    void missingStaysQuiet() {
        List<long[]> events = new ArrayList<>();
        WorkspaceFilePulse pulse = new WorkspaceFilePulse(
                new File(dir.toFile(), "never.json"),
                (m, s) -> events.add(new long[]{m, s}));
        pulse.tick();
        pulse.tick();
        pulse.tick();
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("started pulse notices a change on its own; stop() stops it")
    void threadLoopWorks() throws Exception {
        Path file = dir.resolve(".nmoxapi.json");
        Files.writeString(file, "{}");
        CountDownLatch seen = new CountDownLatch(1);
        WorkspaceFilePulse pulse = new WorkspaceFilePulse(file.toFile(),
                (m, s) -> seen.countDown());
        pulse.tick(); // prime before the thread races the write below
        pulse.start(50);
        try {
            Files.writeString(file, "{\"environments\": []}");
            Files.setLastModifiedTime(file,
                    FileTime.fromMillis(System.currentTimeMillis() + 5_000));
            assertThat(seen.await(10, TimeUnit.SECONDS))
                    .as("the pulse thread should see the edit")
                    .isTrue();
        } finally {
            pulse.stop();
        }
    }
}
