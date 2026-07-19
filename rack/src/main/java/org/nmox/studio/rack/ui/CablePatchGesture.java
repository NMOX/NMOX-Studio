package org.nmox.studio.rack.ui;

import org.nmox.studio.rack.model.Port;

/**
 * The cable-patching gesture as a pure state machine, so click-to-click
 * patching (v1.95.0) shares one brain with the classic press-drag-release
 * and plain unit tests can pin every transition. Found by real use: the
 * rear rack is wider than a default window, so dragging a cable between
 * far-apart devices is physically awkward — click a jack to ARM it (the
 * cable follows the cursor), click a compatible jack to CONNECT, click
 * empty space or press Escape to cancel.
 *
 * <p>The panel feeds it press/release/escape events with the jack under
 * the cursor (already direction/type-checked via {@link Port#canConnectTo});
 * the machine answers with the action to take and whether a cable
 * preview should follow the mouse.
 */
final class CablePatchGesture {

    enum Action {
        /** Nothing to do. */
        NONE,
        /** Start (or keep) tracking; paint the preview from {@link #from}. */
        TRACK,
        /** Connect {@link #from} to the event's port, then reset. */
        CONNECT,
        /** Drop any armed state and preview. */
        CANCEL
    }

    private Port from;
    /** Armed by a click (press+release on the same jack): the preview
     *  persists between clicks instead of dying with the button. */
    private boolean sticky;

    Port from() {
        return from;
    }

    boolean isTracking() {
        return from != null;
    }

    boolean isSticky() {
        return sticky;
    }

    /** Mouse pressed on a jack (or null for empty rack space). */
    Action press(Port port) {
        if (sticky) {
            // second click of a click-to-click patch
            if (port == null) {
                reset();
                return Action.CANCEL;
            }
            if (from.canConnectTo(port)) {
                return Action.CONNECT; // caller connects from() -> port, then reset()
            }
            if (port.getDirection() == from.getDirection()) {
                from = port; // clicked another source-side jack: re-arm from it
                return Action.TRACK;
            }
            reset();
            return Action.CANCEL;
        }
        if (port != null) {
            from = port;
            return Action.TRACK;
        }
        return Action.NONE;
    }

    /**
     * Mouse released; {@code onSource} = the release landed back on the
     * armed jack itself (a click, not a drag), {@code target} = the
     * compatible jack under the cursor otherwise (null when none).
     */
    Action release(boolean onSource, Port target) {
        if (from == null || sticky) {
            // sticky connections/cancels happen on the PRESS of the second
            // click; its release is a no-op
            return Action.NONE;
        }
        if (onSource) {
            sticky = true; // a click arms; the preview now follows the mouse
            return Action.TRACK;
        }
        if (target != null) {
            return Action.CONNECT;
        }
        reset();
        return Action.CANCEL;
    }

    /** Escape (or any external cancel: flip to front, device removal). */
    Action escape() {
        if (from == null) {
            return Action.NONE;
        }
        reset();
        return Action.CANCEL;
    }

    void reset() {
        from = null;
        sticky = false;
    }
}
