package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.devices.ProjectInspector;
import org.nmox.studio.rack.model.RackIO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every template must produce a complete project: parseable
 * package.json with the right name, the standard housekeeping files,
 * and a rack patch that actually mounts devices and cables.
 */
class ProjectTemplatesTest {

    @TempDir
    Path parent;

    @Test
    @DisplayName("Should generate a complete wired project from every template")
    void shouldGenerateEveryTemplate() throws Exception {
        for (ProjectTemplates template : ProjectTemplates.values()) {
            File dir = parent.resolve(template.name().toLowerCase()).toFile();

            template.generate(dir, "demo-app");

            // a recognized project manifest exists; Node templates carry
            // the chosen name in package.json
            assertThat(ProjectInspector.hasProjectManifest(dir))
                    .as(template + " has a project manifest").isTrue();
            Path pkg = dir.toPath().resolve("package.json");
            JSONObject json = Files.exists(pkg)
                    ? new JSONObject(Files.readString(pkg)) : null;
            if (json != null) {
                assertThat(json.getString("name")).isEqualTo("demo-app");
                assertThat(json.getJSONObject("scripts").keySet()).isNotEmpty();
            }

            // housekeeping + editor config land in every template
            assertThat(dir.toPath().resolve(".gitignore")).exists();
            assertThat(dir.toPath().resolve("README.md")).exists();
            assertThat(dir.toPath().resolve(".editorconfig")).exists();
            if (template.lintable()) {
                assertThat(dir.toPath().resolve("eslint.config.mjs"))
                        .as(template + " eslint config").exists();
                assertThat(json).as(template + " lintable implies package.json").isNotNull();
                assertThat(json.getJSONObject("devDependencies").has("eslint"))
                        .as(template + " eslint dependency").isTrue();
            }

            // the infra patch mounts: devices present, cables patched
            Path patch = dir.toPath().resolve(RackIO.DEFAULT_FILENAME);
            assertThat(patch).as(template + " rack patch").exists();
            Rack rack = new Rack();
            rack.setProjectDir(dir);
            try {
                RackIO.fromJson(rack, new JSONObject(Files.readString(patch)));
                assertThat(rack.getDevices()).as(template + " devices").isNotEmpty();
                assertThat(rack.getCables()).as(template + " cables").isNotEmpty();
            } finally {
                rack.shutdown();
            }
        }
    }

    @Test
    @DisplayName("PHP Web (LEMP) generates the full composer + compose + droplet stack")
    void phpWebLempTemplate() throws Exception {
        File dir = parent.resolve("php-web").toFile();

        ProjectTemplates.PHP_WEB.generate(dir, "demo-app");

        // composer.json parses: dev tooling, PSR-4 autoload, the scripts lane
        JSONObject composer = new JSONObject(
                Files.readString(dir.toPath().resolve("composer.json")));
        assertThat(composer.getString("name")).isEqualTo("app/demo-app");
        assertThat(composer.getJSONObject("require-dev").keySet())
                .contains("phpunit/phpunit", "phpstan/phpstan", "laravel/pint");
        assertThat(composer.getJSONObject("autoload").getJSONObject("psr-4")
                .getString("App\\")).isEqualTo("src/");
        assertThat(composer.getJSONObject("scripts").keySet())
                .contains("test", "analyse", "fmt");

        // the front controller guards the autoload and answers the health route
        String front = Files.readString(dir.toPath().resolve("public/index.php"));
        assertThat(front).contains("declare(strict_types=1)")
                .contains("vendor/autoload.php").contains("/api/health");

        // compose runs all three LEMP services; nginx hands PHP to the fpm box
        String compose = Files.readString(dir.toPath().resolve("docker-compose.yml"));
        assertThat(compose).contains("nginx").contains("php:8.4-fpm").contains("mariadb:11");
        assertThat(Files.readString(dir.toPath().resolve("docker/nginx.conf")))
                .contains("root /var/www/html/public").contains("fastcgi_pass php:9000");

        // the droplet bootstrap installs the whole stack
        String cloudInit = Files.readString(dir.toPath().resolve("deploy/cloud-init.yml"));
        assertThat(cloudInit).startsWith("#cloud-config")
                .contains("nginx").contains("mariadb-server").contains("php-fpm");

        // PHPUnit boots the autoloader; a real class + test pair exists
        assertThat(Files.readString(dir.toPath().resolve("phpunit.xml")))
                .contains("vendor/autoload.php").contains("tests");
        assertThat(dir.toPath().resolve("src/Greeting.php")).exists();
        assertThat(dir.toPath().resolve("tests/GreetingTest.php")).exists();
        assertThat(dir.toPath().resolve(".env.example")).exists();
        assertThat(Files.readString(dir.toPath().resolve(".gitignore")))
                .contains("vendor/").contains(".env");
    }

