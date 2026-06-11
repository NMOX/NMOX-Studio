package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.List;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.ToggleSwitch;

/**
 * GLOSS Formatter: prettier over the whole project. WRITE mode
 * rewrites files; CHECK mode only verifies (fails when unformatted).
 */
public class FormatDevice extends CommandDevice {

    private final ToggleSwitch writeSwitch;

    public FormatDevice() {
        super("format", "GLOSS", "CODE FORMATTER", new Color(73, 196, 184), 2);

        writeSwitch = place(new ToggleSwitch("MODE", true, "WRITE", "CHECK"), 50, 42);
        RackButton run = place(new RackButton("FORMAT", new Color(80, 235, 100)), 120, 52);
        run.addActionListener(e -> primaryAction());

        param("write", writeSwitch);
    }

    @Override
    protected List<String> buildCommand() {
        return List.of("npx", "prettier", writeSwitch.isOn() ? "--write" : "--check", ".");
    }
}
