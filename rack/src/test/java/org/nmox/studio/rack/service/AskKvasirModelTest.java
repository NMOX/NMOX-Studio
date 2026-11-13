package org.nmox.studio.rack.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.KvasirClient;

import static org.assertj.core.api.Assertions.assertThat;

/** The Ask model preference: Haiku default, Sonnet remembered, junk safe. */
class AskKvasirModelTest {

    @AfterEach
    void reset() {
        AskKvasirModel.resetForTest();
        org.nmox.studio.rack.engine.KvasirProvider.remember(
                org.nmox.studio.rack.engine.KvasirProvider.ANTHROPIC);
    }

    @Test
    @DisplayName("v2.96.0: the depth is provider-neutral — Deep on Claude is Deep on Gemini, in Gemini's words")
    void depthFollowsProvider() {
        AskKvasirModel.remember(1);
        assertThat(AskKvasirModel.chosen()).isEqualTo(KvasirClient.MODEL_SONNET);
        org.nmox.studio.rack.engine.KvasirProvider.remember(
                org.nmox.studio.rack.engine.KvasirProvider.GOOGLE);
        assertThat(AskKvasirModel.chosen()).isEqualTo(
                org.nmox.studio.rack.engine.KvasirProvider.GOOGLE.model(
                        org.nmox.studio.rack.engine.KvasirProvider.Depth.DEEP));
        assertThat(AskKvasirModel.chosenIndex()).isEqualTo(1);
        assertThat(AskKvasirModel.labels()[1]).isEqualTo("Deep (Pro)");
        assertThat(AskKvasirModel.labels()[0]).isEqualTo("Fast (Flash)");
    }

    @Test
    @DisplayName("Default is Haiku — fast unless the user asks for depth")
    void defaultsToHaiku() {
        assertThat(AskKvasirModel.chosen()).isEqualTo(KvasirClient.MODEL_HAIKU);
        assertThat(AskKvasirModel.chosenIndex()).isZero();
    }

    @Test
    @DisplayName("Choosing Deep is remembered across asks")
    void remembersSonnet() {
        AskKvasirModel.remember(1);
        assertThat(AskKvasirModel.chosen()).isEqualTo(KvasirClient.MODEL_SONNET);
        assertThat(AskKvasirModel.chosenIndex()).isEqualTo(1);
        AskKvasirModel.remember(0);
        assertThat(AskKvasirModel.chosen()).isEqualTo(KvasirClient.MODEL_HAIKU);
    }

    @Test
    @DisplayName("An out-of-range index and a corrupted pref both fall back to Haiku")
    void junkFallsBackToHaiku() {
        AskKvasirModel.remember(7);
        assertThat(AskKvasirModel.chosen()).isEqualTo(KvasirClient.MODEL_HAIKU);
    }

    /**
     * Before v2.96.0 the preference held the Anthropic model id itself, and
     * the reader knew the Sonnet id by comparing it to the CURRENT constant.
     * The day that constant moved (3.13.0, claude-sonnet-5 to
     * claude-sonnet-5-5) an old Deep choice would have read as Fast.
     */
    @Test
    @DisplayName("An id an older build stored reads as its depth, whether it is today's id or a retired one")
    void aStoredIdReadsAsItsDepth() {
        AskKvasirModel.storeForTest("claude-sonnet-5");
        assertThat(AskKvasirModel.chosenIndex()).as("the Sonnet id shipped before 3.13.0").isEqualTo(1);
        assertThat(AskKvasirModel.chosen()).isEqualTo(KvasirClient.MODEL_SONNET);
        AskKvasirModel.storeForTest(KvasirClient.MODEL_SONNET);
        assertThat(AskKvasirModel.chosenIndex()).isEqualTo(1);
        AskKvasirModel.storeForTest("claude-haiku-4-5");
        assertThat(AskKvasirModel.chosenIndex()).as("the Haiku id shipped before 3.13.0").isZero();
        AskKvasirModel.storeForTest("no-such-model");
        assertThat(AskKvasirModel.chosenIndex()).isZero();
    }
}
