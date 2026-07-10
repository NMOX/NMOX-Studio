package org.nmox.studio.rack.devices;

import java.awt.Color;
import org.nmox.studio.rack.engine.RackBus;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.VuMeter;

/**
 * MONITOR Console: a rack-mounted terminal screen. Patched input always
 * displays; the TAP knob additionally taps the rack's monitor bus the
 * way a studio monitor section taps the mix - STDERR hears every error
 * any device prints (the factory position), ALL hears everything.
 * Bus errors glow red on the phosphor and flash the ERR LED.
 */
public class ConsoleDevice extends RackDevice {

    private static final String[] TAPS = {"off", "stderr", "all"};
    private static final Color ERR_TEXT = new Color(255, 110, 95);

    private final LcdDisplay screen;
    private final VuMeter meter;
    private final Knob tapKnob;
    private final Led errLed;
    private final javax.swing.Timer errFade;
    /** EDT-owned knob position mirrored for the pump threads. */
    private volatile String tapMode = "stderr";

    private final RackBus.Listener tap = (device, line, err) -> {
        String mode = tapMode;
        if ("off".equals(mode) || (!err && !"all".equals(mode))) {
            return;
        }
        show("[" + device + "] " + line, err);
    };

    public ConsoleDevice() {
        super("console", "MONITOR", "OUTPUT CONSOLE", new Color(80, 200, 120), 3);

        int colW = 150;
        int colX = RackStyle.RACK_WIDTH - RackStyle.EAR_WIDTH - colW;
        int screenW = RackStyle.RACK_WIDTH - 2 * RackStyle.EAR_WIDTH - colW - 28;
        screen = place(new LcdDisplay(screenW, 8), RackStyle.EAR_WIDTH + 14, 42);
        screen.getAccessibleContext().setAccessibleName("console output");
        RackButton clear = place(new RackButton("CLEAR", RackStyle.MUTATE), colX, 42);
        tapKnob = place(new Knob("TAP", TAPS, 1), colX + 70, 36);
        errLed = place(new Led("ERR", RackStyle.STOP), colX, 92);
        meter = place(new VuMeter("FEED", false), colX, 130);

        tapKnob.setToolTipText("<html><b>Monitor bus tap</b><br>"
                + "off — only what is patched into IN<br>"
                + "stderr — every device's errors, unpatched<br>"
                + "all — everything every device prints</html>");
        errFade = new javax.swing.Timer(600, e -> errLed.setOn(false));
        errFade.setRepeats(false);
        clear.addActionListener(e -> screen.clear());
        tapKnob.addChangeListener(() -> tapMode = tapKnob.getSelectedOption());

        param("tap", tapKnob);

        addInPort("in", "IN", SignalType.DATA);
    }

    private void show(String line, boolean err) {
        screen.appendLine(line, err ? ERR_TEXT : null);
        meter.pulse(0.4 + Math.min(0.55, line.length() / 140.0));
        if (err) {
            onEdt(() -> {
                errLed.setOn(true);
                errFade.restart();
            });
        }
    }

    @Override
    protected void onAttached() {
        RackBus.subscribe(tap);
    }

    @Override
    public void dispose() {
        RackBus.unsubscribe(tap);
        errFade.stop();
        super.dispose();
    }

    @Override
    public void receive(Port in, Signal signal) {
        if (signal.type() == SignalType.DATA) {
            show(signal.payload(), false);
        }
    }
}
