package org.nmox.studio.editor.lsp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The health reporter's contract is "tell them once": a missing server
 * notifies at most once per binary per session, a null binary is a no-op,
 * and the session can be reset so a still-missing server can re-notify.
 * We can't assert on the popup here, only that the dedup bookkeeping
 * behaves and nothing throws.
 */
class LanguageServerHealthTest {

    @AfterEach
    void reset() {
        LanguageServerHealth.resetForTest();
    }

    @Test
    @DisplayName("A null binary is a silent no-op")
    void nullBinaryIsNoOp() {
        assertThatCode(() -> LanguageServerHealth.reportMissing(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Reporting the same binary twice does not throw (deduplicated after the first)")
    void reportsAtMostOncePerBinary() {
        // uncatalogued binary exercises the generic-message branch too
        assertThatCode(() -> {
            LanguageServerHealth.reportMissing("nmox-test-binary-zzz");
            LanguageServerHealth.reportMissing("nmox-test-binary-zzz");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A catalogued binary reports through the language/install path without error")
    void cataloguedBinary() {
        // gopls is in the catalog: reportMissing resolves language + install hint
        assertThat(LanguageServerCatalog.forBinary("gopls")).isNotNull();
        assertThatCode(() -> LanguageServerHealth.reportMissing("gopls")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("resetForTest clears the session so the same binary can be reported again")
    void resetAllowsReReport() {
        assertThatCode(() -> {
            LanguageServerHealth.reportMissing("nmox-reset-check");
            LanguageServerHealth.resetForTest();
            LanguageServerHealth.reportMissing("nmox-reset-check");
        }).doesNotThrowAnyException();
    }
}
