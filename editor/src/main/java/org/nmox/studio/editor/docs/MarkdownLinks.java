package org.nmox.studio.editor.docs;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmox.studio.core.util.Containment;

/**
 * Every relative link in a project's Markdown, checked the way GitHub will
 * render it (3.2.0, Tools ▸ Check Markdown Links…): the file a link names
 * must exist, and the {@code #heading} it names must be a heading of that
 * file under GitHub's anchor rule. Pure over a text reader, so every rule
 * is a unit test; the action does the walking.
 *
 * <p>What it reads: inline links {@code [text](target "title")} and images
 * {@code ![alt](src)} (a broken image is the commonest dead link in a
 * README), angle-bracketed targets {@code [x](<a b.md>)}, and reference
 * definitions {@code [label]: target}. What it leaves alone: anything with
 * a scheme ({@code https:}, {@code mailto:}) or starting {@code //} - this
 * never touches the network - fenced code blocks and inline code spans (a
 * link in an example is an example), and the fragment of a link into a
 * file that is not Markdown ({@code app.js#L10} is GitHub's line anchor).
 * A target starting {@code /} is the repository root's, as GitHub reads
 * it. A link that climbs out of the project is reported, not followed:
 * GitHub cannot follow it either, and a Markdown file in a cloned
 * repository does not get to make the IDE read the disk around it.
 *
 * <p>The anchor rule is the one {@code DocsLinksResolveTest} holds the
 * product's own documentation to: lower-case; keep letters, digits, marks,
 * hyphens and underscores; a space becomes a hyphen; everything else is
 * dropped; a repeated heading gets {@code -1}, {@code -2}. Explicit
 * {@code id="…"}/{@code name="…"} anchors count. A fragment is compared
 * lower-cased, as GitHub's page script resolves one.
 */
public final class MarkdownLinks {

    private MarkdownLinks() {
    }

    /** What is wrong with a link. */
    public enum Kind {
        /** The file or folder it names does not exist. */
        MISSING_FILE,
        /** The file exists and has no heading the fragment names. */
        MISSING_HEADING,
        /** The target climbs out of the project. */
        OUTSIDE_PROJECT
    }

    /** One dead link: where it is written, what it points at, and what is wrong. */
    public record Finding(Path file, int line, String target, Kind kind) {
        /** A link that goes nowhere is an error; a missing heading or a way out is a warning. */
        public boolean error() {
            return kind == Kind.MISSING_FILE;
        }
    }

    /** Everything one run found: the files read and the links checked. */
    public record Report(int files, int links, List<Finding> findings) {
    }

    // Everything here is LINEAR in its input, and that is load-bearing: it
    // runs over a stranger's repository on one lane. The review of 3.2.0
    // measured the first cut at 31 s for one heading line of 2,000 spaces
    // and 21 s for "](" repeated 20,000 times. So inline links and a
    // heading's link text are read by hand-written scanners (inlineLinks,
    // linkText) rather than patterns whose whitespace two quantifiers could
    // both claim, and headings match a greedy line whose trailing
    // whitespace and closing #s are trimmed in Java.
    private static final Pattern REFERENCE =
            Pattern.compile("^ {0,3}\\[[^\\]\\n]+\\]:[ \\t]*(<[^<>\\n]*>|\\S+)", Pattern.MULTILINE);
    private static final Pattern ATX = Pattern.compile("^ {0,3}#{1,6}[ \\t]+([^\\n]*)$", Pattern.MULTILINE);
    private static final Pattern SETEXT = Pattern.compile("^ {0,3}(\\S[^\\n]*)\\n {0,3}(?:=+|-+)[ \\t]*$", Pattern.MULTILINE);
    private static final Pattern SPAN = Pattern.compile("(`+)[^`\\n]*\\1");
    private static final Pattern HTML_ID = Pattern.compile("\\b(?:id|name)=\"([^\"]+)\"");
    private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");

