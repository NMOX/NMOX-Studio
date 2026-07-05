package org.nmox.studio.infra.ui;

import java.awt.EventQueue;
import java.awt.Point;
import java.awt.event.ComponentEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fit() contract: contain the design, never magnify it, and never
 * run the math against an unsized canvas (the bug: dividing by width 0
 * slammed zoom to the floor on every open).
 */
class FlowCanvasFitTest {

    private static final FlowCanvas.Callbacks NOOP = new FlowCanvas.Callbacks() {
        @Override
        public void nodeDoubleClicked(InfraNode node) {
        }

        @Override
        public void nodeContextMenu(InfraNode node, Point screenPoint) {
        }

        @Override
        public void selectionChanged(InfraNode node) {
        }
    };

    @Test
    @DisplayName("A small design fits at natural size — contain, never magnify")
    void smallDesignStaysAtNaturalSize() {
        InfraGraph graph = new InfraGraph();
        graph.addNode(NodeKind.DROPLET, 100, 100);
        graph.addNode(NodeKind.VPC, 300, 100);
        FlowCanvas canvas = new FlowCanvas(graph, NOOP);
        canvas.setSize(1200, 800);

        canvas.fit();

        assertThat(canvas.getZoom()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("A sprawling design zooms out to contain, bounded by the floor")
    void sprawlingDesignContained() {
        InfraGraph graph = new InfraGraph();
        for (int i = 0; i < 6; i++) {
            graph.addNode(NodeKind.DROPLET, i * 700, i * 400);
        }
        FlowCanvas canvas = new FlowCanvas(graph, NOOP);
        canvas.setSize(1000, 700);

        canvas.fit();

        assertThat(canvas.getZoom()).isLessThan(1.0).isGreaterThanOrEqualTo(0.3);
    }

    @Test
    @DisplayName("Fit on an unsized canvas defers instead of computing garbage")
    void unsizedCanvasDefers() {
        InfraGraph graph = new InfraGraph();
        graph.addNode(NodeKind.DROPLET, 0, 0);
        FlowCanvas canvas = new FlowCanvas(graph, NOOP);
        // no setSize: width/height are 0, as at window-open time

        canvas.fit();

        assertThat(canvas.getZoom())
                .as("zoom untouched until a real layout pass").isEqualTo(1.0);
    }

    // ---- startup-hang storm regression (the busy self-repost fix) ----
    //
    // The bug: fit()/selectNode() on a 0×0 canvas did
    //   SwingUtilities.invokeLater(this::fit)
    // gated only on the zero-size check, so on an open-at-startup tab that is
    // never the selected tab the canvas is never sized and this re-posted
    // itself onto the EDT at full speed FOREVER — pinning the EDT and
    // starving the main window's first paint. The fix waits on the resize
    // EVENT: at 0×0 the body does NOT run and AT MOST ONE one-shot resize
    // listener is armed (no unbounded EDT-queue growth); on the first real
    // size the body runs a BOUNDED number of times (exactly once) and the
    // listener disarms. These tests FAIL on the old busy-loop: it never armed
    // a listener (the listener-count assertions fail) and, run on the EDT,
    // grew the event queue without bound.
    //
    // Everything below runs on the EDT (invokeAndWait) so event delivery is
    // single-threaded exactly as in the live IDE — Swing components must only
    // be touched on the EDT, and driving them off-thread introduces a
    // resize-delivery race that does not exist in production.

    private static void onEdt(Runnable body) {
        try {
            EventQueue.invokeAndWait(body);
        } catch (InterruptedException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> T onEdtGet(Callable<T> body) {
        Object[] out = new Object[1];
        onEdt(() -> {
            try {
                out[0] = body.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        @SuppressWarnings("unchecked")
        T result = (T) out[0];
        return result;
    }

    /** Deliver a COMPONENT_RESIZED the way the window system does, on the EDT. */
    private static void fireResized(FlowCanvas canvas) {
        onEdt(() -> canvas.dispatchEvent(
                new ComponentEvent(canvas, ComponentEvent.COMPONENT_RESIZED)));
    }

    @Test
    @DisplayName("fit() on a 0×0 canvas defers via a resize event, not a busy self-repost")
    void fitDefersWithoutBusyLoop() {
        FlowCanvas canvas = onEdtGet(() -> {
            InfraGraph graph = new InfraGraph();
            graph.addNode(NodeKind.DROPLET, 100, 100);
            return new FlowCanvas(graph, NOOP);
        });
        int listenersBefore = onEdtGet(() -> canvas.getComponentListeners().length);

        // 0×0 as at window-open time on an unselected startup tab.
        onEdt(canvas::fit);

        // Body did NOT run (nothing to divide by), and it armed EXACTLY ONE
        // one-shot resize listener rather than posting a retry.
        assertThat(canvas.fitBodyRuns).as("fit body must not run while unsized").isZero();
        assertThat(onEdtGet(() -> canvas.getComponentListeners().length))
                .as("armed exactly one deferral listener").isEqualTo(listenersBefore + 1);

        // A real size arrives: the listener fires the body ONCE and disarms.
        onEdt(() -> canvas.setSize(800, 600));
        fireResized(canvas);

        assertThat(canvas.fitBodyRuns).as("fit body runs exactly once on first real size").isEqualTo(1);
        assertThat(onEdtGet(() -> canvas.getComponentListeners().length))
                .as("deferral listener removed itself after firing").isEqualTo(listenersBefore);
    }

    @Test
    @DisplayName("Repeated fit() calls while unsized arm AT MOST ONE listener (no queue growth)")
    void repeatedFitArmsAtMostOneListener() {
        FlowCanvas canvas = onEdtGet(() -> {
            InfraGraph graph = new InfraGraph();
            graph.addNode(NodeKind.DROPLET, 100, 100);
            return new FlowCanvas(graph, NOOP);
        });
        int listenersBefore = onEdtGet(() -> canvas.getComponentListeners().length);

        onEdt(() -> {
            for (int i = 0; i < 500; i++) {
                canvas.fit();
            }
        });

        // The old busy-loop would have posted 500 EDT retries; the fix arms
        // one listener and drops the other 499.
        assertThat(onEdtGet(() -> canvas.getComponentListeners().length))
                .as("500 unsized fit() calls arm at most one deferral listener")
                .isEqualTo(listenersBefore + 1);
        assertThat(canvas.fitBodyRuns).as("no body run while unsized").isZero();

        // And it fits a BOUNDED number of times (once) when size finally
        // arrives — not once per queued retry.
        onEdt(() -> canvas.setSize(800, 600));
        fireResized(canvas);
        assertThat(canvas.fitBodyRuns)
                .as("bounded: fit body runs exactly once, not per queued retry")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("selectNode() on a 0×0 canvas defers once, then runs once on resize")
    void selectNodeDefersWithoutBusyLoop() {
        InfraNode[] nodeHolder = new InfraNode[1];
        FlowCanvas canvas = onEdtGet(() -> {
            InfraGraph graph = new InfraGraph();
            nodeHolder[0] = graph.addNode(NodeKind.DROPLET, 100, 100);
            return new FlowCanvas(graph, NOOP);
        });
        InfraNode node = nodeHolder[0];
        int listenersBefore = onEdtGet(() -> canvas.getComponentListeners().length);

        onEdt(() -> {
            for (int i = 0; i < 500; i++) {
                canvas.selectNode(node);
            }
        });

        assertThat(canvas.selectBodyRuns).as("select body must not run while unsized").isZero();
        assertThat(onEdtGet(() -> canvas.getComponentListeners().length))
                .as("500 unsized selectNode() calls arm at most one listener")
                .isEqualTo(listenersBefore + 1);

        onEdt(() -> canvas.setSize(800, 600));
        fireResized(canvas);

        assertThat(canvas.selectBodyRuns).as("select body runs exactly once on first real size").isEqualTo(1);
        assertThat(onEdtGet(() -> canvas.getComponentListeners().length))
                .as("listener disarmed after firing").isEqualTo(listenersBefore);
        assertThat(canvas.getSelectedNode()).as("the node ends up selected").isEqualTo(node);
    }

    @Test
    @DisplayName("A resize event that is still 0×0 does NOT fire the body (guarded on real size)")
    void zeroSizeResizeDoesNotFireBody() {
        FlowCanvas canvas = onEdtGet(() -> {
            InfraGraph graph = new InfraGraph();
            graph.addNode(NodeKind.DROPLET, 100, 100);
            return new FlowCanvas(graph, NOOP);
        });
        int listenersBefore = onEdtGet(() -> canvas.getComponentListeners().length);

        onEdt(canvas::fit);         // arms the one-shot
        fireResized(canvas);        // spurious resize while still 0×0

        assertThat(canvas.fitBodyRuns).as("still 0×0: body stays parked").isZero();
        assertThat(onEdtGet(() -> canvas.getComponentListeners().length))
                .as("deferral listener remains armed until a real size")
                .isEqualTo(listenersBefore + 1);

        onEdt(() -> canvas.setSize(640, 480));
        fireResized(canvas);
        assertThat(canvas.fitBodyRuns).as("fires once when the real size finally arrives").isEqualTo(1);
    }

    @Test
    @DisplayName("Zoom buttons step within bounds")
    void zoomButtonsClamp() {
        FlowCanvas canvas = new FlowCanvas(new InfraGraph(), NOOP);
        canvas.setSize(800, 600);
        for (int i = 0; i < 20; i++) {
            canvas.zoomIn();
        }
        assertThat(canvas.getZoom()).isEqualTo(2.5);
        for (int i = 0; i < 40; i++) {
            canvas.zoomOut();
        }
        assertThat(canvas.getZoom()).isEqualTo(0.3);
    }
}
