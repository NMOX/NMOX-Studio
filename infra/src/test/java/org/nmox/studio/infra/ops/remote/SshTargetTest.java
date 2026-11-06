package org.nmox.studio.infra.ops.remote;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The one argv every remote operation rides, pinned. */
class SshTargetTest {

    @Test
    @DisplayName("parses user@host:port, defaulting root and 22")
    void parses() {
        assertThat(SshTarget.parse("deploy@203.0.113.9:2222", null))
                .isEqualTo(new SshTarget("deploy", "203.0.113.9", 2222, null));
        assertThat(SshTarget.parse("example.com", null)).isEqualTo(new SshTarget("root", "example.com", 22, null));
        assertThat(SshTarget.parse("root@[::1]:2222", null)).isEqualTo(new SshTarget("root", "[::1]", 2222, null));
    }

    @Test
    @DisplayName("an option-looking or shell-looking name is refused, not passed to ssh")
    void refusesHostile() {
        assertThatThrownBy(() -> SshTarget.parse("-oProxyCommand=evil@host", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SshTarget("root", "host;rm -rf /", 22, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SshTarget("root", "-evil", 22, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SshTarget.parse("host:abc", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SshTarget("root", "host", 70000, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a non-interactive command runs in BatchMode with a timeout, the Hub's known_hosts and accept-new")
    void batchArgv() {
        SshTarget t = new SshTarget("root", "203.0.113.9", 2222, "/k/id");
        List<String> argv = t.sshCommand("/home/x/.nmox/ops/known_hosts", "uptime");
        assertThat(argv).containsExactly("ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=8",
                "-o", "StrictHostKeyChecking=accept-new", "-o", "UserKnownHostsFile=/home/x/.nmox/ops/known_hosts",
                "-p", "2222", "-i", "/k/id", "root@203.0.113.9", "--", "uptime");
    }

    @Test
    @DisplayName("the interactive ssh carries no BatchMode (a passphrase prompt is fine in a terminal)")
    void interactiveSsh() {
        SshTarget t = new SshTarget("root", "203.0.113.9", 22, null);
        assertThat(t.interactiveSsh(null)).containsExactly("ssh", "-o", "StrictHostKeyChecking=accept-new",
                "-p", "22", "root@203.0.113.9");
    }

    @Test
    @DisplayName("mosh gets its ssh options as one string; a known_hosts path with a space is left out rather than broken")
    void mosh() {
        SshTarget t = new SshTarget("root", "203.0.113.9", 2222, null);
        assertThat(t.interactiveMosh("/x/known_hosts")).containsExactly("mosh",
                "--ssh=ssh -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/x/known_hosts -p 2222",
                "root@203.0.113.9");
        assertThat(t.interactiveMosh("/Application Support/known_hosts").get(1))
                .doesNotContain("Application").contains("-p 2222");
    }
}
