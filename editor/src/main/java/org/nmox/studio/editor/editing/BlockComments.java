package org.nmox.studio.editor.editing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Toggle Block Comment, as a rule over text: which delimiters a language
 * has, and what wrapping or unwrapping a range with them does.
 *
 * <p><b>One home for the delimiters.</b> {@link #styleFor} answers for a
 * mime; a mime in {@link #NONE} has no block comment and the action
 * says so. {@code BlockCommentLedgerTest} walks every mime the product
 * registers an editor feature for and fails until each is in one of the
 * two, so a language added later is decided rather than forgotten.
 *
 * <p><b>The toggle.</b> A selection that IS one block comment (its
 * delimiters at the ends, with or without the inner space) is unwrapped;
 * anything else is wrapped {@code open␠…␠close}. With no selection the
 * current line's content is toggled the same way, and on a blank line
 * the pair is inserted at the caret with the caret inside it. The toggle
 * does not look for a comment that merely surrounds the caret: to remove
 * a comment of several lines, select it.
 *
 * <p><b>The refusal.</b> A range that already contains a delimiter is not
 * wrapped: where block comments do not nest, the first {@code close}
 * inside would end the new comment early and leave the rest as code, and
 * an {@code open} inside means the range starts a comment it does not
 * finish. The answer is a {@link Refusal} naming the delimiter found,
 * never a broken file. Languages known to nest ({@link Style#nests}) accept
 * a range whose inner comments are balanced. A language whose dialects
 * disagree (SQL, Pascal) is treated as not nesting, which is safe in both.
 *
 * <p><b>Markup holds other languages.</b> In an HTML, Vue, Svelte or Astro
 * document a range inside a {@code <script>} or {@code <style>} block is
 * commented as that block's language ({@link #toggle(String, CharSequence,
 * CharSequence, int, int)}); {@code <!-- -->} around JavaScript would be
 * broken code. A range that crosses the block's own tag is refused.
 *
 * <p>Pure: no Swing, no platform. Offsets are in the text handed in, so a
 * caller may pass only the lines it needs.
 */
public final class BlockComments {

    /** A language's block-comment pair, and whether a comment may hold another. */
    public record Style(String open, String close, boolean nests) {
    }

    /** One change to the text: remove {@code remove} characters at {@code offset}, then insert there. */
    public record Op(int offset, int remove, String insert) {
    }

    /** What a toggle answers. */
    public sealed interface Outcome permits Edit, Refusal, NoBlockComment {
    }

    /**
     * The changes, last offset first so each leaves the earlier offsets
     * valid, and the selection afterwards (an empty one is the caret).
     */
    public record Edit(List<Op> ops, int selStart, int selEnd) implements Outcome {

        /** The text after the changes: what the tests read, and what an editor ends up holding. */
        public String applyTo(CharSequence text) {
            StringBuilder sb = new StringBuilder(text);
            for (Op op : ops) {
                sb.replace(op.offset(), op.offset() + op.remove(), op.insert());
            }
            return sb.toString();
        }
    }

    /**
     * The range holds {@code delimiter} (a comment delimiter, or in markup a
     * script or style tag), so a comment around it would break.
     */
    public record Refusal(String delimiter) implements Outcome {
    }

    /** The language at the range has no block comment. */
    public record NoBlockComment() implements Outcome {
    }

    private static final Style C = new Style("/*", "*/", false);
    private static final Style C_NESTING = new Style("/*", "*/", true);
    private static final Style MARKUP = new Style("<!--", "-->", false);
    private static final Style HASKELL = new Style("{-", "-}", true);
    private static final Style ML = new Style("(*", "*)", true);
    private static final Style LISP = new Style("#|", "|#", true);

    private static final Map<String, Style> STYLES = Map.ofEntries(
            // the C family, where the first */ ends the comment
            Map.entry("text/javascript", C),
            Map.entry("text/typescript", C),
            Map.entry("text/css", C),
            Map.entry("text/scss", C),
            Map.entry("text/less", C),
            Map.entry("text/x-scss", C),
            Map.entry("text/x-less", C),
            Map.entry("text/x-java", C),
            Map.entry("text/x-c", C),
            Map.entry("text/x-cpp", C),
            Map.entry("text/x-csharp", C),
            Map.entry("text/x-go", C),
            Map.entry("text/x-php5", C),
            Map.entry("text/x-groovy", C),
            Map.entry("text/x-haxe", C),
            Map.entry("text/x-solidity", C),
            Map.entry("text/x-prolog", C),
            Map.entry("text/x-protobuf", C),
            Map.entry("text/x-d", C), // D's /+ +/ nests; its /* */ does not
            // /* */ where nesting was not settled for this table: treated as
            // not nesting, which is safe whichever way the language goes
            Map.entry("text/x-move", C),
            Map.entry("text/x-tact", C),
            Map.entry("text/x-rescript", C),
            Map.entry("text/x-wit", C),
            Map.entry("text/x-vlang", C),
            Map.entry("text/x-odin", C),
            Map.entry("text/x-sql", C), // PostgreSQL nests, MySQL and SQLite do not
            // the C family that nests
            Map.entry("text/x-rust", C_NESTING),
            Map.entry("text/x-swift", C_NESTING),
            Map.entry("text/x-kotlin", C_NESTING),
            Map.entry("text/x-scala", C_NESTING),
            Map.entry("text/x-dart", C_NESTING),
            // markup
            Map.entry("text/html", MARKUP),
            Map.entry("text/xhtml", MARKUP),
            Map.entry("text/xml", MARKUP),
            Map.entry("text/x-ng-template", MARKUP),
            Map.entry("text/x-vue", MARKUP),
            Map.entry("text/x-svelte", MARKUP),
            Map.entry("text/x-astro", MARKUP),
            Map.entry("text/markdown", MARKUP),
            Map.entry("text/x-markdown", MARKUP),
            Map.entry("text/x-handlebars", new Style("{{!--", "--}}", false)),
            Map.entry("text/x-liquid", new Style("{% comment %}", "{% endcomment %}", false)),
            // the Haskell and ML families: nesting where the language says so
            Map.entry("text/x-haskell", HASKELL),
            Map.entry("text/x-elm", HASKELL),
            Map.entry("text/x-purescript", new Style("{-", "-}", false)), // not settled here: treated as not nesting
            Map.entry("text/x-ocaml", ML),
            Map.entry("text/x-fsharp", ML),
            Map.entry("text/x-pascal", new Style("(*", "*)", false)), // nests in Free Pascal's own mode only
            // the Lisps that have one
            Map.entry("text/x-lisp", LISP),
            Map.entry("text/x-racket", LISP),
            Map.entry("text/x-scheme", LISP),
            // one of a kind
            Map.entry("text/x-lua", new Style("--[[", "]]", false)),
            Map.entry("text/x-julia", new Style("#=", "=#", true)),
            Map.entry("text/x-nim", new Style("#[", "]#", true)),
            Map.entry("text/coffeescript", new Style("###", "###", false)),
            Map.entry("text/x-smalltalk", new Style("\"", "\"", false)));

    /**
     * The mimes that have no block comment, each for the reason beside it.
     * A mime is here because somebody looked, not because nobody did.
     */
    static final Set<String> NONE = Set.of(
            // only a line comment
            "text/x-python", "text/x-vyper", "text/sh", "text/x-yaml", "text/x-toml",
            "text/x-properties", "text/x-ini", "text/x-ignore", "text/x-makefile",
            "text/x-dockerfile", "text/x-nginx-conf", "text/x-apache-conf", "text/x-graphql",
            "text/x-elixir", "text/x-erlang", "text/x-crystal", "text/x-r", "text/x-tcl",
            "text/x-fortran", "text/x-ada", "text/x-cobol", "text/x-zig", "text/x-gleam",
            "text/x-cairo", "text/x-aiken", "text/x-clarity", "text/x-clojure", "text/x-janet",
            "text/x-http-request", "text/x-git-commit", "text/x-git-rebase",
            // only // and /// are documented for a Prisma schema
            "text/x-prisma",
            // =begin/=end and =pod/=cut must each start a line: they cannot wrap a range
            "text/x-ruby", "text/x-perl",
            // comments run by indentation: a closing delimiter does not end them where it stands
            "text/x-sass", "text/x-pug",
            // no comments at all
            "text/x-json");

    /**
     * The markup whose documents hold other languages: inside a
     * {@code <script>} or {@code <style>} block the comment is that
     * language's, not the markup's. A Vue or Svelte component is mostly
     * such blocks.
     */
    static final Set<String> EMBEDDING = Set.of(
            "text/html", "text/xhtml", "text/x-vue", "text/x-svelte", "text/x-astro");

    private static final String[] BLOCK_TAGS = {"script", "style"};

    /** An indented-Sass style block: its comments run by indentation, so it has no pair. */
    private static final Pattern INDENTED_SASS = Pattern.compile("lang\\s*=\\s*[\"']sass[\"']");

    private BlockComments() {
    }

    /** The block-comment pair for a mime, or null when it has none or is not known. */
    public static Style styleFor(String mime) {
        return mime == null ? null : STYLES.get(mime);
    }

    /** Every mime with a pair; the ledger test's first half. */
    static Set<String> styled() {
        return STYLES.keySet();
    }

    /**
     * Toggles a block comment in a document of {@code mime}: the pair is the
     * mime's, or inside a script or style block of markup the block's own.
     * {@code before} is the text that precedes {@code text} in the document
     * (as much of it as the caller read; only markup looks at it), and the
     * offsets are in {@code text}.
     */
    public static Outcome toggle(String mime, CharSequence before, CharSequence text, int selStart, int selEnd) {
        Style style = styleFor(mime);
        if (style == null) {
            return new NoBlockComment();
        }
        if (EMBEDDING.contains(mime)) {
            int s = Math.clamp(Math.min(selStart, selEnd), 0, text.length());
            int e = Math.clamp(Math.max(selStart, selEnd), 0, text.length());
            String tag = blockAt(before.toString() + text.subSequence(0, s));
            if (tag != null) {
                if (tag.startsWith("style") && INDENTED_SASS.matcher(tag).find()) {
                    return new NoBlockComment();
                }
                // a range that leaves the block cannot be commented as the block's language
                String body = s == e
                        ? text.subSequence(lineStart(text, s), lineEnd(text, s)).toString()
                        : text.subSequence(s, e).toString();
                String boundary = blockBoundary(body);
                if (boundary != null) {
                    return new Refusal(boundary);
                }
                style = C;
            }
        }
        return toggle(text, selStart, selEnd, style);
    }

    /**
     * Toggles a block comment over {@code [selStart, selEnd)} of {@code text},
     * or over the caret's line when the two are equal.
     */
    public static Outcome toggle(CharSequence text, int selStart, int selEnd, Style style) {
        int len = text.length();
        int s = Math.clamp(Math.min(selStart, selEnd), 0, len);
        int e = Math.clamp(Math.max(selStart, selEnd), 0, len);
        boolean caretOnly = s == e;
        int a;
        int b;
        if (caretOnly) {
            a = lineStart(text, s);
            b = lineEnd(text, s);
        } else {
            a = s;
            b = withoutFinalTerminator(text, s, e);
        }
        // the range without the whitespace around it: what could BE a comment
        int ta = a;
        while (ta < b && Character.isWhitespace(text.charAt(ta))) {
            ta++;
        }
        int tb = b;
        while (tb > ta && Character.isWhitespace(text.charAt(tb - 1))) {
            tb--;
        }
        String open = style.open();
        String close = style.close();
        if (caretOnly && ta == tb) {
            // a blank line: the pair at the caret, the caret inside it
            return new Edit(List.of(new Op(s, 0, open + "  " + close)),
                    s + open.length() + 1, s + open.length() + 1);
        }
        if (caretOnly) {
            // a line's content, not the indentation and trailing blanks around it
            a = ta;
            b = tb;
        }
        if (isOneComment(text, ta, tb, style)) {
            return unwrap(text, ta, tb, style, caretOnly, s, e);
        }
        String found = offending(text.subSequence(a, b).toString(), style);
        if (found != null) {
            return new Refusal(found);
        }
        List<Op> ops = new ArrayList<>();
        ops.add(new Op(b, 0, " " + close));
        ops.add(new Op(a, 0, open + " "));
        int head = open.length() + 1;
        int added = head + 1 + close.length();
        if (caretOnly) {
            int caret = s < a ? s : s > b ? s + added : s + head;
            return new Edit(ops, caret, caret);
        }
        return new Edit(ops, s, e + added);
    }

    private static Outcome unwrap(CharSequence text, int ta, int tb, Style style, boolean caretOnly, int s, int e) {
        int innerStart = ta + style.open().length();
        int innerEnd = tb - style.close().length();
        int head = style.open().length();
        if (innerStart < innerEnd && text.charAt(innerStart) == ' ') {
            head++;
            innerStart++;
        }
        int tail = style.close().length();
        if (innerStart < innerEnd && text.charAt(innerEnd - 1) == ' ') {
            tail++;
        }
        List<Op> ops = new ArrayList<>();
        ops.add(new Op(tb - tail, tail, ""));
        ops.add(new Op(ta, head, ""));
        if (caretOnly) {
            int caret = s <= ta ? s : s >= tb ? s - head - tail : Math.clamp(s - head, ta, tb - head - tail);
            return new Edit(ops, caret, caret);
        }
        return new Edit(ops, s, e - head - tail);
    }

    /** Whether {@code [ta, tb)} is exactly one block comment: the pair at its ends and nothing inside that breaks it. */
    private static boolean isOneComment(CharSequence text, int ta, int tb, Style style) {
        String open = style.open();
        String close = style.close();
        if (tb - ta < open.length() + close.length()) {
            return false;
        }
        if (!regionMatches(text, ta, open) || !regionMatches(text, tb - close.length(), close)) {
            return false;
        }
        String inner = text.subSequence(ta + open.length(), tb - close.length()).toString();
        return offending(inner, style) == null;
    }

    /**
     * The delimiter that makes {@code body} unsafe to put inside (or take out
     * of) a comment, or null. Not nesting: either delimiter at all. Nesting:
     * the one that is unmatched.
     */
    static String offending(String body, Style style) {
        String open = style.open();
        String close = style.close();
        if (!style.nests() || open.equals(close)) {
            // close first: it is the one that ends the comment early
            if (body.contains(close)) {
                return close;
            }
            return body.contains(open) ? open : null;
        }
        int depth = 0;
        int i = 0;
        while (i < body.length()) {
            if (body.startsWith(open, i)) {
                depth++;
                i += open.length();
            } else if (body.startsWith(close, i)) {
                if (depth == 0) {
                    return close;
                }
                depth--;
                i += close.length();
            } else {
                i++;
            }
        }
        return depth == 0 ? null : open;
    }

    /**
     * The opening tag text ({@code script …} or {@code style …}, lower-cased,
     * without its brackets) of the block that is open at the end of
     * {@code head}, or null in plain markup.
     */
    static String blockAt(String head) {
        String lower = head.toLowerCase(Locale.ROOT);
        int best = -1;
        String found = null;
        for (String tag : BLOCK_TAGS) {
            int open = lastOpening(lower, tag);
            if (open < 0) {
                continue;
            }
            int gt = lower.indexOf('>', open);
            if (gt < 0 || lower.indexOf("</" + tag, gt) >= 0) {
                continue; // still inside the opening tag itself, or the block closed before here
            }
            if (open > best) {
                best = open;
                found = lower.substring(open + 1, gt);
            }
        }
        return found;
    }

    /** The last {@code <tag} that is a whole tag name, not the head of a longer one. */
    private static int lastOpening(String lower, String tag) {
        int from = lower.length();
        while (from >= 0) {
            int at = lower.lastIndexOf("<" + tag, from);
            if (at < 0) {
                return -1;
            }
            int after = at + 1 + tag.length();
            if (after >= lower.length() || lower.charAt(after) == '>'
                    || Character.isWhitespace(lower.charAt(after))) {
                return at;
            }
            from = at - 1;
        }
        return -1;
    }

    /** The script or style tag inside {@code body}, as it is written there, or null. */
    private static String blockBoundary(String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        for (String tag : BLOCK_TAGS) {
            for (String mark : new String[] {"</" + tag, "<" + tag}) {
                int at = lower.indexOf(mark);
                if (at >= 0) {
                    return body.substring(at, at + mark.length()) + ">";
                }
            }
        }
        return null;
    }

    /**
     * A selection that ends at the start of a line was made by selecting
     * whole lines: the comment closes at the end of the last one, not at the
     * start of the line after it. CRLF counts as one terminator.
     */
    private static int withoutFinalTerminator(CharSequence text, int s, int e) {
        int b = e;
        if (b > s && text.charAt(b - 1) == '\n') {
            b--;
            if (b > s && text.charAt(b - 1) == '\r') {
                b--;
            }
        }
        // a selection of nothing but a line end stays what it was
        return b == s ? e : b;
    }

    private static int lineStart(CharSequence text, int at) {
        int i = at;
        while (i > 0 && text.charAt(i - 1) != '\n') {
            i--;
        }
        return i;
    }

    /** The end of the line's content: before its {@code \n} or {@code \r\n}. */
    private static int lineEnd(CharSequence text, int at) {
        int i = at;
        while (i < text.length() && text.charAt(i) != '\n') {
            i++;
        }
        if (i > 0 && i < text.length() && text.charAt(i - 1) == '\r') {
            i--;
        }
        return i;
    }

    private static boolean regionMatches(CharSequence text, int at, String what) {
        if (at < 0 || at + what.length() > text.length()) {
            return false;
        }
        for (int i = 0; i < what.length(); i++) {
            if (text.charAt(at + i) != what.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
