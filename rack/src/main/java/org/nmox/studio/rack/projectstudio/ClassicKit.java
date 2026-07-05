package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.json.JSONObject;

/**
 * The Classic Kit: extends an existing codebase with the classic web
 * stack — jQuery, MooTools, Prototype, Backbone (+Underscore),
 * Knockout — delivered either <em>vendored</em> (pinned minified builds
 * bundled with the IDE, copied into {@code vendor/} and script-tag
 * wired into index.html) or via <em>npm</em> (package.json
 * dependencies); plus never-clobber generators for the era's build
 * tooling: webpack.config.js, Gruntfile.js, gulpfile.js, bower.json.
 *
 * <p>Standards Kit shape: pure builders and planners so every claim is
 * testable, I/O at the edges, existing files never overwritten — a
 * generator whose target exists writes a {@code .suggested} sibling
 * instead. The bundled libraries live in {@code vendor/} next to this
 * class with their provenance in NOTICE-vendor.md. The wizard in the
 * File menu drives {@link #write}.</p>
 */
public final class ClassicKit {

    private ClassicKit() {
    }

    /** How a selected library reaches the project. */
    public enum Mode {
        /** Copy the bundled pinned build into vendor/ and wire a script tag. */
        VENDORED,
        /** Record the dependency in package.json (npm fetches it later). */
        NPM
    }

    /** What the wizard collected: library ids, delivery mode, generator ids. */
    public record Options(Set<String> libraries, Mode mode, Set<String> generators) {
    }

    /** One touched (or skipped) file, for the wizard's report. */
    public record Outcome(String path, String status, boolean changed) {
    }

    /**
     * One classic library the kit can deliver. {@code npmName == null}
     * means vendored-only (Prototype has no canonical npm package).
     * {@code scriptTag} is a compile-time literal, never assembled from
     * parts at runtime — markup construction from variables is exactly
     * what find-sec-bugs' XML-injection detector exists to catch, so
     * the catalog simply doesn't do it.
     */
    public record Lib(String id, String display, String version,
            String vendorFile, String scriptTag, String npmName, String npmRange) {

        public boolean npmCapable() {
            return npmName != null;
        }

        /** Checkbox-ready text: "jQuery 3.7.1". */
        public String label() {
            return display + " " + version;
        }
    }

    /** The honest reason Prototype's npm delivery stays grey. */
    public static final String PROTOTYPE_NPM_NOTE
            = "Prototype ships vendored only — no canonical npm package";

    private static final Lib JQUERY = new Lib("jquery", "jQuery", "3.7.1",
            "jquery-3.7.1.min.js",
            "<script src=\"vendor/jquery-3.7.1.min.js\"></script>",
            "jquery", "^3.7.1");
    private static final Lib MOOTOOLS = new Lib("mootools", "MooTools Core (compat)", "1.6.0",
            "mootools-core-1.6.0-compat.min.js",
            "<script src=\"vendor/mootools-core-1.6.0-compat.min.js\"></script>",
            "mootools", "^1.6.0");
    private static final Lib PROTOTYPE = new Lib("prototype", "Prototype", "1.7.3",
            "prototype-1.7.3.js",
            "<script src=\"vendor/prototype-1.7.3.js\"></script>",
            null, null);
    private static final Lib BACKBONE = new Lib("backbone", "Backbone", "1.6.0",
            "backbone-1.6.0.min.js",
            "<script src=\"vendor/backbone-1.6.0.min.js\"></script>",
            "backbone", "^1.6.0");
    private static final Lib KNOCKOUT = new Lib("knockout", "Knockout", "3.5.1",
            "knockout-3.5.1.js",
            "<script src=\"vendor/knockout-3.5.1.js\"></script>",
            "knockout", "^3.5.1");

    /** Not selectable on its own: Backbone's hard dependency, wired first. */
    private static final Lib UNDERSCORE = new Lib("underscore", "Underscore", "1.13.7",
            "underscore-1.13.7.min.js",
            "<script src=\"vendor/underscore-1.13.7.min.js\"></script>",
            "underscore", "^1.13.7");

    /** The selectable catalog, dialog order. */
    public static List<Lib> libraries() {
        return List.of(JQUERY, MOOTOOLS, PROTOTYPE, BACKBONE, KNOCKOUT);
    }

    /** Backbone's silent companion, for labels and tests. */
    public static Lib underscore() {
        return UNDERSCORE;
    }

    /** The generator ids the wizard offers, report order. */
    public static List<String> generatorIds() {
        return List.of("webpack", "grunt", "gulp", "bower");
    }

    // ---- planning (pure) ----

