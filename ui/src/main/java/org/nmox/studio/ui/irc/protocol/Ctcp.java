package org.nmox.studio.ui.irc.protocol;

/**
 * CTCP — the "client-to-client protocol" that hides inside ordinary
 * PRIVMSG/NOTICE text: a payload wrapped in {@code ^A} bytes, like
 * {@code ^AACTION waves^A} (where ^A is 0x01) (what {@code /me waves} sends) or
 * {@code ^AVERSION^A} (another client asking what software we
 * run). This class only detects, extracts, and wraps — deciding how to
 * ANSWER a CTCP lives in the engine ({@code IrcClient} auto-replies to
 * VERSION and PING), and how to RENDER one ({@code * nick waves}) lives
 * in the UI. Pure text-in/text-out, exhaustively unit-tested.
 *
 * @param command  the CTCP verb, upper-cased by convention on the wire
 *                 ({@code ACTION}, {@code VERSION}, {@code PING})
 * @param argument the rest of the payload, {@code ""} when there is none
 */
public record Ctcp(String command, String argument) {

    /** The CTCP delimiter byte, 0x01. */
    public static final char DELIMITER = '\u0001';

    /** The verb {@code /me} rides on. */
    public static final String ACTION = "ACTION";
    /** The "what client are you" query verb. */
    public static final String VERSION = "VERSION";
    /** The round-trip-time query verb (echo the argument back). */
    public static final String PING = "PING";

    /** True when a PRIVMSG/NOTICE body is a CTCP payload. */
    public static boolean isCtcp(String text) {
        return text != null && text.length() >= 2 && text.charAt(0) == DELIMITER;
    }

    /**
     * Extracts the CTCP inside {@code text}, or {@code null} when the
     * text is plain chat. Tolerates a missing closing delimiter (some
     * clients omit it).
     */
    public static Ctcp extract(String text) {
        if (!isCtcp(text)) {
            return null;
        }
        String body = text.substring(1);
        if (!body.isEmpty() && body.charAt(body.length() - 1) == DELIMITER) {
            body = body.substring(0, body.length() - 1);
        }
        int sp = body.indexOf(' ');
        if (sp < 0) {
            return new Ctcp(body, "");
        }
        return new Ctcp(body.substring(0, sp), body.substring(sp + 1));
    }

    /** Wraps a verb + argument into the wire payload for a PRIVMSG/NOTICE body. */
    public static String wrap(String command, String argument) {
        StringBuilder sb = new StringBuilder(command.length() + argument.length() + 3);
        sb.append(DELIMITER).append(command);
        if (!argument.isEmpty()) {
            sb.append(' ').append(argument);
        }
        return sb.append(DELIMITER).toString();
    }

    /** The PRIVMSG body {@code /me action} sends. */
    public static String action(String action) {
        return wrap(ACTION, action);
    }
}
