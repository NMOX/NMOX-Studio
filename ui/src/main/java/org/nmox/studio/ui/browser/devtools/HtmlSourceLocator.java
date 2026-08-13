package org.nmox.studio.ui.browser.devtools;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.nmox.studio.ui.browser.devtools.DomSnapshotParser.DomNode;

/**
 * Maps a live DOM element back to the line in the HTML source that
 * produced it — the second half of inspect-to-source (v1.357.0).
 *
 * <p>Two strategies, honest about their limits. An element with an id
 * is found by its id attribute (ids are unique by contract; a page
 * that repeats one gets the first, which is what the browser's own
 * getElementById would say). An element without an id is found as the
 * Nth occurrence of its start tag, where N is how many same-tag
 * elements precede it in the DOM snapshot's document order — for
 * hand-written HTML, source order IS document order. When the source
 * holds fewer occurrences than the DOM (script-generated content),
 * this returns -1 and the caller says "not in the source" instead of
 * jumping somewhere wrong.
 *
 * <p>Comments and script/style bodies are neutralized before counting
 * (replaced character-for-character so line numbers stay true): a
 * {@code <div>} inside a comment or a JS string is not an element.
 */
public final class HtmlSourceLocator {

    private HtmlSourceLocator() {
    }

    /**
     * The 1-based source line for {@code target}, or -1 when the
     * element cannot be honestly located.
     */
    public static int lineOf(String html, DomNode target, DomNode root) {
        if (html == null || html.isEmpty() || target == null || target.isPlaceholder()) {
            return -1;
        }
        String neutral = neutralize(html);
        if (!target.id.isEmpty()) {
            int at = idOffset(neutral, target.id);
            if (at >= 0) {
                return lineAt(html, at);
            }
            // fall through: an id assigned by SCRIPT exists in the DOM
            // but not in the source — the nth-tag walk may still find
            // the element itself
        }
        int nth = precedingSameTag(root, target);
        if (nth < 0) {
            return -1;
        }
        int at = nthTagOffset(neutral, target.tag, nth);
        return at >= 0 ? lineAt(html, at) : -1;
    }

    /** How many same-tag elements precede target in document order; -1 if absent. */
    static int precedingSameTag(DomNode root, DomNode target) {
        if (root == null) {
            return -1;
        }
        int count = 0;
        Deque<DomNode> work = new ArrayDeque<>();
        work.push(root);
        while (!work.isEmpty()) {
            DomNode n = work.pop();
            if (n.path.equals(target.path) && n.tag.equals(target.tag)) {
                return count;
            }
            if (n.tag.equals(target.tag)) {
                count++;
            }
            // pre-order: push children in reverse so the first child pops first
            for (int i = n.children.size() - 1; i >= 0; i--) {
                DomNode child = n.children.get(i);
                if (!child.isPlaceholder()) {
                    work.push(child);
                }
            }
        }
        return -1;
    }

    /** Offset of the (nth+1)-th {@code <tag} start tag, or -1. */
    static int nthTagOffset(String neutral, String tag, int nth) {
        Pattern p = Pattern.compile("<" + Pattern.quote(tag) + "(?=[\\s/>])",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(neutral);
        int seen = 0;
        while (m.find()) {
            if (seen == nth) {
                return m.start();
            }
            seen++;
        }
        return -1;
    }

    /** Offset of the start tag carrying id="…"/id='…'/id=…, or -1. */
    static int idOffset(String neutral, String id) {
        Pattern p = Pattern.compile(
                "\\bid\\s*=\\s*([\"'])" + Pattern.quote(id) + "\\1|\\bid\\s*=\\s*"
                + Pattern.quote(id) + "(?=[\\s/>])",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(neutral);
        if (!m.find()) {
            return -1;
        }
        // back up to the start tag the attribute belongs to
        int lt = neutral.lastIndexOf('<', m.start());
        return lt >= 0 ? lt : m.start();
    }

    /**
     * Blanks HTML comments and script/style element BODIES (their
     * content is not parsed as elements), preserving every newline so
     * offsets translate to the original's line numbers.
     */
    static String neutralize(String html) {
        StringBuilder out = new StringBuilder(html);
        blankRanges(out, "<!--", "-->", true);
        blankElementBodies(out, "script");
        blankElementBodies(out, "style");
        return out.toString();
    }

    private static void blankRanges(StringBuilder sb, String open, String close, boolean includeMarkers) {
        int from = 0;
        while (true) {
            int start = indexOfIgnoreCase(sb, open, from);
            if (start < 0) {
                return;
            }
            int end = indexOfIgnoreCase(sb, close, start + open.length());
            int stop = end < 0 ? sb.length() : end + close.length();
            blank(sb, includeMarkers ? start : start + open.length(),
                    includeMarkers ? stop : (end < 0 ? sb.length() : end));
            from = stop;
        }
    }

    private static void blankElementBodies(StringBuilder sb, String tag) {
        int from = 0;
        while (true) {
            int open = indexOfIgnoreCase(sb, "<" + tag, from);
            if (open < 0) {
                return;
            }
            int gt = sb.indexOf(">", open);
            if (gt < 0) {
                return;
            }
            int close = indexOfIgnoreCase(sb, "</" + tag, gt + 1);
            if (close < 0) {
                blank(sb, gt + 1, sb.length());
                return;
            }
            blank(sb, gt + 1, close);
            from = close + tag.length() + 2;
        }
    }

    private static int indexOfIgnoreCase(StringBuilder sb, String needle, int from) {
        String hay = sb.toString().toLowerCase(Locale.ROOT);
        return hay.indexOf(needle.toLowerCase(Locale.ROOT), Math.max(0, from));
    }

    private static void blank(StringBuilder sb, int from, int to) {
        for (int i = from; i < to && i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c != '\n' && c != '\r') {
                sb.setCharAt(i, ' ');
            }
        }
    }

    /** 1-based line of a character offset. */
    static int lineAt(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
