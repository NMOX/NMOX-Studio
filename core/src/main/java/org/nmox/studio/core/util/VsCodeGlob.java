package org.nmox.studio.core.util;

import java.util.ArrayList;
import java.util.List;

/**
 * One glob pattern in VS Code's dialect — the one a repository's
 * {@code .vscode/settings.json} writes in {@code files.exclude},
 * {@code search.exclude} and {@code files.associations} — and the
 * question VS Code asks of it: does this path match? Pure: no disk, no
 * platform.
 *
 * <p><b>The dialect</b>, as VS Code documents it: {@code /} separates
 * path segments; {@code *} is zero or more characters inside one
 * segment; {@code ?} is one character inside a segment; {@code **} as a
 * whole segment is any number of segments, none included; {@code {a,b}}
 * groups alternatives (which may themselves hold slashes and
 * {@code **}); {@code [a-z]} is one character of a range and
 * {@code [!a-z]} one character outside it. The path is matched WHOLE,
 * relative to the folder the settings belong to — so {@code *.log} and
 * {@code node_modules} name entries of that folder only, and reaching
 * every depth is written {@code **}{@code /*.log}, as VS Code's own
 * defaults are ({@code **}{@code /.git}, {@code **}{@code /node_modules}).
 * A trailing {@code /} says nothing more.
 *
 * <p><b>How it is matched.</b> VS Code turns a pattern into a regular
 * expression ({@code glob.ts}); this class builds the same expression's
 * automaton and runs it over the path as a set of states (Thompson's
 * construction), so a match costs at most path length times
 * {@link #states()} and never backtracks, whatever a stranger wrote.
 * The construction follows VS Code's rules piece by piece, including the
 * ones nobody would design: a negated class matches a separator, and a
 * {@code **} that ends a pattern also matches the folder itself.
 *
 * <p><b>What is refused.</b> A pattern this class cannot read exactly as
 * VS Code does is not approximated: it {@linkplain #refusal() says why}
 * and matches nothing. Those are a pattern longer than
 * {@link #MAX_LENGTH}, a brace or bracket left open, a closing one with
 * no opener, braces inside braces (VS Code closes the outer one at the
 * first {@code }}, which is nobody's intent) and a character range
 * written backwards. A path longer than {@link #MAX_PATH} is not
 * matched.
 *
 * <p><b>Case.</b> VS Code ignores case where the file system does
 * (Windows and macOS) and not on Linux; the caller says which.
 */
public final class VsCodeGlob {

    /** A pattern longer than this is not one a person wrote. */
    public static final int MAX_LENGTH = 256;

    /** Paths longer than this are not matched. */
    public static final int MAX_PATH = 1_024;

    private static final String GLOBSTAR = "**";

    // ---- the automaton: one array per field, a state is an index ------

    private static final byte MATCH = 0;
    private static final byte SPLIT = 1;
    private static final byte LIT = 2;
    /** Any character but a separator ({@code [^/\\]}). */
    private static final byte NO_PATH = 3;
    /** A separator ({@code [/\\]}). */
    private static final byte PATH = 4;
    /** A regular expression's dot: anything but a line terminator. */
    private static final byte DOT = 5;
    private static final byte CLASS = 6;

    private final String pattern;
    private final String refusal;
    private final boolean ignoreCase;
    private final byte[] kind;
    private final char[] lit;
    private final int[] out;
    private final int[] out1;
    private final CharClass[] cls;
    private final int start;

    private VsCodeGlob(String pattern, String refusal, boolean ignoreCase, Builder b, int start) {
        this.pattern = pattern;
        this.refusal = refusal;
        this.ignoreCase = ignoreCase;
        if (b == null) {
            kind = new byte[0];
            lit = new char[0];
            out = new int[0];
            out1 = new int[0];
            cls = new CharClass[0];
        } else {
            int n = b.kind.size();
            kind = new byte[n];
            lit = new char[n];
            out = new int[n];
            out1 = new int[n];
            cls = new CharClass[n];
            for (int i = 0; i < n; i++) {
                kind[i] = b.kind.get(i);
                lit[i] = b.lit.get(i);
                out[i] = b.out.get(i);
                out1[i] = b.out1.get(i);
                cls[i] = b.cls.get(i);
            }
        }
        this.start = start;
    }

