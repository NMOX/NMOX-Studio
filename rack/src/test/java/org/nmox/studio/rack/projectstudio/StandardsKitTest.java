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
        JSONObject m = new JSONObject(StandardsKit.manifest("My Site",
                java.util.List.of("icon-192.png", "icon-512.png")));
        assertThat(m.getString("name")).isEqualTo("My Site");
        assertThat(m.getString("start_url")).isEqualTo("/");
        assertThat(m.getString("display")).isEqualTo("standalone");
        assertThat(m.getJSONArray("icons").length()).isGreaterThanOrEqualTo(2);
        assertThat(m.getJSONArray("icons").getJSONObject(0).getString("sizes"))
                .isEqualTo("192x192");
    }

    @Test
    @DisplayName("the manifest lists ONLY icons that exist — none exist, none listed")
    void manifestNeverReferencesMissingIcons(@TempDir Path dir) throws Exception {
        // v1.328.0: this kit forges no icons (the PWA Kit does), and the
        // manifest used to hardcode /icon-192.png + /icon-512.png anyway —
        // so a Standards-Kit-only run shipped a manifest whose icon
        // references 404, failing installability, the exact thing the
        // wizard's checkbox advertises. Icons gate installability, not
        // validity: an icon-less manifest is the honest artifact here.
        var outcomes = StandardsKit.write(dir.toFile(), new StandardsKit.Options(
                "https://example.com", "Site", "sec@example.com",
                false, false, true, false, false));
        JSONObject bare = new JSONObject(Files.readString(dir.resolve("site.webmanifest")));
        assertThat(bare.has("icons"))
                .as("no icons on disk, so the manifest must list none")
                .isFalse();
        assertThat(outcomes.get(0).note())
                .as("the wizard's report says where to forge them")
                .contains("PWA Kit");

        // with the PWA Kit's icons present, the manifest lists exactly them
        Files.delete(dir.resolve("site.webmanifest"));
        Files.writeString(dir.resolve("icon-192.png"), "png");
        Files.writeString(dir.resolve("icon-maskable-512.png"), "png");
        StandardsKit.write(dir.toFile(), new StandardsKit.Options(
                "https://example.com", "Site", "sec@example.com",
                false, false, true, false, false));
        JSONObject m = new JSONObject(Files.readString(dir.resolve("site.webmanifest")));
        var icons = m.getJSONArray("icons");
        assertThat(icons.length()).isEqualTo(2);
        assertThat(icons.getJSONObject(0).getString("src")).isEqualTo("/icon-192.png");
        assertThat(icons.getJSONObject(1).getString("src"))
                .isEqualTo("/icon-maskable-512.png");
        assertThat(icons.getJSONObject(1).getString("sizes")).isEqualTo("512x512");
        assertThat(icons.getJSONObject(1).getString("purpose"))
                .as("maskable icons declare their purpose")
                .isEqualTo("maskable");
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

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("a site URL with & or < produces a well-formed sitemap")
    void hostileUrlStaysValidXml() throws Exception {
        String xml = StandardsKit.sitemap("https://x.example/a?b=1&c=2", java.time.LocalDate.of(2026, 9, 6));
        assertThat(xml).contains("&amp;").doesNotContain("=2</loc>".replace("2", "2&"));
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
    }
}
