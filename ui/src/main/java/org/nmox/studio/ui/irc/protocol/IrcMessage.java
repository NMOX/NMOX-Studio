package org.nmox.studio.ui.irc.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One parsed IRC protocol line — the pure heart of the IRC client
 * (v1.204.0). A raw line like
 * {@code @time=2026-01-01T00:00:00Z :nick!user@host PRIVMSG #chan :hello}
 * becomes tags / prefix / command / params, and {@link #render()} turns
 * it back into the exact wire form (the codec law: {@code render(parse(x))}
 * is byte-identical for canonical input, pinned by tests).
 *
 * <p>The grammar is RFC 1459/2812 plus the IRCv3 message-tags extension
 * (the leading {@code @key=value;key2} block, with the spec's escaping:
 * {@code \:} → {@code ;}, {@code \s} → space, {@code \\} → {@code \},
 * {@code \r}/{@code \n} → CR/LF). Edge cases the parser must not fumble,
 * each with a test vector: no prefix, colons INSIDE a trailing param,
 * an EMPTY trailing param ({@code TOPIC #c :}), and the RFC 1459 rule
 * that after 14 middle params the rest of the line is the final param
 * even without a colon.
 *
 * <p>This class is deliberately free of sockets, Swing, and NetBeans
 * APIs — plain data in, plain data out — so the exhaustive unit tests
 * that guard it run in microseconds with no platform harness.
 */
public final class IrcMessage {

    /** RFC 1459 §2.3.1: a message may carry at most 15 parameters. */
    private static final int MAX_MIDDLE_PARAMS = 14;

    private final Map<String, String> tags;
    private final String prefix;
    private final String command;
    private final List<String> params;
    private final boolean trailing;

    private IrcMessage(Map<String, String> tags, String prefix, String command,
            List<String> params, boolean trailing) {
        this.tags = Collections.unmodifiableMap(new LinkedHashMap<>(tags));
        this.prefix = prefix;
        this.command = command;
        this.params = List.copyOf(params);
        this.trailing = trailing;
    }

    /** Builds a message to send: no tags, no prefix (the server adds ours). */
    public static IrcMessage of(String command, String... params) {
        List<String> p = List.of(params);
        boolean needsTrailing = !p.isEmpty() && wantsTrailing(p.get(p.size() - 1));
        return new IrcMessage(Map.of(), null, command, p, needsTrailing);
    }

    private static boolean wantsTrailing(String last) {
        return last.isEmpty() || last.indexOf(' ') >= 0 || last.startsWith(":");
    }

    /**
     * Parses one raw wire line (without its CRLF). Throws
     * {@link IllegalArgumentException} on a line with no command — the
     * engine treats that as a protocol hiccup to log, never a crash.
     */
    public static IrcMessage parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String rest = raw;

        Map<String, String> tags = new LinkedHashMap<>();
        if (rest.startsWith("@")) {
            int sp = rest.indexOf(' ');
            if (sp < 0) {
                throw new IllegalArgumentException("tags but no command: " + raw);
            }
            parseTags(rest.substring(1, sp), tags);
            rest = rest.substring(sp + 1);
        }

        rest = stripLeadingSpaces(rest);
        String prefix = null;
        if (rest.startsWith(":")) {
            int sp = rest.indexOf(' ');
            if (sp < 0) {
                throw new IllegalArgumentException("prefix but no command: " + raw);
            }
            prefix = rest.substring(1, sp);
            rest = rest.substring(sp + 1);
        }

        String command = null;
        List<String> params = new ArrayList<>();
        boolean trailing = false;
        rest = stripLeadingSpaces(rest);
        while (!rest.isEmpty()) {
            if (command != null && rest.startsWith(":")) {
                // trailing: everything after the colon, spaces and colons included
                params.add(rest.substring(1));
                trailing = true;
                break;
            }
            if (command != null && params.size() >= MAX_MIDDLE_PARAMS) {
                // RFC 1459: the 15th parameter is the rest of the line,
                // colon or not
                params.add(rest);
                trailing = true;
                break;
            }
            int sp = rest.indexOf(' ');
            String token = sp < 0 ? rest : rest.substring(0, sp);
            rest = sp < 0 ? "" : stripLeadingSpaces(rest.substring(sp + 1));
            if (command == null) {
                command = token;
            } else {
                params.add(token);
            }
        }
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("no command in line: " + raw);
        }
        return new IrcMessage(tags, prefix, command, params, trailing);
    }

    private static String stripLeadingSpaces(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == ' ') {
            i++;
        }
        return i == 0 ? s : s.substring(i);
    }

    private static void parseTags(String block, Map<String, String> into) {
        for (String pair : block.split(";")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq < 0) {
                into.put(pair, "");
            } else {
                into.put(pair.substring(0, eq), unescapeTagValue(pair.substring(eq + 1)));
            }
        }
    }

    /** IRCv3 tag-value unescaping; a lone trailing backslash is dropped per spec. */
    static String unescapeTagValue(String v) {
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (i + 1 >= v.length()) {
                break; // dangling backslash: dropped
            }
            char n = v.charAt(++i);
            switch (n) {
                case ':' -> sb.append(';');
                case 's' -> sb.append(' ');
                case '\\' -> sb.append('\\');
                case 'r' -> sb.append('\r');
                case 'n' -> sb.append('\n');
                default -> sb.append(n); // unknown escape: the char itself
            }
        }
        return sb.toString();
    }

    /** IRCv3 tag-value escaping — the exact inverse of {@link #unescapeTagValue}. */
    static String escapeTagValue(String v) {
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case ';' -> sb.append("\\:");
                case ' ' -> sb.append("\\s");
                case '\\' -> sb.append("\\\\");
                case '\r' -> sb.append("\\r");
                case '\n' -> sb.append("\\n");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Renders the wire form (no CRLF appended — the socket layer owns that). */
    public String render() {
        StringBuilder sb = new StringBuilder(64);
        if (!tags.isEmpty()) {
            sb.append('@');
            boolean first = true;
            for (Map.Entry<String, String> e : tags.entrySet()) {
                if (!first) {
                    sb.append(';');
                }
                first = false;
                sb.append(e.getKey());
                if (!e.getValue().isEmpty()) {
                    sb.append('=').append(escapeTagValue(e.getValue()));
                }
            }
            sb.append(' ');
        }
        if (prefix != null) {
            sb.append(':').append(prefix).append(' ');
        }
        sb.append(command);
        for (int i = 0; i < params.size(); i++) {
            String p = params.get(i);
            boolean last = i == params.size() - 1;
            sb.append(' ');
            if (last && (trailing || wantsTrailing(p))) {
                sb.append(':');
            }
            sb.append(p);
        }
        return sb.toString();
    }

    /** IRCv3 message tags, in wire order; empty map when the line had none. */
    public Map<String, String> tags() {
        return tags;
    }

    /** The prefix without its leading colon, or {@code null} when absent. */
    public String prefix() {
        return prefix;
    }

    /** The command, verbatim: {@code PRIVMSG}, {@code 001}, {@code PING}, … */
    public String command() {
        return command;
    }

    /** All parameters, the trailing one (if any) last. Unmodifiable. */
    public List<String> params() {
        return params;
    }

    /** Parameter {@code i}, or {@code ""} when the line has fewer. */
    public String param(int i) {
        return i >= 0 && i < params.size() ? params.get(i) : "";
    }

    /** The trailing parameter, or {@code null} when the line carried none. */
    public String trailing() {
        return trailing && !params.isEmpty() ? params.get(params.size() - 1) : null;
    }

    /** True when the final param was (or must be) sent in {@code :trailing} form. */
    public boolean hasTrailing() {
        return trailing;
    }

    /**
     * The nick half of a {@code nick!user@host} prefix. For a bare server
     * prefix ({@code irc.example.net}) this is the server name — callers
     * that care can check {@link #user()} for {@code null}.
     */
    public String nick() {
        if (prefix == null) {
            return null;
        }
        int bang = prefix.indexOf('!');
        int at = prefix.indexOf('@');
        int end = prefix.length();
        if (bang >= 0) {
            end = bang;
        } else if (at >= 0) {
            end = at;
        }
        return prefix.substring(0, end);
    }

    /** The user half of {@code nick!user@host}, or {@code null}. */
    public String user() {
        if (prefix == null) {
            return null;
        }
        int bang = prefix.indexOf('!');
        if (bang < 0) {
            return null;
        }
        int at = prefix.indexOf('@', bang);
        return at < 0 ? prefix.substring(bang + 1) : prefix.substring(bang + 1, at);
    }

    /** The host half of {@code nick!user@host}, or {@code null}. */
    public String host() {
        if (prefix == null) {
            return null;
        }
        int at = prefix.indexOf('@');
        return at < 0 ? null : prefix.substring(at + 1);
    }

    @Override
    public String toString() {
        return render();
    }
}
