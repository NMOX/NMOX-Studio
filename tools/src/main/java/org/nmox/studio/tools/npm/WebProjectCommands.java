package org.nmox.studio.tools.npm;

import java.io.File;
import java.util.List;
import org.netbeans.spi.project.ActionProvider;
import org.nmox.studio.rack.devices.ProjectInspector;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;

/**
 * Maps a platform project action (Run / Build / Test / Clean) to the
 * command the project's toolchain actually expects, so the IDE's native
 * F6/F11/menu actions drive the real build. Pure and side-effect free:
 * the mapping is unit-tested without ever starting a process. A null
 * return means "this toolchain can't do that action" — the platform
 * greys the menu item out, which is the honest answer.
 */
final class WebProjectCommands {

    /**
     * The STATIC lane's fixed port — shared with the Run consumer, which
     * announces {@code http://localhost:STATIC_PORT/} when python's
     * "Serving HTTP on 0.0.0.0" banner appears (the banner itself never
     * contains a localhost URL for the scan to find).
     */
    static final int STATIC_PORT = 8000;

    private WebProjectCommands() {
    }

    /** The command for an action on this project, or null if unsupported. */
    static List<String> commandFor(File dir, ProjectKind kind, String action) {
        switch (kind) {
            case NODE:
                return node(dir, action);
            case RUST:
                return fixed(action, List.of("cargo", "run"), List.of("cargo", "build"),
                        List.of("cargo", "test"), List.of("cargo", "clean"));
            case FOUNDRY:
                // no single 'run' for a contract project - deploys are scripts
                return fixed(action, null, List.of("forge", "build"),
                        List.of("forge", "test"), List.of("forge", "clean"));
            case GO:
                return fixed(action, List.of("go", "run", "."), List.of("go", "build", "./..."),
                        List.of("go", "test", "./..."), List.of("go", "clean"));
            case ELIXIR:
                return fixed(action, List.of("mix", "run"), List.of("mix", "compile"),
                        List.of("mix", "test"), List.of("mix", "clean"));
            case MAVEN:
                return fixed(action, null, List.of("mvn", "package", "-DskipTests"),
                        List.of("mvn", "test"), List.of("mvn", "clean"));
            case GRADLE:
                return fixed(action, List.of("gradle", "run"), List.of("gradle", "build"),
                        List.of("gradle", "test"), List.of("gradle", "clean"));
            case SWIFT:
                return fixed(action, List.of("swift", "run"), List.of("swift", "build"),
                        List.of("swift", "test"), null);
            case GLEAM:
                return fixed(action, List.of("gleam", "run"), List.of("gleam", "build"),
                        List.of("gleam", "test"), List.of("gleam", "clean"));
            case JULIA:
                return fixed(action, null,
                        List.of("julia", "--project=.", "-e", "using Pkg; Pkg.precompile()"),
                        List.of("julia", "--project=.", "-e", "using Pkg; Pkg.test()"), null);
            case NIM:
                return fixed(action, List.of("nimble", "run"), List.of("nimble", "build"),
                        List.of("nimble", "test"), null);
            case DLANG:
                return fixed(action, List.of("dub", "run"), List.of("dub", "build"),
                        List.of("dub", "test"), List.of("dub", "clean"));
            case RACKET:
                return fixed(action, List.of("racket", "main.rkt"),
                        List.of("raco", "make", "main.rkt"),
                        List.of("raco", "test", "."), null);
            case ELM:
                return fixed(action, List.of("npx", "elm", "reactor"),
                        List.of("npx", "elm", "make", "src/Main.elm"),
                        List.of("npx", "elm-test"), null);
            case RESCRIPT:
                return fixed(action, null, List.of("npx", "rescript", "build"), null,
                        List.of("npx", "rescript", "clean"));
            case PURESCRIPT:
                return fixed(action, List.of("spago", "run"), List.of("spago", "build"),
                        List.of("spago", "test"), null);
            case VLANG:
                return fixed(action, List.of("v", "run", "."), List.of("v", "."),
                        List.of("v", "test", "."), null);
            case CAIRO:
                return fixed(action, List.of("scarb", "execute"), List.of("scarb", "build"),
                        List.of("scarb", "test"), null);
            case AIKEN:
                // validators have no run verb — build is the honest "make my
                // code" (the Move rule); check compiles AND runs the tests
                return fixed(action, List.of("aiken", "build"), List.of("aiken", "build"),
                        List.of("aiken", "check"), null);
            case CLARITY:
                // check IS the compile (Clarity is interpreted on-chain);
                // tests ride the npm vitest/simnet harness beside
                // Clarinet.toml, speaking the project's own package manager
                return fixed(action, null, List.of("clarinet", "check"),
                        node(dir, ActionProvider.COMMAND_TEST), null);
            case MOVE:
                // dialect-aware: Aptos projects (Move.toml names AptosFramework)
                // get aptos move compile/test, everything else Sui
                return fixed(action, ProjectInspector.moveBuildCommand(dir),
                        ProjectInspector.moveBuildCommand(dir),
                        ProjectInspector.moveTestCommand(dir), null);
            case FORTRAN:
                return fixed(action, List.of("fpm", "run"), List.of("fpm", "build"),
                        List.of("fpm", "test"), null);
            case ADA:
                return fixed(action, List.of("alr", "run"), List.of("alr", "build"),
                        null, null);
            case ZIG:
                return fixed(action, List.of("zig", "build", "run"), List.of("zig", "build"),
                        List.of("zig", "build", "test"), null);
            case DART:
                return fixed(action, List.of("dart", "run"), null, List.of("dart", "test"), null);
            case MAKE:
                return fixed(action, List.of("make", "run"), List.of("make"),
                        List.of("make", "test"), List.of("make", "clean"));
            case DOTNET:
                // v1.233.0: the one kind with a COMPLETE toolchain story
                // that fell to default-null — every .NET project greyed
                // all four actions since the kind shipped
                return fixed(action, List.of("dotnet", "run"), List.of("dotnet", "build"),
                        List.of("dotnet", "test"), List.of("dotnet", "clean"));
            case TACT:
                // npm-carried by design (v1.161.0): the compiler is an npm
                // dep and the kit's build/test are package.json scripts —
                // the lanes speak the project's own scripts, like NODE
                return node(dir, action);
            case CMAKE:
                // plansOnlyWhatExists (v1.164.0): with a configured build/
                // the real verbs run there; without one, Build offers the
                // configure step — the honest first move, never a guess
                if (new File(dir, "build").isDirectory()) {
                    return fixed(action, null,
                            List.of("cmake", "--build", "build"),
                            List.of("ctest", "--test-dir", "build"),
                            List.of("cmake", "--build", "build", "--target", "clean"));
                }
                return fixed(action, null, List.of("cmake", "-B", "build"), null, null);
            case PYTHON:
                return ActionProvider.COMMAND_TEST.equals(action)
                        ? List.of("python3", "-m", "pytest") : null;
            case RUBY:
                return ActionProvider.COMMAND_TEST.equals(action)
                        ? List.of("rake", "test") : null;
            case WEBPACK:
                // run = webpack-dev-server; when it isn't installed the
                // command's own error is the honest answer, no probing
                return fixed(action,
                        List.of("npx", "webpack", "serve", "--mode", "development"),
                        List.of("npx", "webpack", "--mode", "production"), null, null);
            case GRUNT:
                // a task runner has a default task, not a run/test story
                return fixed(action, null, List.of("npx", "grunt"), null, null);
            case GULP:
                return fixed(action, null, List.of("npx", "gulp"), null, null);
            case BOWER:
                // a package manager, not a build system - CRATE installs
                return null;
            case STATIC:
                // the same command IGNITION's static lane runs, so the
                // IDE's Run and the rack agree on what "run" means here.
                // -u is load-bearing (v1.216.0, the v1.37.0 lesson
                // relearned): piped python block-buffers its "Serving
                // HTTP on" banner, so without it the line consumer never
                // sees the announce and the serving chain stays dark.
                // the port is PROBED, not pinned (v1.320.0): python's
                // http.server refuses a busy port outright — it has no
                // http-server-style upward scan — and the learner walk of
                // space #89 died on exactly that. The banner parse carries
                // whatever port actually bound, so the serving chain follows.
                return ActionProvider.COMMAND_RUN.equals(action)
                        ? List.of("python3", "-u", "-m", "http.server",
                                String.valueOf(org.nmox.studio.core.util.FreePorts
                                        .firstFreeFrom(STATIC_PORT))) : null;
            // ---- v1.163.0: the kinds the rack always spoke but F6/F11
            // silently greyed — commands mirror the rack device tables ----
            case BUN:
                return fixed(action, List.of("bun", "run", "start"),
                        List.of("bun", "run", "build"), List.of("bun", "test"), null);
            case DENO:
                return fixed(action, List.of("deno", "task", "start"),
                        List.of("deno", "task", "build"), List.of("deno", "test"), null);
            case ERLANG:
                // BEAM apps run under mix/releases — no honest bare run
                return fixed(action, null, List.of("rebar3", "compile"),
                        List.of("rebar3", "eunit"), List.of("rebar3", "clean"));
            case CLOJURE:
                return fixed(action, List.of("clojure", "-M:run"),
                        List.of("clojure", "-P"), List.of("clojure", "-X:test"), null);
            case SCALA:
                return fixed(action, List.of("sbt", "run"), List.of("sbt", "compile"),
                        List.of("sbt", "test"), List.of("sbt", "clean"));
            case HASKELL:
                return fixed(action, List.of("stack", "run"), List.of("stack", "build"),
                        List.of("stack", "test"), List.of("stack", "clean"));
            case OCAML:
                // run needs an executable target name — dune can't guess it
                return fixed(action, null, List.of("dune", "build"),
                        List.of("dune", "runtest"), List.of("dune", "clean"));
            case CRYSTAL:
                return fixed(action, List.of("shards", "run"), List.of("shards", "build"),
                        List.of("crystal", "spec"), null);
            case PHP:
                // run is docroot-dependent (IGNITION owns `php -S`); the
                // test runner mirrors VERITAS' vendored-first resolution
                return fixed(action, null, null,
                        new File(dir, "vendor/bin/phpunit").isFile()
                                ? List.of("./vendor/bin/phpunit")
                                : List.of("phpunit"),
                        null);
            default:
                return null;
        }
    }

