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
 */
public class BuildDevice extends CommandDevice {

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
