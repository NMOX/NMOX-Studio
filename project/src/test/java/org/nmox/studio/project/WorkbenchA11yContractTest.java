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
 * screen reader as to the eye. A JButton's name falls back to its text,
 * so this contract is the FLOOR (nothing nameless); the per-run names
 * ("Stop Run — shop", not a bare "Stop") are pinned by
 * WorkbenchRunningRowsTest, which finds the buttons BY those names — the
 * mutant that drops the explicit name dies there, not here.
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
        // the run's row arrives through a coalesced refresh the EDT may be
        // mid-way through (the v2.82.0 convergence law): poll for the row's
        // own Stop button, so the contract really covers it, never count drains
        List<Component> all = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            SwingUtilities.invokeAndWait(() -> { });
            all.clear();
            collect(tc[0], all);
            if (all.stream().anyMatch(c -> c instanceof AbstractButton b
                    && "Stop Run — a11y".equals(b.getAccessibleContext().getAccessibleName()))) {
                break;
            }
            Thread.sleep(20);
        }
        assertThat(all.stream().anyMatch(c -> c instanceof AbstractButton b
                && "Stop Run — a11y".equals(b.getAccessibleContext().getAccessibleName())))
                .as("the run's row (and its Stop) is on the page, so the contract covers it").isTrue();
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

    @Test
    @DisplayName("text the Workbench had to cut is still readable in full somewhere")
    void cutTextIsNeverLost() throws Exception {
        // v2.119.0, the fresh-install walk: the TOOLING descriptions were
        // being middle-elided into gibberish, and the only full copy lived
        // in the title button's accessible name — where a sighted user
        // cannot get at it. The v1.282.0 law one surface over: a widget
        // that has to cut its text carries the whole of it as a tooltip.
        ProjectExplorerTopComponent[] tc = new ProjectExplorerTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new ProjectExplorerTopComponent());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        List<Component> all = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> collect(tc[0], all));
        List<String> silent = new ArrayList<>();
        int cut = 0;
        for (Component c : all) {
            // the POPULATION is what the window says it shortened, not what
            // happens to end in an ellipsis: "detecting…" is a progress
            // label that was never cut, and the first cut of this gate
            // failed the Windows lane for exactly that reason
            if (c instanceof javax.swing.JLabel l
                    && l.getClientProperty(ProjectExplorerTopComponent.SHORTENED) != null) {
                cut++;
                String full = l.getToolTipText();
                if (full == null || full.isBlank()) {
                    silent.add("'" + l.getText() + "'");
                }
            }
        }
        assertThat(silent)
                .as("a row that shows an ellipsis and offers no way to read the rest "
                        + "has simply lost the text")
                .isEmpty();
        assertThat(cut).as("the fresh Workbench paints at least one shortened subtitle "
                + "(English's own longest is 51 characters against a 38 budget), "
                + "or this gate is measuring nothing").isPositive();
    }
}
