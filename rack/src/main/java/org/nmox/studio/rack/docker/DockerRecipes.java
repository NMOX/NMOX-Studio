package org.nmox.studio.rack.docker;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.nmox.studio.rack.projectstudio.UserTemplates;

/**
 * User-authored Dockerize recipes: a JSON file in
 * {@code ~/.nmox/dockerize.d/} appears in the Docker panel's Dockerize
 * tab beside the detected-toolchain generator (v1.301.0 — the fourth
 * drop-in surface, and the last seam the extensibility direction in
 * plan.md named). A team whose Dockerfile convention differs from the
 * built-in one stops re-editing the generated file on every project.
 *
 * <p>Schema, one recipe per file, read in filename order:
 * <pre>
 * { "name": "Corp Node baseline",
 *   "files": {
 *     "Dockerfile":     "FROM node:24-alpine\\n...",
 *     ".dockerignore":  "node_modules\\n.git\\n",
 *     "compose.yaml":   "services:\\n  {{name}}:\\n    build: .\\n" } }
 * </pre>
 * {@code {{name}}} substitutes the image name, exactly as the preview
 * shows it.
 *
 * <p>The path law is the SAME implementation the template drop-ins use
 * ({@link UserTemplates#pathProblem}) — an absolute path, a {@code ..}
 * segment, a backslash, or a drive letter disqualifies the WHOLE
 * recipe, because the Dockerize writer resolves each declared name
 * under the project directory and a drop-in must never write outside
 * it.
 */
public final class DockerRecipes {

    /** One recipe: label, and its files in declaration order. */
    public record Recipe(String name, Map<String, String> files) {
        @Override
        public String toString() {
            return name + " · yours";
        }
    }

    /** A drop-in that could not be used, and why. */
    public record Skipped(String file, String reason) {
    }

    /** Parse result. */
    public record Loaded(List<Recipe> recipes, List<Skipped> skipped) {
    }

    private DockerRecipes() {
    }

    /** Where recipes live: {@code ~/.nmox/dockerize.d}. */
    public static File dropInDir() {
        return new File(System.getProperty("user.home"), ".nmox/dockerize.d");
    }

    /** Usable recipes from the default drop-in dir, filename order. */
    public static Loaded load() {
        return loadFrom(dropInDir());
    }

    static Loaded loadFrom(File dir) {
        List<Recipe> out = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) {
            return new Loaded(out, skipped);
        }
        Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        for (File f : files) {
            try {
                out.add(parse(Files.readString(f.toPath(), StandardCharsets.UTF_8)));
            } catch (Exception ex) {
                // one bad file must not hide the good ones — the drop-in law
                skipped.add(new Skipped(f.getName(), ex.getMessage()));
            }
        }
        return new Loaded(out, skipped);
    }

    static Recipe parse(String json) throws IOException {
        JSONObject o = new JSONObject(json);
        String name = o.optString("name", "").trim();
        if (name.isEmpty()) {
            throw new IOException("missing \"name\"");
        }
        JSONObject files = o.optJSONObject("files");
        if (files == null || files.isEmpty()) {
            throw new IOException("missing \"files\"");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : files.keySet()) {
            String reason = UserTemplates.pathProblem(key);
            if (reason != null) {
                // the WHOLE recipe is refused — half a recipe is worse than none
                throw new IOException("file path " + reason + ": " + key);
            }
            map.put(key, files.getString(key));
        }
        return new Recipe(name, map);
    }

    /**
     * Resolves a declared filename strictly inside {@code dir}, or
     * throws. The writer routes EVERY file through this — recipes are
     * parse-time path-law checked, but the writer must refuse escapes
     * no matter which producer fed it, and a guard that lives in a
     * private UI method cannot be behaviorally tested (the v1.290.0
     * count-the-guards lesson, resolved structurally this time).
     */
    public static java.nio.file.Path resolveInside(File dir, String name) throws IOException {
        java.nio.file.Path target = new File(dir, name).toPath().normalize();
        if (!target.startsWith(dir.toPath())) {
            throw new IOException("Refusing to write outside the project: " + name);
        }
        return target;
    }

    /** The recipe's files with {@code {{name}}} substituted, ready to preview. */
    public static Map<String, String> materialize(Recipe recipe, String imageName) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : recipe.files().entrySet()) {
            out.put(e.getKey().replace("{{name}}", imageName),
                    e.getValue().replace("{{name}}", imageName));
        }
        return out;
    }
}
