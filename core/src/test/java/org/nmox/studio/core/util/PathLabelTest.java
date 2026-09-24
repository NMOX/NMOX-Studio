package org.nmox.studio.core.util;

import java.util.function.ToIntFunction;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PathLabelTest {

    private static final ToIntFunction<String> CHARS = s -> s.codePointCount(0, s.length()) * 10;
    private static final String DEEP = "/private/tmp/claude-501/-Users-david-vcs-git-github-nmox-NMOX-Studio/"
            + "791bf260-044d-4624-a2fc-7dcbfe17cd1f/scratchpad/walk2/proj";

    @Test
    @DisplayName("a path that fits is shown whole")
    void fitsWhole() {
        assertThat(PathLabel.fitMiddle("/Users/david/NMOX", CHARS, 1000)).isEqualTo("/Users/david/NMOX");
    }

    @Test
    @DisplayName("a long path keeps both ends, the project's name most of all, and fits exactly")
    void keepsTheEnds() {
        String shown = PathLabel.fitMiddle(DEEP, CHARS, 300);
        assertThat(CHARS.applyAsInt(shown)).isLessThanOrEqualTo(300);
        assertThat(shown).startsWith("/private").endsWith("walk2/proj").contains("…");
        assertThat(PathLabel.fitMiddle(DEEP, CHARS, 310).length()).as("the longest that fits").isGreaterThanOrEqualTo(shown.length());
    }

    @Test
    @DisplayName("a surrogate pair is never split")
    void codePoints() {
        String emoji = "/home/" + "😀".repeat(40) + "/app";
        String shown = PathLabel.fitMiddle(emoji, CHARS, 200);
        for (int k = 0; k < shown.length(); k++) {
            char ch = shown.charAt(k);
            if (Character.isHighSurrogate(ch)) {
                assertThat(k + 1 < shown.length() && Character.isLowSurrogate(shown.charAt(k + 1))).isTrue();
            }
            if (Character.isLowSurrogate(ch)) {
                assertThat(k > 0 && Character.isHighSurrogate(shown.charAt(k - 1))).isTrue();
            }
        }
    }

    @Test
    @DisplayName("a deep path never asks its window for more than the cap, and the tooltip holds all of it")
    void neverWidensItsWindow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PathLabel label = new PathLabel();
            label.setPath(DEEP);
            assertThat(label.getPreferredSize().width).isLessThanOrEqualTo(PathLabel.PREFERRED_CAP);
            assertThat(label.getMinimumSize().width).isZero();
            assertThat(label.getToolTipText().strip()).isEqualTo(DEEP);
            JPanel dock = new JPanel(new BorderLayout());
            dock.add(label, BorderLayout.SOUTH);
            assertThat(dock.getPreferredSize().width).isLessThanOrEqualTo(PathLabel.PREFERRED_CAP + 10);
            label.setBounds(0, 0, 150, 20);
            label.dispatchEvent(new java.awt.event.ComponentEvent(label, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
            assertThat(label.getText()).contains("…").endsWith("proj");
        });
    }

    @Test
    @DisplayName("a path is text, never markup")
    void plain() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PathLabel label = new PathLabel();
            label.setPath("<html><img src=http://x/>");
            assertThat(label.getText()).doesNotStartWith("<html>");
        });
    }
}
