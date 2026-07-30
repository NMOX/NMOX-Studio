package org.nmox.studio.ui.irc.protocol;

/**
 * Channel-status prefixes on nicks — the {@code @}/{@code +} sigils a
 * NAMES reply decorates names with ({@code ~} owner, {@code &} admin,
 * {@code @} op, {@code %} half-op, {@code +} voice). With the IRCv3
 * {@code multi-prefix} capability the server STACKS them
 * ({@code @+nick} — an op who also has voice), so stripping exactly one
 * character is wrong; this class strips and ranks the whole run. Pure
 * string-in/string-out, shared by the nick list, the completer, and the
 * away-state bookkeeping so they can never disagree about what the bare
 * nick is.
 */
public final class NickPrefix {

    /** Every prefix sigil servers commonly advertise, highest rank first. */
    private static final String SIGILS = "~&@%+";

    private NickPrefix() {
    }

    /** The nick with its entire (possibly stacked) prefix run removed. */
    public static String strip(String display) {
        int i = 0;
        while (i < display.length() && SIGILS.indexOf(display.charAt(i)) >= 0) {
            i++;
        }
        return display.substring(i);
    }

    /**
     * Sort rank for the nick list: ops (owner/admin fold in) 0, half-op
     * 1, voice 2, plain 3. A stacked prefix ranks by its HIGHEST sigil,
     * wherever it appears — {@code +@nick} from a sloppy bridge still
     * ranks as an op.
     */
    public static int rank(String display) {
        int best = 3;
        for (int i = 0; i < display.length(); i++) {
            int r = switch (display.charAt(i)) {
                case '~', '&', '@' -> 0;
                case '%' -> 1;
                case '+' -> 2;
                default -> -1;
            };
            if (r < 0) {
                break; // the prefix run ended
            }
            best = Math.min(best, r);
        }
        return best;
    }
}
