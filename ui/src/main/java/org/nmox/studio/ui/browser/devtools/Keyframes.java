package org.nmox.studio.ui.browser.devtools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The keyframe system under the Motion pane (v2.12.0 — DHTML,
 * reborn the honest way): a pure model of one CSS animation — named
 * keyframe stops, each a set of declarations, plus the duration/easing/
 * iteration shorthand — that emits a real {@code @keyframes} block and
 * parses one back, so the timeline strip EDITS what is in your
 * stylesheet instead of accumulating duplicates.
 *
 * <p>The write rules mirror {@link StyleWriteback}'s: names must be
 * plain idents, properties and values are refused if they carry the
 * characters that could break out of the block structure, and when a
 * same-named block already exists the LAST one is replaced — that is
 * the block the cascade actually uses, so editing any other would
 * change dead CSS (the v1.359.0 lesson, inherited on day one).
 *
 * <p>The presets are the DHTML classics — marquee, blink-era pulse,
 * fly-in, bounce, shake, spin, rainbow — minus the {@code <marquee>}
 * and {@code <blink>} tags. Every preset is round-tripped through this
 * class's own emit and parse by the tests: worked examples are
 * fixtures, not prose.
 */
public final class Keyframes {

    private Keyframes() {
    }

    /** One keyframe stop: a percent and its declarations, in order. */
    public record Frame(int percent, Map<String, String> props) {

