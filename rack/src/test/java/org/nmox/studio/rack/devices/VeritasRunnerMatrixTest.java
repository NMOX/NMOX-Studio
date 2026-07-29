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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VERITAS's whole runner table: every RUNNER knob position must build
 * that framework's own test argv, AUTO must resolve each detected kind
 * to the right runner, and the COVER switch must decorate only the
 * runners with a real coverage flag. All read through the protected
 * buildCommand seam — the TEST button's exact path — with nothing
 * spawned.
 */
class VeritasRunnerMatrixTest {

    // Mirror of TestDevice.FRAMEWORKS (append-only by law); the argv
    // assertions verify the index mapping stays true.
    private static final String[] FRAMEWORKS = {"auto", "jest", "vitest", "mocha",
        "playwright", "cypress", "pytest", "cargo", "go", "mvn", "rspec", "phpunit",
        "mix", "rebar3", "clojure", "swift", "dotnet", "dart", "sbt", "stack", "zig",
        "dune", "crystal", "bun", "deno", "forge", "gleam", "julia", "nim", "dlang",
        "racket", "elm", "purescript", "vlang", "fortran", "ada", "cairo", "move",
        "aiken"};

    @TempDir
    Path root;

    private int caseNo;

    private Path freshDir(String... files) throws IOException {
        Path dir = root.resolve("case-" + (caseNo++));
        Files.createDirectories(dir);
        for (String f : files) {
            if (f.endsWith("/")) {
                Files.createDirectories(dir.resolve(f.substring(0, f.length() - 1)));
            } else {
                Path p = dir.resolve(f);
                if (p.getParent() != null) {
                    Files.createDirectories(p.getParent());
                }
                Files.writeString(p, "{}");
            }
        }
        return dir;
    }

    private List<String> commandFor(String framework, String... files) throws IOException {
        return commandFor(framework, false, files);
    }

    private List<String> commandFor(String framework, boolean cover, String... files)
            throws IOException {
        int index = List.of(FRAMEWORKS).indexOf(framework);
        assertThat(index).as("knob position " + framework).isNotNegative();
        Rack rack = new Rack();
        rack.setProjectDir(freshDir(files).toFile());
        try {
            TestDevice veritas = new TestDevice();
            rack.addDevice(veritas);
            veritas.applyState(Map.of("framework", String.valueOf(index),
                    "coverage", String.valueOf(cover)));
            return veritas.buildCommand();
        } finally {
            rack.shutdown();
        }
    }

