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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rack's name law (v1.41.0: every control on every faceplate has an
 * accessible name, DeviceContractTest) applied to the Workbench (v2.73.0):
 * every button the home base paints — with and without something running —
 * carries a non-blank accessible name, so the page reads the same to a
 * screen reader as to the eye.
 */
class WorkbenchA11yContractTest {

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
    @DisplayName("every button on the Workbench is named, idle and with runs on the page")
    void everyButtonIsNamed() throws Exception {
        ProjectExplorerTopComponent[] tc = new ProjectExplorerTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new ProjectExplorerTopComponent());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        LiveRuns.add(new LiveRuns.Run("ide-run:/tmp/a11y#1", "Run — a11y", () -> { }));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        List<Component> all = new ArrayList<>();
        collect(tc[0], all);
        List<String> unnamed = new ArrayList<>();
        int buttons = 0;
        for (Component c : all) {
            // the look-and-feel's own chrome (a scroll pane's arrow buttons,
            // javax.swing.plaf.*) is not ours to name — the platform names it
            if (c instanceof AbstractButton b && !b.getClass().getName().startsWith("javax.swing.plaf")) {
                buttons++;
                String name = b.getAccessibleContext().getAccessibleName();
                if (name == null || name.isBlank()) {
                    unnamed.add(b.getClass().getSimpleName() + " '" + b.getText() + "'");
                }
            }
        }
        assertThat(buttons).as("the page has buttons to name (the RUNNING row's Stop at least)").isPositive();
        assertThat(unnamed).as("a button without an accessible name is invisible to assistive technology").isEmpty();
        SwingUtilities.invokeAndWait(tc[0]::componentClosed);
    }
}
