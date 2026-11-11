package org.nmox.studio.infra.ops.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.nmox.studio.infra.ops.model.OpsKind;
import org.nmox.studio.infra.ops.model.OpsNode;
import org.nmox.studio.infra.ops.model.Topology;

/**
 * Where every node should be for a view and a canvas size. Pure and
 * deterministic: the same topology in the same view at the same size
 * lands in the same places, so a user's eye learns the picture and a test
 * can assert it. The canvas tweens the nodes from where they are to
 * these targets; this class never animates.
 *
 * <p>Nodes a view does not show get a target too — the place they would
 * leave towards — with a zero radius, so they shrink away along a path
 * instead of vanishing.
 */
public final class HudLayout {

    /** A node's target: centre and drawn radius (0 = not shown in this view). */
    public record Place(double x, double y, double r) {
    }

    private HudLayout() {
    }

    public static Map<String, Place> layout(Topology topology, HudView view, int width, int height) {
        return switch (view.level()) {
            case FLEET -> fleet(topology, width, height);
            case HOST -> host(topology, view.hostId(), width, height);
            case SERVICES -> services(topology, width, height);
        };
    }

    /** What the canvas writes under a host — its name, its address or status, its readings — as a box from its rim down. */
    static java.awt.geom.Rectangle2D caption(Place host) {
        return new java.awt.geom.Rectangle2D.Double(host.x - 62, host.y + host.r + 2, 124, 46);
    }

