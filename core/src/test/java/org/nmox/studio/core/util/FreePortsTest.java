package org.nmox.studio.core.util;

import java.net.InetSocketAddress;
import java.net.ServerSocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The free-port probe behind the serving lanes that pick their port
 * (v1.320.0). Born from the learner walk of learning space #89: the
 * catalog's front-door space died on its first Run because something
 * on the machine held 8000 and {@code python3 -m http.server} — unlike
 * {@code http-server} — refuses a busy port outright.
 */
class FreePortsTest {

    @Test
    @DisplayName("a free preferred port is returned unchanged")
    void freePortReturnsItself() throws Exception {
        // grab an ephemeral port the OS says is free, release it, and ask
        // for it — raced only in theory, and a flake here would say so
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        assertThat(FreePorts.firstFreeFrom(port)).isEqualTo(port);
    }

    @Test
    @DisplayName("a held port is skipped — the walk's exact failure")
    void heldPortIsSkipped() throws Exception {
        // hold a real port the way the walk's machine held 8000 (a wildcard
        // listener, which is what python itself binds)
        try (ServerSocket holder = new ServerSocket()) {
            holder.setReuseAddress(true);
            holder.bind(new InetSocketAddress(0));
            int held = holder.getLocalPort();
            int picked = FreePorts.firstFreeFrom(held);
            assertThat(picked)
                    .as("the probe must not hand back the port it just found"
                            + " busy — that is the bug this class exists to fix")
                    .isNotEqualTo(held)
                    .isGreaterThan(held)
                    .isLessThan(held + FreePorts.RANGE);
        }
    }
}
