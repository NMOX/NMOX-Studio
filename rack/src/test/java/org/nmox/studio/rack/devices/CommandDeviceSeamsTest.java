package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import org.nmox.studio.rack.ui.controls.Knob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The CommandDevice base contract pieces the per-device suites never
 * reach: the PATH probe, the argv parser's quote rules, the CI-export
 * seams' exception hygiene, the multi-step launch train, and the
 * RackDevice odds and ends (tooltips, grip, flip, default receive)
 * that every faceplate inherits.
 */
class CommandDeviceSeamsTest {

    @TempDir
    Path projectDir;

    private final Predicate<File> originalTrust = CommandDevice.trustCheck;

    @AfterEach
    void restoreTrust() {
        CommandDevice.trustCheck = originalTrust;
    }

    /** A minimal command device whose argv the test dictates. */
    private static final class Scripted extends CommandDevice {

        volatile List<String> command;
        volatile RuntimeException boom;
        final CountDownLatch finished = new CountDownLatch(1);
        volatile int exitCode = Integer.MIN_VALUE;

        Scripted() {
            super("scripted", "SCRIPTED", "TEST DEVICE", new Color(20, 20, 20), 1);
        }

        @Override
        protected List<String> buildCommand() {
            if (boom != null) {
                throw boom;
            }
            return command;
        }

        @Override
        protected java.io.File commandDir() {
            if (boom != null) {
                throw boom;
            }
            return super.commandDir();
        }

        @Override
        protected void onFinished(int code) {
            exitCode = code;
            finished.countDown();
        }
    }

    @Test
    @DisplayName("toolOnPath finds a staple and refuses an invented tool")
    void toolOnPathProbe() {
        // 'sh' exists on POSIX, 'cmd.exe'-adjacent probes cover Windows via .exe
        boolean anyStaple = CommandDevice.toolOnPath("sh")
                || CommandDevice.toolOnPath("cmd");
        assertThat(anyStaple).isTrue();
        assertThat(CommandDevice.toolOnPath("definitely-not-a-tool-2026")).isFalse();
    }

    @Test
    @DisplayName("parseArguments honors double quotes, single quotes, and runs of spaces")
    void parseArgumentsQuoteRules() {
        assertThat(CommandDevice.parseArguments("a \"b c\" 'd e'  f"))
                .containsExactly("a", "b c", "d e", "f");
        assertThat(CommandDevice.parseArguments("\"mixed 'inner'\" plain"))
                .containsExactly("mixed 'inner'", "plain");
        assertThat(CommandDevice.parseArguments("   ")).isEmpty();
        assertThat(CommandDevice.parseArguments(null)).isEmpty();
    }

