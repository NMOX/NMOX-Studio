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

    private static JTree findTree(java.awt.Container c) {
        for (java.awt.Component comp : c.getComponents()) {
            if (comp instanceof JTree t) {
                return t;
            }
            if (comp instanceof java.awt.Container cc) {
                JTree r = findTree(cc);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    /** The tree row for {@code file} once the async scan lands, else null. */
    private static TreePath rowFor(JTree tree, File file) {
        Object root = tree.getModel().getRoot();
        if (!(root instanceof DefaultMutableTreeNode rootNode)) {
            return null;
        }
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            if (file.equals(child.getUserObject())) {
                return new TreePath(child.getPath());
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

        // drive the REAL tree: wait for the async scan to list the file,
        // then select its row exactly as a keyboard/mouse selection would
        AtomicReference<JTree> treeRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> treeRef.set(findTree(tc[0])));
        assertThat(treeRef.get()).as("the studio embeds one JTree").isNotNull();
        long deadline = System.currentTimeMillis() + 10_000;
        AtomicReference<TreePath> row = new AtomicReference<>();
        while (System.currentTimeMillis() < deadline && row.get() == null) {
            SwingUtilities.invokeAndWait(() -> row.set(rowFor(treeRef.get(), file)));
            Thread.sleep(20);
        }
        assertThat(row.get()).as("the background scan listed the file").isNotNull();

        SwingUtilities.invokeAndWait(() -> treeRef.get().setSelectionPath(row.get()));
        awaitActivated(tc[0], file);
        assertThat(tc[0].getActivatedNodes()[0].getLookup().lookup(DataObject.class))
                .as("per-file context actions read the file's DataObject off the node")
                .isNotNull();

        SwingUtilities.invokeAndWait(() -> treeRef.get().clearSelection());
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
