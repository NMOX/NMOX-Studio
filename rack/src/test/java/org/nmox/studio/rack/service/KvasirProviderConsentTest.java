package org.nmox.studio.rack.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.KvasirProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v2.96.0: a consent names its recipient. A yes given to send output to
 * Anthropic's API is not a yes to send it to Google's or OpenAI's — every
 * grant (failure, code, kind) is scoped to the configured provider, and
 * the Anthropic grants keep their pre-provider keys so nothing already
 * given is asked for again.
 */
class KvasirProviderConsentTest {

    @BeforeEach
    @AfterEach
    void reset() {
        for (KvasirProvider p : KvasirProvider.values()) {
            KvasirProvider.remember(p);
            KvasirConsent.revokeForTest();
            KvasirConsent.revokeCodeForTest();
            KvasirConsent.revokeKindForTest("api.response");
        }
        KvasirProvider.remember(KvasirProvider.ANTHROPIC);
    }

    @Test
    @DisplayName("A grant for Claude does not carry to Gemini or ChatGPT; each earns its own")
    void grantsAreScopedToTheProvider() {
        KvasirConsent.grant();
        KvasirConsent.grantCode();
        KvasirConsent.grantKind("api.response");
        assertThat(KvasirConsent.isGranted()).isTrue();

        KvasirProvider.remember(KvasirProvider.GOOGLE);
        assertThat(KvasirConsent.isGranted()).as("failure flow: Google not yet consented").isFalse();
        assertThat(KvasirConsent.isCodeGranted()).as("code flow: Google not yet consented").isFalse();
        assertThat(KvasirConsent.isKindGranted("api.response")).as("kind flow").isFalse();

        KvasirConsent.grant();
        assertThat(KvasirConsent.isGranted()).isTrue();
        KvasirProvider.remember(KvasirProvider.OPENAI);
        assertThat(KvasirConsent.isGranted()).as("OpenAI still unasked").isFalse();

        KvasirProvider.remember(KvasirProvider.ANTHROPIC);
        assertThat(KvasirConsent.isGranted()).as("the Claude grant survived the round trip").isTrue();
        assertThat(KvasirConsent.isCodeGranted()).isTrue();
    }

    @Test
    @DisplayName("The Anthropic keys are the bare pre-v2.96.0 names; the others carry the provider id")
    void keyShapes() {
        KvasirProvider.remember(KvasirProvider.ANTHROPIC);
        assertThat(KvasirConsent.scoped("kvasir.external.consent")).isEqualTo("kvasir.external.consent");
        KvasirProvider.remember(KvasirProvider.GOOGLE);
        assertThat(KvasirConsent.scoped("kvasir.external.consent")).isEqualTo("kvasir.external.consent.google");
        KvasirProvider.remember(KvasirProvider.OPENAI);
        assertThat(KvasirConsent.scoped("kvasir.code.consent")).isEqualTo("kvasir.code.consent.openai");
    }
}
