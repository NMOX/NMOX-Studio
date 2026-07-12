package org.nmox.studio.project;

import java.io.File;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.service.RackService;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.windows.TopComponent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 29 (v1.45.0), workbench half: while the Workbench is showing,
 * its activated nodes carry the aimed directory's DataFolder node — the
 * same contract the rack-module windows hold, so the global selection
 * (and with it Team-menu enablement) is consistent whichever
 * aim-following window is active.
 */
class WorkbenchAimSelectionTest {

    private static File activatedDir(TopComponent tc) {
        Node[] nodes = tc.getActivatedNodes();
        if (nodes == null || nodes.length == 0) {
            return null;
        }
        DataObject dob = nodes[0].getLookup().lookup(DataObject.class);
        return dob == null ? null : FileUtil.toFile(dob.getPrimaryFile());
    }

    private static void awaitActivated(TopComponent tc, File dir) throws Exception {
        File expected = FileUtil.normalizeFile(dir);
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline
                && !expected.equals(activatedDir(tc))) {
            Thread.sleep(20);
        }
        assertThat(activatedDir(tc)).isEqualTo(expected);
    }

    @Test
    @DisplayName("showing workbench publishes the aim; re-aim updates; close detaches")
    void aimBecomesTheActivatedNode(@TempDir Path a, @TempDir Path b, @TempDir Path c)
            throws Exception {
        Rack rack = RackService.getDefault().getRack();
        ProjectExplorerTopComponent[] tc = new ProjectExplorerTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new ProjectExplorerTopComponent());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        SwingUtilities.invokeAndWait(tc[0]::componentShowing);

        rack.setProjectDir(a.toFile());
        awaitActivated(tc[0], a.toFile());
        assertThat(tc[0].getLookup().lookup(DataObject.class))
                .as("the default TC lookup proxies the activated node")
                .isNotNull();

        rack.setProjectDir(b.toFile());
        awaitActivated(tc[0], b.toFile());

        SwingUtilities.invokeAndWait(tc[0]::componentClosed);
        rack.setProjectDir(c.toFile());
        Thread.sleep(200); // generous window for a leaked publication
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(tc[0].getActivatedNodes())
                .as("a closed workbench neither publishes nor pins the old DataObject")
                .isEmpty();
    }

    @Test
    @DisplayName("aims that land while hidden publish on the next show, not before")
    void hiddenWorkbenchDefersPublication(@TempDir Path a) throws Exception {
        // warm the loaders/masterfs machinery: without this, a LEAKED hidden
        // publication can hide behind first-use class loading and land after
        // the negative assertion (see RackAimSelectionTest)
        DataObject.find(FileUtil.toFileObject(FileUtil.normalizeFile(a.toFile())));

        Rack rack = RackService.getDefault().getRack();
        ProjectExplorerTopComponent[] tc = new ProjectExplorerTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new ProjectExplorerTopComponent());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        // never shown: the boot shape of a hidden default-open tab

        rack.setProjectDir(a.toFile());
        Thread.sleep(400);
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(tc[0].getActivatedNodes())
                .as("hidden tabs resolve nothing (v1.38.0 boot law)")
                .isNullOrEmpty();

        SwingUtilities.invokeAndWait(tc[0]::componentShowing);
        awaitActivated(tc[0], a.toFile());
        SwingUtilities.invokeAndWait(tc[0]::componentClosed);
    }
}
