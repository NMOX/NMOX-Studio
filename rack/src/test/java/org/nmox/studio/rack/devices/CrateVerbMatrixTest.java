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
 * CRATE's whole verb table, kind by kind: for every toolchain the AUTO
 * knob can resolve, the install/update/outdated verbs must map onto
 * that toolchain's own dependency commands — or grey honestly (null)
 * where the tool has no such query. Each case aims a fresh rack at a
 * dir holding exactly one manifest and reads the argv through the
 * package-private {@code cmd} seam, the same path the buttons take.
 */
class CrateVerbMatrixTest {

    @TempDir
    Path root;

    private int caseNo;

    /** cmd(verb) for a CRATE aimed at a project with just these files. */
    private List<String> cmd(String verb, String... manifests) throws IOException {
        Path dir = root.resolve("case-" + (caseNo++));
        Files.createDirectories(dir);
        for (String m : manifests) {
            Path p = dir.resolve(m);
            Files.createDirectories(p.getParent() == null ? dir : p.getParent());
            Files.writeString(p, "{}");
        }
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        try {
            PackageManagerDevice crate = new PackageManagerDevice();
            rack.addDevice(crate);
            return crate.cmd(verb);
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("The JS-runtime kinds speak their own package tools")
    void jsRuntimes() throws IOException {
        assertThat(cmd("install", "bunfig.toml")).containsExactly("bun", "install");
        assertThat(cmd("update", "bunfig.toml")).containsExactly("bun", "update");
        assertThat(cmd("outdated", "bunfig.toml")).containsExactly("bun", "outdated");
        assertThat(cmd("install", "deno.json")).containsExactly("deno", "install");
        assertThat(cmd("outdated", "deno.json")).containsExactly("deno", "outdated");
    }

    @Test
    @DisplayName("The compiled kinds fetch, update, and query with their native tools")
    void compiledKinds() throws IOException {
        assertThat(cmd("install", "Cargo.toml")).containsExactly("cargo", "fetch");
        assertThat(cmd("update", "Cargo.toml")).containsExactly("cargo", "update");
        assertThat(cmd("outdated", "Cargo.toml")).containsExactly("cargo", "update", "--dry-run");
        assertThat(cmd("install", "go.mod")).containsExactly("go", "mod", "download");
        assertThat(cmd("update", "go.mod")).containsExactly("go", "get", "-u", "./...");
        assertThat(cmd("outdated", "go.mod")).containsExactly("go", "list", "-u", "-m", "all");
        assertThat(cmd("install", "Package.swift")).containsExactly("swift", "package", "resolve");
        assertThat(cmd("update", "Package.swift")).containsExactly("swift", "package", "update");
        assertThat(cmd("outdated", "Package.swift")).containsExactly("swift", "package", "show-dependencies");
        assertThat(cmd("install", "app.csproj")).containsExactly("dotnet", "restore");
        assertThat(cmd("outdated", "app.csproj")).containsExactly("dotnet", "list", "package", "--outdated");
        assertThat(cmd("install", "build.zig")).containsExactly("zig", "build", "--fetch");
        assertThat(cmd("install", "dune-project")).containsExactly("dune", "build");
        assertThat(cmd("install", "shard.yml")).containsExactly("shards", "install");
        assertThat(cmd("update", "shard.yml")).containsExactly("shards", "update");
        assertThat(cmd("outdated", "shard.yml")).containsExactly("shards", "outdated");
        assertThat(cmd("install", "build.sbt")).containsExactly("sbt", "update");
        assertThat(cmd("install", "stack.yaml")).containsExactly("stack", "build", "--only-dependencies");
    }

    @Test
    @DisplayName("The BEAM family and Clojure prepare deps their own way")
    void beamAndClojure() throws IOException {
        assertThat(cmd("install", "mix.exs")).containsExactly("mix", "deps.get");
        assertThat(cmd("update", "mix.exs")).containsExactly("mix", "deps.update", "--all");
        assertThat(cmd("outdated", "mix.exs")).containsExactly("mix", "hex.outdated");
        assertThat(cmd("install", "rebar.config")).containsExactly("rebar3", "get-deps");
        assertThat(cmd("update", "rebar.config")).containsExactly("rebar3", "upgrade", "--all");
        assertThat(cmd("install", "gleam.toml")).containsExactly("gleam", "deps", "download");
        assertThat(cmd("update", "gleam.toml")).containsExactly("gleam", "deps", "update");
        assertThat(cmd("outdated", "gleam.toml")).as("gleam has no outdated query").isNull();
        assertThat(cmd("install", "deps.edn")).containsExactly("clojure", "-P");
    }

    @Test
    @DisplayName("The indie stacks install with their own tools; missing queries grey")
    void indieStacks() throws IOException {
        assertThat(cmd("install", "Project.toml"))
                .containsExactly("julia", "--project=.", "-e", "using Pkg; Pkg.instantiate()");
        assertThat(cmd("update", "Project.toml"))
                .containsExactly("julia", "--project=.", "-e", "using Pkg; Pkg.update()");
        assertThat(cmd("outdated", "Project.toml"))
                .containsExactly("julia", "--project=.", "-e", "using Pkg; Pkg.status(outdated=true)");
        assertThat(cmd("install", "app.nimble")).containsExactly("nimble", "install", "-d", "-y");
        assertThat(cmd("update", "app.nimble")).containsExactly("nimble", "refresh");
        assertThat(cmd("outdated", "app.nimble")).isNull();
        assertThat(cmd("install", "dub.json")).containsExactly("dub", "upgrade", "--missing-only");
        assertThat(cmd("update", "dub.json")).containsExactly("dub", "upgrade");
        assertThat(cmd("outdated", "dub.json")).isNull();
        assertThat(cmd("install", "info.rkt"))
                .containsExactly("raco", "pkg", "install", "--auto", "--skip-installed");
        assertThat(cmd("update", "info.rkt")).containsExactly("raco", "pkg", "update", "--auto");
        assertThat(cmd("outdated", "info.rkt")).isNull();
        assertThat(cmd("install", "spago.yaml")).containsExactly("spago", "install");
        assertThat(cmd("update", "spago.yaml")).containsExactly("spago", "upgrade");
        assertThat(cmd("outdated", "spago.yaml")).isNull();
        assertThat(cmd("install", "v.mod")).containsExactly("v", "install");
        assertThat(cmd("update", "v.mod")).containsExactly("v", "update");
        assertThat(cmd("outdated", "v.mod")).isNull();
        assertThat(cmd("install", "fpm.toml")).containsExactly("fpm", "build");
        assertThat(cmd("update", "fpm.toml")).containsExactly("fpm", "update");
        assertThat(cmd("outdated", "fpm.toml")).isNull();
        assertThat(cmd("install", "alire.toml")).containsExactly("alr", "build");
        assertThat(cmd("update", "alire.toml")).containsExactly("alr", "update");
        assertThat(cmd("outdated", "alire.toml")).isNull();
    }

    @Test
    @DisplayName("The contract chains: builds fetch deps, missing queries grey, npm-carried kinds delegate")
    void contractChains() throws IOException {
        assertThat(cmd("install", "foundry.toml")).containsExactly("forge", "install");
        assertThat(cmd("update", "foundry.toml")).containsExactly("forge", "update");
        assertThat(cmd("install", "Scarb.toml")).containsExactly("scarb", "build");
        assertThat(cmd("update", "Scarb.toml")).containsExactly("scarb", "update");
        assertThat(cmd("outdated", "Scarb.toml")).isNull();
        assertThat(cmd("install", "Move.toml")).containsExactly("sui", "move", "build");
        assertThat(cmd("outdated", "Move.toml")).isNull();
        assertThat(cmd("install", "aiken.toml")).containsExactly("aiken", "check");
        assertThat(cmd("update", "aiken.toml")).isNull();
        assertThat(cmd("outdated", "aiken.toml")).isNull();
        // CLARITY/TACT deps live in package.json beside the contract
        // manifest — every verb rides the NODE lane
        assertThat(cmd("install", "Clarinet.toml", "package.json"))
                .containsExactly("npm", "install");
        assertThat(cmd("update", "Clarinet.toml", "package.json"))
                .containsExactly("npm", "update");
        assertThat(cmd("install", "tact.config.json", "package.json"))
                .containsExactly("npm", "install");
    }

    @Test
    @DisplayName("The JVM and scripting kinds map onto their own dependency verbs")
    void jvmAndScripting() throws IOException {
        assertThat(cmd("install", "pom.xml")).containsExactly("mvn", "-q", "dependency:resolve");
        assertThat(cmd("update", "pom.xml"))
                .containsExactly("mvn", "-q", "versions:display-dependency-updates");
        assertThat(cmd("outdated", "pom.xml"))
                .containsExactly("mvn", "-q", "versions:display-dependency-updates");
        assertThat(cmd("install", "build.gradle")).containsExactly("gradle", "--quiet", "dependencies");
        assertThat(cmd("install", "Gemfile")).containsExactly("bundle", "install");
        assertThat(cmd("update", "Gemfile")).containsExactly("bundle", "update");
        assertThat(cmd("outdated", "Gemfile")).containsExactly("bundle", "outdated");
        assertThat(cmd("install", "composer.json")).containsExactly("composer", "install");
        assertThat(cmd("update", "composer.json")).containsExactly("composer", "update");
        assertThat(cmd("outdated", "composer.json")).containsExactly("composer", "outdated");
        // PYTHON: requirements.txt drives -r; a bare pyproject installs -e .
        assertThat(cmd("install", "pyproject.toml", "requirements.txt"))
                .containsExactly("pip", "install", "-r", "requirements.txt");
        assertThat(cmd("install", "pyproject.toml")).containsExactly("pip", "install", "-e", ".");
        assertThat(cmd("update", "pyproject.toml"))
                .containsExactly("pip", "install", "--upgrade", "-r", "requirements.txt");
        assertThat(cmd("outdated", "pyproject.toml")).containsExactly("pip", "list", "--outdated");
    }

    @Test
    @DisplayName("Classic web: bower installs via npx; bundler configs declare no deps of their own")
    void classicWeb() throws IOException {
        assertThat(cmd("install", "bower.json")).containsExactly("npx", "bower", "install");
        assertThat(cmd("update", "bower.json")).containsExactly("npx", "bower", "update");
        assertThat(cmd("outdated", "bower.json")).containsExactly("npx", "bower", "list");
        assertThat(cmd("install", "Gruntfile.js")).isNull();
        assertThat(cmd("install", "gulpfile.js")).isNull();
        assertThat(cmd("install", "webpack.config.js")).isNull();
        assertThat(cmd("install", "index.html")).isNull();
        // ELM/RESCRIPT deps ride the NODE lane beside their manifests
        assertThat(cmd("install", "elm.json")).isNull();
        assertThat(cmd("install", "rescript.json")).isNull();
    }

    @Test
    @DisplayName("A multi-toolchain repo's install sequence dedupes and skips dep-less kinds")
    void installStepsSequence() throws IOException {
        Path dir = root.resolve("case-" + (caseNo++));
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("package.json"), "{}");
        Files.writeString(dir.resolve("Cargo.toml"), "[package]");
        Files.writeString(dir.resolve("Gruntfile.js"), "");
        Rack rack = new Rack();
        rack.setProjectDir(dir.toFile());
        try {
            PackageManagerDevice crate = new PackageManagerDevice();
            rack.addDevice(crate);
            List<CommandDevice.Step> steps = crate.installSteps();
            assertThat(steps).extracting(s -> s.command().get(0))
                    .as("one step per installing toolchain, GRUNT skipped")
                    .containsExactlyInAnyOrder("cargo", "npm");
        } finally {
            rack.shutdown();
        }
    }
}
