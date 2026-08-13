package org.nmox.studio.ui.irc;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The operator toolkit's string forms: batched MODE lines with the flag
 * letter repeated once per target (what servers actually parse), and
 * the conventional {@code nick!*@*} ban mask for bare nicks.
 */
class OpModesTest {

    @Test
    @DisplayName("/op alice bob becomes one MODE line: MODE #chan +oo alice bob")
    void batchedGrant() {
        assertThat(OpModes.mode("#nmox", true, 'o', List.of("alice", "bob")))
                .isEqualTo("MODE #nmox +oo alice bob");
    }

    @Test
    @DisplayName("/devoice strips with a minus: MODE #chan -v carol")
    void singleRevoke() {
        assertThat(OpModes.mode("#nmox", false, 'v', List.of("carol")))
                .isEqualTo("MODE #nmox -v carol");
    }

    @Test
    @DisplayName("A bare nick bans as nick!*@*; a real hostmask passes through untouched")
    void banMaskConvention() {
        assertThat(OpModes.banMask("troll")).isEqualTo("troll!*@*");
        assertThat(OpModes.banMask("*!*@198.51.100.7")).isEqualTo("*!*@198.51.100.7");
        assertThat(OpModes.banMask("troll!user@host")).isEqualTo("troll!user@host");
    }
}
