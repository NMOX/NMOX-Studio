package org.nmox.studio.rack.service;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SPI adapter's ordering law, source-gated because the alternative
 * is driving a modal consent dialog in a headless test.
 *
 * <p>Consent must be asked BEFORE the conversation window opens.
 * Live-caught on 2026-07-26: with the gate only inside the engine's
 * send, the window appeared first and rendered "Thinking…" while the
 * consent prompt was still on screen — the UI claiming work that
 * consent had not yet allowed — and declining left an orphaned window.
 * Nothing ever left the machine (the engine gate held), but the window
 * lied about what was happening.
 */
class RackOracleAskGateTest {

    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/service/RackOracleAsk.java"));
    }

    @Test
    @DisplayName("consent is requested before the dialog is constructed or opened")
    void consentPrecedesTheWindow() throws Exception {
        String s = source();

        int consent = s.indexOf("requestKindConsent");
        int dialog = s.indexOf("new AskOracleDialog");
        assertThat(consent).as("the adapter must ask for consent").isGreaterThan(-1);
        assertThat(dialog).as("the adapter opens the conversation window").isGreaterThan(-1);
        assertThat(consent)
                .as("the FIRST consent call must precede the window")
                .isLessThan(dialog);
    }

    @Test
    @DisplayName("a declined consent opens no window and reports not-started")
    void declineOpensNothing() throws Exception {
        String s = source();

        // the early return sits between the consent call and the dialog:
        // decline -> false, and the caller says so in its own status line
        int consent = s.indexOf("requestKindConsent");
        int earlyReturn = s.indexOf("return false", consent);
        int dialog = s.indexOf("new AskOracleDialog");
        assertThat(earlyReturn)
                .as("declining returns before any window is built")
                .isGreaterThan(consent).isLessThan(dialog);
    }

    @Test
    @DisplayName("the per-send gate stays — pre-asking never replaces it")
    void perSendGateSurvives() throws Exception {
        String s = source();

        // v1.149.0's law: EVERY send passes the gate. Once granted the
        // call short-circuits, so pre-asking costs nothing and the
        // defence stays in place for follow-up turns.
        assertThat(s.split("requestKindConsent", -1).length - 1)
                .as("both the pre-ask AND the engine's per-send gate")
                .isGreaterThanOrEqualTo(2);
        assertThat(s).contains("new AskOracleEngine");
    }
}
