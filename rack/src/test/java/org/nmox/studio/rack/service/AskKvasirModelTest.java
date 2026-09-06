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
}
