package org.nmox.studio.rack.blockstudio;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleState;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 48: the canvas — the studio's primary control — is operable
 * without a mouse, and its pieces are visible to assistive technology
 * as accessible children with names, positions, and selection state.
 * Key handling is driven through the same package-private entry the
 * real KeyListener calls, with synthesized events.
 */
class BlockCanvasKeyboardTest {

    /** A canvas over a counter-ish doc, with a change-counting host. */
    private static Object[] canvasWithDoc() throws Exception {
        BlockDoc doc = new BlockDoc();
        Block state = doc.create(BlockKind.STATE);
        state.setParam("name", "count");
        doc.insert(doc.root(), state, 0);
        Block div = doc.create(BlockKind.ELEMENT);
        div.setParam("tag", "div");
        doc.insert(doc.root(), div, 1);
        Block text = doc.create(BlockKind.TEXT);
        text.setParam("text", "hi");
        doc.insert(div, text, 0);

        AtomicInteger changes = new AtomicInteger();
        BlockCanvas[] canvas = new BlockCanvas[1];
        SwingUtilities.invokeAndWait(() -> canvas[0] = new BlockCanvas(new BlockCanvas.Host() {
            @Override
            public void aboutToChange() {
            }

            @Override
            public void changed() {
                changes.incrementAndGet();
            }

            @Override
            public void selected(Block block) {
            }

            @Override
            public void editParams(Block block) {
                changes.addAndGet(1000); // marker: F2 reached the host
            }
        }));
        SwingUtilities.invokeAndWait(() -> canvas[0].setDoc(doc));
        return new Object[]{canvas[0], doc, changes};
    }

    private static void key(BlockCanvas canvas, int code, int modifiers) throws Exception {
        SwingUtilities.invokeAndWait(() -> canvas.handleKey(new KeyEvent(
                canvas, KeyEvent.KEY_PRESSED, 0L, modifiers, code, KeyEvent.CHAR_UNDEFINED)));
    }

    @Test
    @DisplayName("Arrows traverse pieces: Down walks preorder, Left parent, Right child")
    void arrowTraversal() throws Exception {
        Object[] cd = canvasWithDoc();
        BlockCanvas canvas = (BlockCanvas) cd[0];
        BlockDoc doc = (BlockDoc) cd[1];

        key(canvas, KeyEvent.VK_DOWN, 0);           // first row = the component root
        assertThat(canvas.selectedId()).isEqualTo(doc.root().id());
        key(canvas, KeyEvent.VK_DOWN, 0);           // state
        key(canvas, KeyEvent.VK_DOWN, 0);           // div
        Block div = doc.root().children().get(1);
        assertThat(canvas.selectedId()).isEqualTo(div.id());
        key(canvas, KeyEvent.VK_RIGHT, 0);          // into the text child
        assertThat(canvas.selectedId()).isEqualTo(div.children().get(0).id());
        key(canvas, KeyEvent.VK_LEFT, 0);           // back to the div
        assertThat(canvas.selectedId()).isEqualTo(div.id());
        key(canvas, KeyEvent.VK_DOWN, 0);           // past the last row: clamps
        key(canvas, KeyEvent.VK_ESCAPE, 0);
        assertThat(canvas.selectedId()).isNull();
    }

    @Test
    @DisplayName("Alt+Down reorders within the parent; Delete removes; F2 edits")
    void reorderDeleteEdit() throws Exception {
        Object[] cd = canvasWithDoc();
        BlockCanvas canvas = (BlockCanvas) cd[0];
        BlockDoc doc = (BlockDoc) cd[1];
        AtomicInteger changes = (AtomicInteger) cd[2];

        Block state = doc.root().children().get(0);
        key(canvas, KeyEvent.VK_DOWN, 0);
        key(canvas, KeyEvent.VK_DOWN, 0);           // select the state row
        assertThat(canvas.selectedId()).isEqualTo(state.id());

        key(canvas, KeyEvent.VK_DOWN, KeyEvent.ALT_DOWN_MASK);
        assertThat(doc.root().children().get(1).id())
                .as("Alt+Down moved the state below the div")
                .isEqualTo(state.id());
        assertThat(changes.get()).isEqualTo(1);

        key(canvas, KeyEvent.VK_F2, 0);
        assertThat(changes.get()).as("F2 reaches the host's param editor").isGreaterThan(999);

        key(canvas, KeyEvent.VK_DELETE, 0);
        assertThat(doc.find(state.id())).as("Delete removed the selected piece").isNull();
    }

    @Test
    @DisplayName("legalChildren mirrors the interlock law; insertKind inserts and selects")
    void keyboardInsertSeams() throws Exception {
        Object[] cd = canvasWithDoc();
        BlockCanvas canvas = (BlockCanvas) cd[0];
        BlockDoc doc = (BlockDoc) cd[1];

        List<BlockKind> underRoot = canvas.legalChildren(doc.root());
        assertThat(underRoot).contains(BlockKind.STATE, BlockKind.ELEMENT, BlockKind.PROP)
                .doesNotContain(BlockKind.COMPONENT, BlockKind.SET_STATE);
        for (BlockKind k : underRoot) {
            assertThat(BlockRules.accepts(BlockKind.COMPONENT, k)).isTrue();
        }

        boolean[] ok = new boolean[1];
        SwingUtilities.invokeAndWait(() ->
                ok[0] = canvas.insertKind(BlockKind.STATE, doc.root(), 0));
        assertThat(ok[0]).isTrue();
        assertThat(doc.root().children().get(0).kind()).isEqualTo(BlockKind.STATE);
        assertThat(canvas.selectedId()).isEqualTo(doc.root().children().get(0).id());
    }

    @Test
    @DisplayName("Pieces are accessible children with names, roles, and selection state")
    void accessibleChildren() throws Exception {
        Object[] cd = canvasWithDoc();
        BlockCanvas canvas = (BlockCanvas) cd[0];
        BlockDoc doc = (BlockDoc) cd[1];

        AccessibleContext ax = canvas.getAccessibleContext();
        assertThat(ax.getAccessibleChildrenCount())
                .as("one accessible child per layout row")
                .isEqualTo(doc.preorder().size());

        key(canvas, KeyEvent.VK_DOWN, 0); // select the root row
        AccessibleContext first = ax.getAccessibleChild(0).getAccessibleContext();
        assertThat(first.getAccessibleName()).contains("Component").contains("level 1");
        assertThat(first.getAccessibleStateSet().contains(AccessibleState.SELECTED)).isTrue();
        AccessibleContext second = ax.getAccessibleChild(1).getAccessibleContext();
        assertThat(second.getAccessibleStateSet().contains(AccessibleState.SELECTED)).isFalse();
        assertThat(second.getAccessibleIndexInParent()).isEqualTo(1);
    }
}
