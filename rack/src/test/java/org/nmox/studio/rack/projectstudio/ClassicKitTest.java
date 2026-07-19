package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Classic Kit's whole contract: vendored delivery lands the pinned
 * bundled builds and wires script tags idempotently (Underscore ahead
 * of Backbone), npm delivery merges package.json honestly (and refuses
 * what it cannot do), and every generator never clobbers — an existing
 * config gets a .suggested sibling, never an overwrite.
 */
class ClassicKitTest {

    @TempDir
    Path dir;

    private File project() {
        return dir.toFile();
    }

    private static ClassicKit.Options options(Set<String> libs, ClassicKit.Mode mode,
            Set<String> generators) {
        return new ClassicKit.Options(libs, mode, generators);
    }

    // ---- vendored delivery ----

    @Nested
    class Vendored {

        @Test
        @DisplayName("Vendored jQuery lands byte-equal to the bundled build and wires index.html")
        void vendorsAndWires() throws Exception {
            Files.writeString(dir.resolve("index.html"),
                    "<html><head><title>t</title></head><body></body></html>");

            List<ClassicKit.Outcome> outcomes = ClassicKit.write(project(),
                    options(Set.of("jquery"), ClassicKit.Mode.VENDORED, Set.of()));

            Path vendored = dir.resolve("vendor/jquery-3.7.1.min.js");
            assertThat(vendored).exists();
            assertThat(Files.readAllBytes(vendored))
                    .isEqualTo(ClassicKit.vendorBytes("jquery-3.7.1.min.js"));
            assertThat(Files.readString(dir.resolve("index.html")))
                    .contains("<script src=\"vendor/jquery-3.7.1.min.js\"></script>");
            assertThat(outcomes).extracting(ClassicKit.Outcome::status)
                    .containsExactly("written", "wired");
        }

        @Test
        @DisplayName("Running the kit twice changes nothing: all skipped/already wired")
        void secondRunIsIdempotent() throws Exception {
            Files.writeString(dir.resolve("index.html"),
                    "<html><head></head><body></body></html>");
            ClassicKit.Options opts = options(Set.of("jquery", "knockout"),
                    ClassicKit.Mode.VENDORED, Set.of());
            ClassicKit.write(project(), opts);
            String wiredOnce = Files.readString(dir.resolve("index.html"));

            List<ClassicKit.Outcome> second = ClassicKit.write(project(), opts);

            assertThat(second).allSatisfy(o -> assertThat(o.changed()).isFalse());
            assertThat(second).extracting(ClassicKit.Outcome::status)
                    .containsExactly("already exists, untouched",
                            "already exists, untouched", "already wired");
            assertThat(Files.readString(dir.resolve("index.html"))).isEqualTo(wiredOnce);
        }

        @Test
        @DisplayName("Backbone pulls Underscore in, and Underscore's script tag comes first")
        void underscoreBeforeBackbone() throws Exception {
            Files.writeString(dir.resolve("index.html"),
                    "<html><head></head><body></body></html>");

            ClassicKit.write(project(), options(Set.of("backbone"),
                    ClassicKit.Mode.VENDORED, Set.of()));

            assertThat(dir.resolve("vendor/underscore-1.13.7.min.js")).exists();
            assertThat(dir.resolve("vendor/backbone-1.6.0.min.js")).exists();
            String html = Files.readString(dir.resolve("index.html"));
            assertThat(html.indexOf("underscore-1.13.7.min.js"))
                    .isLessThan(html.indexOf("backbone-1.6.0.min.js"));
        }

        @Test
        @DisplayName("No index.html: the kit creates an old-school skeleton carrying the tags")
        void createsSkeletonWhenNoIndex() throws Exception {
            List<ClassicKit.Outcome> outcomes = ClassicKit.write(project(),
                    options(Set.of("mootools"), ClassicKit.Mode.VENDORED, Set.of()));

            String html = Files.readString(dir.resolve("index.html"));
            assertThat(html)
                    .contains("<script src=\"vendor/mootools-core-1.6.0-compat.min.js\"></script>")
                    .contains("<div id=\"header\">")
                    .contains("<div id=\"content\">")
                    .doesNotContain("type=\"module\"");
            assertThat(outcomes).filteredOn(o -> o.path().equals("index.html"))
                    .singleElement().satisfies(o -> {
                        assertThat(o.changed()).isTrue();
                        assertThat(o.status()).contains("written");
                    });
        }

