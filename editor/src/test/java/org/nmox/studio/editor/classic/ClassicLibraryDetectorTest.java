package org.nmox.studio.editor.classic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The detector decides where classic completion appears, so its walk-up,
 * manifest reading, script-tag scan and cache each get pinned against
 * real directory fixtures. Everything runs on a @TempDir - no editor,
 * no platform.
 */
class ClassicLibraryDetectorTest {

    @TempDir
    Path root;

    private final ClassicLibraryDetector detector = new ClassicLibraryDetector();

    // ---- manifests ---------------------------------------------------------

    @Test
    @DisplayName("package.json dependencies and devDependencies both count")
    void packageJson() throws IOException {
        Files.writeString(root.resolve("package.json"), """
                {"name": "app",
                 "dependencies": {"jquery": "^3.7.1", "express": "^4.0.0"},
                 "devDependencies": {"backbone": "~1.6.0"}}
                """);
        Path js = Files.writeString(root.resolve("app.js"), "");
        assertThat(detector.detect(js)).containsExactlyInAnyOrder("jquery", "backbone");
    }

    @Test
    @DisplayName("bower.json dependencies count; lodash maps to underscore")
    void bowerJson() throws IOException {
        Files.writeString(root.resolve("bower.json"), """
                {"name": "app",
                 "dependencies": {"mootools": "^1.6.0", "lodash": "^4.17.0"}}
                """);
        Path js = Files.writeString(root.resolve("app.js"), "");
        assertThat(detector.detect(js)).containsExactlyInAnyOrder("mootools", "underscore");
    }

    @Test
    @DisplayName("Script tags in root html files count, with versioned vendored names")
    void scriptTags() throws IOException {
        Files.writeString(root.resolve("index.html"), """
                <!DOCTYPE html>
                <html><head>
                <script src="js/vendor/jquery-1.12.4.min.js"></script>
                <script type="text/javascript" src='lib/knockout-3.5.1.js'></script>
                <script>var inline = true;</script>
                </head><body></body></html>
                """);
        Path js = Files.createDirectories(root.resolve("js")).resolve("app.js");
        Files.writeString(js, "");
        assertThat(detector.detect(js)).containsExactlyInAnyOrder("jquery", "knockout");
    }

    @Test
    @DisplayName("No manifests and no libraries: empty, never null")
    void nothingDetected() throws IOException {
        Files.writeString(root.resolve("package.json"), "{\"name\": \"plain\"}");
        Path js = Files.writeString(root.resolve("app.js"), "");
        assertThat(detector.detect(js)).isEmpty();
    }

    @Test
    @DisplayName("A malformed package.json contributes nothing and never throws")
    void malformedManifest() throws IOException {
        Files.writeString(root.resolve("package.json"), "{not json at all");
        Files.writeString(root.resolve("index.html"),
                "<script src=\"underscore-min.js\"></script>");
        Path js = Files.writeString(root.resolve("app.js"), "");
        assertThat(detector.detect(js)).containsExactly("underscore");
    }

    // ---- walk-up -----------------------------------------------------------

    @Test
    @DisplayName("A deeply nested file finds the project root's manifests")
    void walksUp() throws IOException {
        Files.writeString(root.resolve("package.json"),
                "{\"dependencies\": {\"knockout\": \"^3.5.1\"}}");
        Path deep = Files.createDirectories(root.resolve("src/main/js/widgets"));
        Path js = Files.writeString(deep.resolve("grid.js"), "");
        assertThat(detector.detect(js)).containsExactly("knockout");
    }

    @Test
    @DisplayName("The walk stops at a .git repo root without web manifests")
    void stopsAtGitBoundary() throws IOException {
        // outer dir HAS a manifest, but the nested repo root fences it off
        Files.writeString(root.resolve("package.json"),
                "{\"dependencies\": {\"jquery\": \"^3.7.1\"}}");
        Path repo = Files.createDirectories(root.resolve("other-repo"));
        Files.createDirectories(repo.resolve(".git"));
        Path js = Files.writeString(Files.createDirectories(repo.resolve("src")).resolve("app.js"), "");
        assertThat(detector.detect(js)).isEmpty();
    }

