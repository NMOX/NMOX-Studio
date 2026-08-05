package org.nmox.studio.infra.ui;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.concurrent.atomic.AtomicBoolean;

import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.NodeKind;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wire gesture works from the CONNECTOR DOT the user actually sees
 * (v1.271.0, the DevOps persona walk). The output nub is painted
 * centered on the node's right edge, so half the dot lies outside the
 * node rectangle — and the old press handler consulted {@code nodeAt}
 * (inside-the-rect only) before it ever tested the nub, so pressing
 * the dot's center or outer half PANNED THE CANVAS instead of starting
 * a wire; the walk hit it four times in a row before reading the code.
 * The drop side had the same flaw: releasing on the target's input nub
 * (left edge, half outside) returned no target and cancelled the wire
 * with no explanation. These tests drive the canvas's real mouse
 * listener with synthesized events, headless.
 */
class FlowCanvasWireGestureTest {

    private static final class Fixture {

        final InfraGraph graph = new InfraGraph();
        final InfraNode a;
        final InfraNode b;
        final FlowCanvas canvas;
        final AtomicBoolean refused = new AtomicBoolean();

        Fixture() {
            a = graph.addNode(NodeKind.DB_POSTGRES, 100, 100);
            b = graph.addNode(NodeKind.DROPLET, 100, 300);
            canvas = new FlowCanvas(graph, new FlowCanvas.Callbacks() {
                @Override
                public void nodeDoubleClicked(InfraNode node) {
                }

                @Override
                public void nodeContextMenu(InfraNode node, Point screenPoint) {
                }

                @Override
                public void selectionChanged(InfraNode node) {
                }

                @Override
                public void wireRefused(InfraNode from, InfraNode to, boolean duplicate) {
                    refused.set(true);
                }
            });
            canvas.setSize(800, 600);
        }

        // zoom 1, pan 0: world coordinates equal component coordinates
        void press(int x, int y) {
            dispatch(MouseEvent.MOUSE_PRESSED, x, y);
        }

        void drag(int x, int y) {
            MouseEvent e = event(MouseEvent.MOUSE_DRAGGED, x, y);
            for (MouseMotionListener l : canvas.getMouseMotionListeners()) {
                l.mouseDragged(e);
            }
        }

        void release(int x, int y) {
            dispatch(MouseEvent.MOUSE_RELEASED, x, y);
        }

        private void dispatch(int id, int x, int y) {
            MouseEvent e = event(id, x, y);
            for (MouseListener l : canvas.getMouseListeners()) {
                if (id == MouseEvent.MOUSE_PRESSED) {
                    l.mousePressed(e);
                } else {
                    l.mouseReleased(e);
                }
            }
        }

        private MouseEvent event(int id, int x, int y) {
            return new MouseEvent(canvas, id, System.currentTimeMillis(),
                    0, x, y, 1, false);
        }
    }

    @Test
    @DisplayName("a wire starts from the nub's CENTER — the pixel the dot invites")
    void wireStartsFromNubCenter() {
        Fixture f = new Fixture();
        int nubX = f.a.x + FlowCanvas.NODE_W;        // the painted dot's center
        int nubY = f.a.y + FlowCanvas.NODE_H / 2;
        f.press(nubX, nubY);
        f.drag(nubX + 20, nubY + 80);
        f.release(f.b.x + 50, f.b.y + 10);           // drop inside the target
        assertThat(f.graph.getWires())
                .as("pressing the visible connector dot arms a wire, it does"
                        + " not pan the canvas out from under the cursor")
                .hasSize(1);
    }

    @Test
    @DisplayName("a wire starts from the nub's OUTER half — the pixels nodeAt disowns")
    void wireStartsFromNubOuterHalf() {
        Fixture f = new Fixture();
        // 3px OUTSIDE the node rectangle, still visibly inside the dot —
        // the exact press the old nodeAt gate turned into a canvas pan
        f.press(f.a.x + FlowCanvas.NODE_W + 3, f.a.y + FlowCanvas.NODE_H / 2);
        f.drag(f.a.x + FlowCanvas.NODE_W + 60, f.a.y + 150);
        f.release(f.b.x + 50, f.b.y + 10);
        assertThat(f.graph.getWires())
                .as("half the painted dot lies outside the node rect; the"
                        + " nub hit-test must own the whole dot")
                .hasSize(1);
    }

    @Test
    @DisplayName("dropping on the target's input nub (outer half) still connects")
    void dropOnInputNubConnects() {
        Fixture f = new Fixture();
        f.press(f.a.x + FlowCanvas.NODE_W, f.a.y + FlowCanvas.NODE_H / 2);
        // 3px LEFT of the target rectangle — the input dot's outer half
        f.release(f.b.x - 3, f.b.y + FlowCanvas.NODE_H / 2);
        assertThat(f.graph.getWires())
                .as("the input nub straddles the left edge exactly as the"
                        + " output nub straddles the right")
                .hasSize(1);
    }

    @Test
    @DisplayName("an illegal wire refuses OUT LOUD, not silently")
    void illegalWireNarrates() {
        Fixture f = new Fixture();
        // droplet -> database is not in the rule table (a wire reads "serves")
        f.press(f.b.x + FlowCanvas.NODE_W, f.b.y + FlowCanvas.NODE_H / 2);
        f.release(f.a.x + 50, f.a.y + 10);
        assertThat(f.graph.getWires()).isEmpty();
        assertThat(f.refused)
                .as("the ghost vanishing silently is indistinguishable from a"
                        + " misdrop — the canvas must say the rule refused")
                .isTrue();
    }

    @Test
    @DisplayName("a managed database wires into a droplet — the most common DO pairing")
    void databaseServesDroplet() {
        Fixture f = new Fixture();
        assertThat(f.graph.canConnect(f.a, f.b))
                .as("PostgreSQL -> Droplet joined the rule table in v1.271.0;"
                        + " App Platform and Kubernetes were representable"
                        + " while the plain-droplet pairing was not")
                .isTrue();
        assertThat(NodeKind.DB_MYSQL.wiresInto()).contains(NodeKind.DROPLET);
        assertThat(NodeKind.DB_POSTGRES.wiresInto()).contains(NodeKind.GPU_DROPLET);
    }
}