        @Test
        @DisplayName("A page with no </head> is skipped with the honest reason, not mangled")
        void headlessPageIsLeftAlone() throws Exception {
            Files.writeString(dir.resolve("index.html"), "<p>just a fragment</p>");

            List<ClassicKit.Outcome> outcomes = ClassicKit.write(project(),
                    options(Set.of("jquery"), ClassicKit.Mode.VENDORED, Set.of()));

            assertThat(Files.readString(dir.resolve("index.html")))
                    .isEqualTo("<p>just a fragment</p>");
            assertThat(outcomes).filteredOn(o -> o.path().equals("index.html"))
                    .singleElement().satisfies(o
                            -> assertThat(o.status()).contains("no </head>"));
        }

        @Test
        @DisplayName("An existing vendor file of the same name is never overwritten")
        void existingVendorFileUntouched() throws Exception {
            Files.createDirectories(dir.resolve("vendor"));
            Files.writeString(dir.resolve("vendor/jquery-3.7.1.min.js"), "// my patched copy");
            Files.writeString(dir.resolve("index.html"),
                    "<html><head></head><body></body></html>");

            List<ClassicKit.Outcome> outcomes = ClassicKit.write(project(),
                    options(Set.of("jquery"), ClassicKit.Mode.VENDORED, Set.of()));

            assertThat(Files.readString(dir.resolve("vendor/jquery-3.7.1.min.js")))
                    .isEqualTo("// my patched copy");
            assertThat(outcomes.get(0).status()).isEqualTo("already exists, untouched");
        }

        @Test
        @DisplayName("Every catalog library vendors from the bundle without error")
        void everyLibraryVendors() throws Exception {
            ClassicKit.write(project(), options(
                    Set.of("jquery", "mootools", "prototype", "backbone", "knockout", "alpine", "htmx"),
                    ClassicKit.Mode.VENDORED, Set.of()));

            for (ClassicKit.Lib lib : ClassicKit.libraries()) {
                assertThat(dir.resolve("vendor/" + lib.vendorFile()))
                        .as(lib.id()).exists();
            }
            assertThat(dir.resolve("vendor/" + ClassicKit.underscore().vendorFile())).exists();
        }
    }

    // ---- npm delivery ----

    @Nested
    class Npm {

        @Test
        @DisplayName("npm delivery merges dependencies and preserves unknown fields")
        void mergesPackageJson() throws Exception {
            Files.writeString(dir.resolve("package.json"), """
                {
                  "name": "legacy-app",
                  "workspaces": ["packages/*"],
                  "dependencies": { "lodash": "^4.17.21" }
                }
                """);

            List<ClassicKit.Outcome> outcomes = ClassicKit.write(project(),
                    options(Set.of("jquery", "backbone", "knockout", "mootools"),
                            ClassicKit.Mode.NPM, Set.of()));

            JSONObject json = new JSONObject(Files.readString(dir.resolve("package.json")));
            JSONObject deps = json.getJSONObject("dependencies");
            assertThat(deps.getString("jquery")).isEqualTo("^3.7.1");
            assertThat(deps.getString("mootools")).isEqualTo("^1.6.0");
            assertThat(deps.getString("backbone")).isEqualTo("^1.6.0");
            assertThat(deps.getString("underscore")).isEqualTo("^1.13.7");
            assertThat(deps.getString("knockout")).isEqualTo("^3.5.1");
            assertThat(deps.getString("lodash")).isEqualTo("^4.17.21");
            assertThat(json.getJSONArray("workspaces").getString(0))
                    .as("unknown fields survive the round-trip").isEqualTo("packages/*");
            assertThat(outcomes).allSatisfy(o -> assertThat(o.changed()).isTrue());
        }

