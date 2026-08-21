package org.nmox.studio.editor.design;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The style regions of an HTML document — the ONLY places the css
 * design surfaces (color swatches, the ⌘-click picker) may look
 * (v2.22.0). In a stylesheet every {@code tomato} is a color; in HTML
 * it is usually prose, so the css family's document-wide scan would
 * paint sentences. The honest boundary is structural: {@code <style>}
 * block contents and {@code style="…"} attribute values, with HTML
 * comments blanked first so a commented-out block cannot contribute.
 *
 * <p>Pure and line-agnostic: offsets index the ORIGINAL text, so
 * spans found inside a region shift by the region's start and land
 * exactly where the literal sits in the editor.
 */
public final class HtmlStyleRegions {

    /** A half-open [start, end) slice of the original text. */
    public record Region(int start, int end) {
    }

    /**
     * The markup family the style-region boundary applies to
     * (v2.25.0): plain HTML plus the component formats that carry
     * {@code <style>} blocks or style attributes — Vue SFCs, Svelte,
     * Angular templates. ONE definition, consulted by every surface
     * (swatches, picker, tokens, Emmet), so the family cannot drift
     * per-surface; the parity gate reads this set.
     */
    public static final java.util.Set<String> MARKUP_MIMES = java.util.Set.of(
            "text/html", "text/x-vue", "text/x-svelte", "text/x-ng-template");

    /** True for documents whose bare color words are prose, not css. */
    public static boolean isMarkup(Object mime) {
        return mime instanceof String m && MARKUP_MIMES.contains(m);
    }

    private static final Pattern STYLE_BLOCK = Pattern.compile(
            "<style\\b[^>]*>(.*?)</style\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern STYLE_ATTR = Pattern.compile(
            "\\bstyle\\s*=\\s*(\"([^\"]*)\"|'([^']*)')",
            Pattern.CASE_INSENSITIVE);

    private HtmlStyleRegions() {
    }

    /** Style regions of {@code html}, in document order. */
    public static List<Region> find(String html) {
        String blanked = blankComments(html);
        List<Region> out = new ArrayList<>();
        Matcher block = STYLE_BLOCK.matcher(blanked);
        while (block.find()) {
            out.add(new Region(block.start(1), block.end(1)));
        }
        Matcher attr = STYLE_ATTR.matcher(blanked);
        while (attr.find()) {
            int group = attr.group(2) != null ? 2 : 3;
            out.add(new Region(attr.start(group), attr.end(group)));
        }
        out.sort((a, b) -> Integer.compare(a.start(), b.start()));
        return out;
    }

    /** True when {@code offset} sits inside any style region. */
    public static boolean inStyle(String html, int offset) {
        for (Region r : find(html)) {
            if (offset >= r.start() && offset < r.end()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Color spans of the style regions only, offsets in the original
     * text — the HTML counterpart of {@link CssColors#scan}.
     */
    public static List<CssColors.ColorSpan> scan(String html) {
        List<CssColors.ColorSpan> out = new ArrayList<>();
        for (Region r : find(html)) {
            String slice = html.substring(r.start(), r.end());
            for (CssColors.ColorSpan s : CssColors.scan(slice)) {
                out.add(new CssColors.ColorSpan(
                        r.start() + s.start(), r.start() + s.end(), s.color()));
            }
        }
        return out;
    }

    /**
     * Custom-property declarations found in the style regions, offsets
     * in the ORIGINAL text — the HTML counterpart of
     * {@link CssTokens#declarations(String)} (v2.23.0, tokens-in-HTML).
     */
    public static java.util.Map<String, CssTokens.Token> declarations(String html) {
        java.util.Map<String, CssTokens.Token> out = new java.util.LinkedHashMap<>();
        for (Region r : find(html)) {
            CssTokens.declarations(html.substring(r.start(), r.end()))
                    .forEach((name, t) -> out.putIfAbsent(name,
                            new CssTokens.Token(name, t.value(), r.start() + t.offset())));
        }
        return out;
    }

    /**
     * var(--token) usages inside style regions painted as the color
     * their token declares — resolution spans ALL the document's style
     * regions (a body style attribute may use a token the head's
     * {@code <style>} block declares), still never touching disk.
     */
    public static java.util.List<CssColors.ColorSpan> varUsageColorSpans(String html) {
        java.util.List<CssColors.ColorSpan> out = new java.util.ArrayList<>();
        java.util.Map<String, CssTokens.Token> declared = declarations(html);
        if (declared.isEmpty()) {
            return out;
        }
        java.util.regex.Matcher m = VAR_USAGE.matcher(html);
        while (m.find()) {
            if (!inStyle(html, m.start(1))) {
                continue;
            }
            CssTokens.Token t = declared.get(m.group(1));
            if (t == null) {
                continue;
            }
            java.util.List<CssColors.ColorSpan> value = CssColors.scan(t.value());
            if (value.size() == 1) {
                out.add(new CssColors.ColorSpan(
                        m.start(1), m.end(1), value.get(0).color()));
            }
        }
        return out;
    }

    private static final Pattern VAR_USAGE =
            Pattern.compile("var\\(\\s*(--[A-Za-z0-9_-]+)");

    /** HTML comments become spaces so their contents cannot region. */
    static String blankComments(String html) {
        StringBuilder sb = new StringBuilder(html);
        int i = 0;
        while ((i = sb.indexOf("<!--", i)) >= 0) {
            int end = sb.indexOf("-->", i + 4);
            int stop = end < 0 ? sb.length() : end + 3;
            for (int k = i; k < stop; k++) {
                if (sb.charAt(k) != '\n') {
                    sb.setCharAt(k, ' ');
                }
            }
            i = stop;
        }
        return sb.toString();
    }
}
