package org.nmox.studio.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A name from disk, made safe for a sink that builds markup from it (3.5.13). */
class PlainTextOneLineTest {

    @Test
    @DisplayName("control characters become spaces: a notification's markup builder throws on them")
    void controlsAreFolded() {
        assertThat(PlainText.oneLine("re\u0007po\nname\u001b[0m", 80)).isEqualTo("re po name [0m");
        assertThat(PlainText.oneLine("a\u2028b", 80)).isEqualTo("a b");
        assertThat(PlainText.oneLine("plain-name_1.2", 80)).isEqualTo("plain-name_1.2");
        assertThat(PlainText.oneLine(null, 80)).isEmpty();
    }

    @Test
    @DisplayName("a long name is cut by code points and says it was cut")
    void longNamesAreCut() {
        String astral = "\uD83D\uDE00".repeat(5); // five two-unit characters
        assertThat(PlainText.oneLine(astral, 3)).isEqualTo("\uD83D\uDE00".repeat(3) + "\u2026");
        assertThat(PlainText.oneLine("abc", 3)).as("at the limit nothing is cut").isEqualTo("abc");
    }
}
