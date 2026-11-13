package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Angular pins live in TWO generators — the ANGULAR project
 * template and the angular learning space — and both must ride the
 * same proven line (the KitCatalogParityTest lesson: same starters,
 * two homes, lockstep or drift). v1.241.0 found the template's ^22
 * pin unable to npm-install against a TypeScript 5 and put both homes
 * on ~21.2; ledger 140 (2026-11-13) moved both to the proven ~22.2 +
 * TypeScript ~6.0 set, each home installed and built for real. This
 * gate fails the build if either moves alone.
 */
class AngularSpacePinParityTest {

    @Test
    @DisplayName("the angular space and the ANGULAR template pin the same @angular line")
    void spaceMatchesTemplate(@TempDir Path tmp) throws Exception {
        File dir = tmp.resolve("ng").toFile();
        ProjectTemplates.ANGULAR.generate(dir, "parity-ng");
        JSONObject tpl = new JSONObject(
                Files.readString(dir.toPath().resolve("package.json")));
        String tplCore = tpl.getJSONObject("dependencies").getString("@angular/core");
        String tplTs = tpl.getJSONObject("devDependencies").getString("typescript");

        JSONObject cat;
        try (var in = ProjectTemplates.class.getResourceAsStream("learn-catalog.json")) {
            cat = new JSONObject(new String(in.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8));
        }
        JSONArray spaces = cat.getJSONArray("spaces");
        JSONObject space = null;
        for (int i = 0; i < spaces.length(); i++) {
            if ("angular".equals(spaces.getJSONObject(i).optString("slug"))) {
                space = spaces.getJSONObject(i);
            }
        }
        assertThat(space).as("angular space present in the catalog").isNotNull();

        JSONObject pkg = null;
        JSONArray files = space.getJSONArray("files");
        for (int i = 0; i < files.length(); i++) {
            JSONObject f = files.getJSONObject(i);
            if ("package.json".equals(f.getString("path"))) {
                pkg = new JSONObject(f.getString("content"));
            }
        }
        assertThat(pkg).as("space ships a package.json").isNotNull();
        assertThat(pkg.getJSONObject("dependencies").getString("@angular/core"))
                .as("space rides the template's proven @angular line")
                .isEqualTo(tplCore);
        assertThat(pkg.getJSONObject("devDependencies").getString("typescript"))
                .as("space keeps the template's TypeScript (Angular 22 peers on >=6.0 <6.1)")
                .isEqualTo(tplTs);
        for (String tool : new String[]{"@angular/cli", "@angular/build", "@angular/compiler-cli"}) {
            assertThat(pkg.getJSONObject("devDependencies").getString(tool))
                    .as("space rides the template's " + tool)
                    .isEqualTo(tpl.getJSONObject("devDependencies").getString(tool));
        }

        // the space is a REAL workspace: ng serve has an angular.json to read
        boolean hasNgJson = false;
        for (int i = 0; i < files.length(); i++) {
            if ("angular.json".equals(files.getJSONObject(i).getString("path"))) {
                hasNgJson = true;
            }
        }
        assertThat(hasNgJson)
                .as("the space's START (ng serve) needs angular.json — the "
                        + "pre-v1.241.0 space shipped none and could not start")
                .isTrue();
    }
}
