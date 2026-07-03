package org.nmox.studio.infra.ui;

import java.awt.Point;
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
