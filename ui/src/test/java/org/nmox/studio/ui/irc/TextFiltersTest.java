package org.nmox.studio.ui.irc;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The custom /filter core (v2.10.0): named regexes hide matching chat
 * lines per channel or globally. Every refusal is a returned reason
 * (never a throw at the command site), matching is case-insensitive
 * like WeeChat's, a disabled filter is inert, and the persisted string
 * form survives a regex that contains the separator itself.
 */
class TextFiltersTest {

    @Test
    @DisplayName("a global filter hides matching lines in every channel; a scoped one only in its own")
    void scopeMatching() {
        TextFilters f = new TextFilters();
        assertThat(f.add("spam", "*", "buy .* now", true)).isNull();
        assertThat(f.add("bots", "#dev", "^<annoybot>", true)).isNull();
        assertThat(f.hides("#random", "<seller> BUY GOLD now")).isTrue();
        assertThat(f.hides("#dev", "<annoybot> build 42 ok")).isTrue();
        assertThat(f.hides("#random", "<annoybot> build 42 ok")).isFalse();
        assertThat(f.hides("#DEV", "<annoybot> case-insensitive scope")).isTrue();
    }

    @Test
    @DisplayName("refusals speak: duplicate name, bad regex, over-long regex — and none enter the table")
    void refusals() {
        TextFilters f = new TextFilters();
        assertThat(f.add("a", "*", "ok", true)).isNull();
        assertThat(f.add("A", "*", "dup", true)).contains("already exists");
        assertThat(f.add("b", "*", "unclosed(", true)).startsWith("bad regex");
        assertThat(f.add("c", "*", "x".repeat(TextFilters.MAX_REGEX + 1), true))
                .contains("refused");
        assertThat(f.list()).hasSize(1);
    }

    @Test
    @DisplayName("a disabled filter is inert until re-enabled; unknown names refuse the toggle")
    void toggling() {
        TextFilters f = new TextFilters();
        f.add("noise", "*", "lorem", true);
        assertThat(f.setEnabled("noise", false)).isTrue();
        assertThat(f.hides("#x", "<a> lorem ipsum")).isFalse();
        assertThat(f.setEnabled("noise", true)).isTrue();
        assertThat(f.hides("#x", "<a> lorem ipsum")).isTrue();
        assertThat(f.setEnabled("ghost", true)).isFalse();
    }

    @Test
    @DisplayName("the string form round-trips, including a regex containing the separator")
    void stringFormRoundTrip() {
        TextFilters f = new TextFilters();
        f.add("either", "#dev", "foo|bar", false);
        String form = f.list().get(0).stringForm();
        TextFilters g = new TextFilters();
        assertThat(g.addFromStringForm("either", form)).isTrue();
        TextFilters.Filter back = g.list().get(0);
        assertThat(back.regex()).isEqualTo("foo|bar");
        assertThat(back.scope()).isEqualTo("#dev");
        assertThat(back.enabled()).isFalse();
        assertThat(g.addFromStringForm("junk", "not-a-form")).isFalse();
    }

    @Test
    @DisplayName("lastlog returns the LAST n matching lines, oldest first, case-insensitive")
    void lastlogMatches() {
        String transcript = String.join("\n",
                "[10:00] <a> deploy started",
                "[10:01] <b> lunch?",
                "[10:02] <a> DEPLOY finished",
                "[10:03] <c> deploy rollback",
                "[10:04] <b> ok");
        List<String> hits = TextFilters.lastlog(transcript, "deploy", 2);
        assertThat(hits).containsExactly(
                "[10:02] <a> DEPLOY finished",
                "[10:03] <c> deploy rollback");
        assertThat(TextFilters.lastlog(transcript, "nothing", 5)).isEmpty();
    }
}
