package org.nmox.studio.rack.sharing;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * What the Share dialog can work out for the sender before they type: the
 * tools a rack needs, read from its own commands, and a filename from its name.
 * Suggestions only — the sender edits both.
 */
public final class ShareCards {

    /** Wrappers that run another tool: the tool worth naming is the one after them. */
    private static final Set<String> RUNNERS = Set.of("npx", "pnpx", "bunx", "env", "sudo", "time", "nice");

    private ShareCards() {
    }

    /**
     * The first word of every command-like setting that is a bare tool name:
     * {@code npm run build} needs {@code npm}, {@code cargo test} needs
     * {@code cargo}, {@code npx vitest} needs {@code npx}. A path
     * ({@code ./gradlew}), a URL, a glob or a lone word is not a command and
     * names nothing. Order of first appearance, at most twelve.
     */
    public static List<String> suggestRequires(JSONObject patch) {
        Set<String> out = new LinkedHashSet<>();
        JSONArray devices = patch == null ? null : patch.optJSONArray("devices");
        if (devices == null) {
            return List.of();
        }
        for (int i = 0; i < devices.length() && out.size() < 12; i++) {
            JSONObject device = devices.optJSONObject(i);
            JSONObject state = device == null ? null : device.optJSONObject("state");
            if (state == null) {
                continue;
            }
            for (String key : state.keySet()) {
                String tool = toolOf(state.optString(key, ""));
                if (tool != null && out.size() < 12) {
                    out.add(tool);
                }
            }
        }
        return new ArrayList<>(out);
    }

    /** The tool a command line starts with, or null when the value is not a command line. */
    static String toolOf(String value) {
        String v = value == null ? "" : value.strip();
        int space = v.indexOf(' ');
        if (space <= 0) {
            return null; // one word is a setting ("main", "8080"), not a command
        }
        String first = v.substring(0, space);
        if (RUNNERS.contains(first) && !"npx".equals(first) && !"pnpx".equals(first) && !"bunx".equals(first)) {
            return toolOf(v.substring(space + 1));
        }
        return isBareLowerTool(first) ? first : null;
    }

    private static boolean isBareLowerTool(String word) {
        if (word.isEmpty() || !Character.isLetter(word.charAt(0))) {
            return false;
        }
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '+';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /**
     * A filename stem from a rack's name: lower case, letters and digits kept
     * (any script — a rack named in Hindi keeps its name), everything else one
     * hyphen, at most 48 code points, never empty, never a dot-file.
     */
    public static String fileStem(String name) {
        StringBuilder sb = new StringBuilder();
        boolean hyphen = false;
        int kept = 0;
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < n.length() && kept < 48;) {
            int cp = n.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetterOrDigit(cp) || Character.getType(cp) == Character.NON_SPACING_MARK
                    || Character.getType(cp) == Character.COMBINING_SPACING_MARK) {
                if (hyphen && sb.length() > 0) {
                    sb.append('-');
                    kept++;
                }
                hyphen = false;
                sb.appendCodePoint(cp);
                kept++;
            } else {
                hyphen = true;
            }
        }
        return sb.length() == 0 ? "rack" : sb.toString();
    }

    /** {@code "npm, cargo  docker"} as the sender typed it → the list a card holds. */
    public static List<String> splitList(String typed) {
        List<String> out = new ArrayList<>();
        if (typed == null) {
            return out;
        }
        StringBuilder word = new StringBuilder();
        for (int i = 0; i <= typed.length(); i++) {
            char c = i < typed.length() ? typed.charAt(i) : ',';
            if (c == ',' || c == ';' || Character.isWhitespace(c)) {
                if (word.length() > 0) {
                    out.add(word.toString());
                    word.setLength(0);
                }
            } else {
                word.append(c);
            }
        }
        return out;
    }
}
