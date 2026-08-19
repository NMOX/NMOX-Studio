package org.nmox.studio.ui.browser.fx;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import javax.swing.JPanel;

import org.nmox.studio.ui.browser.devtools.Keyframes;

/**
 * The timeline strip (v2.12.0): the Motion pane's editing surface — a
 * ruler from 0% to 100%, one row per animated property, a diamond per
 * keyframe, and a scrubber. All the EDITING rules live in the pure
 * inner {@link Model} so they are plain unit tests; this panel only
 * paints the model and translates gestures:
 *
 * <ul>
 *   <li>drag a diamond horizontally to move its stop (snapped to whole
 *       percents, clamped so stops never pass each other),</li>
 *   <li>double-click an empty spot on a track to add a stop there
 *       (seeded from its nearest neighbor's value),</li>
 *   <li>select a diamond and press Delete to remove it,</li>
 *   <li>drag in the ruler to scrub — the host receives the percent and
 *       holds the live page at that moment.</li>
 * </ul>
 */
public final class TimelineStrip extends JPanel {

    /**
     * The strip's pure model: tracks (property → stops), each stop a
     * percent → value. Percent moves clamp between neighbors; adds
     * refuse duplicate percents; the export is a Keyframes frame list
     * (stops regrouped by percent, properties in track order).
     */
    public static final class Model {

        /** property → (percent → value), both insertion-ordered. */
        private final Map<String, java.util.TreeMap<Integer, String>> tracks =
                new LinkedHashMap<>();

        public List<String> properties() {
            return List.copyOf(tracks.keySet());
        }

        public java.util.SortedMap<Integer, String> stops(String property) {
            java.util.TreeMap<Integer, String> t = tracks.get(property);
            return t == null ? new java.util.TreeMap<>() : new java.util.TreeMap<>(t);
        }

        public void addTrack(String property) {
            tracks.computeIfAbsent(property, p -> new java.util.TreeMap<>());
        }

        /** @return true when a track of that name existed — the caller
         *  must be able to tell a removal from a no-op on a free-typed
         *  name, so the gesture is never silently inert */
        public boolean removeTrack(String property) {
            return tracks.remove(property) != null;
        }

        /** Adds/overwrites the stop; percent clamped to 0..100. */
        public void setStop(String property, int percent, String value) {
            addTrack(property);
            tracks.get(property).put(clamp(percent), value);
        }

        public void removeStop(String property, int percent) {
            java.util.TreeMap<Integer, String> t = tracks.get(property);
            if (t != null) {
                t.remove(percent);
            }
        }

        /**
         * Moves a stop to {@code toPercent}, clamped strictly BETWEEN
         * its neighbors (stops never pass or merge — a drag cannot
         * silently delete a keyframe). Returns the percent it landed
         * on, or -1 when the stop does not exist.
         */
        public int moveStop(String property, int fromPercent, int toPercent) {
            java.util.TreeMap<Integer, String> t = tracks.get(property);
            if (t == null || !t.containsKey(fromPercent)) {
                return -1;
            }
            Integer lo = t.lowerKey(fromPercent);
            Integer hi = t.higherKey(fromPercent);
            int min = lo == null ? 0 : lo + 1;
            int max = hi == null ? 100 : hi - 1;
            int landed = Math.max(min, Math.min(max, clamp(toPercent)));
            String value = t.remove(fromPercent);
            t.put(landed, value);
            return landed;
        }

        /** The model as Keyframes frames: stops grouped by percent. */
        public List<Keyframes.Frame> frames() {
            java.util.TreeMap<Integer, Map<String, String>> byPercent = new java.util.TreeMap<>();
            for (Map.Entry<String, java.util.TreeMap<Integer, String>> track : tracks.entrySet()) {
                for (Map.Entry<Integer, String> stop : track.getValue().entrySet()) {
                    byPercent.computeIfAbsent(stop.getKey(), p -> new LinkedHashMap<>())
                            .put(track.getKey(), stop.getValue());
                }
            }
            List<Keyframes.Frame> out = new ArrayList<>();
            byPercent.forEach((p, props) -> out.add(new Keyframes.Frame(p, props)));
            return out;
        }

        /** Replaces the whole model with {@code frames}' tracks. */
        public void load(List<Keyframes.Frame> frames) {
            tracks.clear();
            for (Keyframes.Frame f : frames) {
                for (Map.Entry<String, String> e : f.props().entrySet()) {
                    setStop(e.getKey(), f.percent(), e.getValue());
                }
            }
        }

        private static int clamp(int p) {
            return Math.max(0, Math.min(100, p));
        }
    }

    private static final int RULER_H = 18;
    private static final int ROW_H = 24;
    private static final int LABEL_W = 120;
    private static final Color PHOSPHOR = new Color(0x39, 0xD3, 0x53);
    private static final Color DIM = new Color(0x60, 0x60, 0x60);

    private final Model model = new Model();
    private final IntConsumer onScrub;
    private final Runnable onChange;
    private final java.util.function.BiConsumer<String, Integer> onEditStop;
    private int scrubPercent;
    private String selectedProperty;
    private int selectedPercent = -1;
    private String dragProperty;
    private int dragPercent = -1;

