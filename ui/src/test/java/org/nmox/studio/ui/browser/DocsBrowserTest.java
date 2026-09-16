package org.nmox.studio.ui.browser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shop front the DevTools picture picks from. The pick is a child-index
 * path, so the page's element structure is part of the picture's contract.
 */
class DocsBrowserTest {

    private static String fixtures() throws Exception {
        return Files.readString(Path.of("../docs/i18n/forge-fixtures.json"), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the picked path names the h1: body is <html>'s second element, header its first, h1 the header's first")
    void pickedPathNamesTheHeading(@TempDir Path home) throws Exception {
        File site = new DocsBrowser().stage(home.toFile(), fixtures(), "vi");
        String html = Files.readString(site.toPath().resolve("index.html"), StandardCharsets.UTF_8);
        assertThat(DocsBrowser.HEADING).containsExactly(1, 0, 0);
        int head = html.indexOf("<head>");
        int body = html.indexOf("<body>");
        assertThat(head).isPositive().isLessThan(body);
        String inBody = html.substring(body + "<body>".length()).strip();
        assertThat(inBody).startsWith("<header class=\"hero\">");
        assertThat(inBody.substring("<header class=\"hero\">".length()).strip()).startsWith("<h1>Meridian Coffee</h1>");
        assertThat(html).contains("lang=\"vi\" dir=\"ltr\"").contains("Rang mẻ nhỏ");
        assertThat(Files.readString(site.toPath().resolve("style.css"))).contains(".hero h1");
    }

    @Test
    @DisplayName("the reader's words are text, never markup, and Hebrew and Arabic read right to left")
    void wordsAreEscapedAndRtlLanguagesMirror() {
        String html = DocsBrowser.page("ar", "<script>x</script>", "a & b", "\"go\"");
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;").contains("a &amp; b");
        assertThat(html).contains("dir=\"rtl\"");
        assertThat(DocsBrowser.page("he", "t", "h", "o")).contains("dir=\"rtl\"");
    }

    @Test
    @DisplayName("the forge script serves the directory this scene writes, on the port it loads")
    void scriptServesTheScenesDirectory() throws Exception {
        // the path had two homes and the first English run lost the picture
        // to a 404: the script served $HOME_DIR/storefront/site while the
        // scene wrote under $HOME_DIR/NMOX/storefront/site
        String script = Files.readString(Path.of("../scripts/docs-shots.sh"), StandardCharsets.UTF_8);
        String written = org.nmox.studio.core.util.DocsFixtures.projectDir(new File("HOME"))
                .toPath().resolve(DocsBrowser.SITE).toString().replace(File.separatorChar, '/');
        String served = "\"" + written.replaceFirst("^HOME", "\\$HOME_DIR") + "\"";
        assertThat(script).contains("docs-fixture-server.py\" " + java.net.URI.create(DocsBrowser.URL).getPort()
                + " " + served);
    }

    @Test
    @DisplayName("with no Browser built the scene is quiet and never ready")
    void quietWithoutABrowser() {
        DocsBrowser scene = new DocsBrowser();
        assertThat(scene.ready()).isFalse();
        assertThat(scene.id()).isEqualTo("story-06-devtools-pick");
    }
}
