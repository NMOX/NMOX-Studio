package org.nmox.studio.rack.devices;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The ship-readiness checklist, planned from what the project actually
 * is. The conviction behind it: "done" should be a machine-checkable
 * state, not a feeling - every gap between looks-done and is-done is a
 * bug with a delay on it.
 */
public final class PreflightPlan {

    /** How a check decides it passed. */
    public enum Pass {
        EXIT_ZERO,
        /** Passes only when the command prints nothing (git status --porcelain). */
        EMPTY_OUTPUT
    }

    /**
     * One checklist row. Soft checks warn instead of blocking the
     * verdict - advisory, like a CHANGELOG nudge.
     */
    public record Check(String name, List<String> command, Pass pass, boolean soft) {
    }

    private PreflightPlan() {
    }

    /** The checklist for this project, in run order. */
    public static List<Check> forProject(File dir) {
        List<Check> checks = new ArrayList<>();

        if (new File(dir, ".git").isDirectory()) {
            checks.add(new Check("GIT CLEAN",
                    List.of("git", "status", "--porcelain"), Pass.EMPTY_OUTPUT, false));
        }

        ProjectInspector.ProjectKind kind = ProjectInspector.detectKind(dir);
        switch (kind) {
            case NODE -> {
                if (ProjectInspector.hasScript(dir, "test")) {
                    checks.add(new Check("TESTS", List.of("npm", "test"), Pass.EXIT_ZERO, false));
                }
                if (ProjectInspector.hasScript(dir, "build")) {
                    checks.add(new Check("BUILD", List.of("npm", "run", "build"), Pass.EXIT_ZERO, false));
                }
                if (hasLintConfig(dir)) {
                    checks.add(new Check("LINT", List.of("npx", "eslint", "."), Pass.EXIT_ZERO, false));
                }
                checks.add(new Check("AUDIT",
                        List.of("npm", "audit", "--omit=dev", "--audit-level=high"),
                        Pass.EXIT_ZERO, true));
            }
            case RUST -> {
                checks.add(new Check("TESTS", List.of("cargo", "test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("cargo", "build", "--release"), Pass.EXIT_ZERO, false));
                checks.add(new Check("LINT", List.of("cargo", "clippy", "--", "-D", "warnings"), Pass.EXIT_ZERO, true));
            }
            case GO -> {
                checks.add(new Check("TESTS", List.of("go", "test", "./..."), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("go", "build", "./..."), Pass.EXIT_ZERO, false));
                checks.add(new Check("VET", List.of("go", "vet", "./..."), Pass.EXIT_ZERO, true));
            }
            case PYTHON -> {
                checks.add(new Check("TESTS", List.of("python3", "-m", "pytest"), Pass.EXIT_ZERO, false));
            }
            case MAVEN -> {
                checks.add(new Check("TESTS", List.of("mvn", "-q", "test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("mvn", "-q", "package", "-DskipTests"), Pass.EXIT_ZERO, false));
            }
            case CLARITY -> {
                // v1.161.0 made CLARITY outrank NODE, which silently
                // dropped a Clarinet repo's whole npm ship-check plan —
                // the contract check AND the harness tests both belong here
                checks.add(new Check("CHECK", List.of("clarinet", "check"), Pass.EXIT_ZERO, false));
                if (ProjectInspector.hasScript(dir, "test")) {
                    checks.add(new Check("TESTS", List.of("npm", "test"), Pass.EXIT_ZERO, false));
                }
            }
            case AIKEN -> {
                // check compiles AND runs the declared tests
                checks.add(new Check("CHECK", List.of("aiken", "check"), Pass.EXIT_ZERO, false));
            }
            // ---- v1.163.0: every toolchain with honest test/build verbs
            // gets a ship plan — commands mirror the rack lane tables
            // (VERITAS/FORGE), builds release-hardened where the toolchain
            // offers it (the existing RUST --release convention). Kinds
            // whose verbs are guesses (make/cmake targets, task runners)
            // stay in the default deliberately: a false RED gate is worse
            // than no gate. ----
            case FOUNDRY -> {
                checks.add(new Check("TESTS", List.of("forge", "test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("forge", "build"), Pass.EXIT_ZERO, false));
            }
            case ELIXIR -> {
                checks.add(new Check("TESTS", List.of("mix", "test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("mix", "compile"), Pass.EXIT_ZERO, false));
            }
            case ERLANG -> {
                checks.add(new Check("TESTS", List.of("rebar3", "eunit"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("rebar3", "compile"), Pass.EXIT_ZERO, false));
            }
            case GLEAM -> {
                checks.add(new Check("TESTS", List.of("gleam", "test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("gleam", "build"), Pass.EXIT_ZERO, false));
            }
            case GRADLE -> {
                checks.add(new Check("TESTS", List.of("gradle", "test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("gradle", "build", "-x", "test"), Pass.EXIT_ZERO, false));
            }
            case SWIFT -> {
                checks.add(new Check("TESTS", List.of("swift", "test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("swift", "build", "-c", "release"), Pass.EXIT_ZERO, false));
            }
            case DOTNET -> {
                checks.add(new Check("TESTS", List.of("dotnet", "test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("dotnet", "build", "-c", "Release"), Pass.EXIT_ZERO, false));
            }
            case DART -> {
                // build targets vary (exe/js/aot) — tests are the honest gate
                checks.add(new Check("TESTS", List.of("dart", "test"), Pass.EXIT_ZERO, false));
            }
            case SCALA -> {
                checks.add(new Check("TESTS", List.of("sbt", "test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("sbt", "compile"), Pass.EXIT_ZERO, false));
            }
            case HASKELL -> {
                checks.add(new Check("TESTS", List.of("stack", "test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("stack", "build"), Pass.EXIT_ZERO, false));
            }
            case ZIG -> {
                checks.add(new Check("TESTS", List.of("zig", "build", "test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("zig", "build", "-Doptimize=ReleaseFast"), Pass.EXIT_ZERO, false));
            }
            case OCAML -> {
                checks.add(new Check("TESTS", List.of("dune", "runtest"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("dune", "build"), Pass.EXIT_ZERO, false));
            }
            case CRYSTAL -> {
                checks.add(new Check("TESTS", List.of("crystal", "spec"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("shards", "build"), Pass.EXIT_ZERO, false));
            }
            case BUN -> {
                checks.add(new Check("TESTS", List.of("bun", "test"), Pass.EXIT_ZERO, false));
                if (ProjectInspector.hasScript(dir, "build")) {
                    checks.add(new Check("BUILD", List.of("bun", "run", "build"), Pass.EXIT_ZERO, false));
                }
            }
            case DENO -> {
                // deno tasks live in deno.json, not package.json — tests
                // are the portable gate
                checks.add(new Check("TESTS", List.of("deno", "test"), Pass.EXIT_ZERO, false));
            }
            case JULIA -> {
                checks.add(new Check("TESTS",
                        List.of("julia", "--project=.", "-e", "using Pkg; Pkg.test()"), Pass.EXIT_ZERO, false));
            }
            case NIM -> {
                checks.add(new Check("TESTS", List.of("nimble", "test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("nimble", "build"), Pass.EXIT_ZERO, false));
            }
            case DLANG -> {
                checks.add(new Check("TESTS", List.of("dub", "test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("dub", "build"), Pass.EXIT_ZERO, false));
            }
            case RACKET -> {
                checks.add(new Check("TESTS", List.of("raco", "test", "."), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("raco", "make", "main.rkt"), Pass.EXIT_ZERO, false));
            }
            case ELM -> {
                checks.add(new Check("TESTS", List.of("npx", "elm-test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("npx", "elm", "make", "src/Main.elm"), Pass.EXIT_ZERO, false));
            }
            case RESCRIPT -> {
                // build-only toolchain: no standard test runner
                checks.add(new Check("BUILD", List.of("npx", "rescript", "build"), Pass.EXIT_ZERO, false));
            }
            case PURESCRIPT -> {
                checks.add(new Check("TESTS", List.of("spago", "test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("spago", "build"), Pass.EXIT_ZERO, false));
            }
            case VLANG -> {
                checks.add(new Check("TESTS", List.of("v", "test", "."), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("v", "."), Pass.EXIT_ZERO, false));
            }
            case CAIRO -> {
                checks.add(new Check("TESTS", List.of("scarb", "test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("scarb", "build"), Pass.EXIT_ZERO, false));
            }
            case MOVE -> {
                // dialect-aware: Aptos projects get aptos move test/compile
                checks.add(new Check("TESTS", ProjectInspector.moveTestCommand(dir), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", ProjectInspector.moveBuildCommand(dir), Pass.EXIT_ZERO, false));
            }
            case FORTRAN -> {
                checks.add(new Check("TESTS", List.of("fpm", "test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("fpm", "build"), Pass.EXIT_ZERO, false));
            }
            case ADA -> {
                // Alire has no universal test verb — build is the gate
                checks.add(new Check("BUILD", List.of("alr", "build"), Pass.EXIT_ZERO, false));
            }
            case CLOJURE -> {
                checks.add(new Check("TESTS", List.of("clojure", "-X:test"), Pass.EXIT_ZERO, false));
            }
            case RUBY -> {
                checks.add(new Check("TESTS",
                        new File(dir, "spec").isDirectory()
                                ? List.of("bundle", "exec", "rspec")
                                : List.of("rake", "test"), Pass.EXIT_ZERO, false));
            }
            case PHP -> {
                checks.add(new Check("TESTS",
                        new File(dir, "vendor/bin/phpunit").isFile()
                                ? List.of("./vendor/bin/phpunit")
                                : List.of("phpunit"), Pass.EXIT_ZERO, false));
            }
            default -> {
                // no toolchain (or make/cmake/task-runner kinds whose
                // targets are unknowable): git-clean is the whole list
            }
        }

        return checks;
    }

    /** Package-private: the config-presence seam tests drive directly. */
    static boolean hasLintConfig(File dir) {
        for (String f : new String[]{"eslint.config.js", "eslint.config.mjs", ".eslintrc",
            ".eslintrc.json", ".eslintrc.js", ".eslintrc.cjs",
            "biome.json", "biome.jsonc"}) {
            if (new File(dir, f).isFile()) {
                return true;
            }
        }
        return false;
    }

    /** The verdict for one finished check. */
    public static boolean passed(Check check, int exit, String output) {
        return switch (check.pass()) {
            case EXIT_ZERO -> exit == 0;
            case EMPTY_OUTPUT -> exit == 0 && output.isBlank();
        };
    }
}
