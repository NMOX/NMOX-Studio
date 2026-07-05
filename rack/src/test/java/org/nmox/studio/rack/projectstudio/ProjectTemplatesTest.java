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
        assertThat(compose).contains("nginx").contains("php:8.3-fpm").contains("mariadb:11");
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
