package org.nmox.studio.editor.classic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject;

/**
 * Detects which classic libraries a file's project actually uses, so
 * completion only offers {@code $.ajax} where jQuery is real. Pure
 * filesystem + string work — no rack, no project API, no network.
 *
 * <p>From the edited file the detector walks UP (bounded) to the nearest
 * directory holding {@code package.json}, {@code bower.json} or any root
 * {@code *.html} — stopping at a {@code .git} repo root either way — then
 * reads:
 * <ul>
 *   <li>{@code dependencies} + {@code devDependencies} from package.json,</li>
 *   <li>{@code dependencies} from bower.json,</li>
 *   <li>{@code <script src>} values from up to {@value #MAX_HTML_FILES}
 *       root html files (first {@value #MAX_HTML_BYTES} bytes each),
 *       scanned with a hand-rolled linear pass — no regex.</li>
 * </ul>
 * Lodash counts as underscore: its API is underscore-compatible and the
 * underscore catalog is the one that applies.
 *
 * <p>Results are cached per base directory, keyed on the manifests'
 * lastModified stamps, so completion never re-reads disk per keystroke.
 * Thread-safe: the cache is a {@link ConcurrentHashMap} and entries are
 * immutable.
 */
public final class ClassicLibraryDetector {

    private static final Logger LOG = Logger.getLogger(ClassicLibraryDetector.class.getName());

    /** How many directory levels above the file we are willing to walk. */
    static final int MAX_WALK_UP = 12;
    /** How many root html files we scan for script tags. */
    static final int MAX_HTML_FILES = 5;
    /** How much of each html file we read. */
    static final int MAX_HTML_BYTES = 64 * 1024;

    private record Cached(String fingerprint, Set<String> libraries) {
    }

    private final ConcurrentHashMap<Path, Cached> cache = new ConcurrentHashMap<>();

    /**
     * The classic-library ids used around {@code file} — a subset of
     * {jquery, mootools, prototype, backbone, underscore, knockout}.
     * Never throws; unreadable manifests just contribute nothing.
     */
    public Set<String> detect(Path file) {
        if (file == null) {
            return Set.of();
        }
        Path base = findBaseDir(file.toAbsolutePath().normalize().getParent());
        if (base == null) {
            return Set.of();
        }
        List<Path> htmlFiles = rootHtmlFiles(base);
        String fingerprint = fingerprint(base, htmlFiles);
        Cached hit = cache.get(base);
        if (hit != null && hit.fingerprint().equals(fingerprint)) {
            return hit.libraries();
        }
        Set<String> libs = scan(base, htmlFiles);
        cache.put(base, new Cached(fingerprint, libs));
        return libs;
    }

    /**
     * Walks up from {@code dir}: the first directory containing
     * package.json, bower.json or a root html file wins; a directory
     * containing {@code .git} without any of those stops the walk (a repo
     * root without web manifests is not a classic project).
     */
    static Path findBaseDir(Path dir) {
        Path current = dir;
        for (int i = 0; current != null && i <= MAX_WALK_UP; i++) {
            if (Files.isRegularFile(current.resolve("package.json"))
                    || Files.isRegularFile(current.resolve("bower.json"))
                    || !rootHtmlFiles(current).isEmpty()) {
                return current;
            }
            if (Files.exists(current.resolve(".git"))) {
                return null;
            }
            current = current.getParent();
        }
        return null;
    }