    public TimelineStrip(IntConsumer onScrub, Runnable onChange,
            java.util.function.BiConsumer<String, Integer> onEditStop) {
        this.onScrub = onScrub;
        this.onChange = onChange;
        this.onEditStop = onEditStop;
        setPreferredSize(new Dimension(520, RULER_H + ROW_H * 3 + 6));
        setBackground(new Color(0x16, 0x16, 0x16));
        MouseAdapter mouse = new Mouse();
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        setFocusable(true);
        registerKeyboardAction(e -> deleteSelected(),
                javax.swing.KeyStroke.getKeyStroke("DELETE"),
                javax.swing.JComponent.WHEN_FOCUSED);
        registerKeyboardAction(e -> deleteSelected(),
                javax.swing.KeyStroke.getKeyStroke("BACK_SPACE"),
                javax.swing.JComponent.WHEN_FOCUSED);
        getAccessibleContext().setAccessibleName("Animation timeline");
        getAccessibleContext().setAccessibleDescription(
                "Tracks of keyframes from 0 to 100 percent; drag diamonds to move,"
                + " double-click to add, Delete removes the selected keyframe");
    }

    public Model model() {
        return model;
    }

    public String selectedProperty() {
        return selectedProperty;
    }

    public int selectedPercent() {
        return selectedPercent;
    }

    public void refresh() {
        int rows = Math.max(1, model.properties().size());
        setPreferredSize(new Dimension(520, RULER_H + ROW_H * rows + 6));
        revalidate();
        repaint();
    }

    private void deleteSelected() {
        if (selectedProperty != null && selectedPercent >= 0) {
            model.removeStop(selectedProperty, selectedPercent);
            selectedPercent = -1;
            onChange.run();
            refresh();
        }
    }

    private int percentAt(int x) {
        int w = Math.max(1, getWidth() - LABEL_W - 12);
        return Math.max(0, Math.min(100, Math.round((x - LABEL_W - 6) * 100f / w)));
    }

    private int xOf(int percent) {
        int w = getWidth() - LABEL_W - 12;
        return LABEL_W + 6 + Math.round(percent / 100f * w);
    }

    private final class Mouse extends MouseAdapter {

        @Override
        public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            if (e.getY() <= RULER_H) {
                scrubPercent = percentAt(e.getX());
                onScrub.accept(scrubPercent);
                repaint();
                return;
            }
            int row = (e.getY() - RULER_H) / ROW_H;
            List<String> props = model.properties();
            if (row < 0 || row >= props.size()) {
                return;
            }
            String property = props.get(row);
            int percent = percentAt(e.getX());
            Integer near = nearestStop(property, percent);
            if (near != null && Math.abs(xOf(near) - e.getX()) <= 6) {
                selectedProperty = property;
                selectedPercent = near;
                dragProperty = property;
                dragPercent = near;
                if (e.getClickCount() == 2) {
                    onEditStop.accept(property, near);
                    dragPercent = -1;
                }
            } else if (e.getClickCount() == 2) {
                model.setStop(property, percent, seedValue(property, percent));
                selectedProperty = property;
                selectedPercent = percent;
                onChange.run();
            }
            repaint();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (e.getY() <= RULER_H || (dragProperty == null && e.getY() <= RULER_H + 2)) {
                scrubPercent = percentAt(e.getX());
                onScrub.accept(scrubPercent);
                repaint();
                return;
            }
            if (dragProperty != null && dragPercent >= 0) {
                int landed = model.moveStop(dragProperty, dragPercent, percentAt(e.getX()));
                if (landed >= 0) {
                    dragPercent = landed;
                    selectedPercent = landed;
                    repaint();
                }
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (dragProperty != null) {
                dragProperty = null;
                dragPercent = -1;
                onChange.run();
            }
        }
    }

    private Integer nearestStop(String property, int percent) {
        java.util.SortedMap<Integer, String> stops = model.stops(property);
        Integer best = null;
        for (Integer p : stops.keySet()) {
            if (best == null || Math.abs(p - percent) < Math.abs(best - percent)) {
                best = p;
            }
        }
        return best;
    }

    /** A new stop copies its nearest neighbor's value, else a hint. */
    private String seedValue(String property, int percent) {
        Integer near = nearestStop(property, percent);
        if (near != null) {
            String v = model.stops(property).get(near);
            if (v != null) {
                return v;
            }
        }
        return "transform".equals(property) ? "translateX(0)" : "1";
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // ruler
        g.setColor(DIM);
        for (int p = 0; p <= 100; p += 10) {
            int x = xOf(p);
            g.drawLine(x, RULER_H - 6, x, RULER_H - 1);
            if (p % 50 == 0) {
                g.drawString(p + "%", x - (p == 0 ? 0 : 12), RULER_H - 8);
            }
        }
        // tracks
        List<String> props = model.properties();
        for (int row = 0; row < props.size(); row++) {
            String property = props.get(row);
            int y = RULER_H + row * ROW_H + ROW_H / 2;
            g.setColor(Color.LIGHT_GRAY);
            g.drawString(property, 6, y + 4);
            g.setColor(new Color(0x2a, 0x2a, 0x2a));
            g.drawLine(xOf(0), y, xOf(100), y);
            for (Map.Entry<Integer, String> stop : model.stops(property).entrySet()) {
                int x = xOf(stop.getKey());
                boolean sel = property.equals(selectedProperty)
                        && stop.getKey() == selectedPercent;
                g.setColor(sel ? Color.WHITE : PHOSPHOR);
                int[] xs = {x, x + 5, x, x - 5};
                int[] ys = {y - 5, y, y + 5, y};
                g.fillPolygon(xs, ys, 4);
            }
        }
        if (props.isEmpty()) {
            g.setColor(DIM);
            g.drawString("Add a property track to begin", LABEL_W + 12, RULER_H + 16);
        }
        // scrubber
        g.setColor(new Color(0xE0, 0x60, 0x60));
        g.setStroke(new BasicStroke(1f));
        int sx = xOf(scrubPercent);
        g.drawLine(sx, 2, sx, getHeight() - 2);
    }

}
