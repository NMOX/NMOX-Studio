package org.nmox.studio.infra.ops.remote;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The probe's parser against what real hosts print — and what they do not. */
class HostProbeTest {

    static final String CONTAINER = "{\"Command\":\"\\\"docker-entrypoint.s…\\\"\",\"CreatedAt\":\"2026-11-06 10:00:00 +0000 UTC\","
            + "\"ID\":\"a1b2c3\",\"Image\":\"nmox/shop:1762400000000\",\"Labels\":\"nmox.ops.app=shop,com.docker.compose.project=shop,"
            + "com.docker.compose.service=web,com.docker.compose.depends_on=db:service_started:true,cache:service_healthy:false\","
            + "\"LocalVolumes\":\"0\",\"Mounts\":\"\",\"Names\":\"shop-web-1\",\"Networks\":\"shop_default,bridge\","
            + "\"Ports\":\"0.0.0.0:80->3000/tcp, [::]:80->3000/tcp, 9229/tcp\",\"RunningFor\":\"2 hours ago\",\"Size\":\"0B\","
            + "\"State\":\"running\",\"Status\":\"Up 2 hours\"}";

    @Test
    @DisplayName("a Linux host's full probe parses into facts and containers")
    void linux() {
        HostFacts f = HostProbe.parse(List.of(
                "Welcome to Ubuntu 24.04 LTS (a MOTD line the parser must ignore)",
                "nmox-probe 1", "os Linux", "load 0.42", "cpus 2", "mem 2013480 611204", "disk 24618452 3412340",
                "uptime 8123", "mosh yes", "docker yes", "cloudinit done",
                "container " + CONTAINER, "nmox-probe end"));
        assertThat(f).isNotNull();
        assertThat(f.os()).isEqualTo("Linux");
        assertThat(f.load1()).isEqualTo(0.42);
        assertThat(f.cpus()).isEqualTo(2);
        assertThat(f.memTotalKb()).isEqualTo(2013480);
        assertThat(f.memUsedKb()).isEqualTo(611204);
        assertThat(f.diskUsedKb()).isEqualTo(3412340);
        assertThat(f.uptimeSeconds()).isEqualTo(8123);
        assertThat(f.moshServer()).isTrue();
        assertThat(f.docker()).isTrue();
        assertThat(f.cloudInit()).isEqualTo("done");
        assertThat(f.containers()).hasSize(1);
        HostFacts.ContainerFact c = f.containers().get(0);
        assertThat(c.name()).isEqualTo("shop-web-1");
        assertThat(c.serviceName()).isEqualTo("web");
        assertThat(c.composeProject()).isEqualTo("shop");
        assertThat(c.labels()).containsEntry("nmox.ops.app", "shop");
        assertThat(c.publishedPorts()).containsExactly(80);
        assertThat(c.declaredDependencies()).containsExactly("db", "cache");
        assertThat(c.userNetworks()).containsExactly("shop_default");
    }

    @Test
    @DisplayName("a Mac host reports no memory, no uptime and no cloud-init — absent, not zero")
    void mac() {
        HostFacts f = HostProbe.parse(List.of("nmox-probe 1", "os Darwin", "load 1.87", "cpus 10",
                "uptime", "mosh yes", "docker no", "nmox-probe end"));
        assertThat(f).isNotNull();
        assertThat(f.memTotalKb()).isEqualTo(-1);
        assertThat(f.diskTotalKb()).isEqualTo(-1);
        assertThat(f.uptimeSeconds()).isEqualTo(-1);
        assertThat(f.cloudInit()).isNull();
        assertThat(f.containers()).isEmpty();
        assertThat(f.load1()).isEqualTo(1.87);
    }

    @Test
    @DisplayName("a probe without its closing marker is not a measurement")
    void halfPrinted() {
        assertThat(HostProbe.parse(List.of("nmox-probe 1", "os Linux", "load 0.1"))).isNull();
        assertThat(HostProbe.parse(List.of("os Linux", "nmox-probe end"))).isNull();
        assertThat(HostProbe.parse(List.of())).isNull();
    }

    @Test
    @DisplayName("a container line that is not JSON is dropped, the rest of the probe stands")
    void badContainerLine() {
        HostFacts f = HostProbe.parse(List.of("nmox-probe 1", "container not json", "container " + CONTAINER, "nmox-probe end"));
        assertThat(f).isNotNull();
        assertThat(f.containers()).hasSize(1);
    }

    @Test
    @DisplayName("the script keys every line and stays POSIX")
    void scriptShape() {
        assertThat(HostProbe.SCRIPT).startsWith("echo nmox-probe 1").endsWith("echo nmox-probe end")
                .contains("docker ps --format").doesNotContain("bash").doesNotContain("[[");
    }
}
