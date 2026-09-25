package org.nmox.studio.rack.projectstudio;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.Action;
import javax.swing.ActionMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.actions.NewAction;
import org.openide.actions.NewTemplateAction;
import org.openide.actions.OpenAction;
import org.openide.actions.PropertiesAction;
import org.openide.actions.RenameAction;
import org.openide.explorer.ExplorerManager;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataFolder;
import org.openide.nodes.Node;
import org.openide.util.ContextAwareAction;
import org.openide.util.lookup.Lookups;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rows Project Studio's tree offers on a right-click are rows that work
 * (3.1.0).
 *
 * <p>Measured on the tree's REAL nodes (a DataFolder on disk, filtered the
 * way the panel filters it) with the window's ActionMap in the context the
 * popup builds: the folder menu's first row was {@code NewAction}, which
 * lists a node's NewTypes, and a DataFolder's node has none, so on every
 * folder the row read "Add" and was grey, from v1.64.0 until this release.
 * The comment above it said "New (templates-aware)"; the platform's
 * templates-aware row is {@code NewTemplateAction}, now wired.
 *
 * <p>Paste stays grey until something is on the clipboard, and Cut and
 * Delete on the aimed root are refused on purpose (v1.285.0). Find is bound
 * by the search module when the window activates, which a unit test cannot
 * stand up, so it is not asserted here.
 */
class FileTreeRowsLiveTest {

    private record Tree(Node root, Node folder, Node file, ActionMap map) {}

    private static Tree tree(Path dir) throws Exception {
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("src/app.js"), "x");
        FileTreePanel panel = new FileTreePanel(d -> null);
        ActionMap map = new ActionMap();
        panel.installExplorerActions(map);
        Node root = new FileTreePanel.HeavyAwareFilterNode(
                DataFolder.findFolder(FileUtil.toFileObject(dir.toFile())).getNodeDelegate(), true);
        ExplorerManager em = panel.getExplorerManager();
        em.setRootContext(root);
        Node folder = root.getChildren().getNodes(true)[0];
        Node file = folder.getChildren().getNodes(true)[0];
        return new Tree(root, folder, file, map);
    }

    /** The row of {@code kind} on {@code node}'s menu, resolved as the popup resolves it. */
    private static Action row(Node node, ActionMap map, Class<?> kind) {
        for (Action a : node.getActions(true)) {
            if (kind.isInstance(a)) {
                return a instanceof ContextAwareAction c
                        ? c.createContextAwareInstance(Lookups.fixed(map, node)) : a;
            }
        }
        return null;
    }

    @Test
    @DisplayName("a folder's New row is the templates menu, and it is live")
    void newIsLiveOnFolders(@TempDir Path dir) throws Exception {
        Tree t = tree(dir);
        for (Node folder : new Node[] {t.root(), t.folder()}) {
            Action created = row(folder, t.map(), NewTemplateAction.class);
            assertThat(created).as("the folder menu offers the templates-aware New").isNotNull();
            assertThat(created.isEnabled())
                    .as("New on a folder: grey on every folder from v1.64.0 until 3.1.0")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("no tree menu carries NewAction, which a DataFolder's node can never enable")
    void noDeadNewTypesRow(@TempDir Path dir) throws Exception {
        Tree t = tree(dir);
        for (Node n : new Node[] {t.root(), t.folder(), t.file()}) {
            for (Action a : n.getActions(true)) {
                if (a == null) {
                    continue;   // a separator
                }
                assertThat(a).as("the NewTypes row reads \"Add\" and is grey on every folder")
                        .isNotInstanceOf(NewAction.class);
            }
        }
    }

    @Test
    @DisplayName("files, folders and the root all carry Open on GitHub and Copy GitHub Link, after the path rows (3.2.0)")
    void gitHubRowsOnFilesAndFolders(@TempDir Path dir) throws Exception {
        Tree t = tree(dir);
        for (Node n : new Node[] {t.root(), t.folder(), t.file()}) {
            java.util.List<String> names = new java.util.ArrayList<>();
            for (Action a : n.getActions(true)) {
                names.add(a == null ? "—" : String.valueOf(a.getValue(Action.NAME)));
            }
            assertThat(names).as(n.getDisplayName() + "'s menu")
                    .containsSubsequence("Copy Path", "Copy Relative Path", "—", "Open on GitHub", "Copy GitHub Link");
            for (Action a : n.getActions(true)) {
                if (a != null && String.valueOf(a.getValue(Action.NAME)).contains("GitHub")) {
                    assertThat(a.isEnabled()).as("a GitHub row is live; its refusals speak on click").isTrue();
                }
            }
        }
    }

    @Test
    @DisplayName("Open and Rename on a file, Rename and Properties on a folder, are live")
    void theOtherPlatformRowsWork(@TempDir Path dir) throws Exception {
        Tree t = tree(dir);
        assertThat(row(t.file(), t.map(), OpenAction.class).isEnabled()).as("Open on a file").isTrue();
        assertThat(row(t.file(), t.map(), RenameAction.class).isEnabled()).as("Rename on a file").isTrue();
        assertThat(row(t.folder(), t.map(), RenameAction.class).isEnabled()).as("Rename on a folder").isTrue();
        assertThat(row(t.folder(), t.map(), PropertiesAction.class).isEnabled())
                .as("Properties on a folder").isTrue();
    }
}
