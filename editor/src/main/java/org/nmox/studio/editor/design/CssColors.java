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
            "\\b(rgba?|hsla?|hwb|oklch|oklab|lab|lch)\\(\\s*([^)]{1,120})\\)");
    /** color-mix( needs a balanced-paren walk — its args contain functions. */
    private static final Pattern COLOR_MIX_START = Pattern.compile("\\bcolor-mix\\(");
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

    /**
     * Every color literal in {@code text}, in order, comments skipped.
     * Honest limit (v1.234.0 review): STRING contents are not masked the
     * way comments are, so {@code content: "red"} swatches the word
     * inside the string. Strings in stylesheets are rare and a color
     * word inside one is rarer; masking them would double the pre-pass
     * for a case nobody has hit. Recorded here so the limit is a choice,
     * not a surprise.
     */
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
            // \b treats '-' as a boundary, but `to-rgb(255, 0, 0)` is a
            // Sass helper, not rgb() — same neighbor rule as the named
            // colors below (v1.234.0 review)
            if (identNeighbor(text, fn.start() - 1)) {
                continue;
            }
            Color c = parseFunction(fn.group(1), fn.group(2));
            if (c != null) {
                out.add(new ColorSpan(fn.start(), fn.end(), c));
            }
        }
        Matcher mix = COLOR_MIX_START.matcher(text);
        while (mix.find()) {
            if (inComment[mix.start()]) {
                continue;
            }
            int close = matchingParen(text, mix.end() - 1);
            if (close < 0) {
                continue;
            }
            Color c = parseColorMix(text.substring(mix.end(), close));
            if (c != null) {
                out.add(new ColorSpan(mix.start(), close + 1, c));
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
        // start ascending, wider span first on ties — so an outer
        // color-mix paints before (under) the literals nested inside it
        out.sort(java.util.Comparator.comparingInt(ColorSpan::start)
                .thenComparing(java.util.Comparator.comparingInt(ColorSpan::end).reversed()));
        return out;
    }

    /** One function-notation color; null when the args make no color. */
    static Color parseFunction(String name, String args) {
        return switch (name) {
            case "rgb", "rgba" -> parseRgbArgs(args);
            case "hsl", "hsla" -> parseHslArgs(args);
            case "hwb" -> parseHwbArgs(args);
            case "oklch" -> parseOklchArgs(args);
            case "oklab" -> parseOklabArgs(args);
            case "lab" -> parseLabArgs(args);
            case "lch" -> parseLchArgs(args);
            default -> null;
        };
    }

    /**
     * Black or white, whichever reads against {@code background} — the
     * standard relative-luminance cut so a literal painted as its own
     * color keeps legible text.
     */
    public static Color readableTextOn(Color background) {
        return luminance(background) > 0.45 ? Color.BLACK : Color.WHITE;
    }

    /**
     * Formats a picked color IN THE FORM the literal it replaces was
     * authored in (v1.229.0, the color picker): hex stays hex,
     * {@code rgb()} stays {@code rgb()}, {@code hsl()} stays
     * {@code hsl()} (via the reverse conversion), and a named color —
     * which the picked color almost never has — becomes hex.
     */
    public static String format(Color picked, String originalLiteral) {
        String lower = originalLiteral.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("rgb")) {
            return String.format(java.util.Locale.ROOT, "rgb(%d, %d, %d)",
                    picked.getRed(), picked.getGreen(), picked.getBlue());
        }
        if (lower.startsWith("hsl")) {
            double[] hsl = rgbToHsl(picked);
            return String.format(java.util.Locale.ROOT, "hsl(%.0f, %.0f%%, %.0f%%)",
                    hsl[0], hsl[1] * 100, hsl[2] * 100);
        }
        if (lower.startsWith("hwb")) {
            double[] hsl = rgbToHsl(picked);
            double w = Math.min(picked.getRed(),
                    Math.min(picked.getGreen(), picked.getBlue())) / 255.0;
            double b = 1 - Math.max(picked.getRed(),
                    Math.max(picked.getGreen(), picked.getBlue())) / 255.0;
            return String.format(java.util.Locale.ROOT, "hwb(%.0f %.0f%% %.0f%%)",
                    hsl[0], w * 100, b * 100);
        }
        if (lower.startsWith("oklch")) {
            double[] ok = rgbToOklab(picked);
            double c = Math.hypot(ok[1], ok[2]);
            double h = ((Math.toDegrees(Math.atan2(ok[2], ok[1])) % 360) + 360) % 360;
            return String.format(java.util.Locale.ROOT, "oklch(%.3f %.3f %.1f)",
                    ok[0], c, h);
        }
        if (lower.startsWith("oklab")) {
            double[] ok = rgbToOklab(picked);
            return String.format(java.util.Locale.ROOT, "oklab(%.3f %.3f %.3f)",
                    ok[0], ok[1], ok[2]);
        }
        if (lower.startsWith("lab")) {
            double[] lab = rgbToLab(picked);
            return String.format(java.util.Locale.ROOT, "lab(%.1f %.1f %.1f)",
                    lab[0], lab[1], lab[2]);
        }
        if (lower.startsWith("lch")) {
            double[] lab = rgbToLab(picked);
            double c = Math.hypot(lab[1], lab[2]);
            double h = ((Math.toDegrees(Math.atan2(lab[2], lab[1])) % 360) + 360) % 360;
            return String.format(java.util.Locale.ROOT, "lch(%.1f %.1f %.1f)",
                    lab[0], c, h);
        }
        // color-mix and named colors: a picked color is one color, not a
        // recipe — hex is the honest replacement
        return String.format(java.util.Locale.ROOT, "#%02x%02x%02x",
                picked.getRed(), picked.getGreen(), picked.getBlue());
    }

    /**
     * RGB → HSL: h in [0,360), s and l in [0,1]. The CSS reverse map.
     * Channel comparisons stay on the original ints so no floating-point
     * equality is involved.
     */
    static double[] rgbToHsl(Color c) {
        int ri = c.getRed(), gi = c.getGreen(), bi = c.getBlue();
        int maxI = Math.max(ri, Math.max(gi, bi));
        int minI = Math.min(ri, Math.min(gi, bi));
        double max = maxI / 255.0;
        double min = minI / 255.0;
        double l = (max + min) / 2;
        if (maxI == minI) {
            return new double[]{0, 0, l}; // achromatic
        }
        double r = ri / 255.0, g = gi / 255.0, b = bi / 255.0;
        double d = max - min;
        double s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
        double h;
        if (maxI == ri) {
            h = (g - b) / d + (gi < bi ? 6 : 0);
        } else if (maxI == gi) {
            h = (b - r) / d + 2;
        } else {
            h = (r - g) / d + 4;
        }
        return new double[]{h * 60, s, l};
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

    /** hwb(h w% b%): whiteness/blackness over the pure hue (CSS Color 4 §8). */
    static Color parseHwbArgs(String args) {
        String[] parts = splitArgs(args);
        if (parts.length < 3) {
            return null;
        }
        try {
            double h = hue(parts[0]);
            double w = clamp01(numberOrPercent(parts[1], 100) / 100.0);
            double b = clamp01(numberOrPercent(parts[2], 100) / 100.0);
            if (w + b >= 1) { // fully washed out: the spec's achromatic gray
                int g = (int) Math.round(w / (w + b) * 255);
                return new Color(g, g, g);
            }
            Color pure = hslToRgb(((h % 360) + 360) % 360 / 360.0, 1, 0.5);
            return new Color(
                    hwbChannel(pure.getRed(), w, b),
                    hwbChannel(pure.getGreen(), w, b),
                    hwbChannel(pure.getBlue(), w, b));
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    private static int hwbChannel(int pure8, double w, double b) {
        return (int) Math.round((pure8 / 255.0 * (1 - w - b) + w) * 255);
    }

    /** oklch(L C H): the perceptual polar space juniors reach for first. */
    static Color parseOklchArgs(String args) {
        String[] parts = splitArgs(args);
        if (parts.length < 3) {
            return null;
        }
        try {
            double l = numberOrPercent(parts[0], 1);
            double c = numberOrPercent(parts[1], 0.4);
            double h = Math.toRadians(hue(parts[2]));
            return oklabToRgb(l, c * Math.cos(h), c * Math.sin(h));
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    /** oklab(L a b). */
    static Color parseOklabArgs(String args) {
        String[] parts = splitArgs(args);
        if (parts.length < 3) {
            return null;
        }
        try {
            return oklabToRgb(
                    numberOrPercent(parts[0], 1),
                    numberOrPercent(parts[1], 0.4),
                    numberOrPercent(parts[2], 0.4));
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    /** lab(L a b): CIELAB, D50 white per CSS Color 4. */
    static Color parseLabArgs(String args) {
        String[] parts = splitArgs(args);
        if (parts.length < 3) {
            return null;
        }
        try {
            return labToRgb(
                    numberOrPercent(parts[0], 100),
                    numberOrPercent(parts[1], 125),
                    numberOrPercent(parts[2], 125));
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    /** lch(L C H): CIELAB's polar form. */
    static Color parseLchArgs(String args) {
        String[] parts = splitArgs(args);
        if (parts.length < 3) {
            return null;
        }
        try {
            double l = numberOrPercent(parts[0], 100);
            double c = numberOrPercent(parts[1], 150);
            double h = Math.toRadians(hue(parts[2]));
            return labToRgb(l, c * Math.cos(h), c * Math.sin(h));
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    /**
     * color-mix(in space, c1 p1%, c2 p2%) → the actual computed mix.
     * srgb and srgb-linear mix channelwise in their own space; every
     * other space (oklab, oklch, lab, lch, hsl…) mixes in Oklab — the
     * perceptual result the spec's spaces approximate, close enough
     * for a swatch and stated here rather than silently wrong.
     */
    static Color parseColorMix(String args) {
        List<String> parts = splitTopLevel(args);
        if (parts.size() != 3 || !parts.get(0).trim().startsWith("in ")) {
            return null;
        }
        String space = parts.get(0).trim().substring(3).trim().split("\\s+")[0];
        double[] p = new double[]{-1, -1};
        Color[] c = new Color[2];
        for (int i = 0; i < 2; i++) {
            String comp = parts.get(i + 1).trim();
            Matcher pct = Pattern.compile("(?:^|\\s)(\\d+(?:\\.\\d+)?)%\\s*$").matcher(comp);
            if (pct.find()) {
                p[i] = Double.parseDouble(pct.group(1)) / 100.0;
                comp = comp.substring(0, pct.start()).trim();
            }
            List<ColorSpan> found = scan(comp);
            if (found.size() != 1 || found.get(0).start() != 0
                    || found.get(0).end() != comp.length()) {
                return null; // a component we can't read: no swatch, no guess
            }
            c[i] = found.get(0).color();
        }
        if (p[0] < 0 && p[1] < 0) {
            p[0] = p[1] = 0.5;
        } else if (p[0] < 0) {
            p[0] = 1 - p[1];
        } else if (p[1] < 0) {
            p[1] = 1 - p[0];
        }
        double sum = p[0] + p[1];
        if (sum <= 0) {
            return null;
        }
        double t = p[1] / sum; // fraction of the SECOND color
        return switch (space) {
            case "srgb" -> new Color(
                    lerp8(c[0].getRed(), c[1].getRed(), t),
                    lerp8(c[0].getGreen(), c[1].getGreen(), t),
                    lerp8(c[0].getBlue(), c[1].getBlue(), t));
            case "srgb-linear" -> mixLinear(c[0], c[1], t);
            default -> mixOklab(c[0], c[1], t);
        };
    }

    private static int lerp8(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }

    private static Color mixLinear(Color a, Color b, double t) {
        double[] la = {lin(a.getRed()), lin(a.getGreen()), lin(a.getBlue())};
        double[] lb = {lin(b.getRed()), lin(b.getGreen()), lin(b.getBlue())};
        return new Color(
                gam(la[0] + (lb[0] - la[0]) * t),
                gam(la[1] + (lb[1] - la[1]) * t),
                gam(la[2] + (lb[2] - la[2]) * t));
    }

    private static Color mixOklab(Color a, Color b, double t) {
        double[] oa = rgbToOklab(a);
        double[] ob = rgbToOklab(b);
        return oklabToRgb(
                oa[0] + (ob[0] - oa[0]) * t,
                oa[1] + (ob[1] - oa[1]) * t,
                oa[2] + (ob[2] - oa[2]) * t);
    }

    // ---- argument plumbing --------------------------------------------

    /** Args split on commas/slashes/spaces, alpha tail (after /) dropped. */
    private static String[] splitArgs(String args) {
        int slash = args.indexOf('/');
        if (slash >= 0) {
            args = args.substring(0, slash);
        }
        return args.trim().split("[,\\s]+");
    }

    /** Top-level comma split: commas inside nested parens don't count. */
    static List<String> splitTopLevel(String args) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < args.length(); i++) {
            char ch = args.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                out.add(args.substring(start, i));
                start = i + 1;
            }
        }
        out.add(args.substring(start));
        return out;
    }

    /** The index of the ')' matching the '(' at {@code open}, or -1. */
    static int matchingParen(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length() && i < open + 400; i++) {
            char ch = text.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** A number, or a percentage scaled so 100% = {@code percentRef}. */
    private static double numberOrPercent(String token, double percentRef) {
        token = token.trim();
        if ("none".equals(token)) {
            return 0;
        }
        if (token.endsWith("%")) {
            return Double.parseDouble(token.substring(0, token.length() - 1))
                    * percentRef / 100.0;
        }
        return Double.parseDouble(token);
    }

    private static double hue(String token) {
        token = token.trim();
        if ("none".equals(token)) {
            return 0;
        }
        return Double.parseDouble(token.replace("deg", "").trim());
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    // ---- color-space math (CSS Color 4 reference conversions) ---------

    /** sRGB 8-bit channel → linear light. */
    private static double lin(int v8) {
        double v = v8 / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** Linear light → sRGB 8-bit channel, clamped. */
    private static int gam(double v) {
        double g = v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(Math.max(v, 0), 1 / 2.4) - 0.055;
        return (int) Math.max(0, Math.min(255, Math.round(g * 255)));
    }

    /** Oklab → sRGB (Ottosson's reference matrices); out-of-gamut clamps. */
    static Color oklabToRgb(double l, double a, double b) {
        double l1 = Math.pow(l + 0.3963377774 * a + 0.2158037573 * b, 3);
        double m1 = Math.pow(l - 0.1055613458 * a - 0.0638541728 * b, 3);
        double s1 = Math.pow(l - 0.0894841775 * a - 1.2914855480 * b, 3);
        return new Color(
                gam(+4.0767416621 * l1 - 3.3077115913 * m1 + 0.2309699292 * s1),
                gam(-1.2684380046 * l1 + 2.6097574011 * m1 - 0.3413193965 * s1),
                gam(-0.0041960863 * l1 - 0.7034186147 * m1 + 1.7076147010 * s1));
    }

    /** sRGB → Oklab {L, a, b}. */
    static double[] rgbToOklab(Color c) {
        double r = lin(c.getRed()), g = lin(c.getGreen()), b = lin(c.getBlue());
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[]{
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
    }

    private static final double[] D50 = {0.96422, 1.0, 0.82521};

    /** CIELAB (D50) → sRGB via XYZ and Bradford adaptation to D65. */
    static Color labToRgb(double l, double a, double b) {
        double fy = (l + 16) / 116;
        double fx = fy + a / 500;
        double fz = fy - b / 200;
        double x = fInv(fx) * D50[0];
        double y = fInv(fy) * D50[1];
        double z = fInv(fz) * D50[2];
        // Bradford D50 → D65
        double x65 = 0.9555766 * x - 0.0230393 * y + 0.0631636 * z;
        double y65 = -0.0282895 * x + 1.0099416 * y + 0.0210077 * z;
        double z65 = 0.0122982 * x - 0.0204830 * y + 1.3299098 * z;
        return new Color(
                gam(3.2404542 * x65 - 1.5371385 * y65 - 0.4985314 * z65),
                gam(-0.9692660 * x65 + 1.8760108 * y65 + 0.0415560 * z65),
                gam(0.0556434 * x65 - 0.2040259 * y65 + 1.0572252 * z65));
    }

    /** sRGB → CIELAB (D50) {L, a, b}. */
    static double[] rgbToLab(Color c) {
        double r = lin(c.getRed()), g = lin(c.getGreen()), bl = lin(c.getBlue());
        double x65 = 0.4124564 * r + 0.3575761 * g + 0.1804375 * bl;
        double y65 = 0.2126729 * r + 0.7151522 * g + 0.0721750 * bl;
        double z65 = 0.0193339 * r + 0.1191920 * g + 0.9503041 * bl;
        // Bradford D65 → D50
        double x = 1.0478112 * x65 + 0.0228866 * y65 - 0.0501270 * z65;
        double y = 0.0295424 * x65 + 0.9904844 * y65 - 0.0170491 * z65;
        double z = -0.0092345 * x65 + 0.0150436 * y65 + 0.7521316 * z65;
        double fx = fFwd(x / D50[0]);
        double fy = fFwd(y / D50[1]);
        double fz = fFwd(z / D50[2]);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double fInv(double t) {
        double t3 = t * t * t;
        return t3 > 0.008856 ? t3 : (t - 16.0 / 116) / 7.787;
    }

    private static double fFwd(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116;
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
