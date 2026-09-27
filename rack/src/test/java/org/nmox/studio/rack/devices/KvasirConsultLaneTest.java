package org.nmox.studio.rack.devices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.KvasirClient;
import org.nmox.studio.rack.engine.KvasirClient.FailureContext;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 3.4, "when something goes wrong": a model API that stalled mid-response
 * held KVASIR's consult on the SHARED single-threaded device background
 * lane, so DYNAMO, NPM-9000, ROSETTA and four more devices' background work
 * queued behind it. KVASIR has a lane of its own now: a consult that never
 * answers leaves the shared lane free.
 */
class KvasirConsultLaneTest {

    @Test
    @DisplayName("A consult that never answers does not hold the shared device lane")
    void aStalledConsultLeavesTheSharedLaneFree() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        KvasirDevice device = new KvasirDevice();
        device.client = new KvasirClient((url, body, key) -> {
            entered.countDown();
            try {
                release.await(60, TimeUnit.SECONDS); // the API that stopped answering
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            throw new IOException("released");
        });
        device.failureSource = () -> Optional.of(
                new FailureContext("VERITAS", "npm test", 1, List.of("FAIL"), "app", 100));
        device.keySource = () -> "sk-test".toCharArray();
        device.consentCheck = () -> true;
        device.autoRetryDelayMs = 0;
        try {
            device.receive(device.getPort("explain"), Signal.trigger(false));
            assertThat(entered.await(10, TimeUnit.SECONDS))
                    .as("the cable path reached the (stalled) model call").isTrue();

            long start = System.nanoTime();
            RackDevice.awaitDeviceBgIdle(); // returns at once when the lane is free, else waits 10 s
            long waitedMs = (System.nanoTime() - start) / 1_000_000L;
            assertThat(waitedMs)
                    .as("the shared device lane must not be queued behind a stalled consult")
                    .isLessThan(5_000);
        } finally {
            release.countDown();
        }
    }

    @Test
    @DisplayName("Gate: KVASIR never posts to the shared device lane")
    void kvasirNeverUsesTheSharedLane() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/devices/KvasirDevice.java"))
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("//[^\n]*", "");
        assertThat(src).doesNotContain("offEdt(");
    }
}