        @Test
        @DisplayName("A dependency the project already has is left untouched")
        void existingDependencyUntouched() throws Exception {
            Files.writeString(dir.resolve("package.json"), """
                { "name": "x", "dependencies": { "jquery": "~3.6.0" } }
                """);

            List<ClassicKit.Outcome> outcomes = ClassicKit.write(project(),
                    options(Set.of("jquery"), ClassicKit.Mode.NPM, Set.of()));

            JSONObject json = new JSONObject(Files.readString(dir.resolve("package.json")));
            assertThat(json.getJSONObject("dependencies").getString("jquery"))
                    .isEqualTo("~3.6.0");
            assertThat(outcomes.get(0).changed()).isFalse();
            assertThat(outcomes.get(0).status()).contains("already a dependency");
        }

        @Test
        @DisplayName("Prototype in npm mode is refused with the honest note")
        void prototypeRefusedHonestly() throws Exception {
            Files.writeString(dir.resolve("package.json"), "{ \"name\": \"x\" }");

            List<ClassicKit.Outcome> outcomes = ClassicKit.write(project(),
                    options(Set.of("prototype"), ClassicKit.Mode.NPM, Set.of()));

            assertThat(outcomes).singleElement().satisfies(o -> {
                assertThat(o.changed()).isFalse();
                assertThat(o.status()).contains(ClassicKit.PROTOTYPE_NPM_NOTE);
            });
        }

        @Test
        @DisplayName("No package.json: npm delivery is refused, pointing at Vendored")
        void refusedWithoutPackageJson() throws Exception {
            List<ClassicKit.Outcome> outcomes = ClassicKit.write(project(),
                    options(Set.of("jquery"), ClassicKit.Mode.NPM, Set.of()));

            assertThat(outcomes).singleElement().satisfies(o -> {
                assertThat(o.changed()).isFalse();
                assertThat(o.status()).isEqualTo("skipped (no package.json — use Vendored)");
            });
        }
    }

    // ---- generators ----

    @Nested
    class Generators {

        @Test
        @DisplayName("webpack.config.js detects the project's real entry, first hit wins")
        void webpackEntryAutoDetect() throws Exception {
            Files.createDirectories(dir.resolve("js"));
            Files.writeString(dir.resolve("js/app.js"), "// app");

            ClassicKit.write(project(), options(Set.of(),
                    ClassicKit.Mode.VENDORED, Set.of("webpack")));

            String config = Files.readString(dir.resolve("webpack.config.js"));
            assertThat(config).contains("entry: './js/app.js'")
                    .contains("// Entry: found in this project.")
                    .contains("filename: 'bundle.js'")
                    .contains("devServer");
        }

        @Test
        @DisplayName("src/index.js outranks js/app.js in entry detection")
        void entryDetectionPrecedence() throws Exception {
            Files.createDirectories(dir.resolve("src"));
            Files.createDirectories(dir.resolve("js"));
            Files.writeString(dir.resolve("src/index.js"), "// entry");
            Files.writeString(dir.resolve("js/app.js"), "// app");

            assertThat(ClassicKit.detectEntry(project())).isEqualTo("./src/index.js");
        }

        @Test
        @DisplayName("No conventional entry: the config says so instead of pretending")
        void webpackHonestWhenNoEntry() throws Exception {
            ClassicKit.write(project(), options(Set.of(),
                    ClassicKit.Mode.VENDORED, Set.of("webpack")));

            assertThat(Files.readString(dir.resolve("webpack.config.js")))
                    .contains("no conventional entry file found")
                    .contains("entry: './src/index.js'");
        }

        @Test
        @DisplayName("Gruntfile and gulpfile aim at the project's actual script directory")
        void taskfilesUseRealJsDir() throws Exception {
            Files.createDirectories(dir.resolve("src"));

            ClassicKit.write(project(), options(Set.of(),
                    ClassicKit.Mode.VENDORED, Set.of("grunt", "gulp")));

            assertThat(Files.readString(dir.resolve("Gruntfile.js")))
                    .contains("cwd: 'src'")
                    .contains("npm i -D grunt grunt-contrib-uglify grunt-contrib-watch")
                    .contains("grunt.registerTask('default', ['build'])");
            assertThat(Files.readString(dir.resolve("gulpfile.js")))
                    .contains("src('src/**/*.js')")
                    .contains("exports.build")
                    .contains("exports.watch");
        }

