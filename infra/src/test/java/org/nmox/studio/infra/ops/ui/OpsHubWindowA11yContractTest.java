package org.nmox.studio.infra.ops.ui;

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
 * The name law (v1.41.0, made total by WindowNameLawCensusTest) over the
 * Ops Hub: every button the window paints at construction carries a
 * non-blank accessible name; look-and-feel chrome is the platform's to
 * name and is excluded in writing. The HUD's own rows are covered by
 * {@link HudCanvas}'s accessible children (a list of list items).
 */
class OpsHubWindowA11yContractTest {

    private static void collect(Container c, List<Component> out) {
        for (Component child : c.getComponents()) {
            out.add(child);
            if (child instanceof Container cc) {
                collect(cc, out);
            }
        }
    }

    @Test
    @DisplayName("every button is named, and the window names itself")
    void everyButtonIsNamed() throws Exception {
        List<String> unnamed = new ArrayList<>();
        int[] buttons = {0};
        String[] windowName = {null};
        SwingUtilities.invokeAndWait(() -> {
            OpsHubTopComponent window = new OpsHubTopComponent();
            windowName[0] = window.getAccessibleContext().getAccessibleName();
            List<Component> all = new ArrayList<>();
            collect(window, all);
            for (Component c : all) {
                if (c instanceof AbstractButton b && !b.getClass().getName().startsWith("javax.swing.plaf")) {
                    buttons[0]++;
                    String name = b.getAccessibleContext().getAccessibleName();
                    if (name == null || name.isBlank()) {
                        unnamed.add(b.getClass().getSimpleName() + " '" + b.getText() + "'");
                    }
                }
            }
        });
        assertThat(buttons[0]).as("the window has buttons").isPositive();
        assertThat(unnamed).as("a button without an accessible name is invisible to assistive technology").isEmpty();
        assertThat(windowName[0]).isNotBlank();
    }
}
