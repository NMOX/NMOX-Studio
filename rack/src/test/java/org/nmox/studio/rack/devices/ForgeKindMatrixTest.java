package org.nmox.studio.rack.devices;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FORGE's kind table beyond the Node bundlers: AUTO over each detected
 * toolchain must build with that toolchain's own compiler, honoring the
 * PROD switch where the tool offers a release mode; the legacy config
 * scan must find bundlers declared by file rather than by dependency;
 * and the faceplate's "Open build config" must point at the file that
 * actually drives the build.
 */
class ForgeKindMatrixTest {

    @TempDir
    Path root;

    private int caseNo;

    private Path freshDir(String... files) throws IOException {
        Path dir = root.resolve("case-" + (caseNo++));
        Files.createDirectories(dir);
        for (String f : files) {
            Path p = dir.resolve(f);
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            Files.writeString(p, "{}");
        }
        return dir;
    }

    private List<String> autoCommand(boolean prod, String... files) throws IOException {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir(files).toFile());
        try {
            BuildDevice forge = new BuildDevice();
            rack.addDevice(forge);
            forge.applyState(Map.of("prod", String.valueOf(prod), "watch", "false"));
            return forge.buildCommand();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("AUTO speaks each toolchain's build verb, PROD hardening where offered")
    void kindTable() throws IOException {
        assertThat(autoCommand(false, "bunfig.toml")).containsExactly("bun", "run", "build");
        assertThat(autoCommand(false, "deno.json")).containsExactly("deno", "task", "build");
        assertThat(autoCommand(true, "foundry.toml")).containsExactly("forge", "build");
        assertThat(autoCommand(false, "deps.edn")).containsExactly("clojure", "-P");
        assertThat(autoCommand(true, "Package.swift"))
                .containsExactly("swift", "build", "-c", "release");
        assertThat(autoCommand(false, "Package.swift")).containsExactly("swift", "build");
        assertThat(autoCommand(true, "app.csproj"))
                .containsExactly("dotnet", "build", "-c", "Release");
        assertThat(autoCommand(false, "app.csproj")).containsExactly("dotnet", "build");
        assertThat(autoCommand(false, "pubspec.yaml"))
                .containsExactly("dart", "compile", "exe", "bin/main.dart");
        assertThat(autoCommand(false, "build.sbt")).containsExactly("sbt", "compile");
        assertThat(autoCommand(false, "stack.yaml")).containsExactly("stack", "build");
        assertThat(autoCommand(true, "build.zig"))
                .containsExactly("zig", "build", "-Doptimize=ReleaseFast");
        assertThat(autoCommand(false, "build.zig")).containsExactly("zig", "build");
        assertThat(autoCommand(false, "gleam.toml")).containsExactly("gleam", "build");
        assertThat(autoCommand(false, "Project.toml"))
                .containsExactly("julia", "--project=.", "-e", "using Pkg; Pkg.precompile()");
        assertThat(autoCommand(false, "app.nimble")).containsExactly("nimble", "build");
        assertThat(autoCommand(false, "dub.json")).containsExactly("dub", "build");
        assertThat(autoCommand(false, "info.rkt")).containsExactly("raco", "make", "main.rkt");
        assertThat(autoCommand(false, "elm.json"))
                .containsExactly("npx", "elm", "make", "src/Main.elm");
        assertThat(autoCommand(false, "rescript.json"))
                .containsExactly("npx", "rescript", "build");
        assertThat(autoCommand(false, "spago.yaml")).containsExactly("spago", "build");
        assertThat(autoCommand(false, "v.mod")).containsExactly("v", ".");
        assertThat(autoCommand(false, "Scarb.toml")).containsExactly("scarb", "build");
        assertThat(autoCommand(false, "Move.toml")).containsExactly("sui", "move", "build");
        assertThat(autoCommand(false, "aiken.toml")).containsExactly("aiken", "build");
        assertThat(autoCommand(false, "Clarinet.toml")).containsExactly("clarinet", "check");
        assertThat(autoCommand(false, "tact.config.json"))
                .containsExactly("npx", "tact", "--config", "tact.config.json");
        assertThat(autoCommand(false, "fpm.toml")).containsExactly("fpm", "build");
        assertThat(autoCommand(false, "alire.toml")).containsExactly("alr", "build");
        assertThat(autoCommand(false, "dune-project")).containsExactly("dune", "build");
        assertThat(autoCommand(false, "shard.yml")).containsExactly("shards", "build");
        assertThat(autoCommand(false, "pom.xml"))
                .containsExactly("mvn", "-q", "package", "-DskipTests");
        assertThat(autoCommand(false, "build.gradle"))
                .containsExactly("gradle", "build", "-x", "test");
        assertThat(autoCommand(false, "CMakeLists.txt"))
                .containsExactly("cmake", "--build", "build");
        assertThat(autoCommand(false, "Makefile")).containsExactly("make");
        assertThat(autoCommand(false, "pyproject.toml"))
                .containsExactly("python3", "-m", "compileall", "-q", ".");
        assertThat(autoCommand(false, "Gemfile")).containsExactly("rake", "build");
        assertThat(autoCommand(false, "composer.json"))
                .containsExactly("composer", "install", "--no-dev", "--optimize-autoloader");
    }

    @Test
    @DisplayName("A legacy repo declares its bundler by config file, not by dependency")
    void configFileScan() throws IOException {
        assertThat(autoCommand(false, "package.json", "webpack.config.js"))
                .startsWith("npx", "webpack");
        assertThat(autoCommand(false, "package.json", "Gruntfile.js"))
                .startsWith("npx", "grunt");
        assertThat(autoCommand(false, "package.json", "gulpfile.js"))
                .startsWith("npx", "gulp");
    }

    @Test
    @DisplayName("Open-build-config points at the file driving the build")
    void primaryManifestBranches() throws IOException {
        // a webpack config wins for the webpack tool
        Path webpack = freshDir("package.json", "webpack.config.js");
        Rack rack = new Rack();
        rack.setProjectDir(webpack.toFile());
        try {
            BuildDevice forge = new BuildDevice();
            rack.addDevice(forge);
            assertThat(forge.primaryManifest()).map(File::getName)
                    .hasValue("webpack.config.js");
        } finally {
            rack.shutdown();
        }

        // a kind:-resolved toolchain points at its own manifest
        Path cargo = freshDir("Cargo.toml");
        Rack rack2 = new Rack();
        rack2.setProjectDir(cargo.toFile());
        try {
            BuildDevice forge = new BuildDevice();
            rack2.addDevice(forge);
            assertThat(forge.primaryManifest()).map(File::getName)
                    .hasValue("Cargo.toml");
        } finally {
            rack2.shutdown();
        }

        // a plain npm project falls back to package.json; an empty dir to nothing
        Path npm = freshDir("package.json");
        Rack rack3 = new Rack();
        rack3.setProjectDir(npm.toFile());
        try {
            BuildDevice forge = new BuildDevice();
            rack3.addDevice(forge);
            assertThat(forge.primaryManifest()).map(File::getName)
                    .hasValue("package.json");
        } finally {
            rack3.shutdown();
        }
        Path empty = freshDir();
        Rack rack4 = new Rack();
        rack4.setProjectDir(empty.toFile());
        try {
            BuildDevice forge = new BuildDevice();
            rack4.addDevice(forge);
            assertThat(forge.primaryManifest()).isEmpty();
        } finally {
            rack4.shutdown();
        }
    }

    @Test
    @DisplayName("Watch-mode markers only fire while a process is running")
    void watchMarkersGatedOnProcess() throws IOException {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir("package.json").toFile());
        try {
            BuildDevice forge = new BuildDevice();
            rack.addDevice(forge);
            forge.applyState(Map.of("watch", "true"));
            // no process running: a rebuild marker must not fire OK
            forge.onLine("compiled successfully in 1.2s");
            // watch off: same
            forge.applyState(Map.of("watch", "false"));
            forge.onLine("compiled successfully in 1.2s");
            rack.awaitRouterIdle();
            assertThat(forge.isLive()).isFalse();
        } finally {
            rack.shutdown();
        }
    }
}
