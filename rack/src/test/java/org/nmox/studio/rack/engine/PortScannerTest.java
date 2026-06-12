package org.nmox.studio.rack.engine;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** lsof -F pcn parsing on canned output - no scanning needed. */
class PortScannerTest {

    @Test
    @DisplayName("Ports map to their owning process, deduped, sorted")
    void parsesField() {
        String out = """
                p501
                cnode
                n*:5173
                n127.0.0.1:5173
                p777
                cpostgres
                n127.0.0.1:5432
                p888
                ccom.docke
                n*:8080
                """;
        List<PortScanner.PortInfo> ports = PortScanner.parse(out);
        assertThat(ports).hasSize(3);
        assertThat(ports.get(0).port()).isEqualTo(5173);
        assertThat(ports.get(0).command()).isEqualTo("node");
        assertThat(ports.get(0).pid()).isEqualTo(501);
        assertThat(ports.get(1).port()).isEqualTo(5432);
        assertThat(ports.get(2).command()).isEqualTo("com.docke");
    }

    @Test
    @DisplayName("IPv6 and odd name formats do not break the sweep")
    void handlesIpv6AndNoise() {
        String out = """
                p42
                cjava
                n[::1]:8000
                nlocalhost:9000
                nnoporthere
                """;
        List<PortScanner.PortInfo> ports = PortScanner.parse(out);
        assertThat(ports).extracting(PortScanner.PortInfo::port)
                .containsExactly(8000, 9000);
    }

    @Test
    @DisplayName("Empty output is an empty field, not an error")
    void emptyOutput() {
        assertThat(PortScanner.parse("")).isEmpty();
    }
}
