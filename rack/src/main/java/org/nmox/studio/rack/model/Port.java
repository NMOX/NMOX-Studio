package org.nmox.studio.rack.model;

/**
 * A jack on the back panel of a device. Outputs may fan out to many
 * inputs; inputs may receive from many outputs (a mult, in synth terms).
 */
public final class Port {

    public enum Direction { IN, OUT }

    private final RackDevice device;
    private final String id;
    private final String label;
    private final Direction direction;
    private final SignalType type;
    /** Center of the jack, in device-local coordinates (back view). */
    private int x;
    private int y;

    public Port(RackDevice device, String id, String label, Direction direction, SignalType type) {
        this.device = device;
        this.id = id;
        this.label = label;
        this.direction = direction;
        this.type = type;
    }

    public RackDevice getDevice() {
        return device;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public Direction getDirection() {
        return direction;
    }

    public SignalType getType() {
        return type;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setLocation(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /** Two ports can be cabled if directions oppose and types match. */
    public boolean canConnectTo(Port other) {
        return other != null
                && other.device != this.device
                && other.direction != this.direction
                && other.type == this.type;
    }

    @Override
    public String toString() {
        return device.getTitle() + "." + id + "(" + direction + " " + type + ")";
    }
}
