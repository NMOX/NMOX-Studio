package org.nmox.studio.apiclient.api;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code {{variable}}} substitution against the active environment -
 * the mechanism that lets one saved request hit localhost, staging,
 * and prod by switching environments instead of editing URLs.
 */
public final class Variables {

    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([^}\\s]+)\\s*}}");

    private Variables() {
    }

    /**
     * Replaces every {@code {{name}}} with its value from the map;
     * unknown variables are left verbatim so a missing one is visible
     * in the request rather than silently blanked.
     */
    public static String resolve(String text, Map<String, String> vars) {
        if (text == null || text.indexOf("{{") < 0 || vars == null) {
            return text == null ? "" : text;
        }
        Matcher m = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = vars.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(
                    value != null ? value : m.group(0)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** The variable names referenced in a piece of text, in order seen. */
    public static java.util.List<String> referenced(String text) {
        java.util.List<String> names = new java.util.ArrayList<>();
        if (text == null) {
            return names;
        }
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            if (!names.contains(m.group(1))) {
                names.add(m.group(1));
            }
        }
        return names;
    }
}
