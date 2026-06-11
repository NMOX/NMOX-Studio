package org.nmox.studio.rack.model;

/**
 * A value travelling down a patch cable.
 *
 * @param type the signal kind (must match the emitting port's type)
 * @param payload text payload for DATA signals, or a short event note
 * @param high for GATE signals: true = gate opened, false = closed.
 *             For TRIGGER signals: true = success-ish, false = failure-ish.
 */
public record Signal(SignalType type, String payload, boolean high) {

    public static Signal trigger() {
        return new Signal(SignalType.TRIGGER, "", true);
    }

    public static Signal trigger(boolean success) {
        return new Signal(SignalType.TRIGGER, "", success);
    }

    public static Signal data(String payload) {
        return new Signal(SignalType.DATA, payload, true);
    }

    public static Signal gate(boolean high) {
        return new Signal(SignalType.GATE, "", high);
    }
}
