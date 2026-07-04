package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
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
 * The Web3 pair: ANVIL's chain-up announcements (URL out, READY once,
 * SERVING gate down on exit) and GOVERNOR's gas-budget verdicts (diff
 * parsing, fail-closed without a snapshot, the LCD that names the
 * offender). No test here launches a real anvil or forge - lines are
 * fed straight to the parsing hooks, the idiom of the other device
 * behavior suites.
 */
class Web3DevicesTest {

    @TempDir
    Path projectDir;

    /** Captures every signal that arrives on any of its input jacks. */
    private static final class Probe extends RackDevice {
        final ConcurrentLinkedQueue<Signal> received = new ConcurrentLinkedQueue<>();

        Probe() {
            super("probe", "PROBE", "TEST PROBE", new Color(0, 0, 0), 1);
            addInPort("in", "IN", SignalType.DATA);
            addInPort("trig", "TRIG", SignalType.TRIGGER);
            addInPort("gate", "GATE", SignalType.GATE);
        }

        @Override
        public void receive(Port in, Signal signal) {
            received.add(signal);
        }
    }

    // Drain both async paths before asserting: the EDT and the rack's
    // single-threaded signal router. A bare EDT flush leaves the router
    // race that a loaded CI runner loses.
    private static void settle(Rack rack) {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
            // interrupted / already on EDT — not relevant to the assertion
        }
        rack.awaitRouterIdle();
    }

    private Rack aimedRack(String manifest) throws IOException {
        Files.writeString(projectDir.resolve(manifest), "");
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        return rack;
    }

    // ---------------- ANVIL: chain-up announcements ----------------

    @Test
    @DisplayName("ANVIL's listening line fires READY once and puts the URL on the jack")
    void anvilAnnouncesChainUp() throws Exception {
        Rack rack = aimedRack("foundry.toml");
        try {
            AnvilDevice anvil = new AnvilDevice();
            Probe url = new Probe();
            Probe ready = new Probe();
            rack.addDevice(anvil);
            rack.addDevice(url);
            rack.addDevice(ready);
            rack.connect(anvil.getPort("url"), url.getPort("in"));
            rack.connect(anvil.getPort("ready"), ready.getPort("trig"));

            anvil.onLine("Listening on 127.0.0.1:8545");
            anvil.onLine("Listening on 127.0.0.1:8545");
            settle(rack);

            assertThat(url.received).extracting(Signal::payload)
                    .contains("http://127.0.0.1:8545");
            assertThat(ready.received).as("READY fires once, not per repeat").hasSize(1);
            assertThat(anvil.statusLcd.getText()).contains("CHAIN UP").contains("8545");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("ANVIL parses the unlocked-account banner rows onto its screen")
    void anvilParsesAccountBanner() throws Exception {
        Rack rack = aimedRack("foundry.toml");
        try {
            AnvilDevice anvil = new AnvilDevice();
            rack.addDevice(anvil);
            anvil.onLine("(0) 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266 (10000.000000000000000000 ETH)");
            anvil.onLine("(1) 0x70997970C51812dc3A010C7d01b50e0d17dc79C8 (10000.000000000000000000 ETH)");
            anvil.onLine("Private Keys");  // never parsed as an account
            settle(rack);
            assertThat(anvil.accountsSeen()).isEqualTo(2);
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("ANVIL drops its SERVING gate when the chain exits")
    void anvilDropsServingOnExit() throws Exception {
        Rack rack = aimedRack("foundry.toml");
        try {
            AnvilDevice anvil = new AnvilDevice();
            Probe serving = new Probe();
            rack.addDevice(anvil);
            rack.addDevice(serving);
            rack.connect(anvil.getPort("serving"), serving.getPort("gate"));

            anvil.onFinished(0);
            settle(rack);
            assertThat(serving.received).isNotEmpty();
            assertThat(serving.received.stream().reduce((a, b) -> b).orElseThrow().high())
                    .as("last gate edge is low").isFalse();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("ANVIL assembles its argv from PORT, CHAIN-ID, BLOCK-TIME and FORK-URL")
    void anvilCommand() throws Exception {
        Rack rack = aimedRack("foundry.toml");
        try {
            AnvilDevice fresh = new AnvilDevice();
            rack.addDevice(fresh);
            assertThat(fresh.buildCommand()).containsExactly(
                    "anvil", "--port", "8545", "--chain-id", "31337");

            AnvilDevice dialed = new AnvilDevice();
            rack.addDevice(dialed);
            // PORTS index 1 = 8546; BLOCK_TIMES index 2 = 5 seconds
            dialed.applyState(Map.of("port", "1", "blockTime", "2",
                    "chainId", "1337", "forkUrl", "https://rpc.example.org"));
            assertThat(dialed.buildCommand()).containsExactly(
                    "anvil", "--port", "8546", "--chain-id", "1337",
                    "--block-time", "5", "--fork-url", "https://rpc.example.org");

            AnvilDevice bareChain = new AnvilDevice();
            rack.addDevice(bareChain);
            bareChain.applyState(Map.of("chainId", ""));
            assertThat(bareChain.buildCommand())
                    .as("blank chain id leaves anvil's default")
                    .containsExactly("anvil", "--port", "8545");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- GOVERNOR: the gas-budget gate ----------------

    @Test
    @DisplayName("GOVERNOR builds forge snapshot --check, adding --tolerance only when dialed")
    void governorCommand() throws Exception {
        Rack rack = aimedRack("foundry.toml");
        try {
            GovernorDevice exact = new GovernorDevice();
            rack.addDevice(exact);
            assertThat(exact.buildCommand())
                    .containsExactly("forge", "snapshot", "--check");
            assertThat(exact.tolerancePercent()).isZero();

            GovernorDevice tolerant = new GovernorDevice();
            rack.addDevice(tolerant);
            tolerant.applyState(Map.of("tolerance", "3")); // TOLERANCES index 3 = 5%
            assertThat(tolerant.buildCommand())
                    .containsExactly("forge", "snapshot", "--check", "--tolerance", "5");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("gasDiffLine spots forge's diff headers and per-test gas rows, and nothing else")
    void gasDiffLineParsing() {
        assertThat(GovernorDevice.gasDiffLine(
                "Diff in \"test/Counter.t.sol:CounterTest\":"))
                .startsWith("Diff in");
        assertThat(GovernorDevice.gasDiffLine(
                "test_Increment() (gas: 31303 (prev: 30000))"))
                .contains("prev: 30000");
        assertThat(GovernorDevice.gasDiffLine(
                "testWithdraw() (gas: +1303 (+4.3%))"))
                .contains("+1303");
        // pass rows and chatter never read as diffs
        assertThat(GovernorDevice.gasDiffLine("[PASS] test_Increment() (gas: 31303)")).isNull();
        assertThat(GovernorDevice.gasDiffLine("Compiling 24 files with Solc 0.8.24")).isNull();
    }

    @Test
    @DisplayName("GOVERNOR without a .gas-snapshot fails closed: FAIL fires, nothing launches")
    void governorFailsClosedWithoutSnapshot() throws Exception {
        Rack rack = aimedRack("foundry.toml");
        try {
            GovernorDevice governor = new GovernorDevice();
            Probe fail = new Probe();
            rack.addDevice(governor);
            rack.addDevice(fail);
            rack.connect(governor.getPort("fail"), fail.getPort("trig"));

            governor.primaryAction();
            settle(rack);

            assertThat(fail.received).as("the gate fails closed").hasSize(1);
            assertThat(governor.statusLcd.getText()).contains("NO .gas-snapshot");
            assertThat(governor.isLive()).as("no forge process was launched").isFalse();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("GOVERNOR's verdict LCD: within budget on exit 0, the first offender on failure")
    void governorVerdictLcd() throws Exception {
        Rack rack = aimedRack("foundry.toml");
        try {
            GovernorDevice governor = new GovernorDevice();
            rack.addDevice(governor);

            governor.onFinished(0);
            settle(rack);
            assertThat(governor.statusLcd.getText()).isEqualTo("WITHIN BUDGET");

            governor.onLine("Compiling 24 files with Solc 0.8.24");
            governor.onLine("test_Increment() (gas: 31303 (prev: 30000))");
            governor.onFinished(1);
            settle(rack);
            assertThat(governor.statusLcd.getText())
                    .startsWith("OVER BUDGET")
                    .contains("test_Increment()");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("GOVERNOR always speaks the Foundry lane, whatever else the repo holds")
    void governorPinsFoundryLane() throws Exception {
        Files.writeString(projectDir.resolve("package.json"), "{}");
        Rack rack = aimedRack("foundry.toml");
        try {
            GovernorDevice governor = new GovernorDevice();
            rack.addDevice(governor);
            assertThat(governor.effectiveKind())
                    .isEqualTo(ProjectInspector.ProjectKind.FOUNDRY);
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- the FOUNDRY kind itself ----------------

    @Test
    @DisplayName("foundry.toml detects as the FOUNDRY kind; VERITAS failure lines include forge's")
    void foundryKindAndForgeFailures() throws Exception {
        Files.writeString(projectDir.resolve("foundry.toml"), "[profile.default]\n");
        assertThat(ProjectInspector.detectKind(projectDir.toFile()))
                .isEqualTo(ProjectInspector.ProjectKind.FOUNDRY);
        assertThat(TestDevice.failedTestName(
                "[FAIL: assertion failed] test_Increment() (gas: 31303)"))
                .isEqualTo("test_Increment");
        assertThat(TestDevice.failedTestName(
                "[FAIL. Reason: assertion failed] testWithdraw() (gas: 88)"))
                .isEqualTo("testWithdraw");
        assertThat(TestDevice.rerunFailedCommand("forge",
                java.util.List.of("test_A", "test_B")))
                .containsExactly("forge", "test", "--match-test", "test_A|test_B");
    }
}
