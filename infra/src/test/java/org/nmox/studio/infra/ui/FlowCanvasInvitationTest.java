package org.nmox.studio.infra.ui;

import java.awt.Point;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.NodeKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An empty canvas says where resources come from (v2.122.0).
 *
 * <p>The walk of a fresh install photographed the Infra Designer as a large
 * dark rectangle with a DEPLOY button over it and nothing else — while its
 * sibling canvas one tab away, the rack, has silkscreened the same
 * invitation since v1.0. Same idea, two doors, one silent. The invitation
 * retires the moment a node lands, which is the half that stops it becoming
 * furniture.
 */
class FlowCanvasInvitationTest {

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

    private static FlowCanvas canvas(InfraGraph graph) {
        FlowCanvas c = new FlowCanvas(graph, NOOP);
        c.setSize(800, 500);
        return c;
    }

    @Test
    @DisplayName("an empty canvas invites; one with a node does not")
    void theInvitationRetires() {
        InfraGraph empty = new InfraGraph();
        assertThat(canvas(empty).isInviting())
                .as("nothing on the canvas and nothing telling a newcomer where to start")
                .isTrue();

        InfraGraph one = new InfraGraph();
        one.addNode(NodeKind.DROPLET, 20, 20);
        assertThat(canvas(one).isInviting())
                .as("the invitation is for an empty canvas, not decoration on a full one")
                .isFalse();
    }

    @Test
    @DisplayName("painting the invitation is safe at any size, including none")
    void paintsWithoutBlowingUp() {
        // the v1.33.3 class: a canvas can be asked to paint before it has a
        // size, and a centring calculation is where a divide or a negative
        // width would surface
        for (int[] size : new int[][] {{800, 500}, {1, 1}, {0, 0}}) {
            FlowCanvas c = canvas(new InfraGraph());
            c.setSize(size[0], size[1]);
            BufferedImage img = new BufferedImage(Math.max(1, size[0]),
                    Math.max(1, size[1]), BufferedImage.TYPE_INT_ARGB);
            c.paint(img.getGraphics());
        }
    }
}
