package org.nmox.studio.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductVersionTest {

    @Test
    @DisplayName("The launcher's property wins; the branded bundle is the fallback; blanks are absent")
    void order() {
        assertThat(ProductVersion.current(() -> "NMOX Studio 2.67.0", () -> "NMOX Studio 9.9.9"))
                .isEqualTo("NMOX Studio 2.67.0");
        assertThat(ProductVersion.current(() -> null, () -> " NMOX Studio 2.67.0 ")).isEqualTo("NMOX Studio 2.67.0");
        assertThat(ProductVersion.current(() -> "  ", () -> "NMOX Studio 1.0")).isEqualTo("NMOX Studio 1.0");
        assertThat(ProductVersion.current(() -> null, () -> null)).isNull();
    }

    @Test
    @DisplayName("A read that throws is an absence, never a crash — and the next source still gets its turn")
    void throwingSourceIsAbsent() {
        assertThat(ProductVersion.current(() -> { throw new IllegalStateException("no property"); },
                () -> "NMOX Studio 2.67.0")).isEqualTo("NMOX Studio 2.67.0");
        assertThat(ProductVersion.current(() -> null,
                () -> { throw new java.util.MissingResourceException("x", "y", "z"); })).isNull();
    }

    @Test
    @DisplayName("number() and stamped() read the branded string the way UpdateCheck always meant to")
    void numberAndStamped() {
        assertThat(Versions.extract("NMOX Studio 2.67.0")).isEqualTo("2.67.0");
        assertThat(Versions.isStamped("2.67.0")).isTrue();
        assertThat(Versions.isStamped(Versions.extract("NMOX Studio 1.0"))).isFalse();
        assertThat(Versions.isStamped(null)).isFalse();
    }
}
