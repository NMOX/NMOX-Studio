package org.nmox.studio.rack.service;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.KvasirClient.FailureContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The KVASIR consent gate. The grant is a preference; a fresh install has
 * not granted. In a headless run (this test JVM) {@code requestConsent}
 * auto-allows — like {@link WorkspaceTrust#requestTrust}, there is no human
 * to prompt and nothing here reaches the network on its own — but it must
 * NOT persist a grant the user never made.
 */
class KvasirConsentTest {

    private static FailureContext ctx() {
        return new FailureContext("VERITAS", "npm test", 1,
                List.of("FAIL"), "app", 100);
    }

    @BeforeEach
    @AfterEach
    void reset() {
        KvasirConsent.revokeForTest();
    }

    @Test
    @DisplayName("a fresh install has not granted consent")
    void defaultsToNotGranted() {
        assertThat(KvasirConsent.isGranted()).isFalse();
    }

    @Test
    @DisplayName("grant() records the consent, revoke forgets it")
    void grantAndRevoke() {
        KvasirConsent.grant();
        assertThat(KvasirConsent.isGranted()).isTrue();
        KvasirConsent.revokeForTest();
        assertThat(KvasirConsent.isGranted()).isFalse();
    }

    @Test
    @DisplayName("already-granted consent needs no prompt")
    void grantedShortCircuits() {
        KvasirConsent.grant();
        assertThat(KvasirConsent.requestConsent(ctx())).isTrue();
    }

    @Test
    @DisplayName("headless requestConsent auto-allows but persists nothing")
    void headlessAutoAllowsWithoutPersisting() {
        // surefire runs headless, so this exercises the no-human branch
        assertThat(KvasirConsent.requestConsent(ctx())).isTrue();
        assertThat(KvasirConsent.isGranted())
                .as("a grant the user never made must not be persisted")
                .isFalse();
    }
}
