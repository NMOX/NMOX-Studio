package org.nmox.studio.ui.irc;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Command feedback lands WHERE THE COMMAND WAS TYPED (v2.34.4, made
 * structural by the v2.36.2 law sweep, which found ELEVEN handlers
 * answering into the network-status transcript while the user watched
 * a channel — the worst being "Not connected", which made every
 * command typed while disconnected answer invisibly). The gate is the
 * OUTCOME, not a site list: the feedbackKey() helper must exist with
 * the active-first rule, and NO command handler may append status to
 * the bare network key — any future handler spelled that way fails
 * this test by count, not by review.
 */
class FilterFeedbackInPlaceTest {

    private static String src() throws Exception {
        // normalized: a CRLF checkout (windows autocrlf) must not break
        // the multi-line contains below (the v1.42.0 folding class)
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/ui/irc/IrcTopComponent.java"))
                .replace("\r\n", "\n");
    }

    @Test
    @DisplayName("feedbackKey() carries the active-first rule")
    void helperCarriesTheRule() throws Exception {
        assertThat(src()).contains(
                "private String feedbackKey() {\n"
                + "        return activeKey != null ? activeKey : key(activeNetwork(), \"\");");
    }

    @Test
    @DisplayName("no command answers into the bare network-status transcript")
    void noBareNetworkStatusAnswers() throws Exception {
        assertThat(src())
                .as("a handler spelled appendStatus(key(activeNetwork(), \"\"), …) answers "
                        + "into the wrong room — route it through feedbackKey()")
                .doesNotContain("appendStatus(key(activeNetwork(), \"\")");
    }

    @Test
    @DisplayName("the sweep's sites ride the helper — at least eleven callers")
    void sitesRideTheHelper() throws Exception {
        String s = src();
        long count = s.lines().filter(l -> l.contains("feedbackKey()")).count();
        assertThat(count)
                .as("the eleven swept sites + commandFilter + the definition")
                .isGreaterThanOrEqualTo(12);
    }
}
