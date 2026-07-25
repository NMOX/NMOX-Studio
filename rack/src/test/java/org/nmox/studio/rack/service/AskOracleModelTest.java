package org.nmox.studio.rack.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.OracleClient;

import static org.assertj.core.api.Assertions.assertThat;

/** The Ask model preference: Haiku default, Sonnet remembered, junk safe. */
class AskOracleModelTest {

    @AfterEach
    void reset() {
        AskOracleModel.resetForTest();
    }

    @Test
    @DisplayName("Default is Haiku — fast unless the user asks for depth")
    void defaultsToHaiku() {
        assertThat(AskOracleModel.chosen()).isEqualTo(OracleClient.MODEL_HAIKU);
        assertThat(AskOracleModel.chosenIndex()).isZero();
    }

    @Test
    @DisplayName("Choosing Deep is remembered across asks")
    void remembersSonnet() {
        AskOracleModel.remember(1);
        assertThat(AskOracleModel.chosen()).isEqualTo(OracleClient.MODEL_SONNET);
        assertThat(AskOracleModel.chosenIndex()).isEqualTo(1);
        AskOracleModel.remember(0);
        assertThat(AskOracleModel.chosen()).isEqualTo(OracleClient.MODEL_HAIKU);
    }

    @Test
    @DisplayName("An out-of-range index and a corrupted pref both fall back to Haiku")
    void junkFallsBackToHaiku() {
        AskOracleModel.remember(7);
        assertThat(AskOracleModel.chosen()).isEqualTo(OracleClient.MODEL_HAIKU);
    }
}
