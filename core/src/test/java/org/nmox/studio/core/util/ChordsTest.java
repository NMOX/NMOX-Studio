package org.nmox.studio.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChordsTest {

    @Test
    @DisplayName("the platform notation renders per the D/A/O law on both platforms")
    void notation() {
        assertThat(Chords.human("DA-G", true)).isEqualTo("⌥⌘G");
        assertThat(Chords.human("DA-G", false)).isEqualTo("Ctrl+Alt+G");
        assertThat(Chords.human("DO-G", true)).isEqualTo("⌃⌘G");
        assertThat(Chords.human("ADS-O", true)).isEqualTo("⌥⇧⌘O");
        assertThat(Chords.human("D-SLASH", true)).isEqualTo("⌘/");
        assertThat(Chords.human("F5", true)).isEqualTo("F5");
        assertThat(Chords.human("", true)).isEmpty();
    }

    @Test
    @DisplayName("a key event's parts render identically — one vocabulary for the sheet and the keystroke display")
    void fromParts() {
        assertThat(Chords.human(false, true, false, true, "G", true)).isEqualTo("⌥⌘G");
        assertThat(Chords.human(true, false, true, false, "SLASH", false)).isEqualTo("Ctrl+Shift+/");
        assertThat(Chords.human(false, false, false, true, "a", true)).isEqualTo("⌘A");
        assertThat(Chords.human(false, false, false, false, "ESCAPE", true)).isEqualTo("Esc");
        assertThat(Chords.human(false, false, false, true, "UP", true)).isEqualTo("⌘↑");
    }
}
