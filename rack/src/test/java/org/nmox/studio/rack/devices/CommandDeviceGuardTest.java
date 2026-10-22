package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The project guard: web-tool devices must refuse to launch against a
 * directory with no package.json (e.g. the rack still aimed at the
 * user's home directory) instead of unleashing eslint/npm on it.
 */
class CommandDeviceGuardTest {

    @TempDir
    Path projectDir;

    private static class EchoDevice extends CommandDevice {

        final CountDownLatch finished = new CountDownLatch(1);
        volatile int exitCode = Integer.MIN_VALUE;

        EchoDevice() {
            super("test-echo", "ECHO", "TEST", Color.GRAY, 2);
        }

        @Override
        protected List<String> buildCommand() {
            return List.of("echo", "hello");
        }

        @Override
        protected void onFinished(int code) {
            exitCode = code;
            finished.countDown();
        }
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    @Test
    @DisplayName("Should refuse to launch without a package.json")
    void shouldRefuseWithoutPackageJson() throws Exception {
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        EchoDevice device = new EchoDevice();
        rack.addDevice(device);

        device.primaryAction();
        flushEdt();

        assertThat(device.finished.await(500, TimeUnit.MILLISECONDS))
                .as("command must not run").isFalse();
        assertThat(device.statusLcd.getText()).contains("NO PROJECT MANIFEST");
    }

    @Test
    @DisplayName("A folder with no manifest but one obvious script launches: the script is its manifest (3.8.0)")
    void shouldLaunchInAScriptFolder() throws Exception {
        Files.writeString(projectDir.resolve("hello.py"), "print('hi')\n");
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        EchoDevice device = new EchoDevice();
        rack.addDevice(device);

        device.primaryAction();

        assertThat(device.finished.await(10, TimeUnit.SECONDS))
                .as("the walk that mounted SOLDER for this folder found it refused with NO PROJECT MANIFEST").isTrue();
        assertThat(device.exitCode).isZero();
    }

    @Test
    @DisplayName("A script folder is still a stranger's code: Workspace Trust is asked exactly as for a manifest")
    void scriptFolderStillAsksTrust() throws Exception {
        Files.writeString(projectDir.resolve("hello.py"), "print('hi')\n");
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        CommandDevice.trustCheck = f -> false;
        try {
            EchoDevice device = new EchoDevice();
            rack.addDevice(device);

            device.primaryAction();
            flushEdt();

            assertThat(device.finished.await(500, TimeUnit.MILLISECONDS)).as("Keep Safe: nothing runs").isFalse();
            assertThat(device.statusLcd.getText()).contains("UNTRUSTED WORKSPACE");
        } finally {
            CommandDevice.resetTrustCheck();
        }
    }

    @Test
    @DisplayName("Should launch normally when package.json exists")
    void shouldLaunchWithPackageJson() throws Exception {
        Files.writeString(projectDir.resolve("package.json"), "{\"name\":\"demo\"}");
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        EchoDevice device = new EchoDevice();
        rack.addDevice(device);

        device.primaryAction();

        assertThat(device.finished.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(device.exitCode).isZero();
    }
}
