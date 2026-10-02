package org.nmox.studio.ui.a11y;

import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.explorer.view.NodeRenderer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 126: a row the platform's explorer paints in markup is named for a
 * screen reader with its words, in every tree the platform builds, and a
 * tree with a renderer of its author's choosing is left alone.
 */
class SpokenTreeRowsTest {

    /** The platform's renderer, painting one fixed markup label whatever it is asked for. */
    private static final class PaintsMarkup extends NodeRenderer {
        private final JLabel painted;

        PaintsMarkup(String html) {
            painted = new JLabel(html);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {
            return painted;
        }
    }

    @Test
    @DisplayName("a tree using the platform's renderer is wrapped, once, and its row is named in words")
    void thePlatformsTreeIsSpoken() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            JTree tree = new JTree();
            tree.setCellRenderer(new PaintsMarkup(
                    "<html><b>jdbc:postgresql://127.0.0.1:55001/postgres [postgres on public]</b>"));

            assertThat(SpokenTreeRows.speak(tree)).as("the first meeting wraps").isTrue();
            assertThat(SpokenTreeRows.speak(tree)).as("the second finds nothing to do").isFalse();

            Component row = tree.getCellRenderer().getTreeCellRendererComponent(tree, "x", false, false, true, 0, false);
            assertThat(row.getAccessibleContext().getAccessibleName())
                    .isEqualTo("jdbc:postgresql://127.0.0.1:55001/postgres [postgres on public]");
            assertThat(((JLabel) row).getText()).as("what is painted is untouched").startsWith("<html><b>");
        });
    }

    @Test
    @DisplayName("a tree whose author chose its renderer is left alone")
    void anAuthoredRendererIsNotReplaced() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            JTree swing = new JTree();
            TreeCellRenderer swingsOwn = swing.getCellRenderer();
            assertThat(SpokenTreeRows.speak(swing)).isFalse();
            assertThat(swing.getCellRenderer()).isSameAs(swingsOwn);

            JTree authored = new JTree();
            TreeCellRenderer chosen = new DefaultTreeCellRenderer();
            authored.setCellRenderer(chosen);
            assertThat(SpokenTreeRows.speak(authored)).isFalse();
            assertThat(authored.getCellRenderer()).isSameAs(chosen);
        });
    }

    @Test
    @DisplayName("every platform-rendered tree under a window is found, however deep")
    void treesAreFoundUnderAContainer() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            JTree first = new JTree();
            first.setCellRenderer(new PaintsMarkup("plain words"));
            JTree second = new JTree();
            second.setCellRenderer(new PaintsMarkup("<html><i>x</i>"));
            JTree notOurs = new JTree();
            JPanel inner = new JPanel();
            inner.add(new JScrollPane(second));
            JPanel root = new JPanel();
            root.add(new JScrollPane(first));
            root.add(inner);
            root.add(notOurs);

            assertThat(SpokenTreeRows.speakUnder(root)).isEqualTo(2);
            assertThat(first.getCellRenderer()).isInstanceOf(SpokenTreeRows.Spoken.class);
            assertThat(second.getCellRenderer()).isInstanceOf(SpokenTreeRows.Spoken.class);
            assertThat(SpokenTreeRows.speakUnder(root)).as("a second sweep wraps nothing twice").isZero();
        });
    }

    @Test
    @DisplayName("the platform's renderer is recognised by the name its trees are built with")
    void theNameIsThePlatforms() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            assertThat(NodeRenderer.class.getName()).isEqualTo(SpokenTreeRows.PLATFORM_RENDERER);
            assertThat(SpokenTreeRows.isThePlatforms(new NodeRenderer())).isTrue();
            assertThat(SpokenTreeRows.isThePlatforms(new DefaultTreeCellRenderer())).isFalse();
        });
    }
}
