package org.nmox.studio.infra.ops.search;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.ops.OpsHub;
import org.nmox.studio.infra.ops.OpsHubFixture;
import org.nmox.studio.infra.ops.model.OpsKind;
import org.nmox.studio.infra.ops.model.OpsNode;

import static org.assertj.core.api.Assertions.assertThat;

/** Quick Search over the Hub's own copy for readers: what is found, by what, and what the row says. */
class OpsSearchProviderTest {

    private static List<String> find(String text, List<OpsHub.Findable> entries) {
        List<String> ids = new ArrayList<>();
        OpsSearchProvider.evaluate(text, entries, (id, display) -> ids.add(id));
        return ids;
    }

    private OpsHubFixture fleet() {
        OpsHubFixture f = new OpsHubFixture();
        f.host("edge-eu", 5);
        f.host("worker", 6);
        f.answer(OpsHubFixture.container("shop-web-1", "nmox/shop:300", "0.0.0.0:80->3000/tcp", "nmox.ops.app=shop"));
        f.drain();
        return f;
    }

    @Test
    @DisplayName("a server is found by its name, its address or its status; a service by its name or its server's")
    void finds() {
        OpsHubFixture f = fleet();
        List<OpsHub.Findable> all = f.hub.findable();
        assertThat(all).as("two servers, a service on each").hasSize(4);
        assertThat(find("edge", all)).containsExactly("manual:root@203.0.113.5:22", "manual:root@203.0.113.5:22/shop-web-1");
        assertThat(find("203.0.113.6", all)).contains("manual:root@203.0.113.6:22");
        assertThat(find("shop web", all)).as("terms, not a substring: the dash and the order do not matter")
                .containsExactly("manual:root@203.0.113.5:22/shop-web-1", "manual:root@203.0.113.6:22/shop-web-1");
        assertThat(find("ready", all)).as("by status word").hasSize(2);
        assertThat(find("nothing-like-this", all)).isEmpty();
        assertThat(find("  ", all)).as("a blank query asks nothing").isEmpty();
        assertThat(find(null, all)).isEmpty();
    }

    @Test
    @DisplayName("the copy the search reads follows the topology: a forgotten server is no longer found")
    void followsTheTopology() {
        OpsHubFixture f = fleet();
        OpsNode edge = f.hub.topology().nodes(OpsKind.HOST).get(0);
        f.hub.forgetManualHost(edge);
        assertThat(find("edge", f.hub.findable())).isEmpty();
        assertThat(f.hub.findable()).as("its service went with it").hasSize(2);
    }

    @Test
    @DisplayName("the row says what the thing is: a server with its status and address, a service with its server")
    void rows() {
        OpsHubFixture f = fleet();
        List<String> rows = new ArrayList<>();
        OpsSearchProvider.evaluate("edge", f.hub.findable(), (id, display) -> rows.add(display));
        assertThat(rows.get(0)).contains("edge-eu").contains("host, ready, 203.0.113.5");
        assertThat(rows.get(1)).contains("shop-web-1").contains("service on edge-eu");
    }

    @Test
    @DisplayName("a host with no address yet gets a whole sentence, not one that ends in a comma")
    void rowWithoutAnAddress() {
        var booting = new org.nmox.studio.infra.ops.OpsHub.Findable("h1", org.nmox.studio.infra.ops.model.OpsKind.HOST,
                "fresh-1", null, null, org.nmox.studio.infra.ops.model.HostStatus.PROVISIONING, false);
        List<String> rows = new ArrayList<>();
        OpsSearchProvider.evaluate("fresh", List.of(booting), (id, display) -> rows.add(display));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).stripTrailing()).isEqualTo(rows.get(0)).endsWith("host, provisioning");
    }

    @Test
    @DisplayName("the sink can stop the search, as the platform's does when its list is full")
    void sinkStops() {
        OpsHubFixture f = fleet();
        int[] offered = {0};
        OpsSearchProvider.evaluate("203", f.hub.findable(), (id, display) -> {
            offered[0]++;
            return false;
        });
        assertThat(offered[0]).isEqualTo(1);
    }
}
