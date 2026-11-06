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