        public Frame {
            // defensive AND unmodifiable (review hardening): the record
            // accessor hands out this exact map, and a caller mutating
            // it would silently change an already-validated spec.
            // Map.copyOf would lose declaration order — wrap instead.
            props = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(props));
        }
    }

    /**
     * One animation: name, duration, easing, iterations (0 =
     * infinite), and the stops sorted by percent.
     */
    public record Spec(String name, int durationMs, String easing,
            int iterations, List<Frame> frames) {

        public Spec {
            frames = List.copyOf(frames);
        }

        /** The {@code animation:} shorthand value this spec means. */
        public String animationValue() {
            String secs = durationMs % 1000 == 0
                    ? (durationMs / 1000) + "s"
                    : String.format(Locale.ROOT, "%.3fs", durationMs / 1000.0)
                            .replaceFirst("0+s$", "s").replaceFirst("\\.s$", "s");
            return name + " " + secs + " " + easing + " "
                    + (iterations <= 0 ? "infinite" : String.valueOf(iterations));
        }

        /** The {@code @keyframes} block this spec means, 2-space body. */
        public String block() {
            StringBuilder b = new StringBuilder("@keyframes ").append(name).append(" {\n");
            for (Frame f : frames) {
                b.append("  ").append(f.percent()).append("% { ");
                boolean first = true;
                for (Map.Entry<String, String> e : f.props().entrySet()) {
                    if (!first) {
                        b.append(' ');
                    }
                    b.append(e.getKey()).append(": ").append(e.getValue()).append(';');
                    first = false;
                }
                b.append(" }\n");
            }
            return b.append("}").toString();
        }
    }

    /** The rewritten stylesheet, or a refusal with its reason. */
    public record Result(String css, String reason) {

        public boolean ok() {
            return css != null;
        }

        static Result refused(String reason) {
            return new Result(null, reason);
        }
    }

    /**
     * Validates a spec against the write rules; null means clean, else
     * the human reason. This is the ONE gate both the emit path and
     * the Apply button consult.
     */
    public static String problem(Spec spec) {
        if (!isIdent(spec.name())) {
            return "animation name must be a plain identifier";
        }
        if (spec.frames().isEmpty()) {
            return "an animation needs at least one keyframe";
        }
        if (spec.durationMs() < 1) {
            return "duration must be positive";
        }
        if (!isEasing(spec.easing())) {
            return "easing must be a timing function";
        }
        int prev = -1;
        for (Frame f : spec.frames()) {
            if (f.percent() < 0 || f.percent() > 100) {
                return "keyframe percents must be 0..100";
            }
            if (f.percent() <= prev) {
                return "keyframe percents must be strictly increasing";
            }
            prev = f.percent();
            if (f.props().isEmpty()) {
                return f.percent() + "% has no declarations";
            }
            for (Map.Entry<String, String> e : f.props().entrySet()) {
                String p = e.getKey();
                String v = e.getValue();
                if (!isPropertyName(p)) {
                    return "property \"" + p + "\" is not a plain CSS property name";
                }
                if (v == null || v.isBlank() || v.indexOf('{') >= 0 || v.indexOf('}') >= 0
                        || v.indexOf(';') >= 0) {
                    return "value for " + p + " must not be blank or contain { } ;";
                }
            }
        }
        return null;
    }


    /** Plain ident: letter first, then letters/digits/-/_ (no regex —
     *  a character walk cannot backtrack, the REDOS idiom). */
    private static boolean isIdent(String s) {
        if (s == null || s.isEmpty() || !Character.isLetter(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '_') {
                return false;
            }
        }
        return true;
    }

    /** Letters and hyphens only. */
    private static boolean isPropertyName(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetter(c) && c != '-') {
                return false;
            }
        }
        return true;
    }

    /** A timing function: keyword, or keyword(digits . , space -). */
    private static boolean isEasing(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        int paren = s.indexOf('(');
        String head = paren < 0 ? s : s.substring(0, paren);
        if (!isPropertyName(head)) {
            return false;
        }
        if (paren < 0) {
            return true;
        }
        if (!s.endsWith(")")) {
            return false;
        }
        for (int i = paren + 1; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (!Character.isDigit(c) && c != '.' && c != ',' && c != ' ' && c != '-') {
                return false;
            }
        }
        return true;
    }

    /**
     * Lands {@code spec}'s block in {@code css}: replaces the LAST
     * same-named {@code @keyframes} block in place, else appends at
     * the end with a separating blank line.
     */
    public static Result applyBlock(String css, Spec spec) {
        String bad = problem(spec);
        if (bad != null) {
            return Result.refused(bad);
        }
        String base = css == null ? "" : css;
        int[] existing = findBlock(base, spec.name());
        if (existing != null) {
            return new Result(base.substring(0, existing[0]) + spec.block()
                    + base.substring(existing[1]), null);
        }
        String sep = base.isBlank() ? "" : (base.endsWith("\n") ? "\n" : "\n\n");
        return new Result(base + sep + spec.block() + "\n", null);
    }

    /**
     * Reads the LAST {@code @keyframes name} block in {@code css} back
     * into a Spec (duration/easing/iterations are NOT in the block —
     * the caller supplies the shorthand it read from the element, or
     * defaults). Returns null when the block is absent or malformed —
     * a hand-written block this parser cannot honestly represent
     * (from/to keywords, comma-joined stops) refuses rather than
     * guessing.
     */
    public static Spec parse(String css, String name, int durationMs,
            String easing, int iterations) {
        if (css == null || name == null) {
            return null;
        }
        int[] at = findBlock(css, name);
        if (at == null) {
            return null;
        }
        String body = css.substring(at[0], at[1]);
        int open = body.indexOf('{');
        String inner = body.substring(open + 1, body.lastIndexOf('}'));
        List<Frame> frames = new ArrayList<>();
        int i = 0;
        while (i < inner.length()) {
            int brace = inner.indexOf('{', i);
            if (brace < 0) {
                break;
            }
            String selector = inner.substring(i, brace).trim();
            int close = inner.indexOf('}', brace);
            if (close < 0) {
                return null;
            }
            if (!selector.matches("[0-9]{1,3}%")) {
                return null; // from/to or comma-joined stops: not ours
            }
            int percent = Integer.parseInt(selector.substring(0, selector.length() - 1));
            Map<String, String> props = new LinkedHashMap<>();
            for (String decl : inner.substring(brace + 1, close).split(";")) {
                int colon = decl.indexOf(':');
                if (colon > 0) {
                    String p = decl.substring(0, colon).trim();
                    String v = decl.substring(colon + 1).trim();
                    if (!p.isEmpty() && !v.isEmpty()) {
                        props.put(p, v);
                    }
                }
            }
            if (!props.isEmpty()) {
                frames.add(new Frame(percent, props));
            }
            i = close + 1;
        }
        if (frames.isEmpty()) {
            return null;
        }
        Spec spec = new Spec(name, durationMs, easing, iterations, frames);
        return problem(spec) == null ? spec : null;
    }

    /**
     * {start, end-exclusive} of the LAST {@code @keyframes name} block
     * (comments neutralized first), or null.
     */
    static int[] findBlock(String css, String name) {
        String neutral = StyleWriteback.neutralizeComments(css);
        String lower = neutral.toLowerCase(Locale.ROOT);
        String wanted = name.toLowerCase(Locale.ROOT);
        int[] last = null;
        int from = 0;
        while (true) {
            int at = lower.indexOf("@keyframes", from);
            if (at < 0) {
                return last;
            }
            int nameStart = at + "@keyframes".length();
            while (nameStart < lower.length() && Character.isWhitespace(lower.charAt(nameStart))) {
                nameStart++;
            }
            int nameEnd = nameStart;
            while (nameEnd < lower.length()
                    && (Character.isLetterOrDigit(lower.charAt(nameEnd))
                    || lower.charAt(nameEnd) == '-' || lower.charAt(nameEnd) == '_')) {
                nameEnd++;
            }
            int open = lower.indexOf('{', nameEnd);
            if (open < 0) {
                return last;
            }
            int depth = 0;
            int end = -1;
            for (int i = open; i < lower.length(); i++) {
                char c = lower.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        end = i + 1;
                        break;
                    }
                }
            }
            if (end < 0) {
                return last;
            }
            if (lower.substring(nameStart, nameEnd).equals(wanted)) {
                last = new int[]{at, end};
            }
            from = end;
        }
    }

    /** The DHTML classics, ready to load into the timeline. */
    public static List<Spec> presets() {
        return List.of(
                new Spec("marquee", 8000, "linear", 0, List.of(
                        new Frame(0, Map.of("transform", "translateX(100vw)")),
                        new Frame(100, Map.of("transform", "translateX(-100%)")))),
                new Spec("pulse", 1200, "ease-in-out", 0, List.of(
                        new Frame(0, Map.of("opacity", "1")),
                        new Frame(50, Map.of("opacity", "0.25")),
                        new Frame(100, Map.of("opacity", "1")))),
                new Spec("fly-in", 900, "ease-out", 1, List.of(
                        new Frame(0, Map.of("transform", "translateY(-120%)", "opacity", "0")),
                        new Frame(80, Map.of("transform", "translateY(8%)", "opacity", "1")),
                        new Frame(100, Map.of("transform", "translateY(0)", "opacity", "1")))),
                new Spec("bounce", 1600, "ease-in-out", 0, List.of(
                        new Frame(0, Map.of("transform", "translateY(0)")),
                        new Frame(50, Map.of("transform", "translateY(-24px)")),
                        new Frame(100, Map.of("transform", "translateY(0)")))),
                new Spec("shake", 500, "linear", 1, List.of(
                        new Frame(0, Map.of("transform", "translateX(0)")),
                        new Frame(25, Map.of("transform", "translateX(-8px)")),
                        new Frame(75, Map.of("transform", "translateX(8px)")),
                        new Frame(100, Map.of("transform", "translateX(0)")))),
                new Spec("spin", 2000, "linear", 0, List.of(
                        new Frame(0, Map.of("transform", "rotate(0deg)")),
                        new Frame(100, Map.of("transform", "rotate(360deg)")))),
                new Spec("rainbow", 4000, "linear", 0, List.of(
                        new Frame(0, Map.of("filter", "hue-rotate(0deg)")),
                        new Frame(100, Map.of("filter", "hue-rotate(360deg)")))));
    }
}