        @Test
        @DisplayName("An existing config is never clobbered — the proposal lands as .suggested")
        void neverClobbersExistingConfig() throws Exception {
            Files.writeString(dir.resolve("webpack.config.js"), "module.exports = {};");

            List<ClassicKit.Outcome> outcomes = ClassicKit.write(project(),
                    options(Set.of(), ClassicKit.Mode.VENDORED, Set.of("webpack")));

            assertThat(Files.readString(dir.resolve("webpack.config.js")))
                    .isEqualTo("module.exports = {};");
            assertThat(dir.resolve("webpack.config.js.suggested")).exists();
            assertThat(outcomes).singleElement().satisfies(o -> {
                assertThat(o.path()).isEqualTo("webpack.config.js.suggested");
                assertThat(o.status()).contains("existing webpack.config.js kept");
            });
        }

        @Test
        @DisplayName("The .suggested sibling itself is never overwritten either")
        void suggestedSiblingIsSacredToo() throws Exception {
            Files.writeString(dir.resolve("gulpfile.js"), "// mine");
            Files.writeString(dir.resolve("gulpfile.js.suggested"), "// my notes");

            List<ClassicKit.Outcome> outcomes = ClassicKit.write(project(),
                    options(Set.of(), ClassicKit.Mode.VENDORED, Set.of("gulp")));

            assertThat(Files.readString(dir.resolve("gulpfile.js.suggested")))
                    .isEqualTo("// my notes");
            assertThat(outcomes).singleElement().satisfies(o -> {
                assertThat(o.changed()).isFalse();
                assertThat(o.status()).contains("both exist");
            });
        }

        @Test
        @DisplayName("Re-running a generator over its own output is a no-op, not a .suggested")
        void generatorIdempotentOverOwnOutput() throws Exception {
            ClassicKit.Options opts = options(Set.of(),
                    ClassicKit.Mode.VENDORED, Set.of("webpack", "grunt", "gulp", "bower"));
            ClassicKit.write(project(), opts);

            List<ClassicKit.Outcome> second = ClassicKit.write(project(), opts);

            assertThat(second).hasSize(4).allSatisfy(o -> {
                assertThat(o.changed()).isFalse();
                assertThat(o.status()).isEqualTo("already exists, untouched");
            });
            assertThat(dir.resolve("webpack.config.js.suggested")).doesNotExist();
        }

        @Test
        @DisplayName("bower.json records the vendored libraries, name from the folder")
        void bowerRecordsVendoredLibraries() throws Exception {
            ClassicKit.write(project(), options(Set.of("jquery", "backbone"),
                    ClassicKit.Mode.VENDORED, Set.of("bower")));

            JSONObject bower = new JSONObject(Files.readString(dir.resolve("bower.json")));
            assertThat(bower.getString("name")).isEqualTo(project().getName());
            JSONObject deps = bower.getJSONObject("dependencies");
            assertThat(deps.getString("jquery")).isEqualTo("^3.7.1");
            assertThat(deps.getString("backbone")).isEqualTo("^1.6.0");
            assertThat(deps.getString("underscore")).isEqualTo("^1.13.7");
        }

        @Test
        @DisplayName("bower.json stays empty-handed when libraries go via npm")
        void bowerEmptyInNpmMode() throws Exception {
            Files.writeString(dir.resolve("package.json"), "{ \"name\": \"x\" }");

            ClassicKit.write(project(), options(Set.of("jquery"),
                    ClassicKit.Mode.NPM, Set.of("bower")));

            JSONObject bower = new JSONObject(Files.readString(dir.resolve("bower.json")));
            assertThat(bower.getJSONObject("dependencies").keySet()).isEmpty();
        }
    }

    // ---- planning & validation ----

    @Test
    @DisplayName("The delivery list keeps catalog order with Underscore slotted before Backbone")
    void deliveryListOrder() {
        List<ClassicKit.Lib> plan = ClassicKit.deliveryList(options(
                Set.of("knockout", "backbone", "jquery"), ClassicKit.Mode.VENDORED, Set.of()));

        assertThat(plan).extracting(ClassicKit.Lib::id)
                .containsExactly("jquery", "underscore", "backbone", "knockout");
    }