    @Test
    @DisplayName("Classic Web (jQuery) is the script-tag era: vendored jQuery, no package.json")
    void classicWebJqueryTemplate() throws Exception {
        File dir = parent.resolve("classic-web").toFile();

        ProjectTemplates.CLASSIC_WEB_JQUERY.generate(dir, "retro-site");

        // the era-honest file set — and deliberately NO Node toolchain
        assertThat(dir.toPath().resolve("css/style.css")).exists();
        assertThat(dir.toPath().resolve("js/app.js")).exists();
        assertThat(dir.toPath().resolve("package.json")).doesNotExist();
        assertThat(dir.toPath().resolve("eslint.config.mjs")).doesNotExist();

        // index.html loads jQuery from a plain script tag, header/content
        // divs, no modules
        String html = Files.readString(dir.toPath().resolve("index.html"));
        assertThat(html).contains("<script src=\"vendor/jquery-3.7.1.min.js\"></script>")
                .contains("<div id=\"header\">").contains("<div id=\"content\">")
                .doesNotContain("type=\"module\"");
        assertThat(Files.readString(dir.toPath().resolve("js/app.js")))
                .contains("$(document).ready");

        // the vendored build is byte-equal to the bundled pinned resource
        assertThat(Files.readAllBytes(dir.toPath().resolve("vendor/jquery-3.7.1.min.js")))
                .isEqualTo(ClassicKit.vendorBytes("jquery-3.7.1.min.js"));

        // vendor/ is committed on purpose: the .gitignore must not eat it
        assertThat(Files.readString(dir.toPath().resolve(".gitignore")))
                .doesNotContain("vendor/");

        // the patch parses and is the Classic Web Bench wiring: same device
        // roster (CRATE → DYNAMO → static IGNITION, VITALS, MONITOR), same cabling
        JSONObject patch = new JSONObject(Files.readString(
                dir.toPath().resolve(RackIO.DEFAULT_FILENAME)));
        JSONObject bench = RackPresets.CLASSIC_WEB.buildPatch();
        assertThat(deviceTypes(patch)).isEqualTo(deviceTypes(bench));
        assertThat(patch.getJSONArray("cables").length())
                .isEqualTo(bench.getJSONArray("cables").length());

        // no manifest, but it still opens: the STATIC last resort
        assertThat(ProjectInspector.detectKind(dir))
                .isEqualTo(ProjectInspector.ProjectKind.STATIC);
    }

