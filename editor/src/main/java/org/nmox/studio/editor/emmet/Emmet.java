package org.nmox.studio.editor.emmet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Emmet-style abbreviation expansion, the pure core (v1.329.0):
 * {@code ul>li.item$*3} becomes a real, indented HTML fragment with the
 * caret parked at the first useful empty spot. The editor action in
 * {@link ExpandAbbreviationAction} finds the abbreviation left of the
 * caret and calls {@link #expand}; everything language-shaped lives
 * here where plain tests reach it.
 *
 * <p><b>The v1 grammar, written down</b> (a bounded subset of Emmet,
 * chosen over a full port so every rule is testable and none is
 * half-true): elements ({@code div}, custom elements with dashes),
 * implicit {@code div} when an abbreviation starts with {@code .} or
 * {@code #}, classes {@code .a.b}, id {@code #x}, attributes
 * {@code [href=/ target=_blank]}, inner text {@code {hi}}, child
 * {@code >}, sibling {@code +}, multiplication {@code *3} with
 * {@code $} numbering (1-based; {@code $$} zero-pads), and grouping
 * {@code (...)}, plus the {@code lorem} generator (v1.339.0):
 * {@code lorem} or {@code lorem5} emits DETERMINISTIC placeholder text
 * — the canonical passage, N words (default 12), capitalized and
 * period-terminated — so tests can pin it and two presses agree.
 * Lorem takes no decorations: {@code lorem.big} or {@code lorem{x}}
 * refuses, because a class on placeholder text is a typo, not intent.
 * Climb-up {@code ^} (v1.341.0) returns one level per caret:
 * {@code header>h1^main} puts main beside header, {@code
 * div>ul>li^^footer} climbs two. Two documented refusals instead of
 * Emmet's clamping: climbing PAST THE ROOT refuses (the extra {@code
 * ^} is a typo, not a wish), and a group is a wall — {@code ^} cannot
 * climb out of {@code (...)} (put the sibling after the group
 * instead). Deliberately OUT, recorded here so nobody re-derives the
 * boundary: CSS abbreviations live in {@link CssEmmet}, and implicit
 * tag names by context ({@code ul>.x} makes a div, not an li).
 */
public final class Emmet {

    private Emmet() {
    }

    /** The generated fragment plus where the caret belongs inside it. */
    public record Expansion(String html, int caretOffset) {
    }

