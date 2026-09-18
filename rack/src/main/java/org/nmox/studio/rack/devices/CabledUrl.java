package org.nmox.studio.rack.devices;

import java.util.function.Consumer;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.ui.controls.LcdDisplay;

/**
 * A URL that arrived by cable and may not have landed on the LCD yet.
 *
 * <p>The URL in-jack of SCOPE, PING, VITALS and GAUNTLET writes its LCD
 * through {@code onEdt}, which is {@code invokeLater} on the router
 * thread — while the action that READS the LCD ({@code open}, {@code
 * send}, {@code run}) runs on that same router thread, synchronously,
 * one signal later. The router is one FIFO thread and the EDT is
 * another, so {@code server.url → SCOPE.url} followed by {@code
 * server.ready → SCOPE.open} opened whatever the LCD showed BEFORE the
 * url signal: on a first serve that is the factory default, and the
 * Angular template on 4200 opened {@code http://localhost:5173}
 * (measured on the rack walk, 2026-09-17). The payload is kept here,
 * volatile and set synchronously in {@code receive()}, and cleared only
 * once the EDT has painted it — so a hand-typed URL after that still
 * wins, exactly as it always did.
 *
 * <p>A cable that carries something that is not a URL used to be
 * dropped in silence; refusals speak, so the payload is refused out
 * loud through the sink the device names (its status line, or the URL
 * LCD itself when the device has no other).
 */
final class CabledUrl {

    /** What a refused payload reads on the device's LCD (refusals speak). */
    static final String REFUSAL = "NOT A URL";

    /** Delivered by cable, not yet painted by the EDT; null once it has landed. */
    private volatile String pending;

    /**
     * Router thread: takes the signal's payload as the URL if it is one,
     * painting it onto {@code lcd} on the EDT; otherwise says {@link
     * #REFUSAL} through {@code refuse}. Returns whether it was a URL.
     */
    boolean deliver(Signal signal, LcdDisplay lcd, Consumer<String> refuse) {
        String payload = signal == null ? null : signal.payload();
        if (payload == null || !payload.startsWith("http")) {
            refuse.accept(REFUSAL + (payload == null || payload.isBlank() ? "" : " — " + head(payload)));
            return false;
        }
        pending = payload;
        onEdt(() -> {
            lcd.setText(payload);
            // this payload landed: the LCD is the truth again. A later
            // delivery has already replaced the field with its own payload
            // and keeps it until its own paint lands.
            if (payload.equals(pending)) {
                pending = null;
            }
        });
        return true;
    }

    /** The URL an action should use: the cabled one until it lands, else the LCD's. */
    String resolve(LcdDisplay lcd) {
        String p = pending;
        return p != null ? p : lcd.getText().trim();
    }

    /** Test seam: what is still waiting for the EDT. */
    String pending() {
        return pending;
    }

    /** The refused payload's head, in code points — a cut between the halves of an emoji is the v1.149.0 class. */
    static final int HEAD_CHARS = 40;

    private static String head(String payload) {
        String oneLine = payload.replace('\n', ' ').replace('\r', ' ').trim();
        if (oneLine.codePointCount(0, oneLine.length()) <= HEAD_CHARS) {
            return oneLine;
        }
        return oneLine.substring(0, oneLine.offsetByCodePoints(0, HEAD_CHARS)) + "…";
    }

    /** RackDevice.onEdt's rule (run now on the EDT, else invokeLater), for a non-device class. */
    private static void onEdt(Runnable r) {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            javax.swing.SwingUtilities.invokeLater(r);
        }
    }
}
