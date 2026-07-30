package org.nmox.studio.ui.irc;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the {@code http(s)://} URLs inside a chat line so the
 * transcript can render them as clickable links. Deliberately simple
 * rules that match how people actually paste links into IRC: a URL runs
 * from its scheme to the next whitespace, then sheds trailing
 * punctuation — the {@code .} in "see https://example.com." belongs to
 * the sentence, not the link, and the {@code )} in
 * "(https://example.com)" closes the parenthesis. Pure
 * text-in/ranges-out; the window paints the ranges underlined and
 * routes clicks to the in-app browser.
 */
public final class UrlDetector {

    /** Punctuation a URL sheds from its end (sentence chrome, not address). */
    private static final String TRAILING = ".,;:!?)";

    private UrlDetector() {
    }

    /** One URL's position in the scanned text: {@code [start, end)}. */
    public record Range(int start, int end) {

        /** The URL text itself, cut from {@code source}. */
        public String of(String source) {
            return source.substring(start, end);
        }
    }

    /** Every URL range in {@code text}, left to right; empty when none. */
    public static List<Range> find(String text) {
        List<Range> out = new ArrayList<>(2);
        if (text == null || text.isEmpty()) {
            return out;
        }
        int i = 0;
        while (i < text.length()) {
            int at = indexOfScheme(text, i);
            if (at < 0) {
                return out;
            }
            int end = at;
            while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
                end++;
            }
            while (end > at && TRAILING.indexOf(text.charAt(end - 1)) >= 0) {
                end--;
            }
            // a bare scheme with nothing after it is not a link
            int schemeLen = text.startsWith("https://", at) ? 8 : 7;
            if (end > at + schemeLen) {
                out.add(new Range(at, end));
            }
            i = Math.max(end, at + schemeLen);
        }
        return out;
    }

    private static int indexOfScheme(String text, int from) {
        int http = text.indexOf("http://", from);
        int https = text.indexOf("https://", from);
        if (http < 0) {
            return https;
        }
        if (https < 0) {
            return http;
        }
        return Math.min(http, https);
    }
}
