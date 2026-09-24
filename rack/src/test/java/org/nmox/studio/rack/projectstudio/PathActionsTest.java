package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Path;
import javax.swing.Action;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copy Path, Copy Relative Path and Reveal on Project Studio's tree, the
 * rows VS Code's Explorer puts on every file (3.1.0).
 */
class PathActionsTest {

    @Test
    @DisplayName("a relative path is relative to the aimed project, in the platform's separators")
    void relativeToTheProject() {
        File root = new File("/work/shop");
        assertThat(PathActions.relativePath(root, new File("/work/shop/src/app.js")))
                .isEqualTo(Path.of("src", "app.js").toString());
        assertThat(PathActions.relativePath(root, new File("/work/shop"))).as("the root itself").isEqualTo(".");
    }

    @Test
    @DisplayName("outside the project, or with no project aimed, the path is absolute - a climbing ../ would name somewhere else")
    void outsideIsAbsolute() {
        File root = new File("/work/shop");
        File elsewhere = new File("/work/shopping/x.js");
        assertThat(PathActions.relativePath(root, elsewhere)).as("a sibling that merely shares a prefix")
                .isEqualTo(elsewhere.getAbsolutePath());
        assertThat(PathActions.relativePath(null, elsewhere)).isEqualTo(elsewhere.getAbsolutePath());
    }

    @Test
    @DisplayName("the reveal row is named for the file manager the reader has")
    void revealLabelPerOs() {
        assertThat(PathActions.revealLabel(PathActions.Os.MAC)).isEqualTo("Reveal in Finder");
        assertThat(PathActions.revealLabel(PathActions.Os.WINDOWS)).isEqualTo("Reveal in File Explorer");
        assertThat(PathActions.revealLabel(PathActions.Os.OTHER)).isEqualTo("Open Containing Folder");
    }

    @Test
    @DisplayName("a node with no file on disk gets no path rows, and the menu keeps its shape")
    void noFileNoRows() {
        AbstractNode bare = new AbstractNode(Children.LEAF);
        assertThat(PathActions.forNode(bare)).isEmpty();
        Action head = new javax.swing.AbstractAction("Open") {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
            }
        };
        Action[] menu = FileTreePanel.HeavyAwareFilterNode.withPathRows(bare, new Action[] {head}, new Action[] {head});
        assertThat(menu).containsExactly(head, null, head);
    }
}
