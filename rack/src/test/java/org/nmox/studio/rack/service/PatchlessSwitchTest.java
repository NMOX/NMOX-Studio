package org.nmox.studio.rack.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.devices.DeviceType;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackIO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A project with no patch shows ITS OWN empty rack, never the previous
 * project's devices (v1.278.0, the Task Rack persona walk).
 *
 * <p>{@code autoLoadPatch} loaded {@code .nmoxrack.json} when the aimed
 * project had one and did NOTHING when it didn't — so switching from a
 * project with a patch to one without left the first project's devices
 * mounted. The walk saw it as a lie (a REPL dialed to {@code elm repl}
 * on a plain Node project), but the damage runs deeper: Save Patch
 * would write project A's devices into project B's file, and a running
 * device keeps its process while every lane's {@code commandDir}
 * re-roots to B — pressing GO would run A's command in B's directory.
 */
class PatchlessSwitchTest {

    @Test
    @DisplayName("switching to a patchless project drops the previous project's devices")
    void patchlessProjectDoesNotInheritDevices(@TempDir Path tmp) throws Exception {
        Path withPatch = Files.createDirectory(tmp.resolve("has-patch"));
        Path noPatch = Files.createDirectory(tmp.resolve("no-patch"));

        RackService service = new RackService();
        Rack rack = service.getRack();
        try {
            // author a patch for the first project: two recognizable devices
            // (the starter MONITOR is already mounted — clear it so the
            // authored patch is exactly what this test puts in it)
            clear(rack);
            rack.addDevice(DeviceType.REPL.create());
            rack.addDevice(DeviceType.CMD.create());
            RackIO.save(rack, new File(withPatch.toFile(), RackIO.DEFAULT_FILENAME));

            rack.setProjectDir(withPatch.toFile());
            assertThat(rack.getDevices())
                    .as("the patch loads for the project that owns it")
                    .hasSize(2);

            rack.setProjectDir(noPatch.toFile());
            assertThat(rack.getDevices().stream().map(d -> d.getTypeId()))
                    .as("a project with no patch must not inherit the previous"
                            + " project's pipeline — Save Patch here would"
                            + " otherwise write the OTHER project's devices,"
                            + " and a running device would keep its process"
                            + " while its commandDir re-rooted")
                    .containsExactly("console");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("the patchless state is exactly the starter rack")
    void patchlessStateIsTheStarterRack(@TempDir Path tmp) throws Exception {
        Path noPatch = Files.createDirectory(tmp.resolve("fresh"));
        RackService service = new RackService();
        Rack rack = service.getRack();
        try {
            rack.addDevice(DeviceType.REPL.create());
            rack.setProjectDir(noPatch.toFile());
            assertThat(rack.getDevices()).hasSize(1);
            // same shape StarterRackTest pins for a first launch: one
            // MONITOR with its TAP on stderr (option index 1)
            assertThat(rack.getDevices().get(0).getTypeId()).isEqualTo("console");
            assertThat(rack.getDevices().get(0).getState()).containsEntry("tap", "1");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("a project WITH a patch still loads it, unchanged")
    void patchedProjectStillLoads(@TempDir Path tmp) throws Exception {
        Path a = Files.createDirectory(tmp.resolve("a"));
        Path b = Files.createDirectory(tmp.resolve("b"));
        RackService service = new RackService();
        Rack rack = service.getRack();
        try {
            clear(rack);
            rack.addDevice(DeviceType.REPL.create());
            RackIO.save(rack, new File(a.toFile(), RackIO.DEFAULT_FILENAME));
            clear(rack);
            rack.addDevice(DeviceType.CMD.create());
            rack.addDevice(DeviceType.CMD.create());
            RackIO.save(rack, new File(b.toFile(), RackIO.DEFAULT_FILENAME));

            rack.setProjectDir(a.toFile());
            assertThat(rack.getDevices().stream().map(d -> d.getTypeId()))
                    .containsExactly("repl");
            rack.setProjectDir(b.toFile());
            assertThat(rack.getDevices().stream().map(d -> d.getTypeId()))
                    .as("patch-to-patch switching is untouched by the fix")
                    .containsExactly("cmd", "cmd");
        } finally {
            rack.shutdown();
        }
    }

    /** Drops every mounted device, including the starter MONITOR. */
    private static void clear(Rack rack) {
        for (var d : new java.util.ArrayList<>(rack.getDevices())) {
            rack.removeDevice(d);
        }
    }
}
