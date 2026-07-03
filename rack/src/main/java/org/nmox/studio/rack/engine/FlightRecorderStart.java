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
        // reading + parsing the whole journal is startup weight; it only
        // needs to be attached before the first command can run, and no
        // command can run before the UI is up
        org.openide.util.RequestProcessor.getDefault().post(() -> {
            FlightRecorder recorder = FlightRecorder.getDefault();
            String userdir = System.getProperty("netbeans.user");
            if (userdir != null) {
                recorder.attachJournal(new java.io.File(userdir, "var/nmox/flight.jsonl"));
            }
        });
    }
}
