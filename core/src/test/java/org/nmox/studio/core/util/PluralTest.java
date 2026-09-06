package org.nmox.studio.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PluralTest {

    @Test
    @DisplayName("one is singular, everything else plural, irregulars spelled out")
    void counts() {
        assertThat(Plural.of(1, "card")).isEqualTo("1 card");
        assertThat(Plural.of(0, "card")).isEqualTo("0 cards");
        assertThat(Plural.of(12, "row")).isEqualTo("12 rows");
        assertThat(Plural.of(1, "match", "matches")).isEqualTo("1 match");
        assertThat(Plural.of(3, "match", "matches")).isEqualTo("3 matches");
    }
}