    /**
     * The libraries that will actually be delivered, in catalog order,
     * with Underscore slotted in front of Backbone — Backbone is
     * useless without it, and the script tag order matters.
     */
    static List<Lib> deliveryList(Options opts) {
        List<Lib> out = new ArrayList<>();
        for (Lib lib : libraries()) {
            if (!opts.libraries().contains(lib.id())) {
                continue;
            }
            if ("backbone".equals(lib.id())) {
                out.add(UNDERSCORE);
            }
            out.add(lib);
        }
        return out;
    }

    /** False when the wizard's OK would do nothing at all. */
    public static boolean hasWork(Options opts) {
        return !opts.libraries().isEmpty() || !opts.generators().isEmpty();
    }

    /**
     * The wizard's honesty gate: human-readable problems with this plan,
     * empty when it is sound. The dialog shows these and stops — the
     * write path repeats the same refusals per-file for API callers.
     */
    public static List<String> validate(Options opts, File projectDir) {
        List<String> warnings = new ArrayList<>();
        if (!hasWork(opts)) {
            warnings.add("Nothing selected — pick at least one library or generator.");
        }
        if (opts.mode() == Mode.NPM && !opts.libraries().isEmpty()
                && !new File(projectDir, "package.json").isFile()) {
            warnings.add("No package.json here — npm delivery has nowhere to record "
                    + "dependencies; use Vendored.");
        }
        if (opts.mode() == Mode.NPM && opts.libraries().contains("prototype")) {
            warnings.add(PROTOTYPE_NPM_NOTE + " — it will be skipped.");
        }
        return warnings;
    }

    // ---- the write path (I/O at the edges) ----

    /**
     * Applies the plan to the project: library delivery first (vendored
     * copy + script wiring, or package.json dependencies), then the
     * selected generators. Existing files are never overwritten.
     * Returns what happened, file by file.
     */
    public static List<Outcome> write(File projectDir, Options opts) throws IOException {
        List<Outcome> outcomes = new ArrayList<>();
        List<Lib> delivery = deliveryList(opts);
        if (!delivery.isEmpty()) {
            if (opts.mode() == Mode.VENDORED) {
                vendorLibraries(projectDir, delivery, outcomes);
            } else {
                npmLibraries(projectDir, delivery, outcomes);
            }
        }
        if (opts.generators().contains("webpack")) {
            String entry = detectEntry(projectDir);
            outcomes.add(writeGenerated(projectDir, "webpack.config.js",
                    webpackConfig(entry != null ? entry : "./src/index.js", entry != null)));
        }
        if (opts.generators().contains("grunt")) {
            outcomes.add(writeGenerated(projectDir, "Gruntfile.js",
                    gruntfile(detectJsDir(projectDir))));
        }
        if (opts.generators().contains("gulp")) {
            outcomes.add(writeGenerated(projectDir, "gulpfile.js",
                    gulpfile(detectJsDir(projectDir))));
        }
        if (opts.generators().contains("bower")) {
            // bower.json records the libraries only when they arrive as files
            // (vendored); npm-delivered deps belong to package.json alone
            List<Lib> recorded = opts.mode() == Mode.VENDORED ? delivery : List.of();
            outcomes.add(writeGenerated(projectDir, "bower.json",
                    bowerJson(projectDir.getName(), recorded)));
        }
        return outcomes;
    }

    // ---- vendored delivery ----

    private static void vendorLibraries(File projectDir, List<Lib> delivery,
            List<Outcome> outcomes) throws IOException {
        for (Lib lib : delivery) {
            String rel = "vendor/" + lib.vendorFile();
            File target = new File(projectDir, rel);
            if (target.exists()) {
                outcomes.add(new Outcome(rel, "already exists, untouched", false));
                continue;
            }
            Files.createDirectories(target.getParentFile().toPath());
            Files.write(target.toPath(), vendorBytes(lib.vendorFile()));
            outcomes.add(new Outcome(rel, "written", true));
        }
        outcomes.add(wireIndex(projectDir, delivery));
    }

    /**
     * The bundled pinned build, byte for byte — provenance and SHA-256
     * in NOTICE-vendor.md beside the files.
     */
    static byte[] vendorBytes(String file) throws IOException {
        try (InputStream in = ClassicKit.class.getResourceAsStream("vendor/" + file)) {
            if (in == null) {
                throw new IOException("Bundled library missing from the IDE: " + file);
            }
            return in.readAllBytes();
        }
    }

    private static Outcome wireIndex(File projectDir, List<Lib> delivery) throws IOException {
        File index = new File(projectDir, "index.html");
        if (!index.isFile()) {
            Files.writeString(index.toPath(), skeleton(projectDir.getName(), delivery),
                    StandardCharsets.UTF_8);
            return new Outcome("index.html", "written — page skeleton with the script tags", true);
        }
        String before = Files.readString(index.toPath(), StandardCharsets.UTF_8);
        String after = wireScripts(before, delivery);
        if (after.equals(before)) {
            boolean allPresent = delivery.stream()
                    .allMatch(lib -> before.contains(lib.vendorFile()));
            return new Outcome("index.html", allPresent
                    ? "already wired"
                    : "skipped — no </head> to wire into", false);
        }
        Files.writeString(index.toPath(), after, StandardCharsets.UTF_8);
        return new Outcome("index.html", "wired", true);
    }

