package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.nmox.studio.core.util.Containment;

/**
 * User-authored project templates: drop a JSON file into
 * {@code ~/.nmox/templates.d/} and it appears in the New Project wizard
 * beside the built-ins — no IDE build, no plugin, just a file.
 *
 * <p>Why this exists (v1.293.0, the extensibility arc): "templates live
 * in code; should be extensible/data-driven" had been tech-debt item #1
 * since the debt file was written. The product already settled how a
 * drop-in catalog should behave — {@code ~/.nmox/learn-catalog.d}
 * (v1.53.0) merges community learning spaces at a lazy read point,
 * skipping malformed files with a note instead of blocking the picker —
 * so this class is that idiom applied to the wizard. The BUILT-INS
 * deliberately stay in {@link ProjectTemplates}: their dependency pins
 * carry live-proven version ceilings (TS 5, vite ^6, Angular ~21.2)
 * enforced by build gates that read that source, and moving them into
 * data would move them out of the gates' sight.
 *
 * <p>Schema, one template per file, read in filename order:
 * <pre>
 * {
 *   "name":        "My API Starter",
 *   "description": "Fastify + vitest, the way our team starts services",
 *   "files": {
 *     "package.json":  "{ \"name\": \"{{name}}\", ... }",
 *     "src/server.js": "..."
 *   }
 * }
 * </pre>
 * {@code {{name}}} in any path or content is replaced with the project
 * name — the same var spelling API Studio and .http files already use.
 *
 * <p>Safety: a template writes ONLY strictly inside the target
 * directory. A file entry with an absolute path, a {@code ..} segment,
 * a backslash, or a drive letter disqualifies the WHOLE template (not
 * just the entry — half a template is worse than none), because a
 * drop-in is data someone may have copied from anywhere and a path
 * escape here would be an arbitrary file write.
 */
public final class UserTemplates {

    /** One user-authored template, files in declaration order. */
    public record Custom(String name, String description,
            Map<String, String> files, File source) {
        @Override
        public String toString() {
            return name;
        }
    }

    /** A drop-in file that could not be used, and why — for the status line. */
    public record Skipped(String file, String reason) {
    }

    /** Total bytes of declared content one template may carry (2 MB). */
    static final int MAX_TOTAL_CHARS = 2 * 1024 * 1024;
    /** Files one template may declare — a starter, not an archive. */
    static final int MAX_FILES = 200;

    private UserTemplates() {
    }

    /** Where user templates live: {@code ~/.nmox/templates.d}. */
    public static File dropInDir() {
        return new File(System.getProperty("user.home"), ".nmox/templates.d");
    }

    /** Usable templates from the default drop-in dir, filename order. */
    public static List<Custom> all() {
        return load(dropInDir()).templates();
    }

    /** Parse result: what loaded plus what was skipped and why. */
    public record Loaded(List<Custom> templates, List<Skipped> skipped) {
    }

