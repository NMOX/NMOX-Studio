package org.nmox.studio.project;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.LiveRuns;
import org.nmox.studio.core.util.KeyboardAccess;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tab lands somewhere visible on the Workbench (3.4, question 3). Its row
 * titles are buttons drawn as links — no content area, an empty border —
 * and under FlatLaf focus painting alone draws nothing on such a button, so
 * the keyboard's place was invisible. Every link-style button carries the
 * focus ring, derived from the buttons the page actually paints.
 */
class WorkbenchFocusRingTest {

    @AfterEach
    void drain() {
        LiveRuns.stopAll();
    }

    private static void collect(Container c, List<Component> out) {
        for (Component child : c.getComponents()) {
            out.add(child);
            if (child instanceof Container cc) {
                collect(cc, out);
            }
        }
    }

    @Test
    @DisplayName("every link-style button on the Workbench paints a focus ring")
    void linksShowFocus() throws Exception {
        ProjectExplorerTopComponent[] tc = new ProjectExplorerTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new ProjectExplorerTopComponent());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        LiveRuns.add(new LiveRuns.Run("ide-run:/tmp/ring#1", "Run — ring", () -> { }));
        List<AbstractButton> links = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            SwingUtilities.invokeAndWait(() -> { });
            List<Component> all = new ArrayList<>();
            collect(tc[0], all);
            links.clear();
            for (Component c : all) {
                if (c instanceof AbstractButton b && !b.isContentAreaFilled()
                        && !b.getClass().getName().startsWith("javax.swing.plaf")) {
                    links.add(b);
                }
            }
            if (!links.isEmpty()) {
                break;
            }
            Thread.sleep(20);
        }
        assertThat(links).as("the page paints link-style rows, so the law covers something").isNotEmpty();
        for (AbstractButton b : links) {
            assertThat(b.getBorder()).as("'" + b.getText() + "' shows where Tab landed")
                    .isInstanceOf(KeyboardAccess.FocusRingBorder.class);
        }
    }
}
