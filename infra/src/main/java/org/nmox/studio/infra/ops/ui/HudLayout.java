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

    // ---- fleet: this machine on the left, hosts fanned out to the right, services as satellites ----

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
        // the fan's height leaves room above and below for a host's satellites and labels
        double reachY = Math.min(reach * 0.78, Math.max(60, h / 2.0 - 110));
        double hostR = n <= 4 ? 40 : n <= 8 ? 34 : 28;
        if (n <= 9) {
            // a fan: evenly spread angles, narrower for few hosts so two do not sit at the extremes
            double spread = Math.toRadians(Math.min(120, 26 * Math.max(1, n - 1) + 10));
            for (int i = 0; i < n; i++) {
                double a = n == 1 ? 0 : -spread / 2 + spread * i / (n - 1);
                double x = wx + Math.cos(a) * reach;
                double y = wy + Math.sin(a) * reachY;
                out.put(hosts.get(i).id, new Place(x, y, hostR));
            }
        } else {
            // past a fan's capacity: a grid filling the right-hand three quarters
            int cols = (int) Math.ceil(Math.sqrt(n * 1.6));
            int rows = (int) Math.ceil(n / (double) cols);
            double left = wx + 120;
            double cellW = Math.max(120, (w - left - 40) / Math.max(1, cols));
            double cellH = Math.max(100, (h - 80.0) / Math.max(1, rows));
            for (int i = 0; i < n; i++) {
                int c = i % cols;
                int r = i / cols;
                out.put(hosts.get(i).id, new Place(left + cellW * (c + 0.5), 40 + cellH * (r + 0.5), hostR));
            }
        }
        // services orbit their host, small, starting at the host's outer side
        for (OpsNode host : hosts) {
            Place hp = out.get(host.id);
            List<OpsNode> services = t.servicesOf(host.id);
            int m = services.size();
            double orbit = hostR + 34;
            double angleFrom = Math.atan2(hp.y - wy, hp.x - wx);
            for (int i = 0; i < m; i++) {
                double a = angleFrom + (m == 1 ? 0 : -Math.PI / 3 + (2 * Math.PI / 3) * i / (m - 1));
                out.put(services.get(i).id, new Place(hp.x + Math.cos(a) * orbit, hp.y + Math.sin(a) * orbit, 11));
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