    private List<String> autoCommand(String... files) throws IOException {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir(files).toFile());
        try {
            TestDevice veritas = new TestDevice();
            rack.addDevice(veritas);
            return veritas.buildCommand();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("Every explicit runner position builds that framework's test argv")
    void explicitRunners() throws IOException {
        assertThat(commandFor("jest")).containsExactly("npx", "jest");
        assertThat(commandFor("vitest")).containsExactly("npx", "vitest", "run");
        assertThat(commandFor("mocha")).containsExactly("npx", "mocha");
        assertThat(commandFor("playwright")).containsExactly("npx", "playwright", "test");
        assertThat(commandFor("cypress")).containsExactly("npx", "cypress", "run");
        assertThat(commandFor("pytest")).containsExactly("python3", "-m", "pytest");
        assertThat(commandFor("cargo")).containsExactly("cargo", "test");
        assertThat(commandFor("go")).containsExactly("go", "test", "./...");
        assertThat(commandFor("mvn")).containsExactly("mvn", "-q", "test");
        assertThat(commandFor("mix")).containsExactly("mix", "test");
        assertThat(commandFor("rebar3")).containsExactly("rebar3", "eunit");
        assertThat(commandFor("clojure")).containsExactly("clojure", "-X:test");
        assertThat(commandFor("swift")).containsExactly("swift", "test");
        assertThat(commandFor("dotnet")).containsExactly("dotnet", "test");
        assertThat(commandFor("dart")).containsExactly("dart", "test");
        assertThat(commandFor("sbt")).containsExactly("sbt", "test");
        assertThat(commandFor("stack")).containsExactly("stack", "test");
        assertThat(commandFor("zig")).containsExactly("zig", "build", "test");
        assertThat(commandFor("dune")).containsExactly("dune", "runtest");
        assertThat(commandFor("crystal")).containsExactly("crystal", "spec");
        assertThat(commandFor("bun")).containsExactly("bun", "test");
        assertThat(commandFor("deno")).containsExactly("deno", "test");
        assertThat(commandFor("forge")).containsExactly("forge", "test");
        assertThat(commandFor("gleam")).containsExactly("gleam", "test");
        assertThat(commandFor("julia"))
                .containsExactly("julia", "--project=.", "-e", "using Pkg; Pkg.test()");
        assertThat(commandFor("nim")).containsExactly("nimble", "test");
        assertThat(commandFor("dlang")).containsExactly("dub", "test");
        assertThat(commandFor("racket")).containsExactly("raco", "test", ".");
        assertThat(commandFor("elm")).containsExactly("npx", "elm-test");
        assertThat(commandFor("purescript")).containsExactly("spago", "test");
        assertThat(commandFor("vlang")).containsExactly("v", "test", ".");
        assertThat(commandFor("fortran")).containsExactly("fpm", "test");
        assertThat(commandFor("ada")).as("no universal Ada test verb — greys").isEmpty();
        assertThat(commandFor("cairo")).containsExactly("scarb", "test");
        assertThat(commandFor("move")).containsExactly("sui", "move", "test");
        assertThat(commandFor("aiken")).containsExactly("aiken", "check");
    }

    @Test
    @DisplayName("The rspec and phpunit rows probe the project's own layout")
    void conditionalRunners() throws IOException {
        assertThat(commandFor("rspec", "Gemfile", "spec/"))
                .containsExactly("bundle", "exec", "rspec");
        assertThat(commandFor("rspec", "Gemfile")).containsExactly("rake", "test");
        assertThat(commandFor("phpunit", "composer.json", "vendor/bin/phpunit"))
                .containsExactly("./vendor/bin/phpunit");
        assertThat(commandFor("phpunit", "composer.json")).containsExactly("phpunit");
    }

    @Test
    @DisplayName("AUTO resolves each manifest kind to its own runner")
    void autoResolution() throws IOException {
        assertThat(autoCommand("bunfig.toml")).containsExactly("bun", "test");
        assertThat(autoCommand("deno.json")).containsExactly("deno", "test");
        assertThat(autoCommand("Cargo.toml")).containsExactly("cargo", "test");
        assertThat(autoCommand("foundry.toml")).containsExactly("forge", "test");
        assertThat(autoCommand("mix.exs")).containsExactly("mix", "test");
        assertThat(autoCommand("rebar.config")).containsExactly("rebar3", "eunit");
        assertThat(autoCommand("gleam.toml")).containsExactly("gleam", "test");
        assertThat(autoCommand("Project.toml"))
                .containsExactly("julia", "--project=.", "-e", "using Pkg; Pkg.test()");
        assertThat(autoCommand("app.nimble")).containsExactly("nimble", "test");
        assertThat(autoCommand("dub.json")).containsExactly("dub", "test");
        assertThat(autoCommand("info.rkt")).containsExactly("raco", "test", ".");
        assertThat(autoCommand("elm.json")).containsExactly("npx", "elm-test");
        assertThat(autoCommand("spago.yaml")).containsExactly("spago", "test");
        assertThat(autoCommand("v.mod")).containsExactly("v", "test", ".");
        assertThat(autoCommand("Scarb.toml")).containsExactly("scarb", "test");
        assertThat(autoCommand("Move.toml")).containsExactly("sui", "move", "test");
        assertThat(autoCommand("aiken.toml")).containsExactly("aiken", "check");
        assertThat(autoCommand("fpm.toml")).containsExactly("fpm", "test");
        assertThat(autoCommand("alire.toml")).as("ADA greys").isEmpty();
        assertThat(autoCommand("rescript.json")).as("build-only kind greys").isEmpty();
        assertThat(autoCommand("deps.edn")).containsExactly("clojure", "-X:test");
        assertThat(autoCommand("Package.swift")).containsExactly("swift", "test");
        assertThat(autoCommand("app.csproj")).containsExactly("dotnet", "test");
        assertThat(autoCommand("pubspec.yaml")).containsExactly("dart", "test");
        assertThat(autoCommand("build.sbt")).containsExactly("sbt", "test");
        assertThat(autoCommand("stack.yaml")).containsExactly("stack", "test");
        assertThat(autoCommand("build.zig")).containsExactly("zig", "build", "test");
        assertThat(autoCommand("dune-project")).containsExactly("dune", "runtest");
        assertThat(autoCommand("shard.yml")).containsExactly("crystal", "spec");
        assertThat(autoCommand("go.mod")).containsExactly("go", "test", "./...");
        assertThat(autoCommand("pom.xml")).containsExactly("mvn", "-q", "test");
        assertThat(autoCommand("build.gradle")).containsExactly("gradle", "test");
        assertThat(autoCommand("pyproject.toml")).containsExactly("python3", "-m", "pytest");
        assertThat(autoCommand("Gemfile")).containsExactly("rake", "test");
        assertThat(autoCommand("composer.json")).containsExactly("phpunit");
        // no manifest at all: npm test is the honest default
        assertThat(autoCommand()).containsExactly("npm", "test");
    }

    @Test
    @DisplayName("AUTO on a Node project prefers the test script, then the framework dep")
    void autoNodeResolution() throws IOException {
        Path scripted = freshDir();
        Files.writeString(scripted.resolve("package.json"),
                "{\"scripts\":{\"test\":\"vitest run\"}}");
        Rack rack = new Rack();
        rack.setProjectDir(scripted.toFile());
        try {
            TestDevice veritas = new TestDevice();
            rack.addDevice(veritas);
            assertThat(veritas.buildCommand()).containsExactly("npm", "test");
        } finally {
            rack.shutdown();
        }

        Path playwright = freshDir();
        Files.writeString(playwright.resolve("package.json"),
                "{\"devDependencies\":{\"@playwright/test\":\"^1.40.0\"}}");
        Rack rack2 = new Rack();
        rack2.setProjectDir(playwright.toFile());
        try {
            TestDevice veritas = new TestDevice();
            rack2.addDevice(veritas);
            assertThat(veritas.buildCommand()).containsExactly("npx", "playwright", "test");
        } finally {
            rack2.shutdown();
        }
    }

    @Test
    @DisplayName("COVER decorates only runners with a portable coverage flag")
    void coverageFlagPerRunner() throws IOException {
        assertThat(commandFor("jest", true)).containsExactly("npx", "jest", "--coverage");
        assertThat(commandFor("vitest", true))
                .containsExactly("npx", "vitest", "run", "--coverage");
        assertThat(commandFor("pytest", true))
                .containsExactly("python3", "-m", "pytest", "--cov");
        assertThat(commandFor("cargo", true))
                .as("no portable cargo coverage flag").containsExactly("cargo", "test");
    }
}
