package org.nmox.studio.rack.devices;

import java.awt.Color;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.ToggleSwitch;

/**
 * ATMOS Env Mixer: shapes the environment every other device's
 * commands run in. The NODE_ENV knob and CI switch publish to the
 * rack's shared env; EXTRA takes free-form KEY=VAL,KEY2=VAL2 pairs.
 */
public class EnvDevice extends RackDevice {

    private static final String[] NODE_ENVS = {"development", "test", "production"};

    private final Knob nodeEnvKnob;
    private final ToggleSwitch ciSwitch;
    private final LcdDisplay extraLcd;
    private String appliedExtras = "";

    public EnvDevice() {
        super("env", "ATMOS", "ENV MIXER", new Color(120, 144, 220), 2);

        nodeEnvKnob = place(new Knob("NODE_ENV", NODE_ENVS, 0), 44, 40);
        ciSwitch = place(new ToggleSwitch("CI", false), 124, 42);
        extraLcd = place(new LcdDisplay(280, 1), 190, 46);
        extraLcd.setText("");
        extraLcd.setEditable("Extra vars (KEY=VAL,KEY2=VAL2)");

        nodeEnvKnob.addChangeListener(this::apply);
        ciSwitch.addChangeListener(this::apply);
        extraLcd.addEditListener(this::apply);

        addOutPort("env", "ENV", SignalType.DATA);

        param("nodeEnv", nodeEnvKnob);
        param("ci", ciSwitch);
        param("extra", extraLcd);
    }

    @Override
    protected void onAttached() {
        apply();
    }

    private void apply() {
        if (getRack() == null) {
            return;
        }
        getRack().putEnv("NODE_ENV", NODE_ENVS[nodeEnvKnob.getSelectedIndex()]);
        getRack().putEnv("CI", ciSwitch.isOn() ? "true" : null);

        // retire previously applied extras, then apply the current set
        for (String pair : appliedExtras.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                getRack().putEnv(pair.substring(0, eq).trim(), null);
            }
        }
        appliedExtras = extraLcd.getText();
        for (String pair : appliedExtras.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                getRack().putEnv(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        emit("env", Signal.data("NODE_ENV=" + NODE_ENVS[nodeEnvKnob.getSelectedIndex()]
                + (ciSwitch.isOn() ? ",CI=true" : "")
                + (appliedExtras.isEmpty() ? "" : "," + appliedExtras)));
    }

    @Override
    public void applyState(java.util.Map<String, String> state) {
        super.applyState(state);
        apply();
    }
}
