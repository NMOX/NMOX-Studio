package org.nmox.studio.rack.projectstudio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every file the Standards Kit generates is held to its spec - the
 * point of a standards wizard is that its output would pass review by
 * the standard's own validator.
 */
class StandardsKitTest {

    @Test
    @DisplayName("security.txt carries RFC 9116's REQUIRED fields, Expires within a year")
    void securityTxtIsRfc9116() {
        String txt = StandardsKit.securityTxt("sec@example.com", "https://example.com",
                LocalDate.of(2026, 7, 3));
        assertThat(txt).contains("Contact: mailto:sec@example.com")
                .contains("Expires: 2027-07-03T00:00:00Z")
                .contains("Canonical: https://example.com/.well-known/security.txt");
    }

    @Test
    @DisplayName("The web app manifest carries the members installability requires")
    void manifestIsInstallable() {
        JSONObject m = new JSONObject(StandardsKit.manifest("My Site"));
        assertThat(m.getString("name")).isEqualTo("My Site");
        assertThat(m.getString("start_url")).isEqualTo("/");
        assertThat(m.getString("display")).isEqualTo("standalone");
        assertThat(m.getJSONArray("icons").length()).isGreaterThanOrEqualTo(2);
        assertThat(m.getJSONArray("icons").getJSONObject(0).getString("sizes"))
                .isEqualTo("192x192");
    }

    @Test
    @DisplayName("sitemap.xml speaks the sitemaps.org protocol; robots points at it")
    void sitemapAndRobots() {
        String sitemap = StandardsKit.sitemap("https://example.com/", LocalDate.of(2026, 7, 3));
        assertThat(sitemap).contains("xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"")
                .contains("<loc>https://example.com/</loc>")
                .contains("<lastmod>2026-07-03</lastmod>");
        String robots = StandardsKit.robots("https://example.com/");
        assertThat(robots).contains("User-agent: *")
                .contains("Sitemap: https://example.com/sitemap.xml");
    }

    @Test
    @DisplayName("The kit writes into the project, .well-known included, and never clobbers")
    void writesWithoutClobbering(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("robots.txt"), "my precious custom robots\n");

        var outcomes = StandardsKit.write(dir.toFile(), new StandardsKit.Options(
                "https://example.com", "Site", "sec@example.com",
                true, true, true, true, true));

        assertThat(outcomes).hasSize(5);
        assertThat(outcomes).anyMatch(o -> o.path().equals("robots.txt") && !o.written());
        assertThat(Files.readString(dir.resolve("robots.txt")))
                .isEqualTo("my precious custom robots\n");
        assertThat(dir.resolve(".well-known/security.txt")).exists();
        assertThat(dir.resolve("site.webmanifest")).exists();
        assertThat(dir.resolve("sitemap.xml")).exists();
        assertThat(dir.resolve("humans.txt")).exists();
    }
}
