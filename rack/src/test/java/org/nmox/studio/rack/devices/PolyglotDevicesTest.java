package org.nmox.studio.rack.devices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The polyglot promise: aim the rack at a Rust/Go/Python/Ruby/Java
 * project and the devices speak that toolchain without touching a knob.
 */
class PolyglotDevicesTest {

    @TempDir
    Path projectDir;

    private Rack rackAimedAt(String manifest) throws IOException {
        Files.writeString(projectDir.resolve(manifest), "# test manifest");
        Rack rack = new Rack();
        rack.setProjectDir(projectDir.toFile());
        return rack;
    }

    @Test
    @DisplayName("ProjectKind detects every supported toolchain by manifest")
    void detectsKinds() throws IOException {
        assertThat(ProjectInspector.detectKind(projectDir.toFile()))
                .isEqualTo(ProjectInspector.ProjectKind.NONE);
        Files.writeString(projectDir.resolve("Cargo.toml"), "[package]");
        assertThat(ProjectInspector.detectKind(projectDir.toFile()))
                .isEqualTo(ProjectInspector.ProjectKind.RUST);
        // Node wins over Rust when both exist (precedence order)
        Files.writeString(projectDir.resolve("package.json"), "{}");
        assertThat(ProjectInspector.detectKind(projectDir.toFile()))
                .isEqualTo(ProjectInspector.ProjectKind.NODE);
    }

    @Test
    @DisplayName("IGNITION AUTO runs cargo for Rust projects")
    void ignitionRust() throws IOException {
        Rack rack = rackAimedAt("Cargo.toml");
        RunDevice run = new RunDevice();
        rack.addDevice(run);
        assertThat(run.buildCommand()).startsWith("cargo", "run");
        rack.shutdown();
    }

    @Test
    @DisplayName("IGNITION AUTO runs go for Go projects, with args appended")
    void ignitionGo() throws IOException {
        Rack rack = rackAimedAt("go.mod");
        RunDevice run = new RunDevice();
        run.applyState(java.util.Map.of("args", "--port 9000"));
        rack.addDevice(run);
        assertThat(run.buildCommand()).containsExactly("go", "run", ".", "--port", "9000");
        rack.shutdown();
    }

    @Test
    @DisplayName("VERITAS AUTO runs pytest for Python projects")
    void veritasPython() throws IOException {
        Rack rack = rackAimedAt("pyproject.toml");
        TestDevice test = new TestDevice();
        rack.addDevice(test);
        assertThat(test.buildCommand()).startsWith("python3", "-m", "pytest");
        rack.shutdown();
    }

    @Test
    @DisplayName("FORGE AUTO builds with maven for pom.xml projects")
    void forgeMaven() throws IOException {
        Rack rack = rackAimedAt("pom.xml");
        BuildDevice build = new BuildDevice();
        rack.addDevice(build);
        assertThat(build.buildCommand()).startsWith("mvn");
        rack.shutdown();
    }

    @Test
    @DisplayName("CRATE AUTO installs with bundler for Gemfile projects")
    void crateRuby() throws IOException {
        Rack rack = rackAimedAt("Gemfile");
        PackageManagerDevice deps = new PackageManagerDevice();
        rack.addDevice(deps);
        assertThat(deps.buildCommand()).containsExactly("bundle", "install");
        rack.shutdown();
    }

