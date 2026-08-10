package org.nmox.studio.editor.design;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Design tokens — CSS custom properties — as data (v1.330.0). A design
 * system lives in declarations like {@code --brand-primary: #6c5ce7}
 * (usually in a tokens.css or a :root block), and everything that makes
 * them first-class rides this one core: completion inside {@code var(},
 * ⌘-click from a usage to its declaration, and swatches that resolve
 * THROUGH the token. {@link CssColors} deliberately stops at literal
 * colors and says so in its javadoc; this class is the indirection it
 * points at.
 */
public final class CssTokens {

    private CssTokens() {
    }

    /** One declaration: name (with the -- prefix), raw value, offset of the name. */
    public record Token(String name, String value, int offset) {
    }

    /** A declaration found by the project scan, with its file. */
    public record ProjectToken(File file, String name, String value, int offset) {
    }

    // custom property names: -- then letters/digits/hyphens/underscores
    private static final Pattern DECLARATION = Pattern.compile(
            "(--[A-Za-z0-9_-]+)\\s*:\\s*([^;}]+)");

    /** Directories a token scan must never descend into. */
    private static final Set<String> SKIP_DIRS = Set.of(
            "node_modules", ".git", "dist", "build", "out", "coverage",
            "target", ".next", ".nuxt", ".angular", ".svelte-kit");

    /** Bounded scan: files (by count and size) a project sweep may read. */
    static final int MAX_FILES = 60;
    static final int MAX_FILE_BYTES = 256 * 1024;

    // ---- document-local ---------------------------------------------------

    /**
     * Every custom-property declaration in {@code css}, first
     * declaration of a name wins (the cascade's last-wins rule applies
     * at COMPUTE time; for navigation and completion the first
     * declaration is the one a designer means). Comments are blanked
     * before matching so a commented-out token is not a token.
     */
    public static Map<String, Token> declarations(String css) {
        Map<String, Token> out = new LinkedHashMap<>();
        Matcher m = DECLARATION.matcher(blankComments(css));
        while (m.find()) {
            // no inside-var() guard needed, PROVEN by mutation (v1.330.0):
            // the pattern requires a ':' after the name, and inside var()
            // a name is only ever followed by ',' or ')' — a guard here
            // was equivalent-mutant dead code and was removed
            out.putIfAbsent(m.group(1),
                    new Token(m.group(1), m.group(2).strip(), m.start(1)));
        }
        return out;
    }

    /** True when the offset sits inside a {@code var(...)} call's parens. */
    private static boolean insideVar(String css, int offset) {
        int open = css.lastIndexOf('(', offset);
        if (open <= 2) {
            return false;
        }
        int close = css.lastIndexOf(')', offset);
        if (close > open) {
            return false;                 // the last paren before us is closed
        }
        String head = css.substring(Math.max(0, open - 3), open);
        return head.endsWith("var");
    }

    /** Comments become spaces so offsets survive and their text cannot match. */
    static String blankComments(String css) {
        StringBuilder sb = new StringBuilder(css);
        int i = 0;
        while ((i = sb.indexOf("/*", i)) >= 0) {
            int end = sb.indexOf("*/", i + 2);
            int stop = end < 0 ? sb.length() : end + 2;
            for (int k = i; k < stop; k++) {
                if (sb.charAt(k) != '\n') {
                    sb.setCharAt(k, ' ');
                }
            }
            i = stop;
        }
        return sb.toString();
    }

    // ---- project-wide -----------------------------------------------------

    private record CacheKey(String path, long mtime, long size) {
    }

    private static final Map<CacheKey, List<ProjectToken>> CACHE =
            new ConcurrentHashMap<>();

    /**
     * Every token declared in the project's stylesheets — a BOUNDED
     * walk ({@value #MAX_FILES} files of ≤{@value #MAX_FILE_BYTES}
     * bytes, heavy directories skipped), each file's parse cached by
     * (mtime, size) so repeated completions cost stats, not reads.
     * Callers run this OFF the EDT; the walk touches disk.
     */
    public static List<ProjectToken> scanProject(File root) {
        List<ProjectToken> out = new ArrayList<>();
        if (root == null || !root.isDirectory()) {
            return out;
        }
        List<File> sheets = new ArrayList<>();
        collect(root, sheets, 0);
        for (File f : sheets) {
            CacheKey key = new CacheKey(f.getAbsolutePath(), f.lastModified(), f.length());
            List<ProjectToken> cached = CACHE.get(key);
            if (cached == null) {
                cached = parseFile(f);
                CACHE.put(key, cached);
            }
            out.addAll(cached);
        }
        return out;
    }

    private static void collect(File dir, List<File> sheets, int depth) {
        if (depth > 6 || sheets.size() >= MAX_FILES) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File f : children) {
            if (sheets.size() >= MAX_FILES) {
                return;
            }
            String name = f.getName();
            if (f.isDirectory()) {
                if (!SKIP_DIRS.contains(name) && !name.startsWith(".")) {
                    collect(f, sheets, depth + 1);
                }
            } else if ((name.endsWith(".css") || name.endsWith(".scss")
                    || name.endsWith(".less")) && f.length() <= MAX_FILE_BYTES) {
                sheets.add(f);
            }
        }
    }

    private static List<ProjectToken> parseFile(File f) {
        try {
            String css = Files.readString(f.toPath());
            List<ProjectToken> tokens = new ArrayList<>();
            for (Token t : declarations(css).values()) {
                tokens.add(new ProjectToken(f, t.name(), t.value(), t.offset()));
            }
            return List.copyOf(tokens);
        } catch (IOException | OutOfMemoryError unreadable) {
            return List.of();
        }
    }

    // ---- the var() context ------------------------------------------------

    /**
     * When the caret sits inside {@code var(} typing a token name, the
     * partial name typed so far ("" right after the paren); null when
     * the caret is anywhere else. The completion provider's whole
     * trigger condition, pure and testable.
     */
    public static String varPrefix(String beforeCaret) {
        // a hand scan, not a regex: find-sec-bugs reads the natural
        // "var\(\s*(--…)*$" shape as ReDoS-prone, and the house law is
        // fix-by-idiom, never exclusion (v1.32.0)
        int i = beforeCaret.length();
        int nameStart = i;
        while (nameStart > 0 && isNameChar(beforeCaret.charAt(nameStart - 1))) {
            nameStart--;
        }
        String partial = beforeCaret.substring(nameStart);
        if (!partial.isEmpty() && !partial.startsWith("--")) {
            return null;              // mid-word, but not a token name
        }
        int cursor = nameStart;
        while (cursor > 0 && beforeCaret.charAt(cursor - 1) == ' ') {
            cursor--;
        }
        if (cursor < 4 || !beforeCaret.startsWith("var(", cursor - 4)) {
            return null;              // not inside var( — calc( etc. stay out
        }
        return partial;
    }

    /**
     * The {@code --name} span under {@code offset} when it sits inside a
     * {@code var(...)} — the hyperlink's subject. Returns {start, end}
     * or null.
     */
    public static int[] varNameSpanAt(String text, int offset) {
        if (offset < 0 || offset > text.length()) {
            return null;
        }
        int start = offset;
        while (start > 0 && isNameChar(text.charAt(start - 1))) {
            start--;
        }
        int end = offset;
        while (end < text.length() && isNameChar(text.charAt(end))) {
            end++;
        }
        if (end - start < 3 || text.charAt(start) != '-' || text.charAt(start + 1) != '-') {
            return null;
        }
        return insideVar(text, start) ? new int[] {start, end} : null;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }

    // ---- swatches through the token --------------------------------------

    /**
     * Every {@code var(--x)} USAGE whose document-local token value is a
     * color, as paintable spans over the {@code --x} name — the swatch
     * resolving THROUGH the indirection {@link CssColors} stops at.
     * Document-local by design (recorded limit): the highlighter runs on
     * every edit and must never touch disk; cross-file resolution
     * belongs to completion and the ⌘-click jump, which run off the EDT.
     */
    public static List<CssColors.ColorSpan> varUsageColorSpans(String text) {
        List<CssColors.ColorSpan> out = new ArrayList<>();
        Map<String, Token> local = declarations(text);
        if (local.isEmpty()) {
            return out;
        }
        Matcher m = Pattern.compile("var\\(\\s*(--[A-Za-z0-9_-]+)").matcher(text);
        while (m.find()) {
            Token t = local.get(m.group(1));
            if (t == null) {
                continue;
            }
            List<CssColors.ColorSpan> value = CssColors.scan(t.value());
            if (value.size() == 1) {
                out.add(new CssColors.ColorSpan(
                        m.start(1), m.end(1), value.get(0).color()));
            }
        }
        return out;
    }
}
