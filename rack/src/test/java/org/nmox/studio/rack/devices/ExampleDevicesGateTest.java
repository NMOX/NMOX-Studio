package org.nmox.studio.rack.devices;

import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The example gallery is a set of fixtures, not prose: every device in
 * {@code examples/devices.d/} must parse through the real judge AND
 * mount through the real load path (the v2.0.1 lesson — a docs gate
 * that stops one stage short of what the reader will do is a gate on
 * the wrong door). A rule change that breaks an example fails here,
 * naming the file, instead of failing the first user who copies it.
 */
class ExampleDevicesGateTest {

    private static File[] exampleFiles() {
        File dir = new File("..", "examples/devices.d");
        File[] files = dir.listFiles(f -> f.getName().endsWith(".json"));
        assertThat(files).as("examples/devices.d should exist and hold devices").isNotNull();
        return files;
    }

    @Test
    void everyExampleParsesAndMounts() throws Exception {
        File[] files = exampleFiles();
        assertThat(files.length)
                .as("the gallery advertises six devices").isGreaterThanOrEqualTo(6);
        Set<String> ids = new HashSet<>();
        for (File f : files) {
            DeviceFile.Result r = DeviceFile.read(Files.readString(f.toPath()));
            assertThat(r.problem()).as("%s must parse", f.getName()).isNull();
            assertThat(UserDevices.fit(r.device()))
                    .as("%s must build a mountable face", f.getName()).isNotNull();
            assertThat(ids.add(r.device().id()))
                    .as("%s must not reuse another example's id", f.getName()).isTrue();
            assertThat(r.device().id())
                    .as("%s lives in the examples namespace", f.getName())
                    .startsWith("com.nmox.examples.");
        }
    }

    @Test
    void everyExampleSurvivesTheCatalogLaws() throws Exception {
        // the shelf judges extensions with DeviceCatalog.validate on top
        // of the file judge — an example must clear BOTH or it would be
        // skipped-with-note on a real install
        for (File f : exampleFiles()) {
            DeviceFile.Result r = DeviceFile.read(Files.readString(f.toPath()));
            String problem = DeviceCatalog.validate(
                    new JsonDeviceExtension(UserDevices.fit(r.device())).descriptor(),
                    new HashSet<>());
            assertThat(problem).as("%s must clear the shelf laws", f.getName()).isNull();
        }
    }

    @Test
    void galleryShipsInstalledAndActive() {
        // the whole point: no drop-in dir, no copy step, no override —
        // a fresh install's DEFAULT catalog already holds all six
        UserDevices.dirOverride = new File("does-not-exist-anywhere");
        UserDevices.invalidate();
        try {
            for (String id : new String[]{
                "com.nmox.examples.auditor", "com.nmox.examples.chronicle",
                "com.nmox.examples.quartermaster", "com.nmox.examples.watchtower",
                "com.nmox.examples.probe", "com.nmox.examples.groundskeeper"}) {
                assertThat(DeviceCatalog.byId(id))
                        .as("%s must be on the shelf out of the box", id).isPresent();
            }
        } finally {
            UserDevices.dirOverride = null;
            UserDevices.invalidate();
        }
    }

    @Test
    void indexMatchesTheDirectory() throws Exception {
        // the classpath cannot be listed, so bundled/index names the
        // files — and this pin keeps it honest: a new example that
        // forgets the index would silently not ship
        java.util.List<String> indexed = Files.readAllLines(
                new File("..", "examples/devices.d/index").toPath())
                .stream().map(String::strip).filter(l -> !l.isEmpty()).sorted().toList();
        java.util.List<String> onDisk = java.util.Arrays.stream(exampleFiles())
                .map(File::getName).sorted().toList();
        assertThat(indexed).as("bundled/index must list exactly the gallery files")
                .isEqualTo(onDisk);
    }
}
