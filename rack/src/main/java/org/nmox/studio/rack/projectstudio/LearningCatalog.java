package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The Learning Spaces catalog: the top languages, stacks, frameworks
 * and libraries a developer might want to practice, each a data record
 * describing the sample files, the tutorial, and the REPL (or run
 * command) that {@link LearningSpace} turns into a real, pre-wired
 * project. Loaded once from {@code learn-catalog.json} — new spaces are
 * data, never code.
 *
 * <p>Community spaces ride the same schema: every {@code *.json} file in
 * {@code ~/.nmox/learn-catalog.d/} is merged in at the same lazy point
 * the built-ins load (first {@link #all()} call — the picker, the REPL's
 * ENGINE knob, the Doctor; never at boot). A drop-in space whose slug
 * matches a built-in overrides it in place; new slugs append after the
 * built-ins, files in filename order. Malformed files are skipped with a
 * warning naming the file — a broken drop-in never blocks the picker.
 * See {@code docs/learning-spaces.md} for the schema.
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

    private static final Logger LOG = Logger.getLogger(LearningCatalog.class.getName());

    private static volatile List<Space> cache;

    // the merged (built-ins + drop-ins) view, cached against a fingerprint
    // of the drop-in dir's contents: reopening the picker after editing a
    // drop-in file picks the change up without an IDE restart, while an
    // unchanged dir costs one listing — no re-parse, and no re-warning
    // storm for a malformed file the user has already been told about.
    private static volatile List<Space> mergedCache;
    private static volatile String mergedStamp;

    private LearningCatalog() {
    }

    /** Every space — built-ins in catalog order, then drop-in spaces —
     * lazily loaded and immutable: this is a shared cache returned from
     * an exported package; a caller's add() or sort() must throw rather
     * than corrupt the catalog for everyone. */
    public static List<Space> all() {
        return allFrom(dropInDir());
    }

    /** Where community catalog files live: {@code ~/.nmox/learn-catalog.d}. */
    public static File dropInDir() {
        return new File(System.getProperty("user.home"), ".nmox/learn-catalog.d");
    }

    /** {@link #all()} with the drop-in dir as a seam so tests never touch
     * the real {@code ~/.nmox}. */
    static List<Space> allFrom(File dropInDir) {
        return allFrom(dropInDir, LearningCatalog::warnBadDropIn);
    }

    static List<Space> allFrom(File dropInDir, BiConsumer<File, Exception> onBadFile) {
        List<Space> builtIns = builtIns();
        List<File> files = dropInFiles(dropInDir);
        if (files.isEmpty()) {
            // no drop-ins: hand back the built-in cache itself, so the
            // empty/missing-dir path is byte-identical to the pre-drop-in
            // product (and costs nothing beyond one dir listing)
            return builtIns;
        }
        String stamp = stamp(dropInDir, files);
        List<Space> local = mergedCache;
        if (local == null || !stamp.equals(mergedStamp)) {
            local = List.copyOf(merge(builtIns, parseDropIns(files, onBadFile)));
            mergedCache = local;
            mergedStamp = stamp; // benign race: same last-write-wins idiom as the built-in cache
        }
        return local;
    }

    /** The built-in catalog only, parsed once from the bundled resource. */
    static List<Space> builtIns() {
        List<Space> local = cache;
        if (local == null) {
            local = List.copyOf(load());
            cache = local;
        }
        return local;
    }

    /** The dir's {@code *.json} files sorted by filename — the one
     * deterministic order a user can control from a file manager. */
    private static List<File> dropInFiles(File dir) {
        File[] kids = dir == null ? null
                : dir.listFiles(f -> f.isFile() && f.getName().endsWith(".json"));
        if (kids == null || kids.length == 0) {
            return List.of();
        }
        Arrays.sort(kids, (a, b) -> a.getName().compareTo(b.getName()));
        return Arrays.asList(kids);
    }

    /** Change detector for the merged cache: path + mtime + size of every
     * drop-in file. Content hashing would re-read every file on every
     * picker open; mtime is how the studios' external-edit reloads decide
     * too. */
    private static String stamp(File dir, List<File> files) {
        StringBuilder sb = new StringBuilder(dir.getAbsolutePath());
        for (File f : files) {
            sb.append('|').append(f.getName()).append(':')
                    .append(f.lastModified()).append(':').append(f.length());
        }
        return sb.toString();
    }

    /** Every space from every readable drop-in file, filename order then
     * in-file order. A file that fails to read or parse is reported and
     * skipped whole — its neighbours still load. */
    private static List<Space> parseDropIns(List<File> files, BiConsumer<File, Exception> onBadFile) {
        List<Space> out = new ArrayList<>();
        for (File f : files) {
            try {
                String json = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                out.addAll(parse(new JSONObject(json)));
            } catch (IOException | RuntimeException ex) {
                onBadFile.accept(f, ex);
            }
        }
        return out;
    }

    /**
     * The merge law: a drop-in slug that matches a built-in replaces it
     * IN PLACE (the community improving a built-in shouldn't reshuffle
     * the picker), a new slug appends after all built-ins. Two drop-in
     * files claiming the same new slug: the later filename's content
     * wins, at the earlier filename's position.
     */
    static List<Space> merge(List<Space> builtIns, List<Space> dropIns) {
        LinkedHashMap<String, Space> out = new LinkedHashMap<>();
        for (Space s : builtIns) {
            out.put(s.slug(), s);
        }
        for (Space s : dropIns) {
            out.put(s.slug(), s); // LinkedHashMap replace keeps the original position
        }
        return new ArrayList<>(out.values());
    }

    /** The production bad-file report: a log WARNING plus a status-line
     * note naming the file — visible, never modal, never a blocked picker. */
    private static void warnBadDropIn(File file, Exception ex) {
        LOG.log(Level.WARNING, "Skipping malformed learning-catalog drop-in {0}: {1}",
                new Object[]{file.getAbsolutePath(), ex.toString()});
        org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                "Learning catalog: skipped malformed " + file.getName()
                        + " in " + file.getParentFile().getName());
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
