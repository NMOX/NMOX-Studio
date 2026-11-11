package org.nmox.studio.infra.ops.remote;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** What docker prints in its Ports column, read as the one mapping a rollback must publish again. */
class ContainerFactTest {

    private static int[] mapping(String ports) {
        return new HostFacts.ContainerFact("", "", "", "", ports, Map.of(), List.of()).firstMapping();
    }

    @Test
    @DisplayName("the first single mapping is host port then container port")
    void firstMapping() {
        assertThat(mapping("0.0.0.0:8080->3000/tcp")).containsExactly(8080, 3000);
        assertThat(mapping("0.0.0.0:80->3000/tcp, :::80->3000/tcp")).containsExactly(80, 3000);
        assertThat(mapping("[::]:443->8443/tcp")).containsExactly(443, 8443);
        assertThat(mapping("5432/tcp, 0.0.0.0:9000->9000/udp")).as("an unpublished port is skipped").containsExactly(9000, 9000);
    }

    @Test
    @DisplayName("nothing published, a range, or nonsense is no mapping — never a guess")
    void noMapping() {
        assertThat(mapping(null)).isNull();
        assertThat(mapping("")).isNull();
        assertThat(mapping("5432/tcp")).isNull();
        assertThat(mapping("0.0.0.0:8000-8010->8000-8010/tcp")).isNull();
        assertThat(mapping("0.0.0.0:0->80/tcp")).isNull();
        assertThat(mapping("0.0.0.0:99999->80/tcp")).isNull();
        assertThat(mapping("x->y")).isNull();
    }
}
