package org.nmox.studio.rack.devices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WAYPOINT is ROSETTA one level down: the dial steers which workspace
 * package the Node lanes operate on, and removing the device stops the
 * steering. Every assertion drains the EDT first — the knob options
 * land there (the rack-tests law, applied at writing time).
 */
class WaypointDeviceTest {

    @TempDir
    Path dir;

    private void monorepo() throws IOException {
        Files.writeString(dir.resolve("package.json"),
                "{\"name\":\"mono\",\"workspaces\":[\"packages/*\"],"
                + "\"scripts\":{\"rootjob\":\"echo root\"}}");
        Path web = Files.createDirectories(dir.resolve("packages/web"));
        Files.writeString(web.resolve("package.json"),
                "{\"name\":\"@mono/web\",\"scripts\":{\"dev\":\"vite\"}}");
        Path api = Files.createDirectories(dir.resolve("packages/api"));
        Files.writeString(api.resolve("package.json"),
                "{\"name\":\"@mono/api\",\"scripts\":{\"dev\":\"node .\"}}");
    }

    private static void drain() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
    }

    /**
     * reload() hops the offEdt RequestProcessor lane THEN the EDT — an
     * EDT drain alone races it (the two-async-paths law, third sighting
     * tonight). Await the observable outcome, bounded.
     */
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            drain();
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static void awaitOptions(WaypointDevice waypoint, int count) throws Exception {
        await(() -> waypoint.workspaceKnobForTest().getOptions().length == count);
    }

    @Test
    @DisplayName("Workspaces core: npm globs + pnpm-workspace.yaml union, names from package.json")
    void coreParsesBothDialects() throws IOException {
        monorepo();
        Files.writeString(dir.resolve("pnpm-workspace.yaml"),
                "packages:\n  - \"tools/cli\"\n");
        Path cli = Files.createDirectories(dir.resolve("tools/cli"));
        Files.writeString(cli.resolve("package.json"), "{\"name\":\"cli\"}");

        var found = Workspaces.packages(dir.toFile());
        assertThat(found.keySet())
                .containsExactly("@mono/api", "@mono/web", "cli");
        assertThat(found.get("cli")).isEqualTo(cli.toFile());
    }

    @Test
    @DisplayName("no workspaces declared → empty, and the knob stays at just root")
    void bareProjectHasNoPackages() throws Exception {
        Files.writeString(dir.resolve("package.json"), "{}");
        assertThat(Workspaces.packages(dir.toFile())).isEmpty();
    }

    @Test
    @DisplayName("dialing a package re-roots NPM-9000's scripts and commandDir; root restores")
    void steersNpm9000() throws Exception {
        monorepo();
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        try {
            WaypointDevice waypoint = new WaypointDevice();
            rack.addDevice(waypoint);
            NpmScriptDevice scripts = new NpmScriptDevice();
            rack.addDevice(scripts);
            awaitOptions(waypoint, 3); // root + 2 packages

            waypoint.applyState(Map.of("workspace", "@mono/web"));
            drain();
            scripts.reloadScripts();
            drain();
            scripts.applyState(Map.of("script", "dev"));
            assertThat(scripts.buildCommand()).containsExactly("npm", "run", "dev");
            assertThat(scripts.commandDir())
                    .isEqualTo(dir.resolve("packages/web").toFile());

            waypoint.applyState(Map.of("workspace", "root"));
            drain();
            assertThat(scripts.commandDir()).isEqualTo(dir.toFile());
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("the base lanes re-root too — PURITY lints the chosen package, not the root")
    void steersBaseCommandDevices() throws Exception {
        monorepo();
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        try {
            WaypointDevice waypoint = new WaypointDevice();
            rack.addDevice(waypoint);
            LintDevice lint = new LintDevice();
            rack.addDevice(lint);
            awaitOptions(waypoint, 3);
            waypoint.applyState(Map.of("workspace", "@mono/api"));
            drain();
            // LintDevice does NOT override commandDir — this exercises the
            // CommandDevice base consult that NPM-9000's own override hides
            assertThat(lint.commandDir())
                    .isEqualTo(dir.resolve("packages/api").toFile());
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("only NODE lanes re-root — a ROSETTA-dialed cargo lane keeps its Cargo.toml dir")
    void nonNodeLanesUnaffected() throws Exception {
        monorepo();
        Files.writeString(dir.resolve("Cargo.toml"), "[package]");
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        try {
            WaypointDevice waypoint = new WaypointDevice();
            rack.addDevice(waypoint);
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            awaitOptions(waypoint, 3);
            waypoint.applyState(Map.of("workspace", "@mono/api"));
            drain();
            run.applyState(Map.of("target", "4")); // rust, explicit
            assertThat(run.commandDir()).isEqualTo(dir.toFile());
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("patch-load order restores the dialed workspace — applyState BEFORE the async options land")
    void patchLoadOrderRestoresSelection() throws Exception {
        monorepo();
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        try {
            WaypointDevice waypoint = new WaypointDevice();
            rack.addDevice(waypoint);
            // RackIO's exact order: state applied immediately, options
            // still loading on the offEdt lane — the knob must remember
            // the wish and honor it when the options arrive
            waypoint.applyState(Map.of("workspace", "@mono/web"));
            awaitOptions(waypoint, 3);
            await(() -> "@mono/web".equals(
                    waypoint.workspaceKnobForTest().getSelectedOption()));
            await(() -> rack.getWorkspaceOverride() != null
                    && rack.getWorkspaceOverride().endsWith("web"));
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("removing WAYPOINT stops the steering — the ROSETTA dispose law")
    void disposeClearsOverride() throws Exception {
        monorepo();
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        try {
            WaypointDevice waypoint = new WaypointDevice();
            rack.addDevice(waypoint);
            awaitOptions(waypoint, 3);
            waypoint.applyState(Map.of("workspace", "@mono/api"));
            drain();
            assertThat(rack.getWorkspaceOverride()).isNotNull();
            rack.removeDevice(waypoint);
            drain();
            assertThat(rack.getWorkspaceOverride()).isNull();
            // the CI-caught resurrection: a stale projectChanged fan-out
            // lands AFTER removal — its reload/apply must no-op, not
            // re-steer (this deterministically replays the loaded-runner race)
            waypoint.projectChanged(dir.toFile());
            Thread.sleep(300);
            drain();
            assertThat(rack.getWorkspaceOverride()).isNull();
        } finally {
            rack.shutdown();
        }
    }
}
