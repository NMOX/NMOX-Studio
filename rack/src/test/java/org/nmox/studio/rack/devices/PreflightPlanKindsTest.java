package org.nmox.studio.rack.devices;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.devices.PreflightPlan.Check;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.163.0 promise, row by row: every toolchain with honest
 * test/build verbs gets a ship plan whose commands mirror the rack
 * lane tables. Each case plants exactly one manifest in a fresh dir
 * and asserts the exact argv PREFLIGHT would run — the same
 * plansOnlyWhatExists law the v1.164.0 review enforced on the
 * file-assuming kinds.
 */
class PreflightPlanKindsTest {

    @TempDir
    Path root;

    private int caseNo;

    /** A fresh project dir holding only the named files (dirs end with '/'). */
    private File project(String... files) throws IOException {
        Path dir = root.resolve("case-" + (caseNo++));
        Files.createDirectories(dir);
        for (String f : files) {
            if (f.endsWith("/")) {
                Files.createDirectories(dir.resolve(f.substring(0, f.length() - 1)));
            } else {
                Path p = dir.resolve(f);
                Files.createDirectories(p.getParent());
                Files.writeString(p, "{}");
            }
        }
        return dir.toFile();
    }

    private static List<List<String>> commands(File dir) {
        return PreflightPlan.forProject(dir).stream().map(Check::command).toList();
    }

    @Test
    @DisplayName("Compiled-language kinds plan their native test + release-hardened build")
    void compiledKinds() throws IOException {
        assertThat(commands(project("Cargo.toml"))).containsExactly(
                List.of("cargo", "test"),
                List.of("cargo", "build", "--release"),
                List.of("cargo", "clippy", "--", "-D", "warnings"));
        assertThat(commands(project("go.mod"))).containsExactly(
                List.of("go", "test", "./..."),
                List.of("go", "build", "./..."),
                List.of("go", "vet", "./..."));
        assertThat(commands(project("pom.xml"))).containsExactly(
                List.of("mvn", "-q", "test"),
                List.of("mvn", "-q", "package", "-DskipTests"));
        assertThat(commands(project("build.gradle"))).containsExactly(
                List.of("gradle", "test"),
                List.of("gradle", "build", "-x", "test"));
        assertThat(commands(project("Package.swift"))).containsExactly(
                List.of("swift", "test"),
                List.of("swift", "build", "-c", "release"));
        assertThat(commands(project("app.csproj"))).containsExactly(
                List.of("dotnet", "test"),
                List.of("dotnet", "build", "-c", "Release"));
        assertThat(commands(project("build.zig"))).containsExactly(
                List.of("zig", "build", "test"),
                List.of("zig", "build", "-Doptimize=ReleaseFast"));
        assertThat(commands(project("shard.yml"))).containsExactly(
                List.of("crystal", "spec"),
                List.of("shards", "build"));
        assertThat(commands(project("v.mod"))).containsExactly(
                List.of("v", "test", "."),
                List.of("v", "."));
        assertThat(commands(project("fpm.toml"))).containsExactly(
                List.of("fpm", "test"),
                List.of("fpm", "build"));
    }

    @Test
    @DisplayName("The functional and BEAM families plan their own runners")
    void functionalAndBeamKinds() throws IOException {
        assertThat(commands(project("mix.exs"))).containsExactly(
                List.of("mix", "test"),
                List.of("mix", "compile"));
        assertThat(commands(project("rebar.config"))).containsExactly(
                List.of("rebar3", "eunit"),
                List.of("rebar3", "compile"));
        assertThat(commands(project("gleam.toml"))).containsExactly(
                List.of("gleam", "test"),
                List.of("gleam", "build"));
        assertThat(commands(project("build.sbt"))).containsExactly(
                List.of("sbt", "test"),
                List.of("sbt", "compile"));
        assertThat(commands(project("stack.yaml"))).containsExactly(
                List.of("stack", "test"),
                List.of("stack", "build"));
        assertThat(commands(project("dune-project"))).containsExactly(
                List.of("dune", "runtest"),
                List.of("dune", "build"));
        assertThat(commands(project("deps.edn"))).containsExactly(
                List.of("clojure", "-X:test"));
        assertThat(commands(project("spago.yaml"))).containsExactly(
                List.of("spago", "test"),
                List.of("spago", "build"));
        assertThat(commands(project("rescript.json")))
                .as("ReScript is build-only: no invented test runner")
                .containsExactly(List.of("npx", "rescript", "build"));
    }

