package org.nmox.studio.rack.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The consent-scoping law, generalized to SPI flows: one grant per
 * disclosure kind, never shared. A grant given for an API response can
 * never authorize sending a database row, and neither can borrow the
 * failure flow's yes.
 */
class KvasirKindConsentTest {

    /**
     * Tests own a kind namespace the PRODUCT never uses. These
     * preferences live in the real userRoot, so asserting on a shipping
     * kind ("api.response") reads whatever the developer's own app runs
     * have granted — a live gauntlet on 2026-07-26 granted it and broke
     * this suite. A private namespace is immune by construction.
     */
    private static final String KIND_A = "test.kind.alpha";
    private static final String KIND_B = "test.kind.beta";

    /**
     * EVERY kind this suite grants is revoked here, not at the end of a
     * test body: a test that writes the real userRoot and cleans up only
     * on the happy path leaves its grant behind when it fails, and then
     * poisons its own next run. That is precisely how the blank-kind
     * case broke twice on 2026-07-26.
     */
    @AfterEach
    void clean() {
        KvasirConsent.revokeKindForTest(KIND_A);
        KvasirConsent.revokeKindForTest(KIND_B);
        KvasirConsent.revokeKindForTest("");
    }

    @Test
    @DisplayName("A kind starts ungranted and grants only itself")
    void grantsAreScopedPerKind() {
        assertThat(KvasirConsent.isKindGranted(KIND_A)).isFalse();
        KvasirConsent.grantKind(KIND_A);
        assertThat(KvasirConsent.isKindGranted(KIND_A)).isTrue();
        assertThat(KvasirConsent.isKindGranted(KIND_B)).isFalse();
    }

    @Test
    @DisplayName("A kind grant never CHANGES the failure or code flows")
    void kindGrantDoesNotLeakToNamedFlows() {
        // asserted as "unchanged", not "false": these prefs live in the
        // REAL userRoot, so a developer who granted consent in the app
        // would otherwise fail this test on ambient state alone (the
        // prefs-pollution law — it caught exactly that on 2026-07-26)
        boolean codeBefore = KvasirConsent.isCodeGranted();
        boolean failureBefore = KvasirConsent.isGranted();

        KvasirConsent.grantKind(KIND_A);

        assertThat(KvasirConsent.isCodeGranted()).isEqualTo(codeBefore);
        assertThat(KvasirConsent.isGranted()).isEqualTo(failureBefore);
    }

    @Test
    @DisplayName("A blank kind is still a distinct, honest bucket")
    void blankKindIsNamed() {
        assertThat(KvasirConsent.isKindGranted("")).isFalse();
        KvasirConsent.grantKind("");
        assertThat(KvasirConsent.isKindGranted("")).isTrue();
        // compare against the test namespace, never a SHIPPING kind: a
        // live run of the app grants those for real
        assertThat(KvasirConsent.isKindGranted(KIND_A)).isFalse();
    }
}
