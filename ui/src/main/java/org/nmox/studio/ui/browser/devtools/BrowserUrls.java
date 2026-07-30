package org.nmox.studio.ui.browser.devtools;

/**
 * Small pure helpers for the browser chrome: URL-bar normalization
 * (typed hostnames get an https:// default) and tab-title shaping
 * (page titles are page-authored, i.e. untrusted — capped at
 * {@link #TITLE_CAP} chars, blank falls back to "Browser").
 */
public final class BrowserUrls {

    /** Max chars of page title shown on the tab. */
    public static final int TITLE_CAP = 30;

    /** The tab name when the page has none. */
    public static final String FALLBACK_TITLE = "Browser";

    private BrowserUrls() {
    }

    /**
     * What the URL field's text should load: trimmed; a bare host or
     * path gains {@code https://}; anything already carrying a scheme
     * ({@code http:}, {@code https:}, {@code file:}, {@code about:},
     * …) passes through; blank input answers null (load nothing).
     */
    public static String normalize(String typed) {
        if (typed == null) {
            return null;
        }
        String t = typed.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (hasScheme(t)) {
            return t;
        }
        return "https://" + t;
    }

    /**
     * True when the text starts with a URI scheme ({@code name:}) —
     * but {@code host:port} shapes like {@code localhost:3000} are NOT
     * schemes (a digit right after the colon means port).
     */
    static boolean hasScheme(String t) {
        int colon = t.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        if (colon + 1 < t.length() && Character.isDigit(t.charAt(colon + 1))) {
            return false; // host:port, not a scheme
        }
        for (int i = 0; i < colon; i++) {
            char c = t.charAt(i);
            boolean schemeChar = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (i > 0 && ((c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.'));
            if (!schemeChar) {
                return false;
            }
        }
        return true;
    }

    /** The tab display name for a page title: capped, blank → "Browser". */
    public static String tabTitle(String pageTitle) {
        if (pageTitle == null || pageTitle.isBlank()) {
            return FALLBACK_TITLE;
        }
        String t = pageTitle.strip();
        if (t.length() > TITLE_CAP) {
            // code-point-safe cut (the v1.149.0 cap law: never split a
            // surrogate pair mid-emoji)
            int cut = TITLE_CAP;
            if (Character.isHighSurrogate(t.charAt(cut - 1))) {
                cut--;
            }
            t = t.substring(0, cut) + "…";
        }
        return t;
    }
}
