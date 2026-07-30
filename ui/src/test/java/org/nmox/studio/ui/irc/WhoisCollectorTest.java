package org.nmox.studio.ui.irc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.ui.irc.protocol.IrcMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The WHOIS assembler: five scattered numerics in, one card-ready
 * record out on 318 — plus the idle formatter and the restart-on-311
 * rule that keeps back-to-back queries from blending.
 */
class WhoisCollectorTest {

    private static IrcMessage m(String line) {
        return IrcMessage.parse(line);
    }

    @Test
    @DisplayName("311/312/317/319/330 assemble; 318 completes the record")
    void fullAssembly() {
        WhoisCollector c = new WhoisCollector();
        assertThat(c.accept(m(":srv 311 me alice ali host.example * :Alice Adams"))).isNull();
        assertThat(c.collecting()).isTrue();
        assertThat(c.accept(m(":srv 312 me alice irc.example.net :Example server"))).isNull();
        assertThat(c.accept(m(":srv 317 me alice 3725 1721000000 :seconds idle"))).isNull();
        assertThat(c.accept(m(":srv 319 me alice :@#ops +#dev #chat"))).isNull();
        assertThat(c.accept(m(":srv 330 me alice aliceacct :is logged in as"))).isNull();

        WhoisCollector.WhoisInfo info = c.accept(m(":srv 318 me alice :End of /WHOIS"));
        assertThat(info).isNotNull();
        assertThat(info.nick()).isEqualTo("alice");
        assertThat(info.userHost()).isEqualTo("ali@host.example");
        assertThat(info.realName()).isEqualTo("Alice Adams");
        assertThat(info.server()).isEqualTo("irc.example.net");
        assertThat(info.serverInfo()).isEqualTo("Example server");
        assertThat(info.idleSeconds()).isEqualTo(3725);
        assertThat(info.channels()).containsExactly("@#ops", "+#dev", "#chat");
        assertThat(info.account()).isEqualTo("aliceacct");
        assertThat(c.collecting()).as("318 closes the record").isFalse();
    }

    @Test
    @DisplayName("A 318 with no open record returns null (unsolicited end)")
    void unsolicitedEndIsNull() {
        assertThat(new WhoisCollector().accept(m(":srv 318 me bob :End"))).isNull();
    }

    @Test
    @DisplayName("A new 311 restarts the record — queries never blend")
    void newQueryRestarts() {
        WhoisCollector c = new WhoisCollector();
        c.accept(m(":srv 311 me alice a h * :A"));
        c.accept(m(":srv 319 me alice :#one"));
        c.accept(m(":srv 311 me bob b h2 * :B")); // second query begins
        WhoisCollector.WhoisInfo info = c.accept(m(":srv 318 me bob :End"));
        assertThat(info.nick()).isEqualTo("bob");
        assertThat(info.channels()).as("alice's channels don't leak into bob's card").isEmpty();
    }

    @Test
    @DisplayName("Missing numerics leave honest empties (and idle −1)")
    void sparseAnswer() {
        WhoisCollector c = new WhoisCollector();
        c.accept(m(":srv 311 me carol cu ch * :Carol"));
        WhoisCollector.WhoisInfo info = c.accept(m(":srv 318 me carol :End"));
        assertThat(info.server()).isEmpty();
        assertThat(info.idleSeconds()).isEqualTo(-1);
        assertThat(info.channels()).isEmpty();
        assertThat(info.account()).isEmpty();
    }

    @Test
    @DisplayName("formatIdle renders h/m/s compactly")
    void idleFormatting() {
        assertThat(WhoisCollector.formatIdle(3725)).isEqualTo("1h 2m 5s");
        assertThat(WhoisCollector.formatIdle(65)).isEqualTo("1m 5s");
        assertThat(WhoisCollector.formatIdle(7)).isEqualTo("7s");
        assertThat(WhoisCollector.formatIdle(0)).isEqualTo("0s");
        assertThat(WhoisCollector.formatIdle(-1)).isEmpty();
    }

    @Test
    @DisplayName("cardLines renders only the facts the server sent")
    void cardRendersKnownFacts() {
        WhoisCollector c = new WhoisCollector();
        c.accept(m(":srv 311 me alice ali host * :Alice"));
        c.accept(m(":srv 317 me alice 60 0 :seconds idle"));
        WhoisCollector.WhoisInfo info = c.accept(m(":srv 318 me alice :End"));
        assertThat(WhoisCollector.cardLines(info))
                .containsExactly(
                        "── whois alice ──",
                        "  ali@host (Alice)",
                        "  idle: 1m 0s");
    }
}
