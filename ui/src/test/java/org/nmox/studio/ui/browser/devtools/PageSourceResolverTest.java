package org.nmox.studio.ui.browser.devtools;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.spi.LiveServings.Serving;
import org.nmox.studio.core.spi.LiveServings.Kind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inspect-to-source, half one (v1.357.0): the page URL maps to a file
 * on disk only through channels the IDE can vouch for — file:// pages
 * and the rack's own serving registry. Remote pages, unknown ports,
 * and traversal-shaped paths all refuse.
 */
class PageSourceResolverTest {

    @TempDir
    Path dir;

    private Serving serving(String url) {
        return new Serving("ignition", "IGNITION", url, Kind.WEB, dir.toFile());
    }

    @Test
    @DisplayName("a served page resolves through the serving's project dir; / means index.html")
    void servedPageResolves() throws Exception {
        Files.writeString(dir.resolve("index.html"), "<html></html>");
        Files.writeString(dir.resolve("about.html"), "<html></html>");
        List<Serving> s = List.of(serving("http://localhost:8000/"));
        assertThat(PageSourceResolver.resolve("http://localhost:8000/", s).file())
                .isEqualTo(dir.resolve("index.html").toFile().getCanonicalFile());
        assertThat(PageSourceResolver.resolve("http://localhost:8000/about.html", s).file())
                .isEqualTo(dir.resolve("about.html").toFile().getCanonicalFile());
    }

    @Test
    @DisplayName("loopback spellings are one origin: the page on [::1] matches a 127.0.0.1 serving")
    void loopbackSpellingsUnify() throws Exception {
        Files.writeString(dir.resolve("index.html"), "<html></html>");
        List<Serving> s = List.of(serving("http://127.0.0.1:4200/"));
        assertThat(PageSourceResolver.resolve("http://[::1]:4200/", s)).isNotNull();
        assertThat(PageSourceResolver.resolve("http://localhost:4200/", s)).isNotNull();
        // a DIFFERENT port is a different server, not this project
        assertThat(PageSourceResolver.resolve("http://localhost:4300/", s)).isNull();
    }

    @Test
    @DisplayName("the public/ docroot convention is tried when the root miss")
    void publicDocroot() throws Exception {
        Files.createDirectories(dir.resolve("public"));
        Files.writeString(dir.resolve("public/index.html"), "<html></html>");
        List<Serving> s = List.of(serving("http://localhost:3000"));
        assertThat(PageSourceResolver.resolve("http://localhost:3000/", s).file())
                .isEqualTo(dir.resolve("public/index.html").toFile().getCanonicalFile());
    }

    @Test
    @DisplayName("remote pages and traversal paths refuse")
    void refusals() throws Exception {
        Files.writeString(dir.resolve("index.html"), "<html></html>");
        List<Serving> s = List.of(serving("http://localhost:8000/"));
        assertThat(PageSourceResolver.resolve("https://example.com/", s)).isNull();
        // an encoded escape must never leave the project root
        assertThat(PageSourceResolver.resolve(
                "http://localhost:8000/..%2F..%2Fetc%2Fpasswd", s)).isNull();
        assertThat(PageSourceResolver.resolve(null, s)).isNull();
        assertThat(PageSourceResolver.resolve("not a url", s)).isNull();
    }

    @Test
    @DisplayName("a file:// page IS its source")
    void fileUrl() throws Exception {
        Path f = dir.resolve("page.html");
        Files.writeString(f, "<html></html>");
        assertThat(PageSourceResolver.resolve(f.toUri().toString(), List.of()).file())
                .isEqualTo(f.toFile());
    }
}
