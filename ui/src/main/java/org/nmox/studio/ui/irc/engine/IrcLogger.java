package org.nmox.studio.ui.irc.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmox.studio.ui.irc.protocol.MircFormat;
import org.openide.util.RequestProcessor;

/**
 * Per-channel chat logs: plain-text daily files under
 * {@code ~/.nmox/irc-logs/<network>/<target>/YYYY-MM-DD.log}, the
 * format every IRC user has grepped since the 90s —
 * {@code [HH:mm:ss] <nick> text}, {@code * nick text} for actions,
 * {@code -- nick joined} for events. mIRC control codes are stripped
 * before writing (logs are for reading back, not for re-rendering).
 *
 * <p><b>Threading:</b> callers (the EDT routing chat into transcripts)
 * only FORMAT and enqueue; every disk touch rides a dedicated
 * single-thread {@code RequestProcessor("IRC Log")} lane, so a slow
 * disk can never stutter a repaint (the EDT-never-blocks law).
 *
 * <p><b>Laws:</b> a line longer than {@link #MAX_LINE_CHARS} is
 * truncated with an honest marker (bounded writes); and NOTHING is ever
 * logged for a target named {@code NickServ} or {@code ChanServ}
 * (case-insensitive) — identify commands and services traffic can carry
 * credentials, and a credential must never land in a plaintext file
 * (the Keyring-only law's logging corollary). Logging is on by default,
 * togglable per-session with {@code /log off} and persisted globally in
 * {@link IrcConfig}.
 */
public final class IrcLogger {

    private static final Logger LOG = Logger.getLogger(IrcLogger.class.getName());

    /** Per-logged-line ceiling; past it the text is cut with a marker. */
    static final int MAX_LINE_CHARS = 2000;

    /** Appended to a line that hit the ceiling. */
    static final String TRUNCATION_MARKER = " …[truncated]";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private static IrcLogger instance;

    private final Path root;
    private final RequestProcessor rp = new RequestProcessor("IRC Log", 1);
    private volatile boolean enabled = true;

    /** Test seam: the wall clock the line stamps and file names read. */
    volatile Supplier<LocalDateTime> clock = LocalDateTime::now;

    /** Test seam: any directory works as the log root. */
    IrcLogger(Path root) {
        this.root = root;
    }

    /** The production logger, rooted at {@code ~/.nmox/irc-logs}. */
    public static synchronized IrcLogger getDefault() {
        if (instance == null) {
            instance = new IrcLogger(Paths.get(System.getProperty("user.home"),
                    ".nmox", "irc-logs"));
        }
        return instance;
    }

    /** The session toggle ({@code /log on|off}); construction default true. */
    public void setEnabled(boolean on) {
        this.enabled = on;
    }

    /** Whether lines are currently being written. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Where the files land (for the {@code /log} status line). */
    public Path root() {
        return root;
    }

    /** {@code [HH:mm:ss] <nick> text} — an ordinary chat line. */
    public void chat(String network, String target, String nick, String text) {
        append(network, target, "<" + nick + "> " + MircFormat.stripToText(text));
    }

    /** {@code [HH:mm:ss] * nick text} — a CTCP ACTION ({@code /me}). */
    public void action(String network, String target, String nick, String text) {
        append(network, target, "* " + nick + " " + MircFormat.stripToText(text));
    }

    /** {@code [HH:mm:ss] -- text} — join/part/quit and friends. */
    public void event(String network, String target, String text) {
        append(network, target, "-- " + MircFormat.stripToText(text));
    }

    /**
     * True for the services pseudo-users whose traffic is NEVER logged
     * (credentials transit there).
     */
    static boolean isService(String target) {
        String t = target.toLowerCase(Locale.ROOT);
        return t.equals("nickserv") || t.equals("chanserv");
    }

    /** Filesystem-safe file/dir name: path separators and colons swapped out. */
    static String sanitize(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            sb.append(c == '/' || c == '\\' || c == ':' || c == '\0' ? '_' : c);
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? "_" : s;
    }

    private void append(String network, String target, String body) {
        if (!enabled || isService(target)) {
            return;
        }
        String bounded = body.length() > MAX_LINE_CHARS
                ? body.substring(0, MAX_LINE_CHARS) + TRUNCATION_MARKER
                : body;
        LocalDateTime now = clock.get();
        String line = "[" + TIME.format(now) + "] " + bounded;
        write(network, target, now.toLocalDate(), line);
    }

    /** Package-private for the rotation test: writes one line to one day's file. */
    void write(String network, String target, LocalDate day, String line) {
        rp.post(() -> {
            try {
                Path dir = root.resolve(sanitize(network)).resolve(sanitize(target));
                Files.createDirectories(dir);
                Path file = dir.resolve(DAY.format(day) + ".log");
                Files.writeString(file, line + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ex) {
                LOG.log(Level.FINE, "IRC log write failed", ex);
            }
        });
    }

    /** Drains the write lane (tests and shutdown). */
    public boolean awaitIdle(long timeoutMs) {
        try {
            return rp.post(() -> {
            }).waitFinished(timeoutMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