    /** Node leans on package.json scripts, so the available commands vary per project. */
    private static List<String> node(File dir, String action) {
        // npm/yarn/pnpm resolved from the project's own contract
        // (corepack pin, then lockfile) — never npm-in-a-pnpm-repo
        String pm = ProjectInspector.nodePackageManager(dir);
        switch (action) {
            case ActionProvider.COMMAND_RUN:
                if (ProjectInspector.hasScript(dir, "dev")) {
                    return List.of(pm, "run", "dev");
                }
                if (ProjectInspector.hasScript(dir, "start")) {
                    return List.of(pm, "start");
                }
                if (ProjectInspector.hasScript(dir, "serve")) {
                    return List.of(pm, "run", "serve");
                }
                return null;
            case ActionProvider.COMMAND_BUILD:
                return ProjectInspector.hasScript(dir, "build") ? List.of(pm, "run", "build") : null;
            case ActionProvider.COMMAND_TEST:
                return ProjectInspector.hasScript(dir, "test") ? List.of(pm, "test") : null;
            case ActionProvider.COMMAND_CLEAN:
                return ProjectInspector.hasScript(dir, "clean") ? List.of(pm, "run", "clean") : null;
            default:
                return null;
        }
    }

    private static List<String> fixed(String action, List<String> run, List<String> build,
            List<String> test, List<String> clean) {
        switch (action) {
            case ActionProvider.COMMAND_RUN:
                return run;
            case ActionProvider.COMMAND_BUILD:
                return build;
            case ActionProvider.COMMAND_TEST:
                return test;
            case ActionProvider.COMMAND_CLEAN:
                return clean;
            default:
                return null;
        }
    }
}
