package org.nmox.studio.ui.irc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The custom-filter wiring gate (v2.10.2, the night review's find):
 * every place a chat line can reach a transcript must consult
 * {@code textFilters.hides} — the PRIVMSG path, the NOTICE path, and
 * BOTH local-echo sites (the send path and /msg on a server without
 * echo-message). The v2.10.0 code guarded only the first two, so
 * whether your OWN matching line hid depended on a server capability
 * you cannot see. Wiring lives in a Swing window this suite cannot
 * drive headless, so the gate reads the SOURCE: each
 * {@code capEnabled("echo-message")} local-echo block must carry the
 * verdict before its append.
 */
class FilterWiringGateTest {

    private static String source() throws Exception {
        return Files.readString(Path.of("src", "main", "java", "org", "nmox",
                "studio", "ui", "irc", "IrcTopComponent.java"));
    }

    @Test
    @DisplayName("every local-echo block consults textFilters.hides before appending")
    void localEchoBlocksAreGuarded() throws Exception {
        String src = source();
        int from = 0;
        int blocks = 0;
        while (true) {
            int at = src.indexOf("capEnabled(\"echo-message\")", from);
            if (at < 0) {
                break;
            }
            // the local-echo region: from the cap check to its append
            String region = src.substring(at, Math.min(src.length(), at + 800));
            if (region.contains("appendChat(")) {
                blocks++;
                assertThat(region.indexOf("textFilters.hides"))
                        .as("local-echo block at offset " + at
                                + " must consult the filter before appending")
                        .isBetween(0, region.indexOf("appendChat("));
            }
            from = at + 1;
        }
        assertThat(blocks)
                .as("the two local-echo sites (send path and /msg) exist")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("the receive paths consult the filter too — four hides sites total")
    void receivePathsAreGuarded() throws Exception {
        int sites = source().split("textFilters\\.hides", -1).length - 1;
        assertThat(sites)
                .as("PRIVMSG + NOTICE + two local-echo sites")
                .isGreaterThanOrEqualTo(4);
    }
}
