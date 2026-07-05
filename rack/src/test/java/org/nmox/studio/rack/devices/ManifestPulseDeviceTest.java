package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The device half of the manifest pulse: overriders re-sync from THEIR
 * files and stay idempotent — a reload that finds nothing new fires no
 * knob event (the storm law's equality-guard leg), and irrelevant
 * manifests don't even trigger a reload.
 */
class ManifestPulseDeviceTest {

    @TempDir
    Path projectDir;

    /** Drains the shared device background thread (offEdt work). */
    private static final class BgDrain extends RackDevice {
        private BgDrain() {
            super("drain", "DRAIN", "TEST", new Color(0, 0, 0), 1);
        }

        static void drain() {
            CountDownLatch latch = new CountDownLatch(1);
            offEdt(latch::countDown);
            try {
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("device background thread did not drain");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void flushEdt() {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
            // not relevant to the assertion
        }
    }

    /** Router → device bg → EDT: the full pulse path, in delivery order. */
    private static void settleAll(Rack rack) {
        rack.awaitRouterIdle();
        BgDrain.drain();
        flushEdt();
    }

    /** Writes a file and bumps its mtime past any cached read. */
    private Path write(String name, String content) throws IOException {
        Path file = projectDir.resolve(name);
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(
                System.currentTimeMillis() + (bump += 2_000)));
        return file;
    }

    private long bump;

    private Rack aimedRack() {
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        return rack;
    }

    // ---------------- NPM-9000 / NpmScriptDevice ----------------

    @Test
    @DisplayName("NPM-9000 reloads the SCRIPT knob on package.json change; unchanged content fires no knob event")
    void npmScriptsReloadWithEqualityGuard() throws Exception {
        Path pkg = write("package.json", "{\"scripts\":{\"dev\":\"vite\"}}");
        Rack rack = aimedRack();
        try {
            NpmScriptDevice device = new NpmScriptDevice();
            rack.addDevice(device);
            BgDrain.drain();
            flushEdt();
            assertThat(device.scriptKnobForTest().getOptions()).containsExactly("dev");

            AtomicInteger knobEvents = new AtomicInteger();
            device.scriptKnobForTest().addChangeListener(knobEvents::incrementAndGet);

            // STORM LAW: the manifest pulse fires but nothing changed —
            // the reload must not call setOptions (which always fires)
            device.manifestChanged(List.of(pkg));
            BgDrain.drain();
            flushEdt();
            assertThat(knobEvents).as("unchanged reload is silent").hasValue(0);

            // a real edit: the knob follows, exactly one event
            write("package.json", "{\"scripts\":{\"dev\":\"vite\",\"test\":\"jest\"}}");
            device.manifestChanged(List.of(pkg));
            BgDrain.drain();
            flushEdt();
            assertThat(device.scriptKnobForTest().getOptions()).containsExactly("dev", "test");
            assertThat(knobEvents).hasValue(1);

            // an irrelevant manifest triggers no reload at all
            write("package.json", "{\"scripts\":{\"dev\":\"vite\"}}");
            device.manifestChanged(List.of(projectDir.resolve("composer.json")));
            BgDrain.drain();
            flushEdt();
            assertThat(device.scriptKnobForTest().getOptions())
                    .as("composer.json is not NPM-9000's manifest")
                    .containsExactly("dev", "test");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("NPM-9000's pulse rides the rack router: Rack.manifestChanged reaches the device")
    void npmScriptsThroughRouter() throws Exception {
        Path pkg = write("package.json", "{\"scripts\":{\"dev\":\"vite\"}}");
        Rack rack = aimedRack();
        try {
            NpmScriptDevice device = new NpmScriptDevice();
            rack.addDevice(device);
            BgDrain.drain();
            flushEdt();

            write("package.json", "{\"scripts\":{\"build\":\"vite build\",\"dev\":\"vite\"}}");
            rack.manifestChanged(List.of(pkg));
            settleAll(rack);
            assertThat(device.scriptKnobForTest().getOptions()).containsExactly("build", "dev");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- DYNAMO / DynamoDevice ----------------

    @Test
    @DisplayName("DYNAMO re-parses the taskfile on Gruntfile change; unchanged content fires no knob event")
    void dynamoReloadWithEqualityGuard() throws Exception {
        write("package.json", "{}");
        Path gruntfile = write("Gruntfile.js",
                "module.exports = function(grunt) { grunt.registerTask('build', []); };");
        Rack rack = aimedRack();
        try {
            DynamoDevice device = new DynamoDevice();
            rack.addDevice(device);
            BgDrain.drain();
            flushEdt();
            assertThat(device.taskKnobForTest().getOptions()).containsExactly("build");

            AtomicInteger knobEvents = new AtomicInteger();
            device.taskKnobForTest().addChangeListener(knobEvents::incrementAndGet);

            device.manifestChanged(List.of(gruntfile));
            BgDrain.drain();
            flushEdt();
            assertThat(knobEvents).as("unchanged reload is silent").hasValue(0);

            write("Gruntfile.js", "module.exports = function(grunt) {"
                    + " grunt.registerTask('build', []); grunt.registerTask('deploy', []); };");
            device.manifestChanged(List.of(gruntfile));
            BgDrain.drain();
            flushEdt();
            assertThat(device.taskKnobForTest().getOptions()).containsExactly("build", "deploy");
            assertThat(knobEvents).hasValue(1);
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- CRATE / PackageManagerDevice ----------------

    @Test
    @DisplayName("CRATE refreshes the DEPS LCD when dependency manifests change")
    void crateRefreshesDeps() throws Exception {
        Path pkg = write("package.json", "{\"dependencies\":{\"react\":\"^18.0.0\"}}");
        Rack rack = aimedRack();
        try {
            PackageManagerDevice device = new PackageManagerDevice();
            rack.addDevice(device);
            BgDrain.drain();
            flushEdt();
            assertThat(device.depsTextForTest()).isEqualTo("1+0 DEPS");

            write("package.json",
                    "{\"dependencies\":{\"react\":\"^18.0.0\",\"redux\":\"^5.0.0\"},"
                    + "\"devDependencies\":{\"vitest\":\"^2.0.0\"}}");
            device.manifestChanged(List.of(pkg));
            BgDrain.drain();
            flushEdt();
            assertThat(device.depsTextForTest()).isEqualTo("2+1 DEPS");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- ARTISAN / ArtisanDevice ----------------

    @Test
    @DisplayName("ARTISAN re-checks currency only when the locked laravel/framework moved")
    void artisanVersionGuard() throws Exception {
        write("composer.json", "{}");
        Path lock = write("composer.lock",
                "{\"packages\":[{\"name\":\"laravel/framework\",\"version\":\"v11.0.0\"}]}");
        Rack rack = aimedRack();
        try {
            ArtisanDevice device = new ArtisanDevice();
            rack.addDevice(device);
            BgDrain.drain();
            flushEdt();

            // sentinel: a refresh overwrites the status LCD, so an intact
            // sentinel proves the equality guard held
            javax.swing.SwingUtilities.invokeAndWait(
                    () -> device.statusLcd.setText("SENTINEL"));
            device.manifestChanged(List.of(lock));
            BgDrain.drain();
            flushEdt();
            assertThat(device.statusLcd.getText())
                    .as("unchanged lock does not re-run the refresh").isEqualTo("SENTINEL");

            // the locked version moves → the refresh runs (and, with no
            // artisan script in the project, honestly says so)
            write("composer.lock",
                    "{\"packages\":[{\"name\":\"laravel/framework\",\"version\":\"v11.5.0\"}]}");
            device.manifestChanged(List.of(lock));
            BgDrain.drain();
            flushEdt();
            assertThat(device.statusLcd.getText()).contains("NO artisan");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- HALO / AngularDevice ----------------

    @Test
    @DisplayName("HALO's pulse is guarded: an unchanged @angular/core does not re-probe")
    void haloVersionGuard() throws Exception {
        write("package.json", "{}"); // no @angular/core: installed stays null
        Rack rack = aimedRack();
        try {
            AngularDevice device = new AngularDevice();
            rack.addDevice(device);
            BgDrain.drain();
            flushEdt();

            javax.swing.SwingUtilities.invokeAndWait(
                    () -> device.statusLcd.setText("SENTINEL"));
            device.manifestChanged(List.of(projectDir.resolve("package.json")));
            BgDrain.drain();
            flushEdt();
            assertThat(device.statusLcd.getText())
                    .as("no version movement, no refresh").isEqualTo("SENTINEL");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- default hook ----------------

    @Test
    @DisplayName("manifestChanged is a no-op on devices that declare no interest")
    void defaultHookIsNoOp() {
        Rack rack = aimedRack();
        try {
            ConsoleDevice monitor = new ConsoleDevice();
            rack.addDevice(monitor);
            // fan the batch through the router: nothing throws, nothing changes
            rack.manifestChanged(List.of(projectDir.resolve("package.json")));
            settleAll(rack);
            assertThat(monitor.primaryManifest()).isEmpty();
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- primaryManifest / "Open manifest" ----------------

    @Test
    @DisplayName("manifest-backed devices expose their file for the Open-manifest action; absent file, empty")
    void primaryManifests() throws Exception {
        Rack rack = aimedRack();
        try {
            NpmScriptDevice npm = new NpmScriptDevice();
            rack.addDevice(npm);
            assertThat(npm.primaryManifest()).as("no package.json yet").isEmpty();

            write("package.json", "{\"scripts\":{}}");
            assertThat(npm.primaryManifest().orElseThrow().getName()).isEqualTo("package.json");

            write("Gruntfile.js", "module.exports = function(grunt) {};");
            DynamoDevice dynamo = new DynamoDevice();
            rack.addDevice(dynamo);
            assertThat(dynamo.primaryManifest().orElseThrow().getName()).isEqualTo("Gruntfile.js");

            PackageManagerDevice crate = new PackageManagerDevice();
            rack.addDevice(crate);
            assertThat(crate.primaryManifest().orElseThrow().getName()).isEqualTo("package.json");

            GovernorDevice governor = new GovernorDevice();
            rack.addDevice(governor);
            assertThat(governor.primaryManifest()).as("no snapshot yet").isEmpty();
            write(".gas-snapshot", "testDeposit() (gas: 31000)");
            assertThat(governor.primaryManifest().orElseThrow().getName()).isEqualTo(".gas-snapshot");

            BuildDevice forge = new BuildDevice();
            rack.addDevice(forge);
            // AUTO resolved to grunt (the Gruntfile above): its config file
            assertThat(forge.primaryManifest().orElseThrow().getName()).isEqualTo("Gruntfile.js");
            write("webpack.config.js", "module.exports = {};");
            assertThat(forge.primaryManifest().orElseThrow().getName()).isEqualTo("webpack.config.js");

            write("composer.json", "{}");
            ArtisanDevice artisan = new ArtisanDevice();
            rack.addDevice(artisan);
            assertThat(artisan.primaryManifest().orElseThrow().getName()).isEqualTo("composer.json");

            BgDrain.drain();
            flushEdt();
        } finally {
            rack.shutdown();
        }
    }
}
