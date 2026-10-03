package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A task's {@code problemMatcher}, read and run (3.6.0): the pure half
 * behind "the output of this task becomes findings". Nothing here spawns,
 * paints or publishes; it answers "which matchers does this task declare,
 * and which of them can this IDE apply" ({@link #read}), and, fed a
 * process's output line by line, "which problems did it report" ({@link
 * Session}).
 *
 * <p><b>What is honoured.</b> VS Code's own semantics, read from its
 * {@code problemMatcher.ts} and {@code problemCollectors.ts}:
 * <ul>
 * <li>a matcher by name ({@code "$tsc"}) from {@link #builtIns}, an inline
 * matcher object, an {@code {"base": "$tsc", …}} extension of a built-in,
 * or an array of those;</li>
 * <li>{@code pattern}: one pattern object (VS Code's defaults: file 1,
 * line 2, column 3, message 0 — or file 1, message 0 with a {@code
 * location} or {@code "kind": "file"}), an array of them for output that
 * spans lines (no defaults; {@code loop} on the last one), or the name of
 * a built-in pattern ({@code "$tsc"});</li>
 * <li>{@code fileLocation}: {@code "absolute"}, {@code "relative"} (to
 * the workspace folder), {@code ["relative", "<folder>"]}, {@code
 * "autoDetect"} and {@code ["autoDetect", "<folder>"]} (relative when a
 * file of that name is there, else absolute);</li>
 * <li>{@code severity} as the default when the pattern captures none,
 * and a captured one read as VS Code reads it ({@code error}, {@code
 * warning} / {@code warn}, {@code info}, then {@code E} / {@code W} /
 * {@code I}, {@code hint} and {@code note});</li>
 * <li>{@code background} (or its older spelling {@code watching}) on a
 * task that {@code isBackground}: see {@link Signal}.</li>
 * </ul>
 * The line machine is VS Code's: a matcher of N patterns is tried on the
 * last N lines; the first that matches takes them; a looping matcher
 * keeps its last pattern going on the lines that follow, and the line
 * that ends the loop may begin the next match.
 *
 * <p><b>What is not applied, by name.</b> A {@code $name} this class has
 * no copy of (an extension contributes it), a matcher that is not written
 * the way VS Code reads one, a {@code fileLocation} of {@code "search"}
 * or one whose folder uses a variable only an editor or the user can
 * fill, and a regular expression that does not compile here. Each is a
 * {@link Skipped} with the matcher's name and the reason; the task runs
 * all the same, as it does in VS Code, which warns and carries on.
 * {@code applyTo} is read by nothing here: every finding is reported,
 * whether or not the file is open.
 *
 * <p><b>The regular expressions are a repository's own text</b> and each
 * is run against every line the task prints, so the work is bounded four
 * ways. A line over {@link #MAX_LINE} characters is not matched (counted,
 * and said). Each expression may read at most {@link #STEP_BUDGET}
 * characters per line — a count of what the engine actually reads, not a
 * clock: catastrophic backtracking is cut off after the same amount of
 * work on every machine, and a test of it cannot flake — and a matcher
 * that runs over it on {@link #MAX_STRIKES} lines is switched off for
 * the rest of the run, by name. At most {@link #MAX_FINDINGS} findings
 * are kept per batch; the rest are counted. And a task is read by at
 * most {@link #MAX_MATCHERS} matchers of at most {@link #MAX_PATTERNS}
 * patterns each, no expression longer than {@link #MAX_REGEXP}.
 *
 * <p><b>JavaScript's expressions, in Java's engine.</b> VS Code compiles
 * each {@code regexp} with {@code new RegExp(text)}: no flags. {@link
 * #toJava} rewrites the few places where the same text means something
 * else in {@code java.util.regex} — {@code \s} (JavaScript's set is
 * wider), {@code .} and {@code $} (Java's stop at more line ends),
 * {@code [} and {@code &&} inside a class, a brace that is not a
 * quantifier, {@code \v}, {@code \0}, {@code \cx}, {@code [\b]} — into
 * text with exactly JavaScript's meaning, and refuses, naming the
 * construct, what cannot be said exactly: an escaped letter that is an
 * escape in Java and a plain letter in JavaScript ({@code \h}, {@code
 * \R}, {@code \Q}, {@code \p}, …), {@code []} and {@code [^]}, an inline
 * flag group, a possessive quantifier. Whatever Java then refuses to
 * compile is refused too. One difference is left standing and said here:
 * Java matches by code point and JavaScript (without its {@code u} flag)
 * by UTF-16 unit, so {@code .} takes a whole emoji here and half of one
 * there.
 */
final class VsCodeProblemMatchers {

    /** The most matchers one task's output is read by. */
    static final int MAX_MATCHERS = 16;

    /** The most patterns in one multi-line matcher. */
    static final int MAX_PATTERNS = 16;

    /** The longest regular expression read, in characters. */
    static final int MAX_REGEXP = 2_000;

    /** A line longer than this (in characters, escape sequences stripped) is not matched. */
    static final int MAX_LINE = 2_000;

    /** The most findings one batch keeps; the rest are counted. */
    static final int MAX_FINDINGS = 2_000;

    /** How many characters one expression may read on one line before it is cut off. */
    static final int STEP_BUDGET = 5_000_000;

    /** How many cut-off lines switch a matcher off for the rest of the run. */
    static final int MAX_STRIKES = 10;

    /** The longest message kept, in characters; a longer one is clipped with an ellipsis. */
    static final int MAX_MESSAGE = 1_000;

    private VsCodeProblemMatchers() {
    }

    /* ------------------------------------------------------------- the model */

    /** How serious a finding is. */
    enum Severity {
        ERROR, WARNING, INFO
    }

    /** How a matched file name becomes a file. */
    enum FileLocation {
        ABSOLUTE, RELATIVE, AUTO_DETECT
    }

    /**
     * A regular expression: the JavaScript text the file (or VS Code)
     * wrote and what it compiled to here. Two are equal when their text
     * is — a {@link Pattern} knows no equality of its own.
     */
    record Rx(String source, Pattern pattern) {

        @Override
        public boolean equals(Object o) {
            return o instanceof Rx other && source.equals(other.source);
        }

        @Override
        public int hashCode() {
            return source.hashCode();
        }
    }

    /**
     * One pattern of a matcher: its expression and the capture group
     * each part of a problem is read from, -1 where the pattern names
     * none.
     */
    record Line(Rx regexp, int file, int location, int line, int column, int endLine, int endColumn,
            int severity, int code, int message, boolean loop) {
    }

    /** A background matcher's two expressions, and whether it is active before the first line. */
    record Watch(boolean activeOnStart, Rx begins, Rx ends) {
    }

    /**
     * One matcher this IDE can apply. {@code name} is what a sentence
     * calls it: {@code $tsc}, or {@code #2} for the second entry of a
     * task's list when it is written inline. {@code prefix} is the
     * folder a relative file name is resolved against, as written
     * (variables and all), or null for {@link FileLocation#ABSOLUTE}.
     */
    record Matcher(String name, String owner, String source, Severity severity, FileLocation location,
            String prefix, boolean fileKind, List<Line> lines, Watch watch) {
    }

    /** Why a matcher is not applied. */
    enum Why {
        /** A {@code $name} nothing here defines; detail = the name. */
        UNKNOWN,
        /** A regular expression Java cannot compile; detail = the expression, clipped. */
        REGEXP,
        /** A regular expression that means something else in Java; detail = the construct. */
        DIALECT,
        /** Not written the way VS Code reads one; detail = the JSON key at fault. */
        INVALID,
        /** A file location this IDE cannot resolve; detail = {@code search}, or the variable. */
        FILE_LOCATION,
        /** A regular expression over {@link #MAX_REGEXP}, or more than {@link #MAX_PATTERNS} patterns; detail blank. */
        TOO_LARGE,
        /** More than {@link #MAX_MATCHERS} matchers on one task; detail blank. */
        TOO_MANY
    }

    /** A matcher the task declares and this IDE does not apply. */
    record Skipped(String name, Why why, String detail) {
    }

    /** What a task's {@code problemMatcher} declares: the matchers to apply, and the ones that are not. */
    record Declared(List<Matcher> matchers, List<Skipped> skipped) {

        static final Declared NONE = new Declared(List.of(), List.of());

        /**
         * Whether a matcher here can say when a background task is ready:
         * one of the applied matchers has a {@code background} block.
         */
        boolean watching() {
            return matchers.stream().anyMatch(m -> m.watch() != null);
        }
    }

    /** A matcher and the folder its relative file names are resolved against (null when absolute). */
    record Bound(Matcher matcher, File base) {
    }

    /**
     * A task's matchers, ready to read its output: each bound to its
     * folder, the ones that are not applied, and whether the task is a
     * background one (only then is a {@code background} block used, as
     * in VS Code).
     */
    record Applied(List<Bound> matchers, List<Skipped> skipped, boolean background) {

        /** A task that declares no matcher. */
        static final Applied NONE = new Applied(List.of(), List.of(), false);

        /** Nothing to read and nothing to say. */
        boolean isEmpty() {
            return matchers.isEmpty() && skipped.isEmpty();
        }

        /** Whether this output can say when its background task is ready. */
        boolean watches() {
            return background && matchers.stream().anyMatch(b -> b.matcher().watch() != null);
        }
    }

    /**
     * One problem a task's output reported. {@code line} is 1-based, or
     * 0 for a matcher of {@code "kind": "file"}; a column or an end that
     * the output did not give is 0. {@code source} is the matcher's
     * {@code source}, else its {@code owner}, else null.
     */
    record Finding(File file, int line, int column, int endLine, int endColumn, Severity severity,
            String message, String code, String source) {
    }

    /** A matcher that cannot be applied, on its way to becoming a {@link Skipped}. */
    static final class Unusable extends Exception {

        private static final long serialVersionUID = 1L;

        final Why why;
        final String detail;

        Unusable(Why why, String detail) {
            super(why + " " + detail, null, false, false);
            this.why = why;
            this.detail = detail;
        }
    }

    /* --------------------------------------------- JavaScript's regexp in Java */

    /** JavaScript's {@code \s}, as the members of a Java character class. */
    private static final String JS_SPACE =
            "\\t\\n\\x0B\\f\\r\\x20\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF";

    /** JavaScript's {@code .} (no flags): anything but its four line terminators. */
    private static final String JS_DOT = "[^\\n\\r\\u2028\\u2029]";

    /**
     * {@code js} — the text of a JavaScript {@code RegExp} with no flags
     * — as text {@link Pattern} reads with the same meaning, or the
     * construct that has no exact equivalent. See the class comment.
     */
    static String toJava(String js) throws Unusable {
        StringBuilder out = new StringBuilder(js.length() + 32);
        boolean inClass = false;
        boolean negatedClass = false;
        boolean afterQuantifier = false;
        boolean afterClassEscape = false;
        int n = js.length();
        for (int i = 0; i < n; i++) {
            char c = js.charAt(i);
            boolean quantifier = false;
            boolean classEscape = false;
            if (c == '\\') {
                if (i + 1 >= n) {
                    out.append(c); // a trailing backslash: Java refuses it, as JavaScript does
                    break;
                }
                char e = js.charAt(++i);
                switch (e) {
                    case 'd', 'D', 'w', 'W' -> {
                        out.append('\\').append(e);
                        classEscape = true;
                    }
                    case 's' -> {
                        out.append(inClass ? JS_SPACE : "[" + JS_SPACE + "]");
                        classEscape = true;
                    }
                    case 'S' -> {
                        if (inClass && negatedClass) {
                            // "not (not a space)": Java's nested negation is not relied on to mean it
                            throw new Unusable(Why.DIALECT, "[^\\S]");
                        }
                        // inside a class this is a nested class: Java's union, the same set
                        out.append("[^").append(JS_SPACE).append(']');
                        classEscape = true;
                    }
                    case 'b' -> out.append(inClass ? "\\x08" : "\\b");
                    case 'B' -> {
                        if (inClass) {
                            throw new Unusable(Why.DIALECT, "[\\B]");
                        }
                        out.append("\\B");
                    }
                    case 't', 'n', 'r', 'f' -> out.append('\\').append(e);
                    case 'v' -> out.append("\\x0B");
                    case '0' -> {
                        if (i + 1 < n && Character.isDigit(js.charAt(i + 1))) {
                            throw new Unusable(Why.DIALECT, "\\0" + js.charAt(i + 1));
                        }
                        out.append("\\x00");
                    }
                    case 'x' -> {
                        if (!hex(js, i + 1, 2)) {
                            throw new Unusable(Why.DIALECT, "\\x");
                        }
                        out.append("\\x");
                    }
                    case 'u' -> {
                        if (!hex(js, i + 1, 4)) {
                            throw new Unusable(Why.DIALECT, "\\u");
                        }
                        out.append("\\u");
                    }
                    case 'c' -> {
                        char letter = i + 1 < n ? js.charAt(i + 1) : 0;
                        if (!asciiLetter(letter)) {
                            throw new Unusable(Why.DIALECT, "\\c");
                        }
                        // JavaScript: the letter's value modulo 32; Java's \cx differs for a small letter
                        out.append(String.format(Locale.ROOT, "\\x%02X", letter % 32));
                        i++;
                    }
                    case 'k' -> {
                        if (inClass || i + 1 >= n || js.charAt(i + 1) != '<') {
                            throw new Unusable(Why.DIALECT, "\\k");
                        }
                        out.append("\\k");
                    }
                    default -> {
                        if (e >= '1' && e <= '9') {
                            if (inClass) {
                                throw new Unusable(Why.DIALECT, "[\\" + e + "]");
                            }
                            out.append('\\').append(e);
                        } else if (asciiLetter(e)) {
                            // a plain letter in JavaScript, an escape (or an error) in Java
                            throw new Unusable(Why.DIALECT, "\\" + e);
                        } else {
                            out.append('\\').append(e);
                        }
                    }
                }
            } else if (inClass) {
                if (c == ']') {
                    inClass = false;
                    out.append(c);
                } else if (c == '[') {
                    out.append("\\["); // a literal in JavaScript, a nested class in Java
                } else if (c == '&' && i + 1 < n && js.charAt(i + 1) == '&') {
                    out.append("&\\&"); // two ampersands in JavaScript, an intersection in Java
                    i++;
                } else if (c == '-' && (afterClassEscape || classEscapeAt(js, i + 1))) {
                    out.append("\\-"); // beside \d, \w, \s a hyphen is itself in JavaScript
                } else {
                    out.append(c);
                }
            } else {
                switch (c) {
                    case '[' -> {
                        int j = i + 1;
                        negatedClass = j < n && js.charAt(j) == '^';
                        if (negatedClass) {
                            j++;
                        }
                        if (j < n && js.charAt(j) == ']') {
                            throw new Unusable(Why.DIALECT, js.substring(i, j + 1));
                        }
                        inClass = true;
                        out.append(negatedClass ? "[^" : "[");
                        i = j - 1;
                    }
                    case '(' -> {
                        out.append('(');
                        if (i + 1 < n && js.charAt(i + 1) == '?') {
                            char k = i + 2 < n ? js.charAt(i + 2) : ' ';
                            if (k != ':' && k != '=' && k != '!' && k != '<') {
                                throw new Unusable(Why.DIALECT, "(?" + k);
                            }
                            out.append('?'); // the group's own mark, not a quantifier
                            i++;
                        }
                    }
                    case '*', '+', '?' -> {
                        if (c == '+' && afterQuantifier) {
                            throw new Unusable(Why.DIALECT, js.substring(i - 1, i + 1));
                        }
                        out.append(c);
                        quantifier = !(c == '?' && afterQuantifier); // "*?" is lazy, and ends there
                    }
                    case '{' -> {
                        int close = quantifierEnd(js, i);
                        if (close < 0) {
                            out.append("\\{"); // not a quantifier: a literal in JavaScript, an error in Java
                        } else {
                            out.append(js, i, close + 1);
                            i = close;
                            quantifier = true;
                        }
                    }
                    case '}' -> out.append("\\}");
                    case '.' -> out.append(JS_DOT);
                    case '$' -> out.append("\\z"); // no flags: the end of the line and nothing else
                    default -> out.append(c);
                }
            }
            afterQuantifier = quantifier;
            afterClassEscape = inClass && classEscape;
        }
        return out.toString();
    }

    private static boolean asciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean hex(String s, int from, int count) {
        if (from + count > s.length()) {
            return false;
        }
        for (int i = from; i < from + count; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code \d}, {@code \w}, {@code \s} (either case) starts at {@code at}. */
    private static boolean classEscapeAt(String s, int at) {
        return at + 1 < s.length() && s.charAt(at) == '\\' && "dDwWsS".indexOf(s.charAt(at + 1)) >= 0;
    }

    /** The index of the brace that closes the quantifier opening at {@code open}, or -1 when it is not one. */
    private static int quantifierEnd(String s, int open) {
        int i = open + 1;
        int digits = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            i++;
            digits++;
        }
        if (digits == 0) {
            return -1;
        }
        if (i < s.length() && s.charAt(i) == ',') {
            i++;
            while (i < s.length() && Character.isDigit(s.charAt(i))) {
                i++;
            }
        }
        return i < s.length() && s.charAt(i) == '}' ? i : -1;
    }

    /** {@code js} compiled, or why it cannot be. */
    static Rx compile(String js) throws Unusable {
        if (js.length() > MAX_REGEXP) {
            throw new Unusable(Why.TOO_LARGE, "");
        }
        String java = toJava(js);
        try {
            return new Rx(js, Pattern.compile(java));
        } catch (PatternSyntaxException | StackOverflowError refused) {
            throw new Unusable(Why.REGEXP, clip(js, 80));
        }
    }

    private static String clip(String s, int max) {
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        return oneLine.codePointCount(0, oneLine.length()) <= max ? oneLine
                : oneLine.substring(0, oneLine.offsetByCodePoints(0, max)) + "\u2026";
    }

    /* ---------------------------------------------------------- the built-ins */

    private static final int NO = -1;

    private static final class BuiltIns {

        /** Named patterns: what {@code "pattern": "$name"} refers to. */
        static final Map<String, List<Line>> PATTERNS = new LinkedHashMap<>();
        /** Named matchers: what {@code "problemMatcher": "$name"} refers to. */
        static final Map<String, Matcher> MATCHERS = new LinkedHashMap<>();

        private static final String WORKSPACE = "${workspaceFolder}";

        static {
            try {
                fill();
            } catch (Unusable broken) {
                throw new IllegalStateException("a built-in problem matcher does not compile: " + broken.detail);
            }
        }

        private static Line line(String js, int file, int location, int line, int column,
                int severity, int code, int message, boolean loop) throws Unusable {
            return new Line(compile(js), file, location, line, column, NO, NO, severity, code, message, loop);
        }

        private static void matcher(String name, String owner, String source, Severity severity,
                FileLocation location, String prefix, String pattern, Watch watch) {
            MATCHERS.put(name, new Matcher("$" + name, owner, source, severity, location, prefix, false,
                    PATTERNS.get(pattern), watch));
        }

        private static void fill() throws Unusable {
            // --- VS Code's own registry (problemMatcher.ts: ProblemPatternRegistry, ProblemMatcherRegistry)
            // checked against microsoft/vscode main on 2026-10-03: the location is optional
            // and a category word may stand before the severity (group 3, not read)
            PATTERNS.put("msCompile", List.of(line(
                    "^\\s*(?:\\s*\\d+>)?(\\S.*?)(?:\\((\\d+|\\d+,\\d+|\\d+,\\d+,\\d+,\\d+)\\))?\\s*:\\s+"
                    + "(?:(\\S+)\\s+)?((?:fatal +)?error|warning|info)\\s+(\\w+\\d+)?\\s*:\\s*(.*)$",
                    1, 2, NO, NO, 4, 5, 6, false)));
            PATTERNS.put("gulp-tsc", List.of(line(
                    "^([^\\s].*)\\((\\d+|\\d+,\\d+|\\d+,\\d+,\\d+,\\d+)\\):\\s+(\\d+)\\s+(.*)$",
                    1, 2, NO, NO, NO, 3, 4, false)));
            PATTERNS.put("cpp", List.of(line(
                    "^(\\S.*)\\((\\d+|\\d+,\\d+|\\d+,\\d+,\\d+,\\d+)\\):\\s+(error|warning|info)\\s+(C\\d+)\\s*:\\s*(.*)$",
                    1, 2, NO, NO, 3, 4, 5, false)));
            PATTERNS.put("csc", List.of(line(
                    "^(\\S.*)\\((\\d+|\\d+,\\d+|\\d+,\\d+,\\d+,\\d+)\\):\\s+(error|warning|info)\\s+(CS\\d+)\\s*:\\s*(.*)$",
                    1, 2, NO, NO, 3, 4, 5, false)));
            PATTERNS.put("vb", List.of(line(
                    "^(\\S.*)\\((\\d+|\\d+,\\d+|\\d+,\\d+,\\d+,\\d+)\\):\\s+(error|warning|info)\\s+(BC\\d+)\\s*:\\s*(.*)$",
                    1, 2, NO, NO, 3, 4, 5, false)));
            PATTERNS.put("lessCompile", List.of(line(
                    "^\\s*(.*) in file (.*) line no. (\\d+)$",
                    2, NO, 3, NO, NO, NO, 1, false)));
            PATTERNS.put("jshint", List.of(line(
                    "^(.*):\\s+line\\s+(\\d+),\\s+col\\s+(\\d+),\\s(.+?)(?:\\s+\\((\\w)(\\d+)\\))?$",
                    1, NO, 2, 3, 5, 6, 4, false)));
            PATTERNS.put("jshint-stylish", List.of(
                    line("^(.+)$", 1, NO, NO, NO, NO, NO, NO, false),
                    line("^\\s+line\\s+(\\d+)\\s+col\\s+(\\d+)\\s+(.+?)(?:\\s+\\((\\w)(\\d+)\\))?$",
                            NO, NO, 1, 2, 4, 5, 3, true)));
            PATTERNS.put("eslint-compact", List.of(line(
                    "^(.+):\\sline\\s(\\d+),\\scol\\s(\\d+),\\s(Error|Warning|Info)\\s-\\s(.+)\\s\\((.+)\\)$",
                    1, NO, 2, 3, 4, 6, 5, false)));
            PATTERNS.put("eslint-stylish", List.of(
                    line("^((?:[a-zA-Z]:)*[./\\\\]+.*?)$", 1, NO, NO, NO, NO, NO, NO, false),
                    line("^\\s+(\\d+):(\\d+)\\s+(error|warning|info)\\s+(.+?)(?:\\s\\s+(.*))?$",
                            NO, NO, 1, 2, 3, 5, 4, true)));
            PATTERNS.put("go", List.of(line(
                    "^([^:]*: )?((.:)?[^:]*):(\\d+)(:(\\d+))?: (.*)$",
                    2, NO, 4, 6, NO, NO, 7, false)));

            matcher("msCompile", "msCompile", "cpp", null, FileLocation.ABSOLUTE, null, "msCompile", null);
            matcher("lessCompile", "lessCompile", "less", Severity.ERROR, FileLocation.ABSOLUTE, null,
                    "lessCompile", null);
            matcher("gulp-tsc", "typescript", "ts", null, FileLocation.RELATIVE, WORKSPACE, "gulp-tsc", null);
            matcher("jshint", "jshint", "jshint", null, FileLocation.ABSOLUTE, null, "jshint", null);
            matcher("jshint-stylish", "jshint", "jshint", null, FileLocation.ABSOLUTE, null, "jshint-stylish", null);
            matcher("eslint-compact", "eslint", "eslint", null, FileLocation.ABSOLUTE, null, "eslint-compact", null);
            matcher("eslint-stylish", "eslint", "eslint", null, FileLocation.ABSOLUTE, null, "eslint-stylish", null);
            matcher("go", "go", "go", null, FileLocation.RELATIVE, WORKSPACE, "go", null);

            // --- the extensions that ship inside VS Code
            // typescript-language-features (package.json: problemPatterns, problemMatchers)
            PATTERNS.put("tsc", List.of(line(
                    "^([^\\s].*)[\\(:](\\d+)[,:](\\d+)(?:\\):\\s+|\\s+-\\s+)(error|warning|info)\\s+TS(\\d+)\\s*:\\s*(.*)$",
                    1, NO, 2, 3, 4, 5, 6, false)));
            String clock = "\\[?\\D*.{1,2}[:.].{1,2}[:.].{1,2}\\D*(\u251C\\D*\\d{1,2}\\D+\u2524)?(?:\\]| -)";
            matcher("tsc", "typescript", "ts", null, FileLocation.RELATIVE, "${cwd}", "tsc", null);
            matcher("tsc-watch", "typescript", "ts", null, FileLocation.RELATIVE, "${cwd}", "tsc", new Watch(true,
                    compile("^\\s*(?:message TS6032:|" + clock + ") (Starting compilation in watch mode|"
                            + "File change detected\\. Starting incremental compilation)\\.\\.\\."),
                    compile("^\\s*(?:message TS6042:|" + clock + ") (?:Compilation complete\\.|"
                            + "Found \\d+ errors?\\.) Watching for file changes\\.")));
            // tsgo, the TypeScript 7 compiler's watch mode
            matcher("tsgo-watch", "typescript", "ts", null, FileLocation.RELATIVE, "${cwd}", "tsc", new Watch(true,
                    compile("^build starting at .*$"), compile("^build finished in .*$")));
            // less (package.json: problemMatchers)
            PATTERNS.put("lessc", List.of(line(
                    "(.*)\\sin\\s(.*)\\son line\\s(\\d+),\\scolumn\\s(\\d+)",
                    2, NO, 3, 4, NO, NO, 1, false)));
            matcher("lessc", "lessc", "less", null, FileLocation.ABSOLUTE, null, "lessc", null);

            // --- two extensions a switcher's tasks.json names most, which VS Code does not ship
            // ms-vscode.cpptools
            PATTERNS.put("gcc", List.of(line(
                    "^(.*?):(\\d+):(\\d*):?\\s+(?:fatal\\s+)?(warning|error):\\s+(.*)$",
                    1, NO, 2, 3, 4, NO, 5, false)));
            matcher("gcc", "cpptools", "gcc", null, FileLocation.AUTO_DETECT, "${cwd}", "gcc", null);
            // rust-lang.rust-analyzer
            PATTERNS.put("rustc", List.of(
                    line("^(warning|warn|error)(?:\\[(.*?)\\])?: (.*)$", NO, NO, NO, NO, 1, 2, 3, false),
                    line("^[\\s->=]*(.*?):([1-9]\\d*):([1-9]\\d*)\\s*$", 1, NO, 2, 3, NO, NO, NO, false)));
            matcher("rustc", "rustc", "rustc", null, FileLocation.AUTO_DETECT, WORKSPACE, "rustc", null);
        }
    }

    /**
     * The matchers a {@code "$name"} may name here, by name without the
     * dollar: VS Code's own ({@code msCompile}, {@code lessCompile},
     * {@code gulp-tsc}, {@code jshint}, {@code jshint-stylish}, {@code
     * eslint-compact}, {@code eslint-stylish}, {@code go}), the ones its
     * bundled extensions contribute ({@code tsc}, {@code tsc-watch},
     * {@code lessc}), and two from extensions it does not ship ({@code
     * gcc} from the C/C++ extension, {@code rustc} from rust-analyzer).
     */
    static Map<String, Matcher> builtIns() {
        return BuiltIns.MATCHERS;
    }

    /* --------------------------------------------------------------- reading */

    /**
     * What {@code written} declares — each entry a {@code "$name"} or the
     * JSON text of an inline matcher object, as {@link VsCodeTasks} keeps
     * a task's {@code problemMatcher}. Pure and the same on every call:
     * what is decided here does not depend on the project, so the plan
     * of a run and the run itself agree on whether a background task can
     * say it is ready.
     */
    static Declared read(List<String> written) {
        if (written == null || written.isEmpty()) {
            return Declared.NONE;
        }
        List<Matcher> matchers = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (int i = 0; i < written.size(); i++) {
            String entry = written.get(i);
            boolean inline = entry.startsWith("{");
            String name = inline ? "#" + (i + 1) : clip(entry, 60);
            if (i >= MAX_MATCHERS) {
                skipped.add(new Skipped(name, Why.TOO_MANY, ""));
                break;
            }
            try {
                if (inline) {
                    matchers.add(checked(inline(new JSONObject(entry), name)));
                } else if (entry.length() > 1 && entry.charAt(0) == '$') {
                    Matcher builtIn = builtIns().get(entry.substring(1));
                    if (builtIn == null) {
                        throw new Unusable(Why.UNKNOWN, name);
                    }
                    matchers.add(builtIn);
                } else {
                    // VS Code: "Invalid problemMatcher reference" — a name is written with its dollar
                    throw new Unusable(Why.INVALID, "problemMatcher");
                }
            } catch (Unusable unusable) {
                skipped.add(new Skipped(name, unusable.why, unusable.detail));
            } catch (JSONException malformed) {
                skipped.add(new Skipped(name, Why.INVALID, "problemMatcher"));
            }
        }
        return new Declared(List.copyOf(matchers), List.copyOf(skipped));
    }

    /** {@code matcher}, or why its file location cannot be resolved here. */
    private static Matcher checked(Matcher matcher) throws Unusable {
        if (matcher.prefix() != null) {
            String variable = VsCodeTasks.unsupportedVariable(matcher.prefix());
            if (variable != null) {
                throw new Unusable(Why.FILE_LOCATION, clip(variable, 60));
            }
        }
        return matcher;
    }

    /** An inline matcher object, as VS Code's {@code ProblemMatcherParser} reads one. */
    private static Matcher inline(JSONObject o, String name) throws Unusable {
        Matcher base = null;
        if (o.opt("base") instanceof String written) {
            base = written.length() > 1 && written.charAt(0) == '$' ? builtIns().get(written.substring(1)) : null;
            if (base == null) {
                throw new Unusable(Why.UNKNOWN, clip(written, 60));
            }
        }
        String owner = o.opt("owner") instanceof String s ? s : null;
        String source = o.opt("source") instanceof String s ? s : null;

        FileLocation location = null;
        String prefix = null;
        Object fileLocation = o.opt("fileLocation");
        if (fileLocation == null) {
            if (base == null) {
                location = FileLocation.RELATIVE;
                prefix = "${workspaceFolder}";
            }
        } else if (fileLocation instanceof String word) {
            location = kind(word);
            prefix = location == null || location == FileLocation.ABSOLUTE ? null : "${workspaceFolder}";
        } else if (fileLocation instanceof JSONArray array && array.opt(0) instanceof String word) {
            FileLocation named = kind(word);
            if (array.length() == 1 && named == FileLocation.ABSOLUTE) {
                location = named;
            } else if (array.length() == 2 && named != null && named != FileLocation.ABSOLUTE
                    && array.opt(1) instanceof String folder && !folder.isEmpty()) {
                location = named;
                prefix = folder;
            }
        }
        if (location == null && fileLocation != null && base == null) {
            throw new Unusable(Why.FILE_LOCATION, clip(String.valueOf(fileLocation), 60));
        }

        List<Line> lines = null;
        boolean fileKind = false;
        Object pattern = o.opt("pattern");
        if (pattern instanceof String named) {
            lines = named.length() > 1 && named.charAt(0) == '$' ? BuiltIns.PATTERNS.get(named.substring(1)) : null;
            if (lines == null) {
                throw new Unusable(Why.UNKNOWN, clip(named, 60));
            }
        } else if (pattern instanceof JSONObject single) {
            fileKind = fileKind(single);
            lines = List.of(line(single, true, fileKind));
        } else if (pattern instanceof JSONArray array) {
            if (array.length() == 0) {
                throw new Unusable(Why.INVALID, "pattern");
            }
            if (array.length() > MAX_PATTERNS) {
                throw new Unusable(Why.TOO_LARGE, "");
            }
            List<Line> several = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                if (!(array.opt(i) instanceof JSONObject each)) {
                    throw new Unusable(Why.INVALID, "pattern");
                }
                if (i == 0) {
                    fileKind = fileKind(each);
                } else if (each.has("kind")) {
                    throw new Unusable(Why.INVALID, "kind");
                }
                Line line = line(each, false, false);
                if (line.loop() && i < array.length() - 1) {
                    throw new Unusable(Why.INVALID, "loop");
                }
                several.add(line);
            }
            // VS Code's validateProblemPattern: a file and a message somewhere, and a place unless the kind is "file"
            if (several.stream().noneMatch(l -> l.file() != NO)) {
                throw new Unusable(Why.INVALID, "file");
            }
            if (several.stream().noneMatch(l -> l.message() != NO)) {
                throw new Unusable(Why.INVALID, "message");
            }
            if (!fileKind && several.stream().noneMatch(l -> l.location() != NO || l.line() != NO)) {
                throw new Unusable(Why.INVALID, "line");
            }
            lines = List.copyOf(several);
        } else if (pattern != null) {
            throw new Unusable(Why.INVALID, "pattern");
        }

        Severity severity = null;
        if (o.opt("severity") instanceof String written) {
            // VS Code: an unknown word is said and read as "error"
            Severity read = severityWord(written);
            severity = read == null ? Severity.ERROR : read;
        }

        Watch watch = watch(o);

        if (base != null) {
            return new Matcher(name,
                    owner != null ? owner : base.owner(),
                    source != null ? source : base.source(),
                    severity != null ? severity : base.severity(),
                    location != null ? location : base.location(),
                    location != null ? prefix : base.prefix(),
                    lines != null ? fileKind : base.fileKind(),
                    lines != null ? lines : base.lines(),
                    watch != null ? watch : base.watch());
        }
        if (lines == null) {
            throw new Unusable(Why.INVALID, "pattern");
        }
        return new Matcher(name, owner, source, severity, location, prefix, fileKind, lines, watch);
    }

    private static FileLocation kind(String written) throws Unusable {
        return switch (written.toLowerCase(Locale.ROOT)) {
            case "absolute" -> FileLocation.ABSOLUTE;
            case "relative" -> FileLocation.RELATIVE;
            case "autodetect" -> FileLocation.AUTO_DETECT;
            // VS Code searches the folders for a file of that name; nothing here walks a tree per output line
            case "search" -> throw new Unusable(Why.FILE_LOCATION, "search");
            default -> null;
        };
    }

    private static boolean fileKind(JSONObject pattern) {
        return pattern.opt("kind") instanceof String kind && "file".equalsIgnoreCase(kind);
    }

    /** One pattern object: VS Code's {@code createSingleProblemPattern}. */
    private static Line line(JSONObject o, boolean defaults, boolean fileKind) throws Unusable {
        if (!(o.opt("regexp") instanceof String regexp)) {
            throw new Unusable(Why.INVALID, "regexp");
        }
        int file = group(o, "file");
        int location = group(o, "location");
        int line = group(o, "line");
        int column = group(o, "column");
        int message = group(o, "message");
        if (defaults) {
            if (file == NO) {
                file = 1;
            }
            if (message == NO) {
                message = 0;
            }
            if (location == NO && !fileKind) {
                if (line == NO) {
                    line = 2;
                }
                if (column == NO) {
                    column = 3;
                }
            }
        }
        return new Line(compile(regexp), file, location, line, column, group(o, "endLine"), group(o, "endColumn"),
                group(o, "severity"), group(o, "code"), message, Boolean.TRUE.equals(o.opt("loop")));
    }

    /** The capture group {@code key} names: a whole number from 0, else none (VS Code copies only numbers). */
    private static int group(JSONObject o, String key) {
        Object value = o.opt(key);
        if (value instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())
                && number.doubleValue() >= 0 && number.doubleValue() <= 1_000) {
            return number.intValue();
        }
        return NO;
    }

    /** {@code background} (or {@code watching}, or the 0.1.0 pair): VS Code's {@code addWatchingMatcher}. */
    private static Watch watch(JSONObject o) throws Unusable {
        if (o.opt("watchedTaskBeginsRegExp") instanceof String begins
                && o.opt("watchedTaskEndsRegExp") instanceof String ends) {
            return new Watch(false, compile(begins), compile(ends));
        }
        Object block = o.has("background") ? o.opt("background") : o.opt("watching");
        if (block == null) {
            return null;
        }
        if (!(block instanceof JSONObject monitor)) {
            throw new Unusable(Why.INVALID, "background");
        }
        String begins = watchRegexp(monitor.opt("beginsPattern"));
        String ends = watchRegexp(monitor.opt("endsPattern"));
        if (begins == null && ends == null) {
            return null;
        }
        if (begins == null || ends == null) {
            // VS Code: "A problem matcher must define both a begin pattern and an end pattern for watching."
            throw new Unusable(Why.INVALID, begins == null ? "beginsPattern" : "endsPattern");
        }
        return new Watch(Boolean.TRUE.equals(monitor.opt("activeOnStart")), compile(begins), compile(ends));
    }

    private static String watchRegexp(Object written) {
        if (written instanceof String s) {
            return s;
        }
        return written instanceof JSONObject o && o.opt("regexp") instanceof String s ? s : null;
    }

    /** VS Code's {@code Severity.fromValue}: the word, in any case, or null. */
    private static Severity severityWord(String value) {
        if ("error".equalsIgnoreCase(value)) {
            return Severity.ERROR;
        }
        if ("warning".equalsIgnoreCase(value) || "warn".equalsIgnoreCase(value)) {
            return Severity.WARNING;
        }
        return "info".equalsIgnoreCase(value) ? Severity.INFO : null;
    }

    /**
     * {@link #read} for a run: each matcher bound to the folder its file
     * names are relative to — {@code sub} fills the folder's variables
     * (the workspace folder, {@code ${env:…}}); a folder that is still
     * not absolute is taken inside {@code project}. A task that declares
     * no matcher is {@link Applied#NONE}, background or not.
     */
    static Applied apply(List<String> written, boolean background, File project, UnaryOperator<String> sub) {
        Declared declared = read(written);
        if (declared.matchers().isEmpty() && declared.skipped().isEmpty()) {
            return Applied.NONE;
        }
        List<Bound> bound = new ArrayList<>();
        for (Matcher matcher : declared.matchers()) {
            File base = null;
            if (matcher.prefix() != null) {
                base = new File(sub.apply(matcher.prefix()));
                if (!base.isAbsolute()) {
                    base = new File(project, base.getPath());
                }
            }
            bound.add(new Bound(matcher, base));
        }
        return new Applied(List.copyOf(bound), declared.skipped(), background);
    }

    /* ------------------------------------------------------------ the session */

    /** What a line meant to a background task's matchers. */
    enum Signal {
        /** Nothing to do with a cycle. */
        NONE,
        /**
         * A matcher that was idle saw its {@code beginsPattern}: a new
         * cycle. What was found before belongs to the cycle before it,
         * and is forgotten.
         */
        BEGAN,
        /**
         * A matcher that was active saw its {@code endsPattern}: the
         * cycle is over, what was found is this cycle's whole answer —
         * and the task is READY: this is the moment VS Code starts what
         * waits for a background task. A matcher is active from the
         * first line when {@code activeOnStart} is true, and otherwise
         * only once its {@code beginsPattern} has matched; an {@code
         * endsPattern} seen while idle means nothing, as in VS Code.
         */
        ENDED
    }

    /**
     * What a session has found: the findings of the batch, how many more
     * were over {@link #MAX_FINDINGS}, how many lines were too long to
     * match, the matchers that were switched off, and how many cycles a
     * background task has completed.
     */
    record Report(List<Finding> findings, int dropped, int longLines, List<String> switchedOff, int cycles) {
    }

    /** One line, with the reading of it counted: what the engine may read before it is cut off. */
    private static final class Metered implements CharSequence {

        private final String text;
        private int left;

        Metered(String text, int budget) {
            this.text = text;
            this.left = budget;
        }

        @Override
        public char charAt(int index) {
            if (--left < 0) {
                throw OverBudget.INSTANCE;
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

    private static final class OverBudget extends RuntimeException {

        private static final long serialVersionUID = 1L;
        static final OverBudget INSTANCE = new OverBudget();

        private OverBudget() {
            super(null, null, false, false);
        }
    }

    /** The parts of a problem gathered so far, as text: VS Code's {@code IProblemData}. */
    private static final class Data {

        String file;
        String location;
        String line;
        String column;
        String endLine;
        String endColumn;
        String severity;
        String code;
        String message;

        Data copy() {
            Data d = new Data();
            d.file = file;
            d.location = location;
            d.line = line;
            d.column = column;
            d.endLine = endLine;
            d.endColumn = endColumn;
            d.severity = severity;
            d.code = code;
            d.message = message;
            return d;
        }
    }

    /** One matcher at work: its loop, its cycle, and how often it had to be cut off. */
    private static final class Running {

        final Matcher matcher;
        final File base;
        /** What the patterns before the looping one gathered, while the loop goes on; else null. */
        Data looping;
        boolean active;
        int strikes;
        boolean off;

        Running(Bound bound, boolean background) {
            this.matcher = bound.matcher();
            this.base = bound.base();
            this.active = background && matcher.watch() != null && matcher.watch().activeOnStart();
        }
    }

    /**
     * A task's output, read: feed it every line the process prints, in
     * order; ask it what it found. One session per run. Thread-safe —
     * a process's output and its errors arrive on two threads.
     */
    static final class Session {

        private final List<Running> matchers = new ArrayList<>();
        private final boolean background;
        private final Predicate<File> exists;
        /** The last lines, oldest first — as many as the longest matcher has patterns; null marks a line not matched. */
        private final String[] buffer;
        private int buffered;
        /** The matcher whose loop is going on, or null. */
        private Running loop;
        private final Set<Finding> found = new LinkedHashSet<>();
        private int dropped;
        private int longLines;
        private int cycles;
        /** What the cycle that last ended found, as it stood at its end. */
        private Report lastCycle;

        Session(Applied applied) {
            this(applied, File::exists);
        }

        /** @param exists whether a file is there, for {@code autoDetect} (a seam) */
        Session(Applied applied, Predicate<File> exists) {
            this.background = applied.background();
            this.exists = exists;
            int longest = 1;
            for (Bound bound : applied.matchers()) {
                matchers.add(new Running(bound, background));
                longest = Math.max(longest, bound.matcher().lines().size());
            }
            this.buffer = new String[longest];
        }

        /** One line of output, as the process printed it. */
        synchronized Signal line(String raw) {
            if (raw == null || matchers.isEmpty()) {
                return Signal.NONE;
            }
            String line = stripAnsi(raw);
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.length() > MAX_LINE) {
                longLines++;
                line = null;
            }
            if (background && line != null) {
                // VS Code's tryBegin, then tryFinish: either takes the line
                boolean began = false;
                for (Running m : matchers) {
                    if (m.matcher.watch() != null && !m.active && exec(m, m.matcher.watch().begins(), line) != null) {
                        m.active = true;
                        began = true;
                    }
                }
                if (began) {
                    found.clear();
                    dropped = 0;
                    longLines = 0;
                    return Signal.BEGAN;
                }
                boolean ended = false;
                for (Running m : matchers) {
                    if (m.matcher.watch() != null && m.active && exec(m, m.matcher.watch().ends(), line) != null) {
                        m.active = false;
                        ended = true;
                    }
                }
                if (ended) {
                    cycles++;
                    lastCycle = report();
                    return Signal.ENDED;
                }
            }
            find(line);
            return Signal.NONE;
        }

        /** VS Code's {@code tryFindMarker}. */
        private void find(String line) {
            if (loop != null) {
                Finding next = next(loop, line);
                if (next != null) {
                    keep(next);
                    return;
                }
                buffered = 0;
                loop = null;
            }
            if (buffered < buffer.length) {
                buffer[buffered++] = line;
            } else {
                System.arraycopy(buffer, 1, buffer, 0, buffer.length - 1);
                buffer[buffer.length - 1] = line;
            }
            // VS Code's tryMatchers: the longest matcher that fits first, then by the file's order
            for (int start = 0; start < buffered; start++) {
                int length = buffered - start;
                for (Running m : matchers) {
                    if (m.off || m.matcher.lines().size() != length) {
                        continue;
                    }
                    Finding finding = handle(m, start);
                    if (finding != null) {
                        keep(finding);
                        if (m.matcher.lines().get(length - 1).loop()) {
                            loop = m;
                        } else {
                            m.looping = null;
                        }
                        buffered = 0;
                        return;
                    }
                    m.looping = null;
                }
            }
        }

        /** VS Code's {@code handle}: every pattern of {@code m} against the buffered lines from {@code start}. */
        private Finding handle(Running m, int start) {
            List<Line> patterns = m.matcher.lines();
            Data data = new Data();
            Data prefix = data;
            for (int i = 0; i < patterns.size(); i++) {
                Line pattern = patterns.get(i);
                java.util.regex.Matcher match = exec(m, pattern.regexp(), buffer[start + i]);
                if (match == null) {
                    return null;
                }
                if (pattern.loop() && i == patterns.size() - 1) {
                    data = data.copy(); // each line of the loop starts from what came before the loop
                }
                fill(data, pattern, match);
            }
            m.looping = prefix;
            return finding(m, data);
        }

        /** VS Code's {@code next}: the looping pattern against one more line. */
        private Finding next(Running m, String line) {
            Line pattern = m.matcher.lines().get(m.matcher.lines().size() - 1);
            java.util.regex.Matcher match = m.looping == null ? null : exec(m, pattern.regexp(), line);
            if (match == null) {
                m.looping = null;
                return null;
            }
            Data data = m.looping.copy();
            fill(data, pattern, match);
            return finding(m, data);
        }

        /** {@code regexp} on {@code line}: the match, or null — also when the reading had to be cut off. */
        private java.util.regex.Matcher exec(Running m, Rx regexp, String line) {
            if (m.off || line == null) {
                return null;
            }
            try {
                java.util.regex.Matcher match = regexp.pattern().matcher(new Metered(line, STEP_BUDGET));
                return match.find() ? match : null;
            } catch (OverBudget | StackOverflowError tooMuch) {
                if (++m.strikes >= MAX_STRIKES) {
                    m.off = true;
                }
                return null;
            }
        }

        /** VS Code's {@code fillProblemData}: a part is taken once, a message grows by a line. */
        private static void fill(Data data, Line pattern, java.util.regex.Matcher match) {
            if (data.file == null) {
                data.file = trimmed(group(match, pattern.file()));
            }
            String message = trimmed(group(match, pattern.message()));
            if (message != null) {
                data.message = data.message == null ? message : data.message + "\n" + message;
            }
            if (data.code == null) {
                data.code = trimmed(group(match, pattern.code()));
            }
            if (data.severity == null) {
                data.severity = trimmed(group(match, pattern.severity()));
            }
            if (data.location == null) {
                data.location = trimmed(group(match, pattern.location()));
            }
            if (data.line == null) {
                data.line = group(match, pattern.line());
            }
            if (data.column == null) {
                data.column = group(match, pattern.column());
            }
            if (data.endLine == null) {
                data.endLine = group(match, pattern.endLine());
            }
            if (data.endColumn == null) {
                data.endColumn = group(match, pattern.endColumn());
            }
        }

        private static String group(java.util.regex.Matcher match, int index) {
            return index == NO || index > match.groupCount() ? null : match.group(index);
        }

        /** VS Code's {@code Strings.trim}: spaces, and only spaces. */
        private static String trimmed(String s) {
            if (s == null) {
                return null;
            }
            int from = 0;
            int to = s.length();
            while (from < to && s.charAt(from) == ' ') {
                from++;
            }
            while (to > from && s.charAt(to - 1) == ' ') {
                to--;
            }
            return s.substring(from, to);
        }

        /** VS Code's {@code getMarkerMatch}: a file, a place and a message make a problem; less makes none. */
        private Finding finding(Running m, Data data) {
            if (data.file == null || data.file.isEmpty() || data.message == null || data.message.isEmpty()) {
                return null;
            }
            int line = 0;
            int column = 0;
            int endLine = 0;
            int endColumn = 0;
            if (!m.matcher.fileKind()) {
                if (data.location != null && !data.location.isEmpty()) {
                    String[] parts = data.location.split(",");
                    line = parts.length == 0 ? -1 : parseInt(parts[0]);
                    column = parts.length > 1 ? Math.max(0, parseInt(parts[1])) : 0;
                    if (parts.length > 3) {
                        endLine = Math.max(0, parseInt(parts[2]));
                        endColumn = Math.max(0, parseInt(parts[3]));
                    }
                } else if (data.line != null && !data.line.isEmpty()) {
                    line = parseInt(data.line);
                    column = Math.max(0, parseInt(data.column));
                    endLine = Math.max(0, parseInt(data.endLine));
                    endColumn = Math.max(0, parseInt(data.endColumn));
                } else {
                    return null;
                }
                if (line < 0) {
                    return null; // not a number: VS Code would make a problem on line NaN
                }
            }
            File file = resolve(m, data.file);
            if (file == null) {
                return null;
            }
            String message = data.message.length() > MAX_MESSAGE
                    ? data.message.substring(0, Character.isHighSurrogate(data.message.charAt(MAX_MESSAGE - 1))
                            ? MAX_MESSAGE - 1 : MAX_MESSAGE) + "\u2026"
                    : data.message;
            String source = m.matcher.source() != null ? m.matcher.source() : m.matcher.owner();
            return new Finding(file, line, column, endLine, endColumn, severity(m.matcher, data.severity),
                    message, data.code == null || data.code.isEmpty() ? null : data.code, source);
        }

        /** VS Code's {@code getSeverity}. */
        private static Severity severity(Matcher matcher, String captured) {
            Severity result = null;
            if (captured != null && !captured.isEmpty()) {
                result = severityWord(captured);
                if (result == null) {
                    if ("E".equals(captured)) {
                        result = Severity.ERROR;
                    } else if ("W".equals(captured)) {
                        result = Severity.WARNING;
                    } else if ("I".equals(captured) || "hint".equalsIgnoreCase(captured)
                            || "note".equalsIgnoreCase(captured)) {
                        result = Severity.INFO;
                    }
                }
            }
            if (result == null) {
                result = matcher.severity() != null ? matcher.severity() : Severity.ERROR;
            }
            return result;
        }

        /**
         * VS Code's {@code getResource}: the name as written when the
         * location is absolute, under the matcher's folder when it is
         * relative, and for autoDetect whichever of the two is there.
         * A name is never pulled into the project: {@code
         * /usr/include/stdio.h} and {@code ../lib/x.c} stay where they
         * point.
         */
        private File resolve(Running m, String name) {
            try {
                File absolute = rooted(name);
                File file = switch (m.matcher.location()) {
                    case ABSOLUTE -> absolute;
                    case RELATIVE -> new File(m.base, name);
                    case AUTO_DETECT -> {
                        File relative = new File(m.base, name).toPath().normalize().toFile();
                        yield exists.test(relative) ? relative : absolute;
                    }
                };
                return file.toPath().normalize().toFile();
            } catch (InvalidPathException notAPath) {
                return null;
            }
        }

        /** {@code name} as an absolute path — VS Code puts a slash before one that has none. */
        private static File rooted(String name) {
            File file = new File(name);
            return file.isAbsolute() || name.startsWith("/") || name.startsWith("\\") ? file
                    : new File(File.separator + name);
        }

        private void keep(Finding finding) {
            if (found.contains(finding)) {
                return;
            }
            if (found.size() >= MAX_FINDINGS) {
                dropped++;
                return;
            }
            found.add(finding);
        }

        /**
         * What the cycle that last ended found, exactly as it stood when
         * its {@link Signal#ENDED} came — a line that begins the next
         * cycle on the process's other stream cannot empty it; null
         * before any cycle has ended.
         */
        synchronized Report lastCycle() {
            return lastCycle;
        }

        /** What was found since the session began, or since the last {@link Signal#BEGAN}. */
        synchronized Report report() {
            List<String> off = new ArrayList<>();
            for (Running m : matchers) {
                if (m.off) {
                    off.add(m.matcher.name());
                }
            }
            return new Report(List.copyOf(found), dropped, longLines, List.copyOf(off), cycles);
        }
    }

    /**
     * JavaScript's {@code parseInt} for a line or a column: the digits
     * the text starts with (after any spaces), or -1 where JavaScript
     * says NaN.
     */
    static int parseInt(String s) {
        if (s == null) {
            return -1;
        }
        int i = 0;
        int n = s.length();
        while (i < n && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        long value = 0;
        int digits = 0;
        while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            value = Math.min(value * 10 + (s.charAt(i) - '0'), Integer.MAX_VALUE);
            i++;
            digits++;
        }
        return digits == 0 ? -1 : (int) value;
    }

    /**
     * Escape sequences: CSI and OSC (colours, cursor moves, a terminal's
     * title and its hyperlinks), and the two-character ones (the reset a
     * watcher clears the screen with).
     */
    private static final Pattern ANSI = Pattern.compile(
            "\\u001B(?:\\[[0-9;?]*[ -/]*[@-~]|\\][^\\u0007\\u001B]*(?:\\u0007|\\u001B\\\\)?|[0-~])");

    /** {@code line} without its escape sequences: what a terminal would show. */
    static String stripAnsi(String line) {
        return line.indexOf('\u001B') < 0 ? line : ANSI.matcher(line).replaceAll("");
    }
}
