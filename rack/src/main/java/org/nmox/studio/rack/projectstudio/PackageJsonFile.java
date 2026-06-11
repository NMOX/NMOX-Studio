package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

/**
 * Structured access to a project's package.json for the config editor.
 * Edits operate on the parsed document in place, so fields this editor
 * doesn't know about (exports, workspaces, tool config blocks...)
 * survive a round-trip untouched.
 */
public final class PackageJsonFile {

    private final File file;
    private final JSONObject json;

    private PackageJsonFile(File file, JSONObject json) {
        this.file = file;
        this.json = json;
    }

    public static PackageJsonFile load(File projectDir) throws IOException {
        File f = new File(projectDir, "package.json");
        if (!f.isFile()) {
            throw new IOException("No package.json in " + projectDir);
        }
        try {
            return new PackageJsonFile(f, new JSONObject(Files.readString(f.toPath(), StandardCharsets.UTF_8)));
        } catch (RuntimeException ex) {
            throw new IOException("Malformed package.json: " + ex.getMessage(), ex);
        }
    }

    public void save() throws IOException {
        Files.writeString(file.toPath(), json.toString(2) + "\n", StandardCharsets.UTF_8);
    }

    // ---- identity fields ----

    public String getName() {
        return json.optString("name", "");
    }

    public void setName(String name) {
        put("name", name);
    }

    public String getVersion() {
        return json.optString("version", "");
    }

    public void setVersion(String version) {
        put("version", version);
    }

    public String getDescription() {
        return json.optString("description", "");
    }

    public void setDescription(String description) {
        put("description", description);
    }

    public String getLicense() {
        return json.optString("license", "");
    }

    public void setLicense(String license) {
        put("license", license);
    }

    public String getType() {
        return json.optString("type", "commonjs");
    }

    public void setType(String type) {
        put("type", type);
    }

    private void put(String key, String value) {
        if (value == null || value.isBlank()) {
            json.remove(key);
        } else {
            json.put(key, value.trim());
        }
    }

    // ---- scripts ----

    public Map<String, String> getScripts() {
        Map<String, String> result = new LinkedHashMap<>();
        JSONObject scripts = json.optJSONObject("scripts");
        if (scripts != null) {
            for (String key : scripts.keySet()) {
                result.put(key, scripts.optString(key, ""));
            }
        }
        return result;
    }

    public void setScripts(Map<String, String> scripts) {
        if (scripts.isEmpty()) {
            json.remove("scripts");
            return;
        }
        JSONObject obj = new JSONObject();
        scripts.forEach(obj::put);
        json.put("scripts", obj);
    }

    // ---- dependencies (read-only views; mutations go through npm) ----

    public Map<String, String> getDependencies() {
        return readDeps("dependencies");
    }

    public Map<String, String> getDevDependencies() {
        return readDeps("devDependencies");
    }

    private Map<String, String> readDeps(String key) {
        Map<String, String> result = new LinkedHashMap<>();
        JSONObject deps = json.optJSONObject(key);
        if (deps != null) {
            for (String name : deps.keySet()) {
                result.put(name, deps.optString(name, ""));
            }
        }
        return result;
    }
}
