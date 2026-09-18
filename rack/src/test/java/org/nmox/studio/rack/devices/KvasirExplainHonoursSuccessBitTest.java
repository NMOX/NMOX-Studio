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
 * verdict LCD and never reaches a thread, so a high trigger must leave
 * the LCD alone and a low one must reach that refusal.
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
}
