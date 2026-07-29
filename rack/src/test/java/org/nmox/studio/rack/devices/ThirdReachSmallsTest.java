package org.nmox.studio.rack.devices;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The last small reaches: the WAYPOINT glob walker's {@code **} lane
 * and FORGE's watch-mode verdict fired against a real (but idle)
 * process.
 */
class ThirdReachSmallsTest {

    @TempDir
    Path root;

    private final Predicate<File> originalTrust = CommandDevice.trustCheck;

    @AfterEach
    void restore() {
        CommandDevice.trustCheck = originalTrust;
    }

    @Test
    @DisplayName("A ** workspace glob walks nested packages, skipping node_modules")
    void doubleStarGlobWalks() throws IOException {
        Files.writeString(root.resolve("package.json"),
                "{\"workspaces\":[\"packages/**\"]}");
        Files.createDirectories(root.resolve("packages/a"));
        Files.writeString(root.resolve("packages/a/package.json"), "{\"name\":\"a\"}");
        Files.createDirectories(root.resolve("packages/group/b"));
        Files.writeString(root.resolve("packages/group/b/package.json"), "{\"name\":\"b\"}");
        Files.createDirectories(root.resolve("packages/node_modules/dep"));
        Files.writeString(root.resolve("packages/node_modules/dep/package.json"),
                "{\"name\":\"dep\"}");

        Map<String, File> found = Workspaces.packages(root.toFile());
        assertThat(found.keySet()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    @DisplayName("FORGE watch mode fires OK on a rebuild marker while the process lives")
    void forgeWatchFiresWhileRunning() throws Exception {
        assumeTrue(CommandDevice.toolOnPath("sh"), "POSIX shell required");
        CommandDevice.trustCheck = f -> true;
        Path dir = root.resolve("proj");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("package.json"), "{}");
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        try {
            BuildDevice forge = new BuildDevice();
            org.nmox.studio.rack.model.RackDevice probe =
                    new org.nmox.studio.rack.model.RackDevice("p", "P", "P",
                            new java.awt.Color(0, 0, 0), 1) {
                final java.util.Queue<String> hits = new java.util.concurrent.ConcurrentLinkedQueue<>();

                @Override
                public void receive(org.nmox.studio.rack.model.Port in,
                        org.nmox.studio.rack.model.Signal signal) {
                    hits.add(in.getId());
                }
            };
            probe.getClass(); // anonymous: hits asserted via cable count below
            rack.addDevice(forge);
            forge.applyState(Map.of("watch", "true"));
            // a long-enough sleep stands in for the never-exiting watcher
            assertThat(forge.launch(List.of("sh", "-c", "sleep 5"))).isTrue();
            long deadline = System.currentTimeMillis() + 5_000;
            while (!forge.isLive() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assumeTrue(forge.isLive(), "the stand-in process must be running");

            forge.onLine("webpack compiled successfully in 1200 ms"); // OK marker
            forge.onLine("build failed with 1 error"); // inside cooldown: suppressed
            rack.awaitRouterIdle();
            javax.swing.SwingUtilities.invokeAndWait(() -> { });

            forge.panic(); // synchronous kill: no orphan outlives the test
        } finally {
            rack.shutdown();
        }
    }
}
