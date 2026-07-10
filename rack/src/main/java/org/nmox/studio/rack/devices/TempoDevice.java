package org.nmox.studio.rack.devices;

import java.awt.Color;
import javax.swing.Timer;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.ToggleSwitch;

/**
 * TEMPO Step Sequencer: the rack's transport clock. While running it
 * fires TICK at the dialed interval and BAR every fourth tick - patch
 * TICK into PING for a poor man's uptime monitor, or BAR into SENTRY
 * for a recurring security scan. RESET jack restarts the count;
 * RUN/HALT jacks gate it remotely.
 */
public class TempoDevice extends RackDevice {

    private static final String[] RATES = {"5s", "10s", "30s", "1m", "5m", "15m", "30m", "1h"};
    private static final int[] RATE_MS = {5_000, 10_000, 30_000, 60_000, 300_000, 900_000, 1_800_000, 3_600_000};

    private final Knob rateKnob;
    private final ToggleSwitch runSwitch;
    private final Led tickLed;
    private final Led barLed;
    private final LcdDisplay countLcd;
    private Timer timer;
    private long ticks;

    public TempoDevice() {
        super("tempo", "TEMPO", "STEP SEQUENCER", new Color(255, 211, 105), 2);

        rateKnob = place(new Knob("RATE", RATES, 2), 44, 40);
        runSwitch = place(new ToggleSwitch("CLOCK", false, "RUN", "HALT"), 124, 42);
        tickLed = place(new Led("TICK", new Color(255, 211, 105)), 200, 52);
        barLed = place(new Led("BAR", new Color(255, 140, 60)), 244, 52);
        countLcd = place(new LcdDisplay(120, 1), 292, 46);
        countLcd.getAccessibleContext().setAccessibleName("tick count");
        countLcd.setText("0");

        runSwitch.addChangeListener(this::syncTimer);
        rateKnob.addChangeListener(this::syncTimer);

        addInPort("run", "START", SignalType.TRIGGER);
        addInPort("halt", "STOP", SignalType.TRIGGER);
        // gate-driven clock: patch SURGE's RUNNING gate in and the clock
        // ticks exactly while the dev server lives - health checks,
        // recurring scans, whatever is cabled to TICK, only when it matters
        addInPort("enable", "ENABLE", SignalType.GATE);
        addOutPort("tick", "TICK", SignalType.TRIGGER);
        addOutPort("bar", "BAR", SignalType.TRIGGER);
        addOutPort("running", "RUNNING", SignalType.GATE);

        param("rate", rateKnob);
        param("running", runSwitch);
    }

    @Override
    public boolean isResumable() {
        return runSwitch.isOn();
    }

    @Override
    public void resume() {
        onEdt(() -> runSwitch.setOn(true)); // re-arm the transport clock after a crash
    }

    private void syncTimer() {
        onEdt(() -> {
            if (timer != null) {
                timer.stop();
                timer = null;
            }
            boolean run = runSwitch.isOn();
            emit("running", Signal.gate(run));
            if (!run) {
                tickLed.setOn(false);
                barLed.setOn(false);
                return;
            }
            ticks = 0;
            countLcd.setText("0");
            timer = new Timer(RATE_MS[rateKnob.getSelectedIndex()], e -> tick());
            timer.start();
        });
    }

    private void tick() {
        ticks++;
        countLcd.setText(String.valueOf(ticks));
        flash(tickLed);
        emit("tick", Signal.trigger());
        if (ticks % 4 == 0) {
            flash(barLed);
            emit("bar", Signal.trigger());
        }
    }

    private void flash(Led led) {
        led.setOn(true);
        Timer off = new Timer(250, e -> led.setOn(false));
        off.setRepeats(false);
        off.start();
    }

    @Override
    public void receive(Port in, Signal signal) {
        switch (in.getId()) {
            case "run" -> onEdt(() -> runSwitch.setOn(true));
            case "halt" -> onEdt(() -> runSwitch.setOn(false));
            case "enable" -> onEdt(() -> runSwitch.setOn(signal.high()));
            default -> {
            }
        }
    }

    @Override
    public void applyState(java.util.Map<String, String> state) {
        super.applyState(state);
        syncTimer();
    }

    @Override
    public void dispose() {
        onEdt(() -> {
            if (timer != null) {
                timer.stop();
                timer = null;
            }
        });
        super.dispose();
    }
}
