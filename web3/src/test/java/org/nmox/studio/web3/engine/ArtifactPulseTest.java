package org.nmox.studio.web3.engine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The artifact/workspace poller. tick() is synchronous, so these tests
 * drive it directly — no sleeps, no races; one thread smoke test proves
 * the loop actually loops.
 */
class ArtifactPulseTest {

    @TempDir
    Path project;

    /** Records every callback; asserts run against plain lists. */
    private static final class RecordingSink implements ArtifactPulse.Sink {

        int artifactEvents;
        final List<long[]> workspaceEvents = new ArrayList<>();

        @Override
        public void artifactsChanged() {
            artifactEvents++;
        }

        @Override
        public void workspaceChanged(long mtime, long size) {
            workspaceEvents.add(new long[]{mtime, size});
        }
    }

    private ArtifactPulse pulse(RecordingSink sink) {
        return new ArtifactPulse(project.toFile(),
                new File(project.toFile(), ".nmoxweb3.json"), sink);
    }

    // ---- the scan seam -------------------------------------------------------

    @Test
    @DisplayName("scan sees .json under out/ and artifacts/ only — nothing else")
    void scanScope() throws Exception {
        Files.createDirectories(project.resolve("out/Counter.sol"));
        Files.writeString(project.resolve("out/Counter.sol/Counter.json"), "{}");
        Files.createDirectories(project.resolve("artifacts/contracts/Token.sol"));
        Files.writeString(project.resolve("artifacts/contracts/Token.sol/Token.json"), "{}");
        Files.writeString(project.resolve("out/Counter.sol/notes.md"), "not json");
        Files.createDirectories(project.resolve("src"));
        Files.writeString(project.resolve("src/config.json"), "{}"); // not an artifact dir

        Map<Path, Long> snapshot = ArtifactPulse.scanArtifacts(project);
        assertThat(snapshot).containsOnlyKeys(
                project.resolve("out/Counter.sol/Counter.json"),
                project.resolve("artifacts/contracts/Token.sol/Token.json"));
    }

    @Test
    @DisplayName("missing artifact dirs scan to an empty snapshot, not an error")
    void scanMissingDirs() {
        assertThat(ArtifactPulse.scanArtifacts(project)).isEmpty();
    }

    // ---- tick-driven behavior --------------------------------------------------

    @Test
    @DisplayName("first tick primes the baseline: pre-existing artifacts fire nothing")
    void firstTickIsSilent() throws Exception {
        Files.createDirectories(project.resolve("out"));
        Files.writeString(project.resolve("out/A.json"), "{}");
        Files.writeString(project.resolve(".nmoxweb3.json"), "{}");
        RecordingSink sink = new RecordingSink();
        ArtifactPulse pulse = pulse(sink);

        pulse.tick();
        assertThat(sink.artifactEvents).isZero();
        assertThat(sink.workspaceEvents).isEmpty();
    }

    @Test
    @DisplayName("a build writing many files is one event per tick, then silence")
    void buildBurstCoalesces() throws Exception {
        RecordingSink sink = new RecordingSink();
        ArtifactPulse pulse = pulse(sink);
        pulse.tick(); // prime

        Files.createDirectories(project.resolve("out/Counter.sol"));
        for (int i = 0; i < 5; i++) {
            Files.writeString(project.resolve("out/Counter.sol/C" + i + ".json"), "{}");
        }
        pulse.tick();
        assertThat(sink.artifactEvents).isEqualTo(1);

        pulse.tick(); // nothing new
        pulse.tick();
        assertThat(sink.artifactEvents).isEqualTo(1);
    }

    @Test
    @DisplayName("a deleted artifact is a change too")
    void deletionFires() throws Exception {
        Files.createDirectories(project.resolve("out"));
        Path artifact = project.resolve("out/A.json");
        Files.writeString(artifact, "{}");
        RecordingSink sink = new RecordingSink();
        ArtifactPulse pulse = pulse(sink);
        pulse.tick(); // prime

        Files.delete(artifact);
        pulse.tick();
        assertThat(sink.artifactEvents).isEqualTo(1);
    }

    @Test
    @DisplayName("workspace file stamp changes fire once, with the new stamp")
    void workspaceStamp() throws Exception {
        Path workspace = project.resolve(".nmoxweb3.json");
        Files.writeString(workspace, "{}");
        RecordingSink sink = new RecordingSink();
        ArtifactPulse pulse = pulse(sink);
        pulse.tick(); // prime

        Files.writeString(workspace, "{\"networks\": []}");
        Files.setLastModifiedTime(workspace,
                FileTime.fromMillis(System.currentTimeMillis() + 5_000));
        pulse.tick();
        assertThat(sink.workspaceEvents).hasSize(1);
        assertThat(sink.workspaceEvents.get(0)[0])
                .isEqualTo(workspace.toFile().lastModified());
        assertThat(sink.workspaceEvents.get(0)[1])
                .isEqualTo(workspace.toFile().length());

        pulse.tick(); // stamp settled — no repeat
        assertThat(sink.workspaceEvents).hasSize(1);
        assertThat(sink.artifactEvents).isZero(); // never crossed the streams
    }

    @Test
    @DisplayName("workspace file vanishing reports -1/-1; appearing reports the stamp")
    void workspaceAppearsAndVanishes() throws Exception {
        RecordingSink sink = new RecordingSink();
        ArtifactPulse pulse = pulse(sink);
        pulse.tick(); // prime with no file

        Path workspace = project.resolve(".nmoxweb3.json");
        Files.writeString(workspace, "{}");
        pulse.tick();
        assertThat(sink.workspaceEvents).hasSize(1);

        Files.delete(workspace);
        pulse.tick();
        assertThat(sink.workspaceEvents).hasSize(2);
        assertThat(sink.workspaceEvents.get(1)).containsExactly(-1L, -1L);
    }

    // ---- the loop ------------------------------------------------------------

    @Test
    @DisplayName("started pulse notices a new artifact on its own; stop() stops it")
    void threadLoopWorks() throws Exception {
        CountDownLatch seen = new CountDownLatch(1);
        ArtifactPulse pulse = new ArtifactPulse(project.toFile(),
                new File(project.toFile(), ".nmoxweb3.json"), new ArtifactPulse.Sink() {
            @Override
            public void artifactsChanged() {
                seen.countDown();
            }

            @Override
            public void workspaceChanged(long mtime, long size) {
            }
        });
        pulse.tick(); // prime the baseline before the thread races the write below
        pulse.start(50);
        try {
            Files.createDirectories(project.resolve("out"));
            Files.writeString(project.resolve("out/Live.json"), "{}");
            assertThat(seen.await(10, TimeUnit.SECONDS))
                    .as("the pulse thread should see the new artifact")
                    .isTrue();
        } finally {
            pulse.stop();
        }
    }
}
