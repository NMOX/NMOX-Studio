package org.nmox.studio.ui.browser.devtools;

import java.util.Locale;

/**
 * WCAG 2.x contrast for the DevTools DOM pane (v1.227.0, the Senior
 * Web Designer pass): given an element's computed {@code color} and
 * {@code background-color}, the ratio and the pass/fail verdicts a
 * designer actually checks (AA 4.5:1 normal / 3:1 large, AAA 7:1
 * normal / 4.5:1 large). Pure and unit-tested.
 *
 * <p>Input is the browser's computed-style serialization, which is
 * always {@code rgb(r, g, b)} or {@code rgba(r, g, b, a)} — this
 * class deliberately parses ONLY that shape (authored forms like hex
 * or hsl never appear in computed values). A fully transparent
 * background yields no verdict: the real backdrop is some ancestor's,
 * and guessing would report a contrast the page doesn't have.
 */
public final class WcagContrast {

    /** One verdict: the ratio and the four WCAG thresholds. */
    public record Verdict(double ratio, boolean aaNormal, boolean aaLarge,
            boolean aaaNormal, boolean aaaLarge) {

        /** "4.54:1 — AA pass, AAA fail (normal text)" style summary. */
        public String summary() {
            return String.format(Locale.ROOT,
                    "%.2f:1 — AA %s, AAA %s (normal text); AA %s, AAA %s (large text)",
                    ratio, pass(aaNormal), pass(aaaNormal),
                    pass(aaLarge), pass(aaaLarge));
        }

        private static String pass(boolean ok) {
            return ok ? "pass" : "FAIL";
        }
    }

    private WcagContrast() {
    }

    /**
     * The verdict for computed {@code color} over computed
     * {@code background-color}, or null when either is missing,
     * unparseable, or the background is transparent.
     */
    public static Verdict of(String colorCss, String backgroundCss) {
        int[] fg = parseRgb(colorCss);
        int[] bg = parseRgb(backgroundCss);
        if (fg == null || bg == null) {
            return null;
        }
        double lighter = Math.max(luminance(fg), luminance(bg));
        double darker = Math.min(luminance(fg), luminance(bg));
        double ratio = (lighter + 0.05) / (darker + 0.05);
        // verdicts use the TRUE ratio (that's what WCAG compares), and
        // the displayed value truncates rather than rounds so the two
        // never contradict: a true 4.4987 used to display as
        // "4.50:1 — AA FAIL" (v1.234.0 review); it now shows 4.49
        return new Verdict(Math.floor(ratio * 100.0) / 100.0,
                ratio >= 4.5, ratio >= 3.0, ratio >= 7.0, ratio >= 4.5);
    }

    /**
     * Parses the computed-style {@code rgb(r, g, b)} /
     * {@code rgba(r, g, b, a)} serialization; null for anything else,
     * including a fully transparent rgba (alpha 0 — the element shows
     * an ancestor's backdrop, not this value).
     */
    static int[] parseRgb(String css) {
        if (css == null) {
            return null;
        }
        String s = css.trim().toLowerCase(Locale.ROOT);
        if (!s.startsWith("rgb")) {
            return null;
        }
        int open = s.indexOf('(');
        int close = s.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return null;
        }
        String[] parts = s.substring(open + 1, close).split("[,/\\s]+");
        if (parts.length < 3) {
            return null;
        }
        try {
            // ANY translucency refuses, not just alpha 0 (v1.234.0
            // review): rgba(0,0,0,0.5) — the classic overlay — composites
            // with an ancestor's backdrop this class cannot see, and
            // treating it as opaque reported a contrast the page doesn't
            // have. The honest refusal the javadoc promises for
            // "transparent" applies to every alpha below 1.
            if (parts.length >= 4 && Double.parseDouble(parts[3].trim()) < 1.0) {
                return null;
            }
            return new int[]{
                clamp(parts[0]), clamp(parts[1]), clamp(parts[2])
            };
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    private static int clamp(String token) {
        int v = (int) Math.round(Double.parseDouble(token.trim()));
        return Math.max(0, Math.min(255, v));
    }

    /** WCAG relative luminance in [0,1]. */
    static double luminance(int[] rgb) {
        return 0.2126 * channel(rgb[0]) + 0.7152 * channel(rgb[1])
                + 0.0722 * channel(rgb[2]);
    }

    private static double channel(int v8) {
        double v = v8 / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
}
