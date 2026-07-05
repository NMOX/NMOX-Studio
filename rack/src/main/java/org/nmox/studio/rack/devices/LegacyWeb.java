package org.nmox.studio.rack.devices;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

/**
 * Detects the classic web libraries a project actually ships — jQuery,
 * MooTools, Prototype, Backbone, Underscore, Knockout — by merging three
 * sources: package.json dependencies, bower.json dependencies, and the
 * {@code <script src>} tags of root-level HTML files (with version
 * capture from filenames like {@code jquery-1.12.4.min.js}).
 *
 * <p>Pure and side-effect free. All scanning is hand-rolled character
 * walking — no regular expressions, so no backtracking risk on
 * adversarial input — and each HTML file is read to a 64 KB cap, which
 * comfortably covers any head section that loads scripts.</p>
 */
public final class LegacyWeb {

    /** The classic libraries this detector knows, in report order. */
    private static final String[] LIBRARY_IDS = {
        "jquery", "mootools", "prototype", "backbone", "underscore", "knockout"
    };

    /** Only the leading slice of an HTML file is scanned for script tags. */
    private static final int HTML_SCAN_CAP = 64 * 1024;

    /**
     * One detected library. {@code version} is the cleaned display
     * version ("1.12.4") or the empty string when the source names the
     * library without a number ({@code jquery.min.js}).
     */
    public record Library(String id, String version, boolean eol) {

        /** Chip-ready text: "jquery 1.12.4 — EOL", "backbone 1.6.0", "jquery". */
        public String label() {
            String base = version.isEmpty() ? id : id + " " + version;
            return eol ? base + " — EOL" : base;
        }

        /** The honest sentence behind the EOL flag, or null when current. */
        public String eolMessage() {
            return eol ? "jQuery " + version.charAt(0)
                    + ".x reached end-of-life — 3.x is the supported line" : null;
        }
    }

    private LegacyWeb() {
    }

    /**
     * Scans the project for classic web libraries. One entry per library,
     * deduplicated across sources; a source that knows the version beats
     * one that only knows the name.
     */
    public static List<Library> scan(File projectDir) {
        Map<String, String> found = new LinkedHashMap<>(); // id -> version ("" = unknown)
        mergeManifest(found, new File(projectDir, "package.json"));
        mergeManifest(found, new File(projectDir, "bower.json"));
        File[] pages = projectDir.listFiles(f -> {
            String name = f.getName().toLowerCase(Locale.ROOT);
            return f.isFile() && (name.endsWith(".html") || name.endsWith(".htm"));
        });
        if (pages != null) {
            java.util.Arrays.sort(pages, java.util.Comparator.comparing(File::getName));
            for (File page : pages) {
                for (String src : scriptSrcs(readCapped(page))) {
                    mergeSrc(found, src);
                }
            }
        }
        List<Library> libraries = new ArrayList<>();
        for (String id : LIBRARY_IDS) { // stable report order, whatever source order was
            String version = found.get(id);
            if (version != null) {
                libraries.add(new Library(id, version, isEol(id, version)));
            }
        }
        return libraries;
    }

    /** jQuery major 1 or 2 is end-of-life; everything else is not flagged (v1). */
    private static boolean isEol(String id, String version) {
        if (!"jquery".equals(id) || version.isEmpty()) {
            return false;
        }
        char major = version.charAt(0);
        return major == '1' || major == '2';
    }

    // ---- manifest source (package.json / bower.json) ----

    private static void mergeManifest(Map<String, String> found, File manifest) {
        if (!manifest.isFile()) {
            return;
        }
        JSONObject json;
        try {
            json = new JSONObject(Files.readString(manifest.toPath(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException unreadable) {
            return; // detection is decoration; a broken manifest is not our fight
        }
        for (String section : new String[]{"dependencies", "devDependencies"}) {
            JSONObject deps = json.optJSONObject(section);
            if (deps == null) {
                continue;
            }
            for (String id : LIBRARY_IDS) {
                if (deps.has(id)) {
                    merge(found, id, cleanRange(deps.optString(id, "")));
                }
            }
        }
    }

    /** "~1.12.4" / "^3.7.1" / ">=2.0.0" → the first plain number in the range. */
    private static String cleanRange(String range) {
        int i = 0;
        while (i < range.length() && !isDigit(range.charAt(i))) {
            i++;
        }
        int start = i;
        while (i < range.length() && (isDigit(range.charAt(i)) || range.charAt(i) == '.')) {
            i++;
        }
        return trimDots(range.substring(start, i));
    }

    // ---- script-tag source ----

    /** Registers whatever library a script src names, version from the filename. */
    private static void mergeSrc(Map<String, String> found, String src) {
        String path = src.toLowerCase(Locale.ROOT);
        String file = path.substring(path.lastIndexOf('/') + 1);
        for (String id : LIBRARY_IDS) {
            if (!path.contains(id)) {
                continue;
            }
            // version rides the filename: jquery-1.12.4.min.js → 1.12.4
            String version = "";
            int at = file.indexOf(id + "-");
            if (at >= 0) {
                int i = at + id.length() + 1;
                int start = i;
                while (i < file.length() && (isDigit(file.charAt(i)) || file.charAt(i) == '.')) {
                    i++;
                }
                if (i > start && isDigit(file.charAt(start))) {
                    version = trimDots(file.substring(start, i));
                }
            }
            merge(found, id, version);
        }
    }

    /** Every src attribute of every script tag, hand-scanned. */
    static List<String> scriptSrcs(String html) {
        List<String> srcs = new ArrayList<>();
        String lower = html.toLowerCase(Locale.ROOT);
        int i = 0;
        while ((i = lower.indexOf("<script", i)) >= 0) {
            int end = lower.indexOf('>', i);
            if (end < 0) {
                break;
            }
            String src = srcAttribute(html.substring(i, end));
            if (src != null && !src.isEmpty()) {
                srcs.add(src);
            }
            i = end + 1;
        }
        return srcs;
    }

    /** The quoted value of the tag's src attribute (not data-src), or null. */
    private static String srcAttribute(String tag) {
        String lower = tag.toLowerCase(Locale.ROOT);
        int i = 0;
        while ((i = lower.indexOf("src", i)) >= 0) {
            // a real attribute starts after whitespace; data-src does not count
            if (i == 0 || !Character.isWhitespace(lower.charAt(i - 1))) {
                i += 3;
                continue;
            }
            int j = i + 3;
            while (j < tag.length() && Character.isWhitespace(tag.charAt(j))) {
                j++;
            }
            if (j >= tag.length() || tag.charAt(j) != '=') {
                i += 3;
                continue;
            }
            j++;
            while (j < tag.length() && Character.isWhitespace(tag.charAt(j))) {
                j++;
            }
            if (j >= tag.length() || (tag.charAt(j) != '"' && tag.charAt(j) != '\'')) {
                i += 3;
                continue;
            }
            char quote = tag.charAt(j);
            int close = tag.indexOf(quote, j + 1);
            return close < 0 ? null : tag.substring(j + 1, close);
        }
        return null;
    }

    // ---- small shared helpers ----

    /** A version-bearing entry beats a name-only one; otherwise first wins. */
    private static void merge(Map<String, String> found, String id, String version) {
        String existing = found.get(id);
        if (existing == null || (existing.isEmpty() && !version.isEmpty())) {
            found.put(id, version);
        }
    }

    private static String readCapped(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            return new String(in.readNBytes(HTML_SCAN_CAP), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return "";
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static String trimDots(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '.') {
            end--;
        }
        return s.substring(0, end);
    }
}