    /**
     * True when no host's ring reaches into another host's caption. Two
     * rings that clear each other can still do that — a caption is as tall
     * as a ring's radius — and on a short canvas the fan put the lower
     * host's ring over the upper one's readings (walked: a second host
     * added while the Terminal took the lower half of the window).
     */
    static boolean captionsClear(java.util.Collection<Place> hosts) {
        for (Place a : hosts) {
            java.awt.geom.Rectangle2D box = caption(a);
            for (Place b : hosts) {
                double glow = b.r + 6;
                if (a != b && box.intersects(b.x - glow, b.y - glow, 2 * glow, 2 * glow)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** How many of a host's services stand in one column of the fleet view. */
    static final int SATELLITE_ROWS = 3;
    /** From one service to the next below it. */
    static final double SATELLITE_PITCH = 24;
    /** From one column of services to the next: a glyph and room for its name beside it. */
    static final double SATELLITE_COLUMN = 104;

    // ---- fleet: this machine on the left, hosts fanned out to the right, services beside their host ----

    static Map<String, Place> fleet(Topology t, int w, int h) {
        Map<String, Place> out = new LinkedHashMap<>();
        double wx = Math.max(60, w * 0.14);
        double wy = h / 2.0;
        out.put(Topology.WORKSTATION_ID, new Place(wx, wy, 30));
        List<OpsNode> hosts = t.nodes(OpsKind.HOST);
        int n = hosts.size();
        if (n == 0) {
            return out;
        }
        double reach = Math.min(w * 0.46, Math.max(160, h * 0.62));
        // the fan's height leaves room above and below for a host's ring and what is written under it
        double reachY = Math.min(reach * 0.78, Math.max(60, h / 2.0 - 110));
        double hostR = n <= 4 ? 40 : n <= 8 ? 34 : 28;
        double apart = 2 * hostR + 70; // two hosts need their rings AND their labels clear of each other
        boolean fanned = false;
        if (n <= 9) {
            // a fan: angles spread so neighbours sit `apart` along the arc, within what the canvas allows
            double spread = n == 1 ? 0 : Math.min(Math.toRadians(140), (n - 1) * apart / Math.max(1, reachY));
            Map<String, Place> fan = new LinkedHashMap<>();
            double minGap = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                double a = n == 1 ? 0 : -spread / 2 + spread * i / (n - 1);
                double x = wx + Math.cos(a) * reach;
                double y = wy + Math.sin(a) * reachY;
                for (Place other : fan.values()) {
                    minGap = Math.min(minGap, Math.hypot(other.x - x, other.y - y));
                }
                fan.put(hosts.get(i).id, new Place(x, y, hostR));
            }
            if (n == 1 || (minGap >= apart * 0.8 && captionsClear(fan.values()))) {
                out.putAll(fan);
                fanned = true;
            }
        }
        if (!fanned) {
            // past a fan's capacity, or a canvas too short for one: a grid filling the right-hand three quarters
            int cols = Math.max(1, (int) Math.ceil(Math.sqrt(n * 1.6)));
            int rows = (int) Math.ceil(n / (double) cols);
            double left = wx + 120;
            double cellW = Math.max(apart, (w - left - 40) / Math.max(1, cols));
            double cellH = Math.max(apart, (h - 80.0) / Math.max(1, rows));
            for (int i = 0; i < n; i++) {
                int c = i % cols;
                int r = i / cols;
                out.put(hosts.get(i).id, new Place(left + cellW * (c + 0.5), 40 + cellH * (r + 0.5), hostR));
            }
        }
        // services stand beside their host, small, in short columns on its outer side — and nowhere else.
        // A host's name, address and readings are written under it and its upper neighbour's are written
        // above it, and a satellite in either place sat on top of them (walked on a real host with two
        // containers: the lower one covered the address). Three to a column keeps a column shorter than
        // the host; each column is wide enough for its names, which the canvas writes beside the glyphs.
        for (OpsNode host : hosts) {
            Place hp = out.get(host.id);
            List<OpsNode> services = t.servicesOf(host.id);
            int m = services.size();
            double first = Math.max(hostR + 40, 74);
            for (int i = 0; i < m; i++) {
                int col = i / SATELLITE_ROWS;
                int inColumn = Math.min(SATELLITE_ROWS, m - col * SATELLITE_ROWS);
                double dy = (i % SATELLITE_ROWS - (inColumn - 1) / 2.0) * SATELLITE_PITCH;
                out.put(services.get(i).id, new Place(hp.x + first + col * SATELLITE_COLUMN, hp.y + dy, 11));
            }
        }
        return out;
    }

    // ---- host: one server in the middle, its services on a ring, the rest gone ----

    static Map<String, Place> host(Topology t, String hostId, int w, int h) {
        Map<String, Place> out = new LinkedHashMap<>();
        double cx = w * 0.5;
        double cy = h * 0.5;
        out.put(Topology.WORKSTATION_ID, new Place(Math.max(50, w * 0.1), cy, 22));
        OpsNode host = t.node(hostId);
        if (host == null) {
            return fleet(t, w, h);
        }
        out.put(hostId, new Place(cx, cy, 58));
        List<OpsNode> services = t.servicesOf(hostId);
        int m = services.size();
        double ring = Math.max(110, Math.min(w, h) * 0.3);
        for (int i = 0; i < m; i++) {
            double a = -Math.PI / 2 + 2 * Math.PI * i / Math.max(1, m);
            out.put(services.get(i).id, new Place(cx + Math.cos(a) * ring, cy + Math.sin(a) * ring, 26));
        }
        // other hosts leave towards the right edge at their fleet height, shrunk to nothing
        Map<String, Place> fleet = fleet(t, w, h);
        for (OpsNode other : t.nodes(OpsKind.HOST)) {
            if (!other.id.equals(hostId)) {
                Place f = fleet.get(other.id);
                out.put(other.id, new Place(w + 40, f == null ? cy : f.y, 0));
                for (OpsNode s : t.servicesOf(other.id)) {
                    out.put(s.id, new Place(w + 40, f == null ? cy : f.y, 0));
                }
            }
        }
        return out;
    }

    // ---- services: a column per host, services stacked, hosts as faint headers ----

    static Map<String, Place> services(Topology t, int w, int h) {
        Map<String, Place> out = new LinkedHashMap<>();
        out.put(Topology.WORKSTATION_ID, new Place(-40, h / 2.0, 0));
        List<OpsNode> hosts = t.nodes(OpsKind.HOST);
        int n = hosts.size();
        if (n == 0) {
            return out;
        }
        double colW = w / (double) (n + 1);
        for (int c = 0; c < n; c++) {
            OpsNode host = hosts.get(c);
            double x = colW * (c + 1);
            out.put(host.id, new Place(x, 54, 16));
            List<OpsNode> services = t.servicesOf(host.id);
            int m = services.size();
            double top = 130;
            double bottom = h - 50;
            double step = m <= 1 ? 0 : Math.min(110, (bottom - top) / (m - 1));
            double start = m <= 1 ? (top + bottom) / 2 : (top + bottom) / 2 - step * (m - 1) / 2;
            for (int i = 0; i < m; i++) {
                out.put(services.get(i).id, new Place(x, start + step * i, 26));
            }
        }
        return out;
    }

    /**
     * The box around everything a view shows: each visible node's own
     * circle, nothing added for labels (those are drawn at a fixed size
     * and the camera leaves screen room for them). Null when the view
     * shows nothing.
     */
    public static java.awt.geom.Rectangle2D bounds(Map<String, Place> places) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Place p : places.values()) {
            if (p.r() <= 0) {
                continue;
            }
            minX = Math.min(minX, p.x() - p.r());
            minY = Math.min(minY, p.y() - p.r());
            maxX = Math.max(maxX, p.x() + p.r());
            maxY = Math.max(maxY, p.y() + p.r());
        }
        return minX > maxX ? null : new java.awt.geom.Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
    }

    /** The ids a view shows (radius above zero), in layout order — what keyboard navigation cycles. */
    public static List<String> visible(Map<String, Place> places) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Place> e : places.entrySet()) {
            if (e.getValue().r() > 0) {
                out.add(e.getKey());
            }
        }
        return out;
    }
}
