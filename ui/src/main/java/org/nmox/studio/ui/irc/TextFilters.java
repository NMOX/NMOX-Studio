package org.nmox.studio.ui.irc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * WeeChat's custom {@code /filter} regexes, the honest subset (v2.10.0,
 * tranche 2 of the WeeChat-power arc): named, per-channel-or-global,
 * user-authored regular expressions that HIDE matching chat lines from
 * the transcript. Pure and Swing-free like {@link SmartFilter}, and it
 * sits at the same lawful place — the transcript append decision only:
 * nick-list bookkeeping and the engine-side log tap are upstream, so a
 * hidden line is never lost data, and a line the user explicitly
 * filtered produces NO signal at all (no unread, no mention, no tab).
 *
 * <p>Deliberate bounds: a regex longer than {@value #MAX_REGEX} chars
 * is refused (the line being tested is protocol-capped, so runaway
 * patterns are the remaining ReDoS surface and the user's own — the cap
 * keeps them honest), matching is case-insensitive like WeeChat's, and
 * the tested text is the displayed form {@code <nick> body} so a filter
 * can target a nick, a phrase, or both.
 */
final class TextFilters {

    /** One named filter; {@code scope} is "*" or a channel name. */
    record Filter(String name, String scope, String regex, boolean enabled) {

        /** The one persisted spelling: {@code enabled|scope|regex}
         *  (regex last — it may itself contain the separator). */
        String stringForm() {
            return (enabled ? "1" : "0") + "|" + scope + "|" + regex;
        }
    }

    static final int MAX_REGEX = 200;

    private final Map<String, Filter> byName = new LinkedHashMap<>();
    private final Map<String, Pattern> compiled = new LinkedHashMap<>();

    /**
     * Adds a filter; returns null on success, else the human reason
     * (duplicate name, over-long or malformed regex). The pattern
     * compiles HERE so a bad one never enters the table.
     */
    String add(String name, String scope, String regex, boolean enabled) {
        String key = name.toLowerCase(Locale.ROOT);
        if (key.isEmpty() || scope.isEmpty() || regex.isEmpty()) {
            return "usage: /filter add <name> <#channel|*> <regex>";
        }
        if (byName.containsKey(key)) {
            return "a filter named '" + key + "' already exists (/filter del " + key + " first)";
        }
        if (regex.length() > MAX_REGEX) {
            return "regex longer than " + MAX_REGEX + " chars refused";
        }
        Pattern p;
        try {
            p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException ex) {
            return "bad regex: " + ex.getDescription();
        }
        byName.put(key, new Filter(key, scope, regex, enabled));
        compiled.put(key, p);
        return null;
    }

    /** True when the name existed and is gone now. */
    boolean remove(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        compiled.remove(key);
        return byName.remove(key) != null;
    }

    /** Flips one filter; returns false for an unknown name. */
    boolean setEnabled(String name, boolean on) {
        String key = name.toLowerCase(Locale.ROOT);
        Filter f = byName.get(key);
        if (f == null) {
            return false;
        }
        byName.put(key, new Filter(f.name(), f.scope(), f.regex(), on));
        return true;
    }

    List<Filter> list() {
        return List.copyOf(byName.values());
    }

    /**
     * The verdict: hide {@code line} in {@code channel} when any
     * ENABLED filter whose scope covers the channel matches. Scope "*"
     * covers everything, else a case-insensitive channel name match.
     */
    boolean hides(String channel, String line) {
        for (Filter f : byName.values()) {
            if (!f.enabled()) {
                continue;
            }
            if (!f.scope().equals("*") && !f.scope().equalsIgnoreCase(channel)) {
                continue;
            }
            if (compiled.get(f.name()).matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rebuilds one filter from its {@link Filter#stringForm}; malformed
     * or no-longer-compiling forms return false and change nothing — a
     * hand-edited store loses that filter, never the client.
     */
    boolean addFromStringForm(String name, String form) {
        String[] parts = form.split("\\|", 3);
        if (parts.length != 3) {
            return false;
        }
        return add(name, parts[1], parts[2], "1".equals(parts[0])) == null;
    }

    /** The filters as name → stringForm, for the preferences store. */
    Map<String, String> stringForms() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Filter f : byName.values()) {
            out.put(f.name(), f.stringForm());
        }
        return out;
    }

    /** Matching lines for {@code /lastlog}: the LAST {@code limit}
     *  transcript lines containing {@code pattern} (case-insensitive
     *  substring — a search, not a filter), oldest first. */
    static List<String> lastlog(String transcript, String pattern, int limit) {
        List<String> hits = new ArrayList<>();
        String needle = pattern.toLowerCase(Locale.ROOT);
        for (String line : transcript.split("\n")) {
            if (line.toLowerCase(Locale.ROOT).contains(needle)) {
                hits.add(line);
            }
        }
        int from = Math.max(0, hits.size() - Math.max(1, limit));
        return List.copyOf(hits.subList(from, hits.size()));
    }
}
