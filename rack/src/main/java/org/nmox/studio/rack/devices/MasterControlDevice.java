package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.awt.Dimension;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;

/**
 * MAESTRO Master Control: the transport section. One big RUN button
 * fans a trigger out of four jacks at once - patch them into any
 * combination of devices to fire a whole pipeline; STOP ALL kills
 * every running process in the rack.
 */
public class MasterControlDevice extends RackDevice {

    private final Led pulseLed;

    public MasterControlDevice() {
        super("master", "MAESTRO", "MASTER CONTROL", new Color(240, 196, 25), 2);

        RackButton run = new RackButton("RUN SEQUENCE", new Color(80, 235, 100));
        run.setPreferredSize(new Dimension(130, 48));
        place(run, 44, 48);

        RackButton stopAll = new RackButton("STOP ALL", new Color(255, 70, 60));
        stopAll.setPreferredSize(new Dimension(100, 48));
        place(stopAll, 188, 48);

        pulseLed = place(new Led("PULSE", new Color(240, 196, 25)), 304, 56);

        run.addActionListener(e -> fireAll());
        stopAll.addActionListener(e -> {
            if (getRack() != null) {
                for (RackDevice d : getRack().getDevices()) {
                    d.panic();
                }
            }
        });

        addOutPort("trig1", "TRIG 1", SignalType.TRIGGER);
        addOutPort("trig2", "TRIG 2", SignalType.TRIGGER);
        addOutPort("trig3", "TRIG 3", SignalType.TRIGGER);
        addOutPort("trig4", "TRIG 4", SignalType.TRIGGER);
    }

    private void fireAll() {
        pulseLed.setOn(true);
        javax.swing.Timer t = new javax.swing.Timer(350, e -> pulseLed.setOn(false));
        t.setRepeats(false);
        t.start();
        emit("trig1", Signal.trigger());
        emit("trig2", Signal.trigger());
        emit("trig3", Signal.trigger());
        emit("trig4", Signal.trigger());
    }
}