    /**
     * Checks every link in {@code docs} (all under {@code root}), reading
     * text through {@code read} (null for a file it could not read, which is
     * then neither checked nor used for anchors).
     */
    public static Report check(Path root, List<Path> docs, Function<Path, String> read) {
        Path home = root.toAbsolutePath().normalize();
        Map<Path, Set<String>> anchorCache = new HashMap<>();
        List<Finding> findings = new ArrayList<>();
        int files = 0;
        int links = 0;
        for (Path doc0 : docs) {
            Path doc = doc0.toAbsolutePath().normalize();
            String text = read.apply(doc);
            if (text == null) {
                continue;
            }
            files++;
            String body = blankCode(text);
            int[] lineStarts = lineStarts(body);
            List<int[]> spans = new ArrayList<>();
            List<String> targets = new ArrayList<>();
            inlineLinks(body, spans, targets);
            collect(REFERENCE.matcher(body), spans, targets);
            for (int i = 0; i < targets.size(); i++) {
                String raw = targets.get(i);
                String target = raw.startsWith("<") && raw.endsWith(">") ? raw.substring(1, raw.length() - 1) : raw;
                if (target.isEmpty() || SCHEME.matcher(target).matches() || target.startsWith("//")) {
                    continue;
                }
                links++;
                int line = lineOf(lineStarts, spans.get(i)[0]);
                int hash = target.indexOf('#');
                String path = hash < 0 ? target : target.substring(0, hash);
                int query = path.indexOf('?');
                // GitHub serves ?plain=1 and friends from the same file
                String file = decode(query < 0 ? path : path.substring(0, query));
                String fragment = hash < 0 ? null : decode(target.substring(hash + 1));
                Path resolved;
                if (file.isEmpty()) {
                    resolved = doc;
                } else {
                    Kind refused = null;
                    resolved = null;
                    try {
                        Path lexical = (file.startsWith("/") ? home.resolve(file.substring(1))
                                : doc.getParent().resolve(file)).normalize();
                        if (!lexical.startsWith(home)) {
                            refused = Kind.OUTSIDE_PROJECT;
                        } else if (lexical.equals(home)) {
                            resolved = home;
                        } else {
                            // the one guard (core Containment): it follows links, so
                            // a link inside the project that leads out is refused
                            // here rather than read
                            resolved = Containment.resolvePath(home.toFile(), home.relativize(lexical).toString());
                            if (resolved == null) {
                                refused = Kind.OUTSIDE_PROJECT;
                            }
                        }
                    } catch (InvalidPathException bad) {
                        // a NUL, or on Windows ? * < > | ": a name no file can have
                        refused = Kind.MISSING_FILE;
                    }
                    if (refused != null) {
                        findings.add(new Finding(doc, line, target, refused));
                        continue;
                    }
                }
                if (!Files.exists(resolved)) {
                    findings.add(new Finding(doc, line, target, Kind.MISSING_FILE));
                    continue;
                }
                if (fragment != null && !fragment.isEmpty() && isMarkdown(resolved)) {
                    Set<String> anchors = anchorCache.computeIfAbsent(resolved, p -> anchors(read.apply(p)));
                    if (anchors != null && !anchors.contains(fragment.toLowerCase(Locale.ROOT))) {
                        findings.add(new Finding(doc, line, target, Kind.MISSING_HEADING));
                    }
                }
            }
        }
        return new Report(files, links, List.copyOf(findings));
    }

