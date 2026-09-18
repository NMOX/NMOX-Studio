package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A refused launch is a verdict on the patch bay, not only on the LCD.
 *
 * <p>No manifest, an untrusted workspace, a toolchain with no such verb,
 * GOVERNOR without its snapshot, a chain console without its tool — each
 * used to write the LCD and return, emitting nothing, so a lane wired on
 * DONE (POLYGLOT_GAUNTLET's QUORUM) waited forever. FAIL and DONE both
 * carry {@code false}, the bits a failed run emits (the 2026-09-17 rack
 * audit; {@link CommandDevice#refuseLaunch} is the one home).
 */
class RefusedLaunchSpeaksTest {

    @TempDir
    Path root;

    private final Predicate<java.io.File> originalTrust = CommandDevice.trustCheck;

    @AfterEach
    void restoreTrust() {
        CommandDevice.trustCheck = originalTrust;
    }

    private int caseNo;

    private Path freshDir(String... files) throws IOException {
        Path dir = Files.createDirectories(root.resolve("case-" + (caseNo++)));
        for (String f : files) {
            Files.writeString(dir.resolve(f), "{}");
        }
        return dir;
    }

    private static final class EchoDevice extends CommandDevice {
        volatile List<String> command = List.of("echo", "hello");

        EchoDevice() {
            super("test-echo", "ECHO", "TEST", Color.GRAY, 2);
        }

        @Override
        protected List<String> buildCommand() {
            return command;
        }

        void sequence() {
            launchSequence(List.of(new Step(List.of("echo", "a"), projectDir())));
        }
    }

    /** Records fail/done arrivals as id:high. */
    private static final class Probe extends RackDevice {
        final ConcurrentLinkedQueue<String> received = new ConcurrentLinkedQueue<>();

        Probe() {
            super("probe", "PROBE", "TEST PROBE", new Color(0, 0, 0), 1);
            addInPort("fail", "FAIL", SignalType.TRIGGER);
            addInPort("done", "DONE", SignalType.TRIGGER);
            addInPort("ok", "OK", SignalType.TRIGGER);
        }

        @Override
        public void receive(Port in, Signal signal) {
            received.add(in.getId() + ":" + signal.high());
        }
    }

    private static void settle(Rack rack) throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        rack.awaitRouterIdle();
    }

    private Probe wire(Rack rack, CommandDevice device) {
        Probe probe = new Probe();
        rack.addDevice(device);
        rack.addDevice(probe);
        rack.connect(device.getPort("fail"), probe.getPort("fail"));
        rack.connect(device.getPort("done"), probe.getPort("done"));
        rack.connect(device.getPort("ok"), probe.getPort("ok"));
        return probe;
    }

    private static void assertRefusalVerdict(Probe probe, String name) {
        assertThat(probe.received)
                .as(name + ": a refusal emits FAIL and DONE, both low, and no OK")
                .containsExactlyInAnyOrder("fail:false", "done:false");
    }

    @Test
    @DisplayName("No project manifest: FAIL + DONE leave the jacks, nothing spawns")
    void noManifestSpeaks() throws Exception {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir().toFile());
        try {
            EchoDevice device = new EchoDevice();
            Probe probe = wire(rack, device);
            device.primaryAction();
            settle(rack);
            assertRefusalVerdict(probe, "no manifest");
            assertThat(device.statusLcd.getText()).contains("NO PROJECT MANIFEST");
            assertThat(device.isLive()).isFalse();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("Keep Safe: FAIL + DONE leave the jacks")
    void untrustedSpeaks() throws Exception {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir("package.json").toFile());
        try {
            CommandDevice.trustCheck = f -> false;
            EchoDevice device = new EchoDevice();
            Probe probe = wire(rack, device);
            device.primaryAction();
            settle(rack);
            assertRefusalVerdict(probe, "untrusted");
            assertThat(device.statusLcd.getText()).contains("UNTRUSTED WORKSPACE");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("A toolchain with no such verb (null command): FAIL + DONE leave the jacks")
    void noCommandSpeaks() throws Exception {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir("package.json").toFile());
        try {
            EchoDevice device = new EchoDevice();
            device.command = null;
            Probe probe = wire(rack, device);
            device.primaryAction();
            settle(rack);
            assertRefusalVerdict(probe, "no command");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("A refused sequence (no manifest) speaks the same verdict")
    void refusedSequenceSpeaks() throws Exception {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir().toFile());
        try {
            EchoDevice device = new EchoDevice();
            Probe probe = wire(rack, device);
            device.sequence();
            settle(rack);
            assertRefusalVerdict(probe, "sequence");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("GOVERNOR without .gas-snapshot: DONE now rides beside its FAIL")
    void governorRefusalEmitsDone() throws Exception {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir("foundry.toml").toFile());
        try {
            GovernorDevice governor = new GovernorDevice();
            Probe probe = wire(rack, governor);
            governor.primaryAction();
            settle(rack);
            assertRefusalVerdict(probe, "GOVERNOR");
            assertThat(governor.statusLcd.getText()).contains("NO .gas-snapshot");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("STELLAR without soroban-sdk and ANCHOR without Anchor.toml refuse out loud on the jacks")
    void chainConsolesSpeak() throws Exception {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir("Cargo.toml").toFile());
        try {
            StellarDevice stellar = new StellarDevice();
            Probe stellarProbe = wire(rack, stellar);
            stellar.receive(stellar.getPort("action"), Signal.trigger());
            settle(rack);
            assertRefusalVerdict(stellarProbe, "STELLAR");
            assertThat(stellar.statusText()).contains("NO soroban-sdk");

            AnchorDevice anchor = new AnchorDevice();
            Probe anchorProbe = wire(rack, anchor);
            anchor.receive(anchor.getPort("run"), Signal.trigger());
            settle(rack);
            assertRefusalVerdict(anchorProbe, "ANCHOR");
        } finally {
            rack.shutdown();
        }
    }
}
