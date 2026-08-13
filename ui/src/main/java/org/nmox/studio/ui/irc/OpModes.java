package org.nmox.studio.ui.irc;

import java.util.List;

/**
 * The channel-operator toolkit's pure half: WeeChat-style sugar that
 * turns {@code /op alice bob} into the one MODE line IRC actually
 * speaks ({@code MODE #chan +oo alice bob}), and a bare nick in
 * {@code /ban} into the conventional {@code nick!*@*} mask. Pure
 * string-out so every spelling is testable without a socket.
 */
final class OpModes {

    private OpModes() {
    }

    /**
     * One MODE line granting or removing {@code flag} for every nick —
     * the flag letter repeated once per target, exactly as servers
     * expect batched mode changes.
     */
    static String mode(String channel, boolean grant, char flag, List<String> nicks) {
        StringBuilder line = new StringBuilder("MODE ").append(channel).append(' ')
                .append(grant ? '+' : '-');
        line.append(String.valueOf(flag).repeat(nicks.size()));
        for (String nick : nicks) {
            line.append(' ').append(nick);
        }
        return line.toString();
    }

    /**
     * A ban target: a full hostmask passes through untouched; a bare
     * nick becomes {@code nick!*@*}, the conventional nick-ban.
     */
    static String banMask(String target) {
        return target.contains("!") || target.contains("@")
                ? target
                : target + "!*@*";
    }
}
