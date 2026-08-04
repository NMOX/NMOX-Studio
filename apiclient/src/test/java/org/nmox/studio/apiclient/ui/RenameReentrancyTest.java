package org.nmox.studio.apiclient.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renaming a request must not re-enter the editor bindings (v1.263.0).
 *
 * <p>Found live against the Hacker News API: typing into the Name field
 * ran a listener that called {@code treeModel.reload()} +
 * {@code restoreSelection()}, and re-selecting the node fired the tree's
 * own selection listener → {@code bindRequest} →
 * {@code nameField.setText(...)} — a Document mutation DURING that
 * Document's own notification, which Swing answers with
 * {@code IllegalStateException("Attempt to mutate in notification")}.
 * The aborted setText left the typed name scrambled (typing
 * "HN top stories" produced "N top storiesH": the first keystroke reset
 * the caret to 0, so every later character landed in front of it) and
 * collapsed the whole collections tree. Shipped broken since API Studio
 * itself (v1.19.0, 2026-07-02).
 *
 * <p>The behavioural test below reproduces the exact Swing mechanics
 * with a miniature of the old wiring, proving the failure is real and
 * that the {@code nodeChanged} shape is immune; the source gate then
 * pins the studio to that shape.
 */
class RenameReentrancyTest {

    /** The listener shape that shipped: reload + reselect from a document event. */
    @Test
    @DisplayName("the OLD shape really does throw — reselect re-enters the document")
    void oldShapeThrowsInNotification() {
        JTextField nameField = new JTextField("New request");
        DefaultMutableTreeNode node = new DefaultMutableTreeNode("req");
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        root.add(node);
        JTree tree = new JTree(new DefaultTreeModel(root));
        // the tree's selection listener re-binds the editor, as onTreeSelect does
        tree.addTreeSelectionListener(e -> nameField.setText("rebound"));

        nameField.getDocument().addDocumentListener(new DocumentListener() {
            private void onChange() {
                ((DefaultTreeModel) tree.getModel()).reload();
                tree.setSelectionPath(new TreePath(node.getPath()));
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                onChange();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onChange();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onChange();
            }
        });

        assertThatThrownBySettingText(nameField);
    }

    private static void assertThatThrownBySettingText(JTextField f) {
        try {
            f.setText("HN top stories");
            throw new AssertionError(
                    "expected IllegalStateException — if this stops throwing, Swing's "
                    + "mutate-in-notification rule changed and this gate needs revisiting");
        } catch (IllegalStateException expected) {
            assertThat(expected).hasMessageContaining("mutate in notification");
        }
    }

    /** The shipped shape: repaint one node, fire no selection event. */
    @Test
    @DisplayName("the NEW shape is immune — nodeChanged fires no selection event")
    void nodeChangedDoesNotReenter() {
        JTextField nameField = new JTextField("New request");
        DefaultMutableTreeNode node = new DefaultMutableTreeNode("req");
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        root.add(node);
        JTree tree = new JTree(new DefaultTreeModel(root));
        int[] rebinds = {0};
        tree.addTreeSelectionListener(e -> {
            rebinds[0]++;
            nameField.setText("rebound");
        });

        nameField.getDocument().addDocumentListener(new DocumentListener() {
            private void onChange() {
                ((DefaultTreeModel) tree.getModel()).nodeChanged(node);
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                onChange();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onChange();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onChange();
            }
        });

        nameField.setText("HN top stories");
        assertThat(nameField.getText())
                .as("the typed name survives intact — no re-entrant setText")
                .isEqualTo("HN top stories");
        assertThat(rebinds[0])
                .as("no selection event, so nothing re-binds the editor")
                .isZero();
    }

    @Test
    @DisplayName("Gate: the rename and method listeners repaint one node, never reload+reselect")
    void studioUsesTheSafeShape() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/apiclient/ui/ApiClientTopComponent.java"),
                StandardCharsets.UTF_8);
        int listener = src.indexOf("nameField.getDocument().addDocumentListener");
        assertThat(listener).as("the name listener exists").isPositive();
        // CODE only: the comment above the fix names reload()/restoreSelection()
        // to explain what went wrong, and a naive contains() would match the
        // explanation and fail on correct code
        String body = stripComments(src.substring(listener, src.indexOf("}));", listener)));
        assertThat(body)
                .as("renaming repaints the one node")
                .contains("repaintTreeLabel(current)");
        assertThat(body)
                .as("renaming must NOT rebuild the tree from a document notification")
                .doesNotContain("reload()")
                .doesNotContain("restoreSelection()");
        assertThat(src)
                .as("repaintTreeLabel fires only treeNodesChanged")
                .contains("nodeChanged(n)");
    }

    /** Java source with // and block comments removed. */
    private static String stripComments(String java) {
        return java.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}
