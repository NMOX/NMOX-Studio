package org.nmox.studio.rack.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The kind- and code-scoped KVASIR consents (v1.146.0 / v1.171.0): each
 * disclosure kind earns its own yes, a grant given for one kind never
 * authorizes another, and — the house law — a headless run auto-allows
 * without persisting a grant the user never made.
 */
class KvasirScopedConsentTest {

    @BeforeEach
    @AfterEach
    void reset() {
        KvasirConsent.revokeKindForTest("test-kind");
        KvasirConsent.revokeCodeForTest();
    }

    @Test
    @DisplayName("Kind consent: headless auto-allows without persisting; a grant sticks")
    void kindConsentLifecycle() {
        assumeTrue(java.awt.GraphicsEnvironment.isHeadless(),
                "the no-human branch needs a headless JVM");
        assertThat(KvasirConsent.requestKindConsent("test-kind", "one response header"))
                .isTrue();
        assertThat(KvasirConsent.isKindGranted("test-kind"))
                .as("a grant the user never made must not persist").isFalse();

        KvasirConsent.grantKind("test-kind");
        assertThat(KvasirConsent.isKindGranted("test-kind")).isTrue();
        assertThat(KvasirConsent.requestKindConsent("test-kind", "whatever"))
                .as("an existing grant short-circuits the prompt").isTrue();
        assertThat(KvasirConsent.isKindGranted("another-kind"))
                .as("one kind's grant never authorizes another").isFalse();
    }

    @Test
    @DisplayName("Code consent: grant, remember, revoke")
    void codeConsentLifecycle() {
        assertThat(KvasirConsent.isCodeGranted()).isFalse();
        KvasirConsent.grantCode();
        assertThat(KvasirConsent.isCodeGranted()).isTrue();
        KvasirConsent.revokeCodeForTest();
        assertThat(KvasirConsent.isCodeGranted()).isFalse();
    }
}
