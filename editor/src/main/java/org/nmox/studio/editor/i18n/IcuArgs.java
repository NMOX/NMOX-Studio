package org.nmox.studio.editor.i18n;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The argument set of a translated message, across the dialects a web
 * project's catalogs actually use (v2.177.0): i18next's {@code {{name}}}
 * (with its {@code {{- name}}} unescaped and {@code {{name, format}}}
 * formatted forms), the ICU / vue-i18n / Lingui {@code {name}} and
 * {@code {0}}, vue-i18n's Ruby-style {@code %{name}}, printf's
 * {@code %s} / {@code %d}, and ICU's typed arguments
 * {@code {count, plural, one {…} other {…}}}, whose NAME is collected
 * and whose option bodies are skipped whole — a nested {@code {name}}
 * inside a plural branch is that branch's business, and a translator
 * who reorders branches has not changed the argument set.
 *
 * <p>This is {@code LocaleBundleParityTest}'s placeholder-set law
 * generalised: the one thing about a translation that IS a bug rather
 * than a taste is a value that will format with a different set of
 * arguments than its source.
 */
public final class IcuArgs {

    private IcuArgs() {
    }

    private static final Pattern PRINTF = Pattern.compile("%[0-9$]*[sdif]");
    private static final Pattern RUBY = Pattern.compile("%\\{\\s*([^{}\\s]+)\\s*\\}");
    private static final Set<String> ICU_TYPES =
            Set.of("plural", "select", "selectordinal");

    /**
     * Every argument NAME the value references, sorted. An unbalanced or
     * empty brace pair contributes nothing — a stray {@code {} } in prose
     * is not an argument, and refusing to guess keeps the mismatch check
     * from inventing a difference.
     */
    public static Set<String> names(String value) {
        Set<String> out = new TreeSet<>();
        if (value == null) {
            return out;
        }
        Matcher ruby = RUBY.matcher(value);
        while (ruby.find()) {
            out.add(ruby.group(1));
        }
        Matcher printf = PRINTF.matcher(value);
        while (printf.find()) {
            out.add(printf.group());
        }
        int i = 0;
        int n = value.length();
        while (i < n) {
            char c = value.charAt(i);
            if (c == '\'' && i + 1 < n && value.charAt(i + 1) == '{') {
                // ICU quoting: '{' is a literal brace, not an argument
                int close = value.indexOf('\'', i + 1);
                i = close < 0 ? n : close + 1;
                continue;
            }
            if (c == '%' && i + 1 < n && value.charAt(i + 1) == '{') {
                i += 2;               // the Ruby form was read above
                continue;
            }
            if (c != '{') {
                i++;
                continue;
            }
            boolean doubled = i + 1 < n && value.charAt(i + 1) == '{';
            int start = doubled ? i + 2 : i + 1;
            int end = matching(value, i);
            if (end < 0) {
                break;                // unbalanced: the tail is prose
            }
            String inner = value.substring(start, doubled ? end - 1 : end);
            String name = argumentName(inner);
            if (!name.isEmpty()) {
                out.add(name);
            }
            i = end + 1;
        }
        return out;
    }

    /**
     * True when the value carries an ICU typed argument (a
     * {@code plural}, {@code select} or {@code selectordinal}) — a value
     * that legitimately differs from its source in every language,
     * because the categories are the language's own.
     */
    public static boolean hasIcu(String value) {
        if (value == null) {
            return false;
        }
        int i = 0;
        int n = value.length();
        while (i < n) {
            if (value.charAt(i) != '{') {
                i++;
                continue;
            }
            int end = matching(value, i);
            if (end < 0) {
                return false;
            }
            String inner = value.substring(i + 1, end);
            String[] parts = inner.split(",", 3);
            if (parts.length >= 2 && ICU_TYPES.contains(parts[1].strip())) {
                return true;
            }
            i = end + 1;
        }
        return false;
    }

    /**
     * The name before the first comma of an argument body, with the
     * i18next unescape sigil and any surrounding space dropped; empty
     * when the body names nothing usable (a bare {@code {}}, a body
     * opening with a brace).
     */
    private static String argumentName(String inner) {
        int comma = inner.indexOf(',');
        String head = (comma < 0 ? inner : inner.substring(0, comma)).strip();
        if (head.startsWith("-")) {
            head = head.substring(1).strip();
        }
        for (int k = 0; k < head.length(); k++) {
            char c = head.charAt(k);
            if (Character.isWhitespace(c) || c == '{' || c == '}' || c == '"' || c == '\'') {
                return "";
            }
        }
        return head;
    }

    /** The index of the brace closing the one at {@code open}, or -1. */
    private static int matching(String s, int open) {
        int depth = 0;
        for (int k = open; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return k;
                }
            }
        }
        return -1;
    }
}
