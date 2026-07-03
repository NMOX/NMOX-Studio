package org.nmox.studio.editor.standards;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The EditorConfig standard (editorconfig.org), implemented for real:
 * finds every {@code .editorconfig} from the file's directory upward
 * (stopping above a {@code root = true} file), matches section globs
 * against the file, and merges properties with the standard precedence
 * - closer files win, later sections win. The rack highlights these
 * files; this class makes the IDE actually obey them.
 */
public final class EditorConfig {

    private EditorConfig() {
    }

    /** One parsed file: its directory and its sections in order. */
    record ConfigFile(File dir, boolean root, List<Section> sections) {
    }

    record Section(Pattern pattern, Map<String, String> properties) {
    }

    /** The merged properties that apply to one file, lowercased keys. */
    public static Map<String, String> propertiesFor(File file) {
        List<ConfigFile> chain = new ArrayList<>();
        File dir = file.getParentFile();
        while (dir != null) {
            File cfg = new File(dir, ".editorconfig");
            if (cfg.isFile()) {
                try {
                    ConfigFile parsed = parse(cfg);
                    chain.add(parsed);
                    if (parsed.root()) {
                        break;
                    }
                } catch (IOException ignored) {
                    // unreadable config: skip it, keep walking up
                }
            }
            dir = dir.getParentFile();
        }
        // outermost first so closer files override
        Map<String, String> merged = new LinkedHashMap<>();
        for (int i = chain.size() - 1; i >= 0; i--) {
            ConfigFile cfg = chain.get(i);
            String rel = relativePath(cfg.dir(), file);
            if (rel == null) {
                continue;
            }
            for (Section s : cfg.sections()) {
                if (s.pattern().matcher(rel).matches()) {
                    merged.putAll(s.properties());
                }
            }
        }
        return merged;
    }

    private static String relativePath(File dir, File file) {
        String base = dir.getAbsolutePath();
        String path = file.getAbsolutePath();
        if (!path.startsWith(base)) {
            return null;
        }
        String rel = path.substring(base.length());
        rel = rel.replace(File.separatorChar, '/');
        return rel.startsWith("/") ? rel.substring(1) : rel;
    }

    static ConfigFile parse(File cfg) throws IOException {
        boolean root = false;
        List<Section> sections = new ArrayList<>();
        Map<String, String> current = null;
        for (String raw : Files.readAllLines(cfg.toPath())) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                current = new LinkedHashMap<>();
                sections.add(new Section(
                        globToRegex(line.substring(1, line.length() - 1)), current));
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(eq + 1).strip();
            if (current == null) {
                if (key.equals("root")) {
                    root = "true".equalsIgnoreCase(value);
                }
            } else {
                current.put(key, value.toLowerCase(Locale.ROOT));
            }
        }
        return new ConfigFile(cfg.getParentFile(), root, sections);
    }

    /**
     * EditorConfig glob → regex: {@code *} (not across /), {@code **},
     * {@code ?}, {@code [seq]}, {@code {a,b}} alternation and
     * {@code {n..m}} numeric ranges. A pattern without a slash matches
     * the file name in any directory, per the spec.
     */
    static Pattern globToRegex(String glob) {
        String g = glob;
        boolean anchored = g.startsWith("/");
        if (anchored) {
            g = g.substring(1);
        }
        StringBuilder rx = new StringBuilder();
        if (!anchored && !g.contains("/")) {
            rx.append("(?:.*/)?");
        }
        int i = 0;
        while (i < g.length()) {
            char c = g.charAt(i);
            switch (c) {
                case '*' -> {
                    if (i + 1 < g.length() && g.charAt(i + 1) == '*') {
                        rx.append(".*");
                        i++;
                    } else {
                        rx.append("[^/]*");
                    }
                }
                case '?' -> rx.append("[^/]");
                case '[' -> {
                    int close = g.indexOf(']', i + 1);
                    if (close < 0) {
                        rx.append("\\[");
                    } else {
                        String seq = g.substring(i + 1, close);
                        rx.append('[').append(seq.startsWith("!")
                                ? "^" + escapeClass(seq.substring(1)) : escapeClass(seq)).append(']');
                        i = close;
                    }
                }
                case '{' -> {
                    int close = g.indexOf('}', i + 1);
                    if (close < 0) {
                        rx.append("\\{");
                    } else {
                        rx.append(braceGroup(g.substring(i + 1, close)));
                        i = close;
                    }
                }
                default -> rx.append(Pattern.quote(String.valueOf(c)));
            }
            i++;
        }
        return Pattern.compile(rx.toString());
    }

    private static String braceGroup(String inner) {
        // {3..7} numeric range
        int dots = inner.indexOf("..");
        if (dots > 0 && inner.chars().allMatch(ch -> Character.isDigit(ch) || ch == '.')) {
            try {
                int from = Integer.parseInt(inner.substring(0, dots));
                int to = Integer.parseInt(inner.substring(dots + 2));
                if (to >= from && to - from <= 500) {
                    StringBuilder alt = new StringBuilder("(?:");
                    for (int n = from; n <= to; n++) {
                        alt.append(n == from ? "" : "|").append(n);
                    }
                    return alt.append(')').toString();
                }
            } catch (NumberFormatException ignored) {
                // fall through to alternation
            }
        }
        StringBuilder alt = new StringBuilder("(?:");
        String[] parts = inner.split(",", -1);
        for (int p = 0; p < parts.length; p++) {
            alt.append(p == 0 ? "" : "|").append(Pattern.quote(parts[p]));
        }
        return alt.append(')').toString();
    }

    private static String escapeClass(String seq) {
        return seq.replace("\\", "\\\\").replace("^", "\\^");
    }

    /**
     * Applies the save-safe text standards from a property set:
     * {@code trim_trailing_whitespace} and {@code insert_final_newline}
     * (both directions - {@code false} means the file must NOT end
     * with a newline, per the spec).
     */
    public static String applyOnSave(String text, Map<String, String> props) {
        String out = text;
        if ("true".equals(props.get("trim_trailing_whitespace"))) {
            out = out.replaceAll("[ \\t]+(\\r?\\n)", "$1").replaceAll("[ \\t]+\\z", "");
        }
        String finalNewline = props.get("insert_final_newline");
        if ("true".equals(finalNewline)) {
            if (!out.isEmpty() && !out.endsWith("\n")) {
                out = out + "\n";
            }
        } else if ("false".equals(finalNewline)) {
            out = out.replaceAll("\\n+\\z", "");
        }
        return out;
    }
}
