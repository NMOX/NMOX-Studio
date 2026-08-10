package org.nmox.studio.ui.irc.engine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.nmox.studio.ui.irc.protocol.IrcMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 66 closed (v1.322.0): logging is a client-lifetime concern.
 *
 * <p>These tests drive the tap as what it is — a plain
 * {@link IrcClient.Listener} — with parsed protocol lines and NO window
 * anywhere in sight. That absence is the point: every log line asserted
 * here was previously written by the window's Bridge, so with the tab
 * closed it was silently lost from the files the user turned on.
 */
class IrcLogTapTest {

    private static IrcLogger logger(Path root) {
        IrcLogger l = new IrcLogger(root);
        l.setEnabled(true);
        return l;
    }

    private static String todayFile(Path root, String network, String target)
            throws Exception {
        Path dir = root.resolve(network).resolve(
                target.replaceAll("[^A-Za-z0-9#&._-]", "_"));
        try (var files = Files.list(dir)) {
            List<Path> logs = files.toList();
            assertThat(logs).as("one daily file under " + dir).hasSize(1);
            return Files.readString(logs.get(0));
        }
    }

    @Test
    @DisplayName("channel chat logs with no window in existence")
    void channelChatLogsWindowless(@TempDir File dir) throws Exception {
        IrcLogger logger = logger(dir.toPath());
        IrcLogTap tap = new IrcLogTap("libera", logger);
        tap.registered("me");

        tap.lineReceived(IrcMessage.parse(
                ":alice!a@host PRIVMSG #nmox :the tab is closed and this still logs"));
        assertThat(logger.awaitIdle(5000)).isTrue();

        assertThat(todayFile(dir.toPath(), "libera", "#nmox"))
                .contains("<alice> the tab is closed and this still logs");
    }

    @Test
    @DisplayName("a CTCP ACTION logs as an action; other CTCP is not conversation")
    void actionLogsOtherCtcpDoesNot(@TempDir File dir) throws Exception {
        IrcLogger logger = logger(dir.toPath());
        IrcLogTap tap = new IrcLogTap("libera", logger);
        tap.registered("me");

        tap.lineReceived(IrcMessage.parse(
                ":alice!a@host PRIVMSG #nmox :ACTION waves"));
        tap.lineReceived(IrcMessage.parse(
                ":alice!a@host PRIVMSG #nmox :VERSION"));
        assertThat(logger.awaitIdle(5000)).isTrue();

        String log = todayFile(dir.toPath(), "libera", "#nmox");
        assertThat(log).contains("alice waves");
        assertThat(log).doesNotContain("VERSION");
    }

    @Test
    @DisplayName("a private message files under the PEER, matching the Bridge's keying")
    void privateMessageFilesUnderPeer(@TempDir File dir) throws Exception {
        IrcLogger logger = logger(dir.toPath());
        IrcLogTap tap = new IrcLogTap("libera", logger);
        tap.registered("me");

        // inbound PM: addressed to me, files under the SENDER — and when the
        // echo-message cap returns our own line, sender==self and it files
        // under the TARGET (the peer again), so window-open and
        // window-closed traffic interleave into the same file
        tap.lineReceived(IrcMessage.parse(
                ":bob!b@host PRIVMSG me :psst"));
        tap.lineReceived(IrcMessage.parse(
                ":me!m@host PRIVMSG bob :echoed copy of my own reply"));
        assertThat(logger.awaitIdle(5000)).isTrue();

        String log = todayFile(dir.toPath(), "libera", "bob");
        assertThat(log).contains("<bob> psst");
        assertThat(log).contains("<me> echoed copy of my own reply");
    }

    @Test
    @DisplayName("a 353-seeded member's QUIT logs in the channel they were in")
    void seededMemberQuitLogs(@TempDir File dir) throws Exception {
        IrcLogger logger = logger(dir.toPath());
        IrcLogTap tap = new IrcLogTap("libera", logger);
        tap.registered("me");

        // QUIT names no channels — the fan-out NEEDS the membership map,
        // and a long-time member is only learnable from RPL_NAMREPLY
        tap.lineReceived(IrcMessage.parse(
                ":server 353 me = #nmox :@oldtimer +bob me"));
        tap.lineReceived(IrcMessage.parse(
                ":oldtimer!o@host QUIT :bye"));
        assertThat(logger.awaitIdle(5000)).isTrue();

        assertThat(todayFile(dir.toPath(), "libera", "#nmox"))
                .contains("oldtimer quit");
    }

    @Test
    @DisplayName("a rename is followed, so the later QUIT logs under the new nick")
    void renameThenQuitLogsNewNick(@TempDir File dir) throws Exception {
        IrcLogger logger = logger(dir.toPath());
        IrcLogTap tap = new IrcLogTap("libera", logger);
        tap.registered("me");

        tap.lineReceived(IrcMessage.parse(
                ":carol!c@host JOIN #nmox"));
        tap.lineReceived(IrcMessage.parse(
                ":carol!c@host NICK :carol_away"));
        tap.lineReceived(IrcMessage.parse(
                ":carol_away!c@host QUIT :gone"));
        assertThat(logger.awaitIdle(5000)).isTrue();

        String log = todayFile(dir.toPath(), "libera", "#nmox");
        assertThat(log).contains("carol joined");
        assertThat(log).contains("carol_away quit");
    }

    @Test
    @DisplayName("join/part/kick log as events")
    void membershipEventsLog(@TempDir File dir) throws Exception {
        IrcLogger logger = logger(dir.toPath());
        IrcLogTap tap = new IrcLogTap("libera", logger);
        tap.registered("me");

        tap.lineReceived(IrcMessage.parse(":dave!d@host JOIN :#nmox"));
        tap.lineReceived(IrcMessage.parse(":dave!d@host PART #nmox :later"));
        tap.lineReceived(IrcMessage.parse(":op!o@host KICK #nmox troll :spam"));
        assertThat(logger.awaitIdle(5000)).isTrue();

        String log = todayFile(dir.toPath(), "libera", "#nmox");
        assertThat(log).contains("dave joined")
                .contains("dave left")
                .contains("troll was kicked");
    }

    @Test
    @DisplayName("the tap is wired at client creation and the Bridge no longer logs inbound")
    void tapIsWiredAndBridgeStoppedLogging() throws Exception {
        // The two halves of ledger 66's close, pinned so neither can quietly
        // revert: the tap must be ATTACHED where clients are born (or it
        // never runs), and the Bridge must NOT log inbound traffic (or
        // every line while the window is open would be written twice).
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/ui/irc/IrcTopComponent.java"));
        assertThat(src)
                .as("the tap attaches at client creation, beside the Bridge")
                .contains("new org.nmox.studio.ui.irc.engine.IrcLogTap(");
        assertThat(src)
                .as("the Bridge renders only — event logging moved to the tap")
                .doesNotContain("logger.event(");
        // the send path keeps its logger calls: sends require a window by
        // construction, and they are guarded by !capEnabled("echo-message")
        // so echo-capable servers log the echoed copy via the tap instead
        assertThat(src).contains("capEnabled(\"echo-message\")");
    }
}
