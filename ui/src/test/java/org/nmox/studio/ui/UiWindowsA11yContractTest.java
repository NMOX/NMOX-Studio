package org.nmox.studio.ui;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rack's name law (v1.41.0, DeviceContractTest) over the ui module's windows (the Welcome, IRC) (v2.76.0):
 * every button the window paints at construction carries a non-blank
 * accessible name; look-and-feel chrome (javax.swing.plaf.*) is the
 * platform's to name and is excluded in writing.
 */
class UiWindowsA11yContractTest {

    private static void collect(Container c, List<Component> out) {
        for (Component child : c.getComponents()) {
            out.add(child);
            if (child instanceof Container cc) {
                collect(cc, out);
            }
        }
    }

    @Test
    @DisplayName("every button is named")
    void everyButtonIsNamed() throws Exception {
        List<String> unnamed = new ArrayList<>();
        int[] buttons = {0};
        SwingUtilities.invokeAndWait(() -> {
            for (Container window : List.<Container>of(new MainWindow(), new org.nmox.studio.ui.irc.IrcTopComponent())) {
                List<Component> all = new ArrayList<>();
                collect(window, all);
                for (Component c : all) {
                    if (c instanceof AbstractButton b && !b.getClass().getName().startsWith("javax.swing.plaf")) {
                        buttons[0]++;
                        String name = b.getAccessibleContext().getAccessibleName();
                        if (name == null || name.isBlank()) {
                            unnamed.add(window.getClass().getSimpleName() + ": " + b.getClass().getSimpleName() + " '" + b.getText() + "'");
                        }
                    }
                }
            }
        });
        assertThat(buttons[0]).as("the windows have buttons").isPositive();
        assertThat(unnamed).as("a button without an accessible name is invisible to assistive technology").isEmpty();
    }
}