    @Test
    @DisplayName("INSPECTOR AUTO picks debugpy for Python projects")
    void inspectorPython() throws IOException {
        Rack rack = rackAimedAt("requirements.txt");
        DebugDevice debug = new DebugDevice();
        rack.addDevice(debug);
        List<String> cmd = debug.buildCommand();
        assertThat(cmd).startsWith("python3", "-m", "debugpy");
        assertThat(cmd).contains("--listen", "5678");
        rack.shutdown();
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("Web-capable toolchains detect from their manifests")
    void webLanguageKindsDetect(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        record Case(String file, ProjectInspector.ProjectKind kind) { }
        var cases = java.util.List.of(
                new Case("pubspec.yaml", ProjectInspector.ProjectKind.DART),
                new Case("build.sbt", ProjectInspector.ProjectKind.SCALA),
                new Case("stack.yaml", ProjectInspector.ProjectKind.HASKELL),
                new Case("build.zig", ProjectInspector.ProjectKind.ZIG),
                new Case("gleam.toml", ProjectInspector.ProjectKind.GLEAM),
                new Case("Project.toml", ProjectInspector.ProjectKind.JULIA),
                new Case("dub.json", ProjectInspector.ProjectKind.DLANG),
                new Case("info.rkt", ProjectInspector.ProjectKind.RACKET),
                new Case("elm.json", ProjectInspector.ProjectKind.ELM),
                new Case("rescript.json", ProjectInspector.ProjectKind.RESCRIPT),
                new Case("spago.yaml", ProjectInspector.ProjectKind.PURESCRIPT),
                new Case("dune-project", ProjectInspector.ProjectKind.OCAML),
                new Case("shard.yml", ProjectInspector.ProjectKind.CRYSTAL));
        for (Case c : cases) {
            java.nio.file.Path projectDir = java.nio.file.Files.createDirectories(dir.resolve(c.kind().name()));
            java.nio.file.Files.writeString(projectDir.resolve(c.file()), "# manifest");
            org.assertj.core.api.Assertions.assertThat(ProjectInspector.detectKind(projectDir.toFile()))
                    .as(c.file()).isEqualTo(c.kind());
        }
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName(".NET detects via *.csproj glob, root or one level down")
    void dotnetGlobDetects(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Files.writeString(dir.resolve("App.csproj"), "<Project/>");
        org.assertj.core.api.Assertions.assertThat(ProjectInspector.detectKind(dir.toFile()))
                .isEqualTo(ProjectInspector.ProjectKind.DOTNET);

        java.nio.file.Path mono = java.nio.file.Files.createDirectories(dir.resolveSibling("mono-dotnet"));
        java.nio.file.Path api = java.nio.file.Files.createDirectories(mono.resolve("api"));
        java.nio.file.Files.writeString(api.resolve("Api.fsproj"), "<Project/>");
        org.assertj.core.api.Assertions.assertThat(
                ProjectInspector.kindDir(mono.toFile(), ProjectInspector.ProjectKind.DOTNET))
                .isEqualTo(api.toFile());
    }

    @Test
    @DisplayName("Review fixes (v1.71.0): Racket build targets the program, CHECK greys where there's no outdated verb, ReScript greys run/test")
    void reviewFixes(@TempDir Path root) throws IOException {
        // Racket build compiles main.rkt (the program), not info.rkt (metadata)
        Path rkt = Files.createDirectories(root.resolve("rkt"));
        Files.writeString(rkt.resolve("info.rkt"), "#lang info");
        Rack r1 = new Rack(); r1.setProjectDir(rkt.toFile());
        try {
            BuildDevice b = new BuildDevice(); r1.addDevice(b);
            assertThat(b.buildCommand()).containsExactly("raco", "make", "main.rkt");
        } finally { r1.shutdown(); }

        // CRATE CHECK (outdated) greys for tools without an outdated query
        for (String manifest : new String[]{"gleam.toml", "app.nimble", "dub.json", "info.rkt", "spago.yaml"}) {
            Path d = Files.createDirectories(root.resolve("crate-" + manifest.replace('.', '_')));
            Files.writeString(d.resolve(manifest), "# manifest");
            Rack r = new Rack(); r.setProjectDir(d.toFile());
            try {
                PackageManagerDevice pm = new PackageManagerDevice(); r.addDevice(pm);
                assertThat(pm.cmd("outdated")).as(manifest + " CHECK greys").isNull();
                // but install still works (proves it's the outdated verb, not the kind)
                assertThat(pm.cmd("install")).as(manifest + " install works").isNotNull();
            } finally { r.shutdown(); }
        }

        // ReScript greys IGNITION and VERITAS (build-only, no run/test)
        Path res = Files.createDirectories(root.resolve("res"));
        Files.writeString(res.resolve("rescript.json"), "{}");
        Rack r2 = new Rack(); r2.setProjectDir(res.toFile());
        try {
            RunDevice run = new RunDevice(); r2.addDevice(run);
            assertThat(run.buildCommand()).as("ReScript IGNITION greys").isNull();
            TestDevice test = new TestDevice(); r2.addDevice(test);
            assertThat(test.buildCommand()).as("ReScript VERITAS greys").isEmpty();
            BuildDevice build = new BuildDevice(); r2.addDevice(build);
            assertThat(build.buildCommand()).as("ReScript FORGE builds").containsExactly("npx", "rescript", "build");
        } finally { r2.shutdown(); }
    }

    @Test
    @DisplayName("Nim detects via the *.nimble glob, root or one level down")
    void nimGlobDetects(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("app.nimble"), "# nimble manifest");
        assertThat(ProjectInspector.detectKind(dir.toFile()))
                .isEqualTo(ProjectInspector.ProjectKind.NIM);

        Path mono = Files.createDirectories(dir.resolveSibling("mono-nim"));
        Path pkg = Files.createDirectories(mono.resolve("cli"));
        Files.writeString(pkg.resolve("cli.nimble"), "# nimble manifest");
        assertThat(ProjectInspector.kindDir(mono.toFile(), ProjectInspector.ProjectKind.NIM))
                .isEqualTo(pkg.toFile());
    }

    @Test
    @DisplayName("Indie stacks: every AUTO lane speaks the right toolchain (v1.69.0)")
    void indieStackLanes(@TempDir Path root) throws IOException {
        record Lane(String manifest, String[] run, String[] build, String[] testStart, String[] install) { }
        var lanes = java.util.List.of(
                new Lane("Project.toml", null,
                        new String[]{"julia", "--project=.", "-e", "using Pkg; Pkg.precompile()"},
                        new String[]{"julia", "--project=."},
                        new String[]{"julia", "--project=.", "-e", "using Pkg; Pkg.instantiate()"}),
                new Lane("app.nimble", new String[]{"nimble", "run"},
                        new String[]{"nimble", "build"},
                        new String[]{"nimble", "test"},
                        new String[]{"nimble", "install", "-d", "-y"}),
                new Lane("dub.json", new String[]{"dub", "run"},
                        new String[]{"dub", "build"},
                        new String[]{"dub", "test"},
                        new String[]{"dub", "upgrade", "--missing-only"}),
                new Lane("info.rkt", new String[]{"racket", "main.rkt"},
                        new String[]{"raco", "make", "main.rkt"},
                        new String[]{"raco", "test", "."},
                        new String[]{"raco", "pkg", "install", "--auto", "--skip-installed"}));
        for (Lane lane : lanes) {
            Path dir = Files.createDirectories(root.resolve(lane.manifest().replace('.', '_')));
            Files.writeString(dir.resolve(lane.manifest()), "# manifest");
            Rack rack = new Rack();
            rack.setProjectDir(dir.toFile());
            try {
                if (lane.run() != null) {
                    RunDevice run = new RunDevice();
                    rack.addDevice(run);
                    assertThat(run.buildCommand()).as(lane.manifest() + " run")
                            .containsExactly(lane.run());
                }
                BuildDevice build = new BuildDevice();
                rack.addDevice(build);
                assertThat(build.buildCommand()).as(lane.manifest() + " build")
                        .containsExactly(lane.build());
                TestDevice test = new TestDevice();
                rack.addDevice(test);
                assertThat(test.buildCommand()).as(lane.manifest() + " test")
                        .startsWith(lane.testStart());
                PackageManagerDevice deps = new PackageManagerDevice();
                rack.addDevice(deps);
                assertThat(deps.buildCommand()).as(lane.manifest() + " install")
                        .containsExactly(lane.install());
            } finally {
                rack.shutdown();
            }
        }
    }

    @Test
    @DisplayName("Functional web: elm reactor serves, spago runs, rescript builds; NODE outranks them beside a package.json")
    void functionalWebLanes(@TempDir Path root) throws IOException {
        // elm-only project: the framework's own lanes
        Path elm = Files.createDirectories(root.resolve("elm-app"));
        Files.writeString(elm.resolve("elm.json"), "{}");
        Rack rack = new Rack();
        rack.setProjectDir(elm.toFile());
        try {
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            assertThat(run.buildCommand()).containsExactly("npx", "elm", "reactor");
            BuildDevice build = new BuildDevice();
            rack.addDevice(build);
            assertThat(build.buildCommand()).containsExactly("npx", "elm", "make", "src/Main.elm");
            TestDevice test = new TestDevice();
            rack.addDevice(test);
            assertThat(test.buildCommand()).startsWith("npx", "elm-test");
        } finally {
            rack.shutdown();
        }

        // purescript: spago lanes including CRATE
        Path ps = Files.createDirectories(root.resolve("ps-app"));
        Files.writeString(ps.resolve("spago.yaml"), "# spago");
        Rack rack2 = new Rack();
        rack2.setProjectDir(ps.toFile());
        try {
            PackageManagerDevice deps = new PackageManagerDevice();
            rack2.addDevice(deps);
            assertThat(deps.buildCommand()).containsExactly("spago", "install");
        } finally {
            rack2.shutdown();
        }

        // beside a package.json, NODE wins detection (the WEBPACK-family rule)
        Path mixed = Files.createDirectories(root.resolve("mixed"));
        Files.writeString(mixed.resolve("elm.json"), "{}");
        Files.writeString(mixed.resolve("package.json"), "{}");
        assertThat(ProjectInspector.detectKind(mixed.toFile()))
                .isEqualTo(ProjectInspector.ProjectKind.NODE);
        assertThat(ProjectInspector.detectKinds(mixed.toFile()).keySet())
                .contains(ProjectInspector.ProjectKind.ELM);
    }

    @Test
    @DisplayName("Gleam: every AUTO lane speaks gleam (v1.59.0 expansion)")
    void gleamLanes() throws IOException {
        Rack rack = rackAimedAt("gleam.toml");
        assertThat(ProjectInspector.detectKind(projectDir.toFile()))
                .isEqualTo(ProjectInspector.ProjectKind.GLEAM);

        RunDevice run = new RunDevice();
        rack.addDevice(run);
        assertThat(run.buildCommand()).containsExactly("gleam", "run");

        BuildDevice build = new BuildDevice();
        rack.addDevice(build);
        assertThat(build.buildCommand()).containsExactly("gleam", "build");

        TestDevice test = new TestDevice();
        rack.addDevice(test);
        assertThat(test.buildCommand()).startsWith("gleam", "test");

        PackageManagerDevice deps = new PackageManagerDevice();
        rack.addDevice(deps);
        assertThat(deps.buildCommand()).containsExactly("gleam", "deps", "download");

        rack.shutdown();
    }

    @Test
    @DisplayName("V: v.mod detects and every AUTO lane speaks v (v1.72.0)")
    void vlangLanes() throws IOException {
        Rack rack = rackAimedAt("v.mod");
        assertThat(ProjectInspector.detectKind(projectDir.toFile()))
                .isEqualTo(ProjectInspector.ProjectKind.VLANG);

        RunDevice run = new RunDevice();
        rack.addDevice(run);
        assertThat(run.buildCommand()).containsExactly("v", "run", ".");

        BuildDevice build = new BuildDevice();
        rack.addDevice(build);
        assertThat(build.buildCommand()).containsExactly("v", ".");

        TestDevice test = new TestDevice();
        rack.addDevice(test);
        assertThat(test.buildCommand()).startsWith("v", "test", ".");

        PackageManagerDevice deps = new PackageManagerDevice();
        rack.addDevice(deps);
        assertThat(deps.buildCommand()).containsExactly("v", "install");

        rack.shutdown();
    }
}
