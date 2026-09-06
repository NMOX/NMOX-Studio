package org.nmox.studio.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bundled website's SOURCE truth (v2.40.0): its kit files ARE the
 * kits' own output byte-for-byte ("checked by its own kits" as a
 * build law, not a slogan), and its i18n catalogs carry exactly the
 * keys the markup asks for — the I18nKitTest same-keys law, applied
 * to our own site. The did-it-actually-ship half reads the assembled
 * cluster and lives in {@link PackagedSiteGateTest} at
 * integration-test phase, after package.
 */
class SiteShipsTest {

    private static final Path SITE =
            Path.of("../ui/src/main/release/website");

    @Test
    @DisplayName("a11y.css and i18n.js ARE the kits' own output, byte-for-byte")
    void kitParity() throws Exception {
        assertThat(Files.readString(SITE.resolve("a11y.css")))
                .isEqualTo(org.nmox.studio.rack.projectstudio.A11yKit.stylesheet());
        assertThat(Files.readString(SITE.resolve("i18n.js")))
                .isEqualTo(org.nmox.studio.rack.projectstudio.I18nKit.helper());
    }

    @Test
    @DisplayName("the markup's data-i18n keys equal both catalogs' key sets")
    void i18nKeysAgree() throws Exception {
        String html = Files.readString(SITE.resolve("index.html"));
        Matcher m = Pattern.compile("data-i18n=\"([^\"]+)\"").matcher(html);
        java.util.Set<String> markup = new java.util.TreeSet<>();
        while (m.find()) {
            markup.add(m.group(1));
        }
        JSONObject en = new JSONObject(Files.readString(SITE.resolve("locales/en.json")));
        JSONObject es = new JSONObject(Files.readString(SITE.resolve("locales/es.json")));
        assertThat(en.keySet()).isEqualTo(markup);
        assertThat(es.keySet()).isEqualTo(markup);
        assertThat(markup).isNotEmpty();
        // and the page carries the a11y kit's structural bits
        assertThat(html).contains("skip-link").contains("lang=\"en\"").contains("id=\"main\"");
    }

    /**
     * The website's counts are bound to the same ground truth the docs
     * are (v2.91.0): "Ninety-two ways in" sat a space stale for thirty
     * releases because a word is invisible to a numeral gate — so the
     * site says its counts in numerals, in both catalogs, and this reads
     * them against the learning catalog and the generated device
     * reference exactly the way DocsCountGateTest does.
     */
    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("the site's device and space counts, in both languages, equal the catalogs")
    void countsAreTrue() throws Exception {
        String catalog = java.nio.file.Files.readString(java.nio.file.Path.of("..", "rack", "src", "main", "resources",
                "org", "nmox", "studio", "rack", "projectstudio", "learn-catalog.json"));
        long spaces = java.util.regex.Pattern.compile("\"slug\"\\s*:").matcher(catalog).results().count();
        long devices = java.nio.file.Files.readAllLines(java.nio.file.Path.of("..", "docs", "devices.md")).stream()
                .filter(l -> l.startsWith("### ")).count();
        for (String locale : new String[] {"en", "es"}) {
            String text = java.nio.file.Files.readString(SITE.resolve("locales/" + locale + ".json"));
            java.util.regex.Matcher d = java.util.regex.Pattern.compile("(\\d+) (?:devices|dispositivos)").matcher(text);
            org.assertj.core.api.Assertions.assertThat(d.find()).as(locale + " names a device count").isTrue();
            org.assertj.core.api.Assertions.assertThat(Long.parseLong(d.group(1))).as(locale + " device count").isEqualTo(devices);
            java.util.regex.Matcher w = java.util.regex.Pattern.compile("(\\d+) (?:ways in|puertas de entrada)").matcher(text);
            org.assertj.core.api.Assertions.assertThat(w.find()).as(locale + " names a space count").isTrue();
            org.assertj.core.api.Assertions.assertThat(Long.parseLong(w.group(1))).as(locale + " space count").isEqualTo(spaces);
        }
        String html = java.nio.file.Files.readString(SITE.resolve("index.html"));
        org.assertj.core.api.Assertions.assertThat(html).contains(devices + " devices").contains(spaces + " ways in");
    }
}