    @Test
    @DisplayName("The walk gives up beyond the level bound")
    void boundedWalk() throws IOException {
        Files.writeString(root.resolve("package.json"),
                "{\"dependencies\": {\"jquery\": \"^3.7.1\"}}");
        Path deep = root;
        for (int i = 0; i < ClassicLibraryDetector.MAX_WALK_UP + 2; i++) {
            deep = deep.resolve("d" + i);
        }
        Files.createDirectories(deep);
        Path js = Files.writeString(deep.resolve("app.js"), "");
        assertThat(detector.detect(js)).isEmpty();
    }

    // ---- cache -------------------------------------------------------------

    @Test
    @DisplayName("Adding a dependency invalidates the per-directory cache")
    void cacheInvalidation() throws IOException {
        Path manifest = Files.writeString(root.resolve("package.json"),
                "{\"dependencies\": {\"jquery\": \"^3.7.1\"}}");
        Path js = Files.writeString(root.resolve("app.js"), "");
        assertThat(detector.detect(js)).containsExactly("jquery");

        Files.writeString(manifest,
                "{\"dependencies\": {\"jquery\": \"^3.7.1\", \"underscore\": \"^1.13.7\"}}");
        // lastModified granularity can be a whole second; force a new stamp
        Files.setLastModifiedTime(manifest,
                FileTime.fromMillis(System.currentTimeMillis() + 5_000));
        assertThat(detector.detect(js)).containsExactlyInAnyOrder("jquery", "underscore");
    }

    @Test
    @DisplayName("Unchanged manifests hit the cache (same instance back)")
    void cacheHit() throws IOException {
        Files.writeString(root.resolve("package.json"),
                "{\"dependencies\": {\"jquery\": \"^3.7.1\"}}");
        Path js = Files.writeString(root.resolve("app.js"), "");
        assertThat(detector.detect(js)).isSameAs(detector.detect(js));
    }

    // ---- html scan limits --------------------------------------------------

    @Test
    @DisplayName("A script tag past the 64KB read cap is not seen")
    void htmlReadCap() throws IOException {
        String filler = "<!-- " + "x".repeat(ClassicLibraryDetector.MAX_HTML_BYTES) + " -->\n";
        Files.writeString(root.resolve("index.html"),
                filler + "<script src=\"jquery.min.js\"></script>");
        Path js = Files.writeString(root.resolve("app.js"), "");
        assertThat(detector.detect(js)).isEmpty();
    }

    // ---- pure helpers ------------------------------------------------------

    @Test
    @DisplayName("scriptSrcs: hand-rolled scan finds quoted src values only")
    void scriptSrcScan() {
        List<String> srcs = ClassicLibraryDetector.scriptSrcs("""
                <script src="a.js"></script>
                <SCRIPT SRC='B.js'></SCRIPT>
                <script>inline()</script>
                <script src=unquoted.js></script>
                <script src="c.js?v=2"></script>
                """);
        assertThat(srcs).containsExactly("a.js", "B.js", "c.js?v=2");
    }

    @Test
    @DisplayName("Dependency keys map strictly; filenames map loosely")
    void mappings() {
        java.util.Set<String> byKey = new java.util.TreeSet<>();
        ClassicLibraryDetector.addFromDependencyKey("backbone.marionette", byKey);
        ClassicLibraryDetector.addFromDependencyKey("jquery-ui", byKey); // not core jquery
        ClassicLibraryDetector.addFromDependencyKey("prototypejs", byKey);
        assertThat(byKey).containsExactly("backbone", "prototype");

        java.util.Set<String> byName = new java.util.TreeSet<>();
        ClassicLibraryDetector.addFromName("jquery-1.12.4.min.js", byName);
        ClassicLibraryDetector.addFromName("lodash.core.js", byName);
        ClassicLibraryDetector.addFromName("d3.v7.min.js", byName);
        assertThat(byName).containsExactly("jquery", "underscore");
    }
}