    /** Up to {@link #MAX_HTML_FILES} html files directly in {@code dir}, sorted for determinism. */
    static List<Path> rootHtmlFiles(Path dir) {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if ((name.endsWith(".html") || name.endsWith(".htm")) && Files.isRegularFile(p)) {
                    out.add(p);
                }
            }
        } catch (IOException ex) {
            return List.of();
        }
        Collections.sort(out);
        return out.size() > MAX_HTML_FILES ? out.subList(0, MAX_HTML_FILES) : out;
    }

    private static String fingerprint(Path base, List<Path> htmlFiles) {
        StringBuilder sb = new StringBuilder();
        stamp(sb, base.resolve("package.json"));
        stamp(sb, base.resolve("bower.json"));
        for (Path p : htmlFiles) {
            stamp(sb, p);
        }
        return sb.toString();
    }

    private static void stamp(StringBuilder sb, Path p) {
        long modified;
        long size;
        try {
            modified = Files.getLastModifiedTime(p).toMillis();
            size = Files.size(p);
        } catch (IOException ex) {
            modified = -1;
            size = -1;
        }
        sb.append(p.getFileName()).append(':').append(modified).append(':').append(size).append(';');
    }

    private static Set<String> scan(Path base, List<Path> htmlFiles) {
        Set<String> libs = new TreeSet<>();
        jsonDeps(base.resolve("package.json"), true, libs);
        jsonDeps(base.resolve("bower.json"), false, libs);
        for (Path html : htmlFiles) {
            for (String src : scriptSrcs(readHead(html))) {
                addFromName(fileNameOf(src), libs);
            }
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(libs));
    }

    /** Dependency keys from a manifest; devDependencies too for package.json. */
    private static void jsonDeps(Path manifest, boolean includeDev, Set<String> libs) {
        if (!Files.isRegularFile(manifest)) {
            return;
        }
        try {
            JSONObject root = new JSONObject(Files.readString(manifest, StandardCharsets.UTF_8));
            collectDeps(root.optJSONObject("dependencies"), libs);
            if (includeDev) {
                collectDeps(root.optJSONObject("devDependencies"), libs);
            }
        } catch (IOException | org.json.JSONException ex) {
            LOG.log(Level.FINE, "unreadable manifest skipped: " + manifest, ex);
        }
    }

    private static void collectDeps(JSONObject deps, Set<String> libs) {
        if (deps == null) {
            return;
        }
        for (String key : deps.keySet()) {
            addFromDependencyKey(key.toLowerCase(Locale.ROOT), libs);
        }
    }

    /** Exact-ish dependency-name mapping: keys are package names, so be strict. */
    static void addFromDependencyKey(String key, Set<String> libs) {
        switch (key) {
            case "jquery" -> libs.add("jquery");
            case "mootools", "mootools-core", "mootools-more" -> libs.add("mootools");
            case "prototype", "prototypejs" -> libs.add("prototype");
            case "underscore", "lodash" -> libs.add("underscore");
            case "knockout" -> libs.add("knockout");
            default -> {
                if (key.equals("backbone") || key.startsWith("backbone.")) {
                    libs.add("backbone");
                }
            }
        }
    }

    /** Loose filename mapping: vendored files carry versions (jquery-1.12.4.min.js). */
    static void addFromName(String name, Set<String> libs) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("jquery")) {
            libs.add("jquery");
        }
        if (n.contains("mootools")) {
            libs.add("mootools");
        }
        if (n.contains("prototype")) {
            libs.add("prototype");
        }
        if (n.contains("backbone")) {
            libs.add("backbone");
        }
        if (n.contains("underscore") || n.contains("lodash")) {
            libs.add("underscore");
        }
        if (n.contains("knockout")) {
            libs.add("knockout");
        }
    }

    private static String fileNameOf(String src) {
        int slash = Math.max(src.lastIndexOf('/'), src.lastIndexOf('\\'));
        String name = slash >= 0 ? src.substring(slash + 1) : src;
        int query = name.indexOf('?');
        return query >= 0 ? name.substring(0, query) : name;
    }

    private static String readHead(Path html) {
        try (InputStream in = Files.newInputStream(html)) {
            return new String(in.readNBytes(MAX_HTML_BYTES), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    /**
     * Every {@code src} value of a {@code <script>} tag, found with a
     * single hand-rolled forward pass — indexOf and charAt only, so a
     * pathological page cannot make matching super-linear.
     */
    static List<String> scriptSrcs(String html) {
        List<String> out = new ArrayList<>();
        String lower = html.toLowerCase(Locale.ROOT);
        int i = 0;
        while (true) {
            int tag = lower.indexOf("<script", i);
            if (tag < 0) {
                return out;
            }
            int end = lower.indexOf('>', tag);
            if (end < 0) {
                return out;
            }
            int src = lower.indexOf("src", tag);
            if (src >= 0 && src < end) {
                int j = src + 3;
                while (j < end && Character.isWhitespace(html.charAt(j))) {
                    j++;
                }
                if (j < end && html.charAt(j) == '=') {
                    j++;
                    while (j < end && Character.isWhitespace(html.charAt(j))) {
                        j++;
                    }
                    if (j < end && (html.charAt(j) == '"' || html.charAt(j) == '\'')) {
                        char quote = html.charAt(j);
                        int close = html.indexOf(quote, j + 1);
                        if (close > j && close < end) {
                            out.add(html.substring(j + 1, close));
                        }
                    }
                }
            }
            i = end + 1;
        }
    }
}
