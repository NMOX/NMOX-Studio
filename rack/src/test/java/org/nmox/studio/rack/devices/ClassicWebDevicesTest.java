package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
 * The classic web lane, end to end below the process boundary: the new
 * ProjectKinds and their precedence, FORGE's config-file lanes, CRATE's
 * bower sequencing, IGNITION's static serve with its URL/READY
 * announcements, and DYNAMO's statically-parsed TASK knob. No test
 * here spawns grunt, bower or python - commands are built and lines fed
 * to the parsing hooks, the idiom of the device behavior suites.
 */
class ClassicWebDevicesTest {

    @TempDir
    Path dir;

    /** Captures every signal that arrives on any of its input jacks. */
    private static final class Probe extends RackDevice {
        final ConcurrentLinkedQueue<Signal> received = new ConcurrentLinkedQueue<>();

        Probe() {
            super("probe", "PROBE", "TEST PROBE", new Color(0, 0, 0), 1);
            addInPort("in", "IN", SignalType.DATA);
            addInPort("trig", "TRIG", SignalType.TRIGGER);
        }

        @Override
        public void receive(Port in, Signal signal) {
            received.add(signal);
        }
    }

    // Drain both async paths before asserting: the EDT and the rack's
    // single-threaded signal router.
    private static void settle(Rack rack) {
        // Drain in dependency order: device background work first (a
        // knob-change listener can fire offEdt — e.g. DYNAMO's RUNNER
        // knob triggering an async task reload), THEN the EDT posts that
        // work makes (setOptions), THEN the router. awaitRouterIdle alone
        // left the DEVICE_BG reload racing the assertion on loaded CI.
        org.nmox.studio.rack.model.RackDevice.awaitDeviceBgIdle();
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
            // interrupted / already on EDT — not relevant to the assertion
        }
        rack.awaitRouterIdle();
    }

    private Rack aimedRack() {
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        return rack;
    }

    /** The live-test fixture's shape: Gruntfile + bower.json + index.html, no package.json. */
    private void scaffoldClassicDemo() throws IOException {
        Files.writeString(dir.resolve("Gruntfile.js"), """
                module.exports = function (grunt) {
                  grunt.loadNpmTasks('grunt-contrib-uglify');
                  grunt.loadNpmTasks('grunt-contrib-watch');
                  grunt.registerTask('default', ['uglify']);
                  grunt.registerTask('build', ['uglify']);
                };
                """);
        Files.writeString(dir.resolve("bower.json"),
                "{\"name\":\"site\",\"dependencies\":{\"jquery\":\"~1.12.4\"}}");
        Files.writeString(dir.resolve("index.html"), "<html><body>hi</body></html>");
    }

    // ---------------- the new ProjectKinds ----------------

    @Test
    @DisplayName("Kind precedence: bower-only → BOWER, index-only → STATIC, package.json wins over Gruntfile")
    void kindPrecedence() throws IOException {
        Files.writeString(dir.resolve("bower.json"), "{}");
        assertThat(ProjectInspector.detectKind(dir.toFile()))
                .isEqualTo(ProjectInspector.ProjectKind.BOWER);

        Path staticSite = Files.createDirectory(dir.resolve("static-site"));
        Files.writeString(staticSite.resolve("index.html"), "<html></html>");
        assertThat(ProjectInspector.detectKind(staticSite.toFile()))
                .isEqualTo(ProjectInspector.ProjectKind.STATIC);
        assertThat(ProjectInspector.hasProjectManifest(staticSite.toFile()))
                .as("a bare index.html dir passes the launch guard").isTrue();

        Path node = Files.createDirectory(dir.resolve("node-legacy"));
        Files.writeString(node.resolve("package.json"), "{}");
        Files.writeString(node.resolve("Gruntfile.js"), "");
        assertThat(ProjectInspector.detectKind(node.toFile()))
                .as("a legacy project WITH package.json keeps NODE primary")
                .isEqualTo(ProjectInspector.ProjectKind.NODE);
        assertThat(ProjectInspector.detectKinds(node.toFile()).keySet())
                .containsExactly(ProjectInspector.ProjectKind.NODE,
                        ProjectInspector.ProjectKind.GRUNT);
    }

    @Test
    @DisplayName("Every new manifest spelling detects: webpack .cjs/.mjs, gulpfile variants, Gruntfile.coffee")
    void manifestSpellings() throws IOException {
        Path webpack = Files.createDirectory(dir.resolve("wp"));
        Files.writeString(webpack.resolve("webpack.config.cjs"), "");
        assertThat(ProjectInspector.detectKind(webpack.toFile()))
                .isEqualTo(ProjectInspector.ProjectKind.WEBPACK);

        Path gulp = Files.createDirectory(dir.resolve("gp"));
        Files.writeString(gulp.resolve("gulpfile.babel.js"), "");
        assertThat(ProjectInspector.detectKind(gulp.toFile()))
                .isEqualTo(ProjectInspector.ProjectKind.GULP);

        Path coffee = Files.createDirectory(dir.resolve("cf"));
        Files.writeString(coffee.resolve("Gruntfile.coffee"), "");
        assertThat(ProjectInspector.detectKind(coffee.toFile()))
                .isEqualTo(ProjectInspector.ProjectKind.GRUNT);
    }

    @Test
    @DisplayName("STATIC is a true last resort: suppressed by any manifest, root-only, never from subdirs")
    void staticIsLastResort() throws IOException {
        // a Vite-style app: root index.html BESIDE package.json → no STATIC
        Files.writeString(dir.resolve("package.json"), "{}");
        Files.writeString(dir.resolve("index.html"), "<html></html>");
        assertThat(ProjectInspector.detectKinds(dir.toFile()).keySet())
                .containsExactly(ProjectInspector.ProjectKind.NODE);

        // an index.html one level down never promotes the parent
        Path parent = Files.createDirectory(dir.resolve("docs-holder"));
        Path sub = Files.createDirectory(parent.resolve("docs"));
        Files.writeString(sub.resolve("index.html"), "<html></html>");
        assertThat(ProjectInspector.detectKinds(parent.toFile())).isEmpty();
        assertThat(ProjectInspector.detectKind(parent.toFile()))
                .isEqualTo(ProjectInspector.ProjectKind.NONE);
    }

    @Test
    @DisplayName("The classic-demo fixture shape detects GRUNT primary with BOWER beside it")
    void classicDemoKinds() throws IOException {
        scaffoldClassicDemo();
        assertThat(ProjectInspector.detectKinds(dir.toFile()).keySet())
                .containsExactly(ProjectInspector.ProjectKind.GRUNT,
                        ProjectInspector.ProjectKind.BOWER);
    }

    // ---------------- FORGE: config-file lanes ----------------

    @Test
    @DisplayName("FORGE AUTO reads config files after the dep scan: webpack.config, Gruntfile, gulpfile")
    void forgeConfigFileLanes() throws IOException {
        // webpack.config.js only, no package.json → WEBPACK kind → webpack lane
        Files.writeString(dir.resolve("webpack.config.js"), "module.exports = {};");
        Rack rack = aimedRack();
        try {
            BuildDevice forge = new BuildDevice();
            rack.addDevice(forge);
            assertThat(forge.buildCommand())
                    .containsExactly("npx", "webpack", "--mode", "production");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("A Node project with a Gruntfile and no build script builds via grunt, watch as a task")
    void forgeGruntLane() throws IOException {
        Files.writeString(dir.resolve("package.json"), "{\"dependencies\":{}}");
        Files.writeString(dir.resolve("Gruntfile.js"), "");
        Rack rack = aimedRack();
        try {
            BuildDevice forge = new BuildDevice();
            rack.addDevice(forge);
            assertThat(forge.buildCommand()).containsExactly("npx", "grunt");

            forge.applyState(Map.of("watch", "true"));
            assertThat(forge.buildCommand())
                    .as("the classic runners take watch as a task, not a flag")
                    .containsExactly("npx", "grunt", "watch");

            // a declared bundler dep still outranks the config file
            Files.writeString(dir.resolve("package.json"),
                    "{\"dependencies\":{\"vite\":\"^5.0.0\"}}");
            // ProjectInspector caches package.json by mtime (ms); a rewrite
            // within the same tick would read stale — bump it explicitly
            Files.setLastModifiedTime(dir.resolve("package.json"),
                    java.nio.file.attribute.FileTime.fromMillis(
                            System.currentTimeMillis() + 2_000));
            forge.applyState(Map.of("watch", "false"));
            assertThat(forge.buildCommand()).startsWith("npx", "vite", "build");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("The TOOLS knob gained grunt and gulp positions at the end, dialable directly")
    void forgeKnobPositions() throws IOException {
        Files.writeString(dir.resolve("gulpfile.js"), "");
        Rack rack = aimedRack();
        try {
            BuildDevice auto = new BuildDevice();
            rack.addDevice(auto);
            assertThat(auto.buildCommand()).as("gulpfile-only project in AUTO")
                    .containsExactly("npx", "gulp");

            BuildDevice dialed = new BuildDevice();
            rack.addDevice(dialed);
            dialed.applyState(Map.of("tool", "6")); // grunt, appended index
            assertThat(dialed.buildCommand()).containsExactly("npx", "grunt");
            dialed.applyState(Map.of("tool", "7", "watch", "true")); // gulp
            assertThat(dialed.buildCommand()).containsExactly("npx", "gulp", "watch");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- CRATE: bower sequencing ----------------

    @Test
    @DisplayName("CRATE sequences bower install after npm's when both manifests exist")
    void crateBowerAfterNpm() throws IOException {
        Files.writeString(dir.resolve("package.json"), "{}");
        Files.writeString(dir.resolve("bower.json"), "{}");
        Rack rack = aimedRack();
        try {
            PackageManagerDevice crate = new PackageManagerDevice();
            rack.addDevice(crate);
            var steps = crate.installSteps();
            assertThat(steps).hasSize(2);
            assertThat(steps.get(0).command()).containsExactly("npm", "install");
            assertThat(steps.get(1).command()).containsExactly("npx", "bower", "install");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("On the classic-demo shape, GRUNT contributes no install step — only bower installs")
    void crateSkipsUninstallableKinds() throws IOException {
        scaffoldClassicDemo();
        Rack rack = aimedRack();
        try {
            PackageManagerDevice crate = new PackageManagerDevice();
            rack.addDevice(crate);
            var steps = crate.installSteps();
            assertThat(steps).hasSize(1);
            assertThat(steps.get(0).command()).containsExactly("npx", "bower", "install");
            assertThat(steps.get(0).dir()).isEqualTo(dir.toFile());
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("A bower-primary project installs and updates through npx bower")
    void crateBowerVerbs() throws IOException {
        Files.writeString(dir.resolve("bower.json"), "{}");
        Rack rack = aimedRack();
        try {
            PackageManagerDevice crate = new PackageManagerDevice();
            rack.addDevice(crate);
            assertThat(crate.buildCommand()).containsExactly("npx", "bower", "install");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- IGNITION: the static lane ----------------

    @Test
    @DisplayName("IGNITION AUTO serves a bare index.html dir with python3 http.server on 8000")
    void ignitionStaticAuto() throws IOException {
        Files.writeString(dir.resolve("index.html"), "<html></html>");
        Rack rack = aimedRack();
        try {
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            // -u, not cosmetic: without it python block-buffers the
            // "Serving HTTP on" banner and the lane never announces
            // itself (no READY, no URL, no serving chip). See
            // ServingDevicesTest#ignitionStaticLaneIsUnbuffered.
            assertThat(run.buildCommand())
                    .containsExactly("python3", "-u", "-m", "http.server", "8000");
            assertThat(run.commandDir()).isEqualTo(dir.toFile());
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("IGNITION AUTO on the classic-demo (GRUNT primary) still serves the folder")
    void ignitionServesClassicDemo() throws IOException {
        scaffoldClassicDemo();
        Rack rack = aimedRack();
        try {
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            // -u, not cosmetic: without it python block-buffers the
            // "Serving HTTP on" banner and the lane never announces
            // itself (no READY, no URL, no serving chip). See
            // ServingDevicesTest#ignitionStaticLaneIsUnbuffered.
            assertThat(run.buildCommand())
                    .containsExactly("python3", "-u", "-m", "http.server", "8000");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("The static TARGET is dialable at the appended knob position")
    void ignitionStaticDialed() throws IOException {
        Files.writeString(dir.resolve("package.json"), "{}");
        Rack rack = aimedRack();
        try {
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            run.applyState(Map.of("target", "23")); // static, appended index
            // -u, not cosmetic: without it python block-buffers the
            // "Serving HTTP on" banner and the lane never announces
            // itself (no READY, no URL, no serving chip). See
            // ServingDevicesTest#ignitionStaticLaneIsUnbuffered.
            assertThat(run.buildCommand())
                    .containsExactly("python3", "-u", "-m", "http.server", "8000");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("IGNITION AUTO on a webpack-config-only project runs the dev server the IDE's Run maps to")
    void ignitionWebpackServe() throws IOException {
        Files.writeString(dir.resolve("webpack.config.js"), "module.exports = {};");
        Rack rack = aimedRack();
        try {
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            assertThat(run.buildCommand())
                    .containsExactly("npx", "webpack", "serve", "--mode", "development");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("The Serving-HTTP banner fires READY once and puts the URL on the jack")
    void ignitionAnnouncesServe() throws IOException {
        Files.writeString(dir.resolve("index.html"), "<html></html>");
        Rack rack = aimedRack();
        try {
            RunDevice run = new RunDevice();
            Probe url = new Probe();
            Probe ready = new Probe();
            rack.addDevice(run);
            rack.addDevice(url);
            rack.addDevice(ready);
            rack.connect(run.getPort("url"), url.getPort("in"));
            rack.connect(run.getPort("ready"), ready.getPort("trig"));

            run.onLine("Serving HTTP on :: port 8000 (http://[::]:8000/) ...");
            run.onLine("Serving HTTP on :: port 8000 (http://[::]:8000/) ...");
            settle(rack);

            assertThat(url.received).extracting(Signal::payload)
                    .contains("http://localhost:8000");
            assertThat(ready.received).as("READY fires once, not per repeat").hasSize(1);
            assertThat(run.statusLcd.getText()).contains("SERVING").contains("8000");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- DYNAMO: the task runner ----------------

    @Test
    @DisplayName("DYNAMO lists the Gruntfile's tasks on the TASK knob and runs the dialed one via npx")
    void dynamoGruntTasks() throws IOException {
        scaffoldClassicDemo();
        Rack rack = aimedRack();
        try {
            DynamoDevice dynamo = new DynamoDevice();
            rack.addDevice(dynamo);
            dynamo.reloadTasksNow();
            settle(rack);

            assertThat(dynamo.statusLcd.getText()).isEqualTo("4 TASKS — Gruntfile.js");
            dynamo.applyState(Map.of("task", "build"));
            assertThat(dynamo.buildCommand()).containsExactly("npx", "grunt", "build");
            assertThat(dynamo.commandDir()).isEqualTo(dir.toFile());
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("DYNAMO on a gulpfile lists the v4 exports and speaks gulp")
    void dynamoGulpTasks() throws IOException {
        Files.writeString(dir.resolve("gulpfile.js"), """
                const { series } = require('gulp');
                function clean(cb) { cb(); }
                exports.clean = clean;
                exports.build = series(clean);
                """);
        Rack rack = aimedRack();
        try {
            DynamoDevice dynamo = new DynamoDevice();
            rack.addDevice(dynamo);
            dynamo.reloadTasksNow();
            settle(rack);

            assertThat(dynamo.statusLcd.getText()).isEqualTo("2 TASKS — gulpfile.js");
            dynamo.applyState(Map.of("task", "build"));
            assertThat(dynamo.buildCommand()).containsExactly("npx", "gulp", "build");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("DYNAMO with no taskfile keeps the placeholder and builds no command")
    void dynamoPlaceholderIsHonest() throws IOException {
        Files.writeString(dir.resolve("package.json"), "{}");
        Rack rack = aimedRack();
        try {
            DynamoDevice dynamo = new DynamoDevice();
            rack.addDevice(dynamo);
            dynamo.reloadTasksNow();
            settle(rack);

            assertThat(dynamo.buildCommand()).isNull();
            assertThat(dynamo.statusLcd.getText()).isEqualTo("NO GRUNTFILE OR GULPFILE");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("When a repo carries both taskfiles, AUTO prefers grunt and RUNNER dials gulp")
    void dynamoRunnerKnobSettlesBoth() throws IOException {
        Files.writeString(dir.resolve("Gruntfile.js"),
                "grunt.registerTask('default', []);");
        Files.writeString(dir.resolve("gulpfile.js"),
                "exports.styles = () => {};");
        Rack rack = aimedRack();
        try {
            DynamoDevice dynamo = new DynamoDevice();
            rack.addDevice(dynamo);
            dynamo.reloadTasksNow();
            settle(rack);
            dynamo.applyState(Map.of("task", "default"));
            assertThat(dynamo.buildCommand()).as("AUTO prefers the Gruntfile")
                    .containsExactly("npx", "grunt", "default");

            dynamo.applyState(Map.of("runner", "2")); // RUNNERS = {auto, grunt, gulp}
            settle(rack); // let the RUNNER knob apply on the EDT before the reload
                          // reads effectiveRunner — else a loaded runner reparses
                          // the stale (grunt) runner and keeps grunt's tasks
            dynamo.reloadTasksNow();
            settle(rack);
            dynamo.applyState(Map.of("task", "styles"));
            settle(rack);
            assertThat(dynamo.buildCommand()).containsExactly("npx", "gulp", "styles");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("DYNAMO finds the taskfile lane in a monorepo and runs from its directory")
    void dynamoMonorepoLane() throws IOException {
        Path site = Files.createDirectory(dir.resolve("site"));
        Files.writeString(site.resolve("Gruntfile.js"),
                "grunt.registerTask('default', []);");
        Rack rack = aimedRack();
        try {
            DynamoDevice dynamo = new DynamoDevice();
            rack.addDevice(dynamo);
            dynamo.reloadTasksNow();
            settle(rack);
            dynamo.applyState(Map.of("task", "default"));
            assertThat(dynamo.buildCommand()).containsExactly("npx", "grunt", "default");
            assertThat(dynamo.commandDir()).isEqualTo(site.toFile());
        } finally {
            rack.shutdown();
        }
    }
}
