package org.nmox.studio.editor.snippets;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.nmox.studio.editor.snippets.SnippetBody.Format;
import org.nmox.studio.editor.snippets.SnippetBody.FormatGroup;
import org.nmox.studio.editor.snippets.SnippetBody.FormatText;
import org.nmox.studio.editor.snippets.SnippetBody.Refused;
import org.nmox.studio.editor.snippets.SnippetBody.Transform;

/**
 * A snippet's {@code /regex/format/flags}, applied the way VS Code
 * applies it and bounded the way a stranger's regular expression has to
 * be.
 *
 * <p>The pattern arrives with a {@code git clone}. A pattern is a
 * program: {@code (a+)+$} against thirty characters runs for years, and
 * this one would run on the event thread at the moment somebody accepts
 * a completion. So it is bounded four ways, and a transform that
 * cannot be honoured inside the bounds refuses the WHOLE snippet (at
 * load) or the WHOLE insertion (at accept), never half of one:
 * <ol>
 * <li>the pattern is at most {@link #MAX_PATTERN_CHARS} characters and
 *     must compile;</li>
 * <li>a group that repeats without limit and holds a repeat without
 *     limit, the shape that is exponential on a short input, is refused
 *     when the file is read ({@link #repeatsARepeat});</li>
 * <li>the text it is matched against is at most
 *     {@link #MAX_INPUT_CHARS} characters;</li>
 * <li>the match itself runs against a clock ({@link #BUDGET_NANOS}): the
 *     matcher reads its input through a sequence that looks at the time,
 *     so a pattern the second rule could not recognise (overlapping
 *     alternatives, {@code (a|aa)+$}) still stops.</li>
 * </ol>
 * One clock a transform is not the whole bound, since a body may hold
 * hundreds of transforms that each stop just short of theirs. So the
 * callers share a budget too: one translation
 * ({@link SnippetTemplates#BUDGET_NANOS}), one file's trials when it is
 * read ({@link VsCodeSnippets#TRIAL_NANOS}), one change to a tab stop
 * ({@link SnippetTemplateProcessor#LIVE_BUDGET_NANOS}).
 *
 * <p>What is honoured: the flags {@code g i m s u}; in the format,
 * {@code $1}, {@code ${1}}, the conditionals, and the modifiers
 * {@code /upcase /downcase /capitalize /camelcase /pascalcase}. Any
 * other flag or modifier is refused by name. The expression is compiled
 * by {@link Pattern}, whose syntax is JavaScript's for what snippets
 * use; an expression Java cannot compile ({@code [^]}) is refused and
 * the log says what the compiler said.
 *
 * <p>Case is folded with {@link Locale#ROOT}: the text is an identifier
 * in a source file, and under a Turkish reader {@code title} must still
 * become {@code TITLE}.
 */
final class SnippetTransforms {

    /** A pattern longer than this is refused. */
    static final int MAX_PATTERN_CHARS = 512;

    /** A transform is never run over more text than this. */
    static final int MAX_INPUT_CHARS = 10_000;

    /** A transform that grows its input past this is refused. */
    static final int MAX_OUTPUT_CHARS = 100_000;

    /** How long one transform may match, in nanoseconds. */
    static final long BUDGET_NANOS = 50_000_000L;

