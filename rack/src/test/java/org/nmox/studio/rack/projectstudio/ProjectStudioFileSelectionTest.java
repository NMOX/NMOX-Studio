package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
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
 * Ledger 29 remainder (v1.48.0): Project Studio's file tree refines the
 * v1.45.0 aim selection. Selecting a FILE publishes that file's
 * DataObject node as the activated node — per-file context actions (git
 * Annotate via the chip, platform file verbs) finally see a real file
 * selection — and clearing the selection falls back to the aim node,
 * never an empty selection. Same contract style as
 * {@code ProjectStudioAimSelectionTest}; the storm half lives in
 * {@code AimNodePublisherTest.distinctTargetStormCoalesces}.
 */
class ProjectStudioFileSelectionTest {

    private static File activatedFile(TopComponent tc) {
        Node[] nodes = tc.getActivatedNodes();
        if (nodes == null || nodes.length == 0) {
            return null;
        }
        DataObject dob = nodes[0].getLookup().lookup(DataObject.class);
        return dob == null ? null : FileUtil.toFile(dob.getPrimaryFile());
    }

    private static void awaitActivated(TopComponent tc, File expectedRaw) throws Exception {
        File expected = FileUtil.normalizeFile(expectedRaw);
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline
                && !expected.equals(activatedFile(tc))) {
            Thread.sleep(20);
        }
        assertThat(activatedFile(tc)).isEqualTo(expected);
    }

    private static FileTreePanel findPanel(java.awt.Container c) {
        for (java.awt.Component comp : c.getComponents()) {
            if (comp instanceof FileTreePanel t) {
                return t;
            }
            if (comp instanceof java.awt.Container cc) {
                FileTreePanel r = findPanel(cc);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    @Test
    @DisplayName("selecting a file publishes its node; clearing falls back to the aim")
    void fileSelectionRefinesTheAimNode(@TempDir Path dir) throws Exception {
        File aimed = dir.toFile();
        File file = dir.resolve("app.js").toFile();
        Files.writeString(file.toPath(), "// selected\n");

        Rack rack = RackService.getDefault().getRack();
        ProjectStudioTopComponent[] tc = new ProjectStudioTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new ProjectStudioTopComponent());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        SwingUtilities.invokeAndWait(tc[0]::componentShowing);
        rack.setProjectDir(aimed);
        awaitActivated(tc[0], aimed);

        // drive the REAL explorer tree (the ledger-36 platform rewrite):
        // wait for the root's lazy children to include the file's node,
        // then select it exactly as a keyboard/mouse selection would
        FileTreePanel panel = findPanel(tc[0]);
        assertThat(panel).as("the studio embeds the file tree panel").isNotNull();
        long deadline = System.currentTimeMillis() + 10_000;
        org.openide.nodes.Node fileNode = null;
        while (System.currentTimeMillis() < deadline && fileNode == null) {
            // getNodes(true) computes lazy children — deliberately OFF the EDT.
            // Match by FileObject name, not File.equals: on Windows the @TempDir
            // path carries 8.3 short-name components while FileUtil normalizes to
            // the long form, so File.equals never matched and the loop timed out.
            for (org.openide.nodes.Node n
                    : panel.getExplorerManager().getRootContext().getChildren().getNodes(true)) {
                DataObject dob = n.getLookup().lookup(DataObject.class);
                if (dob != null && file.getName().equals(dob.getPrimaryFile().getNameExt())) {
                    fileNode = n;
                    break;
                }
            }
            Thread.sleep(20);
        }
        assertThat(fileNode).as("the platform children listed the file").isNotNull();

        org.openide.nodes.Node target = fileNode;
        SwingUtilities.invokeAndWait(() -> {
            try {
                panel.getExplorerManager().setSelectedNodes(new org.openide.nodes.Node[]{target});
            } catch (java.beans.PropertyVetoException ex) {
                throw new AssertionError(ex);
            }
        });
        awaitActivated(tc[0], file);
        assertThat(tc[0].getActivatedNodes()[0].getLookup().lookup(DataObject.class))
                .as("per-file context actions read the file's DataObject off the node")
                .isNotNull();

        SwingUtilities.invokeAndWait(() -> {
            try {
                panel.getExplorerManager().setSelectedNodes(new org.openide.nodes.Node[0]);
            } catch (java.beans.PropertyVetoException ex) {
                throw new AssertionError(ex);
            }
        });
        awaitActivated(tc[0], aimed); // the fallback — never an emptied-out selection

        SwingUtilities.invokeAndWait(tc[0]::componentClosed);
    }

    @Test
    @DisplayName("a hidden studio publishes nothing for selection changes — the boot law")
    void hiddenStudioIgnoresSelectionChanges(@TempDir Path dir) throws Exception {
        File file = dir.resolve("hidden.js").toFile();
        Files.writeString(file.toPath(), "// never published\n");

        ProjectStudioTopComponent[] tc = new ProjectStudioTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new ProjectStudioTopComponent());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        // never componentShowing: the tab is hidden behind the selected one

        SwingUtilities.invokeAndWait(() -> tc[0].selectionChanged(file));
        Thread.sleep(300); // generous window for a leaked publication
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(tc[0].getActivatedNodes() == null
                || tc[0].getActivatedNodes().length == 0)
                .as("hidden tabs resolve nothing and publish nothing")
                .isTrue();
        SwingUtilities.invokeAndWait(tc[0]::componentClosed);
    }
}
