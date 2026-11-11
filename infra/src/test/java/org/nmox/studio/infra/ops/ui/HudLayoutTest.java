package org.nmox.studio.infra.ops.ui;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.ops.model.OpsKind;
import org.nmox.studio.infra.ops.model.OpsNode;
import org.nmox.studio.infra.ops.model.Topology;

import static org.assertj.core.api.Assertions.assertThat;

/** The layout is deterministic, keeps every node on the canvas, and hides what a view does not show. */
class HudLayoutTest {

    static Topology fleet(int hosts, int servicesEach) {
        Topology t = new Topology();
        for (int h = 0; h < hosts; h++) {
            OpsNode host = new OpsNode("h" + h, OpsKind.HOST, "host" + h);
            host.address = "203.0.113." + (h + 1);
            t.put(host);
            for (int s = 0; s < servicesEach; s++) {
                OpsNode svc = new OpsNode("h" + h + "/s" + s, OpsKind.SERVICE, "svc" + s);
                svc.hostId = host.id;
                t.put(svc);
            }
        }
        return t;
    }

    @Test
    @DisplayName("the fleet view keeps every host inside the canvas and services beside their host")
    void fleetFits() {
        for (int n : new int[]{1, 3, 8, 14}) {
            Topology t = fleet(n, 2);
            Map<String, HudLayout.Place> p = HudLayout.layout(t, HudView.FLEET, 1200, 700);
            assertThat(p).hasSize(1 + n * 3);
            for (Map.Entry<String, HudLayout.Place> e : p.entrySet()) {
                assertThat(e.getValue().x()).as(e.getKey()).isBetween(0.0, 1200.0);
                assertThat(e.getValue().y()).as(e.getKey()).isBetween(0.0, 700.0);
                assertThat(e.getValue().r()).isPositive();
            }
            HudLayout.Place host = p.get("h0");
            HudLayout.Place svc = p.get("h0/s0");
            assertThat(Math.hypot(host.x() - svc.x(), host.y() - svc.y())).isLessThan(host.r() + 60);
        }
        // and the same input lands in the same place twice
        assertThat(HudLayout.layout(fleet(3, 1), HudView.FLEET, 1000, 600))
                .isEqualTo(HudLayout.layout(fleet(3, 1), HudView.FLEET, 1000, 600));
    }

    /** What is written under a host: three centred lines, from its rim down. */
    private static java.awt.geom.Rectangle2D caption(HudLayout.Place host) {
        return HudLayout.caption(host);
    }

