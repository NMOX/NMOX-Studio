package org.nmox.studio.ui.browser.devtools;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The page→Java bridge, tested with a direct executor (production
 * injects the EDT; the seam exists exactly so these laws are provable
 * headless): every upcall marshals through the executor, hostile
 * strings are capped BEFORE crossing, levels normalize, network JSON
 * routes to the network ring, page errors land as error rows.
 */
class JsBridgeTest {

    private ConsoleModel console;
    private NetworkModel network;
    private JsBridge bridge;
    private List<Runnable> queued;

    @BeforeEach
    void setUp() {
        console = new ConsoleModel();
        network = new NetworkModel();
        queued = new ArrayList<>();
        // an executor we control: proves marshaling happens (nothing
        // reaches a model until the executor runs the task)
        bridge = new JsBridge(queued::add, console, network);
    }

    private void drain() {
        List<Runnable> batch = new ArrayList<>(queued);
        queued.clear();
        batch.forEach(Runnable::run);
    }

    @Test
    @DisplayName("upcalls touch no model until the executor runs them")
    void marshalsThroughExecutor() {
        bridge.log("info", "hello");
        bridge.err("boom");
        bridge.net("{\"m\":\"GET\",\"u\":\"/x\",\"s\":200,\"ok\":true,\"d\":1,\"z\":-1}");
        assertThat(console.entries()).isEmpty();
        assertThat(network.entries()).isEmpty();
        drain();
        assertThat(console.entries()).hasSize(2);
        assertThat(network.entries()).hasSize(1);
    }

    @Test
    @DisplayName("console upcalls keep level and text; err lands as error")
    void routesConsole() {
        bridge.log("warn", "careful");
        bridge.err("TypeError: x is not a function (app.js:12)");
        drain();
        assertThat(console.entries().get(0).level()).isEqualTo("warn");
        assertThat(console.entries().get(0).text()).isEqualTo("careful");
        assertThat(console.entries().get(1).level()).isEqualTo("error");
    }

    @Test
    @DisplayName("hostile megabyte strings are capped on the calling thread")
    void capsBeforeCrossing() {
        bridge.log("log", "x".repeat(1_000_000));
        // capped BEFORE the executor task was queued: the queued lambda
        // holds an already-truncated string
        drain();
        assertThat(console.entries().get(0).text())
                .hasSize(ConsoleModel.TEXT_CAP + ConsoleModel.TRUNCATED.length());
    }

    @Test
    @DisplayName("hostile levels and garbage network JSON stay harmless")
    void hostileInputHarmless() {
        bridge.log("EVIL\n", "t");
        bridge.net("not json at all");
        bridge.net(null);
        drain();
        assertThat(console.entries().get(0).level()).isEqualTo("log");
        assertThat(network.entries()).isEmpty();
    }
}
