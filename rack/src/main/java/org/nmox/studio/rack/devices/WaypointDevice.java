package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * WAYPOINT Workspace Selector: ROSETTA one level down. In a JS
 * workspaces monorepo (package.json {@code "workspaces"} or
 * pnpm-workspace.yaml) the dial steers which PACKAGE the Node lanes
 * operate on — NPM-9000's scripts, PURITY, GLOSS, VERITAS and friends
 * all run in the chosen package's directory. {@code root} (the
 * default) leaves everything at the repository root, exactly as
 * before the device existed.
 */
public class WaypointDevice extends RackDevice {

    private final Knob workspaceKnob;
    private final LcdDisplay countLcd;
    private volatile LinkedHashMap<String, File> packages = new LinkedHashMap<>();

    public WaypointDevice() {
        super("waypoint", "WAYPOINT", "WORKSPACE SELECTOR", new Color(255, 179, 71), 2);

        workspaceKnob = place(new Knob("WORKSPACE", new String[]{"root"}, 0), 44, 40);
        countLcd = place(new LcdDisplay(330, 1), 130, 46);
        countLcd.getAccessibleContext().setAccessibleName("workspace packages");
        countLcd.setText("—");
        countLcd.setToolTipText(
                "Packages declared by package.json workspaces / pnpm-workspace.yaml");

        workspaceKnob.addChangeListener(this::apply);

        addOutPort("dir", "DIR", SignalType.DATA);

        // by name: package positions shift as the monorepo grows, names travel
        paramByName("workspace", workspaceKnob);
    }

    @Override
    protected void onAttached() {
        reload();
    }

    @Override
    public void projectChanged(File dir) {
        reload();
    }

    /** A saved manifest re-declares the package set. */
    @Override
    public void manifestChanged(java.util.List<java.nio.file.Path> changed) {
        boolean relevant = changed.stream().map(path -> path.getFileName().toString())
                .anyMatch(n -> "package.json".equals(n) || "pnpm-workspace.yaml".equals(n));
        if (relevant) {
            reload();
        }
    }

    @Override
    public void applyState(Map<String, String> state) {
        super.applyState(state);
        apply();
    }

    /** Removing the device must stop steering the rack (the ROSETTA law). */
    @Override
    public void dispose() {
        if (getRack() != null) {
            getRack().setWorkspaceOverride(null);
        }
        super.dispose();
    }

    /** Test seam: the WORKSPACE knob, so tests can await its options. */
    Knob workspaceKnobForTest() {
        return workspaceKnob;
    }

    private void reload() {
        File dir = projectDir();
        offEdt(() -> {
            LinkedHashMap<String, File> found = Workspaces.packages(dir);
            packages = found;
            String[] options = new String[found.size() + 1];
            options[0] = "root";
            int i = 1;
            for (String name : found.keySet()) {
                options[i++] = name;
            }
            onEdt(() -> {
                // equality-guarded: an unchanged package set must not re-fire
                if (!java.util.Arrays.equals(options, workspaceKnob.getOptions())) {
                    workspaceKnob.setOptions(options);
                }
                apply();
            });
        });
    }

    private void apply() {
        if (getRack() == null) {
            return;
        }
        String selected = workspaceKnob.getSelectedOption();
        File dir = "root".equals(selected) ? null : packages.get(selected);
        getRack().setWorkspaceOverride(dir == null ? null : dir.getAbsolutePath());
        int count = packages.size();
        String label = count == 0 ? "NO WORKSPACES"
                : count + (count == 1 ? " PACKAGE" : " PACKAGES");
        String active = dir == null ? "" : "  [" + selected + "]";
        onEdt(() -> {
            countLcd.setTextColor(dir != null ? RackStyle.LCD_AMBER : RackStyle.LCD_TEXT);
            countLcd.setText(label + active);
        });
        emit("dir", Signal.data(dir == null ? "root" : dir.getAbsolutePath()));
    }
}
