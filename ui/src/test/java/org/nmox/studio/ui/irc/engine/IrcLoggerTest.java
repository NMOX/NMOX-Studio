package org.nmox.studio.ui.irc.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The chat logger against a temp directory: file layout and line
 * format, date-based rotation, the services refusal (NickServ/ChanServ
 * traffic never touches disk), the session toggle, mIRC stripping, and
 * the bounded-line truncation marker.
 */
class IrcLoggerTest {

    private static final LocalDateTime NOON
            = LocalDateTime.of(2026, 7, 30, 12, 34, 56);

    private IrcLogger loggerAt(Path root) {
        IrcLogger logger = new IrcLogger(root);
        logger.clock = () -> NOON;
        return logger;
    }

    private static String read(Path root, String network, String target, String day)
            throws Exception {
        return Files.readString(root.resolve(network).resolve(target).resolve(day + ".log"));
    }

    @Test
    @DisplayName("Chat, action, and event lines land in the daily file, formatted")
    void formatsAndWrites(@TempDir Path root) throws Exception {
        IrcLogger logger = loggerAt(root);
        logger.chat("libera", "#dev", "alice", "hello world");
        logger.action("libera", "#dev", "bob", "waves");
        logger.event("libera", "#dev", "carol joined");
        assertThat(logger.awaitIdle(5000)).isTrue();
        String content = read(root, "libera", "#dev", "2026-07-30");
        assertThat(content).containsSubsequence(
                "[12:34:56] <alice> hello world",
                "[12:34:56] * bob waves",
                "[12:34:56] -- carol joined");
    }

    @Test
    @DisplayName("A new day means a new file (rotation by date)")
    void rotatesByDate(@TempDir Path root) throws Exception {
        IrcLogger logger = loggerAt(root);
        logger.write("net", "#c", LocalDate.of(2026, 7, 29), "[23:59:59] <a> yesterday");
        logger.write("net", "#c", LocalDate.of(2026, 7, 30), "[00:00:01] <a> today");
        assertThat(logger.awaitIdle(5000)).isTrue();
        assertThat(read(root, "net", "#c", "2026-07-29")).contains("yesterday");
        assertThat(read(root, "net", "#c", "2026-07-30")).contains("today");
    }

    @Test
    @DisplayName("NickServ and ChanServ targets are NEVER logged (credentials transit)")
    void servicesAreRefused(@TempDir Path root) {
        IrcLogger logger = loggerAt(root);
        logger.chat("libera", "NickServ", "me", "IDENTIFY hunter2");
        logger.chat("libera", "nickserv", "me", "IDENTIFY hunter2");
        logger.chat("libera", "ChanServ", "me", "OP #chan");
        logger.chat("libera", "CHANSERV", "me", "OP #chan");
        assertThat(logger.awaitIdle(5000)).isTrue();
        assertThat(root.resolve("libera")).doesNotExist();
    }

    @Test
    @DisplayName("The session toggle silences and re-enables writes")
    void toggleHonored(@TempDir Path root) throws Exception {
        IrcLogger logger = loggerAt(root);
        logger.setEnabled(false);
        logger.chat("net", "#c", "a", "while off");
        logger.setEnabled(true);
        logger.chat("net", "#c", "a", "while on");
        assertThat(logger.awaitIdle(5000)).isTrue();
        String content = read(root, "net", "#c", "2026-07-30");
        assertThat(content).contains("while on").doesNotContain("while off");
    }

    @Test
    @DisplayName("mIRC control codes are stripped before writing")
    void mircStripped(@TempDir Path root) throws Exception {
        IrcLogger logger = loggerAt(root);
        logger.chat("net", "#c", "a", "bold and 04red text");
        assertThat(logger.awaitIdle(5000)).isTrue();
        assertThat(read(root, "net", "#c", "2026-07-30"))
                .contains("<a> bold and red text");
    }

    @Test
    @DisplayName("A line past 2000 chars is truncated with an honest marker")
    void boundedLines(@TempDir Path root) throws Exception {
        IrcLogger logger = loggerAt(root);
        logger.chat("net", "#c", "flooder", "x".repeat(5000));
        assertThat(logger.awaitIdle(5000)).isTrue();
        String content = read(root, "net", "#c", "2026-07-30");
        assertThat(content).contains(IrcLogger.TRUNCATION_MARKER);
        assertThat(content.length())
                .isLessThan(IrcLogger.MAX_LINE_CHARS + 100);
    }

    @Test
    @DisplayName("Path-hostile channel names are sanitized for the filesystem")
    void sanitizesNames(@TempDir Path root) throws Exception {
        IrcLogger logger = loggerAt(root);
        logger.chat("net", "#a/../b", "a", "sneaky");
        assertThat(logger.awaitIdle(5000)).isTrue();
        assertThat(root.resolve("net").resolve("#a_.._b").resolve("2026-07-30.log"))
                .exists();
        assertThat(IrcLogger.sanitize("a:b\\c/d")).isEqualTo("a_b_c_d");
        assertThat(IrcLogger.sanitize("")).isEqualTo("_");
    }
}
