package org.nmox.studio.editor.standards;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * One {@code .editorconfig} section glob, matched in guaranteed
 * polynomial time.
 *
 * <p><b>Why this is not a regex.</b> A cloned repository writes both the
 * section header and the file name, and a {@code **} translated to
 * {@code .*} backtracks: {@code [**a**a…**b]} against {@code aaaa…a.js}
 * took 2.3 s per match at twelve groups over a 28-character name (measured
 * by the 3.1 review) — its work is the number of ways to place the groups
 * in the name, C(28,12) there and C(40,20) ≈ 1.4×10¹¹ for twenty groups
 * over forty characters — on the one-thread lane that resolves every open
 * file's indentation. So the glob compiles to a small automaton and the
 * match runs the automaton over the path once, tracking the SET of states
 * reachable at each position — work is bounded by
 * {@code states × (length + 1) × }{@value #MAX_NUMBER_WIDTH}, whatever the
 * glob says.
 *
 * <p><b>What it speaks</b> (spec.editorconfig.org; every glob the regex
 * translation it replaced matched still matches, with four deliberate
 * corrections to the spec's side: {@code [!c]} no longer matches
 * {@code /}; {@code {single}} and {@code {}} are literal rather than a
 * one-way alternation; brace alternatives are globs rather than quoted
 * literals; and a numeric range is not capped at 500 values):
 * <ul>
 * <li>{@code *} any run of characters except {@code /};
 *     {@code **} any run including {@code /};</li>
 * <li>{@code ?} one character except {@code /};</li>
 * <li>{@code [seq]}, {@code [!seq]} with {@code a-z} ranges — a class
 *     never matches {@code /}; a {@code ]} first in the class is a member;
 *     an unclosed {@code [}, or one whose class would contain {@code /},
 *     is a literal {@code [};</li>
 * <li>{@code {s1,s2}} alternatives, each itself a glob, nested freely;
 *     {@code {n1..n2}} any integer in the range written without leading
 *     zeros; a brace with no comma ({@code {single}}, {@code {}}) or no
 *     closing brace is literal, as the spec's own tests require;</li>
 * <li>{@code \x} is the literal {@code x};</li>
 * <li>a glob with no {@code /} matches the file name at any depth; one
 *     with a {@code /} (a leading one included) is anchored to the
 *     directory of the {@code .editorconfig} that holds it.</li>
 * </ul>
 *
 * <p><b>Refusals.</b> A glob past {@value #MAX_GLOB_LENGTH} characters,
 * {@value #MAX_ALTERNATIVES} brace alternatives, {@value #MAX_NESTING}
 * levels of nested braces or {@value #MAX_STATES} automaton states is
 * refused: it never throws and it matches no file, and
 * {@link #refusal()} says why so the parser can log it once.
 */
final class EditorConfigGlob {

    static final int MAX_GLOB_LENGTH = 1024;
    static final int MAX_ALTERNATIVES = 1000;
    static final int MAX_NESTING = 32;
    static final int MAX_STATES = 2048;
    /** A range number is at most a sign and eighteen digits (fits a long). */
    static final int MAX_NUMBER_WIDTH = 19;

    private static final byte LIT = 0;
    private static final byte ANY = 1;
    private static final byte CLASS = 2;
    private static final byte STAR = 3;
    private static final byte DSTAR = 4;
    private static final byte NUM = 5;
    private static final byte SPLIT = 6;
    private static final byte MATCH = 7;

    private final String refusal;
    private final boolean anywhere;
    private final int start;
    private final byte[] kind;
    private final char[] ch;
    private final int[] next;
    private final int[][] targets;   // SPLIT
    private final int[][] classes;   // CLASS: lo,hi pairs
    private final boolean[] negated; // CLASS
    private final long[] lo;         // NUM
    private final long[] hi;         // NUM

    private EditorConfigGlob(String refusal, boolean anywhere, int start, List<State> states) {
        this.refusal = refusal;
        this.anywhere = anywhere;
        this.start = start;
        int n = states.size();
        kind = new byte[n];
        ch = new char[n];
        next = new int[n];
        targets = new int[n][];
        classes = new int[n][];
        negated = new boolean[n];
        lo = new long[n];
        hi = new long[n];
        for (int i = 0; i < n; i++) {
            State s = states.get(i);
            kind[i] = s.kind;
            ch[i] = s.ch;
            next[i] = s.next;
            targets[i] = s.targets;
            classes[i] = s.ranges;
            negated[i] = s.negated;
            lo[i] = s.lo;
            hi[i] = s.hi;
        }
    }

    /** Compiles a section header's glob; never throws. */
    static EditorConfigGlob compile(String glob) {
        try {
            return build(glob);
        } catch (Refused r) {
            return refused(r.getMessage());
        } catch (RuntimeException | StackOverflowError e) {
            return refused("unreadable glob (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static EditorConfigGlob refused(String why) {
        return new EditorConfigGlob(why, false, 0, List.of());
    }

    /** Why this glob matches nothing, or null when it is a working glob. */
    String refusal() {
        return refusal;
    }

    /** Whether the path (relative to the config's directory, {@code /}-separated) matches. */
    boolean matches(String path) {
        return evaluate(path) >= 0;
    }

    /**
     * The match itself, reporting its work: the number of (state, position)
     * visits it made, {@code >= 0} on a match and {@code -(work + 1)} on a
     * miss. The work count is what the adversarial test bounds, so a
     * backtracking regression fails by count rather than by clock.
     */
    long evaluate(String path) {
        if (refusal != null) {
            return -1;
        }
        int len = path.length();
        int n = kind.length;
        BitSet[] reach = new BitSet[len + 1];
        int[] stack = new int[n];
        long work = 0;
        boolean matched = false;
        for (int j = 0; j <= len; j++) {
            BitSet r = reach[j];
            if (j == 0 || (anywhere && path.charAt(j - 1) == '/')) {
                if (r == null) {
                    r = new BitSet(n);
                    reach[j] = r;
                }
                r.set(start);
            }
            if (r == null) {
                continue;
            }
            reach[j] = null;
            // epsilon closure, in place
            int top = 0;
            for (int s = r.nextSetBit(0); s >= 0; s = r.nextSetBit(s + 1)) {
                stack[top++] = s;
            }
            while (top > 0) {
                int s = stack[--top];
                work++;
                if (kind[s] == SPLIT) {
                    for (int t : targets[s]) {
                        if (!r.get(t)) {
                            r.set(t);
                            stack[top++] = t;
                        }
                    }
                } else if ((kind[s] == STAR || kind[s] == DSTAR) && !r.get(next[s])) {
                    r.set(next[s]);
                    stack[top++] = next[s];
                }
            }
            if (j == len) {
                matched = r.get(0);
                break;
            }
            char c = path.charAt(j);
            for (int s = r.nextSetBit(0); s >= 0; s = r.nextSetBit(s + 1)) {
                work++;
                switch (kind[s]) {
                    case LIT -> {
                        if (c == ch[s]) {
                            mark(reach, j + 1, next[s], n);
                        }
                    }
                    case ANY -> {
                        if (c != '/') {
                            mark(reach, j + 1, next[s], n);
                        }
                    }
                    case CLASS -> {
                        if (c != '/' && inClass(classes[s], c) != negated[s]) {
                            mark(reach, j + 1, next[s], n);
                        }
                    }
                    case STAR -> {
                        if (c != '/') {
                            mark(reach, j + 1, s, n);
                        }
                    }
                    case DSTAR -> mark(reach, j + 1, s, n);
                    case NUM -> work += numberEnds(path, j, s, reach, n);
                    default -> {
                        // SPLIT and MATCH consume nothing
                    }
                }
            }
        }
        return matched ? work : -(work + 1);
    }

    private static void mark(BitSet[] reach, int at, int state, int n) {
        BitSet b = reach[at];
        if (b == null) {
            b = new BitSet(n);
            reach[at] = b;
        }
        b.set(state);
    }

    private static boolean inClass(int[] ranges, char c) {
        for (int i = 0; i < ranges.length; i += 2) {
            if (c >= ranges[i] && c <= ranges[i + 1]) {
                return true;
            }
        }
        return false;
    }

    /** Every end of an in-range integer starting at {@code j}; returns the characters read. */
    private int numberEnds(String path, int j, int s, BitSet[] reach, int n) {
        int len = path.length();
        int k = j;
        boolean negative = k < len && path.charAt(k) == '-';
        if (negative) {
            k++;
        }
        int digitsFrom = k;
        long value = 0;
        while (k < len && k - digitsFrom < MAX_NUMBER_WIDTH - 1
                && path.charAt(k) >= '0' && path.charAt(k) <= '9') {
            value = value * 10 + (path.charAt(k) - '0');
            k++;
            // no leading zeros: "060" is not sixty, "0" is zero
            if (k - digitsFrom > 1 && path.charAt(digitsFrom) == '0') {
                break;
            }
            long signed = negative ? -value : value;
            if (!(negative && value == 0) && signed >= lo[s] && signed <= hi[s]) {
                mark(reach, k, next[s], n);
            }
        }
        return k - j;
    }

    // ---- compilation ----------------------------------------------------

    private static final class Refused extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Refused(String why) {
            super(why, null, false, false);
        }
    }

    private static final class State {
        byte kind;
        char ch;
        int next = -1;
        int[] targets;
        int[] ranges;
        boolean negated;
        long lo;
        long hi;
    }

    private sealed interface Node permits Lit, Any, Star, Klass, Num, Alt {
    }

    private record Lit(char c) implements Node {
    }

    private record Any() implements Node {
    }

    private record Star(boolean crossesSlash) implements Node {
    }

    private record Klass(int[] ranges, boolean negated) implements Node {
    }

    private record Num(long lo, long hi) implements Node {
    }

    private record Alt(List<List<Node>> options) implements Node {
    }

    private static EditorConfigGlob build(String glob) {
        if (glob.length() > MAX_GLOB_LENGTH) {
            throw new Refused("longer than " + MAX_GLOB_LENGTH + " characters");
        }
        String g = glob;
        boolean anchored = g.startsWith("/");
        if (anchored) {
            g = g.substring(1);
        }
        boolean anywhere = !anchored && g.indexOf('/') < 0;
        Parser p = new Parser(g);
        List<Node> seq = p.sequence(0, g.length(), 0);
        List<State> states = new ArrayList<>();
        State match = new State();
        match.kind = MATCH;
        states.add(match); // state 0 is always MATCH
        int start = emit(seq, 0, states);
        return new EditorConfigGlob(null, anywhere, start, states);
    }

    private static int emit(List<Node> seq, int cont, List<State> states) {
        int c = cont;
        for (int i = seq.size() - 1; i >= 0; i--) {
            c = emit(seq.get(i), c, states);
        }
        return c;
    }

    private static int emit(Node node, int cont, List<State> states) {
        State s = new State();
        s.next = cont;
        switch (node) {
            case Lit l -> {
                s.kind = LIT;
                s.ch = l.c();
            }
            case Any a -> s.kind = ANY;
            case Star st -> s.kind = st.crossesSlash() ? DSTAR : STAR;
            case Klass k -> {
                s.kind = CLASS;
                s.ranges = k.ranges();
                s.negated = k.negated();
            }
            case Num num -> {
                s.kind = NUM;
                s.lo = num.lo();
                s.hi = num.hi();
            }
            case Alt alt -> {
                int[] t = new int[alt.options().size()];
                for (int i = 0; i < t.length; i++) {
                    t[i] = emit(alt.options().get(i), cont, states);
                }
                s.kind = SPLIT;
                s.targets = t;
            }
        }
        if (states.size() >= MAX_STATES) {
            throw new Refused("more than " + MAX_STATES + " pattern states");
        }
        states.add(s);
        return states.size() - 1;
    }

    /** Recursive descent over the glob text; every region is [from, to). */
    private static final class Parser {
        private final String g;
        private int alternatives;

        Parser(String g) {
            this.g = g;
        }

        List<Node> sequence(int from, int to, int depth) {
            List<Node> out = new ArrayList<>();
            int i = from;
            while (i < to) {
                char c = g.charAt(i);
                switch (c) {
                    case '\\' -> {
                        if (i + 1 < to) {
                            out.add(new Lit(g.charAt(i + 1)));
                            i += 2;
                        } else {
                            out.add(new Lit('\\'));
                            i++;
                        }
                    }
                    case '*' -> {
                        boolean dbl = i + 1 < to && g.charAt(i + 1) == '*';
                        out.add(new Star(dbl));
                        i++;
                        while (dbl && i < to && g.charAt(i) == '*') {
                            i++;
                        }
                    }
                    case '?' -> {
                        out.add(new Any());
                        i++;
                    }
                    case '[' -> i = bracket(i, to, out);
                    case '{' -> i = brace(i, to, depth, out);
                    default -> {
                        out.add(new Lit(c));
                        i++;
                    }
                }
            }
            return out;
        }

        /** A character class, or a literal {@code [} when it does not close cleanly. */
        private int bracket(int open, int to, List<Node> out) {
            int i = open + 1;
            boolean neg = i < to && g.charAt(i) == '!';
            if (neg) {
                i++;
            }
            List<Integer> ranges = new ArrayList<>();
            boolean first = true;
            while (i < to) {
                char c = g.charAt(i);
                if (c == ']' && !first) {
                    int[] r = new int[ranges.size()];
                    for (int k = 0; k < r.length; k++) {
                        r[k] = ranges.get(k);
                    }
                    out.add(new Klass(r, neg));
                    return i + 1;
                }
                first = false;
                if (c == '/') {
                    break; // a class never spans a path separator
                }
                if (c == '\\' && i + 1 < to) {
                    c = g.charAt(++i);
                }
                char low = c;
                char high = c;
                if (i + 2 < to && g.charAt(i + 1) == '-' && g.charAt(i + 2) != ']') {
                    int h = i + 2;
                    char hc = g.charAt(h);
                    if (hc == '\\' && h + 1 < to) {
                        hc = g.charAt(++h);
                    }
                    if (hc == '/') {
                        break;
                    }
                    high = hc;
                    i = h;
                    if (high < low) {
                        char t = low;
                        low = high;
                        high = t;
                    }
                }
                ranges.add((int) low);
                ranges.add((int) high);
                i++;
            }
            out.add(new Lit('['));
            return open + 1;
        }

        /** Alternatives, a numeric range, or a literal {@code {}. */
        private int brace(int open, int to, int depth, List<Node> out) {
            int close = matchingClose(open, to);
            if (close < 0) {
                out.add(new Lit('{'));
                return open + 1;
            }
            Num range = numericRange(open + 1, close);
            if (range != null) {
                out.add(range);
                return close + 1;
            }
            List<int[]> pieces = splitTopLevel(open + 1, close);
            if (pieces.size() < 2) {
                out.add(new Lit('{')); // {single} and {} are literal
                return open + 1;
            }
            if (depth + 1 > MAX_NESTING) {
                throw new Refused("braces nested deeper than " + MAX_NESTING);
            }
            alternatives += pieces.size();
            if (alternatives > MAX_ALTERNATIVES) {
                throw new Refused("more than " + MAX_ALTERNATIVES + " brace alternatives");
            }
            List<List<Node>> options = new ArrayList<>(pieces.size());
            for (int[] piece : pieces) {
                options.add(sequence(piece[0], piece[1], depth + 1));
            }
            out.add(new Alt(options));
            return close + 1;
        }

        private int matchingClose(int open, int to) {
            int depth = 0;
            for (int i = open; i < to; i++) {
                char c = g.charAt(i);
                if (c == '\\') {
                    i++;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}' && --depth == 0) {
                    return i;
                }
            }
            return -1;
        }

        private List<int[]> splitTopLevel(int from, int to) {
            List<int[]> pieces = new ArrayList<>();
            int depth = 0;
            int pieceStart = from;
            for (int i = from; i < to; i++) {
                char c = g.charAt(i);
                if (c == '\\') {
                    i++;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    pieces.add(new int[]{pieceStart, i});
                    pieceStart = i + 1;
                }
            }
            pieces.add(new int[]{pieceStart, to});
            return pieces;
        }

        /** {@code n1..n2} with optional minus signs, or null. */
        private Num numericRange(int from, int to) {
            String inner = g.substring(from, to);
            int dots = inner.indexOf("..");
            if (dots <= 0 || !isInteger(inner.substring(0, dots))
                    || !isInteger(inner.substring(dots + 2))) {
                return null;
            }
            long a = Long.parseLong(inner.substring(0, dots));
            long b = Long.parseLong(inner.substring(dots + 2));
            return new Num(Math.min(a, b), Math.max(a, b));
        }

        private static boolean isInteger(String s) {
            int start = s.startsWith("-") ? 1 : 0;
            if (s.length() == start || s.length() - start > 18) {
                return false;
            }
            for (int i = start; i < s.length(); i++) {
                if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                    return false;
                }
            }
            return true;
        }
    }
}
