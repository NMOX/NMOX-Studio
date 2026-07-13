package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.ToggleSwitch;

/**
 * FORGE Build Engine: drives the project's bundler. AUTO uses the
 * package.json build script; the other positions call the tool directly.
 *
 * In WATCH mode the process never exits, so FORGE listens to the build
 * output instead and fires OK/FAIL on every rebuild - patch OK into
 * VERITAS or PING and each save ripples down the pipeline.
 */
public class BuildDevice extends CommandDevice {

    /** Rebuild-finished markers across vite, webpack/CRA, rollup, esbuild. */
    private static final String[] WATCH_OK_MARKERS = {
        "built in", "compiled successfully", "build completed", "build finished", "created "
    };
    private static final String[] WATCH_FAIL_MARKERS = {
        "build failed", "failed to compile", "error in ", "[!] error", "error ts"
    };
    private static final long WATCH_FIRE_COOLDOWN_MS = 1500;

    private volatile long lastWatchFire;

    // APPEND-ONLY: patches persist the knob by index (grunt=6, gulp=7 since v1.34)
    private static final String[] TOOLS = {"auto", "vite", "webpack", "rollup", "esbuild", "parcel", "grunt", "gulp"};

    private final Knob toolKnob;
    private final ToggleSwitch prodSwitch;
    private final ToggleSwitch watchSwitch;

    public BuildDevice() {
        super("build", "FORGE", "BUILD ENGINE", new Color(232, 166, 35), 2);

        RackButton build = place(new RackButton("BUILD", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        build.setCommandPreview(this::commandPreview);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 52);
        toolKnob = place(new Knob("TOOL", TOOLS, 0), 180, 40);
        prodSwitch = place(new ToggleSwitch("MODE", true, "PROD", "DEV"), 254, 42);
        watchSwitch = place(new ToggleSwitch("WATCH", false), 324, 42);

        build.addActionListener(e -> primaryAction());
        stop.addActionListener(e -> stopProcess());

        param("tool", toolKnob);
        param("prod", prodSwitch);
        param("watch", watchSwitch);
    }

    @Override
    protected void onLine(String line) {
        if (!watchSwitch.isOn() || !isProcessRunning()) {
            return;
        }
        String lower = line.toLowerCase();
        boolean ok = matchesAny(lower, WATCH_OK_MARKERS);
        boolean fail = !ok && matchesAny(lower, WATCH_FAIL_MARKERS);
        if (!ok && !fail) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastWatchFire < WATCH_FIRE_COOLDOWN_MS) {
            return;
        }
        lastWatchFire = now;
        onEdt(() -> {
            okLed.setOn(ok);
            failLed.setOn(fail);
            statusLcd.setTextColor(ok ? org.nmox.studio.rack.ui.controls.RackStyle.LCD_TEXT
                    : new Color(255, 90, 80));
            statusLcd.setText(ok ? "REBUILT — WATCHING" : "REBUILD FAILED — WATCHING");
        });
        emit(ok ? "ok" : "fail", org.nmox.studio.rack.model.Signal.trigger(ok));
    }