    /**
     * Reads one pattern. Never throws: a pattern that cannot be read
     * exactly comes back {@linkplain #refusal() refused} and matches
     * nothing.
     */
    public static VsCodeGlob compile(String pattern, boolean ignoreCase) {
        String trimmed = pattern == null ? "" : pattern.strip(); // VS Code trims
        if (trimmed.length() > MAX_LENGTH) {
            return new VsCodeGlob(trimmed, "longer than " + MAX_LENGTH + " characters", ignoreCase, null, -1);
        }
        if (trimmed.isEmpty()) {
            return new VsCodeGlob(trimmed, "empty", ignoreCase, null, -1);
        }
        try {
            Node tree = parse(trimmed);
            Builder b = new Builder();
            int match = b.state(MATCH, '\0', -1, -1, null);
            int start = b.build(tree, match);
            return new VsCodeGlob(trimmed, null, ignoreCase, b, start);
        } catch (Refused r) {
            return new VsCodeGlob(trimmed, r.getMessage(), ignoreCase, null, -1);
        }
    }

    /** The pattern as written, trimmed. */
    public String pattern() {
        return pattern;
    }

    /** Why this pattern matches nothing, or null when it was read. */
    public String refusal() {
        return refusal;
    }

    /** How many states one match walks at most per character of the path. */
    public int states() {
        return kind.length;
    }

