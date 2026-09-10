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
        // v2.105.0: the population is the DIRECTORY, not a hand-kept pair.
        // The site spoke two languages while the IDE spoke thirteen, and a
        // gate naming en and es could never have said so.
        assertThat(markup).isNotEmpty();
        java.util.List<String> locales = locales();
        assertThat(locales).as("the site's catalogs").hasSizeGreaterThanOrEqualTo(13).contains("en");
        for (String locale : locales) {
            JSONObject cat = new JSONObject(Files.readString(SITE.resolve("locales/" + locale + ".json")));
            assertThat(cat.keySet()).as("%s must answer exactly the markup's keys", locale)
                    .isEqualTo(markup);
            for (String key : cat.keySet()) {
                assertThat(cat.getString(key)).as("%s.%s is blank", locale, key).isNotBlank();
                assertThat(cat.getString(key))
                        .as("%s.%s uses a bare ASCII apostrophe — the v2.98.0 rule", locale, key)
                        .doesNotContain("'");
            }
        }
        // every catalog on disk is reachable from the page, and every button
        // it offers has a catalog behind it — a picker that names a missing
        // locale warns to the console and silently stays where it was
        String footer = Files.readString(SITE.resolve("index.html"));
        for (String locale : locales) {
            assertThat(footer).as("no button for %s — the catalog is unreachable", locale)
                    .contains("data-setlocale=\"" + locale + "\"");
        }
        java.util.regex.Matcher b = java.util.regex.Pattern
                .compile("data-setlocale=\"([a-z]{2})\"").matcher(footer);
        while (b.find()) {
            assertThat(locales).as("the picker offers %s with no catalog behind it", b.group(1))
                    .contains(b.group(1));
        }
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
    /** Every catalog the site ships, by language tag, from the directory. */
    private static java.util.List<String> locales() throws Exception {
        try (java.util.stream.Stream<java.nio.file.Path> s =
                java.nio.file.Files.list(SITE.resolve("locales"))) {
            return s.map(p -> p.getFileName().toString())
                    .filter(n -> n.matches("[a-z]{2}\\.json"))
                    .map(n -> n.substring(0, 2)).sorted().toList();
        }
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("the site's device and space counts equal the catalogs, in every language")
    void countsAreTrue() throws Exception {
        String catalog = java.nio.file.Files.readString(java.nio.file.Path.of("..", "rack", "src", "main", "resources",
                "org", "nmox", "studio", "rack", "projectstudio", "learn-catalog.json"));
        long spaces = java.util.regex.Pattern.compile("\"slug\"\\s*:").matcher(catalog).results().count();
        long devices = java.nio.file.Files.readAllLines(java.nio.file.Path.of("..", "docs", "devices.md")).stream()
                .filter(l -> l.startsWith("### ")).count();
        // the word after the numeral is different in every language, so the
        // numeral itself is what every catalog is held to — a stale 92 fails
        // here exactly as it did when only two languages were checked
        for (String locale : locales()) {
            String text = java.nio.file.Files.readString(SITE.resolve("locales/" + locale + ".json"));
            org.assertj.core.api.Assertions.assertThat(text)
                    .as(locale + " names the device count").containsPattern("\\b" + devices + "\\b");
            org.assertj.core.api.Assertions.assertThat(text)
                    .as(locale + " names the space count").containsPattern("\\b" + spaces + "\\b");
        }
        // English and Spanish keep the stronger, word-anchored form they had
        for (String[] pair : new String[][] {{"en", "devices"}, {"es", "dispositivos"}}) {
            String text = java.nio.file.Files.readString(SITE.resolve("locales/" + pair[0] + ".json"));
            java.util.regex.Matcher d = java.util.regex.Pattern
                    .compile("(\\d+) " + pair[1]).matcher(text);
            org.assertj.core.api.Assertions.assertThat(d.find()).as(pair[0] + " names a device count").isTrue();
            org.assertj.core.api.Assertions.assertThat(Long.parseLong(d.group(1)))
                    .as(pair[0] + " device count").isEqualTo(devices);
        }
        String html = java.nio.file.Files.readString(SITE.resolve("index.html"));
        org.assertj.core.api.Assertions.assertThat(html).contains(devices + " devices").contains(spaces + " ways in");
    }
}
