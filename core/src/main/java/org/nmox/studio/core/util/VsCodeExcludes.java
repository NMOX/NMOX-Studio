package org.nmox.studio.core.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * What a repository's {@code .vscode/settings.json} keeps out of sight:
 * its {@code files.exclude} (hidden from the file trees and, as in
 * VS Code, from a search) and its {@code search.exclude} (skipped by a
 * search only). A value, immutable; the patterns are {@link VsCodeGlob}s
 * and a path is asked about relative to the folder this was
 * {@linkplain #under bound to}.
 *
 * <p><b>How VS Code reads the two settings</b>, and so how this does:
 * <ul>
 * <li>each is an object of glob → value. {@code true} excludes;
 *     {@code false} says nothing here (in VS Code it switches off a
 *     pattern inherited from the user's own settings, and this product
 *     reads only what the repository states);</li>
 * <li>a search uses {@code files.exclude} with {@code search.exclude}
 *     laid over it KEY BY KEY: {@code "**}{@code /dist": false} in
 *     {@code search.exclude} brings a {@code dist} that
 *     {@code files.exclude} hides back into the search;</li>
 * <li>a pattern is matched against the path relative to the folder the
 *     settings belong to, and a folder that matches takes everything
 *     beneath it;</li>
 * <li>neither setting can be written per language: one inside a
 *     {@code "[typescript]"} block is not read.</li>
 * </ul>
 *
 * <p><b>What is not honoured is said, never approximated</b>
 * ({@link #notHonoured()}): a conditional exclude
 * ({@code {"when": "$(basename).ts"}}), a value that is neither
 * {@code true} nor {@code false}, a pattern {@link VsCodeGlob} refuses, a
 * setting that is not an object, and whatever lies past the bounds — a
 * settings file is a stranger's text, so a setting holds at most
 * {@link #MAX_PATTERNS} patterns whose matchers total at most
 * {@link #MAX_STATES} states. Each of those leaves a file SHOWN or
 * SEARCHED that VS Code would not: the side that loses nothing.
 */
public final class VsCodeExcludes {

    /** Patterns read per setting; a real file has a dozen. */
    public static final int MAX_PATTERNS = 128;

    /** States all of one setting's patterns may total: the cost of asking about one path. */
    public static final int MAX_STATES = 4_096;

    /** A project that excludes nothing. */
    public static final VsCodeExcludes NONE =
            new VsCodeExcludes(List.of(), List.of(), List.of(), "", false);

    private final List<VsCodeGlob> view;
    private final List<VsCodeGlob> search;
    private final List<String> notHonoured;
    /** Where the folder asked about sits beneath the settings' folder: "" or "packages/web/". */
    private final String prefix;
    private final boolean ignoreCase;

    private VsCodeExcludes(List<VsCodeGlob> view, List<VsCodeGlob> search, List<String> notHonoured,
            String prefix, boolean ignoreCase) {
        this.view = view;
        this.search = search;
        this.notHonoured = notHonoured;
        this.prefix = prefix;
        this.ignoreCase = ignoreCase;
    }

    /**
     * Reads the two settings out of a settings file's text (JSON with
     * comments). Pure. Text that is not a JSON object excludes nothing and
     * says so.
     *
     * @param ignoreCase whether patterns ignore case, as VS Code does where the file system does
     */
    public static VsCodeExcludes parse(String settingsText, boolean ignoreCase) {
        JSONObject root;
        try {
            root = new JSONObject(Jsonc.strip(settingsText == null ? "" : settingsText));
        } catch (JSONException ex) {
            return new VsCodeExcludes(List.of(), List.of(),
                    List.of("the file is not a JSON object: " + ex.getMessage()), "", ignoreCase);
        }
        Set<String> said = new LinkedHashSet<>();
        Map<String, String> files = entries(root, "files.exclude", said);
        Map<String, String> searchOnly = entries(root, "search.exclude", said);
        // key by key, as VS Code mixes the two objects: search.exclude wins
        Map<String, String> merged = new LinkedHashMap<>(files);
        Map<String, String> origin = new LinkedHashMap<>();
        files.keySet().forEach(k -> origin.put(k, "files.exclude"));
        for (Map.Entry<String, String> e : searchOnly.entrySet()) {
            merged.put(e.getKey(), e.getValue());
            origin.put(e.getKey(), "search.exclude");
        }
        Map<String, VsCodeGlob> compiled = new LinkedHashMap<>();
        Map<String, String> filesOrigin = new LinkedHashMap<>();
        files.keySet().forEach(k -> filesOrigin.put(k, "files.exclude"));
        List<VsCodeGlob> view = globs(files, filesOrigin, compiled, ignoreCase, said);
        List<VsCodeGlob> search = globs(merged, origin, compiled, ignoreCase, said);
        if (view.isEmpty() && search.isEmpty() && said.isEmpty()) {
            return NONE;
        }
        return new VsCodeExcludes(List.copyOf(view), List.copyOf(search), List.copyOf(said), "", ignoreCase);
    }

    private static final String ON = "on";
    private static final String OFF = "off";

    /** One setting's patterns in a fixed order, each ON, OFF, or the reason it is not honoured. */
    private static Map<String, String> entries(JSONObject root, String setting, Set<String> said) {
        Map<String, String> out = new LinkedHashMap<>();
        Object value = root.opt(setting);
        if (value == null) {
            return out;
        }
        if (!(value instanceof JSONObject object)) {
            said.add(setting + " is not an object of patterns");
            return out;
        }
        for (String pattern : new TreeSet<>(object.keySet())) { // JSONObject keeps no order: sorted is stable
            Object v = object.opt(pattern);
            if (Boolean.TRUE.equals(v)) {
                out.put(pattern, ON);
            } else if (Boolean.FALSE.equals(v)) {
                out.put(pattern, OFF);
            } else if (v instanceof JSONObject when && when.opt("when") instanceof String) {
                out.put(pattern, "is conditional (\"when\")");
            } else {
                out.put(pattern, "is neither true nor false");
            }
        }
        return out;
    }

    /** The patterns that are ON, compiled, inside the bounds; everything else named in {@code said}. */
    private static List<VsCodeGlob> globs(Map<String, String> entries, Map<String, String> origin,
            Map<String, VsCodeGlob> compiled, boolean ignoreCase, Set<String> said) {
        List<VsCodeGlob> out = new ArrayList<>();
        int states = 0;
        for (Map.Entry<String, String> e : entries.entrySet()) {
            String pattern = e.getKey();
            String state = e.getValue();
            String label = origin.get(pattern) + " \"" + pattern + "\" ";
            if (OFF.equals(state)) {
                continue;
            }
            if (!ON.equals(state)) {
                said.add(label + state);
                continue;
            }
            if (out.size() >= MAX_PATTERNS) {
                said.add(label + "is past the first " + MAX_PATTERNS + " patterns");
                continue;
            }
            VsCodeGlob glob = compiled.computeIfAbsent(pattern, p -> VsCodeGlob.compile(p, ignoreCase));
            if (glob.refusal() != null) {
                said.add(label + "is not a pattern this reads: " + glob.refusal());
                continue;
            }
            if (states + glob.states() > MAX_STATES) {
                said.add(label + "is past what one setting's patterns may cost");
                continue;
            }
            states += glob.states();
            out.add(glob);
        }
        return out;
    }

    /**
     * The same exclusions, asked about from a folder beneath the
     * settings' own: {@code relative} is that folder's path from the
     * settings' folder ({@code ""} for the folder itself,
     * {@code "packages/web"} below it).
     */
    public VsCodeExcludes under(String relative) {
        String clean = relative == null ? "" : relative.replace('\\', '/');
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        String p = clean.isEmpty() ? "" : clean + "/";
        return p.equals(prefix) ? this : new VsCodeExcludes(view, search, notHonoured, p, ignoreCase);
    }

    /** Whether nothing is excluded from anything. */
    public boolean isEmpty() {
        return view.isEmpty() && search.isEmpty();
    }

    /**
     * Whether {@code files.exclude} hides {@code relativePath} (from the
     * folder this is bound to, {@code /} between segments) — itself, or
     * through a folder above it inside that folder.
     */
    public boolean hides(String relativePath) {
        return excluded(view, relativePath);
    }

    /** Whether a search skips {@code relativePath}: {@code files.exclude} with {@code search.exclude} over it. */
    public boolean skipsSearch(String relativePath) {
        return excluded(search, relativePath);
    }

    private boolean excluded(List<VsCodeGlob> globs, String relativePath) {
        if (globs.isEmpty() || relativePath == null || relativePath.isEmpty()) {
            return false;
        }
        String rel = relativePath.replace('\\', '/');
        // the path, then each folder above it down to (not including) the
        // folder asked from: aiming a tree at an excluded folder shows it
        int end = rel.length();
        while (end > 0) {
            String path = prefix + rel.substring(0, end);
            for (VsCodeGlob g : globs) {
                if (g.matches(path)) {
                    return true;
                }
            }
            end = rel.lastIndexOf('/', end - 1);
        }
        return false;
    }

    /**
     * What the file states that is NOT applied, one line each, naming the
     * setting and the pattern — for the log.
     */
    public List<String> notHonoured() {
        return notHonoured;
    }

    /** The patterns in force for the trees, as written. */
    public List<String> hiddenPatterns() {
        return view.stream().map(VsCodeGlob::pattern).toList();
    }

    /** The patterns in force for a search, as written. */
    public List<String> searchPatterns() {
        return search.stream().map(VsCodeGlob::pattern).toList();
    }

    /** Equal when the same patterns apply from the same place: what a tree compares to know it must redraw. */
    @Override
    public boolean equals(Object o) {
        return o instanceof VsCodeExcludes other
                && ignoreCase == other.ignoreCase
                && prefix.equals(other.prefix)
                && hiddenPatterns().equals(other.hiddenPatterns())
                && searchPatterns().equals(other.searchPatterns());
    }

    @Override
    public int hashCode() {
        return Objects.hash(ignoreCase, prefix, hiddenPatterns(), searchPatterns());
    }

    @Override
    public String toString() {
        return "VsCodeExcludes[hidden=" + hiddenPatterns() + ", search=" + searchPatterns()
                + (prefix.isEmpty() ? "" : ", under " + prefix) + "]";
    }
}
