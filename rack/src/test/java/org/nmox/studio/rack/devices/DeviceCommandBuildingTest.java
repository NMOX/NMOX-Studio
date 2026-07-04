package org.nmox.studio.rack.devices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The command devices' one job: turn a knob/switch/toolchain into the
 * exact argv they will hand a shell. This drives each device's public
 * knobs via applyState and reads back buildCommand() - the code path
 * behind every RUN button and CI export - without launching a thing.
 */
class DeviceCommandBuildingTest {

    @TempDir
    Path projectDir;

    /** A rack aimed at a project with the given manifest file present. */
    private Rack rackWith(String... manifests) throws IOException {
        for (String m : manifests) {
            Files.writeString(projectDir.resolve(m), "{}");
        }
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        return rack;
    }

    private <D extends RackDevice> D aim(Rack rack, D device, Map<String, String> state) {
        rack.addDevice(device);
        device.applyState(state);
        return device;
    }

    // ---------------- FORGE / BuildDevice ----------------

    @Test
    @DisplayName("FORGE explicit tools build the right npx invocation per knob and switch")
    void forgeExplicitTools() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            // TOOLS = {auto, vite, webpack, rollup, esbuild, parcel}
            BuildDevice vite = aim(rack, new BuildDevice(),
                    Map.of("tool", "1", "prod", "false", "watch", "true"));
            assertThat(vite.buildCommand())
                    .containsExactly("npx", "vite", "build", "--watch", "--mode", "development");

            BuildDevice webpackProd = aim(rack, new BuildDevice(),
                    Map.of("tool", "2", "prod", "true", "watch", "false"));
            assertThat(webpackProd.buildCommand())
                    .containsExactly("npx", "webpack", "--mode", "production");

            BuildDevice rollupWatch = aim(rack, new BuildDevice(),
                    Map.of("tool", "3", "watch", "true"));
            assertThat(rollupWatch.buildCommand())
                    .containsExactly("npx", "rollup", "-c", "--watch");

            BuildDevice esbuild = aim(rack, new BuildDevice(), Map.of("tool", "4"));
            assertThat(esbuild.buildCommand())
                    .containsExactly("npx", "esbuild", "--bundle", "src/index.js", "--outdir=dist");

