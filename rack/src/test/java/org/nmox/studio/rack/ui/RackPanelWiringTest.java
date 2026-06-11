package org.nmox.studio.rack.ui;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.SignalType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the rear-view cable interaction with synthetic mouse events:
 * press on an output jack, drag, release on an input jack, and assert
 * the patch cable exists - the drag-to-wire gesture end to end.
 */
class RackPanelWiringTest {

    private static class SourceDevice extends RackDevice {

        SourceDevice() {
            super("test-source", "SOURCE", "TEST", Color.RED, 1);
            addOutPort("trig", "TRIG", SignalType.TRIGGER);
            addOutPort("data", "DATA", SignalType.DATA);
        }
    }

    private static class SinkDevice extends RackDevice {

        SinkDevice() {
            super("test-sink", "SINK", "TEST", Color.BLUE, 1);
            addInPort("trig", "TRIG", SignalType.TRIGGER);
            addInPort("data", "DATA", SignalType.DATA);
        }
    }

    private Rack rack;
    private SourceDevice source;
    private SinkDevice sink;
    private RackPanel panel;

    @BeforeEach
    void setUp() {
        rack = new Rack();
        source = new SourceDevice();
        sink = new SinkDevice();
        rack.addDevice(source);
        rack.addDevice(sink);
        panel = new RackPanel(rack);
        panel.setSize(panel.getPreferredSize());
        panel.doLayout();
        panel.setFront(false);
    }

    /** Device-local coordinates of a port, for aiming mouse events. */
    private static Point jack(RackDevice d, String portId) {
        Port p = d.getPort(portId);
        return new Point(p.getX(), p.getY());
    }

    /** Point on the sink's jack expressed in the SOURCE device's coordinates,
     *  mimicking Swing's behavior of delivering the whole drag gesture to the
     *  component that received the press. */
    private Point sinkJackInSourceCoords(String portId) {
        Point p = jack(sink, portId);
        return new Point(p.x + sink.getX() - source.getX(), p.y + sink.getY() - source.getY());
    }

    private static void press(RackDevice d, Point p) {
        d.dispatchEvent(new MouseEvent(d, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                InputEvent.BUTTON1_DOWN_MASK, p.x, p.y, 1, false, MouseEvent.BUTTON1));
    }

    private static void drag(RackDevice d, Point p) {
        d.dispatchEvent(new MouseEvent(d, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
                InputEvent.BUTTON1_DOWN_MASK, p.x, p.y, 1, false, MouseEvent.BUTTON1));
    }

    private static void release(RackDevice d, Point p) {
        d.dispatchEvent(new MouseEvent(d, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                0, p.x, p.y, 1, false, MouseEvent.BUTTON1));
    }

    @Test
    @DisplayName("Should create a cable by dragging from an output jack to a matching input jack")
    void shouldWireOutputToInputByDragging() {
        press(source, jack(source, "trig"));
        drag(source, sinkJackInSourceCoords("trig"));
        release(source, sinkJackInSourceCoords("trig"));

        assertThat(rack.getCables()).hasSize(1);
        assertThat(rack.getCables().get(0).getFrom()).isEqualTo(source.getPort("trig"));
        assertThat(rack.getCables().get(0).getTo()).isEqualTo(sink.getPort("trig"));
    }

    @Test
    @DisplayName("Should snap to a nearby compatible jack on imprecise release")
    void shouldSnapToNearbyJack() {
        Point near = sinkJackInSourceCoords("trig");
        near.translate(18, 10); // sloppy release, within snap radius

        press(source, jack(source, "trig"));
        drag(source, near);
        release(source, near);

        assertThat(rack.getCables()).hasSize(1);
        assertThat(rack.getCables().get(0).getTo()).isEqualTo(sink.getPort("trig"));
    }

    @Test
    @DisplayName("Should refuse to wire incompatible signal types")
    void shouldRefuseTypeMismatchedDrag() {
        press(source, jack(source, "trig"));
        drag(source, sinkJackInSourceCoords("data"));
        release(source, sinkJackInSourceCoords("data"));

        assertThat(rack.getCables()).isEmpty();
    }

    @Test
    @DisplayName("Should do nothing when the drag starts off-jack")
    void shouldIgnoreDragFromEmptyPanel() {
        press(source, new Point(5, 5));
        release(source, sinkJackInSourceCoords("trig"));

        assertThat(rack.getCables()).isEmpty();
    }

    @Test
    @DisplayName("Should wire input to output dragged in reverse too")
    void shouldWireReverseDirection() {
        Point sourceJackInSinkCoords = jack(source, "trig");
        sourceJackInSinkCoords.translate(source.getX() - sink.getX(), source.getY() - sink.getY());

        press(sink, jack(sink, "trig"));
        drag(sink, sourceJackInSinkCoords);
        release(sink, sourceJackInSinkCoords);

        assertThat(rack.getCables()).hasSize(1);
        assertThat(rack.getCables().get(0).getFrom()).isEqualTo(source.getPort("trig"));
        assertThat(rack.getCables().get(0).getTo()).isEqualTo(sink.getPort("trig"));
    }
}
