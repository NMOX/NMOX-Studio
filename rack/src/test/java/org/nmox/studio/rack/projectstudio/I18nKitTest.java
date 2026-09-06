package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The I18n Kit's laws: idempotent wiring, never-clobber, honest
 * refusals — and the kit-specific ones: both catalogs parse as real
 * JSON with IDENTICAL key sets (a sample pair that already disagrees
 * would teach catalog drift on day one), and the helper keeps the
 * document's {@code lang} truthful.
 */
class I18nKitTest {

    @Test
    @DisplayName("wire(): the script tag lands once — run twice, nothing doubles")
    void wireIsIdempotent() {
        String page = "<html><head><title>x</title></head><body></body></html>";
        String once = I18nKit.wire(page);
        assertThat(once).contains("i18n.js");
        assertThat(I18nKit.wire(once)).isEqualTo(once);
    }

    @Test
    @DisplayName("no </head> means no script tag — never a guess")
    void refusesHeadlessPage() {
        String fragment = "<div>not a full page</div>";
        assertThat(I18nKit.wire(fragment)).isEqualTo(fragment);
    }

    @Test
    @DisplayName("the sample catalogs are real JSON with identical key sets")
    void catalogsAgree() {
        JSONObject en = new JSONObject(I18nKit.enCatalog("demo"));
        JSONObject es = new JSONObject(I18nKit.esCatalog("demo"));
        assertThat(es.keySet()).isEqualTo(en.keySet());
        assertThat(en.keySet()).isNotEmpty();
    }

    @Test
    @DisplayName("the helper keeps <html lang> truthful and never blanks a missing key")
    void helperClaims() {
        String js = I18nKit.helper();
        assertThat(js).contains("document.documentElement.lang = tag");
        assertThat(js)
                .as("a missing key must surface, never vanish")
                .contains("I18N.messages[key] ?? key");
    }

    @Test
    @DisplayName("never-clobber: existing files are untouched; wiring reports honestly")
    void neverClobber(@TempDir Path work) throws IOException {
        File dir = work.toFile();
        Files.writeString(new File(dir, "i18n.js").toPath(), "MINE");
        Files.writeString(new File(dir, "index.html").toPath(),
                "<html><head></head><body></body></html>");
        List<I18nKit.Outcome> first = I18nKit.write(dir,
                new I18nKit.Options(true, true, true, true));
        assertThat(Files.readString(new File(dir, "i18n.js").toPath()))
                .isEqualTo("MINE");
        assertThat(first).filteredOn(o -> o.path().equals("i18n.js"))
                .allMatch(o -> !o.written());
        // second run: everything refuses, index byte-identical
        String wired = Files.readString(new File(dir, "index.html").toPath());
        List<I18nKit.Outcome> second = I18nKit.write(dir,
                new I18nKit.Options(true, true, true, true));
        assertThat(second).allMatch(o -> !o.written());
        assertThat(Files.readString(new File(dir, "index.html").toPath()))
                .isEqualTo(wired);
    }

    @Test
    @DisplayName("no index.html is an honest outcome, not a stack trace")
    void missingIndexIsHonest(@TempDir Path work) throws IOException {
        List<I18nKit.Outcome> out = I18nKit.write(work.toFile(),
                new I18nKit.Options(false, false, false, true));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).written()).isFalse();
        assertThat(out.get(0).note()).contains("no index.html found");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("a project name with a quote or backslash still produces valid JSON")
    void hostileNameStaysValidJson() {
        String hostile = "Ac\"me \\ & Co";
        JSONObject en = new JSONObject(I18nKit.enCatalog(hostile));
        JSONObject es = new JSONObject(I18nKit.esCatalog(hostile));
        assertThat(en.getString("app.title")).isEqualTo(hostile);
        assertThat(es.getString("app.title")).isEqualTo(hostile);
    }
}
