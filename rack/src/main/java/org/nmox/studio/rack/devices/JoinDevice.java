package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.nmox.studio.rack.model.Cable;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * QUORUM Lane Join: the barrier where parallel lanes converge. It has
 * four trigger inputs; in ALL mode it fires OK once every wired input
 * has arrived and all of them passed (FAIL if any failed) - the
 * synchronizer for "web tests AND api tests pass, then deploy". In ANY
 * mode it relays the first arrival - a race. Patch each lane's DONE
 * jack into an IN, and OK into the shared downstream step (LAUNCHPAD,
 * PREFLIGHT…). RESET clears a half-collected round.
 */
public class JoinDevice extends RackDevice {

    private static final String[] MODES = {"ALL", "ANY"};
    /** ANY-mode debounce so one simultaneous batch fires once, not four times. */
    private static final long ANY_COOLDOWN_MS = 800;

    private final Knob modeKnob;
    private final LcdDisplay statusLcd;
    private final Led passLed;
    private final Led failLed;

    private final Object lock = new Object();
    private final Map<String, Boolean> arrivals = new HashMap<>();
    private volatile long lastAnyFire;

    public JoinDevice() {
        super("join", "QUORUM", "LANE JOIN", new Color(90, 190, 210), 2);

        RackButton reset = place(new RackButton("RESET", RackStyle.QUERY), RackStyle.TRANSPORT_X, 52);
        modeKnob = place(new Knob("MODE", MODES, 0), 180, 40);
        statusLcd = place(new LcdDisplay(190, 1), 270, 46);
        statusLcd.setText("WAITING");
        passLed = place(new Led("PASS", RackStyle.GO), 486, 52);
        failLed = place(new Led("FAIL", RackStyle.STOP), 530, 52);

        reset.setToolTipText("Clear a half-collected round of lane signals");
        reset.addActionListener(e -> reset());

        addInPort("in1", "IN 1", SignalType.TRIGGER);
        addInPort("in2", "IN 2", SignalType.TRIGGER);
        addInPort("in3", "IN 3", SignalType.TRIGGER);
        addInPort("in4", "IN 4", SignalType.TRIGGER);
        addOutPort("ok", "OK", SignalType.TRIGGER);
        addOutPort("fail", "FAIL", SignalType.TRIGGER);
        addOutPort("done", "DONE", SignalType.TRIGGER);

        param("mode", modeKnob);
    }

    /** The set of IN port ids that actually have a cable feeding them. */
    private Set<String> wiredInputIds() {
        Set<String> ids = new HashSet<>();
        Rack rack = getRack();
        if (rack == null) {
            return ids;
        }
        for (Cable c : rack.getCables()) {
            if (c.getTo().getDevice() == this) {
                ids.add(c.getTo().getId());
            }
        }
        return ids;
    }

    @Override
    public void receive(Port in, Signal signal) {
        if (signal.type() != SignalType.TRIGGER || !in.getId().startsWith("in")) {
            return;
        }
        boolean any = modeKnob.getSelectedIndex() == 1;
        if (any) {
            long now = System.currentTimeMillis();
            if (now - lastAnyFire < ANY_COOLDOWN_MS) {
                return; // same batch already relayed
            }
            lastAnyFire = now;
            synchronized (lock) {
                arrivals.clear();
            }
            fire(signal.high(), 1, 1);
            return;
        }
        boolean fire;
        boolean result;
        int have;
        int total;
        synchronized (lock) {
            arrivals.put(in.getId(), signal.high());
            Set<String> wired = wiredInputIds();
            wired.add(in.getId()); // count this lane even if cable bookkeeping lags
            total = wired.size();
            have = 0;
            boolean allPass = true;
            for (String id : wired) {
                Boolean passed = arrivals.get(id);
                if (passed == null) {
                    allPass = false;
                } else {
                    have++;
                    allPass &= passed;
                }
            }
            fire = have >= total;
            result = allPass;
            if (fire) {
                arrivals.clear();
            }
        }
        if (fire) {
            fire(result, have, total);
        } else {
            status(have, total);
        }
    }

    private void status(int have, int total) {
        onEdt(() -> {
            statusLcd.setTextColor(RackStyle.LCD_AMBER);
            statusLcd.setText(have + "/" + total + " LANES IN");
        });
    }

    private void fire(boolean ok, int have, int total) {
        onEdt(() -> {
            passLed.setOn(ok);
            failLed.setOn(!ok);
            statusLcd.setTextColor(ok ? RackStyle.LCD_TEXT : new Color(255, 90, 80));
            statusLcd.setText((ok ? "ALL PASS " : "FAIL ") + have + "/" + total);
        });
        emit(ok ? "ok" : "fail", Signal.trigger(ok));
        emit("done", Signal.trigger(ok));
    }

    private void reset() {
        synchronized (lock) {
            arrivals.clear();
        }
        onEdt(() -> {
            passLed.setOn(false);
            failLed.setOn(false);
            statusLcd.setTextColor(RackStyle.LCD_TEXT);
            statusLcd.setText("WAITING");
        });
    }
}