    @Test
    @DisplayName("no host's ring reaches into another host's caption, on a tall canvas or a short one")
    void ringsKeepOffTheCaptions() {
        for (int[] size : new int[][]{{1200, 700}, {900, 360}, {632, 358}, {640, 330}, {640, 420}}) {
            for (int n : new int[]{2, 3, 4, 5, 8, 9}) {
                Map<String, HudLayout.Place> p = HudLayout.layout(fleet(n, 0), HudView.FLEET, size[0], size[1]);
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        HudLayout.Place other = p.get("h" + j);
                        double glow = other.r() + 6;
                        assertThat(i != j && caption(p.get("h" + i)).intersects(other.x() - glow, other.y() - glow, 2 * glow, 2 * glow))
                                .as(size[0] + "x" + size[1] + ", " + n + " hosts: the ring of h" + j + " over the caption of h" + i)
                                .isFalse();
                    }
                }
            }
        }
    }

    private static boolean covers(HudLayout.Place sat, java.awt.geom.Rectangle2D caption) {
        return caption.intersects(sat.x() - sat.r(), sat.y() - sat.r(), 2 * sat.r(), 2 * sat.r());
    }

    @Test
    @DisplayName("no service sits on its own host's caption, however many there are, and a first column sits on nobody's")
    void satellitesKeepOffTheCaptions() {
        for (int[] size : new int[][]{{1200, 700}, {900, 520}, {640, 420}}) {
            for (int hosts : new int[]{1, 2, 3, 5, 12}) {
                for (int services : new int[]{1, 2, 3, 6, 9}) {
                    Map<String, HudLayout.Place> p = HudLayout.layout(fleet(hosts, services), HudView.FLEET, size[0], size[1]);
                    String where = size[0] + "x" + size[1] + ", " + hosts + " hosts with " + services + " services: ";
                    for (int h = 0; h < hosts; h++) {
                        HudLayout.Place host = p.get("h" + h);
                        for (int v = 0; v < services; v++) {
                            // the name, address and readings under a host stay readable (the walk: a real
                            // host's second container sat on its address)
                            assertThat(covers(p.get("h" + h + "/s" + v), caption(host)))
                                    .as(where + "h" + h + "/s" + v + " over its own host's caption").isFalse();
                            assertThat(p.get("h" + h + "/s" + v).x()).as(where + "beside the host, on its outer side")
                                    .isGreaterThan(host.x() + host.r());
                        }
                        // a neighbour's caption: held for the first column, which is every service of a host
                        // with three or fewer. A second column of a crowded fan can still reach under the
                        // host above it (5 hosts with 6 services each does) — ledger 141 says so
                        for (int g = 0; g < hosts && hosts <= 9; g++) {
                            for (int v = 0; v < Math.min(services, HudLayout.SATELLITE_ROWS); v++) {
                                assertThat(covers(p.get("h" + g + "/s" + v), caption(host)))
                                        .as(where + "h" + g + "/s" + v + " over the caption of h" + h).isFalse();
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("services stand three to a column, each column a name's width from the last, centred on the host")
    void satelliteColumns() {
        Map<String, HudLayout.Place> p = HudLayout.layout(fleet(1, 5), HudView.FLEET, 1200, 700);
        HudLayout.Place host = p.get("h0");
        assertThat(p.get("h0/s0").x()).isEqualTo(p.get("h0/s1").x()).isEqualTo(p.get("h0/s2").x());
        assertThat(p.get("h0/s3").x() - p.get("h0/s0").x()).as("the second column").isEqualTo(HudLayout.SATELLITE_COLUMN);
        assertThat(p.get("h0/s1").y()).as("a full column is centred on the host").isEqualTo(host.y());
        assertThat((p.get("h0/s3").y() + p.get("h0/s4").y()) / 2).as("and so is a short one").isEqualTo(host.y());
        assertThat(p.get("h0/s1").y() - p.get("h0/s0").y()).isEqualTo(HudLayout.SATELLITE_PITCH);
        HudLayout.Place alone = HudLayout.layout(fleet(1, 1), HudView.FLEET, 1200, 700).get("h0/s0");
        assertThat(alone.y()).as("one service is level with its host").isEqualTo(HudLayout.layout(fleet(1, 1), HudView.FLEET, 1200, 700).get("h0").y());
    }

    @Test
    @DisplayName("two hosts on a short canvas never overlap (the walk found them on top of each other)")
    void shortCanvas() {
        for (int n : new int[]{2, 3, 5}) {
            Map<String, HudLayout.Place> p = HudLayout.layout(fleet(n, 0), HudView.FLEET, 640, 330);
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    HudLayout.Place a = p.get("h" + i);
                    HudLayout.Place b = p.get("h" + j);
                    assertThat(Math.hypot(a.x() - b.x(), a.y() - b.y())).as(n + " hosts, " + i + " vs " + j)
                            .isGreaterThanOrEqualTo(a.r() + b.r() + 40);
                }
            }
        }
    }

    @Test
    @DisplayName("a host view centres the host, rings its services, and sends the other hosts off the right edge with no size")
    void hostView() {
        Topology t = fleet(3, 2);
        Map<String, HudLayout.Place> p = HudLayout.layout(t, HudView.host("h1"), 1000, 600);
        assertThat(p.get("h1").x()).isEqualTo(500);
        assertThat(p.get("h1").r()).isGreaterThan(50);
        assertThat(p.get("h1/s0").r()).isPositive();
        assertThat(p.get("h0").r()).isZero();
        assertThat(p.get("h0").x()).isGreaterThan(1000);
        assertThat(p.get("h2/s1").r()).isZero();
        assertThat(HudLayout.visible(p)).containsExactly(Topology.WORKSTATION_ID, "h1", "h1/s0", "h1/s1");
        assertThat(HudView.host("h1").up()).isEqualTo(HudView.FLEET);
        assertThat(HudView.SERVICES.up()).isEqualTo(HudView.SERVICES);
    }

    @Test
    @DisplayName("the services view stacks each host's services in its own column")
    void servicesView() {
        Topology t = fleet(2, 3);
        Map<String, HudLayout.Place> p = HudLayout.layout(t, HudView.SERVICES, 900, 600);
        assertThat(p.get("h0/s0").x()).isEqualTo(p.get("h0/s2").x()).isEqualTo(p.get("h0").x());
        assertThat(p.get("h1/s0").x()).isGreaterThan(p.get("h0/s0").x());
        assertThat(p.get("h0/s0").y()).isLessThan(p.get("h0/s1").y());
        assertThat(p.get(Topology.WORKSTATION_ID).r()).isZero();
    }

    @Test
    @DisplayName("a spring arrives without overshooting and reports settled; a snap arrives at once")
    void spring() {
        Motion.Spring s = new Motion.Spring(0);
        s.setTarget(100);
        double max = 0;
        int steps = 0;
        while (!s.settled() && steps++ < 1000) {
            s.step(1 / 60.0, Motion.LAYOUT_K);
            max = Math.max(max, s.value());
        }
        assertThat(s.settled()).isTrue();
        assertThat(s.value()).isEqualTo(100);
        assertThat(max).as("critically damped: no overshoot").isLessThanOrEqualTo(100.5);
        assertThat(steps).isBetween(10, 300);
        Motion.Spring snap = new Motion.Spring(0);
        snap.setTarget(5);
        snap.snap();
        assertThat(snap.settled()).isTrue();
        assertThat(snap.value()).isEqualTo(5);
    }
}