    @Test
    @DisplayName("Classic Web (MooTools) is the Class-based classic: vendored compat build, no package.json")
    void classicWebMootoolsTemplate() throws Exception {
        File dir = parent.resolve("classic-moo").toFile();

        ProjectTemplates.CLASSIC_WEB_MOOTOOLS.generate(dir, "retro-oo");

        // the era-honest file set — and deliberately NO Node toolchain
        assertThat(dir.toPath().resolve("css/style.css")).exists();
        assertThat(dir.toPath().resolve("package.json")).doesNotExist();
        assertThat(dir.toPath().resolve("eslint.config.mjs")).doesNotExist();

        // index.html loads the compat build from a plain script tag; app.js
        // opens with the MooTools signature — a real Class, wired on domready
        String html = Files.readString(dir.toPath().resolve("index.html"));
        assertThat(html)
                .contains("<script src=\"vendor/mootools-core-1.6.0-compat.min.js\"></script>")
                .doesNotContain("type=\"module\"");
        String app = Files.readString(dir.toPath().resolve("js/app.js"));
        assertThat(app).contains("new Class(")
                .contains("window.addEvent('domready'");

        // the vendored build is byte-equal to the bundled pinned resource
        assertThat(Files.readAllBytes(
                dir.toPath().resolve("vendor/mootools-core-1.6.0-compat.min.js")))
                .isEqualTo(ClassicKit.vendorBytes("mootools-core-1.6.0-compat.min.js"));

        // vendor/ is committed on purpose: the .gitignore must not eat it
        assertThat(Files.readString(dir.toPath().resolve(".gitignore")))
                .doesNotContain("vendor/");

        // same script-tag-era wiring as the jQuery template - one definition
        JSONObject patch = new JSONObject(Files.readString(
                dir.toPath().resolve(RackIO.DEFAULT_FILENAME)));
        JSONObject bench = RackPresets.CLASSIC_WEB.buildPatch();
        assertThat(deviceTypes(patch)).isEqualTo(deviceTypes(bench));

        // no manifest, but it still opens: the STATIC last resort
        assertThat(ProjectInspector.detectKind(dir))
                .isEqualTo(ProjectInspector.ProjectKind.STATIC);
    }

    @Test
    @DisplayName("Vite + Svelte is Svelte 5: runes in App.svelte, mount() in main.js")
    void viteSvelteIsSvelte5() throws Exception {
        File dir = parent.resolve("vite-svelte").toFile();

        ProjectTemplates.VITE_SVELTE.generate(dir, "demo-app");

        // package.json parses and pins the Svelte 5 toolchain: svelte ^5
        // with the vite-plugin-svelte major that peers on Vite 8 (7, which
        // also wants svelte >=5.46.4 — the whole set proven by npm install,
        // build and test on Node 22.23.5, 2026-11-13; the template's vite
        // major matches the other three Vite templates')
        JSONObject pkg = new JSONObject(
                Files.readString(dir.toPath().resolve("package.json")));
        JSONObject dev = pkg.getJSONObject("devDependencies");
        assertThat(dev.getString("svelte")).startsWith("^5.");
        assertThat(dev.getString("@sveltejs/vite-plugin-svelte")).startsWith("^7.");
        assertThat(dev.getString("vite")).startsWith("^8.");
        assertThat(pkg.getJSONObject("scripts").getString("dev")).isEqualTo("vite");

        // the component speaks runes, not the Svelte-4 idiom
        String app = Files.readString(dir.toPath().resolve("src/App.svelte"));
        assertThat(app).contains("let count = $state(0);");
        assertThat(app).contains("onclick=");
        assertThat(app).doesNotContain("on:click");

        // Svelte 5 mounts via mount(), not `new App(...)` (removed in 5)
        String main = Files.readString(dir.toPath().resolve("src/main.js"));
        assertThat(main).contains("import { mount } from 'svelte';");
        assertThat(main).contains("mount(App, { target:");
        assertThat(main).doesNotContain("new App(");

        // the vite config wires the svelte plugin
        assertThat(Files.readString(dir.toPath().resolve("vite.config.js")))
                .contains("@sveltejs/vite-plugin-svelte");
    }

