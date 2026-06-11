package org.nmox.studio.rack.model;

import java.awt.Color;

/**
 * A patch cable between an output port and an input port.
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
