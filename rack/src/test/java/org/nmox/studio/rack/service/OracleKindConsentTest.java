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
class OracleKindConsentTest {

    @AfterEach
    void clean() {
        OracleConsent.revokeKindForTest("api.response");
        OracleConsent.revokeKindForTest("other.kind");
    }

    @Test
    @DisplayName("A kind starts ungranted and grants only itself")
    void grantsAreScopedPerKind() {
        assertThat(OracleConsent.isKindGranted("api.response")).isFalse();
        OracleConsent.grantKind("api.response");
        assertThat(OracleConsent.isKindGranted("api.response")).isTrue();
        assertThat(OracleConsent.isKindGranted("other.kind")).isFalse();
    }

    @Test
    @DisplayName("A kind grant never CHANGES the failure or code flows")
    void kindGrantDoesNotLeakToNamedFlows() {
        // asserted as "unchanged", not "false": these prefs live in the
        // REAL userRoot, so a developer who granted consent in the app
        // would otherwise fail this test on ambient state alone (the
        // prefs-pollution law — it caught exactly that on 2026-07-26)
        boolean codeBefore = OracleConsent.isCodeGranted();
        boolean failureBefore = OracleConsent.isGranted();

        OracleConsent.grantKind("api.response");

        assertThat(OracleConsent.isCodeGranted()).isEqualTo(codeBefore);
        assertThat(OracleConsent.isGranted()).isEqualTo(failureBefore);
    }

    @Test
    @DisplayName("A blank kind is still a distinct, honest bucket")
    void blankKindIsNamed() {
        assertThat(OracleConsent.isKindGranted("")).isFalse();
        OracleConsent.grantKind("");
        assertThat(OracleConsent.isKindGranted("")).isTrue();
        assertThat(OracleConsent.isKindGranted("api.response")).isFalse();
        OracleConsent.revokeKindForTest("");
    }
}