    @Test
    @DisplayName("The Vite templates obey the ceilings: no CRA, vite ^8, vitest ^5, proven plugin majors")
    void viteTemplatesObeyTheCeilings() throws Exception {
        // Ported from the deleted platform wizard's WizardTemplateCeilingTest
        // (v1.246.0): this is now the ONE wizard, so the ceilings it enforced
        // live here. react-scripts is dead upstream and cannot install beside
        // react 19 (the v1.244.0 find). vite is ^8 and vitest ^5, the lines
        // measured and proven on 2026-11-13 (ledger 140): vite 8.3.8 declares
        // node ^20.19.0 || >=22.12.0 and vitest 5.1.0 declares
        // ^22.12.0 || ^24.0.0 || >=26.0.0, both met by the current patch of
        // every Node line still in support (22.23.5 that day), and each
        // template installed, built and tested green on that Node. The pins
        // are Java string literals — invisible to Dependabot (v1.236.0
        // pattern), so this test is the only thing that fails a bump PR: a
        // new major here needs a new proof and a new floor at the pins.
        java.util.List<String> onVite = new java.util.ArrayList<>();
        for (ProjectTemplates template : ProjectTemplates.values()) {
            File dir = parent.resolve("ceiling-" + template.name().toLowerCase()).toFile();
            template.generate(dir, "demo-app");
            Path pkgPath = dir.toPath().resolve("package.json");
            if (!Files.exists(pkgPath)) {
                continue;
            }
            JSONObject pkg = new JSONObject(Files.readString(pkgPath));
            for (String section : new String[]{"dependencies", "devDependencies"}) {
                JSONObject deps = pkg.optJSONObject(section);
                if (deps != null) {
                    assertThat(deps.has("react-scripts"))
                            .as(template + " " + section + " must never carry react-scripts").isFalse();
                    if (deps.has("vite")) {
                        onVite.add(template.name());
                        assertThat(deps.getString("vite"))
                                .as(template + " vite ceiling").startsWith("^8.");
                    }
                    if (deps.has("vitest")) {
                        assertThat(deps.getString("vitest"))
                                .as(template + " vitest ceiling").startsWith("^5.");
                    }
                }
            }
            if (pkg.optJSONObject("devDependencies") != null
                    && pkg.getJSONObject("devDependencies").has("vite")) {
                // the floor is said where a newcomer will read it
                assertThat(Files.readString(dir.toPath().resolve("README.md")))
                        .as(template + " README names the Node floor").contains("needs Node 22.12");
            }
        }
        // the population is the four Vite templates: a fifth joins the law
        // by carrying vite, and losing one would silently shrink it
        assertThat(onVite).containsExactlyInAnyOrder(
                "VITE_REACT", "VITE_VUE", "VITE_SOLID", "VITE_SVELTE");

        // the plugin majors are the ones that peer on Vite 8 (same proof)
        JSONObject react = new JSONObject(Files.readString(
                parent.resolve("ceiling-vite_react").resolve("package.json")));
        assertThat(react.getJSONObject("dependencies").getString("react")).startsWith("^19.");
        assertThat(react.getJSONObject("devDependencies").getString("@vitejs/plugin-react"))
                .startsWith("^6.");
        assertThat(react.getJSONObject("scripts").getString("dev")).isEqualTo("vite");

        JSONObject vue = new JSONObject(Files.readString(
                parent.resolve("ceiling-vite_vue").resolve("package.json")));
        assertThat(vue.getJSONObject("dependencies").getString("vue")).startsWith("^3.");
        JSONObject vueDev = vue.getJSONObject("devDependencies");
        assertThat(vueDev.getString("@vitejs/plugin-vue")).startsWith("^6.");
        // Vue's language server needs a TypeScript that still ships
        // tsserverlibrary.js; 7 (the Go port) does not. The template
        // declares no typescript today — if one arrives it stays below 7.
        assertThat(vueDev.optString("typescript", "^6.0.0"))
                .as("VITE_VUE typescript ceiling").doesNotStartWith("^7").doesNotStartWith("~7");

        JSONObject solid = new JSONObject(Files.readString(
                parent.resolve("ceiling-vite_solid").resolve("package.json")));
        assertThat(solid.getJSONObject("devDependencies").getString("vite-plugin-solid"))
                .startsWith("^2.11");
    }

