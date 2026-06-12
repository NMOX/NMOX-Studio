package org.nmox.studio.rack.docker;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The parsers, fed real-world docker CLI output - no daemon needed. */
class DockerClientParseTest {

    @Test
    @DisplayName("Containers parse: state, ports, and clickable host ports")
    void parsesContainers() {
        String out = """
                {"ID":"abc123","Names":"web","Image":"nginx:1.27","State":"running","Status":"Up 2 hours","Ports":"0.0.0.0:8080->80/tcp, :::8080->80/tcp"}
                {"ID":"def456","Names":"db","Image":"postgres:16","State":"exited","Status":"Exited (0) 3 days ago","Ports":""}
                garbage line the daemon coughed up
                """;
        List<DockerClient.ContainerInfo> list = DockerClient.parseContainers(out);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).running()).isTrue();
        assertThat(list.get(0).hostPorts()).containsExactly(8080);
        assertThat(list.get(1).running()).isFalse();
        assertThat(list.get(1).hostPorts()).isEmpty();
    }

    @Test
    @DisplayName("Host-port extraction handles IPv4, IPv6, and multi-port strings")
    void extractsHostPorts() {
        assertThat(DockerClient.hostPorts("0.0.0.0:3000->3000/tcp, 0.0.0.0:9229->9229/tcp"))
                .containsExactly(3000, 9229);
        assertThat(DockerClient.hostPorts(":::5432->5432/tcp")).containsExactly(5432);
        assertThat(DockerClient.hostPorts("80/tcp")).isEmpty();
        assertThat(DockerClient.hostPorts(null)).isEmpty();
    }

    @Test
    @DisplayName("Images parse: dangling detection via <none>")
    void parsesImages() {
        String out = """
                {"Repository":"nginx","Tag":"1.27","ID":"sha1","Size":"188MB","CreatedSince":"3 weeks ago"}
                {"Repository":"<none>","Tag":"<none>","ID":"sha2","Size":"1.1GB","CreatedSince":"5 days ago"}
                """;
        List<DockerClient.ImageInfo> list = DockerClient.parseImages(out);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).dangling()).isFalse();
        assertThat(list.get(0).ref()).isEqualTo("nginx:1.27");
        assertThat(list.get(1).dangling()).isTrue();
        assertThat(list.get(1).ref()).isEqualTo("sha2");
    }

    @Test
    @DisplayName("system df parse: the reclaim ledger")
    void parsesDf() {
        String out = """
                {"Type":"Images","TotalCount":"12","Active":"4","Size":"6.5GB","Reclaimable":"4.2GB (64%)"}
                {"Type":"Build Cache","TotalCount":"88","Active":"0","Size":"2.1GB","Reclaimable":"2.1GB"}
                """;
        List<DockerClient.DfRow> rows = DockerClient.parseDf(out);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).type()).isEqualTo("Images");
        assertThat(rows.get(0).reclaimable()).startsWith("4.2GB");
    }

    @Test
    @DisplayName("Stats snapshot parse: live cpu/mem per container")
    void parsesStats() {
        String out = """
                {"Container":"abc","Name":"web","CPUPerc":"0.34%","MemUsage":"24MiB / 7.6GiB"}
                """;
        List<DockerClient.StatRow> rows = DockerClient.parseStats(out);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).cpu()).isEqualTo("0.34%");
    }

    @Test
    @DisplayName("Volumes and networks parse")
    void parsesVolumesAndNetworks() {
        assertThat(DockerClient.parseVolumes("{\"Name\":\"pgdata\",\"Driver\":\"local\"}\n"))
                .singleElement().satisfies(v -> assertThat(v.name()).isEqualTo("pgdata"));
        assertThat(DockerClient.parseNetworks("{\"ID\":\"n1\",\"Name\":\"bridge\",\"Driver\":\"bridge\",\"Scope\":\"local\"}\n"))
                .singleElement().satisfies(n -> assertThat(n.driver()).isEqualTo("bridge"));
    }
}
