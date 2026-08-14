package org.nmox.studio.rack.model;

import java.awt.Color;

/**
 * A patch cable between an output port and an input port — the rack's
 * whole pipeline idea in one small class. When a device finishes, the
 * {@link Rack} router looks up every cable whose {@code from} is that
 * device's firing OUT jack and delivers the signal to each cable's
 * {@code to} IN jack; chains like install → build → test are nothing
 * more than three cables. The color is cosmetic (assigned at patch
 * time so parallel runs are easy to follow on the rear view); identity
 * and routing live in the two port references. Cables are created and
 * removed through {@code Rack} so undo history and the trigger-cooldown
 * bookkeeping stay consistent — never construct one and stash it
 * elsewhere.
 */
public final class Cable {

    private final Port from;   // OUT port
    private final Port to;     // IN port
    private final Color color;

    public Cable(Port from, Port to, Color color) {
        if (from.getDirection() != Port.Direction.OUT || to.getDirection() != Port.Direction.IN) {
            throw new IllegalArgumentException("Cable must run OUT -> IN: " + from + " -> " + to);
        }
        this.from = from;
        this.to = to;
        this.color = color;
    }

    public Port getFrom() {
        return from;
    }

    public Port getTo() {
        return to;
    }

    public Color getColor() {
        return color;
    }

    public boolean touches(Port p) {
        return from == p || to == p;
    }

    public boolean touches(RackDevice d) {
        return from.getDevice() == d || to.getDevice() == d;
    }
}
