package org.nmox.studio.ui.irc.protocol;

/**
 * The IRC numeric replies the client actually routes on, plus a small
 * classifier. IRC servers answer most things with a three-digit command
 * ({@code 001} welcome, {@code 353} a page of channel names, {@code 433}
 * "nickname in use") — several hundred exist, but a client only needs to
 * KNOW a handful and can show the rest as status text. {@link #classify}
 * folds the ones we route into families the engine and UI switch on;
 * everything in 400–599 it doesn't know by name is an {@link Kind#ERROR}
 * (the RFC reserves that range for errors), and everything else is
 * {@link Kind#OTHER}.
 */
public final class Numerics {

    /** 001 — registration accepted; the connection is usable. */
    public static final int RPL_WELCOME = 1;
    /** 332 — a channel's topic text. */
    public static final int RPL_TOPIC = 332;
    /** 333 — who set the topic, and when. */
    public static final int RPL_TOPICWHOTIME = 333;
    /** 353 — one page of a channel's name list. */
    public static final int RPL_NAMREPLY = 353;
    /** 366 — end of the name list. */
    public static final int RPL_ENDOFNAMES = 366;
    /** 372 — one MOTD line. */
    public static final int RPL_MOTD = 372;
    /** 375 — MOTD start. */
    public static final int RPL_MOTDSTART = 375;
    /** 376 — MOTD end. */
    public static final int RPL_ENDOFMOTD = 376;
    /** 311 — WHOIS: user line. */
    public static final int RPL_WHOISUSER = 311;
    /** 312 — WHOIS: server line. */
    public static final int RPL_WHOISSERVER = 312;
    /** 317 — WHOIS: idle time. */
    public static final int RPL_WHOISIDLE = 317;
    /** 318 — WHOIS: end. */
    public static final int RPL_ENDOFWHOIS = 318;
    /** 319 — WHOIS: channel list. */
    public static final int RPL_WHOISCHANNELS = 319;
    /** 433 — nickname already in use (retry with a variant). */
    public static final int ERR_NICKNAMEINUSE = 433;

    /** The routing families {@link #classify} sorts numerics into. */
    public enum Kind {
        /** 001 — registered and ready. */
        WELCOME,
        /** 353 — a page of channel names. */
        NAMES,
        /** 366 — the name list is complete. */
        NAMES_END,
        /** 332 — topic text. */
        TOPIC,
        /** 333 — topic author + timestamp. */
        TOPIC_META,
        /** 433 — pick another nickname. */
        NICK_IN_USE,
        /** 372/375/376 — message-of-the-day lines. */
        MOTD,
        /** 311/312/317/318/319 — WHOIS replies. */
        WHOIS,
        /** any other 4xx/5xx — the RFC's error range. */
        ERROR,
        /** a numeric we show as plain status text. */
        OTHER,
        /** not a numeric at all (PRIVMSG, JOIN, …). */
        NOT_NUMERIC
    }

    private Numerics() {
    }

    /** True when the command is a three-digit numeric reply. */
    public static boolean isNumeric(String command) {
        if (command == null || command.length() != 3) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            if (!Character.isDigit(command.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Sorts a command into its routing family; see {@link Kind}. */
    public static Kind classify(String command) {
        if (!isNumeric(command)) {
            return Kind.NOT_NUMERIC;
        }
        int n = Integer.parseInt(command);
        return switch (n) {
            case RPL_WELCOME -> Kind.WELCOME;
            case RPL_NAMREPLY -> Kind.NAMES;
            case RPL_ENDOFNAMES -> Kind.NAMES_END;
            case RPL_TOPIC -> Kind.TOPIC;
            case RPL_TOPICWHOTIME -> Kind.TOPIC_META;
            case ERR_NICKNAMEINUSE -> Kind.NICK_IN_USE;
            case RPL_MOTD, RPL_MOTDSTART, RPL_ENDOFMOTD -> Kind.MOTD;
            case RPL_WHOISUSER, RPL_WHOISSERVER, RPL_WHOISIDLE,
                 RPL_ENDOFWHOIS, RPL_WHOISCHANNELS -> Kind.WHOIS;
            default -> n >= 400 && n <= 599 ? Kind.ERROR : Kind.OTHER;
        };
    }
}
