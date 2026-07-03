package org.nmox.studio.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Version arithmetic for the update check: extraction from branded
 * strings and numeric (not lexicographic) comparison.
 */
class VersionsTest {

    @Test
    @DisplayName("extract finds the version inside branded and tagged strings")
    void extraction() {
        assertThat(Versions.extract("NMOX Studio 1.24.0")).isEqualTo("1.24.0");
        assertThat(Versions.extract("v1.25.0")).isEqualTo("1.25.0");
        assertThat(Versions.extract("NMOX Studio 1.0")).isEqualTo("1.0");
        assertThat(Versions.extract("no version here")).isNull();
        assertThat(Versions.extract(null)).isNull();
    }

    @Test
    @DisplayName("compare is numeric: 1.9 is older than 1.24, missing parts are zero")
    void comparison() {
        assertThat(Versions.compare("1.9.0", "1.24.0")).isNegative();
        assertThat(Versions.compare("1.24.0", "1.24.0")).isZero();
        assertThat(Versions.compare("1.24.1", "1.24.0")).isPositive();
        assertThat(Versions.compare("1.24", "1.24.0")).isZero();
        assertThat(Versions.compare("2.0", "1.99.99")).isPositive();
    }

    @Test
    @DisplayName("only release builds are stamped; the dev '1.0' is not")
    void stamped() {
        assertThat(Versions.isStamped("1.24.0")).isTrue();
        assertThat(Versions.isStamped("1.0")).isFalse();
        assertThat(Versions.isStamped(null)).isFalse();
    }
}
