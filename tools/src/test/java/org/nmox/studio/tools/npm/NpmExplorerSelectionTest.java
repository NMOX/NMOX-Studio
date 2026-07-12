package org.nmox.studio.tools.npm;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.windows.TopComponent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 29 remainder (v1.48.0): NPM Explorer publishes the found Node
 * project's directory node as its own selection — the last studio window
 * whose lookup stayed empty. The contract mirrors
 * {@code ProjectStudioAimSelectionTest}: publish while a project is found,
 * withdraw (a null opinion, so the registry keeps the last real selection
 * for the hand-read fallback) when there is none, and never survive a
 * close. Plus the guard the new publishing makes load-bearing: the
 * registry fallback must skip our own published node, or a re-aim away
 * from a project would echo the stale project straight back.
 */
class NpmExplorerSelectionTest {

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

    private static void drainEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    @DisplayName("a found project publishes its node; no project withdraws it; close detaches")
    void projectNodeFollowsTheAim(@TempDir Path project, @TempDir Path empty)
            throws Exception {
        Files.writeString(project.resolve("package.json"),
                "{\"name\":\"selection-test\",\"scripts\":{\"dev\":\"noop\"}}");
        var rack = org.nmox.studio.rack.service.RackService.getDefault().getRack();
        rack.setProjectDir(project.toFile());

        NpmExplorerTopComponent[] tc = new NpmExplorerTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new NpmExplorerTopComponent());
        SwingUtilities.invokeAndWait(tc[0]::componentOpened);
        SwingUtilities.invokeAndWait(tc[0]::componentShowing); // serves the pending refresh
        awaitActivated(tc[0], project.toFile());

        // re-aim somewhere without a Node project: the pending refresh
        // (headless tabs are never isShowing) is served by the next show,
        // finds nothing, and must WITHDRAW the stale node — null opinion,
        // not empty, so the registry keeps the last real selection
        rack.setProjectDir(empty.toFile());
        drainEdt(); // the listener marks refreshPending on the EDT
        SwingUtilities.invokeAndWait(tc[0]::componentShowing);
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline && activatedDir(tc[0]) != null) {
            Thread.sleep(20);
        }
        assertThat(tc[0].getActivatedNodes()).as("no project → no published node").isNull();

        // and a re-found project republishes past the equality guard
        rack.setProjectDir(project.toFile());
        drainEdt();
        SwingUtilities.invokeAndWait(tc[0]::componentShowing);
        awaitActivated(tc[0], project.toFile());

        SwingUtilities.invokeAndWait(tc[0]::componentClosed);
        rack.setProjectDir(empty.toFile());
        Thread.sleep(200); // generous window for a leaked publication
        drainEdt();
        assertThat(tc[0].getActivatedNodes())
                .as("a closed explorer neither publishes nor pins the old DataObject")
                .isNull();
    }

    @Test
    @DisplayName("the selection fallback skips the node this window itself published")
    void fallbackNeverConsumesOurOwnOutput(@TempDir Path project) throws Exception {
        Files.writeString(project.resolve("package.json"), "{\"name\":\"echo\"}");
        FileObject dirFo = FileUtil.toFileObject(FileUtil.normalizeFile(project.toFile()));
        Node projectNode = DataObject.find(dirFo).getNodeDelegate();

        assertThat(FileUtil.toFile(NpmExplorerTopComponent.projectFromNodes(
                new Node[]{projectNode}, null)))
                .as("another window's selection inside a Node project is found")
                .isEqualTo(FileUtil.normalizeFile(project.toFile()));

        assertThat(NpmExplorerTopComponent.projectFromNodes(
                new Node[]{projectNode}, projectNode))
                .as("our own published node is never consumed — a re-aim away from "
                        + "the project must not echo it back")
                .isNull();
    }
}
