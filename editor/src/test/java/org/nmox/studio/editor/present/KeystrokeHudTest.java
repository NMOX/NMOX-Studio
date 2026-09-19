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
    @DisplayName("the function-key rule is TWO intervals: VK_F13 jumps to 61440, so one F1..F24 range would show unmodified punctuation to the room (v2.184.0)")
    void functionKeysAreNotContiguous() {
        // the constants, not literals typed here — this is the fact the rule
        // has to survive, and reading it from the JDK is what makes the test
        // true of the JDK the product runs on
        assertThat(KeyEvent.VK_F13)
                .as("F13 is not F12 + 1 — the range F1..F24 spans %d..%d", KeyEvent.VK_F1, KeyEvent.VK_F24)
                .isGreaterThan(KeyEvent.VK_F12 + 1);

        assertThat(KeystrokeHud.shows(0, KeyEvent.VK_F1)).isTrue();
        assertThat(KeystrokeHud.shows(0, KeyEvent.VK_F12)).isTrue();
        assertThat(KeystrokeHud.shows(0, KeyEvent.VK_F13)).isTrue();
        assertThat(KeystrokeHud.shows(0, KeyEvent.VK_F24)).isTrue();
        assertThat(KeystrokeHud.shows(0, KeyEvent.VK_ESCAPE)).isTrue();

        // every one of these sits inside 112..61451 and is typed without a
        // modifier: a password holding a quote, a backtick, @ or : would have
        // flashed that key onto the projector
        int[] typedWithoutAModifier = {
            KeyEvent.VK_DELETE, KeyEvent.VK_INSERT, KeyEvent.VK_BACK_QUOTE, KeyEvent.VK_QUOTE,
            KeyEvent.VK_AT, KeyEvent.VK_COLON, KeyEvent.VK_DOLLAR, KeyEvent.VK_AMPERSAND,
            KeyEvent.VK_NUMBER_SIGN, KeyEvent.VK_EXCLAMATION_MARK, KeyEvent.VK_EURO_SIGN,
            KeyEvent.VK_DEAD_GRAVE};
        for (int code : typedWithoutAModifier) {
            assertThat(KeyEvent.getKeyText(code)).isNotNull();
            assertThat(KeystrokeHud.shows(0, code))
                    .as("key code %d (%s) is typing, not a chord", code, KeyEvent.getKeyText(code))
                    .isFalse();
            assertThat(KeystrokeHud.shows(KeyEvent.SHIFT_DOWN_MASK, code))
                    .as("⇧ alone is still typing for key code %d", code)
                    .isFalse();
            assertThat(KeystrokeHud.shows(KeyEvent.META_DOWN_MASK, code))
                    .as("with ⌘ it is a chord and does show")
                    .isTrue();
        }
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

    @Test
    @DisplayName("the pill's linger is 1.6 s unless a walk sets -Dnmox.keystrokes.linger, and nonsense reads as the default")
    void lingerProperty() {
        String key = "nmox.keystrokes.linger";
        String before = System.getProperty(key);
        try {
            System.clearProperty(key);
            assertThat(KeystrokeOverlay.lingerMs()).isEqualTo(1600);
            System.setProperty(key, "60000");
            assertThat(KeystrokeOverlay.lingerMs()).isEqualTo(60000);
            System.setProperty(key, "5");
            assertThat(KeystrokeOverlay.lingerMs()).isEqualTo(1600);
            System.setProperty(key, "999999999");
            assertThat(KeystrokeOverlay.lingerMs()).isEqualTo(1600);
            System.setProperty(key, "soon");
            assertThat(KeystrokeOverlay.lingerMs()).isEqualTo(1600);
        } finally {
            if (before == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, before);
            }
        }
        String src = readOverlay();
        assertThat(src).contains("new Timer(lingerMs(), e -> hideNow())");
    }

    private static String readOverlay() {
        try {
            return Files.readString(Path.of("src/main/java/org/nmox/studio/editor/present/KeystrokeOverlay.java"));
        } catch (java.io.IOException ex) {
            throw new AssertionError(ex);
        }
    }
}
