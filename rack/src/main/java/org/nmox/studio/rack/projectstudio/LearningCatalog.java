package org.nmox.studio.rack.projectstudio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The Learning Spaces catalog: the top languages, stacks, frameworks
 * and libraries a developer might want to practice, each a data record
 * describing the sample files, the tutorial, and the REPL (or run
 * command) that {@link LearningSpace} turns into a real, pre-wired
 * project. Loaded once from {@code learn-catalog.json} — new spaces are
 * data, never code.
 */
public final class LearningCatalog {

    /** What kind of thing you're learning — groups the picker. */
    public enum Category {
        LANGUAGE("Languages"),
        STACK("Stacks"),
        FRAMEWORK("Frameworks"),
        LIBRARY("Libraries");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    /** How the space is driven: an interactive REPL, or a run command. */
    public enum DriverKind {
        REPL, RUN
    }

    public record Driver(DriverKind kind, List<String> command, String prompt,
            List<String> snippets) {

        public Driver {
            command = List.copyOf(command);
            snippets = List.copyOf(snippets);
        }
    }

    public record SampleFile(String path, String content) {
    }

    public record Space(String slug, String name, Category category, String family,
            String blurb, Driver driver, Map<String, String> install,
            List<SampleFile> files, String tutorial) {

        public Space {
            // the catalog is a shared 52-space cache handed to every caller;
            // no one may mutate it. LinkedHashMap copy keeps install ordering
            // (Map.copyOf would scramble the mac/linux/windows display order).
            install = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(install));
            files = List.copyOf(files);
        }

        /** The file a fresh space should open first: the tutorial. */
        public String openFile() {
            return "TUTORIAL.md";
        }
    }

    private static volatile List<Space> cache;

    private LearningCatalog() {
    }

    /** Every space, in catalog order. Parsed once. Immutable — this is a
     * shared cache returned from an exported package; a caller's add() or
     * sort() must throw rather than corrupt the catalog for everyone. */
    public static List<Space> all() {
        List<Space> local = cache;
        if (local == null) {
            local = List.copyOf(load());
            cache = local;
        }
        return local;
    }

    public static List<Space> byCategory(Category category) {
        List<Space> out = new ArrayList<>();
        for (Space s : all()) {
            if (s.category() == category) {
                out.add(s);
            }
        }
        return out;
    }

    public static Space find(String slug) {
        for (Space s : all()) {
            if (s.slug().equals(slug)) {
                return s;
            }
        }
        return null;
    }

    private static List<Space> load() {
        try (InputStream in = LearningCatalog.class.getResourceAsStream("learn-catalog.json")) {
            if (in == null) {
                return List.of();
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return parse(new JSONObject(json));
        } catch (IOException | RuntimeException ex) {
            // a broken catalog must not take down the IDE; the picker just empties
            return List.of();
        }
    }

    /** Parses the catalog JSON — package-visible so tests can feed fixtures. */
    static List<Space> parse(JSONObject root) {
        List<Space> spaces = new ArrayList<>();
        JSONArray arr = root.optJSONArray("spaces");
        if (arr == null) {
            return spaces;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            JSONObject d = o.getJSONObject("driver");
            DriverKind kind = "run".equalsIgnoreCase(d.optString("kind", "repl"))
                    ? DriverKind.RUN : DriverKind.REPL;
            Driver driver = new Driver(kind,
                    strings(d.optJSONArray("command")),
                    d.optString("prompt", ""),
                    strings(d.optJSONArray("snippets")));
            Map<String, String> install = new LinkedHashMap<>();
            JSONObject inst = o.optJSONObject("install");
            if (inst != null) {
                for (String key : inst.keySet()) {
                    install.put(key, inst.getString(key));
                }
            }
            List<SampleFile> files = new ArrayList<>();
            JSONArray fa = o.optJSONArray("files");
            if (fa != null) {
                for (int j = 0; j < fa.length(); j++) {
                    JSONObject f = fa.getJSONObject(j);
                    files.add(new SampleFile(f.getString("path"), f.getString("content")));
                }
            }
            spaces.add(new Space(
                    o.getString("slug"), o.getString("name"),
                    Category.valueOf(o.getString("category").toUpperCase(java.util.Locale.ROOT)),
                    o.optString("family", ""), o.optString("blurb", ""),
                    driver, install, files, o.optString("tutorial", "")));
        }
        return spaces;
    }

    private static List<String> strings(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                out.add(arr.getString(i));
            }
        }
        return out;
    }
}