    @Test
    @DisplayName("Vite + Solid's test script runs without a DOM package")
    void viteSolidTestsRunWithoutADom() throws Exception {
        // Measured 2026-11-13 on the old pins and the new: vite-plugin-solid
        // sets Vitest's environment to jsdom unless the config names one,
        // the starter installs no jsdom, and `npm test` exited 1 with
        // "Cannot find package 'jsdom'". Either the config names node or
        // the package is declared; anything else is a test script that
        // cannot run.
        File dir = parent.resolve("solid-test-env").toFile();
        ProjectTemplates.VITE_SOLID.generate(dir, "demo-app");

        String config = Files.readString(dir.toPath().resolve("vite.config.js"));
        JSONObject dev = new JSONObject(Files.readString(dir.toPath().resolve("package.json")))
                .getJSONObject("devDependencies");
        assertThat(config.contains("test: { environment: 'node' }") || dev.has("jsdom"))
                .as("vitest must not be pointed at a jsdom nobody installed").isTrue();
    }

    @Test
    @DisplayName("TypeScript Library pins ^7 and builds to dist/ root with tests run once")
    void tsLibraryBuildsWhereItsManifestPoints() throws Exception {
        File dir = parent.resolve("ts-lib-layout").toFile();
        ProjectTemplates.TS_LIBRARY.generate(dir, "demo-lib");

        JSONObject pkg = new JSONObject(Files.readString(dir.toPath().resolve("package.json")));
        // TypeScript 7 is served by `tsc --lsp` since 3.10.0, and nothing
        // in this template needs tsserverlibrary.js
        assertThat(pkg.getJSONObject("devDependencies").getString("typescript")).startsWith("^7.");
        assertThat(pkg.getString("main")).isEqualTo("dist/index.js");

        // TypeScript 6 and 7 refuse an outDir without a rootDir (TS5011,
        // measured: `tsc` exit 2, and what it did emit went to dist/src/,
        // not where "main" points)
        JSONObject options = new JSONObject(Files.readString(dir.toPath().resolve("tsconfig.json")))
                .getJSONObject("compilerOptions");
        assertThat(options.getString("rootDir")).isEqualTo("src");
        assertThat(options.getString("outDir")).isEqualTo("dist");

        // Vitest 4+ no longer skips dist/ by itself: without the include,
        // `npm test` after a build ran every test twice (measured: 2 files)
        assertThat(Files.readString(dir.toPath().resolve("vitest.config.ts")))
                .contains("include: ['src/**/*.test.ts']");
    }

