package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.engine.ToolLocator;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The PREFLIGHT run-loop against real tools: the planner is unit
 * tested elsewhere; this proves the device actually runs the list,
 * reaches a verdict, and fires the right trigger - the jack that
 * gates deploys.
 */
class PreflightDeviceTest {

    @TempDir
    Path projectDir;

    private Rack rack;

    @BeforeAll
    static void requireTools() {
        assumeTrue(toolOnPath("git"), "git not installed");
        assumeTrue(toolOnPath("node"), "node not installed");
    }

    private static boolean toolOnPath(String tool) {
        for (String dir : ToolLocator.augmentedPath().split(File.pathSeparator)) {
            if (new File(dir, tool).canExecute()) {
                return true;
            }
        }
        return false;
    }

    @AfterEach
    void tearDown() {
        if (rack != null) {
            rack.shutdown();
        }
    }

    /** A project whose whole checklist can pass in well under a second. */
    private void writeCleanProject() throws Exception {
        Files.writeString(projectDir.resolve("package.json"), """
                {"name":"pf","scripts":{
                  "test":"node -e \\"process.exit(0)\\"",
                  "build":"node -e \\"process.exit(0)\\""
                }}
                """);
        run("git", "init");
        run("git", "add", "-A");
        run("git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-m", "x");
    }

    private void run(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(projectDir.toFile())
                .redirectErrorStream(true);
        pb.environment().put("PATH", ToolLocator.augmentedPath());
        Process p = pb.start();
        p.getInputStream().readAllBytes();
        p.waitFor(30, TimeUnit.SECONDS);
    }

    private static final class Probe extends RackDevice {

        final CountDownLatch ok = new CountDownLatch(1);
        final CountDownLatch fail = new CountDownLatch(1);

        Probe() {
            super("probe", "PROBE", "PROBE", new Color(0, 0, 0), 1);
            addInPort("okIn", "OK", SignalType.TRIGGER);
            addInPort("failIn", "FAIL", SignalType.TRIGGER);
        }

        @Override
        public void receive(Port in, Signal signal) {
            if ("okIn".equals(in.getId())) {
                ok.countDown();
            } else {
                fail.countDown();
            }
        }
    }

    @Test
    @DisplayName("A clean project earns OK on the deploy-gating jack")
    void cleanProjectFiresOk() throws Exception {
        writeCleanProject();
        rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        PreflightDevice preflight = new PreflightDevice();
        Probe probe = new Probe();
        rack.addDevice(preflight);
        rack.addDevice(probe);
        rack.connect(preflight.getPort("ok"), probe.getPort("okIn"));
        rack.connect(preflight.getPort("fail"), probe.getPort("failIn"));

        preflight.receive(preflight.getPort("run"), Signal.trigger(true));

        assertThat(probe.ok.await(60, TimeUnit.SECONDS))
                .as("all checks pass -> OK fires").isTrue();
        assertThat(probe.fail.getCount()).as("FAIL must not fire").isEqualTo(1);
    }

    @Test
    @DisplayName("A dirty tree blocks the verdict: FAIL fires, OK does not")
    void dirtyTreeFiresFail() throws Exception {
        writeCleanProject();
        Files.writeString(projectDir.resolve("uncommitted.js"), "// not committed");
        rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        PreflightDevice preflight = new PreflightDevice();
        Probe probe = new Probe();
        rack.addDevice(preflight);
        rack.addDevice(probe);
        rack.connect(preflight.getPort("ok"), probe.getPort("okIn"));
        rack.connect(preflight.getPort("fail"), probe.getPort("failIn"));

        preflight.receive(preflight.getPort("run"), Signal.trigger(true));

        assertThat(probe.fail.await(60, TimeUnit.SECONDS))
                .as("git unclean -> FAIL fires").isTrue();
        assertThat(probe.ok.getCount()).as("OK must not fire").isEqualTo(1);
    }
}
