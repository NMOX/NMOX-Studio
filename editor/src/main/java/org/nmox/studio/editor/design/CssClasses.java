package org.nmox.studio.editor.design;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class selectors as data (v2.27.0): the {@code class} attribute is the
 * markup side of every stylesheet, and this core makes the connection
 * navigable — completion inside {@code class="…"} offering the classes
 * the project's stylesheets actually declare, and ⌘-click from a class
 * usage to its rule. The sibling of {@link CssTokens}, riding the same
 * bounded cached project walk.
 *
 * <p><b>Classes only, deliberately.</b> {@code #id} selectors are
 * textually indistinguishable from hex colors without a real CSS value
 * parser ({@code #fff} is both a valid id selector and the most common
 * color literal), so an id scan would either miss ids or invent them
 * from every color in the sheet. The class dot has no such twin; the
 * id attribute stays out until a parser-backed scan exists.
 */
public final class CssClasses {

    private CssClasses() {
    }

    /** One class selector: name without the dot, offset OF the dot. */
    public record Selector(String name, int offset) {
    }

    /** A selector found by the project scan, with its file. */
    public record ProjectSelector(File file, String name, int offset) {
    }

    // .name — a letter/underscore start, then the CSS ident family.
    // No digit lookbehind: `.5em`/`0.5` already fail the LETTER-start
    // requirement, so a lookbehind was equivalent-mutant dead code
    // (proven by mutation here exactly as in CssTokens v1.330.0 —
    // relaxing the letter start to digits fails decimalsAreNotClasses
    // by name, so the test pins the rule that does the real work).
    private static final Pattern CLASS_SELECTOR = Pattern.compile(
            "\\.([A-Za-z_][A-Za-z0-9_-]*)");

    // ---- document-local ---------------------------------------------------

    /**
     * Every class selector declared in {@code css}, first occurrence of
     * a name wins (navigation goes to the first rule; the cascade's
     * last-wins applies at COMPUTE time, not to "where is this class?").
     * Comments, strings, and {@code url(...)} bodies are blanked first
     * so {@code content: ".fake"} and {@code url(a.png)} never register.
     */
    public static Map<String, Selector> selectors(String css) {
        Map<String, Selector> out = new LinkedHashMap<>();
        Matcher m = CLASS_SELECTOR.matcher(blankNonSelectors(css));
        while (m.find()) {
            out.putIfAbsent(m.group(1), new Selector(m.group(1), m.start()));
        }
        return out;
    }

    /**
     * Comments (both forms, via {@link CssTokens#blankComments}), then
     * string literals and unquoted {@code url(...)} bodies become
     * spaces, offsets preserved. A dot inside any of them is prose or a
     * filename, never a selector.
     */
    static String blankNonSelectors(String css) {
        StringBuilder sb = new StringBuilder(CssTokens.blankComments(css));
        // string literals, both quote chars, backslash-escape aware
        for (int i = 0; i < sb.length(); i++) {
            char q = sb.charAt(i);
            if (q == '"' || q == '\'') {
                int k = i + 1;
                while (k < sb.length() && sb.charAt(k) != q) {
                    if (sb.charAt(k) == '\\') {
                        k++;
                    }
                    k++;
                }
                for (int j = i + 1; j < Math.min(k, sb.length()); j++) {
                    if (sb.charAt(j) != '\n') {
                        sb.setCharAt(j, ' ');
                    }
                }
                i = Math.min(k, sb.length() - 1);
            }
        }
        // unquoted url(...) bodies: url(a.png) carries a dot that is a
        // file extension, not a class
        int u = 0;
        while ((u = sb.indexOf("url(", u)) >= 0) {
            int close = sb.indexOf(")", u + 4);
            int stop = close < 0 ? sb.length() : close;
            for (int j = u + 4; j < stop; j++) {
                if (sb.charAt(j) != '\n') {
                    sb.setCharAt(j, ' ');
                }
            }
            u = stop;
        }
        return sb.toString();
    }

    // ---- project-wide -----------------------------------------------------

    private record CacheEntry(long mtime, long size, List<ProjectSelector> selectors) {
    }

    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    /**
     * Every class selector declared in the project's stylesheets and
     * the markup family's {@code <style>} blocks — the same bounded
     * walk and per-path freshness cache as {@link CssTokens#scanProject}
     * (one entry per file, replaced on change). Callers run this OFF
     * the EDT; the walk touches disk.
     */
    public static List<ProjectSelector> scanProject(File root) {
        List<ProjectSelector> out = new ArrayList<>();
        if (root == null || !root.isDirectory()) {
            return out;
        }
        for (File f : CssTokens.collectStylesheets(root)) {
            String path = f.getAbsolutePath();
            long mtime = f.lastModified();
            long size = f.length();
            CacheEntry entry = CACHE.get(path);
            if (entry == null || entry.mtime() != mtime || entry.size() != size) {
                entry = new CacheEntry(mtime, size, parseFile(f));
                CACHE.put(path, entry);
            }
            out.addAll(entry.selectors());
        }
        return out;
    }

    private static List<ProjectSelector> parseFile(File f) {
        try {
            String text = Files.readString(f.toPath());
            String n = f.getName();
            Map<String, Selector> decls;
            if (n.endsWith(".html") || n.endsWith(".htm")
                    || n.endsWith(".vue") || n.endsWith(".svelte")) {
                // markup: only <style> regions declare selectors — a
                // class ATTRIBUTE is a usage, never a declaration
                decls = new LinkedHashMap<>();
                for (HtmlStyleRegions.Region r : HtmlStyleRegions.find(text)) {
                    int shift = r.start();
                    selectors(text.substring(r.start(), r.end())).forEach(
                            (name, s) -> decls.putIfAbsent(name,
                                    new Selector(name, s.offset() + shift)));
                }
            } else {
                decls = selectors(text);
            }
            List<ProjectSelector> out = new ArrayList<>();
            for (Selector s : decls.values()) {
                out.add(new ProjectSelector(f, s.name(), s.offset()));
            }
            return List.copyOf(out);
        } catch (IOException | OutOfMemoryError unreadable) {
            return List.of();
        }
    }

    // ---- the class attribute context --------------------------------------

    /**
     * When the caret sits inside a {@code class="…"} attribute value
     * typing a class name, the partial name typed so far ("" right
     * after the quote or a space); null anywhere else. The completion
     * trigger, pure and testable. Only the literal {@code class}
     * attribute qualifies — {@code href}, {@code data-class}, Vue's
     * {@code :class} bindings (JavaScript) all refuse.
     */
    public static String attrPrefix(String beforeCaret) {
        int i = beforeCaret.length();
        int nameStart = i;
        while (nameStart > 0 && isNameChar(beforeCaret.charAt(nameStart - 1))) {
            nameStart--;
        }
        // between the word and the opening quote: names and spaces only
        int cursor = nameStart;
        while (cursor > 0) {
            char c = beforeCaret.charAt(cursor - 1);
            if (c == '"' || c == '\'') {
                break;
            }
            if (c != ' ' && !isNameChar(c)) {
                return null;          // punctuation → not a class value
            }
            cursor--;
        }
        if (cursor == 0) {
            return null;              // no quote found
        }
        int q = cursor - 1;           // the quote character's index
        int a = q;                    // scan back over ="  to the attr name
        while (a > 0 && beforeCaret.charAt(a - 1) == ' ') {
            a--;
        }
        if (a == 0 || beforeCaret.charAt(a - 1) != '=') {
            return null;
        }
        a--;
        while (a > 0 && beforeCaret.charAt(a - 1) == ' ') {
            a--;
        }
        int attrEnd = a;
        while (a > 0 && isNameChar(beforeCaret.charAt(a - 1))) {
            a--;
        }
        String attr = beforeCaret.substring(a, attrEnd);
        // the char before the attribute must open it (tag space), so
        // data-class and formaction never qualify
        boolean opensAttr = a == 0 || beforeCaret.charAt(a - 1) == ' '
                || beforeCaret.charAt(a - 1) == '\n' || beforeCaret.charAt(a - 1) == '\t';
        if (!opensAttr || !"class".equalsIgnoreCase(attr)) {
            return null;
        }
        return beforeCaret.substring(nameStart);
    }

    /**
     * The class-name span under {@code offset} when it sits inside a
     * {@code class="…"} attribute value — the ⌘-click subject. Returns
     * {start, end} or null.
     */
    public static int[] attrNameSpanAt(String text, int offset) {
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
        if (end == start) {
            return null;
        }
        return attrPrefix(text.substring(0, end)) == null
                ? null : new int[] {start, end};
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }

    // ---- the reverse direction: selector → markup usages (v2.28.0) --------

    /**
     * The class-NAME span when {@code offset} sits on a {@code .name}
     * selector in {@code css} (dot excluded) — the stylesheet-side
     * ⌘-click subject. Comments/strings/url() are blanked first, so a
     * dot in prose is never a jump point. Returns {start, end} or null.
     */
    public static int[] selectorSpanAt(String css, int offset) {
        if (offset < 0 || offset > css.length()) {
            return null;
        }
        Matcher m = CLASS_SELECTOR.matcher(blankNonSelectors(css));
        while (m.find()) {
            if (offset >= m.start(1) && offset <= m.end(1)) {
                return new int[] {m.start(1), m.end(1)};
            }
            if (m.start(1) > offset) {
                break;
            }
        }
        return null;
    }

    /** One class usage in a markup file's {@code class} attribute. */
    public record Usage(File file, int offset) {
    }

    // class attribute values, both quote styles
    private static final Pattern CLASS_ATTR = Pattern.compile(
            "\\bclass\\s*=\\s*(\"([^\"]*)\"|'([^']*)')");

    /**
     * Offsets (of the token) where {@code name} appears as a WHOLE
     * space-separated token inside a {@code class="…"} attribute value
     * of {@code markup}. Prose mentions, other attributes, and
     * super-strings ({@code cardigan} for {@code card}) never match.
     */
    public static List<Integer> usagesIn(String markup, String name) {
        List<Integer> out = new ArrayList<>();
        Matcher m = CLASS_ATTR.matcher(markup);
        while (m.find()) {
            int g = m.group(2) != null ? 2 : 3;
            String value = m.group(g);
            int base = m.start(g);
            int i = 0;
            while (i < value.length()) {
                int end = value.indexOf(' ', i);
                if (end < 0) {
                    end = value.length();
                }
                if (value.substring(i, end).equals(name)) {
                    out.add(base + i);
                }
                i = end + 1;
            }
        }
        return out;
    }

    /** Extensions of the markup family the usage search reads. */
    static boolean isMarkupFile(String n) {
        return n.endsWith(".html") || n.endsWith(".htm")
                || n.endsWith(".vue") || n.endsWith(".svelte");
    }

    /**
     * Every {@code class="…"} usage of {@code name} across the
     * project's markup files, capped at {@code cap} — a click-time
     * query, bounded by the same walk as the scans but uncached (a
     * usage list is wanted once, not on every keystroke). Callers run
     * this OFF the EDT.
     */
    public static List<Usage> findUsages(File root, String name, int cap) {
        List<Usage> out = new ArrayList<>();
        if (root == null || !root.isDirectory() || name == null || name.isEmpty()) {
            return out;
        }
        for (File f : CssTokens.collectStylesheets(root)) {
            if (!isMarkupFile(f.getName())) {
                continue;
            }
            try {
                for (int offset : usagesIn(Files.readString(f.toPath()), name)) {
                    out.add(new Usage(f, offset));
                    if (out.size() >= cap) {
                        return out;
                    }
                }
            } catch (IOException | OutOfMemoryError unreadable) {
                // skip the file, keep the sweep
            }
        }
        return out;
    }

    // ---- rename (v2.29.0) -------------------------------------------------

    /** A syntactically valid class name: the CSS ident family. */
    public static boolean validClassName(String name) {
        if (name == null || name.isEmpty()
                || !(Character.isLetter(name.charAt(0)) || name.charAt(0) == '_')) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!isNameChar(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Every {@code .name} selector span (name only, dot excluded) in a
     * stylesheet text — ALL occurrences, because a rename must hit every
     * rule, not the navigation scan's first-wins entry.
     */
    static List<int[]> selectorSpans(String css, String name) {
        List<int[]> out = new ArrayList<>();
        Matcher m = CLASS_SELECTOR.matcher(blankNonSelectors(css));
        while (m.find()) {
            if (m.group(1).equals(name)) {
                out.add(new int[] {m.start(1), m.end(1)});
            }
        }
        return out;
    }

    /**
     * {@code text} with the class renamed everywhere it means the
     * class: selector spans (whole name — {@code .cardigan} survives a
     * {@code card} rename) plus, for markup files, whole-token usages
     * inside {@code class="…"} attributes and selector spans inside
     * {@code <style>} regions. Pure — the applier reads a file, calls
     * this, writes the result; recomputing here from the fresh read is
     * what makes the cross-file apply race-safe per file.
     */
    public static String renameInText(String text, boolean markupFile,
            String oldName, String newName) {
        List<int[]> spans = new ArrayList<>();
        if (markupFile) {
            for (int offset : usagesIn(text, oldName)) {
                spans.add(new int[] {offset, offset + oldName.length()});
            }
            for (HtmlStyleRegions.Region r : HtmlStyleRegions.find(text)) {
                for (int[] s : selectorSpans(
                        text.substring(r.start(), r.end()), oldName)) {
                    spans.add(new int[] {s[0] + r.start(), s[1] + r.start()});
                }
            }
            spans.sort((a, b) -> Integer.compare(a[0], b[0]));
        } else {
            spans.addAll(selectorSpans(text, oldName));
        }
        StringBuilder sb = new StringBuilder(text);
        for (int i = spans.size() - 1; i >= 0; i--) {
            sb.replace(spans.get(i)[0], spans.get(i)[1], newName);
        }
        return sb.toString();
    }

    /** The rename survey: what would change, and whether it may proceed. */
    public record RenameSurvey(List<File> files, int spanCount,
            boolean collision, boolean censusComplete) {
    }

    /**
     * Surveys a project rename OFF the EDT: which files mention the
     * class (as selector or usage), how many spans, whether
     * {@code newName} is already declared anywhere (a rename onto an
     * existing class silently merges rules — the v1.284.0 law refuses
     * it), and whether the file census is COMPLETE — a walk that hit
     * its {@link CssTokens#MAX_FILES} cap could miss files, and a
     * partial rename is data corruption, so the caller must refuse.
     */
    public static RenameSurvey surveyRename(File root, String oldName, String newName) {
        List<File> files = new ArrayList<>();
        int spans = 0;
        boolean collision = false;
        List<File> census = root == null || !root.isDirectory()
                ? List.of() : CssTokens.collectStylesheets(root);
        for (File f : census) {
            try {
                String text = Files.readString(f.toPath());
                boolean markup = isMarkupFile(f.getName());
                int here;
                if (markup) {
                    here = usagesIn(text, oldName).size();
                    for (HtmlStyleRegions.Region r : HtmlStyleRegions.find(text)) {
                        String region = text.substring(r.start(), r.end());
                        here += selectorSpans(region, oldName).size();
                        collision |= !selectorSpans(region, newName).isEmpty();
                    }
                } else {
                    here = selectorSpans(text, oldName).size();
                    collision |= !selectorSpans(text, newName).isEmpty();
                }
                if (here > 0) {
                    files.add(f);
                    spans += here;
                }
            } catch (IOException | OutOfMemoryError unreadable) {
                // skip the file, keep the survey
            }
        }
        return new RenameSurvey(files, spans, collision,
                census.size() < CssTokens.MAX_FILES);
    }
}