    @Test
    @DisplayName("Angular pins the proven ~22.2 + TS ~6.0 line, a test script that runs, and the suffixed naming")
    void angularObeysItsCeilings() throws Exception {
        File dir = parent.resolve("ng-ceilings").toFile();

        ProjectTemplates.ANGULAR.generate(dir, "demo-ng");

        // Angular is ~22.2 with TypeScript ~6.0 (proven 2026-11-13 on Node
        // 22.23.5: npm install, ng build, ng test, ng generate). The
        // TypeScript ceiling is Angular's own: @angular/build and
        // @angular/compiler-cli 22 peer on typescript >=6.0 <6.1, and the
        // Angular Language Service needs tsserverlibrary.js, which
        // TypeScript 7 does not ship. A tilde keeps npm inside 6.0.
        JSONObject pkg = new JSONObject(
                Files.readString(dir.toPath().resolve("package.json")));
        assertThat(pkg.getJSONObject("dependencies").getString("@angular/core"))
                .startsWith("~22.2");
        JSONObject dev = pkg.getJSONObject("devDependencies");
        assertThat(dev.getString("@angular/cli")).startsWith("~22.2");
        assertThat(dev.getString("@angular/build")).startsWith("~22.2");
        assertThat(dev.getString("typescript")).startsWith("~6.0");

        // `ng test` is the unit-test builder: it needs vitest, a DOM and
        // a spec tsconfig that names vitest's globals. The template
        // shipped none of the three before 2026-11-13 and its test script
        // exited 1 (measured on the ~21.2 pins too).
        assertThat(pkg.getJSONObject("scripts").getString("test")).isEqualTo("ng test");
        assertThat(dev.getString("vitest")).startsWith("^5.");
        assertThat(dev.getString("jsdom")).startsWith("^30.");
        assertThat(Files.readString(dir.toPath().resolve("tsconfig.spec.json")))
                .contains("\"vitest/globals\"").contains("src/**/*.spec.ts");

        // Angular 22's CLI refuses a Node below its floor (exit 3); the
        // README says the floor the registry declares
        assertThat(Files.readString(dir.toPath().resolve("README.md")))
                .contains("needs Node 22.22.3+, 24.15+, or 26+");

        // the workspace teaches the suffixed naming the IDE's template
        // intelligence keys on (v1.217.0 resolver: *.component.html) —
        // and pins ng generate to keep producing it
        String ngJson = Files.readString(dir.toPath().resolve("angular.json"));
        assertThat(ngJson).contains("\"@schematics/angular:component\"");
        assertThat(ngJson).contains("\"type\": \"component\"");
        assertThat(dir.toPath().resolve("src/app/app.component.html")).exists();
        assertThat(dir.toPath().resolve("src/app/app.component.ts")).exists();
        assertThat(Files.readString(dir.toPath().resolve("src/main.ts")))
                .contains("./app/app.component");
    }

