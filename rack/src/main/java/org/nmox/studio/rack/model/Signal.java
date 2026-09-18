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

    /** The note a verdict trigger carries when the run succeeded. */
    public static final String VERDICT_OK = "ok";
    /** The note a verdict trigger carries when the run failed (or was refused). */
    public static final String VERDICT_FAIL = "fail";

    /**
     * A verdict-less pulse — MASTER's trigs, TEMPO's tick, REFLEX's
     * changed, SCOPE's opened. It rides {@code high=true} like an OK verdict
     * but carries NO note, which is how a consumer tells the two apart.
     */
    public static Signal trigger() {
        return new Signal(SignalType.TRIGGER, "", true);
    }

    /**
     * A verdict: OK/FAIL/DONE from a run (and a refused launch's FAIL+DONE).
     * The success bit rides {@code high}; the note says which it was.
     *
     * <p>WHY the note: the 2026-09-17 rack audit taught KVASIR's EXPLAIN jack
     * to ignore a high trigger so a {@code VERITAS done → EXPLAIN} cable stops
     * consulting the model on every GREEN run — and that guard also killed
     * every pulse cabled to EXPLAIN (a MASTER trig, a REFLEX save), because a
     * pulse is high too. Only an EXPLICIT OK is not a failure to explain, and
     * a bare boolean cannot say "explicit". The note can.
     */
    public static Signal trigger(boolean success) {
        return new Signal(SignalType.TRIGGER, success ? VERDICT_OK : VERDICT_FAIL, success);
    }

    /** True only for a trigger that is an explicit OK verdict — never for a pulse. */
    public boolean okVerdict() {
        return type == SignalType.TRIGGER && high && VERDICT_OK.equals(payload);
    }

    public static Signal data(String payload) {
        return new Signal(SignalType.DATA, payload, true);
    }

    public static Signal gate(boolean high) {
        return new Signal(SignalType.GATE, "", high);
    }
}
