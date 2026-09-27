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
@org.openide.util.NbBundle.Messages({
    "TimelineStrip_accessibleName=Animation timeline",
    "TimelineStrip_accessibleDescription=Tracks of keyframes from 0 to 100 percent. Left and Right choose a keyframe,"
        + " Up and Down a track, Shift with an arrow moves the keyframe, Enter edits it, Insert or + adds one at the"
        + " playhead, [ and ] move the playhead, Delete removes the keyframe. With a mouse, drag a diamond to move"
        + " it and double-click to add or edit.",
    "# {0} the CSS property, {1} the percent, {2} the keyframe's value",
    "TimelineStrip_accessibleNameSelected=Animation timeline: {0} at {1}%, {2}",
    "TimelineStrip_emptyHint=Add a property track to begin"
})
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
        // the keyboard's timeline (3.4): every gesture the mouse had — select,
        // move, edit, add a stop, scrub — without one
        key("LEFT", () -> step(-1));
        key("RIGHT", () -> step(+1));
        key("UP", () -> changeTrack(-1));
        key("DOWN", () -> changeTrack(+1));
        key("shift LEFT", () -> nudge(-1));
        key("shift RIGHT", () -> nudge(+1));
        key("ENTER", this::editSelected);
        key("INSERT", this::addAtScrubber);
        key("typed +", this::addAtScrubber);
        key("OPEN_BRACKET", () -> scrubBy(-5));
        key("CLOSE_BRACKET", () -> scrubBy(+5));
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                repaint();
            }
        });
        getAccessibleContext().setAccessibleName(Bundle.TimelineStrip_accessibleName());
        getAccessibleContext().setAccessibleDescription(
                Bundle.TimelineStrip_accessibleDescription());
    }

    private void key(String stroke, Runnable run) {
        javax.swing.KeyStroke ks = javax.swing.KeyStroke.getKeyStroke(stroke);
        String name = "nmox-timeline-" + stroke;
        getInputMap(WHEN_FOCUSED).put(ks, name);
        getActionMap().put(name, new javax.swing.AbstractAction(name) {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                run.run();
            }
        });
    }

    /** Selects a stop, repaints, and says which one to a screen reader. */
    private void select(String property, int percent) {
        selectedProperty = property;
        selectedPercent = percent;
        String value = property == null || percent < 0 ? null : model.stops(property).get(percent);
        getAccessibleContext().setAccessibleName(value == null
                ? Bundle.TimelineStrip_accessibleName()
                : Bundle.TimelineStrip_accessibleNameSelected(property, String.valueOf(percent), value));
        repaint();
    }

    /** The track the keyboard works on: the selected one, else the first. */
    private String currentTrack() {
        List<String> props = model.properties();
        if (selectedProperty != null && props.contains(selectedProperty)) {
            return selectedProperty;
        }
        return props.isEmpty() ? null : props.get(0);
    }

    /** Left/Right: the previous or next stop on the track (the first stop when none is selected). */
    void step(int direction) {
        String track = currentTrack();
        if (track == null) {
            return;
        }
        java.util.TreeMap<Integer, String> stops = new java.util.TreeMap<>(model.stops(track));
        if (stops.isEmpty()) {
            select(track, -1);
            return;
        }
        Integer to;
        if (!track.equals(selectedProperty) || selectedPercent < 0) {
            to = direction > 0 ? stops.firstKey() : stops.lastKey();
        } else {
            to = direction > 0 ? stops.higherKey(selectedPercent) : stops.lowerKey(selectedPercent);
        }
        if (to != null) {
            select(track, to);
        }
    }

    /** Up/Down: the neighbouring track, on its stop nearest the current percent. */
    void changeTrack(int direction) {
        List<String> props = model.properties();
        if (props.isEmpty()) {
            return;
        }
        int at = props.indexOf(currentTrack());
        int next = Math.max(0, Math.min(props.size() - 1, at + direction));
        String track = props.get(next);
        Integer near = nearestStop(track, selectedPercent < 0 ? scrubPercent : selectedPercent);
        select(track, near == null ? -1 : near);
    }

    /** Shift+Left/Right: moves the selected stop one percent, clamped between its neighbours as a drag is. */
    void nudge(int delta) {
        if (selectedProperty == null || selectedPercent < 0) {
            return;
        }
        int landed = model.moveStop(selectedProperty, selectedPercent, selectedPercent + delta);
        if (landed >= 0) {
            select(selectedProperty, landed);
            onChange.run();
        }
    }

    /** Enter: edits the selected stop's value, as a double-click on its diamond does. */
    void editSelected() {
        if (selectedProperty != null && selectedPercent >= 0) {
            onEditStop.accept(selectedProperty, selectedPercent);
        }
    }

    /** Insert or +: a stop on the current track at the scrubber, as a double-click on the track adds one. */
    void addAtScrubber() {
        String track = currentTrack();
        if (track == null || model.stops(track).containsKey(scrubPercent)) {
            return;
        }
        model.setStop(track, scrubPercent, seedValue(track, scrubPercent));
        select(track, scrubPercent);
        onChange.run();
    }

    /** [ and ]: the scrubber five percent back or on, as a drag in the ruler moves it. */
    void scrubBy(int delta) {
        scrubPercent = Math.max(0, Math.min(100, scrubPercent + delta));
        onScrub.accept(scrubPercent);
        repaint();
    }

    int scrubPercentForTest() {
        return scrubPercent;
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
            select(selectedProperty, -1);
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
                select(property, near);
                dragProperty = property;
                dragPercent = near;
                if (e.getClickCount() == 2) {
                    onEditStop.accept(property, near);
                    dragPercent = -1;
                }
            } else if (e.getClickCount() == 2) {
                model.setStop(property, percent, seedValue(property, percent));
                select(property, percent);
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
                    select(dragProperty, landed);
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
            g.drawString(Bundle.TimelineStrip_emptyHint(), LABEL_W + 12, RULER_H + 16);
        }
        // scrubber
        g.setColor(new Color(0xE0, 0x60, 0x60));
        g.setStroke(new BasicStroke(1f));
        int sx = xOf(scrubPercent);
        g.drawLine(sx, 2, sx, getHeight() - 2);
        // where the keyboard is (3.4): a ring while the strip holds focus
        if (hasFocus()) {
            g.setColor(org.nmox.studio.core.util.KeyboardAccess.focusColor());
            g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        }
    }

}
