package org.nmox.studio.ui.shortcuts;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.ui.shortcuts.ShortcutSheet.Row;

import static org.assertj.core.api.Assertions.assertThat;

class ShortcutSheetTest {

    @Test
    @DisplayName("The notation law: D is ⌘/Ctrl, O is ⌃/Alt, A is ⌥/Alt, S is ⇧ — both platforms")
    void notation() {
        assertThat(ShortcutSheet.humanChord("DA-G", true)).isEqualTo("⌥⌘G");
        assertThat(ShortcutSheet.humanChord("DA-G", false)).isEqualTo("Ctrl+Alt+G");
        assertThat(ShortcutSheet.humanChord("DO-G", true)).isEqualTo("⌃⌘G");
        assertThat(ShortcutSheet.humanChord("DO-G", false)).isEqualTo("Ctrl+Alt+G");
        assertThat(ShortcutSheet.humanChord("ADS-O", true)).isEqualTo("⌥⇧⌘O");
        assertThat(ShortcutSheet.humanChord("D-8", true)).isEqualTo("⌘8");
        assertThat(ShortcutSheet.humanChord("DA-SLASH", true)).isEqualTo("⌥⌘/");
        assertThat(ShortcutSheet.humanChord("F6", true)).isEqualTo("F6");
        assertThat(ShortcutSheet.humanChord("S-F6", false)).isEqualTo("Shift+F6");
        assertThat(ShortcutSheet.humanChord("ESCAPE", true)).isEqualTo("Esc");
        assertThat(ShortcutSheet.humanChord("", true)).isEmpty();
    }

    @Test
    @DisplayName("The sheet sorts by action, escapes pipes, and names the profile")
    void render() {
        String md = ShortcutSheet.renderMarkdown(List.of(
                new Row("⌘I", "Quick Search"), new Row("⌥⌘G", "Complete with KVASIR"),
                new Row("⌥⌘2", "Tests | window")), "NetBeans");
        assertThat(md).startsWith("| Shortcut | Action |\n|---|---|\n| `⌥⌘G` | Complete with KVASIR |\n| `⌘I` | Quick Search |\n| `⌥⌘2` | Tests \\| window |\n");
        assertThat(md).endsWith("keymap profile: NetBeans_\n");
    }
}