    /**
     * Whether {@code path} — relative to the folder the pattern belongs
     * to, segments separated by {@code /} — matches, whole.
     */
    public boolean matches(String path) {
        if (refusal != null || path == null || path.length() > MAX_PATH) {
            return false;
        }
        int n = kind.length;
        int[] current = new int[n];
        int[] next = new int[n];
        int[] mark = new int[n]; // the step a state was last added in, plus one
        int[] stack = new int[2 * n + 2];
        int count = add(start, current, 0, mark, 1, stack);
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            int step = i + 2;
            int nextCount = 0;
            for (int k = 0; k < count; k++) {
                int s = current[k];
                if (accepts(s, c)) {
                    nextCount = add(out[s], next, nextCount, mark, step, stack);
                }
            }
            if (nextCount == 0) {
                return false;
            }
            int[] swap = current;
            current = next;
            next = swap;
            count = nextCount;
        }
        for (int k = 0; k < count; k++) {
            if (kind[current[k]] == MATCH) {
                return true;
            }
        }
        return false;
    }

    /** Adds {@code s} and everything reachable from it without reading a character. */
    private int add(int s, int[] list, int count, int[] mark, int step, int[] stack) {
        int top = 0;
        stack[top++] = s;
        while (top > 0) {
            int t = stack[--top];
            if (mark[t] == step) {
                continue;
            }
            mark[t] = step;
            if (kind[t] == SPLIT) {
                // a split is expanded once per step (the mark), and pushes
                // two: the stack holds at most 2 x states + 1
                stack[top++] = out1[t];
                stack[top++] = out[t];
            } else {
                list[count++] = t;
            }
        }
        return count;
    }

    private boolean accepts(int s, char c) {
        switch (kind[s]) {
            case LIT:
                return same(lit[s], c);
            case NO_PATH:
                return c != '/' && c != '\\';
            case PATH:
                return c == '/' || c == '\\';
            case DOT:
                return c != '\n' && c != '\r' && c != ' ' && c != ' ';
            case CLASS:
                return cls[s].has(c, ignoreCase);
            default:
                return false;
        }
    }

    private boolean same(char a, char b) {
        if (a == b) {
            return true;
        }
        return ignoreCase && (Character.toUpperCase(a) == Character.toUpperCase(b)
                || Character.toLowerCase(a) == Character.toLowerCase(b));
    }

    // ---- reading the pattern: VS Code's own rules, in its own order ----

    /** A pattern that is not read. */
    private static final class Refused extends Exception {
        private static final long serialVersionUID = 1L;

        Refused(String why) {
            super(why, null, false, false);
        }
    }

    /** A piece of the regular expression VS Code would build. */
    private abstract static class Node {
    }

    private static final class Seq extends Node {
        final List<Node> parts = new ArrayList<>();
    }

    private static final class Alt extends Node {
        final List<Node> choices = new ArrayList<>();
    }

    private static final class Star extends Node {
        final Node body;

        Star(Node body) {
            this.body = body;
        }
    }

    private static final class Atom extends Node {
        final byte kind;
        final char c;
        final CharClass cls;

        Atom(byte kind, char c, CharClass cls) {
            this.kind = kind;
            this.c = c;
            this.cls = cls;
        }
    }

    private static Atom atom(byte kind) {
        return new Atom(kind, '\0', null);
    }

    private static Node plus(Node body) {
        Seq s = new Seq();
        s.parts.add(body);
        s.parts.add(new Star(body));
        return s;
    }

    private static Seq seq(Node... parts) {
        Seq s = new Seq();
        s.parts.addAll(List.of(parts));
        return s;
    }

    /**
     * {@code **} as a whole segment. Not last: any run of "a separator"
     * or "a name and a separator". Last: also "a separator and a name",
     * which is how {@code out/**} reaches {@code out/a/b} — and why it
     * matches {@code out} itself.
     */
    private static Node globstar(boolean last) {
        Alt alt = new Alt();
        alt.choices.add(atom(PATH));
        alt.choices.add(seq(plus(atom(NO_PATH)), atom(PATH)));
        if (last) {
            alt.choices.add(seq(atom(PATH), plus(atom(NO_PATH))));
        }
        return new Star(alt);
    }

    /**
     * VS Code's {@code splitGlobAware}: split on {@code split} outside
     * braces and brackets; a trailing empty piece is not kept, a leading
     * one is.
     */
    static List<String> split(String pattern, char split) {
        List<String> out = new ArrayList<>();
        boolean inBraces = false;
        boolean inBrackets = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == split) {
                if (!inBraces && !inBrackets) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    continue;
                }
            } else if (c == '{') {
                inBraces = true;
            } else if (c == '}') {
                inBraces = false;
            } else if (c == '[') {
                inBrackets = true;
            } else if (c == ']') {
                inBrackets = false;
            }
            cur.append(c);
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    /** VS Code's {@code parseRegExp}. */
    private static Node parse(String pattern) throws Refused {
        Seq seq = new Seq();
        if (pattern.isEmpty()) {
            return seq;
        }
        List<String> segments = split(pattern, '/');
        boolean onlyGlobstars = true;
        for (String s : segments) {
            onlyGlobstars &= GLOBSTAR.equals(s);
        }
        if (onlyGlobstars) {
            seq.parts.add(new Star(atom(DOT))); // ".*": everything
            return seq;
        }
        boolean previousWasGlobstar = false;
        for (int index = 0; index < segments.size(); index++) {
            String segment = segments.get(index);
            if (GLOBSTAR.equals(segment)) {
                if (!previousWasGlobstar) {
                    seq.parts.add(globstar(index == segments.size() - 1));
                }
                previousWasGlobstar = true;
                continue;
            }
            segment(segment, seq);
            // the separator this segment was split on, unless what follows is
            // a closing ** (which brings its own)
            if (index < segments.size() - 1
                    && (!GLOBSTAR.equals(segments.get(index + 1)) || index + 2 < segments.size())) {
                seq.parts.add(atom(PATH));
            }
            previousWasGlobstar = false;
        }
        return seq;
    }

    private static void segment(String segment, Seq seq) throws Refused {
        boolean inBraces = false;
        StringBuilder braceVal = new StringBuilder();
        boolean inBrackets = false;
        boolean negated = false;
        List<Character> members = new ArrayList<>();
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c != '}' && inBraces) {
                if (c == '{') {
                    throw new Refused("braces inside braces");
                }
                braceVal.append(c);
                continue;
            }
            // a ] closes a class only once the class holds something
            if (inBrackets && (c != ']' || (members.isEmpty() && !negated))) {
                if ((c == '^' || c == '!') && members.isEmpty() && !negated) {
                    negated = true;
                } else if (c != '/') { // a separator is not allowed in a class: dropped
                    members.add(c);
                }
                continue;
            }
            switch (c) {
                case '{':
                    inBraces = true;
                    break;
                case '[':
                    inBrackets = true;
                    break;
                case '}': {
                    if (!inBraces) {
                        throw new Refused("a } with no {");
                    }
                    Alt alt = new Alt();
                    for (String choice : split(braceVal.toString(), ',')) {
                        alt.choices.add(parse(choice));
                    }
                    seq.parts.add(alt);
                    inBraces = false;
                    braceVal.setLength(0);
                    break;
                }
                case ']':
                    if (!inBrackets) {
                        throw new Refused("a ] with no [");
                    }
                    seq.parts.add(new Atom(CLASS, '\0', CharClass.of(negated, members)));
                    inBrackets = false;
                    negated = false;
                    members = new ArrayList<>();
                    break;
                case '?':
                    seq.parts.add(atom(NO_PATH));
                    break;
                case '*':
                    seq.parts.add(new Star(atom(NO_PATH)));
                    break;
                default:
                    seq.parts.add(new Atom(LIT, c, null));
            }
        }
        if (inBraces) {
            throw new Refused("a { with no }");
        }
        if (inBrackets) {
            throw new Refused("a [ with no ]");
        }
    }

    /** A bracket expression, as the regular-expression class VS Code writes for it. */
    private static final class CharClass {
        final boolean negated;
        final char[] from;
        final char[] to;

        private CharClass(boolean negated, char[] from, char[] to) {
            this.negated = negated;
            this.from = from;
            this.to = to;
        }

        /** {@code a-z} is a range when a member follows the dash; a dash first or last is itself. */
        static CharClass of(boolean negated, List<Character> members) throws Refused {
            List<char[]> ranges = new ArrayList<>();
            int i = 0;
            while (i < members.size()) {
                char a = members.get(i);
                if (i + 2 < members.size() && members.get(i + 1) == '-') {
                    char b = members.get(i + 2);
                    if (a > b) {
                        throw new Refused("the range " + a + "-" + b + " runs backwards");
                    }
                    ranges.add(new char[] {a, b});
                    i += 3;
                } else {
                    ranges.add(new char[] {a, a});
                    i++;
                }
            }
            char[] from = new char[ranges.size()];
            char[] to = new char[ranges.size()];
            for (int k = 0; k < from.length; k++) {
                from[k] = ranges.get(k)[0];
                to[k] = ranges.get(k)[1];
            }
            return new CharClass(negated, from, to);
        }

        boolean has(char c, boolean ignoreCase) {
            boolean in = in(c) || ignoreCase && (in(Character.toUpperCase(c)) || in(Character.toLowerCase(c)));
            return in != negated;
        }

        private boolean in(char c) {
            for (int k = 0; k < from.length; k++) {
                if (c >= from[k] && c <= to[k]) {
                    return true;
                }
            }
            return false;
        }
    }

    // ---- the tree, as states ---------------------------------------------

    private static final class Builder {
        final List<Byte> kind = new ArrayList<>();
        final List<Character> lit = new ArrayList<>();
        final List<Integer> out = new ArrayList<>();
        final List<Integer> out1 = new ArrayList<>();
        final List<CharClass> cls = new ArrayList<>();

        int state(byte k, char c, int o, int o1, CharClass cc) {
            kind.add(k);
            lit.add(c);
            out.add(o);
            out1.add(o1);
            cls.add(cc);
            return kind.size() - 1;
        }

        /** The state where {@code node} begins, given the state that follows it. */
        int build(Node node, int next) {
            if (node instanceof Atom a) {
                return state(a.kind, a.c, next, -1, a.cls);
            }
            if (node instanceof Seq s) {
                int at = next;
                for (int i = s.parts.size() - 1; i >= 0; i--) {
                    at = build(s.parts.get(i), at);
                }
                return at;
            }
            if (node instanceof Alt alt) {
                if (alt.choices.isEmpty()) {
                    return next; // "{}": nothing
                }
                int at = build(alt.choices.get(alt.choices.size() - 1), next);
                for (int i = alt.choices.size() - 2; i >= 0; i--) {
                    at = state(SPLIT, '\0', build(alt.choices.get(i), next), at, null);
                }
                return at;
            }
            if (node instanceof Star star) {
                int split = state(SPLIT, '\0', -1, next, null);
                out.set(split, build(star.body, split));
                return split;
            }
            throw new IllegalStateException("not a piece of a pattern: " + node);
        }
    }

    @Override
    public String toString() {
        return refusal == null ? pattern : pattern + " (refused: " + refusal + ")";
    }
}
