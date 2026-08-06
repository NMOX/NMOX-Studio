package org.nmox.studio.rack.projectstudio;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.ActionMap;
import javax.swing.text.DefaultEditorKit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The file tree's Cut/Copy/Delete actually work (v1.285.0, the
 * project-starter persona walk).
 *
 * <p>Since the v1.64.0 platform-tree rewrite every file's context menu
 * has offered Cut, Copy and Delete — and every one of them was
 * permanently disabled, because those are {@code CallbackSystemAction}s
 * that only enable when the activated component's ActionMap binds their
 * keys, and nothing ever bound them. The v1.64.0 comment even
 * celebrated "Cut/Copy/Paste are new". The v1.38.1 pattern: an
 * affordance documented but never exercised is untested.
 */
class FileTreeExplorerActionsTest {

    /** A destroyable stand-in for a platform file node. */
    private static final class DestroyableNode extends AbstractNode {
        DestroyableNode() {
            super(Children.LEAF);
        }

        @Override
        public boolean canDestroy() {
            return true;
        }

        @Override
        public boolean canCut() {
            return true;
        }
    }

    @Test
    @DisplayName("install binds the four callback keys the node menus advertise")
    void installBindsTheCallbackKeys() {
        FileTreePanel panel = new FileTreePanel(dir -> null);
        ActionMap map = new ActionMap();
        panel.installExplorerActions(map);

        assertThat(map.get(DefaultEditorKit.copyAction))
                .as("Copy in the menu is dead without this binding").isNotNull();
        assertThat(map.get(DefaultEditorKit.cutAction)).isNotNull();
        assertThat(map.get(DefaultEditorKit.pasteAction)).isNotNull();
        assertThat(map.get("delete"))
                .as("Delete in the menu is dead without this binding").isNotNull();
    }

    @Test
    @DisplayName("the project root refuses destroy and cut; ordinary folders do not")
    void rootIsNotDeletableFromItsOwnTree() {
        Node destroyable = new DestroyableNode();
        FileTreePanel.HeavyAwareFilterNode asRoot =
                new FileTreePanel.HeavyAwareFilterNode(destroyable, true);
        FileTreePanel.HeavyAwareFilterNode asChild =
                new FileTreePanel.HeavyAwareFilterNode(destroyable, false);

        assertThat(asRoot.canDestroy())
                .as("Delete on the aimed project would destroy the checkout")
                .isFalse();
        assertThat(asRoot.canCut()).isFalse();
        assertThat(asChild.canDestroy())
                .as("a subfolder keeps the platform verb")
                .isTrue();
        assertThat(asChild.canCut()).isTrue();
    }

    @Test
    @DisplayName("the studio installs the bindings and Delete confirms")
    void studioInstallsAndDeleteConfirms() throws Exception {
        String tc = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "rack", "projectstudio", "ProjectStudioTopComponent.java"),
                StandardCharsets.UTF_8);
        assertThat(tc)
                .as("without this call the whole fix is inert")
                .contains("treePanel.installExplorerActions(getActionMap())");
        assertThat(tc)
                .as("cut/copy enablement tracks the activation lifecycle")
                .contains("activateExplorerActions(true)")
                .contains("activateExplorerActions(false)");

        String panel = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "rack", "projectstudio", "FileTreePanel.java"),
                StandardCharsets.UTF_8);
        assertThat(panel)
                .as("Delete is irreversible — the platform confirm dialog is"
                        + " the v1.98.0 safe default, so confirmDelete must"
                        + " stay true")
                .contains("actionDelete(manager, true)");
        assertThat(panel)
                .as("the resolver must mark the root, or the guard guards nothing")
                .contains("getNodeDelegate(), true)");
    }
}