    private static boolean matchesAny(String line, String[] markers) {
        for (String marker : markers) {
            if (line.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the AUTO knob position to what the project actually uses:
     * a "build" script wins; otherwise the bundler found in the
     * dependencies; otherwise fall through to `npm run build`.
     */
    private String effectiveTool() {
        String tool = toolKnob.getSelectedOption();
        if (!"auto".equals(tool)) {
            return tool;
        }
        // non-Node toolchains build with their own tool; the classic web
        // kinds map straight onto their knob positions
        ProjectInspector.ProjectKind kind = effectiveKind();
        switch (kind) {
            case WEBPACK -> {
                return "webpack";
            }
            case GRUNT -> {
                return "grunt";
            }
            case GULP -> {
                return "gulp";
            }
            case BOWER, STATIC, NODE, NONE -> {
                // fall through to the script/dependency/config-file scan
            }
            default -> {
                return "kind:" + kind.name();
            }
        }
        if (ProjectInspector.hasScript(projectDir(), "build")) {
            return "npm-script";
        }
        String dep = ProjectInspector.firstDependency(projectDir(),
                "vite", "webpack", "rollup", "esbuild", "parcel");
        if (dep != null) {
            return dep;
        }
        // a legacy repo declares its build tool by config file, not by deps
        File dir = ProjectInspector.kindDir(projectDir(), ProjectInspector.ProjectKind.NODE);
        if (ProjectInspector.hasManifestAt(dir, ProjectInspector.ProjectKind.WEBPACK)) {
            return "webpack";
        }
        if (ProjectInspector.hasManifestAt(dir, ProjectInspector.ProjectKind.GRUNT)) {
            return "grunt";
        }
        if (ProjectInspector.hasManifestAt(dir, ProjectInspector.ProjectKind.GULP)) {
            return "gulp";
        }
        return "npm-script";
    }

    /** The build command for non-Node toolchains. */
    private List<String> kindCommand(ProjectInspector.ProjectKind kind, boolean prod) {
        return switch (kind) {
            case BUN -> List.of("bun", "run", "build");
            case DENO -> List.of("deno", "task", "build");
            case RUST -> prod ? List.of("cargo", "build", "--release") : List.of("cargo", "build");
            case FOUNDRY -> List.of("forge", "build");
            case GO -> List.of("go", "build", "./...");
            case ELIXIR -> List.of("mix", "compile");
            case ERLANG -> List.of("rebar3", "compile");
            case CLOJURE -> List.of("clojure", "-P"); // prepare deps; build aliases vary
            case SWIFT -> prod ? List.of("swift", "build", "-c", "release") : List.of("swift", "build");
            case DOTNET -> prod ? List.of("dotnet", "build", "-c", "Release") : List.of("dotnet", "build");
            case DART -> List.of("dart", "compile", "exe", "bin/main.dart");
            case SCALA -> List.of("sbt", "compile");
            case HASKELL -> List.of("stack", "build");
            case ZIG -> prod ? List.of("zig", "build", "-Doptimize=ReleaseFast") : List.of("zig", "build");
            case GLEAM -> List.of("gleam", "build");
            case OCAML -> List.of("dune", "build");
            case CRYSTAL -> List.of("shards", "build");
            case MAVEN -> List.of("mvn", "-q", "package", "-DskipTests");
            case GRADLE -> List.of("gradle", "build", "-x", "test");
            case CMAKE -> List.of("cmake", "--build", "build");
            case MAKE -> List.of("make");
            case PYTHON -> List.of("python3", "-m", "compileall", "-q", ".");
            case RUBY -> List.of("rake", "build");
            case PHP -> List.of("composer", "install", "--no-dev", "--optimize-autoloader");
            default -> List.of("npm", "run", "build");
        };
    }

    // No manifestChanged override: FORGE is stateless — effectiveTool()
    // re-resolves scripts/deps/config files on every buildCommand(), so
    // there is nothing cached for a manifest pulse to invalidate.

    /** The faceplate context menu's "Open <build config>". */
    @Override
    public java.util.Optional<File> primaryManifest() {
        File dir = ProjectInspector.kindDir(projectDir(), ProjectInspector.ProjectKind.NODE);
        String tool = effectiveTool();
        String[] candidates = switch (tool) {
            case "webpack" -> new String[]{"webpack.config.js", "webpack.config.cjs", "webpack.config.mjs"};
            case "grunt" -> new String[]{"Gruntfile.js", "Gruntfile.coffee"};
            case "gulp" -> new String[]{"gulpfile.js", "gulpfile.babel.js", "gulpfile.mjs"};
            default -> new String[0];
        };
        for (String name : candidates) {
            File config = new File(dir, name);
            if (config.isFile()) {
                return java.util.Optional.of(config);
            }
        }
        if (tool.startsWith("kind:")) {
            ProjectInspector.ProjectKind kind =
                    ProjectInspector.ProjectKind.valueOf(tool.substring(5));
            File manifest = new File(ProjectInspector.kindDir(projectDir(), kind), kind.manifest());
            if (manifest.isFile()) {
                return java.util.Optional.of(manifest);
            }
        }
        File pkg = new File(dir, "package.json");
        return pkg.isFile() ? java.util.Optional.of(pkg) : java.util.Optional.empty();
    }

    /** Builds run where the effective toolchain's manifest lives. */
    @Override
    protected java.io.File commandDir() {
        String tool = effectiveTool();
        ProjectInspector.ProjectKind kind = tool.startsWith("kind:")
                ? ProjectInspector.ProjectKind.valueOf(tool.substring(5))
                : ProjectInspector.ProjectKind.NODE;
        return ProjectInspector.kindDir(projectDir(), kind);
    }

    @Override
    protected List<String> buildCommand() {
        boolean prod = prodSwitch.isOn();
        boolean watch = watchSwitch.isOn();
        List<String> cmd = new ArrayList<>();
        String tool = effectiveTool();
        if (tool.startsWith("kind:")) {
            return kindCommand(ProjectInspector.ProjectKind.valueOf(tool.substring(5)), prod);
        }
        switch (tool) {
            case "vite" -> {
                cmd.addAll(List.of("npx", "vite", "build"));
                if (watch) {
                    cmd.add("--watch");
                }
                if (!prod) {
                    cmd.addAll(List.of("--mode", "development"));
                }
            }
            case "webpack" -> {
                cmd.addAll(List.of("npx", "webpack", "--mode", prod ? "production" : "development"));
                if (watch) {
                    cmd.add("--watch");
                }
            }
            case "rollup" -> {
                cmd.addAll(List.of("npx", "rollup", "-c"));
                if (watch) {
                    cmd.add("--watch");
                }
            }
            case "esbuild" -> {
                cmd.addAll(List.of("npx", "esbuild", "--bundle", "src/index.js", "--outdir=dist"));
                if (watch) {
                    cmd.add("--watch");
                }
            }
            case "parcel" -> cmd.addAll(List.of("npx", "parcel", watch ? "watch" : "build"));
            // the classic runners take a task, not a flag: watch is a task
            case "grunt" -> cmd.addAll(watch
                    ? List.of("npx", "grunt", "watch") : List.of("npx", "grunt"));
            case "gulp" -> cmd.addAll(watch
                    ? List.of("npx", "gulp", "watch") : List.of("npx", "gulp"));
            default -> cmd.addAll(List.of("npm", "run", "build"));
        }
        return cmd;
    }
}
