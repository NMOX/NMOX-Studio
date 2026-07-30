package org.nmox.studio.ui.irc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.ui.irc.protocol.IrcMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code /list} collector against fake-server-shaped lines: rows
 * from 322 (topics stripped of mIRC codes), completion on 323, and the
 * bounded-list law — past {@link ChannelListCollector#CAP} rows it
 * keeps counting but stops storing, so the dialog can say "showing
 * first 2000 of N" honestly.
 */
class ChannelListCollectorTest {

    private static IrcMessage m(String line) {
        return IrcMessage.parse(line);
    }

    @Test
    @DisplayName("322 rows collect name/users/topic; 323 completes")
    void rowsCollectAndComplete() {
        ChannelListCollector c = new ChannelListCollector();
        assertThat(c.accept(m(":srv 321 me Channel :Users Name"))).isFalse();
        assertThat(c.accept(m(":srv 322 me #dev 42 :All things development"))).isFalse();
        assertThat(c.accept(m(":srv 322 me #ops 7 :"))).isFalse();
        assertThat(c.complete()).isFalse();
        assertThat(c.accept(m(":srv 323 me :End of /LIST"))).isTrue();
        assertThat(c.complete()).isTrue();
        assertThat(c.rows()).containsExactly(
                new ChannelListCollector.Row("#dev", 42, "All things development"),
                new ChannelListCollector.Row("#ops", 7, ""));
        assertThat(c.totalSeen()).isEqualTo(2);
        assertThat(c.truncated()).isFalse();
    }

    @Test
    @DisplayName("Topics are stripped of mIRC formatting codes")
    void topicsStripped() {
        ChannelListCollector c = new ChannelListCollector();
        c.accept(m(":srv 322 me #x 1 :bold topic"));
        assertThat(c.rows().get(0).topic()).isEqualTo("bold topic");
    }

    @Test
    @DisplayName("Past the cap the collector counts but stops storing")
    void boundedPastTheCap() {
        ChannelListCollector c = new ChannelListCollector();
        for (int i = 0; i < ChannelListCollector.CAP + 500; i++) {
            c.accept(m(":srv 322 me #chan" + i + " 5 :t"));
        }
        c.accept(m(":srv 323 me :End of /LIST"));
        assertThat(c.rows()).hasSize(ChannelListCollector.CAP);
        assertThat(c.totalSeen()).isEqualTo(ChannelListCollector.CAP + 500);
        assertThat(c.truncated()).isTrue();
    }

    @Test
    @DisplayName("A garbled user count degrades to 0, never a crash")
    void garbledUserCount() {
        ChannelListCollector c = new ChannelListCollector();
        c.accept(m(":srv 322 me #weird abc :topic"));
        assertThat(c.rows().get(0).users()).isZero();
    }

    @Test
    @DisplayName("Unrelated numerics are ignored")
    void unrelatedIgnored() {
        ChannelListCollector c = new ChannelListCollector();
        assertThat(c.accept(m(":srv 372 me :motd line"))).isFalse();
        assertThat(c.rows()).isEmpty();
        assertThat(c.totalSeen()).isZero();
    }
}
