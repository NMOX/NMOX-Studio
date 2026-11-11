package org.nmox.studio.infra.ops.remote;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A container's name is the host's word: only docker's own alphabet reaches a remote line. */
class ContainerDoorsTest {

    @Test
    @DisplayName("the two doors are exactly these lines for a name docker accepts")
    void lines() {
        assertThat(ContainerDoors.logs("shop-web-1")).isEqualTo("docker logs -f --tail 200 shop-web-1");
        assertThat(ContainerDoors.shell("nmox_shop.2"))
                .isEqualTo("docker exec -it nmox_shop.2 sh -c 'command -v bash >/dev/null 2>&1 && exec bash || exec sh'");
    }

    @Test
    @DisplayName("a name a shell would read is refused before it is spliced, in both doors")
    void hostileNames() {
        for (String bad : List.of("web; rm -rf /", "$(id)", "`id`", "a b", "-rf", "", "web\nid", "web|nc", "web&&x",
                "web'x", "web\"x", "web>out", "../x", "x".repeat(129), "wéb")) {
            assertThat(ContainerDoors.nameIsSafe(bad)).as(bad).isFalse();
            assertThatThrownBy(() -> ContainerDoors.logs(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ContainerDoors.shell(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(ContainerDoors.nameIsSafe(null)).isFalse();
        for (String good : List.of("a", "web", "shop-web-1", "nmox_shop.2", "9lives", "x".repeat(128))) {
            assertThat(ContainerDoors.nameIsSafe(good)).as(good).isTrue();
        }
    }

    @Test
    @DisplayName("the terminal's ssh carries the line as ONE argument after --, under a pty")
    void oneArgument() {
        SshTarget t = new SshTarget("deploy", "203.0.113.9", 2222, null);
        List<String> argv = t.interactiveSsh("/k/known", ContainerDoors.logs("web"));
        assertThat(argv.get(0)).isEqualTo("ssh");
        assertThat(argv.get(1)).as("a log follow and a shell both need a pty").isEqualTo("-t");
        assertThat(argv).endsWith("deploy@203.0.113.9", "--", "docker logs -f --tail 200 web");
        assertThat(argv).contains("UserKnownHostsFile=/k/known", "2222");
        assertThat(argv).as("no BatchMode: a passphrase prompt is fine in a terminal").doesNotContain("BatchMode=yes");
    }
}