    /** HTML void elements — expanded without a closing tag. */
    private static final Set<String> VOID = Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "source", "track", "wbr");

    /**
     * The HTML elements a BARE word may expand to. An abbreviation with
     * any operator ({@code . # > + * [ {}) is deliberate whatever its
     * names, but a lone word only expands when it IS an element —
     * otherwise the chord pressed mid-sentence would turn prose like
     * "world" into {@code <world></world>}. Dashed names pass as custom
     * elements, the platform convention that guarantees a hyphen.
     */
    private static final Set<String> KNOWN_ELEMENTS = Set.of(
            "a", "abbr", "address", "area", "article", "aside", "audio",
            "b", "base", "bdi", "bdo", "blockquote", "body", "br",
            "button", "canvas", "caption", "cite", "code", "col",
            "colgroup", "data", "datalist", "dd", "del", "details",
            "dfn", "dialog", "div", "dl", "dt", "em", "embed",
            "fieldset", "figcaption", "figure", "footer", "form", "h1",
            "h2", "h3", "h4", "h5", "h6", "head", "header", "hgroup",
            "hr", "html", "i", "iframe", "img", "input", "ins", "kbd",
            "label", "legend", "li", "link", "main", "map", "mark",
            "menu", "meta", "meter", "nav", "noscript", "object", "ol",
            "optgroup", "option", "output", "p", "picture", "pre",
            "progress", "q", "rp", "rt", "ruby", "s", "samp", "script",
            "search", "section", "select", "slot", "small", "source",
            "span", "strong", "style", "sub", "summary", "sup", "table",
            "tbody", "td", "template", "textarea", "tfoot", "th",
            "thead", "time", "title", "tr", "track", "u", "ul", "var",
            "video", "wbr");

    /**
     * Emmet's conventional default attributes, kept SMALL: only the
     * pairs a web dev types hundreds of times a day. An empty value
     * becomes the caret's first stop.
     */
    private static final Map<String, String[]> DEFAULT_ATTRS = Map.of(
            "a", new String[] {"href"},
            "img", new String[] {"src", "alt"},
            "input", new String[] {"type"},
            "link", new String[] {"rel", "href"},
            "iframe", new String[] {"src"},
            "label", new String[] {"for"});

    // ---- public API ------------------------------------------------------

    /**
     * Expands {@code abbrev}, or returns null when it is not a
     * parseable abbreviation (the action then does nothing — an
     * explicit chord must never mangle text it cannot understand).
     * {@code indentUnit} is one level of the surrounding file's
     * indentation.
     */
    public static Expansion expand(String abbrev, String indentUnit) {
        if (abbrev == null || abbrev.isBlank()) {
            return null;
        }
        try {
            String trimmed = abbrev.trim();
            Parser p = new Parser(trimmed);
            List<Node> roots = p.parseSiblings();
            if (!p.atEnd() || roots.isEmpty()) {
                return null;
            }
            // climbs that outlived every level would step past the root
            // (or out of a group — a group is a wall, use a sibling):
            // refuse rather than clamp (v1.341.0)
            if (p.pendingClimbs > 0) {
                return null;
            }
            // a lone word must BE an element (see KNOWN_ELEMENTS' javadoc);
            // lorem/loremN is the one non-element bare word (v1.339.0)
            boolean bareWord = trimmed.chars().allMatch(
                    c -> Character.isLetterOrDigit(c) || c == '-');
            if (bareWord && !KNOWN_ELEMENTS.contains(trimmed)
                    && trimmed.indexOf('-') < 0
                    && loremWords(trimmed) < 0) {
                return null;
            }
            Out out = new Out(indentUnit);
            for (int i = 0; i < roots.size(); i++) {
                if (i > 0) {
                    out.newline(0);
                }
                render(roots.get(i), out, 0, 1, 1);
            }
            return new Expansion(out.sb.toString(),
                    out.caret >= 0 ? out.caret : out.sb.length());
        } catch (RuntimeException notAnAbbreviation) {
            return null;
        }
    }

    /**
     * The abbreviation AT the caret, auto-pair aware (v1.332.0): the
     * shipped-app walk typed {@code a.link{Item}} the way anyone does —
     * and the editor's pair intelligence closed the brace, leaving the
     * caret BEFORE the auto-inserted {@code }}, so the text up to the
     * caret was an unclosed abbreviation and the chord refused. When
     * the caret is followed by a run of closers ({@code } ) ]}), they
     * are folded into the abbreviation if that makes it parse; the
     * returned span end tells the action how far past the caret the
     * replacement reaches.
     */
    public record AtCaret(String abbrev, int trailingClosers) {
    }

    public static AtCaret abbreviationAt(String lineBeforeCaret, String lineAfterCaret) {
        String plain = abbreviationIn(lineBeforeCaret);
        if (plain != null) {
            return new AtCaret(plain, 0);
        }
        int closers = 0;
        while (closers < lineAfterCaret.length()
                && isCloser(lineAfterCaret.charAt(closers))) {
            closers++;
        }
        for (int take = 1; take <= closers; take++) {
            String extended = abbreviationIn(
                    lineBeforeCaret + lineAfterCaret.substring(0, take));
            if (extended != null) {
                return new AtCaret(extended, take);
            }
        }
        return null;
    }

    private static boolean isCloser(char c) {
        return c == '}' || c == ')' || c == ']';
    }

    /**
     * The abbreviation ending at the caret, given the line text before
     * it: the LONGEST suffix that parses. Trying suffixes (started
     * after whitespace, {@code >} of a real tag, or {@code "}) beats
     * guessing a character class, because {@code <p>ul>li} must expand
     * {@code ul>li}, not swallow the tag.
     */
    public static String abbreviationIn(String lineBeforeCaret) {
        String s = lineBeforeCaret;
        for (int start = 0; start < s.length(); start++) {
            char before = start == 0 ? ' ' : s.charAt(start - 1);
            if (start == 0 || Character.isWhitespace(before)
                    || before == '>' || before == '"' || before == '\'') {
                String candidate = s.substring(start).trim();
                if (!candidate.isEmpty() && expand(candidate, "  ") != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    // ---- model -----------------------------------------------------------

    private static final class Node {
        String name;                       // null for a pure group
        String id;
        final List<String> classes = new ArrayList<>();
        final Map<String, String> attrs = new LinkedHashMap<>();
        String text;
        int times = 1;
        final List<Node> children = new ArrayList<>();
    }

    // ---- parser ----------------------------------------------------------

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return i >= s.length();
        }

        /**
         * Climbs still owed to enclosing levels (v1.341.0): {@code ^}
         * sets this and unwinds the sibling recursion; each level's
         * {@code >} branch consumes one. Left over at the top =
         * climbing past the root = refusal, never a clamp.
         */
        int pendingClimbs;

        /** siblings := unit (('+'|'>'|'^') ...) — '>' nests into the LAST unit. */
        List<Node> parseSiblings() {
            List<Node> list = new ArrayList<>();
            list.add(parseUnit());
            while (!atEnd()) {
                char c = s.charAt(i);
                if (c == '+') {
                    i++;
                    list.add(parseUnit());
                } else if (c == '>') {
                    i++;
                    Node parent = list.get(list.size() - 1);
                    parent.children.addAll(parseSiblings());
                    if (pendingClimbs > 0) {
                        pendingClimbs--;
                        if (pendingClimbs > 0) {
                            break;             // keep climbing through the caller
                        }
                        list.add(parseUnit()); // landed: a sibling at THIS level
                    }
                } else if (c == '^') {
                    // climb-up: count the run, unwind to the level that
                    // owns the landing spot ('a>b^c' puts c beside a)
                    while (!atEnd() && s.charAt(i) == '^') {
                        i++;
                        pendingClimbs++;
                    }
                    break;
                } else {
                    break;
                }
            }
            return list;
        }

        /** unit := group | element, with optional '*N'. */
        private Node parseUnit() {
            Node n;
            if (!atEnd() && s.charAt(i) == '(') {
                i++;
                n = new Node();               // group: no name of its own
                n.children.addAll(parseSiblings());
                expect(')');
            } else {
                n = parseElement();
            }
            if (!atEnd() && s.charAt(i) == '*') {
                i++;
                n.times = parseInt();
            }
            return n;
        }

        private Node parseElement() {
            Node n = new Node();
            n.name = parseName();
            boolean any = n.name != null;
            while (!atEnd()) {
                char c = s.charAt(i);
                if (c == '.') {
                    i++;
                    n.classes.add(requireToken());
                } else if (c == '#') {
                    i++;
                    n.id = requireToken();
                } else if (c == '[') {
                    parseAttrs(n);
                } else if (c == '{') {
                    n.text = parseBraced();
                } else {
                    break;
                }
                any = true;
            }
            if (!any) {
                throw new IllegalStateException("empty element at " + i);
            }
            if (n.name == null) {
                n.name = "div";               // .card / #main imply a div
            }
            return n;
        }

        private String parseName() {
            int start = i;
            while (!atEnd() && (Character.isLetterOrDigit(s.charAt(i))
                    || s.charAt(i) == '-')) {
                i++;
            }
            return i > start ? s.substring(start, i) : null;
        }

        private String requireToken() {
            int start = i;
            while (!atEnd() && (Character.isLetterOrDigit(s.charAt(i))
                    || s.charAt(i) == '-' || s.charAt(i) == '_'
                    || s.charAt(i) == '$')) {
                i++;
            }
            if (i == start) {
                throw new IllegalStateException("token expected at " + start);
            }
            return s.substring(start, i);
        }

        private void parseAttrs(Node n) {
            expect('[');
            while (!atEnd() && s.charAt(i) != ']') {
                while (!atEnd() && s.charAt(i) == ' ') {
                    i++;
                }
                if (!atEnd() && s.charAt(i) == ']') {
                    break;
                }
                String key = requireToken();
                String value = "";
                if (!atEnd() && s.charAt(i) == '=') {
                    i++;
                    if (!atEnd() && (s.charAt(i) == '"' || s.charAt(i) == '\'')) {
                        char q = s.charAt(i++);
                        int start = i;
                        while (!atEnd() && s.charAt(i) != q) {
                            i++;
                        }
                        value = s.substring(start, i);
                        expect(q);
                    } else {
                        int start = i;
                        while (!atEnd() && s.charAt(i) != ' ' && s.charAt(i) != ']') {
                            i++;
                        }
                        value = s.substring(start, i);
                    }
                }
                n.attrs.put(key, value);
            }
            expect(']');
        }

        private String parseBraced() {
            expect('{');
            int start = i;
            while (!atEnd() && s.charAt(i) != '}') {
                i++;
            }
            String text = s.substring(start, i);
            expect('}');
            return text;
        }

        private int parseInt() {
            int start = i;
            while (!atEnd() && Character.isDigit(s.charAt(i))) {
                i++;
            }
            if (i == start) {
                throw new IllegalStateException("count expected at " + start);
            }
            int value = Integer.parseInt(s.substring(start, i));
            if (value < 1 || value > 100) {   // *0 is meaningless, *1000 a typo
                throw new IllegalStateException("count out of range: " + value);
            }
            return value;
        }

        private void expect(char c) {
            if (atEnd() || s.charAt(i) != c) {
                throw new IllegalStateException("expected '" + c + "' at " + i);
            }
            i++;
        }
    }

    // ---- renderer --------------------------------------------------------

    private static final class Out {
        final StringBuilder sb = new StringBuilder();
        final String indentUnit;
        int caret = -1;                       // first useful empty spot

        Out(String indentUnit) {
            this.indentUnit = indentUnit;
        }

        void newline(int depth) {
            sb.append('\n');
            sb.append(indentUnit.repeat(depth));
        }

        void markCaret() {
            if (caret < 0) {
                caret = sb.length();
            }
        }
    }

    private static void render(Node n, Out out, int depth, int index, int count) {
        for (int rep = 1; rep <= n.times; rep++) {
            int idx = n.times > 1 ? rep : index;
            int total = n.times > 1 ? n.times : count;
            if (rep > 1) {
                out.newline(depth);
            }
            if (n.name == null) {             // group: render children in place
                for (int c = 0; c < n.children.size(); c++) {
                    if (c > 0) {
                        out.newline(depth);
                    }
                    render(n.children.get(c), out, depth, idx, total);
                }
                continue;
            }
            int loremCount = loremWords(n.name);
            if (loremCount >= 0) {            // lorem emits TEXT, not a tag
                if (n.id != null || !n.classes.isEmpty() || !n.attrs.isEmpty()
                        || n.text != null || !n.children.isEmpty()) {
                    // a decorated lorem is a typo, not intent — refuse the
                    // whole abbreviation (expand() catches this as null)
                    throw new IllegalStateException("lorem takes no decorations");
                }
                out.sb.append(loremText(loremCount));
                continue;
            }
            out.sb.append('<').append(n.name);
            if (n.id != null) {
                out.sb.append(" id=\"").append(number(n.id, idx, total)).append('"');
            }
            if (!n.classes.isEmpty()) {
                out.sb.append(" class=\"");
                for (int c = 0; c < n.classes.size(); c++) {
                    if (c > 0) {
                        out.sb.append(' ');
                    }
                    out.sb.append(number(n.classes.get(c), idx, total));
                }
                out.sb.append('"');
            }
            Map<String, String> attrs = new LinkedHashMap<>();
            for (String def : DEFAULT_ATTRS.getOrDefault(n.name, new String[0])) {
                attrs.put(def, "");
            }
            attrs.putAll(n.attrs);
            for (Map.Entry<String, String> e : attrs.entrySet()) {
                out.sb.append(' ').append(e.getKey()).append("=\"");
                if (e.getValue().isEmpty()) {
                    out.markCaret();
                } else {
                    out.sb.append(number(e.getValue(), idx, total));
                }
                out.sb.append('"');
            }
            if (VOID.contains(n.name)) {
                out.sb.append('>');
                continue;                     // no closing tag, no children
            }
            out.sb.append('>');
            if (!n.children.isEmpty()) {
                for (Node child : n.children) {
                    out.newline(depth + 1);
                    render(child, out, depth + 1, 1, 1);
                }
                out.newline(depth);
            } else if (n.text != null) {
                out.sb.append(number(n.text, idx, total));
            } else {
                out.markCaret();
            }
            out.sb.append("</").append(n.name).append('>');
        }
    }

    /**
     * The canonical passage, one source for every {@code lorem}
     * expansion — DETERMINISTIC by design (real Emmet randomizes; a
     * generator whose output changes between presses cannot be pinned
     * by a test or trusted in a diff).
     */
    private static final String[] LOREM_WORDS = ("lorem ipsum dolor sit amet"
            + " consectetur adipiscing elit sed do eiusmod tempor incididunt"
            + " ut labore et dolore magna aliqua enim ad minim veniam quis"
            + " nostrud exercitation ullamco laboris nisi aliquip ex ea"
            + " commodo consequat duis aute irure in reprehenderit voluptate"
            + " velit esse cillum eu fugiat nulla pariatur excepteur sint"
            + " occaecat cupidatat non proident sunt culpa qui officia"
            + " deserunt mollit anim id est laborum").split(" ");

    /** Word count for a {@code lorem}/{@code loremN} name, or -1 if not lorem. */
    private static int loremWords(String name) {
        if (name == null || !name.startsWith("lorem")) {
            return -1;
        }
        String n = name.substring(5);
        if (n.isEmpty()) {
            return 12;
        }
        try {
            int v = Integer.parseInt(n);
            return (v >= 1 && v <= 200) ? v : -1;
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    /** N words from the passage (cycling), capitalized, period-closed. */
    private static String loremText(int words) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words; i++) {
            String w = LOREM_WORDS[i % LOREM_WORDS.length];
            if (i == 0) {
                w = Character.toUpperCase(w.charAt(0)) + w.substring(1);
            } else {
                sb.append(' ');
            }
            sb.append(w);
        }
        return sb.append('.').toString();
    }

    /** {@code $} numbering: {@code item$} → item1..itemN; {@code $$} pads. */
    private static String number(String s, int index, int count) {
        if (s.indexOf('$') < 0) {
            return s;
        }
        StringBuilder r = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '$') {
                int run = 1;
                while (i + 1 < s.length() && s.charAt(i + 1) == '$') {
                    run++;
                    i++;
                }
                r.append(String.format("%0" + run + "d", index));
            } else {
                r.append(s.charAt(i));
            }
        }
        return r.toString();
    }
}
