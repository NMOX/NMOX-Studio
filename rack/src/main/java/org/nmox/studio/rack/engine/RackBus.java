package org.nmox.studio.rack.engine;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The rack's monitor bus: every line any device's process prints travels
 * over it, tagged with the device that produced it and whether it came
 * from stderr. A console can tap the bus the way a studio monitor
 * section taps the mix - hearing everything without being patched to
 * anything.
 */
public final class RackBus {

    /** Receives bus traffic. Called on process pump threads, never the EDT. */
    public interface Listener {

        void line(String device, String line, boolean err);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private RackBus() {
    }

    public static void subscribe(Listener l) {
        LISTENERS.add(l);
    }

    public static void unsubscribe(Listener l) {
        LISTENERS.remove(l);
    }

    public static void publish(String device, String line, boolean err) {
        for (Listener l : LISTENERS) {
            try {
                l.line(device, line, err);
            } catch (RuntimeException ignored) {
                // a misbehaving tap must not stall the output pump
            }
        }
    }
}
