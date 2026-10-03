package org.nmox.studio.editor.snippets;

import java.util.ArrayList;
import java.util.List;

/**
 * A VS Code snippet body, parsed: the tree behind
 * {@code console.log('${1:label}', $1);$0}.
 *
 * <p>The grammar is the one in VS Code's "Snippets in Visual Studio
 * Code" page, and the parser follows VS Code's own, token for token,
 * including what it does with text that is NOT well formed, because a
 * body written against VS Code's behaviour has to mean the same thing
 * here:
 * <ul>
 * <li>{@code $1}, {@code ${1}}, {@code ${1:default}} (the default may
 *     hold anything, further placeholders included),
 *     {@code ${1|one,two|}}, and {@code $0} for where the caret ends;</li>
 * <li>{@code $NAME}, {@code ${NAME}}, {@code ${NAME:default}};</li>
 * <li>{@code ${1/regex/format/flags}} and {@code ${NAME/regex/format/flags}},
 *     the format holding {@code $1}, {@code ${1}},
 *     {@code ${1:/upcase}}, {@code ${1:+if}}, {@code ${1:?if:else}},
 *     {@code ${1:-else}} and {@code ${1:else}};</li>
 * <li>{@code \$}, <code>\&#125;</code> and {@code \\} are the character
 *     itself; in a choice {@code \,} and {@code \|} are too; a backslash
 *     before anything else is a backslash;</li>
 * <li>a construct that does not close (<code>$&#123;1:never closed</code>,
 *     a choice with no end) is the text it was written as, which is what
 *     VS Code inserts.</li>
 * </ul>
 *
 * <p>A body comes from a repository somebody cloned, so the parser is
 * bounded three ways and refuses BY NAME rather than degrade:
 * {@link #MAX_CHARS} characters, {@link #MAX_DEPTH} placeholders inside
 * one another, {@link #MAX_NODES} nodes. A transform whose regular
 * expression cannot be honoured safely ({@link SnippetTransforms})
 * refuses the whole body: half a transform is a wrong insertion, and a
 * wrong insertion into somebody's source file is worse than none.
 *
 * <p>Pure: no Swing, no platform, no disk.
 *
 * @param nodes the body, in order; adjacent text is one node
 */
public record SnippetBody(List<Node> nodes) {

    /** A body longer than this is not a snippet. */
    public static final int MAX_CHARS = 16 * 1024;

    /** Placeholders and variable defaults nested deeper than this are refused. */
    public static final int MAX_DEPTH = 16;

    /** A body with more tab stops, variables and format groups than this is refused; its text is bounded by {@link #MAX_CHARS}. */
    public static final int MAX_NODES = 4000;

    /** The largest tab stop number honoured; VS Code's own snippets stay far below it. */
    public static final int MAX_TAB_STOP = 9999;

    /** One piece of a body. */
    public sealed interface Node permits Text, TabStop, Variable {
    }

    /** Literal text, escapes already resolved. */
    public record Text(String value) implements Node {
    }

    /**
     * A tab stop: {@code $1}, {@code ${1:default}}, {@code ${1|a,b|}},
     * {@code ${1/re/fmt/}}. Number 0 is where the caret ends.
     *
     * @param number the stop's number; equal numbers mirror one another
     * @param children the default text's nodes, empty when there is none
     * @param choices the choices of {@code ${1|a,b|}}, empty otherwise
     * @param transform the transform, or null
     */
    public record TabStop(int number, List<Node> children, List<String> choices, Transform transform)
            implements Node {
    }

    /**
     * A variable: {@code $TM_FILENAME}, {@code ${NAME:default}},
     * {@code ${NAME/re/fmt/}}.
     *
     * @param name the variable's name as written
     * @param children the default's nodes, empty when there is none
     * @param transform the transform, or null
     */
    public record Variable(String name, List<Node> children, Transform transform) implements Node {
    }

    /**
     * {@code /regex/format/flags}.
     *
     * @param regex the regular expression's source, {@code \/} already a slash
     * @param format what each match is replaced with
     * @param flags the option letters as written
     */
    public record Transform(String regex, List<Format> format, String flags) {
    }

    /** One piece of a transform's format. */
    public sealed interface Format permits FormatText, FormatGroup {
    }

    /** Literal text in a format. */
    public record FormatText(String value) implements Format {
    }

