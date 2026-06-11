package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.RackButton;
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

    private static final String[] TOOLS = {"auto", "vite", "webpack", "rollup", "esbuild", "parcel"};

    private final Knob toolKnob;
    private final ToggleSwitch prodSwitch;
    private final ToggleSwitch watchSwitch;

    public BuildDevice() {
        super("build", "FORGE", "BUILD ENGINE", new Color(232, 166, 35), 2);

        toolKnob = place(new Knob("TOOL", TOOLS, 0), 44, 40);
        prodSwitch = place(new ToggleSwitch("MODE", true, "PROD", "DEV"), 120, 42);
        watchSwitch = place(new ToggleSwitch("WATCH", false), 180, 42);
        RackButton build = place(new RackButton("BUILD", new Color(80, 235, 100)), 244, 52);
        RackButton stop = place(new RackButton("STOP", new Color(255, 70, 60)), 308, 52);

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
        if (ProjectInspector.hasScript(projectDir(), "build")) {
            return "npm-script";
        }
        String dep = ProjectInspector.firstDependency(projectDir(),
                "vite", "webpack", "rollup", "esbuild", "parcel");
        return dep != null ? dep : "npm-script";
    }

    @Override
    protected List<String> buildCommand() {
        boolean prod = prodSwitch.isOn();
        boolean watch = watchSwitch.isOn();
        List<String> cmd = new ArrayList<>();
        switch (effectiveTool()) {
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
            case "esbuild" -> cmd.addAll(List.of("npx", "esbuild", "--bundle", "src/index.js", "--outdir=dist"));
            case "parcel" -> cmd.addAll(List.of("npx", "parcel", watch ? "watch" : "build"));
            default -> cmd.addAll(List.of("npm", "run", "build"));
        }
        return cmd;
    }
}
