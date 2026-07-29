package org.nmox.studio.ui.irc.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nmox.studio.ui.irc.protocol.Numerics.Kind;

/**
 * The numeric classifier routes server replies into the families the
 * engine and UI switch on; these pin every family plus the 4xx/5xx
 * error fallback and the not-a-numeric case.
 */
class NumericsTest {

    @Test
    @DisplayName("001 is the welcome; the connection is registered")
    void welcome() {
        assertThat(Numerics.classify("001")).isEqualTo(Kind.WELCOME);
    }

    @Test
    @DisplayName("353/366 are the names list and its end")
    void names() {
        assertThat(Numerics.classify("353")).isEqualTo(Kind.NAMES);
        assertThat(Numerics.classify("366")).isEqualTo(Kind.NAMES_END);
    }

    @Test
    @DisplayName("332/333 are topic text and topic metadata")
    void topic() {
        assertThat(Numerics.classify("332")).isEqualTo(Kind.TOPIC);
        assertThat(Numerics.classify("333")).isEqualTo(Kind.TOPIC_META);
    }

    @Test
    @DisplayName("433 is nick-in-use — an error the engine handles specially")
    void nickInUse() {
        assertThat(Numerics.classify("433")).isEqualTo(Kind.NICK_IN_USE);
    }

    @Test
    @DisplayName("372/375/376 are all MOTD lines")
    void motd() {
        assertThat(Numerics.classify("372")).isEqualTo(Kind.MOTD);
        assertThat(Numerics.classify("375")).isEqualTo(Kind.MOTD);
        assertThat(Numerics.classify("376")).isEqualTo(Kind.MOTD);
    }

    @Test
    @DisplayName("311/312/317/318/319 are the WHOIS family")
    void whois() {
        for (String n : new String[] {"311", "312", "317", "318", "319"}) {
            assertThat(Numerics.classify(n)).as(n).isEqualTo(Kind.WHOIS);
        }
    }

    @Test
    @DisplayName("Unnamed 4xx/5xx numerics are errors; others are OTHER")
    void errorRangeAndOther() {
        assertThat(Numerics.classify("404")).isEqualTo(Kind.ERROR);
        assertThat(Numerics.classify("502")).isEqualTo(Kind.ERROR);
        assertThat(Numerics.classify("005")).isEqualTo(Kind.OTHER);
        assertThat(Numerics.classify("251")).isEqualTo(Kind.OTHER);
    }

    @Test
    @DisplayName("Word commands are NOT_NUMERIC; malformed numerics too")
    void notNumeric() {
        assertThat(Numerics.classify("PRIVMSG")).isEqualTo(Kind.NOT_NUMERIC);
        assertThat(Numerics.classify("12a")).isEqualTo(Kind.NOT_NUMERIC);
        assertThat(Numerics.classify(null)).isEqualTo(Kind.NOT_NUMERIC);
        assertThat(Numerics.isNumeric("001")).isTrue();
        assertThat(Numerics.isNumeric("1")).isFalse();
        assertThat(Numerics.isNumeric("PING")).isFalse();
    }
}