            BuildDevice parcelWatch = aim(rack, new BuildDevice(),
                    Map.of("tool", "5", "watch", "true"));
            assertThat(parcelWatch.buildCommand()).containsExactly("npx", "parcel", "watch");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("FORGE AUTO uses the npm build script when the project declares one")
    void forgeAutoNpmScript() throws IOException {
        Rack rack = new Rack();
        try {
            Files.writeString(projectDir.resolve("package.json"),
                    "{\"scripts\":{\"build\":\"vite build\"}}");
            rack.setProjectDir(projectDir.toFile());
            BuildDevice build = aim(rack, new BuildDevice(), Map.of()); // tool=auto
            assertThat(build.buildCommand()).containsExactly("npm", "run", "build");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("FORGE AUTO speaks each non-Node toolchain, honoring the PROD switch")
    void forgeAutoNonNode() throws IOException {
        assertBuild("Cargo.toml", BuildDevice::new, Map.of("prod", "true"),
                "cargo", "build", "--release");
        assertBuild("Cargo.toml", BuildDevice::new, Map.of("prod", "false"),
                "cargo", "build");
        assertBuild("go.mod", BuildDevice::new, Map.of(), "go", "build", "./...");
        assertBuild("pom.xml", BuildDevice::new, Map.of(),
                "mvn", "-q", "package", "-DskipTests");
        assertBuild("mix.exs", BuildDevice::new, Map.of(), "mix", "compile");
    }

    // ---------------- VERITAS / TestDevice ----------------

    @Test
    @DisplayName("VERITAS builds the right runner per FRAMEWORK knob position")
    void veritasExplicitFrameworks() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            // FRAMEWORKS index: 1=jest, 2=vitest, 3=mocha, 6=pytest, 7=cargo, 8=go
            assertThat(aim(rack, new TestDevice(), Map.of("framework", "1")).buildCommand())
                    .containsExactly("npx", "jest");
            assertThat(aim(rack, new TestDevice(), Map.of("framework", "2")).buildCommand())
                    .containsExactly("npx", "vitest", "run");
            assertThat(aim(rack, new TestDevice(), Map.of("framework", "3")).buildCommand())
                    .containsExactly("npx", "mocha");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("VERITAS appends the coverage flag only for runners that carry one")
    void veritasCoverageFlag() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            assertThat(aim(rack, new TestDevice(),
                    Map.of("framework", "1", "coverage", "true")).buildCommand())
                    .containsExactly("npx", "jest", "--coverage");
            // mocha (index 3) has no portable coverage flag: left untouched
            assertThat(aim(rack, new TestDevice(),
                    Map.of("framework", "3", "coverage", "true")).buildCommand())
                    .containsExactly("npx", "mocha");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("VERITAS AUTO follows the detected toolchain")
    void veritasAuto() throws IOException {
        assertBuild("pyproject.toml", TestDevice::new, Map.of(), "python3", "-m", "pytest");
        assertBuild("Cargo.toml", TestDevice::new, Map.of(), "cargo", "test");
        assertBuild("go.mod", TestDevice::new, Map.of(), "go", "test", "./...");
    }

    @Test
    @DisplayName("VERITAS coverage floor: measured < floor fails; unmeasured never gates")
    void veritasCoverageGate() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            // COVERAGE_MINIMUMS index: 4 = "80"
            TestDevice t = aim(rack, new TestDevice(),
                    Map.of("coverage", "true", "covMin", "4"));
            assertThat(t.coverageMinimum()).isEqualTo(80);
            // exit non-zero always fails
            assertThat(t.overallSuccess(1)).isFalse();
            // clean exit with no measured coverage: passes (refuses to punish)
            assertThat(t.overallSuccess(0)).isTrue();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("VERITAS failure-name parsing across jest/pytest/cargo/go runners")
    void veritasFailureNames() {
        assertThat(TestDevice.failedTestName("  ✕ adds two numbers (12 ms)"))
                .isEqualTo("adds two numbers");
        assertThat(TestDevice.failedTestName("FAILED tests/test_api.py::test_login"))
                .isEqualTo("tests/test_api.py::test_login");
        assertThat(TestDevice.failedTestName("test math::adds ... FAILED"))
                .isEqualTo("math::adds");
        assertThat(TestDevice.failedTestName("--- FAIL: TestLogin"))
                .isEqualTo("TestLogin");
        assertThat(TestDevice.failedTestName("all good here")).isNull();
    }

    @Test
    @DisplayName("VERITAS coverage-percent parsing across istanbul/pytest-cov/go")
    void veritasCoveragePercent() {
        assertThat(TestDevice.coveragePercent("Lines        : 87.5% ( 700/800 )")).isEqualTo(87.5);
        assertThat(TestDevice.coveragePercent("TOTAL      1200    120    90%")).isEqualTo(90.0);
        assertThat(TestDevice.coveragePercent("coverage: 64.2% of statements")).isEqualTo(64.2);
        assertThat(TestDevice.coveragePercent("nothing here")).isEqualTo(-1);
    }

    @Test
    @DisplayName("VERITAS re-run-failed command targets exactly the named failures, per runner")
    void veritasRerunFailed() {
        List<String> names = List.of("adds", "subtracts");
        assertThat(TestDevice.rerunFailedCommand("jest", names))
                .containsExactly("npx", "jest", "-t", "adds|subtracts");
        assertThat(TestDevice.rerunFailedCommand("vitest", names))
                .containsExactly("npx", "vitest", "run", "-t", "adds|subtracts");
        assertThat(TestDevice.rerunFailedCommand("go", names))
                .containsExactly("go", "test", "./...", "-run", "adds|subtracts");
        assertThat(TestDevice.rerunFailedCommand("pytest", List.of("a::b")))
                .containsExactly("python3", "-m", "pytest", "a::b");
        assertThat(TestDevice.rerunFailedCommand("mocha", names))
                .as("no trustworthy name filter for mocha").isNull();
        assertThat(TestDevice.rerunFailedCommand("jest", List.of()))
                .as("nothing failing, nothing to re-run").isNull();
    }

    // ---------------- IGNITION / RunDevice ----------------

    @Test
    @DisplayName("IGNITION explicit TARGET builds the language runner; ARGS append")
    void ignitionExplicitTargets() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            // TARGETS index: 3=go, 4=rust
            assertThat(aim(rack, new RunDevice(),
                    Map.of("target", "3", "args", "--port 9000")).buildCommand())
                    .containsExactly("go", "run", ".", "--port", "9000");
            // rust inserts "--" before program args
            assertThat(aim(rack, new RunDevice(),
                    Map.of("target", "4", "args", "--verbose")).buildCommand())
                    .containsExactly("cargo", "run", "--", "--verbose");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("IGNITION php serves the public/ docroot when present, the project root otherwise")
    void ignitionPhpDocroot() throws IOException {
        Rack rack = rackWith("composer.json");
        try {
            // no public/ dir: built-in server from the project root
            RunDevice bare = aim(rack, new RunDevice(), Map.of()); // target=auto → php
            assertThat(bare.buildCommand())
                    .containsExactly("php", "-S", "127.0.0.1:8000");

            // composer-era layout: serve the public/ docroot
            Files.createDirectory(projectDir.resolve("public"));
            // TARGETS index 19 = php (explicit knob, same command as auto)
            RunDevice docroot = aim(rack, new RunDevice(), Map.of("target", "19"));
            assertThat(docroot.buildCommand())
                    .containsExactly("php", "-S", "127.0.0.1:8000", "-t", "public");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("IGNITION AUTO runs npm start when a start script exists, node index otherwise")
    void ignitionAutoNode() throws IOException {
        Rack withStart = new Rack();
        try {
            Files.writeString(projectDir.resolve("package.json"),
                    "{\"scripts\":{\"start\":\"node server.js\"}}");
            withStart.setProjectDir(projectDir.toFile());
            assertThat(aim(withStart, new RunDevice(), Map.of()).buildCommand())
                    .containsExactly("npm", "start");
        } finally {
            withStart.shutdown();
        }
    }

    // ---------------- CRATE / PackageManagerDevice ----------------

    @Test
    @DisplayName("CRATE INSTALL speaks each toolchain in AUTO")
    void crateInstallAuto() throws IOException {
        assertBuild("Cargo.toml", PackageManagerDevice::new, Map.of(), "cargo", "fetch");
        assertBuild("go.mod", PackageManagerDevice::new, Map.of(), "go", "mod", "download");
        assertBuild("Gemfile", PackageManagerDevice::new, Map.of(), "bundle", "install");
        assertBuild("composer.json", PackageManagerDevice::new, Map.of(), "composer", "install");
    }

    @Test
    @DisplayName("CRATE explicit ENGINE always talks Node with the chosen manager")
    void crateExplicitManager() throws IOException {
        Rack rack = rackWith("Cargo.toml", "package.json");
        try {
            // MANAGERS = {auto, npm, yarn, pnpm}; index 2 = yarn
            assertThat(aim(rack, new PackageManagerDevice(), Map.of("manager", "2")).buildCommand())
                    .containsExactly("yarn", "install");
            assertThat(aim(rack, new PackageManagerDevice(), Map.of("manager", "3")).buildCommand())
                    .containsExactly("pnpm", "install");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- INSPECTOR / DebugDevice ----------------

    @Test
    @DisplayName("INSPECTOR builds the language debugger per TARGET")
    void inspectorTargets() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            // TARGETS = {auto, node, python, go, maven, ruby, php}; index 2 = python, 3 = go
            assertThat(aim(rack, new DebugDevice(), Map.of("target", "2")).buildCommand())
                    .startsWith("python3", "-m", "debugpy", "--listen", "5678");
            assertThat(aim(rack, new DebugDevice(), Map.of("target", "3")).buildCommand())
                    .containsExactly("dlv", "debug", "--headless",
                            "--listen=:2345", "--api-version=2", "--accept-multiclient");
            assertThat(aim(rack, new DebugDevice(), Map.of("target", "1")).buildCommand())
                    .startsWith("node", "--inspect=9229");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- PURITY / LintDevice ----------------

    @Test
    @DisplayName("PURITY picks eslint or stylelint and appends --fix when FIX is on")
    void purityLinters() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            // LINTERS = {eslint, stylelint}
            assertThat(aim(rack, new LintDevice(), Map.of("linter", "0", "fix", "false")).buildCommand())
                    .containsExactly("npx", "eslint", ".");
            assertThat(aim(rack, new LintDevice(), Map.of("linter", "0", "fix", "true")).buildCommand())
                    .containsExactly("npx", "eslint", ".", "--fix");
            assertThat(aim(rack, new LintDevice(), Map.of("linter", "1", "fix", "true")).buildCommand())
                    .containsExactly("npx", "stylelint", "**/*.css", "--fix");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- TYPEGUARD / TypecheckDevice ----------------

    @Test
    @DisplayName("TYPEGUARD assembles tsc flags from STRICT and WATCH")
    void typeguardFlags() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            assertThat(aim(rack, new TypecheckDevice(), Map.of()).buildCommand())
                    .containsExactly("npx", "tsc", "--noEmit", "--pretty", "false");
            assertThat(aim(rack, new TypecheckDevice(),
                    Map.of("strict", "true", "watch", "true")).buildCommand())
                    .containsExactly("npx", "tsc", "--noEmit", "--pretty", "false", "--strict", "--watch");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- GLOSS / FormatDevice ----------------

    @Test
    @DisplayName("GLOSS writes in WRITE mode and only checks in CHECK mode")
    void glossWriteVsCheck() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            // write switch defaults on
            assertThat(aim(rack, new FormatDevice(), Map.of()).buildCommand())
                    .containsExactly("npx", "prettier", "--write", ".");
            assertThat(aim(rack, new FormatDevice(), Map.of("write", "false")).buildCommand())
                    .containsExactly("npx", "prettier", "--check", ".");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("GLOSS PHP lane runs Laravel Pint, honoring WRITE vs CHECK")
    void glossPhpLane() throws IOException {
        assertBuild("composer.json", FormatDevice::new, Map.of(),
                "vendor/bin/pint");
        assertBuild("composer.json", FormatDevice::new, Map.of("write", "false"),
                "vendor/bin/pint", "--test");
    }

    @Test
    @DisplayName("TYPEGUARD PHP lane runs phpstan raw; the tsc lane stays byte-identical")
    void typeguardPhpLane() throws IOException {
        assertBuild("composer.json", TypecheckDevice::new, Map.of(),
                "vendor/bin/phpstan", "analyse", "--no-progress", "--error-format=raw");
        // the JS/TS lane is pinned exactly as before
        assertBuild("package.json", TypecheckDevice::new, Map.of(),
                "npx", "tsc", "--noEmit", "--pretty", "false");
    }

    // ---------------- ARTISAN / ArtisanDevice ----------------

    @Test
    @DisplayName("ARTISAN builds one real artisan command per ACTION knob position")
    void artisanActions() throws IOException {
        Rack rack = rackWith("composer.json");
        try {
            // ACTIONS = {serve, test, migrate, fresh, queue, routes}
            assertThat(aim(rack, new ArtisanDevice(), Map.of("action", "0")).buildCommand())
                    .containsExactly("php", "artisan", "serve");
            assertThat(aim(rack, new ArtisanDevice(), Map.of("action", "1")).buildCommand())
                    .containsExactly("php", "artisan", "test");
            assertThat(aim(rack, new ArtisanDevice(), Map.of("action", "2")).buildCommand())
                    .containsExactly("php", "artisan", "migrate");
            assertThat(aim(rack, new ArtisanDevice(), Map.of("action", "3")).buildCommand())
                    .containsExactly("php", "artisan", "migrate:fresh", "--seed");
            assertThat(aim(rack, new ArtisanDevice(), Map.of("action", "4")).buildCommand())
                    .containsExactly("php", "artisan", "queue:work");
            assertThat(aim(rack, new ArtisanDevice(), Map.of("action", "5")).buildCommand())
                    .containsExactly("php", "artisan", "route:list");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- SENTRY / AuditDevice ----------------

    @Test
    @DisplayName("SENTRY always audits as JSON")
    void sentryAudit() throws IOException {
        assertBuild("package.json", AuditDevice::new, Map.of(), "npm", "audit", "--json");
    }

    // ---------------- WORMHOLE / TunnelDevice ----------------

    @Test
    @DisplayName("WORMHOLE builds the tunnel argv per provider and port")
    void wormholeProviders() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            // PROVIDERS = {cloudflared, ngrok, localtunnel}; PORTS index 0 = 3000
            assertThat(aim(rack, new TunnelDevice(),
                    Map.of("provider", "0", "port", "0")).buildCommand())
                    .containsExactly("cloudflared", "tunnel", "--url", "http://localhost:3000");
            assertThat(aim(rack, new TunnelDevice(),
                    Map.of("provider", "1", "port", "0")).buildCommand())
                    .containsExactly("ngrok", "http", "3000", "--log", "stdout");
            assertThat(aim(rack, new TunnelDevice(),
                    Map.of("provider", "2", "port", "0")).buildCommand())
                    .containsExactly("npx", "localtunnel", "--port", "3000");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- SURGE / DevServerDevice ----------------

    @Test
    @DisplayName("SURGE explicit server knob picks the serving tool and port")
    void surgeServers() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            // SERVERS = {auto, vite, http-server, serve}; PORTS index 0 = 3000
            assertThat(aim(rack, new DevServerDevice(),
                    Map.of("server", "1", "port", "0")).buildCommand())
                    .containsExactly("npx", "vite", "--port", "3000");
            assertThat(aim(rack, new DevServerDevice(),
                    Map.of("server", "2", "port", "0")).buildCommand())
                    .containsExactly("npx", "http-server", "-p", "3000");
            assertThat(aim(rack, new DevServerDevice(),
                    Map.of("server", "3", "port", "0")).buildCommand())
                    .containsExactly("npx", "serve", "-l", "3000");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- NPM-9000 / NpmScriptDevice ----------------

    @Test
    @DisplayName("NPM-9000 returns null with no script and manager/run when one is picked")
    void npmScriptNullThenManager() throws IOException {
        // placeholder "—" script → nothing to run. Deterministic: a fresh,
        // unattached device holds only the placeholder in its SCRIPT knob.
        NpmScriptDevice fresh = new NpmScriptDevice();
        assertThat(fresh.buildCommand()).isNull();
        fresh.dispose();

        Rack rack = new Rack();
        try {
            Files.writeString(projectDir.resolve("package.json"),
                    "{\"scripts\":{\"dev\":\"vite\",\"build\":\"vite build\"}}");
            rack.setProjectDir(projectDir.toFile());
            NpmScriptDevice dev = new NpmScriptDevice();
            rack.addDevice(dev);
            // reloadScripts() posts setOptions to the EDT; drain it before
            // selecting so "build" is a real option. Without the flush the test
            // races the EDT — a dev box wins it, a loaded CI runner loses and
            // reads back null.
            flushEdt();

            // once a real script is selected it runs via the chosen manager
            // (MANAGERS is a fixed {npm,yarn,pnpm}, so index 1 is always yarn —
            // no dependence on which managers are installed on the box)
            dev.applyState(Map.of("script", "build", "manager", "1"));
            flushEdt();
            assertThat(dev.buildCommand()).containsExactly("yarn", "run", "build");
        } finally {
            rack.shutdown();
        }
    }

    private static void flushEdt() {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ex) {
            throw new AssertionError("EDT flush interrupted", ex);
        }
    }

    // ---------------- GAUNTLET / BenchDevice ----------------

    @Test
    @DisplayName("GAUNTLET autocannon argv from duration/connections/url")
    void gauntletBench() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            // duration index 1 = "10s", connections index 2 = "100"
            BenchDevice bench = aim(rack, new BenchDevice(),
                    Map.of("duration", "1", "connections", "2", "url", "http://localhost:8080"));
            assertThat(bench.buildCommand())
                    .containsExactly("npx", "autocannon", "-d", "10", "-c", "100",
                            "http://localhost:8080");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- VITALS ----------------

    @Test
    @DisplayName("VITALS builds a headless lighthouse run, null when the URL is blank")
    void vitalsBuild() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            VitalsDevice v = aim(rack, new VitalsDevice(), Map.of("url", "https://example.com"));
            assertThat(v.buildCommand())
                    .containsExactly("npx", "lighthouse", "https://example.com",
                            "--output=json", "--output-path=stdout", "--quiet",
                            "--chrome-flags=--headless --no-sandbox");
            VitalsDevice blank = aim(rack, new VitalsDevice(), Map.of("url", ""));
            assertThat(blank.buildCommand()).isNull();
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- SOLDER / CommandLineDevice ----------------

    @Test
    @DisplayName("SOLDER runs exactly the typed argv, null when empty")
    void solderArgv() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            CommandLineDevice solder = aim(rack, new CommandLineDevice(),
                    Map.of("command", "make seed-db"));
            assertThat(solder.buildCommand()).containsExactly("make", "seed-db");
            CommandLineDevice quoted = aim(rack, new CommandLineDevice(),
                    Map.of("command", "echo 'hello world'"));
            assertThat(quoted.buildCommand()).containsExactly("echo", "hello world");
            CommandLineDevice empty = aim(rack, new CommandLineDevice(), Map.of("command", "   "));
            assertThat(empty.buildCommand()).isNull();
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- NEPTUNE / DatabaseDevice ----------------

    @Test
    @DisplayName("NEPTUNE ping builds the client per DB TYPE and connection target")
    void neptunePing() throws IOException {
        Rack rack = rackWith("package.json");
        try {
            // DB_TYPES = {Postgres, MySQL, SQLite, Prisma, Ecto, Django}
            DatabaseDevice pg = aim(rack, new DatabaseDevice(),
                    Map.of("dbType", "0", "conn", "appdb"));
            // ping is the default lastAction
            assertThat(pg.buildCommand()).containsExactly("psql", "-d", "appdb", "-c", "SELECT 1;");

            DatabaseDevice sqliteMem = aim(rack, new DatabaseDevice(),
                    Map.of("dbType", "2", "conn", ""));
            assertThat(sqliteMem.buildCommand())
                    .containsExactly("sqlite3", ":memory:", "SELECT 1;");

            DatabaseDevice prisma = aim(rack, new DatabaseDevice(), Map.of("dbType", "3"));
            assertThat(prisma.buildCommand()).containsExactly("npx", "prisma", "validate");
        } finally {
            rack.shutdown();
        }
    }

    // ---------------- shared assertion helper ----------------

    /** Aim a fresh rack at a project with one manifest, apply state, assert argv. */
    private void assertBuild(String manifest,
            java.util.function.Supplier<? extends CommandDevice> factory,
            Map<String, String> state, String... expected) throws IOException {
        Path dir = Files.createTempDirectory("nmox-cmd-");
        try {
            Files.writeString(dir.resolve(manifest), "{}");
            Rack rack = new Rack();
            rack.setProjectDir(dir.toFile());
            CommandDevice device = factory.get();
            rack.addDevice(device);
            device.applyState(state);
            assertThat(device.buildCommand()).as(manifest + " " + state).containsExactly(expected);
            rack.shutdown();
        } finally {
            deleteTree(dir);
        }
    }

    private static void deleteTree(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }
}