    /**
     * Every inline link's target, {@code [text](target "title")}: from each
     * {@code "]("} the target runs to whitespace or a bracket, a parenthesis
     * or an angle bracket (or is {@code <...>} on one line), then an optional
     * quoted title, then {@code ")"}. Each scan stops at the next bracket,
     * so the whole text is read a bounded number of times.
     */
    static void inlineLinks(String body, List<int[]> spans, List<String> targets) {
        int n = body.length();
        int at = 0;
        while ((at = body.indexOf("](", at)) >= 0) {
            int j = skipBlanks(body, at + 2);
            at += 2;
            int start = j;
            int end;
            if (j < n && body.charAt(j) == '<') {
                int k = j + 1;
                while (k < n && body.charAt(k) != '>' && body.charAt(k) != '<' && body.charAt(k) != '\n') {
                    k++;
                }
                if (k >= n || body.charAt(k) != '>') {
                    continue;
                }
                end = k + 1;
            } else {
                int k = j;
                while (k < n && "()[]<>".indexOf(body.charAt(k)) < 0 && !Character.isWhitespace(body.charAt(k))) {
                    k++;
                }
                if (k == j) {
                    continue;
                }
                end = k;
            }
            int k = skipBlanks(body, end);
            if (k < n && k > end && (body.charAt(k) == '"' || body.charAt(k) == '\'')) {
                char quote = body.charAt(k);
                int close = k + 1;
                while (close < n && body.charAt(close) != quote && body.charAt(close) != '\n') {
                    close++;
                }
                if (close >= n || body.charAt(close) != quote) {
                    continue;
                }
                k = skipBlanks(body, close + 1);
            }
            if (k < n && body.charAt(k) == ')') {
                spans.add(new int[] {start, end});
                targets.add(body.substring(start, end));
            }
        }
    }

    private static int skipBlanks(String s, int i) {
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }

    /**
     * A heading's text with each {@code [text](url)} (or image) reduced to
     * its text, as GitHub's anchor reads it. One pass: an unclosed
     * {@code (} ends the search for closers for the rest of the line.
     */
    static String linkText(String heading) {
        StringBuilder out = new StringBuilder();
        int open = -1;
        boolean noCloser = false;
        int i = 0;
        while (i < heading.length()) {
            char c = heading.charAt(i);
            if (c == '[') {
                if (open >= 0) {
                    out.append(heading, open, i);
                }
                open = i;
                i++;
                continue;
            }
            if (open >= 0 && c == ']' && i + 1 < heading.length() && heading.charAt(i + 1) == '(' && !noCloser) {
                int close = heading.indexOf(')', i + 2);
                if (close < 0) {
                    noCloser = true;
                } else {
                    int textFrom = open + 1;
                    if (out.length() > 0 && out.charAt(out.length() - 1) == '!') {
                        out.setLength(out.length() - 1);
                    }
                    out.append(heading, textFrom, i);
                    open = -1;
                    i = close + 1;
                    continue;
                }
            }
            if (open < 0) {
                out.append(c);
            }
            i++;
        }
        if (open >= 0) {
            out.append(heading, open, heading.length());
        }
        return out.toString();
    }

    private static void collect(Matcher m, List<int[]> spans, List<String> targets) {
        while (m.find()) {
            spans.add(new int[] {m.start(1), m.end(1)});
            targets.add(m.group(1));
        }
    }