    /**
     * Script tags for every delivered library the page does not already
     * load, inserted before {@code </head>} via the shared
     * {@link HtmlWiring} seam. Delivery order is preserved, so
     * Underscore's tag lands ahead of Backbone's. Running it twice
     * changes nothing. The tags themselves are catalog literals — no
     * markup is assembled from variables here.
     */
    static String wireScripts(String html, List<Lib> delivery) {
        StringBuilder add = new StringBuilder();
        for (Lib lib : delivery) {
            if (!html.contains(lib.vendorFile())) {
                add.append("  ").append(lib.scriptTag()).append('\n');
            }
        }
        return HtmlWiring.insertBeforeHeadClose(html, add.toString());
    }

    /**
     * The page created when the project has no index.html: an old-school
     * skeleton — header/content divs, scripts loaded from plain tags in
     * the head, no modules, no app shell. The markup is one constant;
     * dynamic data enters only through explicit escaped substitution
     * (the anti-injection idiom, proven by the hostile-title test).
     */
    private static final String SKELETON_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <title>__TITLE__</title>
            __SCRIPTS__</head>
            <body>
              <div id="header">
                <h1>__TITLE__</h1>
              </div>
              <div id="content">
                <p>Generated by NMOX Studio's Classic Kit.</p>
              </div>
            </body>
            </html>
            """;

    static String skeleton(String title, List<Lib> delivery) {
        StringBuilder scripts = new StringBuilder();
        for (Lib lib : delivery) {
            scripts.append("  ").append(lib.scriptTag()).append('\n');
        }
        return SKELETON_TEMPLATE
                .replace("__TITLE__", PwaKit.escapeHtml(title))
                .replace("__SCRIPTS__", scripts);
    }

    // ---- npm delivery ----

    private static void npmLibraries(File projectDir, List<Lib> delivery,
            List<Outcome> outcomes) throws IOException {
        File pkg = new File(projectDir, "package.json");
        if (!pkg.isFile()) {
            outcomes.add(new Outcome("package.json",
                    "skipped (no package.json — use Vendored)", false));
            return;
        }
        JSONObject json;
        try {
            json = new JSONObject(Files.readString(pkg.toPath(), StandardCharsets.UTF_8));
        } catch (RuntimeException bad) {
            throw new IOException("Malformed package.json: " + bad.getMessage(), bad);
        }
        JSONObject deps = json.optJSONObject("dependencies");
        JSONObject devDeps = json.optJSONObject("devDependencies");
        boolean changed = false;
        for (Lib lib : delivery) {
            if (!lib.npmCapable()) {
                outcomes.add(new Outcome("package.json",
                        lib.display() + ": " + PROTOTYPE_NPM_NOTE, false));
                continue;
            }
            if ((deps != null && deps.has(lib.npmName()))
                    || (devDeps != null && devDeps.has(lib.npmName()))) {
                outcomes.add(new Outcome("package.json",
                        lib.npmName() + " already a dependency, untouched", false));
                continue;
            }
            if (deps == null) {
                deps = new JSONObject();
                json.put("dependencies", deps);
            }
            deps.put(lib.npmName(), lib.npmRange());
            changed = true;
            outcomes.add(new Outcome("package.json",
                    lib.npmName() + " " + lib.npmRange() + " added to dependencies", true));
        }
        if (changed) {
            // the package.json editor's idiom: org.json round-trip, two-space
            // indent — fields the kit doesn't know about survive untouched
            Files.writeString(pkg.toPath(), json.toString(2) + "\n", StandardCharsets.UTF_8);
        }
    }

    // ---- generators (pure builders) ----

    /**
     * The project's real entry point, checked at the era's conventional
     * spots in order — or null when none exists (the config then says
     * so in a comment instead of pretending).
     */
    static String detectEntry(File projectDir) {
        for (String candidate : new String[]{
            "src/index.js", "js/app.js", "js/main.js", "index.js"}) {
            if (new File(projectDir, candidate).isFile()) {
                return "./" + candidate;
            }
        }
        return null;
    }

    /** The project's actual script directory: js/, then src/, else js. */
    static String detectJsDir(File projectDir) {
        for (String candidate : new String[]{"js", "src"}) {
            if (new File(projectDir, candidate).isDirectory()) {
                return candidate;
            }
        }
        return "js";
    }

    /**
     * webpack.config.js, written to be read: detected entry (or an
     * honest comment when detection found nothing), one bundle in
     * dist/, mode from the CLI, dev server hosting the project root.
     */
    static String webpackConfig(String entry, boolean detected) {
        String entryComment = detected
                ? "// Entry: found in this project."
                : "// Entry: no conventional entry file found — adjust this to your real one.";
        return """
                // webpack.config.js — generated by NMOX Studio's Classic Kit. Edit freely.
                const path = require('path');