    private static final Set<String> MODIFIERS =
            Set.of("upcase", "downcase", "capitalize", "camelcase", "pascalcase");

    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9]+");

    private SnippetTransforms() {
    }

    /** Why {@code t} cannot be honoured, as a clause for the log; null when it can. */
    static String refusal(Transform t) {
        if (t.regex().length() > MAX_PATTERN_CHARS) {
            return "its transform pattern is " + t.regex().length() + " characters, over the "
                    + MAX_PATTERN_CHARS + " limit";
        }
        for (int i = 0; i < t.flags().length(); i++) {
            char f = t.flags().charAt(i);
            if ("gimsu".indexOf(f) < 0) {
                return "its transform uses the flag " + printable(f) + ", which is not honoured";
            }
        }
        for (Format f : t.format()) {
            if (f instanceof FormatGroup g && g.modifier() != null && !MODIFIERS.contains(g.modifier())) {
                return "its transform uses the modifier /" + g.modifier() + ", which is not honoured";
            }
        }
        if (repeatsARepeat(t.regex())) {
            return "its transform pattern repeats a group that itself repeats, "
                    + "which can run without end on a short input";
        }
        try {
            compile(t);
        } catch (PatternSyntaxException ex) {
            return "its transform pattern does not compile: " + firstLine(ex.getDescription());
        }
        return null;
    }

    private static String printable(char c) {
        return c > ' ' && c < 0x7f ? String.valueOf(c) : "U+" + Integer.toHexString(c);
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "";
        }
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    private static Pattern compile(Transform t) {
        int flags = 0;
        if (t.flags().indexOf('i') >= 0) {
            flags |= Pattern.CASE_INSENSITIVE;
            if (t.flags().indexOf('u') >= 0) {
                flags |= Pattern.UNICODE_CASE;
            }
        }
        if (t.flags().indexOf('m') >= 0) {
            flags |= Pattern.MULTILINE;
        }
        if (t.flags().indexOf('s') >= 0) {
            flags |= Pattern.DOTALL;
        }
        return Pattern.compile(t.regex(), flags);
    }

    /**
     * Whether the expression holds a group repeated without limit whose
     * own content repeats without limit: {@code (a+)+}, {@code (\w*)*},
     * {@code ((ab)+c)*}. That shape tries every way of cutting the input
     * between the two repeats. A repeat with an upper limit
     * ({@code ?}, {@code {2,4}}) is not counted, and neither are the
     * characters of a class or an escape. Deeper than 64 groups is
     * answered true: a guard that cannot answer refuses.
     */
    static boolean repeatsARepeat(String re) {
        boolean[] holdsRepeat = new boolean[66];
        int depth = 0;
        boolean inClass = false;
        int n = re.length();
        int i = 0;
        while (i < n) {
            char c = re.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (inClass) {
                if (c == ']') {
                    inClass = false;
                }
                i++;
                continue;
            }
            if (c == '[') {
                inClass = true;
                i++;
                // a ] straight after [ or [^ is a member, not the end
                if (i < n && re.charAt(i) == '^') {
                    i++;
                }
                if (i < n && re.charAt(i) == ']') {
                    i++;
                }
                continue;
            }
            if (c == '(') {
                if (++depth > 64) {
                    return true;
                }
                holdsRepeat[depth] = false;
                i++;
                continue;
            }
            if (c == ')') {
                boolean inner = holdsRepeat[depth];
                if (depth > 0) {
                    depth--;
                }
                i++;
                boolean repeated = unbounded(re, i);
                if (repeated && inner) {
                    return true;
                }
                if (repeated || inner) {
                    holdsRepeat[depth] = true;
                }
                continue;
            }
            if (unbounded(re, i)) {
                holdsRepeat[depth] = true;
            }
            i++;
        }
        return false;
    }

    /** Whether an unlimited quantifier ({@code *}, {@code +}, {@code {n,}}) starts at {@code i}. */
    private static boolean unbounded(String re, int i) {
        if (i >= re.length()) {
            return false;
        }
        char c = re.charAt(i);
        if (c == '*' || c == '+') {
            return true;
        }
        if (c != '{') {
            return false;
        }
        int close = re.indexOf('}', i);
        if (close < 0) {
            return false;
        }
        String inside = re.substring(i + 1, close);
        int comma = inside.indexOf(',');
        if (comma < 0 || comma != inside.length() - 1) {
            return false;
        }
        for (int k = 0; k < comma; k++) {
            if (inside.charAt(k) < '0' || inside.charAt(k) > '9') {
                return false;
            }
        }
        return comma > 0;
    }

    /** {@code input} transformed. */
    static String apply(Transform t, String input) throws Refused {
        return apply(t, input, BUDGET_NANOS);
    }

    /** {@link #apply(Transform, String)} against a clock of the caller's choosing (the tests' seam). */
    static String apply(Transform t, String input, long budgetNanos) throws Refused {
        if (input.length() > MAX_INPUT_CHARS) {
            throw new Refused("its transform was handed " + input.length() + " characters, over the "
                    + MAX_INPUT_CHARS + " limit");
        }
        Pattern pattern;
        try {
            pattern = compile(t);
        } catch (PatternSyntaxException ex) {
            throw new Refused("its transform pattern does not compile: " + firstLine(ex.getDescription()));
        }
        boolean global = t.flags().indexOf('g') >= 0;
        try {
            Matcher m = pattern.matcher(new Timed(input, System.nanoTime() + budgetNanos));
            StringBuilder out = new StringBuilder();
            int last = 0;
            boolean matched = false;
            while (m.find()) {
                matched = true;
                out.append(input, last, m.start());
                replace(t, m, out);
                last = m.end();
                if (out.length() > MAX_OUTPUT_CHARS) {
                    throw new Refused("its transform grew the text past " + MAX_OUTPUT_CHARS + " characters");
                }
                if (!global) {
                    break;
                }
            }
            out.append(input, last, input.length());
            if (!matched && hasElse(t)) {
                // VS Code: no match at all, and the format has an else branch: the format alone
                StringBuilder alone = new StringBuilder();
                replace(t, null, alone);
                return alone.toString();
            }
            return out.toString();
        } catch (Overrun late) {
            throw new Refused("its transform did not finish within "
                    + (budgetNanos / 1_000_000L) + " ms on this text");
        } catch (StackOverflowError deep) {
            throw new Refused("its transform pattern recursed too deeply on this text");
        }
    }

    private static boolean hasElse(Transform t) {
        for (Format f : t.format()) {
            if (f instanceof FormatGroup g && g.elseValue() != null && !g.elseValue().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void replace(Transform t, Matcher m, StringBuilder out) {
        for (Format f : t.format()) {
            if (f instanceof FormatText text) {
                out.append(text.value());
            } else if (f instanceof FormatGroup g) {
                String value = "";
                if (m != null && g.group() <= m.groupCount()) {
                    String captured = m.group(g.group());
                    value = captured == null ? "" : captured;
                }
                out.append(resolve(g, value));
            }
        }
    }

    /** One group reference, as VS Code's {@code FormatString.resolve} answers it. */
    static String resolve(FormatGroup g, String value) {
        if (g.modifier() != null) {
            return switch (g.modifier()) {
                case "upcase" -> value.toUpperCase(Locale.ROOT);
                case "downcase" -> value.toLowerCase(Locale.ROOT);
                case "capitalize" -> value.isEmpty() ? ""
                        : value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
                case "pascalcase" -> words(value, true);
                case "camelcase" -> words(value, false);
                default -> value;
            };
        }
        if (!value.isEmpty() && g.ifValue() != null) {
            return g.ifValue();
        }
        if (value.isEmpty() && g.elseValue() != null) {
            return g.elseValue();
        }
        return value;
    }

    /** {@code user-profile_card} as {@code UserProfileCard} or {@code userProfileCard}. */
    private static String words(String value, boolean firstUpper) {
        Matcher m = WORD.matcher(value);
        StringBuilder out = new StringBuilder();
        boolean any = false;
        while (m.find()) {
            String word = m.group();
            String head = word.substring(0, 1);
            out.append(any || firstUpper ? head.toUpperCase(Locale.ROOT) : head.toLowerCase(Locale.ROOT));
            out.append(word, 1, word.length());
            any = true;
        }
        return any ? out.toString() : value;
    }

    /** The clock ran out; carries no stack, it is caught three frames up. */
    private static final class Overrun extends RuntimeException {

        private static final long serialVersionUID = 1L;

        Overrun() {
            super(null, null, false, false);
        }
    }

    /**
     * The matcher's input, read through a clock. {@link Matcher} asks
     * for every character it considers through {@link #charAt}, so a
     * match that is backtracking without end keeps passing through here,
     * and every 256th pass looks at the time.
     */
    private static final class Timed implements CharSequence {

        private final String text;
        private final long deadline;
        private int reads;

        Timed(String text, long deadline) {
            this.text = text;
            this.deadline = deadline;
        }

        @Override
        public char charAt(int index) {
            if ((++reads & 0xff) == 0 && System.nanoTime() - deadline > 0) {
                throw new Overrun();
            }
            return text.charAt(index);
        }

        @Override
        public int length() {
            return text.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return text.substring(start, end);
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
