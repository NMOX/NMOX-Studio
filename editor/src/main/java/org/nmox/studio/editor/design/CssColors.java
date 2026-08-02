package org.nmox.studio.editor.design;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The pure half of CSS color awareness (v1.227.0, the Senior Web
 * Designer pass): find every color literal in a stylesheet — hex
 * ({@code #rgb}, {@code #rgba}, {@code #rrggbb}, {@code #rrggbbaa}),
 * functional ({@code rgb()}, {@code rgba()}, {@code hsl()},
 * {@code hsla()}), and the CSS named colors — with its exact span, so
 * the editor can paint the literal AS its color. UI-free; every rule
 * is a plain unit test.
 *
 * <p>Honest limits, by design: literals inside {@code /* comments *}{@code /}
 * are skipped (a comment mentioning "red" is prose, not a color);
 * {@code var()} indirections and preprocessor variables are not
 * resolved (their value isn't in this file); alpha is parsed but the
 * preview shows the opaque color (a checkerboard blend inside a text
 * run would be noise).
 */
public final class CssColors {

    /** One color literal: [start, end) in the text, and its color. */
    public record ColorSpan(int start, int end, Color color) {
    }

    private static final Pattern HEX = Pattern.compile(
            "#([0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})\\b");
    private static final Pattern FUNC = Pattern.compile(
            "\\b(rgba?|hsla?)\\(\\s*([^)]{1,120})\\)");
    // The 17 CSS Level 1/2 names plus the modern ones designers actually
    // type; the full 148-name table is deliberately NOT included — rare
    // names like "papayawhip" add lookup cost and false-positive surface
    // for little daily value, and the common set below covers real use.
    private static final Map<String, Color> NAMED = Map.ofEntries(
            Map.entry("black", new Color(0x000000)),
            Map.entry("silver", new Color(0xC0C0C0)),
            Map.entry("gray", new Color(0x808080)),
            Map.entry("grey", new Color(0x808080)),
            Map.entry("white", new Color(0xFFFFFF)),
            Map.entry("maroon", new Color(0x800000)),
            Map.entry("red", new Color(0xFF0000)),
            Map.entry("purple", new Color(0x800080)),
            Map.entry("fuchsia", new Color(0xFF00FF)),
            Map.entry("green", new Color(0x008000)),
            Map.entry("lime", new Color(0x00FF00)),
            Map.entry("olive", new Color(0x808000)),
            Map.entry("yellow", new Color(0xFFFF00)),
            Map.entry("navy", new Color(0x000080)),
            Map.entry("blue", new Color(0x0000FF)),
            Map.entry("teal", new Color(0x008080)),
            Map.entry("aqua", new Color(0x00FFFF)),
            Map.entry("orange", new Color(0xFFA500)),
            Map.entry("gold", new Color(0xFFD700)),
            Map.entry("pink", new Color(0xFFC0CB)),
            Map.entry("brown", new Color(0xA52A2A)),
            Map.entry("coral", new Color(0xFF7F50)),
            Map.entry("crimson", new Color(0xDC143C)),
            Map.entry("indigo", new Color(0x4B0082)),
            Map.entry("violet", new Color(0xEE82EE)),
            Map.entry("salmon", new Color(0xFA8072)),
            Map.entry("tomato", new Color(0xFF6347)),
            Map.entry("turquoise", new Color(0x40E0D0)),
            Map.entry("rebeccapurple", new Color(0x663399)),
            Map.entry("transparent", new Color(0, 0, 0, 0)));
    private static final Pattern NAMED_PATTERN = Pattern.compile(
            "\\b(" + String.join("|", NAMED.keySet()) + ")\\b",
            Pattern.CASE_INSENSITIVE);

    private CssColors() {
    }

    /** Every color literal in {@code text}, in order, comments skipped. */
    public static List<ColorSpan> scan(String text) {
        List<ColorSpan> out = new ArrayList<>();
        boolean[] inComment = commentMask(text);
        Matcher hex = HEX.matcher(text);
        while (hex.find()) {
            if (inComment[hex.start()]) {
                continue;
            }
            Color c = parseHex(hex.group(1));
            if (c != null) {
                out.add(new ColorSpan(hex.start(), hex.end(), c));
            }
        }
        Matcher fn = FUNC.matcher(text);
        while (fn.find()) {
            if (inComment[fn.start()]) {
                continue;
            }
            Color c = fn.group(1).startsWith("rgb")
                    ? parseRgbArgs(fn.group(2)) : parseHslArgs(fn.group(2));
            if (c != null) {
                out.add(new ColorSpan(fn.start(), fn.end(), c));
            }
        }
        Matcher named = NAMED_PATTERN.matcher(text);
        while (named.find()) {
            if (inComment[named.start()]) {
                continue;
            }
            // \b sees '-' and '$' as boundaries, but `$red-dark` or
            // `--red` is an identifier, not the color red — require the
            // neighbors to be outside the CSS ident vocabulary entirely
            if (identNeighbor(text, named.start() - 1)
                    || identNeighbor(text, named.end())) {
                continue;
            }
            out.add(new ColorSpan(named.start(), named.end(),
                    NAMED.get(named.group(1).toLowerCase(java.util.Locale.ROOT))));
        }
        out.sort(java.util.Comparator.comparingInt(ColorSpan::start));
        return out;
    }

    /**
     * Black or white, whichever reads against {@code background} — the
     * standard relative-luminance cut so a literal painted as its own
     * color keeps legible text.
     */
    public static Color readableTextOn(Color background) {
        return luminance(background) > 0.45 ? Color.BLACK : Color.WHITE;
    }

    /** WCAG relative luminance in [0,1]. */
    static double luminance(Color c) {
        return 0.2126 * channel(c.getRed())
                + 0.7152 * channel(c.getGreen())
                + 0.0722 * channel(c.getBlue());
    }

    private static double channel(int v8) {
        double v = v8 / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static boolean identNeighbor(String text, int index) {
        if (index < 0 || index >= text.length()) {
            return false;
        }
        char c = text.charAt(index);
        return Character.isLetterOrDigit(c) || c == '-' || c == '_'
                || c == '$' || c == '#' || c == '@' || c == '.';
    }

    /** True at each index inside a CSS block comment. */
    private static boolean[] commentMask(String text) {
        boolean[] mask = new boolean[text.length() + 1];
        int i = 0;
        while (true) {
            int open = text.indexOf("/*", i);
            if (open < 0) {
                break;
            }
            int close = text.indexOf("*/", open + 2);
            int end = close < 0 ? text.length() : close + 2;
            java.util.Arrays.fill(mask, open, end, true);
            if (close < 0) {
                break;
            }
            i = end;
        }
        return mask;
    }

    static Color parseHex(String digits) {
        try {
            return switch (digits.length()) {
                case 3 -> new Color(dup(digits.charAt(0)), dup(digits.charAt(1)),
                        dup(digits.charAt(2)));
                case 4 -> new Color(dup(digits.charAt(0)), dup(digits.charAt(1)),
                        dup(digits.charAt(2)), dup(digits.charAt(3)));
                case 6 -> new Color(Integer.parseInt(digits, 16));
                case 8 -> new Color(
                        Integer.parseInt(digits.substring(0, 2), 16),
                        Integer.parseInt(digits.substring(2, 4), 16),
                        Integer.parseInt(digits.substring(4, 6), 16),
                        Integer.parseInt(digits.substring(6, 8), 16));
                default -> null;
            };
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    private static int dup(char hexDigit) {
        int v = Character.digit(hexDigit, 16);
        return v * 16 + v;
    }

    /** rgb()/rgba() args: ints or percentages, commas or spaces, clamped. */
    static Color parseRgbArgs(String args) {
        String[] parts = args.split("[,/\\s]+");
        if (parts.length < 3) {
            return null;
        }
        try {
            int r = component(parts[0]);
            int g = component(parts[1]);
            int b = component(parts[2]);
            return new Color(r, g, b);
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    private static int component(String token) {
        token = token.trim();
        double v = token.endsWith("%")
                ? Double.parseDouble(token.substring(0, token.length() - 1)) * 255.0 / 100.0
                : Double.parseDouble(token);
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    /** hsl()/hsla() args → RGB via the CSS hue-to-rgb algorithm. */
    static Color parseHslArgs(String args) {
        String[] parts = args.split("[,/\\s]+");
        if (parts.length < 3) {
            return null;
        }
        try {
            double h = Double.parseDouble(parts[0].replace("deg", "").trim());
            double s = percent(parts[1]);
            double l = percent(parts[2]);
            return hslToRgb(((h % 360) + 360) % 360 / 360.0, s, l);
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    private static double percent(String token) {
        token = token.trim();
        if (!token.endsWith("%")) {
            throw new NumberFormatException("expected % in " + token);
        }
        return Math.max(0, Math.min(1,
                Double.parseDouble(token.substring(0, token.length() - 1)) / 100.0));
    }

    private static Color hslToRgb(double h, double s, double l) {
        double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
        double p = 2 * l - q;
        return new Color(
                (int) Math.round(hue(p, q, h + 1.0 / 3) * 255),
                (int) Math.round(hue(p, q, h) * 255),
                (int) Math.round(hue(p, q, h - 1.0 / 3) * 255));
    }

    private static double hue(double p, double q, double t) {
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        if (t < 1.0 / 6) {
            return p + (q - p) * 6 * t;
        }
        if (t < 1.0 / 2) {
            return q;
        }
        if (t < 2.0 / 3) {
            return p + (q - p) * (2.0 / 3 - t) * 6;
        }
        return p;
    }
}