    @Test
    @DisplayName("Should refuse to generate into a non-empty directory")
    void shouldRefuseNonEmptyDirectory() throws Exception {
        File dir = parent.resolve("occupied").toFile();
        assertThat(dir.mkdirs()).isTrue();
        Files.writeString(dir.toPath().resolve("existing.txt"), "data");

        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                () -> ProjectTemplates.VANILLA.generate(dir, "demo"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("A new project starts versioned: git init + first commit")
    void newProjectStartsVersioned() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(gitAvailable(), "git not installed");
        java.io.File dir = parent.resolve("versioned-app").toFile();
        ProjectTemplates.values()[0].generate(dir, "versioned-app");

        ProjectTemplates.initGitRepo(dir);

        org.assertj.core.api.Assertions.assertThat(new java.io.File(dir, ".git"))
                .as("repo must exist").isDirectory();
        Process log = new ProcessBuilder("git", "-C", dir.getAbsolutePath(), "log", "--oneline")
                .redirectErrorStream(true).start();
        String out = new String(log.getInputStream().readAllBytes());
        log.waitFor();
        org.assertj.core.api.Assertions.assertThat(out)
                .as("the first commit").contains("Initial commit");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("The install's lockfile joins the scaffold commit: one commit, clean tree")
    void lockfileJoinsTheInitialCommit() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(gitAvailable(), "git not installed");
        java.io.File dir = scaffoldedRepo("locked-app");
        Files.writeString(dir.toPath().resolve("package-lock.json"), "{\"lockfileVersion\": 3}");
        org.assertj.core.api.Assertions.assertThat(git(dir, "status", "--porcelain"))
                .as("the walk's find: the lockfile starts untracked").contains("?? package-lock.json");

        org.assertj.core.api.Assertions.assertThat(ProjectTemplates.foldLockfileIntoInitialCommit(dir))
                .as("amended").isTrue();

        org.assertj.core.api.Assertions.assertThat(git(dir, "status", "--porcelain"))
                .as("first git status is clean").isBlank();
        org.assertj.core.api.Assertions.assertThat(git(dir, "log", "--format=%s").strip())
                .as("still exactly one commit, ours").isEqualTo(ProjectTemplates.INITIAL_COMMIT);
        org.assertj.core.api.Assertions.assertThat(git(dir, "ls-tree", "--name-only", "HEAD"))
                .as("the lockfile is in the commit").contains("package-lock.json");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("The fold refuses when anything but a fresh lockfile changed")
    void foldRefusesWhenTheUserTouchedTheTree() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(gitAvailable(), "git not installed");
        java.io.File dir = scaffoldedRepo("edited-app");
        String head = git(dir, "rev-parse", "HEAD");
        Files.writeString(dir.toPath().resolve("package-lock.json"), "{}");
        Files.writeString(dir.toPath().resolve("README.md"), "# mine now\n");

        org.assertj.core.api.Assertions.assertThat(ProjectTemplates.foldLockfileIntoInitialCommit(dir))
                .as("an edit the user made while the install ran is never swept into our commit")
                .isFalse();

        org.assertj.core.api.Assertions.assertThat(git(dir, "rev-parse", "HEAD")).isEqualTo(head);
        org.assertj.core.api.Assertions.assertThat(git(dir, "status", "--porcelain"))
                .contains("?? package-lock.json").contains("M README.md");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("The fold refuses past the scaffold commit, with a remote, and without a lockfile")
    void foldRefusesOutsideItsExactShape() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(gitAvailable(), "git not installed");
        // no lockfile at all: nothing to fold
        java.io.File bare = scaffoldedRepo("bare-app");
        org.assertj.core.api.Assertions.assertThat(ProjectTemplates.foldLockfileIntoInitialCommit(bare)).isFalse();

        // a second commit: history is the user's now
        java.io.File committed = scaffoldedRepo("committed-app");
        Files.writeString(committed.toPath().resolve("notes.txt"), "x");
        git(committed, "add", "notes.txt");
        git(committed, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-m", "user work");
        Files.writeString(committed.toPath().resolve("package-lock.json"), "{}");
        String head = git(committed, "rev-parse", "HEAD");
        org.assertj.core.api.Assertions.assertThat(ProjectTemplates.foldLockfileIntoInitialCommit(committed)).isFalse();
        org.assertj.core.api.Assertions.assertThat(git(committed, "rev-parse", "HEAD")).isEqualTo(head);

        // a remote: the commit may have left the machine
        java.io.File pushed = scaffoldedRepo("pushed-app");
        git(pushed, "remote", "add", "origin", parent.resolve("nowhere.git").toString());
        Files.writeString(pushed.toPath().resolve("package-lock.json"), "{}");
        head = git(pushed, "rev-parse", "HEAD");
        org.assertj.core.api.Assertions.assertThat(ProjectTemplates.foldLockfileIntoInitialCommit(pushed)).isFalse();
        org.assertj.core.api.Assertions.assertThat(git(pushed, "rev-parse", "HEAD")).isEqualTo(head);
        org.assertj.core.api.Assertions.assertThat(git(pushed, "status", "--porcelain")).contains("?? package-lock.json");
    }

    /** A generated project with its scaffold commit — the wizard's state before the install. */
    private java.io.File scaffoldedRepo(String name) throws Exception {
        java.io.File dir = parent.resolve(name).toFile();
        ProjectTemplates.VANILLA.generate(dir, name);
        ProjectTemplates.initGitRepo(dir);
        return dir;
    }

    private static String git(java.io.File dir, String... args) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of("git", "-C", dir.getAbsolutePath()));
        cmd.addAll(java.util.List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        p.waitFor();
        return out.strip();
    }

    /** The ordered device-type roster of a serialized patch. */
    private static java.util.List<String> deviceTypes(JSONObject patch) {
        java.util.List<String> types = new java.util.ArrayList<>();
        var devices = patch.getJSONArray("devices");
        for (int i = 0; i < devices.length(); i++) {
            types.add(devices.getJSONObject(i).getString("type"));
        }
        return types;
    }

    private static boolean gitAvailable() {
        for (String d : org.nmox.studio.core.process.ToolLocator.augmentedPath()
                .split(java.io.File.pathSeparator)) {
            if (new java.io.File(d, "git").canExecute()) {
                return true;
            }
        }
        return false;
    }
}
