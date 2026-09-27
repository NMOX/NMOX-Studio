package org.nmox.studio.ui;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.util.KeyboardAccess;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tab lands somewhere visible on the Welcome (3.4, question 3). Its links
 * are buttons with focus painting off and an empty border, so a keyboard
 * user could Tab through START, TOOLING and FIRST STEPS without seeing where
 * they were. Every link-style button the Welcome paints carries the focus
 * ring — a population read from the window, so a link added tomorrow is in it.
 */
class WelcomeFocusRingTest {

    private static void collect(Container c, List<Component> out) {
        for (Component child : c.getComponents()) {
            out.add(child);
            if (child instanceof Container cc) {
                collect(cc, out);
            }
        }
    }

    @Test
    @DisplayName("every link on the Welcome paints a focus ring")
    void linksShowFocus() throws Exception {
        List<AbstractButton> links = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            List<Component> all = new ArrayList<>();
            collect(new MainWindow(), all);
            for (Component c : all) {
                if (c instanceof AbstractButton b && !b.isContentAreaFilled()
                        && !b.getClass().getName().startsWith("javax.swing.plaf")) {
                    links.add(b);
                }
            }
        });
        assertThat(links).as("the Welcome's links").hasSizeGreaterThan(5);
        for (AbstractButton b : links) {
            assertThat(b.getBorder()).as("'" + b.getText() + "' shows where Tab landed")
                    .isInstanceOf(KeyboardAccess.FocusRingBorder.class);
        }
    }
}