                module.exports = (env, argv) => ({
                  %s
                  entry: '%s',

                  // One bundle: dist/bundle.js — point your script tag there.
                  output: {
                    path: path.resolve(__dirname, 'dist'),
                    filename: 'bundle.js',
                  },

                  // `webpack --mode production` minifies; development keeps builds
                  // fast and debuggable. No flag defaults to development.
                  mode: argv && argv.mode ? argv.mode : 'development',

                  // `webpack serve` hosts the project root, so the plain
                  // index.html keeps working while the bundle rebuilds on save.
                  devServer: {
                    static: { directory: __dirname },
                  },
                });
                """.formatted(entryComment, entry);
    }

    /**
     * Gruntfile.js: uglify the project's script directory into dist/,
     * watch to re-run on save, build/default task aliases.
     */
    static String gruntfile(String jsDir) {
        return """
                // Gruntfile.js — generated by NMOX Studio's Classic Kit. Edit freely.
                // Task dependencies: npm i -D grunt grunt-contrib-uglify grunt-contrib-watch
                module.exports = function (grunt) {
                  grunt.initConfig({
                    // Minify every script in %1$s/ into dist/, one .min.js each.
                    uglify: {
                      build: {
                        files: [{
                          expand: true,
                          cwd: '%1$s',
                          src: ['**/*.js', '!**/*.min.js'],
                          dest: 'dist',
                          ext: '.min.js',
                        }],
                      },
                    },

                    // Rebuild on save.
                    watch: {
                      scripts: {
                        files: ['%1$s/**/*.js'],
                        tasks: ['uglify'],
                      },
                    },
                  });

                  grunt.loadNpmTasks('grunt-contrib-uglify');
                  grunt.loadNpmTasks('grunt-contrib-watch');

                  grunt.registerTask('build', ['uglify']);
                  grunt.registerTask('default', ['build']);
                };
                """.formatted(jsDir);
    }

    /**
     * gulpfile.js in gulp 4 style — named exports, series for the watch
     * loop — same build shape as the Gruntfile.
     */
    static String gulpfile(String jsDir) {
        return """
                // gulpfile.js — generated by NMOX Studio's Classic Kit. Edit freely.
                // Task dependencies: npm i -D gulp gulp-uglify
                const { src, dest, watch, series } = require('gulp');
                const uglify = require('gulp-uglify');

                // Minify every script in %1$s/ into dist/.
                function build() {
                  return src('%1$s/**/*.js')
                    .pipe(uglify())
                    .pipe(dest('dist'));
                }

                exports.build = build;

                // Rebuild on save.
                exports.watch = function () {
                  watch('%1$s/**/*.js', series(build));
                };

                exports.default = build;
                """.formatted(jsDir);
    }

    /**
     * bower.json: name from the project folder, dependencies recording
     * whatever libraries this run delivers vendored — so bower-era
     * projects keep an honest ledger of what they load.
     */
    static String bowerJson(String name, List<Lib> vendored) {
        JSONObject bower = new JSONObject();
        bower.put("name", name);
        bower.put("private", true);
        JSONObject deps = new JSONObject();
        for (Lib lib : vendored) {
            deps.put(lib.id(), "^" + lib.version());
        }
        bower.put("dependencies", deps);
        return bower.toString(2) + "\n";
    }

    /**
     * The never-clobber write (the Standards Kit rule, extended for
     * config files people edit): fresh target → written; existing and
     * byte-identical → untouched; existing and different → a
     * {@code .suggested} sibling carries the proposal — unless a
     * suggestion already sits there, which is never overwritten either.
     */
    private static Outcome writeGenerated(File dir, String name, String content)
            throws IOException {
        File target = new File(dir, name);
        if (!target.exists()) {
            Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
            return new Outcome(name, "written", true);
        }
        if (content.equals(Files.readString(target.toPath(), StandardCharsets.UTF_8))) {
            return new Outcome(name, "already exists, untouched", false);
        }
        File suggested = new File(dir, name + ".suggested");
        if (suggested.exists()) {
            return new Outcome(name,
                    "skipped — " + name + " and " + name + ".suggested both exist", false);
        }
        Files.writeString(suggested.toPath(), content, StandardCharsets.UTF_8);
        return new Outcome(name + ".suggested",
                "existing " + name + " kept — suggestion written alongside", true);
    }
}