    /** True for a Markdown file by its name. */
    public static boolean isMarkdown(Path p) {
        String n = p.getFileName() == null ? "" : p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".md") || n.endsWith(".markdown");
    }

    /**
     * The anchors a Markdown text defines: its headings under GitHub's rule
     * (repeats numbered) and its explicit ids, all lower-case; null for a
     * file that could not be read.
     */
    static Set<String> anchors(String text) {
        if (text == null) {
            return null;
        }
        String body = blankFences(text);
        Set<String> out = new HashSet<>();
        Map<String, Integer> seen = new HashMap<>();
        List<int[]> at = new ArrayList<>();
        List<String> headings = new ArrayList<>();
        Matcher atx = ATX.matcher(body);
        while (atx.find()) {
            at.add(new int[] {atx.start()});
            headings.add(trimClosing(atx.group(1)));
        }
        Matcher setext = SETEXT.matcher(body);
        while (setext.find()) {
            String h = setext.group(1).stripTrailing();
            if (!h.startsWith("#") && !h.startsWith("-") && !h.startsWith("|")) {
                at.add(new int[] {setext.start()});
                headings.add(h);
            }
        }
        // number repeats in document order, as GitHub does
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < at.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> Integer.compare(at.get(a)[0], at.get(b)[0]));
        for (int i : order) {
            String s = slug(linkText(headings.get(i)));
            int n = seen.merge(s, 1, Integer::sum) - 1;
            out.add(n == 0 ? s : s + "-" + n);
        }
        Matcher id = HTML_ID.matcher(body);
        while (id.find()) {
            out.add(id.group(1).toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /**
     * An ATX heading's text without its optional closing run of {@code #}s
     * (which must follow whitespace, or be the whole text) and trailing
     * whitespace: what the old lazy pattern did, without its backtracking.
     */
    static String trimClosing(String raw) {
        String h = raw.stripTrailing();
        int end = h.length();
        while (end > 0 && h.charAt(end - 1) == '#') {
            end--;
        }
        if (end < h.length() && (end == 0 || h.charAt(end - 1) == ' ' || h.charAt(end - 1) == '\t')) {
            h = h.substring(0, end).stripTrailing();
        }
        return h;
    }

    /** GitHub's heading anchor. */
    static String slug(String heading) {
        String h = heading.strip().toLowerCase(Locale.ROOT).replaceAll("<[^>]+>", "").replace("`", "");
        StringBuilder b = new StringBuilder();
        h.codePoints().forEach(cp -> {
            int type = Character.getType(cp);
            if (cp == ' ') {
                b.append('-');
            } else if (cp == '-' || cp == '_' || Character.isLetterOrDigit(cp)
                    || type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                b.appendCodePoint(cp);
            }
        });
        return b.toString();
    }

    /**
     * The text with fenced code blocks and inline code spans turned into
     * spaces, newlines kept, so offsets and line numbers still point at the
     * original.
     */
    static String blankCode(String text) {
        char[] out = blankFences(text).toCharArray();
        // inline code spans, on what is left: a run of backticks to the next
        // run of the same length on the same paragraph
        Matcher span = SPAN.matcher(new String(out));
        while (span.find()) {
            blank(out, span.start(), span.end());
        }
        return new String(out);
    }

    /**
     * The text with only fenced code blocks turned into spaces: what a
     * heading is read from, because GitHub keeps a heading's inline code in
     * its anchor ({@code ## `nmox` from a terminal} is
     * {@code #nmox-from-a-terminal}).
     */
    static String blankFences(String text) {
        char[] out = text.toCharArray();
        String[] lines = text.split("\n", -1);
        int offset = 0;
        String fence = null;
        for (String line : lines) {
            String stripped = line.stripLeading();
            boolean fenceLine = stripped.startsWith("```") || stripped.startsWith("~~~");
            if (fence == null && fenceLine) {
                fence = stripped.substring(0, 3);
                blank(out, offset, offset + line.length());
            } else if (fence != null) {
                blank(out, offset, offset + line.length());
                if (stripped.startsWith(fence)) {
                    fence = null;
                }
            }
            offset += line.length() + 1;
        }
        return new String(out);
    }

    private static void blank(char[] out, int from, int to) {
        for (int i = from; i < to && i < out.length; i++) {
            if (out[i] != '\n') {
                out[i] = ' ';
            }
        }
    }

    private static int[] lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    /** The 1-based line holding {@code offset}. */
    private static int lineOf(int[] starts, int offset) {
        int at = java.util.Arrays.binarySearch(starts, offset);
        return (at >= 0 ? at : -at - 2) + 1;
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            return s;
        }
    }

    /** The reader the action uses: bounded, UTF-8, null for what it could not read. */
    public static String readBounded(Path p, long maxBytes) {
        try {
            return org.nmox.studio.core.util.BoundedReads.read(p, maxBytes);
        } catch (IOException ex) {
            return null;
        }
    }
}
