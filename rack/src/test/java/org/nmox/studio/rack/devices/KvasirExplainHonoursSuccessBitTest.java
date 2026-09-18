package org.nmox.studio.rack.devices;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Signal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KVASIR's EXPLAIN jack honours the trigger's success bit.
 *
 * <p>DONE carries the run's verdict ({@code Signal.high}: true = success)
 * and FAIL carries false; the jack used to consult on ANY trigger, so a
 * {@code VERITAS done → EXPLAIN} cable asked the model to explain every
 * GREEN run (the 2026-09-17 rack audit). The consent gate is the
 * observation point: with no consent the cable path refuses on the
 * verdict LCD and never reaches a thread, so an OK verdict must leave
 * the LCD alone and a low one must reach that refusal.
 *
 * <p>And the law's other half: a verdict-LESS pulse ({@link Signal#trigger()}
 * — MASTER's trig, REFLEX's changed, TEMPO's tick) rides high like an OK
 * verdict and MUST still consult. The first cut of the guard read the bare
 * bit and silently killed "explain now" on a button; the pulse case is what
 * keeps the guard on the note, not the bit.
 */
class KvasirExplainHonoursSuccessBitTest {

    private static void flushEdt() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    @DisplayName("DONE high (a green run) is not a failure to explain: the cable path is not entered")
    void highTriggerIsIgnored() throws Exception {
        KvasirDevice device = new KvasirDevice();
        try {
            device.consentCheck = () -> false;
            device.receive(device.getPort("explain"), Signal.trigger(true));
            flushEdt();
            assertThat(device.verdictText()).doesNotContain(KvasirDevice.AUTO_NO_CONSENT);
        } finally {
            device.dispose();
        }
    }

    @Test
    @DisplayName("DONE low / FAIL (a failed run) enters the cable path")
    void lowTriggerConsults() throws Exception {
        KvasirDevice device = new KvasirDevice();
        try {
            device.consentCheck = () -> false;
            device.receive(device.getPort("explain"), Signal.trigger(false));
            flushEdt();
            assertThat(device.verdictText()).contains(KvasirDevice.AUTO_NO_CONSENT);
        } finally {
            device.dispose();
        }
    }

    @Test
    @DisplayName("a verdict-less pulse (MASTER trig / REFLEX changed / TEMPO tick) still consults — it is high but not an OK verdict")
    void pulseStillConsults() throws Exception {
        KvasirDevice device = new KvasirDevice();
        try {
            device.consentCheck = () -> false;
            Signal pulse = Signal.trigger();
            assertThat(pulse.high()).as("a pulse rides high like an OK verdict").isTrue();
            assertThat(pulse.okVerdict()).as("but it is not one").isFalse();
            device.receive(device.getPort("explain"), pulse);
            flushEdt();
            assertThat(device.verdictText()).contains(KvasirDevice.AUTO_NO_CONSENT);
        } finally {
            device.dispose();
        }
    }
}
