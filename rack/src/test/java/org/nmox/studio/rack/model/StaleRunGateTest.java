package org.nmox.studio.rack.model;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.devices.ViteDevice;
import org.nmox.studio.rack.service.WorkspaceTrust;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 18, second half: a stale run's exit must not drop the gate of
 * the run that replaced it.
 */
class StaleRunGateTest {

    @TempDir
    Path dir;

    private final List<Runnable> lane = new CopyOnWriteArrayList<>();

    @BeforeEach
    void trustScratch() {
        WorkspaceTrust.clearForTest();
    }

    @AfterEach
    void restore() {
        RackDevice.resetExecLane();
        WorkspaceTrust.clearForTest();
    }

    private static final class GateProbe extends RackDevice {
        final ConcurrentLinkedQueue<Boolean> gates = new ConcurrentLinkedQueue<>();

        GateProbe() {
            super("probe", "PROBE", "PROBE", new Color(0, 0, 0), 1);
            addInPort("gate", "GATE", SignalType.GATE);
        }

        @Override
        public void receive(Port in, Signal signal) {
            gates.add(signal.high());
        }
    }

    private Rack rackWithNodeProject() throws IOException {
        Files.writeString(dir.resolve("package.json"), "{}");
        WorkspaceTrust.trust(dir.toFile());
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        return rack;
    }

    private static void settle(Rack rack) {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
            // not relevant to the assertion
        }
        rack.awaitRouterIdle();
    }

    @Test
    @DisplayName("DEV pressed twice: the first run's exit must not drop the second run's SERVING gate")
    void staleExitKeepsItsHandsOffTheLiveRun() throws Exception {
        RackDevice.execLane = lane::add;
        Rack rack = rackWithNodeProject();
        try {
            ViteDevice vite = new ViteDevice();
            GateProbe probe = new GateProbe();
            rack.addDevice(vite);
            rack.addDevice(probe);
            rack.connect(vite.getPort("serving"), probe.getPort("gate"));

            vite.receive(vite.getPort("serve"), Signal.trigger());   // run A
            vite.receive(vite.getPort("serve"), Signal.trigger());   // run B replaces it
            settle(rack);
            assertThat(lane).as("both launches queued their spawn on the lane").hasSize(2);
            assertThat(probe.gates).as("each launch raised the gate").containsExactly(true, true);

            // Run A's lane task now runs: it finds itself cancelled by B,
            // spawns nothing, and reports its exit — the STALE run's exit,
            // arriving while B is the live run.
            lane.get(0).run();
            settle(rack);

            assertThat(vite.isLive())
                    .as("B is still the live run — nothing stopped it")
                    .isTrue();
            assertThat(probe.gates)
                    .as("a stale run's exit must not drop the live run's SERVING gate")
                    .containsExactly(true, true);
        } finally {
            rack.shutdown();
        }
    }

    /**
     * Fires the replaced run's exit from inside the kill that replaces it —
     * the window a real pump thread lands in, made deterministic.
     */
    private static final class KillWindowDevice extends RackDevice {
        final List<Boolean> superseded = new CopyOnWriteArrayList<>();
        private Runnable duringKill = () -> { };

        KillWindowDevice() {
            super("test.killwindow", "KILLWINDOW", "", Color.GRAY, 1);
        }

        void run(List<String> command) {
            exec(command, java.util.Map.of(), null, l -> { },
                    (code, replaced) -> superseded.add(replaced));
        }

        void onNextKill(Runnable r) {
            duringKill = r;
        }

        @Override
        protected void stopProcess() {
            super.stopProcess();
            Runnable r = duringKill;
            duringKill = () -> { };
            r.run();
        }
    }

    @Test
    @DisplayName("the replacement is numbered BEFORE the kill — an exit landing inside that window is already stale")
    void theKillWindowIsClosed() {
        RackDevice.execLane = lane::add;
        KillWindowDevice d = new KillWindowDevice();

        d.run(List.of("sleep", "60"));                  // run A
        assertThat(lane).hasSize(1);

        // A's exit now lands DURING the kill that run B performs — the
        // handful of instructions a real pump thread can arrive in.
        d.onNextKill(() -> lane.get(0).run());
        d.run(List.of("echo", "second"));               // run B replaces A

        assertThat(d.superseded)
                .as("a run whose exit lands inside its own replacement's kill "
                        + "must already read as replaced — numbering after the "
                        + "kill leaves exactly this window open")
                .containsExactly(true);
    }

    @Test
    @DisplayName("STOP is not a replacement: the run that really ended still drops its gate")
    void anEndedRunStillSpeaks() throws Exception {
        RackDevice.execLane = lane::add;
        Rack rack = rackWithNodeProject();
        try {
            ViteDevice vite = new ViteDevice();
            GateProbe probe = new GateProbe();
            rack.addDevice(vite);
            rack.addDevice(probe);
            rack.connect(vite.getPort("serving"), probe.getPort("gate"));

            vite.receive(vite.getPort("serve"), Signal.trigger());   // one run
            vite.receive(vite.getPort("stop"), Signal.trigger());    // the user stops it
            settle(rack);

            // Stopping opens no new launch, so this run still owns the
            // device: its exit is the truth about what is serving.
            lane.get(0).run();
            settle(rack);

            assertThat(vite.isLive()).isFalse();
            assertThat(probe.gates)
                    .as("a run nothing replaced must still drop the gate it raised")
                    .containsExactly(true, false);
        } finally {
            rack.shutdown();
        }
    }
}
