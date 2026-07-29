package org.nmox.studio.ui.irc.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CTCP hides inside PRIVMSG bodies between 0x01 bytes; these pin
 * detection, extraction (ACTION/VERSION/PING shapes), tolerance for a
 * missing closing delimiter, and the wrap/extract round trip.
 */
class CtcpTest {

    @Test
    @DisplayName("An ACTION payload extracts to its verb and argument")
    void extractsAction() {
        Ctcp c = Ctcp.extract("\u0001ACTION waves hello\u0001");
        assertThat(c).isNotNull();
        assertThat(c.command()).isEqualTo(Ctcp.ACTION);
        assertThat(c.argument()).isEqualTo("waves hello");
    }

    @Test
    @DisplayName("A bare VERSION query has an empty argument")
    void extractsVersion() {
        Ctcp c = Ctcp.extract("\u0001VERSION\u0001");
        assertThat(c).isNotNull();
        assertThat(c.command()).isEqualTo(Ctcp.VERSION);
        assertThat(c.argument()).isEmpty();
    }

    @Test
    @DisplayName("PING carries its token through")
    void extractsPing() {
        Ctcp c = Ctcp.extract("\u0001PING 1234567\u0001");
        assertThat(c).isNotNull();
        assertThat(c.command()).isEqualTo(Ctcp.PING);
        assertThat(c.argument()).isEqualTo("1234567");
    }

    @Test
    @DisplayName("Plain chat is not CTCP")
    void plainTextIsNotCtcp() {
        assertThat(Ctcp.isCtcp("hello")).isFalse();
        assertThat(Ctcp.extract("hello")).isNull();
        assertThat(Ctcp.extract(null)).isNull();
        assertThat(Ctcp.extract("")).isNull();
    }

    @Test
    @DisplayName("A missing closing delimiter is tolerated (some clients omit it)")
    void toleratesMissingCloser() {
        Ctcp c = Ctcp.extract("\u0001ACTION shrugs");
        assertThat(c).isNotNull();
        assertThat(c.command()).isEqualTo(Ctcp.ACTION);
        assertThat(c.argument()).isEqualTo("shrugs");
    }

    @Test
    @DisplayName("wrap/extract round-trips; action() is wrap(ACTION, …)")
    void wrapRoundTrip() {
        String wire = Ctcp.wrap(Ctcp.PING, "tok");
        assertThat(wire).isEqualTo("\u0001PING tok\u0001");
        Ctcp back = Ctcp.extract(wire);
        assertThat(back).isNotNull();
        assertThat(back.command()).isEqualTo(Ctcp.PING);
        assertThat(back.argument()).isEqualTo("tok");
        assertThat(Ctcp.action("waves")).isEqualTo("\u0001ACTION waves\u0001");
        assertThat(Ctcp.wrap(Ctcp.VERSION, "")).isEqualTo("\u0001VERSION\u0001");
    }
}
