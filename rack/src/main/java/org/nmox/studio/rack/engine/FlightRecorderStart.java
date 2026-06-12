package org.nmox.studio.rack.engine;

import org.openide.modules.OnStart;

/**
 * A flight recorder that starts when you open the viewer has missed
 * the flight. Touch the recorder at module startup so the tape runs
 * from the first command of the session, BLACKBOX racked or not.
 */
@OnStart
public class FlightRecorderStart implements Runnable {

    @Override
    public void run() {
        FlightRecorder.getDefault();
    }
}
