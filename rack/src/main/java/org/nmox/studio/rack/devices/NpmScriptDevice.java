package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.RackButton;

/**
 * NPM-9000 Script Sequencer: runs package.json scripts. The script
 * selector knob is populated from the current project's package.json.
 */
public class NpmScriptDevice extends CommandDevice {

    private static final String[] MANAGERS = {"npm", "yarn", "pnpm"};

    private final Knob scriptKnob;
    private final Knob managerKnob;
    private final RackButton runButton;
    private final RackButton stopButton;

    public NpmScriptDevice() {
        super("npm-script", "NPM-9000", "SCRIPT SEQUENCER", new Color(203, 56, 55), 2);

        scriptKnob = place(new Knob("SCRIPT", new String[]{"—"}, 0), 44, 40);
        managerKnob = place(new Knob("ENGINE", MANAGERS, 0), 118, 40);
        runButton = place(new RackButton("RUN", new Color(80, 235, 100)), 200, 52);
        stopButton = place(new RackButton("STOP", new Color(255, 70, 60)), 264, 52);

        runButton.addActionListener(e -> primaryAction());
        stopButton.addActionListener(e -> stopProcess());

        param("script", scriptKnob);
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

    private void reloadScripts() {
        File pkg = new File(projectDir(), "package.json");
        List<String> names = new ArrayList<>();
        if (pkg.isFile()) {
            try {
                JSONObject json = new JSONObject(Files.readString(pkg.toPath()));
                if (json.has("scripts")) {
                    for (String key : json.getJSONObject("scripts").keySet()) {
                        names.add(key);
                    }
                }
            } catch (Exception ex) {
                // unreadable package.json: leave the placeholder
            }
        }
        if (names.isEmpty()) {
            names.add("—");
        }
        names.sort(String::compareTo);
        onEdt(() -> {
            scriptKnob.setOptions(names.toArray(new String[0]));
            statusLcd.setText(pkg.isFile() ? names.size() + " SCRIPTS LOADED" : "NO PACKAGE.JSON");
        });
    }

    @Override
    protected List<String> buildCommand() {
        String script = scriptKnob.getSelectedOption();
        if (script == null || "—".equals(script)) {
            return null;
        }
        return List.of(MANAGERS[managerKnob.getSelectedIndex()], "run", script);
    }
}
