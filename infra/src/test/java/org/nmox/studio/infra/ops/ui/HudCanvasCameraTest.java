package org.nmox.studio.infra.ops.ui;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Map;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.ops.OpsHubFixture;
import org.nmox.studio.infra.ops.model.OpsNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The camera wired into the HUD: the real listeners driven with real
 * events on the event thread, motion reduced so every spring has
 * arrived when an assertion looks. What is held here is that every
 * reader of a node's position goes through the camera — the paint is a
 * rendering of the same numbers the hit test and the accessible bounds
 * answer with, so those two are what a test can ask.
 */
class HudCanvasCameraTest {

    private OpsHubFixture fixture;
    private HudCanvas canvas;

    @BeforeEach
    void still() {
        ReducedMotion.systemSays(true);
    }

    @AfterEach
    void moving() {
        ReducedMotion.systemSays(null);
    }

    private void build(int hosts, int width, int height) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            fixture = new OpsHubFixture();
            for (int i = 0; i < hosts; i++) {
                fixture.host("box" + i, i + 1);
            }
            canvas = new HudCanvas(fixture.hub);
            canvas.setSize(width, height);
            canvas.relayout();
        });
    }

    private static void edt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private Map<String, HudLayout.Place> places() {
        return HudLayout.layout(fixture.hub.topology(), canvas.view(), canvas.getWidth(), canvas.getHeight());
    }

    private String firstHost() {
        return fixture.hub.topology().nodes(org.nmox.studio.infra.ops.model.OpsKind.HOST).get(0).id;
    }

    private void wheel(int x, int y, double notches) {
        MouseWheelEvent e = new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, 0, 0, x, y, x, y, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, (int) Math.signum(notches), notches);
        for (MouseWheelListener l : canvas.getMouseWheelListeners()) {
            l.mouseWheelMoved(e);
        }
    }

    private void mouse(int id, int x, int y, int clicks) {
        MouseEvent e = new MouseEvent(canvas, id, 0, id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.BUTTON1_DOWN_MASK : 0,
                x, y, clicks, false, MouseEvent.BUTTON1);
        if (id == MouseEvent.MOUSE_DRAGGED) {
            for (MouseMotionListener l : canvas.getMouseMotionListeners()) {
                l.mouseDragged(e);
            }
            return;
        }
        for (MouseListener l : canvas.getMouseListeners()) {
            if (id == MouseEvent.MOUSE_PRESSED) {
                l.mousePressed(e);
            } else {
                l.mouseReleased(e);
            }
        }
    }

    @Test
    @DisplayName("the hit test follows the camera: a zoomed node is where it is painted, not where the layout put it")
    void hitTestFollowsTheCamera() throws Exception {
        build(2, 900, 600);
        edt(() -> {
            String id = firstHost();
            HudLayout.Place p = places().get(id);
            assertThat(canvas.nodeAt((int) p.x(), (int) p.y())).isEqualTo(id);
            wheel(40, 40, -8); // eight notches in, about a corner far from the host
            HudCamera cam = canvas.camera();
            assertThat(cam.zoom()).isGreaterThan(1.9);
            int sx = (int) cam.sx(p.x());
            int sy = (int) cam.sy(p.y());
            assertThat(Math.hypot(sx - p.x(), sy - p.y())).as("the node moved on screen").isGreaterThan(p.r() * cam.zoom() + 20);
            assertThat(canvas.nodeAt(sx, sy)).as("found where it is painted").isEqualTo(id);
            assertThat(canvas.nodeAt((int) p.x(), (int) p.y())).as("and not where it used to be").isNotEqualTo(id);
        });
    }

    @Test
    @DisplayName("a screen reader is pointed at the screen: accessible bounds are the painted bounds")
    void accessibleBoundsFollowTheCamera() throws Exception {
        build(2, 900, 600);
        edt(() -> {
            String id = firstHost();
            HudLayout.Place p = places().get(id);
            canvas.select(id);
            wheel(20, 20, -5);
            HudCamera cam = canvas.camera();
            AccessibleContext hud = canvas.getAccessibleContext();
            Accessible row = hud.getAccessibleSelection().getAccessibleSelection(0);
            Rectangle box = row.getAccessibleContext().getAccessibleComponent().getBounds();
            assertThat(box.getCenterX()).isCloseTo(cam.sx(p.x()), within(2.0));
            assertThat(box.getCenterY()).isCloseTo(cam.sy(p.y()), within(2.0));
            assertThat(box.getWidth()).as("and it grew with the zoom").isGreaterThan(2 * p.r() * 1.4);
        });
    }

    @Test
    @DisplayName("the wheel zooms about the cursor: what is under the pointer stays under it")
    void wheelZoomsAboutTheCursor() throws Exception {
        build(3, 900, 600);
        edt(() -> {
            HudCamera cam = canvas.camera();
            double wx = cam.wx(610);
            double wy = cam.wy(240);
            wheel(610, 240, -3);
            assertThat(cam.zoom()).isCloseTo(Math.pow(HudCamera.WHEEL_STEP, 3), within(1e-6));
            // the clamp may nudge a camera that would lose the content; here the content is in view, so it must not
            assertThat(cam.sx(wx)).isCloseTo(610, within(0.5));
            assertThat(cam.sy(wy)).isCloseTo(240, within(0.5));
            assertThat(cam.manual()).isTrue();
            wheel(610, 240, 3);
            assertThat(cam.zoom()).isCloseTo(1.0, within(1e-6));
        });
    }

    @Test
    @DisplayName("a drag on empty space pans and keeps the selection; a click there lets it go")
    void dragPansAndKeepsTheSelection() throws Exception {
        build(2, 900, 600);
        edt(() -> {
            String id = firstHost();
            canvas.select(id);
            assertThat(canvas.nodeAt(5, 5)).as("the corner is empty").isNull();
            mouse(MouseEvent.MOUSE_PRESSED, 5, 5, 1);
            mouse(MouseEvent.MOUSE_DRAGGED, 65, 35, 0);
            mouse(MouseEvent.MOUSE_RELEASED, 65, 35, 1);
            assertThat(canvas.camera().panX()).isCloseTo(60, within(1e-6));
            assertThat(canvas.camera().panY()).isCloseTo(30, within(1e-6));
            assertThat(canvas.selected()).as("panning the map is not letting go of the node").isEqualTo(id);
            mouse(MouseEvent.MOUSE_PRESSED, 5, 5, 1);
            mouse(MouseEvent.MOUSE_RELEASED, 5, 5, 1);
            assertThat(canvas.selected()).as("a plain click on nothing deselects").isNull();
        });
    }

    @Test
    @DisplayName("the keys: plus and minus zoom, zero fits")
    void keys() throws Exception {
        build(2, 900, 600);
        edt(() -> {
            HudCamera cam = canvas.camera();
            canvas.getActionMap().get("zoom-in").actionPerformed(null);
            assertThat(cam.zoom()).isCloseTo(HudCamera.KEY_STEP, within(1e-6));
            canvas.getActionMap().get("zoom-out").actionPerformed(null);
            canvas.getActionMap().get("zoom-out").actionPerformed(null);
            assertThat(cam.zoom()).isCloseTo(1 / HudCamera.KEY_STEP, within(1e-6));
            canvas.getActionMap().get("pan-left").actionPerformed(null);
            assertThat(cam.manual()).isTrue();
            canvas.getActionMap().get("fit").actionPerformed(null);
            assertThat(cam.identity()).isTrue();
            assertThat(cam.manual()).isFalse();
            // every camera key is really bound to a stroke on the focused canvas
            for (char c : new char[]{'+', '=', '-', '0'}) {
                assertThat(canvas.getInputMap().get(javax.swing.KeyStroke.getKeyStroke(c))).as("key " + c).isNotNull();
            }
        });
    }

    @Test
    @DisplayName("a fleet too large for its canvas is framed whole instead of running off the edge")
    void crowdedFleetIsFramed() throws Exception {
        build(40, 700, 420);
        edt(() -> {
            HudCamera cam = canvas.camera();
            assertThat(cam.zoom()).as("forty hosts do not fit a 700 by 420 canvas at natural size").isLessThan(1.0);
            assertThat(cam.manual()).isFalse();
            for (Map.Entry<String, HudLayout.Place> e : places().entrySet()) {
                HudLayout.Place p = e.getValue();
                if (p.r() <= 0) {
                    continue;
                }
                assertThat(cam.sx(p.x())).as(e.getKey()).isBetween(0.0, 700.0);
                assertThat(cam.sy(p.y())).as(e.getKey()).isBetween(0.0, 420.0);
            }
        });
    }

    @Test
    @DisplayName("a moved camera stays put when the fleet changes, and another view is framed afresh")
    void manualStaysUntilTheViewChanges() throws Exception {
        build(2, 900, 600);
        edt(() -> {
            wheel(450, 300, -4);
            double zoom = canvas.camera().zoom();
            OpsNode third = fixture.host("late", 9);
            canvas.relayout();
            assertThat(canvas.camera().zoom()).as("a new host does not take the camera back").isEqualTo(zoom);
            assertThat(canvas.camera().manual()).isTrue();
            canvas.setView(HudView.host(third.id));
            assertThat(canvas.camera().manual()).as("a view change frames the new picture").isFalse();
            assertThat(canvas.camera().identity()).isTrue();
        });
    }

    @Test
    @DisplayName("the keyboard's selection is brought into view when the camera had left it behind")
    void selectionIsRevealed() throws Exception {
        build(3, 900, 600);
        edt(() -> {
            wheel(0, 0, -9);
            canvas.getActionMap().get("RIGHT").actionPerformed(null);
            String id = canvas.selected();
            assertThat(id).isNotNull();
            HudLayout.Place p = places().get(id);
            HudCamera cam = canvas.camera();
            double r = p.r() * cam.zoom();
            assertThat(cam.sx(p.x()) - r).isGreaterThanOrEqualTo(-0.5);
            assertThat(cam.sx(p.x()) + r).isLessThanOrEqualTo(900.5);
            assertThat(cam.sy(p.y()) - r).isGreaterThanOrEqualTo(-0.5);
            assertThat(cam.sy(p.y()) + r).isLessThanOrEqualTo(600.5);
        });
    }
}
