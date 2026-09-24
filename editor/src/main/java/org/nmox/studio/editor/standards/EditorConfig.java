package org.nmox.studio.editor.standards;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    /** A section; a refused glob (see {@link EditorConfigGlob#refusal()}) matches no file. */
    record Section(EditorConfigGlob glob, Map<String, String> properties) {
    }

    private static final Logger LOG = Logger.getLogger(EditorConfig.class.getName());

    /** The merged properties that apply to one file, lowercased keys. */
    public static Map<String, String> propertiesFor(File file) {
        List<ConfigFile> chain = new ArrayList<>();
        File dir = file.getParentFile();
        while (dir != null) {
            File cfg = new File(dir, ".editorconfig");
            if (cfg.isFile()) {
                try {
                    ConfigFile parsed = parseCached(cfg);
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
                if (s.glob().matches(rel)) {
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

    /** Parsed files by absolute path; an entry is good while mtime and size hold. */
    private static final int PARSE_CACHE_CAP = 256;
    private static final Map<String, CachedParse> PARSE_CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedParse> eldest) {
            return size() > PARSE_CACHE_CAP;
        }
    };

    private record CachedParse(long modified, long length, ConfigFile parsed) {
    }

    /**
     * {@link #parse} behind a path + mtime + size cache: the indentation
     * provider asks for a file's properties far more often than the
     * {@code .editorconfig} files change, and every read of a file the
     * clone brought is bounded anyway. An edit changes the mtime or the
     * size, so the next ask re-reads.
     */
    static ConfigFile parseCached(File cfg) throws IOException {
        String key = cfg.getAbsolutePath();
        long modified = cfg.lastModified();
        long length = cfg.length();
        synchronized (PARSE_CACHE) {
            CachedParse hit = PARSE_CACHE.get(key);
            if (hit != null && hit.modified() == modified && hit.length() == length) {
                return hit.parsed();
            }
        }
        ConfigFile parsed = parse(cfg);
        synchronized (PARSE_CACHE) {
            PARSE_CACHE.put(key, new CachedParse(modified, length, parsed));
        }
        return parsed;
    }

    private static String clip(String header) {
        return header.codePointCount(0, header.length()) <= 80 ? header
                : header.substring(0, header.offsetByCodePoints(0, 80)) + "…";
    }

    static ConfigFile parse(File cfg) throws IOException {
        boolean root = false;
        List<Section> sections = new ArrayList<>();
        Map<String, String> current = null;
        // read on EVERY save, over a file the clone brought
        for (String raw : org.nmox.studio.core.util.BoundedReads.readLines(cfg.toPath())) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                current = new LinkedHashMap<>();
                String header = line.substring(1, line.length() - 1);
                EditorConfigGlob compiled = glob(header);
                if (compiled.refusal() != null) {
                    // parse runs once per file + mtime + size (parseCached), so a
                    // hostile header speaks once, never once per resolve
                    LOG.log(Level.INFO, "{0}: section [{1}] is refused and applies to no file: {2}",
                            new Object[]{cfg, clip(header), compiled.refusal()});
                }
                sections.add(new Section(compiled, current));
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
     * A section header's glob, compiled for {@link EditorConfigGlob}'s
     * polynomial-time match. It never throws: a glob past the matcher's
     * bounds comes back refused and matches no file. (Until 3.1 this was a
     * translation to {@code java.util.regex}, which backtracks
     * exponentially on a hostile header such as {@code [**a**a…**b]} —
     * and a cloned repository writes both the header and the file name.)
     */
    static EditorConfigGlob glob(String glob) {
        return EditorConfigGlob.compile(glob);
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