    @Test
    @DisplayName("validate: nothing selected is called out")
    void validateNothingSelected() {
        List<String> warnings = ClassicKit.validate(
                options(Set.of(), ClassicKit.Mode.VENDORED, Set.of()), project());

        assertThat(warnings).singleElement().satisfies(w
                -> assertThat(w).contains("Nothing selected"));
        assertThat(ClassicKit.hasWork(
                options(Set.of(), ClassicKit.Mode.VENDORED, Set.of()))).isFalse();
    }

    @Test
    @DisplayName("validate: npm delivery without a package.json is called out")
    void validateNpmWithoutPackageJson() {
        List<String> warnings = ClassicKit.validate(
                options(Set.of("jquery"), ClassicKit.Mode.NPM, Set.of()), project());

        assertThat(warnings).singleElement().satisfies(w
                -> assertThat(w).contains("No package.json").contains("use Vendored"));
    }

    @Test
    @DisplayName("validate: Prototype under npm gets the honest note")
    void validatePrototypeUnderNpm() throws Exception {
        Files.writeString(dir.resolve("package.json"), "{}");

        List<String> warnings = ClassicKit.validate(
                options(Set.of("prototype"), ClassicKit.Mode.NPM, Set.of()), project());

        assertThat(warnings).singleElement().satisfies(w
                -> assertThat(w).contains(ClassicKit.PROTOTYPE_NPM_NOTE));
    }

    @Test
    @DisplayName("validate: a sound vendored plan raises no warnings")
    void validateSoundPlan() {
        assertThat(ClassicKit.validate(options(Set.of("jquery"),
                ClassicKit.Mode.VENDORED, Set.of("webpack")), project())).isEmpty();
    }

    @Test
    @DisplayName("Options copies its sets: immutable to callers, insertion order kept")
    void optionsSetsAreImmutableCopies() {
        java.util.Set<String> libs = new java.util.LinkedHashSet<>(
                List.of("knockout", "jquery"));
        java.util.Set<String> gens = new java.util.LinkedHashSet<>(List.of("gulp", "webpack"));
        ClassicKit.Options opts = options(libs, ClassicKit.Mode.VENDORED, gens);

        assertThat(opts.libraries()).containsExactly("knockout", "jquery");
        assertThat(opts.generators()).containsExactly("gulp", "webpack");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> opts.libraries().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> opts.generators().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        // defensive copy: mutating the caller's sets changes nothing
        libs.clear();
        gens.clear();
        assertThat(opts.libraries()).hasSize(2);
        assertThat(opts.generators()).hasSize(2);
    }

    @Test
    @DisplayName("Only Prototype is npm-incapable; the catalog carries five libraries")
    void catalogShape() {
        assertThat(ClassicKit.libraries()).hasSize(7);
        assertThat(ClassicKit.libraries()).filteredOn(lib -> !lib.npmCapable())
                .extracting(ClassicKit.Lib::id).containsExactly("prototype");
        assertThat(ClassicKit.underscore().npmCapable()).isTrue();
        assertThat(ClassicKit.generatorIds())
                .containsExactly("webpack", "grunt", "gulp", "bower");
    }

    @Test
    @DisplayName("Every catalog literal script tag loads exactly its own vendor file")
    void scriptTagsMatchTheirFiles() {
        java.util.List<ClassicKit.Lib> all = new java.util.ArrayList<>(ClassicKit.libraries());
        all.add(ClassicKit.underscore());
        for (ClassicKit.Lib lib : all) {
            // the tag must load exactly its own vendor file; attributes
            // like Alpine's defer (required when injected into <head>) are
            // part of the library's correct spelling, not drift
            assertThat(lib.scriptTag())
                    .as(lib.id())
                    .startsWith("<script ")
                    .contains(" src=\"vendor/" + lib.vendorFile() + "\"")
                    .endsWith("></script>");
        }
    }

    @Test
    @DisplayName("A hostile project name cannot break out of the skeleton's markup")
    void hostileTitleIsEscaped() {
        String html = ClassicKit.skeleton("<script>alert(1)</script>\" onload=\"x",
                ClassicKit.deliveryList(options(Set.of("jquery"),
                        ClassicKit.Mode.VENDORED, Set.of())));

        assertThat(html).doesNotContain("<script>alert(1)</script>");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;&quot; onload=&quot;x");
        // the only script tag left is the catalog's own literal
        assertThat(html.split("<script", -1)).hasSize(2);
    }
}
