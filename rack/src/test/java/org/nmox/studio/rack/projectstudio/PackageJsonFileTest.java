package org.nmox.studio.rack.projectstudio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class PackageJsonFileTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("Should edit known fields while preserving unknown ones")
    void shouldPreserveUnknownFields() throws Exception {
        Files.writeString(dir.resolve("package.json"), """
            {
              "name": "old-name",
              "version": "1.0.0",
              "exports": { ".": "./dist/index.js" },
              "scripts": { "build": "tsc" },
              "dependencies": { "react": "^18.0.0" },
              "devDependencies": { "vite": "^5.0.0" },
              "volta": { "node": "20.11.0" }
            }
            """);

        PackageJsonFile pkg = PackageJsonFile.load(dir.toFile());
        assertThat(pkg.getName()).isEqualTo("old-name");
        assertThat(pkg.getDependencies()).containsKey("react");
        assertThat(pkg.getDevDependencies()).containsKey("vite");

        pkg.setName("new-name");
        pkg.setDescription("a fine app");
        Map<String, String> scripts = new LinkedHashMap<>(pkg.getScripts());
        scripts.put("test", "vitest run");
        pkg.setScripts(scripts);
        pkg.save();

        JSONObject saved = new JSONObject(Files.readString(dir.resolve("package.json")));
        assertThat(saved.getString("name")).isEqualTo("new-name");
        assertThat(saved.getString("description")).isEqualTo("a fine app");
        assertThat(saved.getJSONObject("scripts").getString("test")).isEqualTo("vitest run");
        assertThat(saved.getJSONObject("scripts").getString("build")).isEqualTo("tsc");
        // fields the editor doesn't know about survive
        assertThat(saved.getJSONObject("exports").getString(".")).isEqualTo("./dist/index.js");
        assertThat(saved.getJSONObject("volta").getString("node")).isEqualTo("20.11.0");
    }

    @Test
    @DisplayName("Should remove blanked fields rather than writing empty strings")
    void shouldRemoveBlankedFields() throws Exception {
        Files.writeString(dir.resolve("package.json"), """
            { "name": "x", "version": "1.0.0", "description": "old" }
            """);

        PackageJsonFile pkg = PackageJsonFile.load(dir.toFile());
        pkg.setDescription("");
        pkg.save();

        JSONObject saved = new JSONObject(Files.readString(dir.resolve("package.json")));
        assertThat(saved.has("description")).isFalse();
    }
}
