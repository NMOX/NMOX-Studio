package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.List;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.ToggleSwitch;

/**
 * GLOSS Formatter: prettier over the whole project. WRITE mode
 * rewrites files; CHECK mode only verifies (fails when unformatted).
 * On PHP lanes it runs Laravel Pint instead, same two modes.
 */
public class FormatDevice extends CommandDevice {

    private final ToggleSwitch writeSwitch;

    public FormatDevice() {
        super("format", "GLOSS", "CODE FORMATTER", new Color(73, 196, 184), 2);

        RackButton run = place(new RackButton("FORMAT", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        run.setCommandPreview(this::commandPreview);
        writeSwitch = place(new ToggleSwitch("MODE", true, "WRITE", "CHECK"), 112, 42);
        run.addActionListener(e -> primaryAction());

        param("write", writeSwitch);
    }

    @Override
    protected List<String> buildCommand() {
        // PHP lane: Laravel Pint writes by default; --test only verifies
        if (effectiveKind() == ProjectInspector.ProjectKind.PHP) {
            return writeSwitch.isOn()
                    ? List.of("vendor/bin/pint")
                    : List.of("vendor/bin/pint", "--test");
        }
        return List.of("npx", "prettier", writeSwitch.isOn() ? "--write" : "--check", ".");
    }
}
