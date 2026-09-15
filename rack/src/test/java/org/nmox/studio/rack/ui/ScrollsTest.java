package org.nmox.studio.rack.ui;

import java.awt.Dimension;
import java.awt.Point;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rack view opens at the start of its content (v2.162.0): the forge's
 * Hebrew and Arabic Task Rack pictures showed every device face cut off,
 * because a mirrored window opens a wide view at Swing's zero scroll
 * VALUE, which is the far end of the content. Mutation-proven: a rule that
 * leaves the position alone fails {@code aScrolledViewOpensAtItsContentsStart}
 * by name.
 */
class ScrollsTest {

    private static JScrollPane wideScrollPane() {
        JPanel wide = new JPanel();
        wide.setPreferredSize(new Dimension(2_000, 400));
        wide.setSize(2_000, 400);
        JScrollPane pane = new JScrollPane(wide);
        pane.setSize(500, 300);
        pane.getViewport().setSize(500, 300);
        return pane;
    }

    @Test
    @DisplayName("a scrolled view opens at the start of its content, wherever it was")
    void aScrolledViewOpensAtItsContentsStart() {
        JScrollPane pane = wideScrollPane();
        pane.getViewport().setViewPosition(new Point(1_400, 60));
        assertThat(pane.getViewport().getViewPosition().x)
                .as("the fixture really starts away from the content's start")
                .isGreaterThan(0);

        Scrolls.toContentStart(pane);

        assertThat(pane.getViewport().getViewPosition().x)
                .as("the first column of the content is showing")
                .isZero();
        assertThat(pane.getViewport().getViewPosition().y)
                .as("how far down the reader had scrolled is left alone")
                .isEqualTo(60);
    }

    @Test
    @DisplayName("a view already at its start, or missing, is left alone")
    void nothingToDoIsSafe() {
        JScrollPane pane = wideScrollPane();
        Scrolls.toContentStart(pane);
        assertThat(pane.getViewport().getViewPosition()).isEqualTo(new Point(0, 0));
        Scrolls.toContentStart(null); // must not throw
    }

    @Test
    @DisplayName("the rack window asks for it on every show")
    void theRackWindowAsksForIt() throws Exception {
        // a rule with green tests and no call site is a payload without a
        // gate (the v1.321.0 law): the window must really ask
        Path window = Path.of("src/main/java/org/nmox/studio/rack/RackTopComponent.java");
        assertThat(window).isRegularFile();
        String source = Files.readString(window);
        assertThat(Pattern.compile("(?m)^\\s*(\\(\\) ->\\s*)?(\\w+\\.)*Scrolls\\.toContentStart\\(").matcher(source).find())
                .as("RackTopComponent calls Scrolls.toContentStart")
                .isTrue();
        assertThat(source).contains("protected void componentShowing()");
    }
}
