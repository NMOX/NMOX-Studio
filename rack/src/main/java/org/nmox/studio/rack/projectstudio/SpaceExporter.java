package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The teacher's exporter (v2.39.3, the developer-teacher persona):
 * turn the AIMED project into a learning-space drop-in — author an
 * exercise by building it, not by hand-writing JSON with embedded
 * file bodies. The output is one {@code <slug>.json} in
 * {@code ~/.nmox/learn-catalog.d}, the same file a teacher hands
 * their class: students drop it in the same directory and the space
 * appears in their picker — sample files, tutorial, driver, and the
 * teacher's checkpoints (v2.39.1) intact.
 *
 * <p>The laws: TEXT files only (a NUL in the first 8k marks binary),
 * bounded ({@value #FILE_CAP} chars per file, {@value #TOTAL_CAP}
 * total, {@value #MAX_FILES} files — a space is a lesson, not a
 * backup), the heavy dirs excluded (node_modules, .git, dist, build,
 * coverage, target) along with the studio's own workspace files; and
 * the export is REFUSED WHOLE unless its own bytes round-trip through
 * {@link LearningCatalog#parse} to exactly one space with every
 * checkpoint accepted — a teacher must hear about a broken checkpoint
 * at export time, never their student at drop-in time. Overwriting is
 * allowed only over a file that already claims the SAME single slug
 * (the teacher iterating on their own export); anything else refuses.
 */
public final class SpaceExporter {

    static final int FILE_CAP = 64_000;
    static final int TOTAL_CAP = 512_000;
    static final int MAX_FILES = 40;

    private static final Set<String> EXCLUDED_DIRS = Set.of(
            "node_modules", ".git", "dist", "build", "coverage", "target",
            ".nmox", "out");

    private SpaceExporter() {
    }

    /** What the dialog collects. Command is argv, RUN-driver v1. */
    public record Options(String name, String blurb,
            LearningCatalog.Category category, String family,
            List<String> runCommand) {
    }

    /** The export's outcome: where it landed and what was left out. */
    public record Outcome(File file, String slug, int filesIncluded,
            List<String> skipped) {
    }

    static String slugOf(String name) {
        String slug = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "space" : slug;
    }

    /** Text sample files under the caps; skips note their reason. */
    static List<LearningCatalog.SampleFile> gather(File projectDir,
            List<String> skipped) throws IOException {
        List<LearningCatalog.SampleFile> out = new ArrayList<>();
        Path root = projectDir.toPath();
        long total = 0;
        List<Path> candidates;
        try (var walk = Files.walk(root)) {
            candidates = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        for (Path part : root.relativize(p)) {
                            String s = part.toString();
                            if (EXCLUDED_DIRS.contains(s) || s.startsWith(".nmox")) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .sorted()
                    .toList();
        }
        for (Path p : candidates) {
            String rel = root.relativize(p).toString().replace('\\', '/');
            if (out.size() >= MAX_FILES) {
                skipped.add(rel + " — over the " + MAX_FILES + "-file cap");
                continue;
            }
            byte[] head = new byte[8192];
            int n;
            try (var in = Files.newInputStream(p)) {
                n = in.readNBytes(head, 0, head.length);
            }
            boolean binary = false;
            for (int i = 0; i < n; i++) {
                if (head[i] == 0) {
                    binary = true;
                    break;
                }
            }
            if (binary) {
                skipped.add(rel + " — binary, not a sample file");
                continue;
            }
            String text = Files.readString(p, StandardCharsets.UTF_8);
            if (text.length() > FILE_CAP) {
                skipped.add(rel + " — over the per-file cap ("
                        + text.length() + " chars)");
                continue;
            }
            if (total + text.length() > TOTAL_CAP) {
                skipped.add(rel + " — over the total cap");
                continue;
            }
            total += text.length();
            out.add(new LearningCatalog.SampleFile(rel, text));
        }
        return out;
    }

    /**
     * Builds, VALIDATES through the real parser, and writes the
     * drop-in. Throws with the reasons when validation refuses —
     * nothing is written on any refusal.
     */
    public static Outcome export(File projectDir, Options opts) throws IOException {
        List<String> skipped = new ArrayList<>();
        List<LearningCatalog.SampleFile> files = gather(projectDir, skipped);
        if (files.isEmpty()) {
            throw new IOException("No text files to export — a space needs sample files.");
        }
        if (opts.runCommand() == null || opts.runCommand().isEmpty()) {
            throw new IOException("A run command is required — it becomes the space's driver.");
        }
        String slug = slugOf(opts.name());

        File tut = new File(projectDir, "TUTORIAL.md");
        String tutorial = tut.isFile()
                ? Files.readString(tut.toPath(), StandardCharsets.UTF_8)
                : "# " + opts.name() + "\n\nWalk your students through the code:"
                  + " what to run, what to change, what to notice.\n";

        JSONObject space = new JSONObject();
        space.put("slug", slug);
        space.put("name", opts.name());
        space.put("category", opts.category().name());
        space.put("family", opts.family());
        space.put("blurb", opts.blurb());
        JSONObject driver = new JSONObject();
        driver.put("kind", "run");
        driver.put("command", new JSONArray(opts.runCommand()));
        space.put("driver", driver);
        space.put("install", new JSONObject());
        JSONArray fileArr = new JSONArray();
        for (LearningCatalog.SampleFile f : files) {
            fileArr.put(new JSONObject().put("path", f.path()).put("content", f.content()));
        }
        space.put("files", fileArr);
        space.put("tutorial", tutorial);

        File cps = new File(projectDir, ".nmox-checkpoints.json");
        if (cps.isFile()) {
            JSONArray arr = new JSONArray(
                    Files.readString(cps.toPath(), StandardCharsets.UTF_8));
            List<String> notes = new ArrayList<>();
            Checkpoints.parse(arr, notes);
            if (!notes.isEmpty()) {
                throw new IOException("Checkpoints refused — fix these before"
                        + " exporting (your student would hit them otherwise): "
                        + String.join("; ", notes));
            }
            space.put("checkpoints", arr);
        }

        JSONObject root = new JSONObject().put("spaces", new JSONArray().put(space));
        // the round-trip gate: the export must parse with the SAME code
        // the student's picker will use — a worked example is a fixture
        List<LearningCatalog.Space> parsed = LearningCatalog.parse(
                new JSONObject(root.toString()));
        if (parsed.size() != 1 || !slug.equals(parsed.get(0).slug())) {
            throw new IOException("Export failed its own round-trip parse — not written.");
        }

        File dir = LearningCatalog.dropInDir();
        Files.createDirectories(dir.toPath());
        File target = new File(dir, slug + ".json");
        if (target.exists() && !claimsOnly(target, slug)) {
            throw new IOException(target.getName() + " exists and is not a"
                    + " previous export of this space — not overwritten.");
        }
        Files.writeString(target.toPath(), root.toString(2), StandardCharsets.UTF_8);
        return new Outcome(target, slug, files.size(), skipped);
    }

    /** True when the existing drop-in claims exactly this one slug. */
    static boolean claimsOnly(File dropIn, String slug) {
        try {
            JSONArray arr = new JSONObject(
                    Files.readString(dropIn.toPath(), StandardCharsets.UTF_8))
                    .optJSONArray("spaces");
            return arr != null && arr.length() == 1
                    && slug.equals(arr.getJSONObject(0).optString("slug"));
        } catch (IOException | RuntimeException broken) {
            return false;
        }
    }
}
