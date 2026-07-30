package org.nmox.studio.ui.irc.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stacked status prefixes (the IRCv3 multi-prefix capability): strip
 * removes the WHOLE sigil run, rank takes the highest sigil anywhere
 * in it.
 */
class NickPrefixTest {

    @Test
    @DisplayName("strip removes single and STACKED prefix runs")
    void stripHandlesStacks() {
        assertThat(NickPrefix.strip("@alice")).isEqualTo("alice");
        assertThat(NickPrefix.strip("@+alice")).isEqualTo("alice");
        assertThat(NickPrefix.strip("~&@%+alice")).isEqualTo("alice");
        assertThat(NickPrefix.strip("alice")).isEqualTo("alice");
        assertThat(NickPrefix.strip("")).isEmpty();
    }

    @Test
    @DisplayName("rank: ops 0, half-op 1, voice 2, plain 3")
    void singleSigilRanks() {
        assertThat(NickPrefix.rank("~owner")).isZero();
        assertThat(NickPrefix.rank("&admin")).isZero();
        assertThat(NickPrefix.rank("@op")).isZero();
        assertThat(NickPrefix.rank("%half")).isEqualTo(1);
        assertThat(NickPrefix.rank("+voice")).isEqualTo(2);
        assertThat(NickPrefix.rank("plain")).isEqualTo(3);
        assertThat(NickPrefix.rank("")).isEqualTo(3);
    }

    @Test
    @DisplayName("A stacked prefix ranks by its HIGHEST sigil, in any order")
    void stackedRanksByHighest() {
        assertThat(NickPrefix.rank("@+alice")).isZero();
        assertThat(NickPrefix.rank("+@alice")).as("sloppy bridge order still ranks op").isZero();
        assertThat(NickPrefix.rank("%+bob")).isEqualTo(1);
    }

    @Test
    @DisplayName("Sigil-like characters INSIDE a nick are not prefixes")
    void sigilsInsideNickIgnored() {
        assertThat(NickPrefix.strip("we+ird")).isEqualTo("we+ird");
        assertThat(NickPrefix.rank("we+ird")).isEqualTo(3);
    }
}
