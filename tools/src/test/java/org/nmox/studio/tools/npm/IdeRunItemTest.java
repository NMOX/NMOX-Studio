package org.nmox.studio.tools.npm;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.CommandExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class IdeRunItemTest {

    @Test
    @DisplayName("The platform item reports the run: running until finished, stop kills the handle, Repeat reruns")
    void item() {
        AtomicInteger reruns = new AtomicInteger();
        AtomicReference<CommandExecutor.Handle> handle = new AtomicReference<>();
        IdeRunItem item = new IdeRunItem("run", null, "Run — demo", handle::get, reruns::incrementAndGet);
        assertThat(item.getAction()).isEqualTo("run");
        assertThat(item.getDisplayName()).isEqualTo("Run — demo");
        assertThat(item.isRunning()).as("registered at spawn: running").isTrue();
        item.stopRunning();
        assertThat(handle.get()).as("no handle yet: stop is a no-op, never a NullPointerException").isNull();
        item.repeatExecution();
        assertThat(reruns.get()).isEqualTo(1);
        item.finished();
        assertThat(item.isRunning()).as("the exit handler's word").isFalse();
    }

    @Test
    @DisplayName("stopRunning kills through the handle the spawn produced")
    void stopKills() {
        CommandExecutor.Handle h = CommandExecutor.run("IdeRunItemTest", new java.io.File("."),
                java.util.Map.of(), java.util.List.of("sh", "-c", "sleep 30"), l -> { }, e -> { });
        IdeRunItem item = new IdeRunItem("run", null, "Run — sleeper", () -> h, () -> { });
        item.stopRunning();
        long deadline = System.currentTimeMillis() + 5_000;
        while (h.isAlive() && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(h.isAlive()).as("the sleeper is dead within the grace").isFalse();
    }
}
