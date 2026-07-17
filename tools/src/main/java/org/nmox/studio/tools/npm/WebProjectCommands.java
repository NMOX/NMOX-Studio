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
                // IDE's Run and the rack agree on what "run" means here
                return ActionProvider.COMMAND_RUN.equals(action)
                        ? List.of("python3", "-m", "http.server", "8000") : null;
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
