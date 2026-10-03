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
 * broken code. A range that crosses the block's own tag is refused, from
 * either side: the state is read at the range's start AND its end, and a
 * markup range that opens a block, or ends inside one, would put the
 * {@code -->} in the middle of the script. Markup is read the way a
 * browser reads it for these two elements: a commented-out
 * {@code <script>} opens nothing. An Astro component's frontmatter (the
 * script between the leading {@code ---} fences) is TypeScript and takes
 * {@code /* *}{@code /}; a range on or across a fence is refused.
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
            Outcome embedded = embedded(mime, before.toString(), text, selStart, selEnd);
            if (embedded != null) {
                return embedded;
            }
        }
        return toggle(text, selStart, selEnd, style);
    }

    /**
     * The answer inside markup that holds other languages, or null when the
     * range is plain markup and the markup's own pair applies. Every
     * position is decided in the document's text ({@code head}, then
     * {@code text}), so the state at the range's start and at its end are
     * read from the same characters.
     */
    private static Outcome embedded(String mime, String head, CharSequence text, int selStart, int selEnd) {
        int s = Math.clamp(Math.min(selStart, selEnd), 0, text.length());
        int e = Math.clamp(Math.max(selStart, selEnd), 0, text.length());
        boolean caretOnly = s == e;
        String all = head + text;
        int offset = head.length();
        int rangeStart = offset + (caretOnly ? lineStart(text, s) : s);
        int rangeEnd = offset + (caretOnly ? lineEnd(text, s) : e);
        String body = text.subSequence(rangeStart - offset, rangeEnd - offset).toString();
        int markupFrom = 0;
        if ("text/x-astro".equals(mime)) {
            int[] fence = frontmatter(all);
            if (fence != null) {
                if (rangeStart >= fence[1] && rangeEnd <= fence[2]) {
                    // the component script: TypeScript, between the fences
                    return toggle(text, selStart, selEnd, C);
                }
                if (rangeStart < fence[3]) {
                    // a fence line, or a range across one: neither pair can wrap it
                    return new Refusal(FENCE);
                }
                markupFrom = fence[3];
            }
        }
        Block start = scan(all, markupFrom, rangeStart).open();
        Block end = scan(all, markupFrom, rangeEnd).open();
        if (start == null) {
            // plain markup: <!-- --> holds only markup, so a range that opens
            // a script or style block, or stops inside one, is refused
            Scan inside = scan(body, 0, body.length());
            String opened = inside.open() != null ? inside.open().name()
                    : inside.unfinished() != null ? inside.unfinished() : end != null ? end.name() : null;
            return opened == null ? null : new Refusal("<" + opened + ">");
        }
        if (start.tag().startsWith("style") && INDENTED_SASS.matcher(start.tag()).find()) {
            return new NoBlockComment();
        }
        // a range that leaves the block cannot be commented as the block's language
        String boundary = blockBoundary(body);
        if (boundary != null) {
            return new Refusal(boundary);
        }
        if (!start.equals(end)) {
            return new Refusal("</" + start.name() + ">");
        }
        return toggle(text, selStart, selEnd, C);
    }

    /** What an Astro refusal names: the component script's fence. */
    static final String FENCE = "---";

    /**
     * An Astro component's frontmatter: the script between two {@code ---}
     * lines at the top of the file (blank lines may come first). The
     * answer is {opening fence, script start, script end, end of the
     * closing fence}, the script running to the end when no fence closes
     * it; null when the file does not open with a fence.
     */
    static int[] frontmatter(CharSequence all) {
        int i = 0;
        while (i < all.length() && Character.isWhitespace(all.charAt(i))) {
            i++;
        }
        if (i >= all.length()) {
            return null;
        }
        int open = lineStart(all, i);
        if (!isFence(all, open)) {
            return null;
        }
        int bodyStart = nextLine(all, open);
        int at = bodyStart;
        while (at < all.length()) {
            if (isFence(all, at)) {
                return new int[] {open, bodyStart, at, lineEnd(all, at)};
            }
            at = nextLine(all, at);
        }
        return new int[] {open, bodyStart, all.length(), all.length()};
    }

    /** The start of the line after the one starting at {@code at}, or the end of the text. */
    private static int nextLine(CharSequence all, int at) {
        int i = at;
        while (i < all.length() && all.charAt(i) != '\n') {
            i++;
        }
        return Math.min(all.length(), i + 1);
    }

    /** Whether the line starting at {@code at} is {@code ---} and nothing else but blanks. */
    private static boolean isFence(CharSequence all, int at) {
        return all.subSequence(at, lineEnd(all, at)).toString().strip().equals(FENCE);
    }

    /**
     * A script or style block that is open: where its opening tag starts,
     * and that tag's text ({@code script …}, lower-cased, without brackets).
     */
    record Block(int open, String tag) {

        /** The element's name: {@code script} or {@code style}. */
        String name() {
            return tag.startsWith("script") ? "script" : "style";
        }
    }

    /**
     * Markup read from {@code from} to {@code to}: the block open at
     * {@code to} (null in plain markup), and the name of a script or style
     * opening tag that {@code to} falls inside, before its {@code >}.
     */
    private record Scan(Block open, String unfinished) {
    }

    /**
     * Reads markup the way a browser does for these two elements: an HTML
     * comment hides what it holds (a commented-out {@code <script>} opens
     * nothing), and inside a script or style block only its own end tag
     * means anything (a {@code <!--} in a script's string is not a
     * comment). Case-insensitive, without copying the text.
     */
    private static Scan scan(CharSequence all, int from, int to) {
        int end = Math.min(to, all.length());
        int i = from;
        while (i < end) {
            int lt = indexOf(all, '<', i, end);
            if (lt < 0) {
                break;
            }
            if (regionMatches(all, lt, "<!--", false)) {
                int close = indexOf(all, "-->", lt + 4, end, false);
                if (close < 0) {
                    return new Scan(null, null); // inside a comment: markup
                }
                i = close + 3;
                continue;
            }
            String tag = tagAt(all, lt, end);
            if (tag == null) {
                i = lt + 1;
                continue;
            }
            int gt = indexOf(all, '>', lt, end);
            if (gt < 0) {
                return new Scan(null, tag); // inside the opening tag itself
            }
            int close = indexOf(all, "</" + tag, gt, end, true);
            if (close < 0) {
                return new Scan(new Block(lt, all.subSequence(lt + 1, gt).toString().toLowerCase(Locale.ROOT)), null);
            }
            i = close + 2 + tag.length();
        }
        return new Scan(null, null);
    }

    /** The block tag whose whole name starts at {@code lt} ({@code <script} but not {@code <scripted}), or null. */
    private static String tagAt(CharSequence all, int lt, int end) {
        for (String tag : BLOCK_TAGS) {
            int after = lt + 1 + tag.length();
            if (after <= all.length() && regionMatches(all, lt + 1, tag, true)
                    && (after >= end || all.charAt(after) == '>' || Character.isWhitespace(all.charAt(after)))) {
                return tag;
            }
        }
        return null;
    }

    private static int indexOf(CharSequence all, char c, int from, int end) {
        for (int i = from; i < end; i++) {
            if (all.charAt(i) == c) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOf(CharSequence all, String what, int from, int end, boolean ignoreCase) {
        for (int i = from; i + what.length() <= end; i++) {
            if (regionMatches(all, i, what, ignoreCase)) {
                return i;
            }
        }
        return -1;
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
        Block open = scan(head, 0, head.length()).open();
        return open == null ? null : open.tag();
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
        return regionMatches(text, at, what, false);
    }

    private static boolean regionMatches(CharSequence text, int at, String what, boolean ignoreCase) {
        if (at < 0 || at + what.length() > text.length()) {
            return false;
        }
        for (int i = 0; i < what.length(); i++) {
            char c = text.charAt(at + i);
            char w = what.charAt(i);
            if (c != w && !(ignoreCase && Character.toLowerCase(c) == Character.toLowerCase(w))) {
                return false;
            }
        }
        return true;
    }
}