    static Loaded load(File dir) {
        List<Custom> out = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) {
            return new Loaded(out, skipped);
        }
        Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        for (File f : files) {
            try {
                out.add(parse(org.nmox.studio.core.util.BoundedReads.read(f.toPath()), f));
            } catch (Exception ex) {
                // one bad file must not hide the good ones — the learn-catalog law
                skipped.add(new Skipped(f.getName(), ex.getMessage()));
            }
        }
        return new Loaded(out, skipped);
    }

    static Custom parse(String json, File source) throws IOException {
        JSONObject o = new JSONObject(json);
        String name = o.optString("name", "").trim();
        if (name.isEmpty()) {
            throw new IOException("missing \"name\"");
        }
        String description = o.optString("description", "").trim();
        JSONObject files = o.optJSONObject("files");
        if (files == null || files.isEmpty()) {
            throw new IOException("missing \"files\"");
        }
        if (files.length() > MAX_FILES) {
            throw new IOException("more than " + MAX_FILES + " files");
        }
        Map<String, String> map = new LinkedHashMap<>();
        long total = 0;
        for (String key : files.keySet()) {
            String reason = pathProblem(key);
            if (reason != null) {
                // the WHOLE template is refused: a template that would write
                // outside its target directory must not half-generate
                throw new IOException("file path " + reason + ": " + key);
            }
            String content = files.getString(key);
            total += content.length();
            if (total > MAX_TOTAL_CHARS) {
                throw new IOException("content exceeds " + (MAX_TOTAL_CHARS / 1024) + " KB");
            }
            map.put(key, content);
        }
        return new Custom(name, description, map, source);
    }

    /**
     * Why a declared path is unsafe, or null when it is fine. Everything
     * here is judged BEFORE {@code {{name}}} substitution — the project
     * name is sanitized by the wizard, so the declared path is where an
     * escape could hide.
     *
     * <p>This is a rule about what a template AUTHOR may write, and it
     * deliberately survives {@link org.nmox.studio.core.util.Containment}
     * arriving in {@link #generate} (ledger 117). The two answer different
     * questions at different moments:
     *
     * <ul>
     *   <li>This one runs at PARSE time, when no target directory exists
     *       yet and so no containment guard can be asked. Its refusal
     *       disqualifies the WHOLE template and is reported against the
     *       drop-in's filename in the wizard's skipped list.</li>
     *   <li>{@code Containment} runs at GENERATE time against a real
     *       root, and refuses one write.</li>
     * </ul>
     *
     * <p>They also disagree about {@code ~} on purpose: {@code "~/x"} is
     * not a traversal at all — it would land harmlessly at
     * {@code root/~/x} — so containment has nothing to say about it. An
     * author who wrote it meant their home directory and should be told
     * the file will not go there, which is author intent rather than
     * containment. {@link org.nmox.studio.rack.docker.DockerRecipes}
     * shares this rule for the same reason.
     */
    public static String pathProblem(String path) {
        if (path.isBlank()) {
            return "is blank";
        }
        if (path.startsWith("/") || path.startsWith("~")) {
            return "is absolute";
        }
        if (path.contains("\\")) {
            return "contains a backslash";
        }
        if (path.length() >= 2 && path.charAt(1) == ':') {
            return "looks like a drive letter";
        }
        for (String segment : path.split("/")) {
            if (segment.equals("..")) {
                return "contains ..";
            }
        }
        return null;
    }

    /**
     * Writes the template into {@code dir}, refusing a non-empty target
     * exactly as the built-ins do. No extra files are added — the
     * template IS the contract; whoever wrote it decided what a project
     * of theirs contains.
     *
     * <p>Every write is placed by {@link Containment} (ledger 117). This
     * used to be spelled here as {@code root.resolve(rel).normalize()}
     * followed by {@code startsWith(root)}, which is LEXICAL: it folds
     * {@code ..} textually and never asks the filesystem, so it judged a
     * path the write would not take. Two measured consequences, both on
     * the shipped code:
     *
     * <ul>
     *   <li>It refused writes it should have allowed. The wizard builds
     *       its target as {@code new File(locationField, name)} and the
     *       location is free text, so a location a user typed or pasted
     *       with a {@code ..} segment left the BASE un-normalized while
     *       the target was normalized — the two could then never share a
     *       prefix, and EVERY file of a perfectly ordinary template was
     *       refused with "Refusing to write outside the project".</li>
     *   <li>It let a symlinked segment through. Nothing reachable today
     *       exploits that — the never-clobber check two lines up proves
     *       the target empty, and this loop creates only plain
     *       directories and regular files, so no link can exist under the
     *       root while it runs — but this is the product's one templated
     *       WRITE path and it should not be the weakest spelling of the
     *       rule it depends on.</li>
     * </ul>
     *
     * <p>The refusal SENTENCE stays this surface's own, which is the
     * division {@code Containment} states: it decides containment, each
     * caller keeps its own voice. A path that resolves to the project
     * root itself is refused here too, and that is a gain rather than a
     * side effect — it used to reach {@link Files#writeString} and come
     * back as the operating system's raw "Is a directory", a sentence
     * that names no template and belongs to nobody.
     *
     * <p>Unchanged: a refusal mid-loop leaves earlier files on disk. The
     * whole-template law lives at parse time ({@link #pathProblem}),
     * where a bad path means no {@link Custom} is ever built.
     */
    public static void generate(Custom template, File dir, String projectName)
            throws IOException {
        Path root = dir.toPath();
        Files.createDirectories(root);
        try (var entries = Files.list(root)) {
            if (entries.findAny().isPresent()) {
                throw new IOException("Directory is not empty: " + dir);
            }
        }
        for (Map.Entry<String, String> e : template.files().entrySet()) {
            String rel = e.getKey().replace("{{name}}", projectName);
            // belt and braces: pathProblem() already refused escapes at
            // parse time; a substitution result must not reopen the hole
            Path target = Containment.resolvePath(dir, rel);
            if (target == null) {
                throw new IOException("Refusing to write outside the project: " + rel);
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, e.getValue().replace("{{name}}", projectName),
                    StandardCharsets.UTF_8);
        }
    }
}
