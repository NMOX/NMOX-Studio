package org.nmox.studio.ui.irc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The clear-the-line contract, learned the hard way in the v1.205.0
 * live gauntlet: <b>Escape never reaches the IRC input</b> in a docked
 * TopComponent — the window system consumes it above the component, so
 * neither a {@code KeyListener} nor a {@code WHEN_FOCUSED} key binding
 * fires (both were tried against the assembled app, and the leftover
 * text turned a {@code /query} into a public message).
 *
 * <p>Ctrl+U — the readline "kill line" chord — does arrive, and is what
 * the client documents. This is a source gate because the binding lives
 * in a Swing method no headless test can drive; what it protects is the
 * pair: the working chord must stay bound, and no surface may advertise
 * Escape again.
 */
class InputClearGateTest {

    private static String tc() throws Exception {
        return Files.readString(Path.of("src", "main", "java", "org", "nmox",
                "studio", "ui", "irc", "IrcTopComponent.java"));
    }

    @Test
    @DisplayName("Ctrl+U is bound to the clear action — the chord that actually reaches the field")
    void ctrlUStaysBound() throws Exception {
        String s = tc();
        assertThat(s)
                .as("dropping this binding leaves NO working way to abandon a "
                        + "half-typed line — Escape cannot substitute")
                .contains("KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK");
        assertThat(s).contains("\"nmox-irc-clear\"");
        assertThat(s)
                .as("every clear path routes through one method so they cannot drift")
                .contains("private void clearInput()");
    }

    @Test
    @DisplayName("Ctrl+J rides the input field's WHEN_FOCUSED map — window-level bindings die in a docked TC")
    void ctrlJBindsOnTheInputField() throws Exception {
        String s = tc();
        assertThat(s)
                .as("the v2.2.0 Libera walk proved the WHEN_IN_FOCUSED_WINDOW "
                        + "binding never fires in the shipped app (the Escape "
                        + "layer, again) — the jump must be bound where the "
                        + "user's focus lives, the input field")
                .contains("KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK");
        int at = s.indexOf("KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK");
        String around = s.substring(Math.max(0, at - 300), Math.min(s.length(), at + 300));
        assertThat(around)
                .as("the chord must map to the jump action on the FIELD's maps")
                .contains("irc-jump-activity")
                .contains("WHEN_FOCUSED");
    }

    @Test
    @DisplayName("no user-facing surface advertises Escape as the clear key")
    void escapeIsNotAdvertised() throws Exception {
        int at = tc().indexOf("private void commandHelp");
        String help = tc().substring(at, tc().indexOf("\n    }", at));
        assertThat(help)
                .as("/help must not promise Escape — the gauntlet proved it dead")
                .doesNotContain("Escape");
        assertThat(help).contains("Ctrl+U clears the line");

        String guide = Files.readString(Path.of("..", "docs", "user-guide.md"));
        int irc = guide.indexOf("## 6. IRC");
        if (irc < 0) {
            irc = guide.indexOf("IRC (⌥⌘3)");
        }
        assertThat(irc).as("the guide documents IRC").isGreaterThan(0);
        String section = guide.substring(irc, Math.min(guide.length(), irc + 4000));
        assertThat(section).contains("Ctrl+U");
    }
}
