package org.nmox.studio.ui.irc.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The IRC line codec is the pure heart of the client — every byte the
 * engine reads or writes goes through it, so the parser vectors here
 * aim exhaustive: prefix forms, IRCv3 tags with the spec's escaping,
 * trailing-parameter edges (colons inside, empty, absent), and the RFC
 * 1459 fifteen-parameter rule. The codec law is pinned too:
 * {@code render(parse(x))} is byte-identical for canonical wire lines.
 */
class IrcMessageTest {

    @Test
    @DisplayName("A full PRIVMSG parses into prefix parts, command, params, trailing")
    void parseFullPrivmsg() {
        IrcMessage m = IrcMessage.parse(":dave!~david@example.org PRIVMSG #nmox :hello world");
        assertThat(m.prefix()).isEqualTo("dave!~david@example.org");
        assertThat(m.nick()).isEqualTo("dave");
        assertThat(m.user()).isEqualTo("~david");
        assertThat(m.host()).isEqualTo("example.org");
        assertThat(m.command()).isEqualTo("PRIVMSG");
        assertThat(m.params()).containsExactly("#nmox", "hello world");
        assertThat(m.trailing()).isEqualTo("hello world");
    }

    @Test
    @DisplayName("A line with no prefix parses; nick/user/host are null")
    void parseNoPrefix() {
        IrcMessage m = IrcMessage.parse("PING :irc.example.net");
        assertThat(m.prefix()).isNull();
        assertThat(m.nick()).isNull();
        assertThat(m.user()).isNull();
        assertThat(m.host()).isNull();
        assertThat(m.command()).isEqualTo("PING");
        assertThat(m.trailing()).isEqualTo("irc.example.net");
    }

    @Test
    @DisplayName("A bare server prefix has no user or host halves")
    void parseServerPrefix() {
        IrcMessage m = IrcMessage.parse(":irc.example.net 001 dave :Welcome to IRC");
        assertThat(m.nick()).isEqualTo("irc.example.net");
        assertThat(m.user()).isNull();
        assertThat(m.host()).isNull();
        assertThat(m.command()).isEqualTo("001");
        assertThat(m.param(0)).isEqualTo("dave");
    }

    @Test
    @DisplayName("IRCv3 tags parse, with spec escaping, before an ordinary line")
    void parseTags() {
        IrcMessage m = IrcMessage.parse(
                "@time=2026-01-01T00:00:00Z;msgid=abc;draft/label=a\\sb\\:c;flag "
                + ":n!u@h PRIVMSG #c :hi");
        assertThat(m.tags()).containsEntry("time", "2026-01-01T00:00:00Z")
                .containsEntry("msgid", "abc")
                .containsEntry("draft/label", "a b;c")
                .containsEntry("flag", "");
        assertThat(m.command()).isEqualTo("PRIVMSG");
        assertThat(m.trailing()).isEqualTo("hi");
    }

    @Test
    @DisplayName("Tag-value escapes cover backslash, CR, LF; a dangling backslash drops")
    void tagEscapes() {
        assertThat(IrcMessage.unescapeTagValue("a\\\\b\\rc\\nd")).isEqualTo("a\\b\rc\nd");
        assertThat(IrcMessage.unescapeTagValue("oops\\")).isEqualTo("oops");
        assertThat(IrcMessage.unescapeTagValue("\\x")).isEqualTo("x");
        assertThat(IrcMessage.escapeTagValue("a b;c\\d")).isEqualTo("a\\sb\\:c\\\\d");
    }

    @Test
    @DisplayName("Colons INSIDE a trailing param stay verbatim")
    void trailingWithColons() {
        IrcMessage m = IrcMessage.parse(":srv 372 me :- MOTD: read this: now");
        assertThat(m.trailing()).isEqualTo("- MOTD: read this: now");
    }

    @Test
    @DisplayName("An empty trailing param is present-but-empty, not absent")
    void emptyTrailing() {
        IrcMessage m = IrcMessage.parse("TOPIC #chan :");
        assertThat(m.hasTrailing()).isTrue();
        assertThat(m.trailing()).isEmpty();
        assertThat(m.params()).containsExactly("#chan", "");
    }

