package org.nmox.studio.editor.present;

import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeystrokeHudTest {

    @Test
    @DisplayName("chords with ⌘/⌃/⌥ show; function keys and Escape show on their own")
    void chordsShow() {
        assertThat(KeystrokeHud.shows(KeyEvent.META_DOWN_MASK, KeyEvent.VK_S)).isTrue();
        assertThat(KeystrokeHud.shows(KeyEvent.META_DOWN_MASK | KeyEvent.ALT_DOWN_MASK, KeyEvent.VK_G)).isTrue();
        assertThat(KeystrokeHud.shows(KeyEvent.CTRL_DOWN_MASK, KeyEvent.VK_SPACE)).isTrue();
        assertThat(KeystrokeHud.shows(0, KeyEvent.VK_F5)).isTrue();
        assertThat(KeystrokeHud.shows(0, KeyEvent.VK_ESCAPE)).isTrue();
        assertThat(KeystrokeHud.shows(KeyEvent.SHIFT_DOWN_MASK | KeyEvent.META_DOWN_MASK, KeyEvent.VK_O)).isTrue();
    }

    @Test
    @DisplayName("typing never shows — plain keys, ⇧-only keys, Enter, and a bare modifier press are all silent (a projector must not echo a password)")
    void typingNeverShows() {
        assertThat(KeystrokeHud.shows(0, KeyEvent.VK_A)).isFalse();
        assertThat(KeystrokeHud.shows(KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_A)).isFalse();
        assertThat(KeystrokeHud.shows(0, KeyEvent.VK_ENTER)).isFalse();
        assertThat(KeystrokeHud.shows(0, KeyEvent.VK_9)).isFalse();
        assertThat(KeystrokeHud.shows(KeyEvent.META_DOWN_MASK, KeyEvent.VK_META)).isFalse();
        assertThat(KeystrokeHud.shows(KeyEvent.CTRL_DOWN_MASK, KeyEvent.VK_CONTROL)).isFalse();
        assertThat(KeystrokeHud.shows(KeyEvent.ALT_DOWN_MASK, KeyEvent.VK_ALT)).isFalse();
        assertThat(KeystrokeHud.shows(KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_SHIFT)).isFalse();
        assertThat(KeystrokeHud.shows(KeyEvent.META_DOWN_MASK, KeyEvent.VK_UNDEFINED)).isFalse();
    }

    @Test
    @DisplayName("the label is the product's one chord vocabulary, on both platforms")
    void labels() {
        assertThat(KeystrokeHud.label(KeyEvent.META_DOWN_MASK | KeyEvent.ALT_DOWN_MASK, KeyEvent.VK_G, true)).isEqualTo("⌥⌘G");
        assertThat(KeystrokeHud.label(KeyEvent.META_DOWN_MASK, KeyEvent.VK_A, true)).isEqualTo("⌘A");
        assertThat(KeystrokeHud.label(KeyEvent.META_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_O, true)).isEqualTo("⇧⌘O");
        assertThat(KeystrokeHud.label(KeyEvent.CTRL_DOWN_MASK, KeyEvent.VK_SLASH, false)).isEqualTo("Ctrl+/");
        assertThat(KeystrokeHud.label(0, KeyEvent.VK_F5, true)).isEqualTo("F5");
        assertThat(KeystrokeHud.label(0, KeyEvent.VK_ESCAPE, true)).isEqualTo("Esc");
    }

    @Test
    @DisplayName("a repeated chord coalesces to a count instead of flashing")
    void coalesces() {
        assertThat(KeystrokeHud.coalesce("", 0, "⌘Z")).isEqualTo("⌘Z");
        assertThat(KeystrokeHud.coalesce("⌘Z", 1, "⌘Z")).isEqualTo("⌘Z ×2");
        assertThat(KeystrokeHud.coalesce("⌘Z", 2, "⌘Z")).isEqualTo("⌘Z ×3");
        assertThat(KeystrokeHud.coalesce("⌘Z", 2, "⌘S")).isEqualTo("⌘S");
    }

    @Test
    @DisplayName("the listener is the toolkit's own, installed on KEY events only while on, removed when off; the overlay never takes focus")
    void wiring() throws Exception {
        String hud = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/present/KeystrokeHud.java"));
        assertThat(hud).contains("addAWTEventListener(LISTENER, AWTEvent.KEY_EVENT_MASK)")
                .contains("removeAWTEventListener(LISTENER)")
                .contains("ke.getID() != KeyEvent.KEY_PRESSED");
        assertThat(hud.indexOf("if (!shows(")).as("the showing rule guards every display").isLessThan(hud.indexOf("overlay.show("));
        String overlay = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/present/KeystrokeOverlay.java"));
        assertThat(overlay).contains("setFocusableWindowState(false)").contains("hide.restart()");
        String action = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/present/ShowKeystrokesAction.java"));
        assertThat(action).contains("path = \"Menu/View\", position = 1180");
    }
}
