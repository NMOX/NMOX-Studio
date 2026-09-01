package org.nmox.studio.editor.importmap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/**
 * The pure core of import-map intelligence (futures-2031 F3: the
 * CDN-less ESM app is normal by 2031, and an IDE that cannot follow a
 * bare specifier through the page's map cannot answer "where does this
 * import go"). Parses the page's {@code <script type="importmap">}
 * block and resolves specifiers by the spec's own order: an exact
 * match wins outright, otherwise the LONGEST trailing-slash prefix
 * key applies. Only BARE specifiers are in scope — relative and URL
 * imports already resolve without the map, so offering to "resolve"
 * them would be noise dressed as intelligence.
 */
public final class ImportMaps {

    private ImportMaps() {
    }

    static final int MAX_PAGE_BYTES = 512 * 1024;

    /** The parsed map: specifier keys → targets, plus where each KEY
     *  sits in the page (for jump-to-line), and the page itself. */
    public record PageMap(Map<String, String> imports,
            Map<String, Integer> keyOffsets, File page) {
    }

    private static final Pattern MAP_SCRIPT = Pattern.compile(
            "<script[^>]*type\\s*=\\s*[\"']importmap[\"'][^>]*>(.*?)</script>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /**
     * Parses the FIRST import-map block in the page text (the spec
     * honors only the first; later blocks are ignored the same way the
     * browser ignores them). Malformed JSON, no block, or no imports
     * member all return null — the callers phrase honest misses.
     */
    public static PageMap parse(String html, File page) {
        if (html == null) {
            return null;
        }
        Matcher m = MAP_SCRIPT.matcher(html);
        if (!m.find()) {
            return null;
        }
        String json = m.group(1);
        int blockStart = m.start(1);
        JSONObject imports;
        try {
            JSONObject root = new JSONObject(json);
            imports = root.optJSONObject("imports");
        } catch (RuntimeException notJson) {
            return null;
        }
        if (imports == null || imports.isEmpty()) {
            return null;
        }
        Map<String, String> map = new LinkedHashMap<>();
        Map<String, Integer> offsets = new LinkedHashMap<>();
        for (String key : imports.keySet()) {
            Object v = imports.opt(key);
            if (!(v instanceof String target) || key.isBlank()) {
                continue; // a malformed entry loses itself, not the map
            }
            map.put(key, target);
            // the key's offset in the PAGE: find its quoted spelling
            // inside the block (first occurrence — duplicate keys are
            // already collapsed by JSON parse, first-in-page wins)
            int in = json.indexOf('"' + key + '"');
            offsets.put(key, in < 0 ? blockStart : blockStart + in);
        }
        return map.isEmpty() ? null : new PageMap(map, offsets, page);
    }

    /**
     * The spec's resolution order: exact match wins outright; otherwise
     * the LONGEST key ending in '/' that prefixes the specifier. Null
     * when nothing maps — the honest miss.
     */
    public static String resolveKey(String specifier, Map<String, String> imports) {
        if (specifier == null || imports == null) {
            return null;
        }
        if (imports.containsKey(specifier)) {
            return specifier;
        }
        String best = null;
        for (String key : imports.keySet()) {
            if (key.endsWith("/") && specifier.startsWith(key)
                    && (best == null || key.length() > best.length())) {
                best = key;
            }
        }
        return best;
    }

    /** True for a specifier the map is FOR: not relative, not absolute,
     *  not a URL — those resolve without any map. */
    public static boolean isBare(String specifier) {
        return specifier != null && !specifier.isBlank()
                && !specifier.startsWith("./") && !specifier.startsWith("../")
                && !specifier.startsWith("/") && !specifier.contains("://")
                && !specifier.startsWith("data:");
    }

    /**
     * The quoted specifier span at {@code offset} inside an import
     * gesture — {@code import … from '…'}, {@code import '…'}, or
     * {@code import('…')} — bare specifiers only. Returns
     * {start, end} of the text INSIDE the quotes, or null.
     */
    public static int[] specifierSpanAt(String text, int offset) {
        Matcher m = IMPORT_SOURCE.matcher(text);
        while (m.find()) {
            int start = m.start(2);
            int end = m.end(2);
            if (offset >= start && offset <= end) {
                return isBare(m.group(2)) ? new int[]{start, end} : null;
            }
            if (start > offset) {
                break;
            }
        }
        return null;
    }

    private static final Pattern IMPORT_SOURCE = Pattern.compile(
            "(?:\\bfrom\\s*|\\bimport\\s*\\(\\s*|\\bimport\\s+)(['\"])([^'\"\\n]+)\\1");

    /**
     * The typed specifier prefix when the caret sits inside an OPEN
     * import quote — {@code from '<prefix}, {@code import '<prefix},
     * {@code import('<prefix} — null anywhere else. Relative prefixes
     * return null too: the map has nothing to offer a ./ path.
     */
    public static String specifierPrefixAt(String beforeCaret) {
        Matcher m = OPEN_IMPORT.matcher(beforeCaret);
        String prefix = null;
        while (m.find()) {
            if (m.end() == beforeCaret.length()) {
                prefix = m.group(2);
            }
        }
        return prefix != null && isBare(prefix + "x") ? prefix : null;
    }

    private static final Pattern OPEN_IMPORT = Pattern.compile(
            "(?:\\bfrom\\s*|\\bimport\\s*\\(\\s*|\\bimport\\s+)(['\"])([^'\"\\n]*)$");

    /**
     * The page most likely to carry the map: the entry-page convention
     * (root, then public/, then src/ index.html — the same order the
     * kits write; a shared constant with the ui module would cross the
     * dependency graph for three literals). First page that EXISTS and
     * parses wins; null is the honest "this project has no import map".
     */
    public static PageMap findProjectMap(File projectDir) {
        if (projectDir == null) {
            return null;
        }
        for (String candidate : new String[]{"index.html",
            "public/index.html", "src/index.html"}) {
            File page = new File(projectDir, candidate);
            if (!page.isFile() || page.length() > MAX_PAGE_BYTES) {
                continue;
            }
            try {
                PageMap map = parse(Files.readString(page.toPath()), page);
                if (map != null) {
                    return map;
                }
            } catch (IOException | RuntimeException unreadable) {
                // an unreadable candidate contributes nothing, quietly
            }
        }
        return null;
    }
}
