package org.nmox.studio.infra.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JList;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.util.KeyboardAccess;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Infra Designer's canvas without a mouse (3.4, question 3): the arrow
 * keys select resources, Enter opens properties, Shift+F10 the node menu,
 * W wires through the same model call and rule table as the drag, and a
 * screen reader hears the selected resource and its wires. Driven through
 * the canvas's own key bindings on a real InfraGraph.
 */
class FlowCanvasKeyboardTest {

    private final InfraGraph graph = new InfraGraph();
    private final InfraNode vpc = graph.addNode(NodeKind.VPC, 100, 100);
    private final InfraNode droplet = graph.addNode(NodeKind.DROPLET, 400, 100);
    private final InfraNode db = graph.addNode(NodeKind.DB_POSTGRES, 100, 300);
    private final List<String> events = new ArrayList<>();
    private final List<List<InfraNode>> offered = new ArrayList<>();
    private final FlowCanvas canvas = new FlowCanvas(graph, new FlowCanvas.Callbacks() {
        @Override
        public void nodeDoubleClicked(InfraNode node) {
            events.add("open " + node.id);
        }

        @Override
        public void nodeContextMenu(InfraNode node, Point screenPoint) {
            events.add("menu " + node.id);
        }

        @Override
        public void selectionChanged(InfraNode node) {
            events.add("select " + (node == null ? null : node.id));
        }

        @Override
        public void wireRefused(InfraNode from, InfraNode to, boolean duplicate) {
            events.add("refused " + from.id + ">" + to.id);
        }
    });

    FlowCanvasKeyboardTest() {
        canvas.setSize(800, 600);
    }

    private void key(String stroke) {
        assertThat(KeyboardAccess.perform(canvas, KeyStroke.getKeyStroke(stroke))).as(stroke + " is bound").isTrue();
    }

    @Test
    @DisplayName("arrow keys walk the resources by direction, from the first in reading order")
    void arrowsSelect() {
        key("RIGHT");
        assertThat(canvas.getSelectedNode()).as("nothing selected: the first in reading order").isSameAs(vpc);
        key("RIGHT");
        assertThat(canvas.getSelectedNode()).isSameAs(droplet);
        key("RIGHT");
        assertThat(canvas.getSelectedNode()).as("nothing further right: stays").isSameAs(droplet);
        key("LEFT");
        assertThat(canvas.getSelectedNode()).isSameAs(vpc);
        key("DOWN");
        assertThat(canvas.getSelectedNode()).isSameAs(db);
        assertThat(events).contains("select " + db.id);
    }

    @Test
    @DisplayName("Enter opens the selected resource's properties; Shift+F10 and the menu key open its menu")
    void enterAndMenu() {
        key("ENTER");
        assertThat(events).as("nothing selected, nothing opens").isEmpty();
        key("RIGHT");
        key("ENTER");
        key("shift F10");
        assertThat(KeyboardAccess.perform(canvas, KeyboardAccess.CONTEXT_MENU)).isTrue();
        assertThat(events).containsSubsequence("open " + vpc.id, "menu " + vpc.id, "menu " + vpc.id);
    }

    @Test
    @DisplayName("W wires to a chosen legal target through graph.connect; already-wired and illegal ones are never offered")
    void wireFromTheKeyboard() {
        canvas.wireChooser = (from, targets) -> {
            offered.add(List.copyOf(targets));
            return targets.isEmpty() ? null : targets.get(0);
        };
        key("RIGHT");
        key("W");
        assertThat(offered.get(0)).containsExactlyInAnyOrder(droplet, db);
        assertThat(graph.getWires()).contains(new InfraGraph.Wire(vpc.id, offered.get(0).get(0).id));
        key("W");
        assertThat(offered.get(1)).as("a wire already made is not offered again").hasSize(1)
                .doesNotContain(offered.get(0).get(0));

        offered.clear();
        canvas.setLocked(true);
        key("W");
        assertThat(offered).as("a cloud operation holds the canvas: no wiring, as for the drag").isEmpty();
    }

    @Test
    @DisplayName("Shift+arrows move the selected resource one grid step")
    void shiftMoves() {
        key("RIGHT");
        key("shift RIGHT");
        key("shift DOWN");
        assertThat(vpc.x).isEqualTo(110);
        assertThat(vpc.y).isEqualTo(110);
    }

    @Test
    @DisplayName("a screen reader hears the canvas, the selected resource, and its wires")
    void accessibleContext() {
        AccessibleContext ac = canvas.getAccessibleContext();
        assertThat(ac.getAccessibleRole()).isEqualTo(AccessibleRole.CANVAS);
        assertThat(ac.getAccessibleName()).isEqualTo("Infrastructure design");
        assertThat(ac.getAccessibleDescription()).contains("Arrow keys").contains("Enter");
        graph.connect(vpc, droplet);
        List<String> announced = new ArrayList<>();
        ac.addPropertyChangeListener(e -> {
            if (AccessibleContext.ACCESSIBLE_NAME_PROPERTY.equals(e.getPropertyName())) {
                announced.add(String.valueOf(e.getNewValue()));
            }
        });
        key("RIGHT");
        String name = "Infrastructure design: " + NodeKind.VPC.getDisplayName() + " " + vpc.label;
        assertThat(ac.getAccessibleName()).isEqualTo(name);
        assertThat(announced).containsExactly(name);
        assertThat(ac.getAccessibleDescription())
                .isEqualTo("Serves: " + NodeKind.DROPLET.getDisplayName() + " " + droplet.label
                        + ". Served by: nothing.");
    }

    @Test
    @DisplayName("Enter on a palette entry adds that resource, as the double-click does")
    @SuppressWarnings("unchecked")
    void paletteEnterAdds() {
        InfraPalette palette = new InfraPalette(graph);
        JList<Object> list = find(palette, JList.class);
        int before = graph.getNodes().size();
        list.setSelectedIndex(0);   // a category heading: nothing to add
        KeyboardAccess.perform(list, KeyboardAccess.ENTER);
        assertThat(graph.getNodes()).hasSize(before);
        list.setSelectedIndex(1);
        assertThat(KeyboardAccess.perform(list, KeyboardAccess.ENTER)).isTrue();
        assertThat(graph.getNodes()).hasSize(before + 1);
    }

    private static <T> T find(Container c, Class<T> type) {
        for (Component child : c.getComponents()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container cc) {
                T hit = find(cc, type);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }
}
