package org.nmox.studio.rack.projectstudio;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A file tree row is heard as the words it paints (3.4). The 3.4 walk read
 * the AX tree VoiceOver reads and found every git-annotated row named as
 * the platform renderer's markup.
 */
class FileTreeSpokenRowsTest {

    @Test
    @DisplayName("a row painted in markup is named with its words")
    void markupRowIsNamedInWords() {
        // as the platform paints it, with NO <html> prefix: with one, Swing names
        // the label in words by itself and this test passed with the wrapper
        // naming nothing (found in 3.5.1 by running that mutant)
        JLabel painted = new JLabel("<font color=\"#ff6464\">a.txt</font><font color=\"#ffffff\"> [UU]</font>");
        FileTreePanel.SpokenRows rows = new FileTreePanel.SpokenRows((t, v, s, e, l, r, f) -> painted);
        Component c = rows.getTreeCellRendererComponent(new JTree(), "x", false, false, true, 0, false);
        assertThat(c).isSameAs(painted);
        assertThat(c.getAccessibleContext().getAccessibleName()).isEqualTo("a.txt [UU]");
    }

    @Test
    @DisplayName("the panel's tree carries it: a seam nobody calls names nothing")
    void thePanelsTreeIsWrapped() throws Exception {
        FileTreePanel[] panel = new FileTreePanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new FileTreePanel());
        JTree tree = viewOf(panel[0]).tree();
        assertThat(tree).isNotNull();
        assertThat(tree.getCellRenderer()).isInstanceOf(FileTreePanel.SpokenRows.class);
    }

    private static FileTreePanel.SpokenTreeView viewOf(Container c) {
        for (Component child : c.getComponents()) {
            if (child instanceof FileTreePanel.SpokenTreeView v) {
                return v;
            }
        }
        return null;
    }
}