    @Test
    @DisplayName("The CI-export seams never throw: a broken device exports null and its project dir")
    void exportSeamsSwallow() throws IOException {
        Files.writeString(projectDir.resolve("package.json"), "{}");
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        try {
            Scripted device = new Scripted();
            rack.addDevice(device);
            device.command = List.of("echo", "hi");
            assertThat(device.exportCommand()).containsExactly("echo", "hi");
            assertThat(device.exportDir()).isEqualTo(projectDir.toFile());

            device.boom = new IllegalStateException("misdialed");
            assertThat(device.exportCommand()).as("export skips, never throws").isNull();
            assertThat(device.exportDir()).isEqualTo(projectDir.toFile());
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("commandPreview shows the argv and directory, and degrades to null")
    void commandPreviewShapes() throws IOException {
        Files.writeString(projectDir.resolve("package.json"), "{}");
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        try {
            Scripted device = new Scripted();
            rack.addDevice(device);
            device.command = List.of("echo", "hello");
            assertThat(device.commandPreview())
                    .contains("$ echo hello")
                    .contains(projectDir.toFile().getAbsolutePath());
            device.command = null;
            assertThat(device.commandPreview()).isNull();
            device.boom = new IllegalStateException("misdialed");
            assertThat(device.commandPreview()).isNull();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("launchSequence runs steps back to back and stops the train on failure")
    void launchSequenceTrain() throws Exception {
        assumeTrue(CommandDevice.toolOnPath("sh"), "POSIX shell required");
        Files.writeString(projectDir.resolve("package.json"), "{}");
        CommandDevice.trustCheck = f -> true;
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        try {
            Scripted device = new Scripted();
            rack.addDevice(device);
            File marker = new File(projectDir.toFile(), "step2-ran");
            device.launchSequence(List.of(
                    new CommandDevice.Step(List.of("sh", "-c", "true"), projectDir.toFile()),
                    new CommandDevice.Step(List.of("sh", "-c", "touch step2-ran"),
                            projectDir.toFile())));
            assertThat(device.finished.await(30, TimeUnit.SECONDS)).isTrue();
            waitUntil(() -> marker.isFile());
            assertThat(device.exitCode).isZero();
        } finally {
            rack.shutdown();
        }

        // a failing first step stops the train: the second never runs
        Rack rack2 = new Rack();
        rack2.setProjectDir(projectDir.toFile());
        try {
            Scripted device = new Scripted();
            rack2.addDevice(device);
            File never = new File(projectDir.toFile(), "never-ran");
            device.launchSequence(List.of(
                    new CommandDevice.Step(List.of("sh", "-c", "exit 3"), projectDir.toFile()),
                    new CommandDevice.Step(List.of("sh", "-c", "touch never-ran"),
                            projectDir.toFile())));
            assertThat(device.finished.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(device.exitCode).isEqualTo(3);
            Thread.sleep(200); // give a wrongly-started step 2 a chance to show
            assertThat(never).as("the train stops at the failure").doesNotExist();
        } finally {
            rack2.shutdown();
        }
    }

    private static void waitUntil(java.util.function.BooleanSupplier cond)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(cond.getAsBoolean()).isTrue();
    }

    @Test
    @DisplayName("launchSequence refuses empty lists, manifest-less dirs, and distrust")
    void launchSequenceRefusals() throws Exception {
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.resolve("bare").toFile());
        try {
            Scripted device = new Scripted();
            rack.addDevice(device);
            device.launchSequence(null);
            device.launchSequence(List.of());
            // manifest-less dir: refused before any trust prompt
            device.launchSequence(List.of(
                    new CommandDevice.Step(List.of("sh", "-c", "true"), projectDir.toFile())));
            assertThat(device.finished.getCount()).isEqualTo(1);
        } finally {
            rack.shutdown();
        }

        Files.writeString(projectDir.resolve("package.json"), "{}");
        CommandDevice.trustCheck = f -> false; // the Keep Safe answer
        Rack rack2 = new Rack();
        rack2.setProjectDir(projectDir.toFile());
        try {
            Scripted device = new Scripted();
            rack2.addDevice(device);
            device.launchSequence(List.of(
                    new CommandDevice.Step(List.of("sh", "-c", "true"), projectDir.toFile())));
            assertThat(device.finished.getCount())
                    .as("distrust spawns nothing").isEqualTo(1);
        } finally {
            rack2.shutdown();
        }
    }

    @Test
    @DisplayName("putRackEnv lands in the rack env; noEnv is empty; both are rack-safe")
    void envSeams() {
        Rack rack = new Rack();
        try {
            Scripted device = new Scripted();
            rack.addDevice(device);
            device.putRackEnv("NMOX_TEST_KEY", "42");
            assertThat(rack.getEnvOverrides()).containsEntry("NMOX_TEST_KEY", "42");
            assertThat(CommandDevice.noEnv()).isEmpty();
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- RackDevice odds and ends ----------------

    /** A bare RackDevice: the base-class defaults unshadowed. */
    private static final class Bare extends RackDevice {

        Bare() {
            super("bare", "BARE", "BASE DEVICE", new Color(10, 60, 90), 1);
            addInPort("in", "IN", SignalType.DATA);
            addOutPort("out", "OUT", SignalType.TRIGGER);
        }
    }

    @Test
    @DisplayName("The base device answers accent, flip state, grip, resume, and default receive")
    void rackDeviceBasics() {
        Bare device = new Bare();
        assertThat(device.getAccent()).isEqualTo(new Color(10, 60, 90));
        assertThat(device.isFront()).isTrue();

        device.setSize(device.getPreferredSize());
        assertThat(device.isGrip(new Point(100, 10))).isTrue();
        assertThat(device.isGrip(new Point(100, 100))).isFalse();
        device.setFront(false);
        assertThat(device.isFront()).isFalse();
        assertThat(device.isGrip(new Point(100, 10)))
                .as("no grip on the rear view").isFalse();
        device.setFront(true);

        // the base receive and resume are deliberate no-ops
        device.receive(device.getPort("in"), Signal.data("x"));
        device.resume();
        assertThat(device.isLive()).isFalse();
        assertThat(device.isResumable()).isFalse();
    }

    @Test
    @DisplayName("Rear-view jack tooltips name the port; the front patch bay counts cables")
    void jackTooltips() {
        Bare device = new Bare();
        device.setSize(device.getPreferredSize());

        device.setFront(false);
        Port in = device.getPort("in");
        MouseEvent onJack = new MouseEvent(device, MouseEvent.MOUSE_MOVED, 0, 0,
                in.getX(), in.getY(), 0, false);
        assertThat(device.getToolTipText(onJack))
                .contains("IN").contains("DATA").contains("input");
        MouseEvent offJack = new MouseEvent(device, MouseEvent.MOUSE_MOVED, 0, 0,
                1, 1, 0, false);
        assertThat(device.getToolTipText(offJack)).isNull();

        device.setFront(true);
        MouseEvent onPatchBay = new MouseEvent(device, MouseEvent.MOUSE_MOVED, 0, 0,
                device.getWidth() - 30, device.getHeight() - 8, 0, false);
        assertThat(device.getToolTipText(onPatchBay))
                .contains("Patch bay").contains("0 of 2");
    }

    @Test
    @DisplayName("A continuous knob persists by value where a stepped one persists by index")
    void continuousKnobParam() {
        final Knob dial = new Knob("GAIN", 0.25);
        RackDevice device = new RackDevice("knobbed", "KNOBBED", "TEST",
                new Color(0, 0, 0), 1) {
            {
                param("gain", dial);
            }
        };
        assertThat(device.getState()).containsEntry("gain", "0.25");
        device.applyState(Map.of("gain", "0.75"));
        assertThat(dial.getValue()).isEqualTo(0.75);
        // malformed persisted values keep the default rather than throwing
        device.applyState(Map.of("gain", "not-a-number"));
        assertThat(dial.getValue()).isEqualTo(0.75);
    }
}
