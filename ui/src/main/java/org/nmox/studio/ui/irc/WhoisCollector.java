package org.nmox.studio.ui.irc;

import java.util.ArrayList;
import java.util.List;
import org.nmox.studio.ui.irc.protocol.IrcMessage;

/**
 * Assembles a WHOIS answer from the scattered numerics servers send it
 * as — 311 (user@host + real name), 312 (server), 317 (idle seconds +
 * signon), 319 (channels), 330 (logged-in-as account) — into one
 * {@link WhoisInfo} record delivered when 318 (end of WHOIS) arrives.
 * The window then renders a single tidy card instead of five raw
 * status lines. One collector per network; a new 311 restarts the
 * record so back-to-back {@code /whois} calls don't blend. Pure
 * message-in/record-out, no Swing.
 */
public final class WhoisCollector {

    /**
     * One completed WHOIS answer. Absent fields are {@code ""} (or −1
     * for the idle seconds) — servers vary in which numerics they send.
     *
     * @param nick        who was asked about
     * @param userHost    {@code user@host}
     * @param realName    the real-name (gecos) field
     * @param server      the server the nick is attached to
     * @param serverInfo  that server's descriptive text
     * @param idleSeconds seconds idle, or −1 when the server didn't say
     * @param channels    the channels the nick is visible in
     * @param account     services account (330), {@code ""} when none
     */
    public record WhoisInfo(String nick, String userHost, String realName,
            String server, String serverInfo, long idleSeconds,
            List<String> channels, String account) {

        public WhoisInfo {
            channels = List.copyOf(channels);
        }
    }

    private String nick = "";
    private String userHost = "";
    private String realName = "";
    private String server = "";
    private String serverInfo = "";
    private long idleSeconds = -1;
    private List<String> channels = new ArrayList<>();
    private String account = "";
    private boolean collecting;

    /**
     * Feeds one numeric. Returns the completed info on 318, else
     * {@code null}. Numerics that aren't WHOIS-shaped are ignored, so
     * callers can feed unconditionally.
     */
    public WhoisInfo accept(IrcMessage msg) {
        switch (msg.command()) {
            case "311" -> {
                // :srv 311 me nick user host * :real name
                reset();
                collecting = true;
                nick = msg.param(1);
                userHost = msg.param(2) + "@" + msg.param(3);
                realName = msg.trailing() == null ? "" : msg.trailing();
            }
            case "312" -> {
                if (collecting) {
                    server = msg.param(2);
                    serverInfo = msg.trailing() == null ? "" : msg.trailing();
                }
            }
            case "317" -> {
                if (collecting) {
                    try {
                        idleSeconds = Long.parseLong(msg.param(2));
                    } catch (NumberFormatException notANumber) {
                        idleSeconds = -1;
                    }
                }
            }
            case "319" -> {
                if (collecting && msg.trailing() != null) {
                    for (String c : msg.trailing().split(" ")) {
                        if (!c.isEmpty()) {
                            channels.add(c);
                        }
                    }
                }
            }
            case "330" -> {
                // :srv 330 me nick account :is logged in as
                if (collecting) {
                    account = msg.param(2);
                }
            }
            case "318" -> {
                if (collecting) {
                    WhoisInfo done = new WhoisInfo(nick, userHost, realName,
                            server, serverInfo, idleSeconds, channels, account);
                    reset();
                    return done;
                }
            }
            default -> {
                // not ours
            }
        }
        return null;
    }

    /** True when a 311 opened a record that hasn't seen its 318 yet. */
    public boolean collecting() {
        return collecting;
    }

    private void reset() {
        nick = "";
        userHost = "";
        realName = "";
        server = "";
        serverInfo = "";
        idleSeconds = -1;
        channels = new ArrayList<>();
        account = "";
        collecting = false;
    }

    /** {@code 3725} → {@code "1h 2m 5s"}; {@code -1} → {@code ""}. */
    public static String formatIdle(long seconds) {
        if (seconds < 0) {
            return "";
        }
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) {
            sb.append(h).append("h ");
        }
        if (h > 0 || m > 0) {
            sb.append(m).append("m ");
        }
        sb.append(s).append("s");
        return sb.toString();
    }

    /** The card the transcript renders: one line per known fact. */
    public static List<String> cardLines(WhoisInfo info) {
        List<String> out = new ArrayList<>();
        out.add("── whois " + info.nick() + " ──");
        out.add("  " + info.userHost()
                + (info.realName().isEmpty() ? "" : " (" + info.realName() + ")"));
        if (!info.server().isEmpty()) {
            out.add("  server: " + info.server()
                    + (info.serverInfo().isEmpty() ? "" : " — " + info.serverInfo()));
        }
        if (!info.channels().isEmpty()) {
            out.add("  channels: " + String.join(" ", info.channels()));
        }
        if (info.idleSeconds() >= 0) {
            out.add("  idle: " + formatIdle(info.idleSeconds()));
        }
        if (!info.account().isEmpty()) {
            out.add("  logged in as: " + info.account());
        }
        return out;
    }
}
