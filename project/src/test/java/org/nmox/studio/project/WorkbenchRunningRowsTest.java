package org.nmox.studio.project;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.LiveRuns;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The RUNNING section on a real (headless) Workbench (v2.73.0): nothing
 * running paints no section; a live run paints its row with a real Stop
 * button carrying an accessible name, and pressing that button kills that
 * run through its own killer and withdraws it.
 */
class WorkbenchRunningRowsTest {

    @AfterEach
    void drain() {
        LiveRuns.stopAll();
        org.nmox.studio.rack.service.ServingRegistry.getDefault().deregister("ide-run:/tmp/shop#9");
    }

    private static void collect(Container c, List<Component> out) {
        for (Component child : c.getComponents()) {
            out.add(child);
            if (child instanceof Container cc) {
                collect(cc, out);
            }
        }
    }

    private static List<Component> tree(ProjectExplorerTopComponent tc) {
        List<Component> all = new ArrayList<>();
        collect(tc, all);
        return all;
    }

    @Test
    @DisplayName("no run, no section; a live run paints a row whose Stop button kills exactly that run")
    void rowStopKillsTheRun() throws Exception {
        ProjectExplorerTopComponent[] tc = new ProjectExplorerTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new ProjectExplorerTopComponent());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        assertThat(tree(tc[0]).stream().filter(c -> c instanceof JLabel l && "RUNNING".equals(l.getText())))
                .as("nothing running: no RUNNING section").isEmpty();

        AtomicBoolean killed = new AtomicBoolean();
        LiveRuns.add(new LiveRuns.Run("ide-run:/tmp/shop#9", "Run — shop", () -> killed.set(true)));
        LiveRuns.add(new LiveRuns.Run("npm-run:/tmp/shop#10", "npm run test — shop", () -> { }));
        org.nmox.studio.rack.service.ServingRegistry.getDefault().register(
                new org.nmox.studio.rack.service.ServingRegistry.Serving("ide-run:/tmp/shop#9", "Run — shop",
                        "http://localhost:3999/", org.nmox.studio.rack.service.ServingRegistry.Kind.WEB, new java.io.File("/tmp/shop")));
        // the listener requests a coalesced (invokeLater) refresh; drain it
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
        List<Component> all = tree(tc[0]);
        assertThat(all.stream().filter(c -> c instanceof JLabel l && "RUNNING".equals(l.getText())))
                .as("the section appears once something runs").hasSize(1);
        // every row and every gesture in the section is named for assistive technology (v2.73.0)
        assertThat(all.stream().filter(c -> c instanceof JButton).map(c -> ((JButton) c).getAccessibleContext().getAccessibleName()))
                .contains("Stop Run — shop", "Stop npm run test — shop");
        assertThat(all.stream().filter(c -> c instanceof javax.swing.JPanel p
                        && p.getAccessibleContext().getAccessibleName() != null
                        && p.getAccessibleContext().getAccessibleName().startsWith("Run — shop — ")))
                .as("the row panel carries the title and subtitle as its name").hasSize(1);
        JButton stop = (JButton) all.stream()
                .filter(c -> c instanceof JButton b && "Stop Run — shop".equals(b.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow(() -> new AssertionError("the row's Stop button, named for assistive technology"));
        assertThat(all.stream().filter(c -> c instanceof JButton b && "Open http://localhost:3999/ in the Browser".equals(b.getAccessibleContext().getAccessibleName())))
                .as("the serving row has a real Open button").hasSize(1);
        SwingUtilities.invokeAndWait(stop::doClick);
        assertThat(killed).as("the row's Stop ran THAT run's killer").isTrue();
        assertThat(LiveRuns.live()).extracting(LiveRuns.Run::id)
                .as("only the pressed row's run is gone").containsExactly("npm-run:/tmp/shop#10");
        SwingUtilities.invokeAndWait(tc[0]::componentClosed);
    }
}
