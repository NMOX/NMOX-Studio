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
    @Test
    @DisplayName("a sentence written in Mac notation is unchanged on a Mac")
    void macSentenceStaysOnAMac() {
        assertThat(Chords.forOs("New Experiment…  ⌥⌘K", true)).isEqualTo("New Experiment…  ⌥⌘K");
        assertThat(Chords.forOs(null, false)).isNull();
        assertThat(Chords.forOs("no chord here", false)).isEqualTo("no chord here");
    }

    @Test
    @DisplayName("off a Mac, a chord in a sentence names the keys that keyboard has")
    void chordsBecomeTheReadersKeys() {
        assertThat(Chords.forOs("New Experiment…  ⌥⌘K", false)).isEqualTo("New Experiment…  Ctrl+Alt+K");
        assertThat(Chords.forOs("New Project…  ⇧⌘N", false)).isEqualTo("New Project…  Ctrl+Shift+N");
        assertThat(Chords.forOs("Task Rack  ⌘9", false)).isEqualTo("Task Rack  Ctrl+9");
        assertThat(Chords.forOs("KVASIR edit applied — ⌘Z undoes it.", false))
                .isEqualTo("KVASIR edit applied — Ctrl+Z undoes it.");
        assertThat(Chords.forOs("open it in the in-app Browser (⌥⌘4).", false))
                .isEqualTo("open it in the in-app Browser (Ctrl+Alt+4).");
        assertThat(Chords.forOs("⌘F finds in the transcript (⌘F again closes)", false))
                .isEqualTo("Ctrl+F finds in the transcript (Ctrl+F again closes)");
        assertThat(Chords.forOs("⌃Space completes", false)).as("⌃ alone is Ctrl everywhere")
                .isEqualTo("Ctrl+Space completes");
        assertThat(Chords.forOs("⌃⌘G", false)).as("⌃ beside ⌘ is the other modifier: Alt off a Mac")
                .isEqualTo("Ctrl+Alt+G");
        assertThat(Chords.forOs("⌥⇧⌘O", false)).as("the order elsewhere is Ctrl, Alt, Shift")
                .isEqualTo("Ctrl+Alt+Shift+O");
    }

    @Test
    @DisplayName("a hyphen keeps its shape and a modifier named alone is its name")
    void hyphenAndBareModifiers() {
        assertThat(Chords.forOs("⌘-click jumps to the rule", false)).isEqualTo("Ctrl-click jumps to the rule");
        assertThat(Chords.forOs("(⌥-wheel fine-tunes)", false)).isEqualTo("(Alt-wheel fine-tunes)");
        assertThat(Chords.forOs("hold ⌥, then drag", false)).isEqualTo("hold Alt, then drag");
        assertThat(Chords.forOs("hold ⇧", false)).isEqualTo("hold Shift");
        assertThat(Chords.forOs("按 ⌘。", false)).as("a full stop in another script ends the clause too")
                .isEqualTo("按 Ctrl。");
    }

    @Test
    @DisplayName("the round trip agrees with the notation: what human() writes for a Mac reads back as human() elsewhere")
    void agreesWithTheNotation() {
        for (String nb : new String[] {"DA-G", "DS-N", "D-9", "DO-G", "ADS-O", "D-SLASH", "C-SPACE", "A-UP"}) {
            assertThat(Chords.forOs(Chords.human(nb, true), false))
                    .as("%s", nb).isEqualTo(Chords.human(nb, false));
        }
    }
}
