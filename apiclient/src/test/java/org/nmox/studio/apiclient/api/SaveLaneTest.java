package org.nmox.studio.apiclient.api;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.AtomicFiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The debt-#16 contract: the studio's Swing debounce timer stays the
 * clock, but the write body leaves the EDT — WITHOUT breaking the three
 * things the EDT's synchrony used to guarantee for free. Each test pins
 * one of them: writes never run on the EDT, writes never interleave
 * (storm), a verdict can never land between a write and its self-write
 * stamp, and a close-time flush completes the queued edit before the
 * studio is torn down.
 */
class SaveLaneTest {

    @TempDir
    File dir;

    @Test
    @DisplayName("the write body runs off the EDT — the EDT only snapshots and queues")
    void writeBodyRunsOffTheEdt() throws Exception {
        SaveLane lane = new SaveLane("test saves");
        AtomicBoolean sawEdt = new AtomicBoolean(true);
        CountDownLatch wrote = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> lane.save(() -> {
            sawEdt.set(SwingUtilities.isEventDispatchThread());
            wrote.countDown();
        }));
        assertThat(wrote.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(sawEdt).as("the write body must not run on the EDT").isFalse();
    }

    @Test
    @DisplayName("a storm of saves serializes: no two writes interleave, the last one wins")
    void stormSerializesAndLastWriteWins() throws Exception {
        SaveLane lane = new SaveLane("test saves");
        File file = new File(dir, "workspace.json");
        AtomicInteger inWrite = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        AtomicInteger edtWrites = new AtomicInteger();
        int storm = 200;
        SwingUtilities.invokeAndWait(() -> {
            for (int i = 0; i < storm; i++) {
                String payload = "{\"edit\":" + i + "}"; // the EDT-time snapshot
                lane.save(() -> {
                    maxConcurrent.accumulateAndGet(inWrite.incrementAndGet(), Math::max);
                    if (SwingUtilities.isEventDispatchThread()) {
                        edtWrites.incrementAndGet();
                    }
                    try {
                        AtomicFiles.writeString(file.toPath(), payload);
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    } finally {
                        inWrite.decrementAndGet();
                    }
                });
            }
        });
        assertThat(lane.flush(10, TimeUnit.SECONDS)).isTrue();
        assertThat(maxConcurrent.get()).as("two writes must never interleave").isEqualTo(1);
        assertThat(edtWrites.get()).as("no write may run on the EDT").isZero();
        assertThat(Files.readString(file.toPath()))
                .as("the newest snapshot wins — never an older queued one")
                .isEqualTo("{\"edit\":" + (storm - 1) + "}");
    }

    @Test
    @DisplayName("close flush: the queued edit is on disk when flush returns")
    void closeFlushCompletesTheQueuedWrite() throws Exception {
        SaveLane lane = new SaveLane("test saves");
        File file = new File(dir, "workspace.json");
        lane.save(() -> {
            try {
                Thread.sleep(100); // a slow disk mid-teardown
                AtomicFiles.writeString(file.toPath(), "{\"last\":\"edit\"}");
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(lane.flush(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Files.readString(file.toPath()))
                .as("componentClosed must not outrun the last save")
                .isEqualTo("{\"last\":\"edit\"}");
    }

    @Test
    @DisplayName("a verdict queued mid-write always sees the write's stamp — never a foreign misread")
    void classificationQueuesBehindTheWriteAndItsStamp() throws Exception {
        SaveLane lane = new SaveLane("test saves");
        AtomicBoolean stampRecorded = new AtomicBoolean();
        AtomicBoolean verdictSawStamp = new AtomicBoolean();
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch verdictRan = new CountDownLatch(1);
        lane.save(() -> {
            writeStarted.countDown();
            try {
                // the file bytes have landed but the stamp hasn't yet — the
                // exact window in which an EDT-only re-check would misread
                // our own save as a foreign edit
                Thread.sleep(150);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            stampRecorded.set(true); // write + stamp are ONE lane task
        });
        assertThat(writeStarted.await(5, TimeUnit.SECONDS)).isTrue();
        // the pulse tick that raced the landing write files its verdict NOW
        lane.classify(() -> {
            verdictSawStamp.set(stampRecorded.get());
            verdictRan.countDown();
        });
        assertThat(verdictRan.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(verdictSawStamp)
                .as("the verdict must queue behind the write+stamp pair")
                .isTrue();
    }
}