    @Test
    @DisplayName("No trailing at all: trailing() is null")
    void noTrailing() {
        IrcMessage m = IrcMessage.parse("JOIN #chan");
        assertThat(m.hasTrailing()).isFalse();
        assertThat(m.trailing()).isNull();
        assertThat(m.params()).containsExactly("#chan");
    }

    @Test
    @DisplayName("After 14 middle params the rest of the line is the final param (RFC 1459)")
    void maxParams() {
        StringBuilder line = new StringBuilder("CMD");
        for (int i = 1; i <= 14; i++) {
            line.append(" p").append(i);
        }
        line.append(" rest of the line");
        IrcMessage m = IrcMessage.parse(line.toString());
        assertThat(m.params()).hasSize(15);
        assertThat(m.param(14)).isEqualTo("rest of the line");
    }

    @Test
    @DisplayName("The codec law: render(parse(x)) is byte-identical for canonical lines")
    void renderRoundTrip() {
        String[] vectors = {
            ":dave!~david@example.org PRIVMSG #nmox :hello world",
            "@time=2026-01-01T00:00:00Z :n!u@h PRIVMSG #c :hi",
            "PING :token",
            "TOPIC #chan :",
            "JOIN #chan",
            "MODE #chan +o dave",
            ":srv 353 me = #chan :@op +voiced plain",
            ":srv 372 me :- MOTD: read this: now",
        };
        for (String v : vectors) {
            assertThat(IrcMessage.parse(v).render()).as("round trip of: " + v).isEqualTo(v);
        }
    }

    @Test
    @DisplayName("A tag value with spaces re-escapes on render")
    void renderReEscapesTags() {
        String line = "@label=a\\sb PRIVMSG #c :x";
        assertThat(IrcMessage.parse(line).render()).isEqualTo(line);
    }

    @Test
    @DisplayName("of() builds sendable lines and colons the last param only when needed")
    void ofBuildsLines() {
        assertThat(IrcMessage.of("PRIVMSG", "#c", "hello world").render())
                .isEqualTo("PRIVMSG #c :hello world");
        assertThat(IrcMessage.of("JOIN", "#c").render()).isEqualTo("JOIN #c");
        assertThat(IrcMessage.of("PRIVMSG", "#c", "").render()).isEqualTo("PRIVMSG #c :");
    }

    @Test
    @DisplayName("param(i) out of range is an empty string, never a crash")
    void paramOutOfRange() {
        IrcMessage m = IrcMessage.parse("PING");
        assertThat(m.param(0)).isEmpty();
        assertThat(m.param(-1)).isEmpty();
        assertThat(m.param(99)).isEmpty();
    }

    @Test
    @DisplayName("Doubled spaces between params are tolerated")
    void toleratesExtraSpaces() {
        IrcMessage m = IrcMessage.parse(":srv  433  me  taken :Nickname is already in use");
        assertThat(m.command()).isEqualTo("433");
        assertThat(m.params()).containsExactly("me", "taken", "Nickname is already in use");
    }

    @Test
    @DisplayName("A line with no command is refused, honestly")
    void refusesCommandlessLines() {
        assertThatIllegalArgumentException().isThrownBy(() -> IrcMessage.parse(""));
        assertThatIllegalArgumentException().isThrownBy(() -> IrcMessage.parse("@tag=1"));
        assertThatIllegalArgumentException().isThrownBy(() -> IrcMessage.parse(":prefix.only"));
    }

    @Test
    @DisplayName("A nick-only prefix (no !user, no @host) is just a nick")
    void nickOnlyPrefix() {
        IrcMessage m = IrcMessage.parse(":dave QUIT :bye");
        assertThat(m.nick()).isEqualTo("dave");
        assertThat(m.user()).isNull();
        assertThat(m.host()).isNull();
    }

    @Test
    @DisplayName("nick@host without !user still splits")
    void nickAtHostPrefix() {
        IrcMessage m = IrcMessage.parse(":dave@example.org AWAY");
        assertThat(m.nick()).isEqualTo("dave");
        assertThat(m.user()).isNull();
        assertThat(m.host()).isEqualTo("example.org");
    }
}