    /**
     * A group reference in a format.
     *
     * @param group the capture group's number
     * @param modifier {@code upcase}, {@code downcase}, … or null
     * @param ifValue the text when the group matched, or null
     * @param elseValue the text when it did not, or null
     */
    public record FormatGroup(int group, String modifier, String ifValue, String elseValue)
            implements Format {
    }

    /**
     * A body, or an insertion, that is refused whole; the message says why, in English, for the log.
     * Not final for one reason: {@link SnippetTemplates.TooLarge}, the refusal the status line names.
     */
    public static class Refused extends Exception {

        private static final long serialVersionUID = 1L;

        /** @param reason why, as a clause that follows "because" */
        public Refused(String reason) {
            super(reason);
        }
    }

    /**
     * Parses {@code body}.
     *
     * @throws Refused when the body is over a bound or carries a transform
     *         that cannot be honoured; the message names which
     */
    public static SnippetBody parse(String body) throws Refused {
        if (body.length() > MAX_CHARS) {
            throw new Refused("its body is " + body.length() + " characters, over the "
                    + MAX_CHARS + " limit");
        }
        Parser p = new Parser(body);
        List<Node> out = new ArrayList<>();
        while (p.parse(out)) {
            // one construct, or one token of text, per turn
        }
        SnippetBody parsed = new SnippetBody(List.copyOf(merge(out)));
        String problem = firstTransformProblem(parsed.nodes());
        if (problem != null) {
            throw new Refused(problem);
        }
        return parsed;
    }

    private static String firstTransformProblem(List<Node> nodes) {
        for (Node n : nodes) {
            Transform t = n instanceof TabStop ts ? ts.transform()
                    : n instanceof Variable v ? v.transform() : null;
            if (t != null) {
                String problem = SnippetTransforms.refusal(t);
                if (problem != null) {
                    return problem;
                }
            }
            List<Node> children = n instanceof TabStop ts ? ts.children()
                    : n instanceof Variable v ? v.children() : List.of();
            String inner = firstTransformProblem(children);
            if (inner != null) {
                return inner;
            }
        }
        return null;
    }

    /** Adjacent text nodes joined, at every level. */
    private static List<Node> merge(List<Node> nodes) {
        List<Node> out = new ArrayList<>(nodes.size());
        StringBuilder text = null;
        for (Node n : nodes) {
            if (n instanceof Text t) {
                if (text == null) {
                    text = new StringBuilder();
                }
                text.append(t.value());
                continue;
            }
            if (text != null) {
                out.add(new Text(text.toString()));
                text = null;
            }
            if (n instanceof TabStop ts) {
                out.add(new TabStop(ts.number(), List.copyOf(merge(ts.children())), ts.choices(), ts.transform()));
            } else if (n instanceof Variable v) {
                out.add(new Variable(v.name(), List.copyOf(merge(v.children())), v.transform()));
            }
        }
        if (text != null) {
            out.add(new Text(text.toString()));
        }
        return out;
    }

    // token kinds, as VS Code's scanner cuts a body
    private static final int EOF = 0;
    private static final int DOLLAR = 1;
    private static final int COLON = 2;
    private static final int COMMA = 3;
    private static final int OPEN = 4;
    private static final int CLOSE = 5;
    private static final int BACKSLASH = 6;
    private static final int SLASH = 7;
    private static final int PIPE = 8;
    private static final int PLUS = 9;
    private static final int DASH = 10;
    private static final int QUESTION = 11;
    private static final int INT = 12;
    private static final int NAME = 13;
    private static final int OTHER = 14;
    private static final int ANY = -1;

    private static int single(char c) {
        return switch (c) {
            case '$' -> DOLLAR;
            case ':' -> COLON;
            case ',' -> COMMA;
            case '{' -> OPEN;
            case '}' -> CLOSE;
            case '\\' -> BACKSLASH;
            case '/' -> SLASH;
            case '|' -> PIPE;
            case '+' -> PLUS;
            case '-' -> DASH;
            case '?' -> QUESTION;
            default -> -1;
        };
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }

