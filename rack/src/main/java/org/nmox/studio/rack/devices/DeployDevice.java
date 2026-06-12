package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.List;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.ToggleSwitch;

/**
 * LAUNCHPAD Deploy Output: pushes builds to the world. The LAUNCH
 * button only operates while the ARM switch is up - a missile-switch
 * guard against accidental production deploys.
 */
public class DeployDevice extends CommandDevice {

    private static final String[] TARGETS = {"npm script", "netlify", "vercel", "gh-pages", "npm publish (dry)"};

    private final Knob targetKnob;
    private final ToggleSwitch armSwitch;
    private final RackButton launchButton;
    private final Led armedLed;

    public DeployDevice() {
        super("deploy", "LAUNCHPAD", "DEPLOY OUTPUT", new Color(231, 52, 99), 2);

        targetKnob = place(new Knob("TARGET", TARGETS, 0), 44, 40);
        armSwitch = place(new ToggleSwitch("ARM", false, "ARMED", "SAFE"), 124, 42);
        launchButton = place(new RackButton("LAUNCH", RackStyle.STOP), 192, 52);
        armedLed = place(new Led("ARMED", RackStyle.STOP), 260, 58);

        launchButton.setEnabledLook(false);
        armSwitch.addChangeListener(() -> {
            boolean armed = armSwitch.isOn();
            launchButton.setEnabledLook(armed);
            armedLed.setBlinking(armed);
        });
        launchButton.addActionListener(e -> {
            if (armSwitch.isOn()) {
                primaryAction();
            }
        });

        // ARM is deliberately NOT persisted: a loaded patch must never
        // come up with the deploy pad hot
        param("target", targetKnob);
    }

    /** The RUN jack also respects the ARM switch; an unarmed pad ignores triggers. */
    @Override
    protected void primaryAction() {
        if (!armSwitch.isOn()) {
            onEdt(() -> statusLcd.setText("NOT ARMED — TRIGGER IGNORED"));
            return;
        }
        launch(buildCommand());
    }

    @Override
    protected List<String> buildCommand() {
        return switch (targetKnob.getSelectedOption()) {
            case "netlify" -> List.of("npx", "netlify", "deploy", "--prod");
            case "vercel" -> List.of("npx", "vercel", "--prod");
            case "gh-pages" -> List.of("npx", "gh-pages", "-d", "dist");
            case "npm publish (dry)" -> List.of("npm", "publish", "--dry-run");
            default -> List.of("npm", "run", "deploy");
        };
    }
}