    @Test
    @DisplayName("The indie stacks plan tests-and-build; file-assuming rows probe first")
    void indieKinds() throws IOException {
        assertThat(commands(project("Project.toml"))).containsExactly(
                List.of("julia", "--project=.", "-e", "using Pkg; Pkg.test()"));
        assertThat(commands(project("app.nimble"))).containsExactly(
                List.of("nimble", "test"),
                List.of("nimble", "build"));
        assertThat(commands(project("dub.json"))).containsExactly(
                List.of("dub", "test"),
                List.of("dub", "build"));
        // RACKET: BUILD only when main.rkt exists (the v1.164.0 law)
        assertThat(commands(project("info.rkt"))).containsExactly(
                List.of("raco", "test", "."));
        assertThat(commands(project("info.rkt", "main.rkt"))).containsExactly(
                List.of("raco", "test", "."),
                List.of("raco", "make", "main.rkt"));
        // ELM: BUILD only when src/Main.elm exists
        assertThat(commands(project("elm.json"))).containsExactly(
                List.of("npx", "elm-test"));
        assertThat(commands(project("elm.json", "src/Main.elm"))).containsExactly(
                List.of("npx", "elm-test"),
                List.of("npx", "elm", "make", "src/Main.elm"));
        // DART: tests only with the conventional test/ dir
        assertThat(commands(project("pubspec.yaml"))).isEmpty();
        assertThat(commands(project("pubspec.yaml", "test/"))).containsExactly(
                List.of("dart", "test"));
    }

    @Test
    @DisplayName("The contract chains plan their check/build verbs")
    void contractKinds() throws IOException {
        assertThat(commands(project("foundry.toml"))).containsExactly(
                List.of("forge", "test"),
                List.of("forge", "build"));
        assertThat(commands(project("Scarb.toml"))).containsExactly(
                List.of("scarb", "test"),
                List.of("scarb", "build"));
        // MOVE is dialect-aware: a bare Move.toml is Sui-first
        assertThat(commands(project("Move.toml"))).containsExactly(
                List.of("sui", "move", "test"),
                List.of("sui", "move", "build"));
    }

    @Test
    @DisplayName("The scripting kinds probe before planning (RUBY/PHP/PYTHON/BUN/DENO/ADA)")
    void scriptingKinds() throws IOException {
        assertThat(commands(project("pyproject.toml"))).containsExactly(
                List.of("python3", "-m", "pytest"));
        // RUBY: rspec with spec/, rake with a Rakefile, neither without
        assertThat(commands(project("Gemfile", "spec/"))).containsExactly(
                List.of("bundle", "exec", "rspec"));
        assertThat(commands(project("Gemfile", "Rakefile"))).containsExactly(
                List.of("rake", "test"));
        assertThat(commands(project("Gemfile"))).isEmpty();
        // PHP: only a declared phpunit setup plans tests; local binary preferred
        assertThat(commands(project("composer.json"))).isEmpty();
        assertThat(commands(project("composer.json", "phpunit.xml"))).containsExactly(
                List.of("phpunit"));
        assertThat(commands(project("composer.json", "phpunit.xml.dist"))).containsExactly(
                List.of("phpunit"));
        assertThat(commands(project("composer.json", "vendor/bin/phpunit"))).containsExactly(
                List.of("./vendor/bin/phpunit"));
        // BUN: tests always, build only with a build script
        assertThat(commands(project("bunfig.toml"))).containsExactly(
                List.of("bun", "test"));
        // DENO: lint, fmt, and test all ship in the runtime — zero-config
        // full gate (v1.350.0)
        assertThat(commands(project("deno.json"))).containsExactly(
                List.of("deno", "lint"),
                List.of("deno", "fmt", "--check"),
                List.of("deno", "test"));
        // ADA: no universal test verb — build is the gate
        assertThat(commands(project("alire.toml"))).containsExactly(
                List.of("alr", "build"));
    }

    @Test
    @DisplayName("A BUN project with a build script plans bun run build")
    void bunWithBuildScript() throws IOException {
        File dir = project("bunfig.toml");
        Files.writeString(new File(dir, "package.json").toPath(),
                "{\"scripts\":{\"build\":\"bun build ./index.ts\"}}");
        assertThat(commands(dir)).containsExactly(
                List.of("bun", "test"),
                List.of("bun", "run", "build"));
    }

    @Test
    @DisplayName("Guess-verb kinds (make/cmake) plan only the git check")
    void guessVerbKindsStayOut() throws IOException {
        File make = project("Makefile");
        assertThat(commands(make)).isEmpty();
        File cmake = project("CMakeLists.txt");
        Files.createDirectories(new File(cmake, ".git").toPath());
        assertThat(commands(cmake)).containsExactly(
                List.of("git", "status", "--porcelain"));
    }

    @Test
    @DisplayName("hasLintConfig recognizes each config spelling, and only real files")
    void lintConfigSpellings() throws IOException {
        for (String config : new String[]{"eslint.config.js", "eslint.config.mjs",
            ".eslintrc", ".eslintrc.json", ".eslintrc.js", ".eslintrc.cjs",
            "biome.json", "biome.jsonc"}) {
            assertThat(PreflightPlan.hasLintConfig(project(config)))
                    .as(config).isTrue();
        }
        assertThat(PreflightPlan.hasLintConfig(project())).isFalse();
    }
}
