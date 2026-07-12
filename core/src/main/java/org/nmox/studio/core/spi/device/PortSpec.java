package org.nmox.studio.core.spi.device;

/**
 * One jack on the device's back panel. Ports connect only to ports of
 * the same signal kind, opposite direction — declare them here and the
 * host lays out the jack field, the patch-bay LEDs, and the rear view.
 *
 * <p>The port lexicon is law: a device with an IN named {@code serve}
 * or {@code start} must also declare an IN named {@code stop} (anything
 * you can start by cable you can stop by cable), and every GATE-kind
 * OUT must be labeled {@code RUNNING}, {@code SERVING}, or
 * {@code ENABLE}. The host's catalog validation and the shipped
 * contract tests both enforce this.
 *
 * @param id    stable identifier, referenced by saved patches — never rename
 * @param label the silkscreen text at the jack, non-blank
 * @param direction which way signals flow
 * @param signal    what travels: momentary TRIGGER (with ok/fail),
 *                  DATA text lines, or a sustained GATE level
 * @since 1.55
 */
public record PortSpec(String id, String label, Direction direction, Signal signal) {

    /** @since 1.55 */
    public enum Direction { IN, OUT }

    /** @since 1.55 */
    public enum Signal { TRIGGER, DATA, GATE }
}
