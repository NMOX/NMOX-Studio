package org.nmox.studio.editor.emmet;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emmet-style CSS abbreviations (v1.336.0) — the v1.329.0 grammar's
 * recorded deliberate out ("CSS abbreviations"), closed as its own pure
 * core. Type {@code m10-20} or {@code df} in a stylesheet and ⌥⌘E
 * expands it to the real declaration.
 *
 * <p><b>The grammar is a written-down subset, and the refusals are
 * features</b> (the {@link Emmet} law): anything not in the tables
 * below leaves the text untouched. Real Emmet fuzzy-matches any letter
 * soup onto its property list ("ov-h", "ovh" and "oh" all work); a
 * fuzzy match that guesses wrong MUTATES YOUR STYLESHEET, so this core
 * only expands what it can expand exactly:
 *
 * <ul>
 * <li><b>Keywords</b> — an exact-match table of the declarations
 *     designers type all day: {@code df} → {@code display: flex;},
 *     {@code aic} → {@code align-items: center;}, {@code posa},
 *     {@code tac}, {@code ttu}, {@code m0a} … (see {@code KEYWORDS};
 *     the table IS the spec).</li>
 * <li><b>Numeric properties</b> — {@code <prefix><value>} for the
 *     size/spacing family ({@code m mt mr mb ml p pt pr pb pl w h
 *     miw mih maw mah t r b l fz lh bdrs z op}). Values default to
 *     {@code px}; suffix {@code p} → {@code %}, {@code e} → {@code em},
 *     {@code r} → {@code rem}; {@code 0} stays bare; {@code z} and
 *     {@code op} are unitless by definition. Decimals allowed
 *     ({@code fz1.2r} → {@code font-size: 1.2rem;}). Margin and
 *     padding take up to four {@code -}-separated values
 *     ({@code p10-20} → {@code padding: 10px 20px;}).</li>
 * <li><b>Colors</b> — {@code c#f00} → {@code color: #f00;},
 *     {@code bgc#1a2b3c} → {@code background-color: #1a2b3c;}
 *     (3/4/6/8 hex digits, validated).</li>
 * <li><b>{@code !}</b> — a trailing bang appends
 *     {@code !important}.</li>
 * <li><b>{@code +}</b> — chains declarations (v1.338.0): {@code
 *     df+aic+jcc} expands to three lines. All-or-nothing: one bad part
 *     refuses the whole chain, because a partial expansion would
 *     mutate the stylesheet on a typo.</li>
 * </ul>
 *
 * <p>Deliberately out, recorded here: fuzzy property matching, vendor
 * prefixes, negative leading values ({@code m-10} — the {@code -} is
 * this grammar's value separator), {@code @} unit modifiers, and
 * lorem. Each can join later without breaking what's pinned.
 */
public final class CssEmmet {

    /** The expansion and where the caret lands (always after the ;). */
    public record Expansion(String css, int caretOffset) {}

    /** Exact-match keyword declarations. The table IS the grammar. */
    static final Map<String, String> KEYWORDS = new LinkedHashMap<>();
    static {
        // display
        KEYWORDS.put("db", "display: block;");
        KEYWORDS.put("dib", "display: inline-block;");
        KEYWORDS.put("df", "display: flex;");
        KEYWORDS.put("dif", "display: inline-flex;");
        KEYWORDS.put("dg", "display: grid;");
        KEYWORDS.put("dn", "display: none;");
        // position
        KEYWORDS.put("posa", "position: absolute;");
        KEYWORDS.put("posr", "position: relative;");
        KEYWORDS.put("posf", "position: fixed;");
        KEYWORDS.put("poss", "position: sticky;");
        // text
        KEYWORDS.put("tac", "text-align: center;");
        KEYWORDS.put("tal", "text-align: left;");
        KEYWORDS.put("tar", "text-align: right;");
        KEYWORDS.put("taj", "text-align: justify;");
        KEYWORDS.put("fwb", "font-weight: bold;");
        KEYWORDS.put("fwn", "font-weight: normal;");
        KEYWORDS.put("fsi", "font-style: italic;");
        KEYWORDS.put("ttu", "text-transform: uppercase;");
        KEYWORDS.put("ttl", "text-transform: lowercase;");
        KEYWORDS.put("ttc", "text-transform: capitalize;");
        KEYWORDS.put("ttn", "text-transform: none;");
        KEYWORDS.put("tdn", "text-decoration: none;");
        KEYWORDS.put("tdu", "text-decoration: underline;");
        KEYWORDS.put("wsn", "white-space: nowrap;");
        // overflow
        KEYWORDS.put("ovh", "overflow: hidden;");
        KEYWORDS.put("ova", "overflow: auto;");
        KEYWORDS.put("ovs", "overflow: scroll;");
        // flex
        KEYWORDS.put("aic", "align-items: center;");
        KEYWORDS.put("aifs", "align-items: flex-start;");
        KEYWORDS.put("aife", "align-items: flex-end;");
        KEYWORDS.put("jcc", "justify-content: center;");
        KEYWORDS.put("jcsb", "justify-content: space-between;");
        KEYWORDS.put("jcsa", "justify-content: space-around;");
        KEYWORDS.put("jcfs", "justify-content: flex-start;");
        KEYWORDS.put("jcfe", "justify-content: flex-end;");
        KEYWORDS.put("fdc", "flex-direction: column;");
        KEYWORDS.put("fdr", "flex-direction: row;");
        KEYWORDS.put("fww", "flex-wrap: wrap;");
        // misc daily drivers
        KEYWORDS.put("curp", "cursor: pointer;");
        KEYWORDS.put("curd", "cursor: default;");
        KEYWORDS.put("usn", "user-select: none;");
        KEYWORDS.put("pen", "pointer-events: none;");
        KEYWORDS.put("bxbb", "box-sizing: border-box;");
        KEYWORDS.put("m0a", "margin: 0 auto;");
    }

    /** Numeric-property prefixes; longest prefix wins ({@code miw} before {@code m}). */
    static final Map<String, String> NUMERIC = new LinkedHashMap<>();
    static {
        NUMERIC.put("miw", "min-width");
        NUMERIC.put("mih", "min-height");
        NUMERIC.put("maw", "max-width");
        NUMERIC.put("mah", "max-height");
        NUMERIC.put("bdrs", "border-radius");
        NUMERIC.put("mt", "margin-top");
        NUMERIC.put("mr", "margin-right");
        NUMERIC.put("mb", "margin-bottom");
        NUMERIC.put("ml", "margin-left");
        NUMERIC.put("pt", "padding-top");
        NUMERIC.put("pr", "padding-right");
        NUMERIC.put("pb", "padding-bottom");
        NUMERIC.put("pl", "padding-left");
        NUMERIC.put("fz", "font-size");
        NUMERIC.put("lh", "line-height");
        NUMERIC.put("op", "opacity");
        NUMERIC.put("m", "margin");
        NUMERIC.put("p", "padding");
        NUMERIC.put("w", "width");
        NUMERIC.put("h", "height");
        NUMERIC.put("t", "top");
        NUMERIC.put("r", "right");
        NUMERIC.put("b", "bottom");
        NUMERIC.put("l", "left");
        NUMERIC.put("z", "z-index");
    }

    /** Properties whose values never carry a unit. */
    private static final java.util.Set<String> UNITLESS =
            java.util.Set.of("z-index", "opacity");

    /** Only the box shorthands take multiple {@code -}-separated values. */
    private static final java.util.Set<String> MULTI_VALUE =
            java.util.Set.of("margin", "padding");

    private CssEmmet() {
    }

    /**
     * Expand {@code abbrev} to a declaration, or null when the grammar
     * refuses. Refusal means the caller must leave the text untouched.
     */
    public static Expansion expand(String abbrev) {
        if (abbrev == null || abbrev.isEmpty()) {
            return null;
        }
        // the + combinator (v1.338.0): df+aic+jcc chains declarations,
        // one per line. ALL parts must parse or the WHOLE chain refuses —
        // a partial expansion would mutate the stylesheet on a typo,
        // exactly what the exact-match design exists to prevent.
        String[] parts = abbrev.split("\\+", -1);
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            String decl = expandSingle(part);
            if (decl == null) {
                return null;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(decl);
        }
        String css = out.toString();
        return new Expansion(css, css.length());
    }

    /** One declaration ({@code !} handled per part), or null to refuse. */
    private static String expandSingle(String abbrev) {
        if (abbrev.isEmpty()) {
            return null;
        }
        boolean important = abbrev.endsWith("!");
        String body = important ? abbrev.substring(0, abbrev.length() - 1) : abbrev;
        if (body.isEmpty()) {
            return null;
        }
        String decl = declarationFor(body);
        if (decl == null) {
            return null;
        }
        if (important) {
            decl = decl.substring(0, decl.length() - 1) + " !important;";
        }
        return decl;
    }

    private static String declarationFor(String body) {
        String kw = KEYWORDS.get(body);
        if (kw != null) {
            return kw;
        }
        // colors before the numeric grammar: '#' never starts a value there
        if (body.startsWith("c#")) {
            return colorDecl("color", body.substring(2));
        }
        if (body.startsWith("bgc#")) {
            return colorDecl("background-color", body.substring(4));
        }
        return numericDecl(body);
    }

    private static String colorDecl(String property, String hex) {
        int n = hex.length();
        if (n != 3 && n != 4 && n != 6 && n != 8) {
            return null;
        }
        for (int i = 0; i < n; i++) {
            char c = hex.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!ok) {
                return null;
            }
        }
        return property + ": #" + hex + ";";
    }

    private static String numericDecl(String body) {
        // longest prefix wins so miw10 never reads as margin "iw10"
        String property = null;
        String rest = null;
        int best = 0;
        for (Map.Entry<String, String> e : NUMERIC.entrySet()) {
            String key = e.getKey();
            if (key.length() > best
                    && body.startsWith(key)
                    && body.length() > key.length()
                    && isValueStart(body.charAt(key.length()))) {
                property = e.getValue();
                rest = body.substring(key.length());
                best = key.length();
            }
        }
        if (property == null) {
            return null;
        }
        String[] parts = rest.split("-", -1);
        if (parts.length > 1 && !MULTI_VALUE.contains(property)) {
            return null;
        }
        if (parts.length > 4) {
            return null;
        }
        StringBuilder value = new StringBuilder();
        for (String part : parts) {
            String v = cssValue(part, UNITLESS.contains(property));
            if (v == null) {
                return null;
            }
            if (value.length() > 0) {
                value.append(' ');
            }
            value.append(v);
        }
        return property + ": " + value + ";";
    }

    private static boolean isValueStart(char c) {
        return c >= '0' && c <= '9';
    }

    /** {@code 10} → 10px, {@code 50p} → 50%, {@code 1.5e} → 1.5em, {@code 0} → 0. */
    private static String cssValue(String raw, boolean unitless) {
        if (raw.isEmpty()) {
            return null;
        }
        String unit = "px";
        String number = raw;
        char last = raw.charAt(raw.length() - 1);
        if (last == 'p' || last == 'e' || last == 'r') {
            if (unitless) {
                return null;
            }
            unit = switch (last) {
                case 'p' -> "%";
                case 'e' -> "em";
                default -> "rem";
            };
            number = raw.substring(0, raw.length() - 1);
        }
        if (!number.matches("\\d+(\\.\\d+)?")) {
            return null;
        }
        if (unitless || number.matches("0(\\.0+)?")) {
            return number;
        }
        return number + unit;
    }

    /**
     * The abbreviation ending at the caret, or null. The token is the
     * trailing run of abbreviation characters; it must parse, and it
     * must not sit in VALUE position — {@code color: tdn} is someone
     * typing a value, not an abbreviation, so a {@code :} immediately
     * before the token refuses (documented limit: a full declaration
     * already on the line means the chord is not for you).
     */
    public static String abbreviationIn(String lineBeforeCaret) {
        int start = lineBeforeCaret.length();
        while (start > 0 && isAbbrevChar(lineBeforeCaret.charAt(start - 1))) {
            start--;
        }
        if (start == lineBeforeCaret.length()) {
            return null;
        }
        String prefix = lineBeforeCaret.substring(0, start).stripTrailing();
        if (prefix.endsWith(":")) {
            return null;
        }
        String token = lineBeforeCaret.substring(start);
        return expand(token) == null ? null : token;
    }

    private static boolean isAbbrevChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '#' || c == '.'
                || c == '-' || c == '!' || c == '+';
    }
}
