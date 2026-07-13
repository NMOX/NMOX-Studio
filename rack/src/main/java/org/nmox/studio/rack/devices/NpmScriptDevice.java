package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * NPM-9000 Script Sequencer: runs package.json scripts. The script
 * selector knob is populated from the current project's package.json.
 */
public class NpmScriptDevice extends CommandDevice {

    // "auto" appended, not inserted: knob positions persist by index and
    // saved patches that pinned an engine must keep it (v1.59.0 law)
    private static final String[] MANAGERS = {"npm", "yarn", "pnpm", "auto"};

    private final Knob scriptKnob;
    private final Knob managerKnob;

    public NpmScriptDevice() {
        super("npm-script", "NPM-9000", "SCRIPT SEQUENCER", new Color(203, 56, 55), 2);

        RackButton runButton = place(new RackButton("RUN", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        runButton.setCommandPreview(this::commandPreview);
        RackButton stopButton = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 52);
        scriptKnob = place(new Knob("SCRIPT", new String[]{"—"}, 0), 180, 40);
        managerKnob = place(new Knob("ENGINE", MANAGERS, 3), 254, 40);

        runButton.addActionListener(e -> primaryAction());
        stopButton.addActionListener(e -> stopProcess());

        // by name: script indexes shift per project, names travel
        paramByName("script", scriptKnob);
        param("manager", managerKnob);
    }

    @Override
    protected void onAttached() {
        reloadScripts();
    }

    @Override
    public void projectChanged(File dir) {
        reloadScripts();
    }

    /** Manifest pulse: a saved package.json refreshes the SCRIPT knob. */
    @Override
    public void manifestChanged(java.util.List<java.nio.file.Path> changed) {
        if (anyNamed(changed, "package.json")) {
            offEdt(this::reloadScripts);
        }
    }

    /** The faceplate context menu's "Open package.json". */
    @Override
    public java.util.Optional<File> primaryManifest() {
        File pkg = new File(commandDir(), "package.json");
        return pkg.isFile() ? java.util.Optional.of(pkg) : java.util.Optional.empty();
    }

    /** A package dir's scripts, read directly (no root-cache indirection). */
    private static java.util.Map<String, String> readScriptsAt(File dir) {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        File pkg = new File(dir, "package.json");
        if (!pkg.isFile()) {
            return result;
        }
        try {
            org.json.JSONObject json = new org.json.JSONObject(
                    java.nio.file.Files.readString(pkg.toPath(),
                            java.nio.charset.StandardCharsets.UTF_8));
            org.json.JSONObject scripts = json.optJSONObject("scripts");
            if (scripts != null) {
                for (String key : scripts.keySet()) {
                    result.put(key, scripts.optString(key, ""));
                }
            }
        } catch (java.io.IOException | org.json.JSONException ex) {
            // unreadable manifest = no scripts, same as the root reader
        }
        return result;
    }

    void reloadScripts() {
        File pkg = new File(commandDir(), "package.json");
        // WAYPOINT-steered: the SCRIPT knob lists the chosen package's
        // scripts; at root it lists the root manifest's, as always
        java.io.File ws = workspaceDir();
        List<String> names = new ArrayList<>(
                (ws != null ? readScriptsAt(ws) : ProjectInspector.scripts(projectDir())).keySet());
        if (names.isEmpty()) {
            names.add("—");
        }
        names.sort(String::compareTo);
        onEdt(() -> {
            // equality-guarded: setOptions always fires a knob change, and
            // a reload that found the same scripts must not re-fire (the
            // manifest pulse would otherwise flash every listener per save)
            String[] fresh = names.toArray(new String[0]);
            if (!java.util.Arrays.equals(fresh, scriptKnob.getOptions())) {
                scriptKnob.setOptions(fresh);
            }
            statusLcd.setText(pkg.isFile() ? names.size() + " SCRIPTS LOADED" : "NO PACKAGE.JSON");
        });
    }

    /** Test seam: the SCRIPT knob, so tests can count its change events. */
    Knob scriptKnobForTest() {
        return scriptKnob;
    }

    /** npm speaks from the package.json directory, monorepo or not. */
    @Override
    protected java.io.File commandDir() {
        java.io.File ws = workspaceDir();
        return ws != null ? ws
                : ProjectInspector.kindDir(projectDir(), ProjectInspector.ProjectKind.NODE);
    }

    @Override
    protected List<String> buildCommand() {
        String script = scriptKnob.getSelectedOption();
        if (script == null || "—".equals(script)) {
            return null;
        }
        String engine = MANAGERS[managerKnob.getSelectedIndex()];
        if ("auto".equals(engine)) {
            engine = ProjectInspector.nodePackageManager(projectDir());
        }
        return List.of(engine, "run", script);
    }
}
