package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.List;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.RackButton;

/**
 * CRATE Package Manager: dependency installation and maintenance.
 * INSTALL is the primary action (fired by the RUN jack). The DEPS LCD
 * shows the declared dependency counts straight from package.json and
 * refreshes after every operation.
 */
public class PackageManagerDevice extends CommandDevice {

    private static final String[] MANAGERS = {"npm", "yarn", "pnpm"};

    private final Knob managerKnob;
    private final LcdDisplay depsLcd;

    public PackageManagerDevice() {
        super("package-manager", "CRATE", "PACKAGE MANAGER", new Color(214, 121, 41), 2);

        managerKnob = place(new Knob("ENGINE", MANAGERS, 0), 44, 40);

        RackButton install = place(new RackButton("INSTALL", new Color(80, 235, 100)), 122, 52);
        RackButton update = place(new RackButton("UPDATE", new Color(255, 190, 60)), 186, 52);
        RackButton outdated = place(new RackButton("CHECK", new Color(70, 170, 235)), 250, 52);
        RackButton stop = place(new RackButton("STOP", new Color(255, 70, 60)), 314, 52);
        depsLcd = place(new LcdDisplay(96, 1), 382, 52);
        depsLcd.setText("—");
        depsLcd.setToolTipText("dependencies + devDependencies declared in package.json");

        install.addActionListener(e -> launch(cmd("install")));
        update.addActionListener(e -> launch(cmd(manager().equals("yarn") ? "upgrade" : "update")));
        outdated.addActionListener(e -> launch(cmd("outdated")));
        stop.addActionListener(e -> stopProcess());

        param("manager", managerKnob);
    }

    private String manager() {
        return MANAGERS[managerKnob.getSelectedIndex()];
    }

    private List<String> cmd(String verb) {
        return List.of(manager(), verb);
    }

    @Override
    protected List<String> buildCommand() {
        return cmd("install");
    }

    @Override
    protected void onAttached() {
        refreshDepsLcd();
    }

    @Override
    public void projectChanged(File dir) {
        refreshDepsLcd();
    }

    /** Installs and updates change package.json; re-read the truth. */
    @Override
    protected void onFinished(int exitCode) {
        refreshDepsLcd();
    }

    private void refreshDepsLcd() {
        int[] counts = ProjectInspector.dependencyCounts(projectDir());
        onEdt(() -> {
            depsLcd.setText(counts == null ? "NO PKG" : counts[0] + "+" + counts[1] + " DEPS");
            depsLcd.setToolTipText(counts == null
                    ? "No package.json in the project"
                    : counts[0] + " dependencies, " + counts[1] + " devDependencies");
        });
    }
}
