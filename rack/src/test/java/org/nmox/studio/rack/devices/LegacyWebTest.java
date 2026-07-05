package org.nmox.studio.rack.devices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Classic library detection: three sources merged (package.json,
 * bower.json, root HTML script tags), filename version capture, the
 * jQuery EOL rule, deduplication, and the 64 KB scan cap.
 */
class LegacyWebTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("package.json dependencies name the classics, ranges cleaned")
    void packageJsonSource() throws IOException {
        Files.writeString(dir.resolve("package.json"), """
                {"dependencies":{"jquery":"^3.7.1","express":"^4.0.0"},
                 "devDependencies":{"underscore":"~1.13.7"}}
                """);
        var libs = LegacyWeb.scan(dir.toFile());
        assertThat(libs).extracting(LegacyWeb.Library::id)
                .containsExactly("jquery", "underscore");
        assertThat(libs.get(0).version()).isEqualTo("3.7.1");
        assertThat(libs.get(0).eol()).as("jQuery 3.x is current").isFalse();
        assertThat(libs.get(1).version()).isEqualTo("1.13.7");
        assertThat(libs.get(1).eol()).as("only jQuery gets the EOL rule in v1").isFalse();
    }

    @Test
    @DisplayName("bower.json dependencies count, and jQuery 1.x is honestly EOL")
    void bowerJsonSourceAndEolRule() throws IOException {
        Files.writeString(dir.resolve("bower.json"), """
                {"name":"site","dependencies":{"jquery":"~1.12.4","knockout":"3.5.1"}}
                """);
        var libs = LegacyWeb.scan(dir.toFile());
        assertThat(libs).extracting(LegacyWeb.Library::id)
                .containsExactly("jquery", "knockout");
        LegacyWeb.Library jquery = libs.get(0);
        assertThat(jquery.version()).isEqualTo("1.12.4");
        assertThat(jquery.eol()).isTrue();
        assertThat(jquery.label()).isEqualTo("jquery 1.12.4 — EOL");
        assertThat(jquery.eolMessage())
                .isEqualTo("jQuery 1.x reached end-of-life — 3.x is the supported line");
        assertThat(libs.get(1).label()).isEqualTo("knockout 3.5.1");
        assertThat(libs.get(1).eolMessage()).isNull();
    }

    @Test
    @DisplayName("Script tags in root HTML are scanned; the filename carries the version")
    void scriptTagSource() throws IOException {
        Files.writeString(dir.resolve("index.html"), """
                <!DOCTYPE html>
                <html><head>
                <script src="js/vendor/jquery-1.12.4.min.js"></script>
                <SCRIPT SRC='libs/backbone/backbone.js'></SCRIPT>
                <script data-src="js/prototype-9.9.9.js"></script>
                <script src="js/app.js"></script>
                </head><body></body></html>
                """);
        var libs = LegacyWeb.scan(dir.toFile());
        assertThat(libs).extracting(LegacyWeb.Library::id)
                .as("data-src is not a script source")
                .containsExactly("jquery", "backbone");
        assertThat(libs.get(0).version()).isEqualTo("1.12.4");
        assertThat(libs.get(0).eol()).isTrue();
        assertThat(libs.get(1).version()).as("no version in the filename").isEmpty();
        assertThat(libs.get(1).label()).isEqualTo("backbone");
    }

    @Test
    @DisplayName("A versionless filename means version unknown — and no EOL claim")
    void versionUnknownStaysHonest() throws IOException {
        Files.writeString(dir.resolve("index.html"),
                "<script src=\"js/jquery.min.js\"></script>");
        var libs = LegacyWeb.scan(dir.toFile());
        assertThat(libs).hasSize(1);
        assertThat(libs.get(0).version()).isEmpty();
        assertThat(libs.get(0).eol())
                .as("no version, no EOL verdict — never guess").isFalse();
        assertThat(libs.get(0).label()).isEqualTo("jquery");
    }

    @Test
    @DisplayName("Sources merge to one entry per library; a known version beats an unknown one")
    void dedupeAcrossSources() throws IOException {
        // package.json names jquery without a usable number; the script
        // tag knows better - the merged entry carries 2.2.4 and the flag
        Files.writeString(dir.resolve("package.json"),
                "{\"dependencies\":{\"jquery\":\"*\",\"mootools\":\"1.6.0\"}}");
        Files.writeString(dir.resolve("bower.json"),
                "{\"dependencies\":{\"jquery\":\"*\"}}");
        Files.writeString(dir.resolve("index.html"),
                "<script src=\"vendor/jquery-2.2.4.js\"></script>");
        var libs = LegacyWeb.scan(dir.toFile());
        assertThat(libs).extracting(LegacyWeb.Library::id)
                .containsExactly("jquery", "mootools");
        assertThat(libs.get(0).version()).isEqualTo("2.2.4");
        assertThat(libs.get(0).eol()).as("jQuery 2.x is EOL too").isTrue();
        assertThat(libs.get(0).eolMessage()).contains("jQuery 2.x");
        assertThat(libs.get(1).version()).isEqualTo("1.6.0");
    }

    @Test
    @DisplayName("Only the first 64 KB of an HTML file is scanned")
    void htmlScanCap() throws IOException {
        String early = "<script src=\"js/knockout-3.5.1.js\"></script>\n";
        String padding = "<!-- " + "x".repeat(70_000) + " -->\n";
        String late = "<script src=\"js/jquery-1.12.4.min.js\"></script>\n";
        Files.writeString(dir.resolve("index.html"),
                "<html>" + early + padding + late + "</html>");
        var libs = LegacyWeb.scan(dir.toFile());
        assertThat(libs).extracting(LegacyWeb.Library::id)
                .as("the tag before the cap is seen, the one after is not")
                .containsExactly("knockout");
    }

    @Test
    @DisplayName("No sources, unreadable manifests, empty dirs: empty list, never a throw")
    void gracefulOnAbsence() throws IOException {
        assertThat(LegacyWeb.scan(dir.toFile())).isEmpty();
        Files.writeString(dir.resolve("package.json"), "{not json at all");
        Files.writeString(dir.resolve("index.html"), "<p>no scripts here</p>");
        assertThat(LegacyWeb.scan(dir.toFile())).isEmpty();
        assertThat(LegacyWeb.scan(dir.resolve("no-such-subdir").toFile())).isEmpty();
    }

    @Test
    @DisplayName("The classic-demo shape reports exactly jQuery 1.12.4, EOL")
    void classicDemoShape() throws IOException {
        Files.writeString(dir.resolve("bower.json"), """
                {"name":"riverside-hardware","version":"0.3.1",
                 "dependencies":{"jquery":"~1.12.4"}}
                """);
        Files.writeString(dir.resolve("index.html"), """
                <!DOCTYPE html><html><body>
                <script src="js/vendor/jquery-1.12.4.min.js"></script>
                <script src="js/app.js"></script>
                </body></html>
                """);
        var libs = LegacyWeb.scan(dir.toFile());
        assertThat(libs).hasSize(1);
        assertThat(libs.get(0).label()).isEqualTo("jquery 1.12.4 — EOL");
    }
}
