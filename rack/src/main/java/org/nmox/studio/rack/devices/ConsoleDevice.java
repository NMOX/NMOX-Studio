package org.nmox.studio.rack.devices;

import java.awt.Color;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.VuMeter;

/**
 * MONITOR Console: a rack-mounted terminal screen. Patch any device's
 * OUT jack into IN and its output scrolls across the green phosphor.
 */
public class ConsoleDevice extends RackDevice {

    private final LcdDisplay screen;
    private final VuMeter meter;

    public ConsoleDevice() {
        super("console", "MONITOR", "OUTPUT CONSOLE", new Color(80, 200, 120), 3);

        int screenW = RackStyle.RACK_WIDTH - 2 * RackStyle.EAR_WIDTH - 160;
        screen = place(new LcdDisplay(screenW, 8), RackStyle.EAR_WIDTH + 14, 42);
        RackButton clear = place(new RackButton("CLEAR", new Color(255, 190, 60)),
                RackStyle.RACK_WIDTH - RackStyle.EAR_WIDTH - 130, 42);
        meter = place(new VuMeter("FEED", false), RackStyle.RACK_WIDTH - RackStyle.EAR_WIDTH - 130, 92);

        clear.addActionListener(e -> screen.clear());

        addInPort("in", "IN", SignalType.DATA);
    }

    @Override
    public void receive(Port in, Signal signal) {
        if (signal.type() == SignalType.DATA) {
            screen.appendLine(signal.payload());
            meter.pulse(0.4 + Math.min(0.55, signal.payload().length() / 140.0));
        }
    }
}
