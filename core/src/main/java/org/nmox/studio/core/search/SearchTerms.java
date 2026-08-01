package org.nmox.studio.core.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How every search box in the product decides whether a thing matches
 * what you typed.
 *
 * <p><b>Why this exists.</b> Until v1.215.0 each search surface did the
 * obvious thing — {@code haystack.toLowerCase().contains(query)} — and
 * the obvious thing is wrong in three ways that a user feels
 * immediately:
 *
 * <ol>
 *   <li><b>Phrases die.</b> {@code "bundle size"} had to appear in that
 *       exact order with that exact spacing. PRISM's own description
 *       reads "Bundle-Size Gate", so the hyphen alone made the device
 *       unfindable by its own name.</li>
 *   <li><b>Plurals die.</b> {@code "logs"} does not occur inside "Log
 *       Follower", so TAIL was unfindable by the word most people would
 *       reach for.</li>
 *   <li><b>Junk survives.</b> {@code contains} matches inside words, so
 *       searching {@code "ai"} returned T<b>AI</b>L and ANVIL
 *       ("ch<b>ai</b>n") alongside the device you wanted.</li>
 * </ol>
 *
 * <p><b>The rule here instead.</b> Split the query into terms and the
 * haystack into words, then require <em>every</em> term to prefix-match
 * <em>some</em> word. Word boundaries are the fix for junk matches;
 * per-term matching is the fix for phrases; a small plural rule is the
 * fix for "logs" vs "Log". Order does not matter, so "size bundle"
 * finds the same device as "bundle size".
 *
 * <p>Pure and dependency-free on purpose: every consumer is a UI class
 * that is hard to test, so the logic they share lives somewhere a plain
 * unit test can reach it.
 */
public final class SearchTerms {

    /**
     * Terms at least this long may also match in the middle of a word.
     *
     * <p>Word-boundary matching is what kills junk hits, but applied to
     * everything it would break a real habit: pasting a fragment of a
     * contract address or a table name. So short terms — where the junk
     * lives, "ai" inside "tail" — must start a word, and longer terms,
     * where a mid-word hit is almost always deliberate, may land
     * anywhere. Three characters is the line: it keeps "sql" finding
     * "PostgreSQL" while "ai" still cannot find "TAIL".
     */
    private static final int MIN_LOOSE_TERM = 3;

    private SearchTerms() {
    }

    /**
     * The query split into search terms, lowercased. Blank input yields
     * an empty list — callers treat that as "match nothing", since an
     * empty Quick Search box should not dump the whole catalog.
     */
    public static List<String> terms(String query) {
        return words(query);
    }

    /**
     * True when every term in {@code query} prefix-matches some word in
     * some haystack. A blank query matches nothing.
     *
     * <p>Haystack entries may be null — callers pass optional fields
     * (a description, a keyword list) without pre-filtering.
     */
    public static boolean matches(String query, String... haystacks) {
        List<String> needles = words(query);
        if (needles.isEmpty()) {
            return false;
        }
        // Split the haystacks once, then test every term against the
        // same word list: N terms x M words, no repeated tokenizing.
        // The raw text is kept alongside for the mid-word fallback.
        List<String> hay = new ArrayList<>();
        StringBuilder raw = new StringBuilder();
        for (String h : haystacks) {
            if (h != null && !h.isEmpty()) {
                hay.addAll(words(h));
                raw.append(h.toLowerCase(Locale.ROOT)).append('\n');
            }
        }
        if (hay.isEmpty()) {
            return false;
        }
        String rawText = raw.toString();
        for (String needle : needles) {
            if (matchesAnyWord(needle, hay)) {
                continue;
            }
            // Mid-word fallback for terms long enough to be deliberate:
            // a pasted address or table-name fragment. CJK terms skip the
            // length gate entirely (v1.216.0): CJK text has no separators
            // — a run like \u524d\u7aef\u9879\u76ee tokenizes as ONE
            // word — and each character carries word-level meaning, so a
            // two-character query is a full query, not junk. The old
            // contains matcher handled these; the length gate regressed
            // them.
            if ((needle.length() >= MIN_LOOSE_TERM || hasCjk(needle))
                    && rawText.contains(needle)) {
                continue;
            }
            return false;
        }
        return true;
    }

    /** True when the term carries any CJK ideograph. */
    private static boolean hasCjk(String term) {
        for (int i = 0; i < term.length(); ) {
            int cp = term.codePointAt(i);
            if (Character.isIdeographic(cp)) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    private static boolean matchesAnyWord(String needle, List<String> hay) {
        String stem = singular(needle);
        for (String word : hay) {
            // Prefix, not contains: "test" finds "tests", but "ai"
            // does not find "tail". (The reverse — "testing" finding
            // "test" — is deliberately NOT offered: the raw fallback
            // requires the literal, and stemming beyond one plural "s"
            // buys little for what it costs in junk.)
            if (word.startsWith(needle) || word.startsWith(stem)) {
                return true;
            }
            // The other direction, so a plural query finds a singular
            // word: "logs" -> "log" matches the word "log".
            if (singular(word).startsWith(stem)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Drops one trailing "s" so plural queries reach singular text.
     * Guarded on length so short words survive ("is" stays "is"), and
     * on a double s so "css" and "class" are left alone.
     */
    private static String singular(String word) {
        if (word.length() >= 4 && word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    /**
     * Breaks text into lowercase words. Anything that is not a letter
     * or digit separates — so {@code -}, {@code _}, {@code .},
     * {@code /} and whitespace all split, which is what makes
     * "Bundle-Size Gate" findable as "bundle size". A lowercase-to-
     * uppercase step also splits, so camelCase identifiers
     * ({@code getUserById}) are searchable by their parts.
     */
    static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        StringBuilder current = new StringBuilder();
        char previous = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                flush(current, out);
                previous = 0;
                continue;
            }
            // camelCase: the uppercase letter starts a new word, but
            // only after a lowercase one — "NPM" must stay whole.
            if (Character.isUpperCase(c) && Character.isLowerCase(previous)) {
                flush(current, out);
            }
            current.append(Character.toLowerCase(c));
            previous = c;
        }
        flush(current, out);
        return out;
    }

    private static void flush(StringBuilder buffer, List<String> out) {
        if (buffer.length() > 0) {
            out.add(buffer.toString().toLowerCase(Locale.ROOT));
            buffer.setLength(0);
        }
    }
}
