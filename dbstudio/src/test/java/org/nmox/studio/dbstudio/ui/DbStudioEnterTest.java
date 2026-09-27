package org.nmox.studio.dbstudio.ui;

import java.awt.Component;
import java.awt.Container;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JEditorPane;
import javax.swing.JList;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.util.KeyboardAccess;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB Studio's double-click gestures answer Enter (3.4, question 3): the
 * history list loads an entry back into the console, and the connection
 * tree peeks a table or opens and closes any other node.
 */
class DbStudioEnterTest {

    private static <T> T find(Container c, Class<T> type, java.util.function.Predicate<T> test) {
        for (Component child : c.getComponents()) {
            if (type.isInstance(child) && test.test(type.cast(child))) {
                return type.cast(child);
            }
            if (child instanceof Container cc) {
                T hit = find(cc, type, test);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    @Test
    @DisplayName("Enter on a history entry loads it into the console, as the double-click does")
    @SuppressWarnings("unchecked")
    void enterLoadsHistory() throws Exception {
        List<String> console = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            DbStudioTopComponent window = new DbStudioTopComponent();
            JList<ConsoleHistory.Entry> history = find(window, JList.class,
                    l -> Bundle.DbStudioTopComponent_historyA11y().equals(l.getAccessibleContext().getAccessibleName()));
            JEditorPane pane = find(window, JEditorPane.class, p -> true);
            ((DefaultListModel<ConsoleHistory.Entry>) history.getModel())
                    .addElement(new ConsoleHistory.Entry("SELECT 42;", "SQLITE", 1L));
            history.setSelectedIndex(0);
            assertThat(KeyboardAccess.perform(history, KeyboardAccess.ENTER)).isTrue();
            console.add(pane.getText());
        });
        assertThat(console.get(0)).contains("SELECT 42;");
    }

    @Test
    @DisplayName("Enter on a tree node that is not a table opens and closes it")
    void enterTogglesANode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DbStudioTopComponent window = new DbStudioTopComponent();
            JTree tree = find(window, JTree.class, t -> true);
            DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
            DefaultMutableTreeNode group = new DefaultMutableTreeNode("group");
            group.add(new DefaultMutableTreeNode("leaf"));
            model.insertNodeInto(group, root, root.getChildCount());
            TreePath path = new TreePath(group.getPath());
            tree.setSelectionPath(path);
            boolean before = tree.isExpanded(path);
            assertThat(KeyboardAccess.perform(tree, KeyboardAccess.ENTER)).isTrue();
            assertThat(tree.isExpanded(path)).isEqualTo(!before);
        });
    }

    @Test
    @DisplayName("the double-click and Enter share the peek: a table node peeks on either")
    void peekIsShared() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/dbstudio/ui/DbStudioTopComponent.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        int at = src.indexOf("private void enterOnTree()");
        String body = src.substring(at, src.indexOf("\n    }\n", at));
        assertThat(body).contains("instanceof TableInfo info").contains("peek(info);");
        assertThat(src).contains("KeyboardAccess.onEnter(tree, this::enterOnTree)");
    }
}