    /** The first character of a variable's name: the snippet grammar's {@code [_a-zA-Z]}, never a reader's letter. */
    private static boolean nameStart(char c) {
        return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /** VS Code's parser, on VS Code's tokens. */
    private static final class Parser {

        private final String s;
        private final int[] kind;
        private final int[] start;
        private final int count;
        /** index of the current token */
        private int at;
        private int depth;
        private int structure;

        Parser(String s) {
            this.s = s;
            int n = s.length();
            int[] kinds = new int[n + 1];
            int[] starts = new int[n + 2];
            int k = 0;
            int i = 0;
            while (i < n) {
                char c = s.charAt(i);
                starts[k] = i;
                int one = single(c);
                if (one >= 0) {
                    kinds[k++] = one;
                    i++;
                } else if (digit(c)) {
                    kinds[k++] = INT;
                    while (i < n && digit(s.charAt(i))) {
                        i++;
                    }
                } else if (nameStart(c)) {
                    kinds[k++] = NAME;
                    while (i < n && (nameStart(s.charAt(i)) || digit(s.charAt(i)))) {
                        i++;
                    }
                } else {
                    kinds[k++] = OTHER;
                    i++;
                    while (i < n && single(s.charAt(i)) < 0 && !digit(s.charAt(i)) && !nameStart(s.charAt(i))) {
                        i++;
                    }
                }
            }
            kinds[k] = EOF;
            starts[k] = n;
            starts[k + 1] = n;
            this.kind = kinds;
            this.start = starts;
            this.count = k;
        }

        private int kind() {
            return kind[at];
        }

        private String text(int token) {
            return s.substring(start[token], token >= count ? s.length() : start[token + 1]);
        }

        private boolean accept(int wanted) {
            if (kind() == EOF || (wanted != ANY && kind() != wanted)) {
                return false;
            }
            at++;
            return true;
        }

        /** The current token's text when it is {@code wanted} (and the parser moves on), else null. */
        private String take(int wanted) {
            if (kind() == EOF || (wanted != ANY && kind() != wanted)) {
                return null;
            }
            return text(at++);
        }

        private boolean backTo(int token) {
            at = token;
            return false;
        }

        private void counted() throws Refused {
            if (++structure > MAX_NODES) {
                throw new Refused("its body has more than " + MAX_NODES + " tab stops and variables");
            }
        }

        private void deeper() throws Refused {
            if (++depth > MAX_DEPTH) {
                throw new Refused("its placeholders are nested deeper than " + MAX_DEPTH);
            }
        }

        boolean parse(List<Node> out) throws Refused {
            return parseEscaped(out)
                    || parseTabStopOrVariableName(out)
                    || parseComplexPlaceholder(out)
                    || parseComplexVariable(out)
                    || parseAnything(out);
        }

        /** {@code \$}, {@code \}} and {@code \\} are the character; a lone backslash is a backslash. */
        private boolean parseEscaped(List<Node> out) throws Refused {
            String value = take(BACKSLASH);
            if (value == null) {
                return false;
            }
            String escaped = take(DOLLAR);
            if (escaped == null) {
                escaped = take(CLOSE);
            }
            if (escaped == null) {
                escaped = take(BACKSLASH);
            }
            out.add(new Text(escaped == null ? value : escaped));
            return true;
        }

        /** {@code $foo} and {@code $1}. */
        private boolean parseTabStopOrVariableName(List<Node> out) throws Refused {
            int token = at;
            if (!accept(DOLLAR)) {
                return backTo(token);
            }
            String name = take(NAME);
            if (name != null) {
                counted();
                out.add(new Variable(name, List.of(), null));
                return true;
            }
            String number = take(INT);
            if (number != null) {
                counted();
                out.add(new TabStop(number(number), List.of(), List.of(), null));
                return true;
            }
            return backTo(token);
        }

        private int number(String digits) throws Refused {
            if (digits.length() > 4) {
                throw new Refused("the number " + (digits.length() > 12 ? digits.substring(0, 12) + "…" : digits)
                        + " is over " + MAX_TAB_STOP);
            }
            return Integer.parseInt(digits);
        }

        /** {@code ${1}}, {@code ${1:children}}, {@code ${1|a,b|}}, {@code ${1/re/fmt/}}. */
        private boolean parseComplexPlaceholder(List<Node> out) throws Refused {
            int token = at;
            if (!accept(DOLLAR) || !accept(OPEN)) {
                return backTo(token);
            }
            String index = take(INT);
            if (index == null) {
                return backTo(token);
            }
            int n = number(index);
            if (accept(COLON)) {
                List<Node> children = new ArrayList<>();
                deeper();
                try {
                    while (true) {
                        if (accept(CLOSE)) {
                            counted();
                            out.add(new TabStop(n, children, List.of(), null));
                            return true;
                        }
                        if (parse(children)) {
                            continue;
                        }
                        // never closed: VS Code inserts what was written
                        out.add(new Text("${" + index + ":"));
                        out.addAll(children);
                        return true;
                    }
                } finally {
                    depth--;
                }
            }
            if (n > 0 && accept(PIPE)) {
                List<String> choices = new ArrayList<>();
                while (true) {
                    String choice = parseChoiceElement();
                    if (choice != null) {
                        choices.add(choice);
                        if (accept(COMMA)) {
                            continue;
                        }
                        if (accept(PIPE) && accept(CLOSE)) {
                            counted();
                            out.add(new TabStop(n, List.of(), List.copyOf(choices), null));
                            return true;
                        }
                    }
                    return backTo(token);
                }
            }
            if (accept(SLASH)) {
                Transform transform = parseTransform();
                if (transform == null) {
                    return backTo(token);
                }
                counted();
                out.add(new TabStop(n, List.of(), List.of(), transform));
                return true;
            }
            if (accept(CLOSE)) {
                counted();
                out.add(new TabStop(n, List.of(), List.of(), null));
                return true;
            }
            return backTo(token);
        }

        /** One choice, up to the next unescaped comma or pipe; null when there is none. */
        private String parseChoiceElement() {
            int token = at;
            StringBuilder value = new StringBuilder();
            while (kind() != COMMA && kind() != PIPE) {
                String piece;
                if (take(BACKSLASH) != null) {
                    piece = take(COMMA);
                    if (piece == null) {
                        piece = take(PIPE);
                    }
                    if (piece == null) {
                        piece = take(BACKSLASH);
                    }
                    if (piece == null) {
                        piece = "\\";
                    }
                } else {
                    piece = take(ANY);
                }
                if (piece == null) {
                    backTo(token);
                    return null;
                }
                value.append(piece);
            }
            if (value.length() == 0) {
                backTo(token);
                return null;
            }
            return value.toString();
        }

        /** {@code ${foo}}, {@code ${foo:children}}, {@code ${foo/re/fmt/}}. */
        private boolean parseComplexVariable(List<Node> out) throws Refused {
            int token = at;
            if (!accept(DOLLAR) || !accept(OPEN)) {
                return backTo(token);
            }
            String name = take(NAME);
            if (name == null) {
                return backTo(token);
            }
            if (accept(COLON)) {
                List<Node> children = new ArrayList<>();
                deeper();
                try {
                    while (true) {
                        if (accept(CLOSE)) {
                            counted();
                            out.add(new Variable(name, children, null));
                            return true;
                        }
                        if (parse(children)) {
                            continue;
                        }
                        out.add(new Text("${" + name + ":"));
                        out.addAll(children);
                        return true;
                    }
                } finally {
                    depth--;
                }
            }
            if (accept(SLASH)) {
                Transform transform = parseTransform();
                if (transform == null) {
                    return backTo(token);
                }
                counted();
                out.add(new Variable(name, List.of(), transform));
                return true;
            }
            if (accept(CLOSE)) {
                counted();
                out.add(new Variable(name, List.of(), null));
                return true;
            }
            return backTo(token);
        }

        /** The {@code regex/format/flags} and closing brace that follow a first slash; null when it does not close. */
        private Transform parseTransform() throws Refused {
            StringBuilder regex = new StringBuilder();
            while (true) {
                if (accept(SLASH)) {
                    break;
                }
                if (take(BACKSLASH) != null) {
                    String slash = take(SLASH);
                    regex.append(slash == null ? "\\" : slash);
                    continue;
                }
                String piece = take(ANY);
                if (piece == null) {
                    return null;
                }
                regex.append(piece);
            }
            List<Format> format = new ArrayList<>();
            while (true) {
                if (accept(SLASH)) {
                    break;
                }
                if (take(BACKSLASH) != null) {
                    String escaped = take(BACKSLASH);
                    if (escaped == null) {
                        escaped = take(SLASH);
                    }
                    format.add(new FormatText(escaped == null ? "\\" : escaped));
                    continue;
                }
                FormatGroup group = parseFormatGroup();
                if (group != null) {
                    counted();
                    format.add(group);
                    continue;
                }
                String piece = take(ANY);
                if (piece == null) {
                    return null;
                }
                format.add(new FormatText(piece));
            }
            StringBuilder flags = new StringBuilder();
            while (true) {
                if (accept(CLOSE)) {
                    break;
                }
                String piece = take(ANY);
                if (piece == null) {
                    return null;
                }
                flags.append(piece);
            }
            return new Transform(regex.toString(), List.copyOf(mergeFormat(format)), flags.toString());
        }

        private static List<Format> mergeFormat(List<Format> format) {
            List<Format> out = new ArrayList<>(format.size());
            StringBuilder text = null;
            for (Format f : format) {
                if (f instanceof FormatText t) {
                    if (text == null) {
                        text = new StringBuilder();
                    }
                    text.append(t.value());
                    continue;
                }
                if (text != null) {
                    out.add(new FormatText(text.toString()));
                    text = null;
                }
                out.add(f);
            }
            if (text != null) {
                out.add(new FormatText(text.toString()));
            }
            return out;
        }

        /** {@code $1}, {@code ${1}}, {@code ${1:/upcase}}, {@code ${1:+if}}, {@code ${1:?if:else}}, {@code ${1:-else}}, {@code ${1:else}}. */
        private FormatGroup parseFormatGroup() throws Refused {
            int token = at;
            if (!accept(DOLLAR)) {
                return null;
            }
            boolean complex = accept(OPEN);
            String index = take(INT);
            if (index == null) {
                backTo(token);
                return null;
            }
            int group = number(index);
            if (!complex) {
                return new FormatGroup(group, null, null, null);
            }
            if (accept(CLOSE)) {
                return new FormatGroup(group, null, null, null);
            }
            if (!accept(COLON)) {
                backTo(token);
                return null;
            }
            if (accept(SLASH)) {
                String modifier = take(NAME);
                if (modifier == null || !accept(CLOSE)) {
                    backTo(token);
                    return null;
                }
                return new FormatGroup(group, modifier, null, null);
            }
            if (accept(PLUS)) {
                String ifValue = until(CLOSE);
                if (ifValue != null && !ifValue.isEmpty()) {
                    return new FormatGroup(group, null, ifValue, null);
                }
            } else if (accept(DASH)) {
                String elseValue = until(CLOSE);
                if (elseValue != null && !elseValue.isEmpty()) {
                    return new FormatGroup(group, null, null, elseValue);
                }
            } else if (accept(QUESTION)) {
                String ifValue = until(COLON);
                if (ifValue != null && !ifValue.isEmpty()) {
                    String elseValue = until(CLOSE);
                    if (elseValue != null && !elseValue.isEmpty()) {
                        return new FormatGroup(group, null, ifValue, elseValue);
                    }
                }
            } else {
                String elseValue = until(CLOSE);
                if (elseValue != null && !elseValue.isEmpty()) {
                    return new FormatGroup(group, null, null, elseValue);
                }
            }
            backTo(token);
            return null;
        }

        /** The text up to the next {@code wanted} token, escapes resolved, and past it; null when there is none. */
        private String until(int wanted) {
            int from = at;
            while (kind() != wanted) {
                if (kind() == EOF) {
                    return null;
                }
                if (kind() == BACKSLASH) {
                    at++;
                    if (kind() != DOLLAR && kind() != CLOSE && kind() != BACKSLASH) {
                        return null;
                    }
                }
                at++;
            }
            String raw = s.substring(start[from], start[at]);
            at++;
            StringBuilder out = new StringBuilder(raw.length());
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c == '\\' && i + 1 < raw.length()
                        && (raw.charAt(i + 1) == '$' || raw.charAt(i + 1) == '}' || raw.charAt(i + 1) == '\\')) {
                    out.append(raw.charAt(++i));
                } else {
                    out.append(c);
                }
            }
            return out.toString();
        }

        private boolean parseAnything(List<Node> out) throws Refused {
            String piece = take(ANY);
            if (piece == null) {
                return false;
            }
            out.add(new Text(piece));
            return true;
        }
    }
}
