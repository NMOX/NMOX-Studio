package org.nmox.studio.rack.projectstudio;

import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.explorer.view.TreeView;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 3.1.0 gave Project Studio VS Code's Explorer chord, ⇧⌘E. The chord
 * opens the studio through its open action, which ACTIVATES it — and an
 * activated window that keeps focus on its own frame leaves the arrow
 * keys doing nothing, which is not what "focus the Explorer" means. A
 * focus request on the studio must end at the file tree.
 */
class ProjectStudioFocusTest {

    @Test
    @DisplayName("a focus request on Project Studio lands on the file tree")
    void focusGoesToTheTree() throws Exception {
        ProjectStudioTopComponent[] tc = new ProjectStudioTopComponent[1];
        SwingUtilities.invokeAndWait(() -> tc[0] = new ProjectStudioTopComponent());
        assertThat(tc[0].focusTarget())
                .as("the tree view, which forwards focus to its JTree — not the panel around it")
                .isInstanceOf(TreeView.class);
        assertThat(javax.swing.SwingUtilities.isDescendingFrom(tc[0].focusTarget(), tc[0]))
                .as("the tree the studio focuses is the studio's own")
                .isTrue();
    }
}
