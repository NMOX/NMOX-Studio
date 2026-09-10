package org.nmox.studio.application;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A fresh install speaks the reader's own language before they touch
 * anything (v2.111.0) — including from a country we never named.
 *
 * <p>Nobody configures an IDE they cannot read. If NMOX Studio opened in
 * English for a Chinese user, the way out would be a menu path written in
 * English, which is the wrong place to make someone start. It does not:
 * {@code ResourceBundle.getBundle} keys on the JVM's default locale, and
 * the JVM's default locale is the operating system's, so a first launch on
 * a Chinese desktop answers Chinese with no {@code --locale} and no
 * Options visit. That has been true since v2.97.0 and was never written
 * down or held anywhere.
 *
 * <p>The half worth gating is the REGIONAL one. Bundles are named for a
 * language and never a country ({@code Bundle_zh}, {@code Bundle_pt}) —
 * a deliberate v2.99.0 decision so that Taiwan, Singapore, Portugal and
 * Quebec fall back to a usable IDE rather than to English. That decision
 * lives in a file NAME, which no compiler checks: renaming one bundle to
 * {@code Bundle_zh_CN} would silently strand every Chinese reader outside
 * the mainland, and nothing in the build would notice. So this gate asks
 * the real JDK lookup, over the assembled cluster, from a country we do
 * not ship for.
 *
 * <p>Runs at integration-test phase, with the other packaged-result gates:
 * {@code target/nmoxstudio} exists only after {@code package}, and a clean
 * build caught this gate asserting against a cluster that was not there
 * yet — it had passed until then only because a stale one was on disk.
 */
class SystemLocaleReachTest {

    /** A country we never named, for each language we do ship. */
    private static final Map<String, String> ELSEWHERE = Map.ofEntries(
            Map.entry("es", "es-MX"), Map.entry("fr", "fr-CA"), Map.entry("de", "de-AT"),
            Map.entry("ru", "ru-KZ"), Map.entry("uk", "uk-UA"), Map.entry("pl", "pl-PL"),
            Map.entry("pt", "pt-PT"), Map.entry("id", "id-ID"), Map.entry("tl", "tl-PH"),
            Map.entry("vi", "vi-VN"), Map.entry("zh", "zh-TW"), Map.entry("hi", "hi-IN"));

    private static final String BUNDLE = "org.nmox.studio.ui.Bundle";
    private static final String KEY = "CTL_MainWindowTopComponent";

    private static Path cluster() {
        return Path.of("target/nmoxstudio/nmoxstudio");
    }

    /** The assembled cluster's jars, which is where the shipped bytes are. */
    private static ClassLoader clusterLoader() throws IOException {
        List<URL> urls = new ArrayList<>();
        Path modules = cluster().resolve("modules");
        assertThat(modules).as("the assembled cluster exists after package").isDirectory();
        try (Stream<Path> jars = Files.list(modules)) {
            for (Path jar : jars.filter(j -> j.toString().endsWith(".jar")).toList()) {
                urls.add(jar.toUri().toURL());
            }
        }
        // parent null on purpose: only the shipped jars answer, so a stray
        // test-classpath copy of a bundle cannot make this gate pass
        return new URLClassLoader(urls.toArray(URL[]::new), null);
    }

    @Test
    @DisplayName("a first launch in a country we never named still answers in that country's language")
    void everyLanguageIsReachedFromAnotherCountry() throws IOException {
        ClassLoader loader = clusterLoader();
        String english = lookupAs(Locale.US, loader);
        assertThat(english).as("the English value, for comparison").isNotBlank();

        List<String> stranded = new ArrayList<>();
        for (String lang : LocaleBundleParityTest.LOCALES) {
            String tag = ELSEWHERE.get(lang);
            assertThat(tag).as("every shipped language needs a country to be tested from").isNotNull();
            String fromAbroad = lookupAs(Locale.forLanguageTag(tag), loader);
            String fromLanguage = lookupAs(Locale.forLanguageTag(lang), loader);
            if (fromAbroad.equals(english) || !fromAbroad.equals(fromLanguage)) {
                stranded.add(tag + " read “" + fromAbroad + "” where " + lang
                        + " reads “" + fromLanguage + "”");
            }
        }
        assertThat(stranded)
                .as("a bundle named for a country strands every reader outside it (v2.99.0); "
                        + "these locales fell back to English instead of to their own language")
                .isEmpty();
    }

    @Test
    @DisplayName("a language we do not ship falls back to English — so the test above can tell the difference")
    void anUnshippedLanguageFallsBackToEnglish() throws IOException {
        ClassLoader loader = clusterLoader();
        String english = lookupAs(Locale.US, loader);
        // Japanese is not one of the thirteen. If this ALSO answered in
        // Japanese the gate above would be measuring nothing, so the
        // control is part of the proof rather than an afterthought.
        assertThat(lookupAs(Locale.JAPAN, loader))
                .as("an unshipped locale must land on English, not on whatever ran last")
                .isEqualTo(english);
    }

    /**
     * The lookup the JVM itself does at startup: the DEFAULT locale drives
     * {@code getBundle}, so the default is what gets moved here rather than
     * a locale argument — passing one explicitly would test a call the
     * product never makes.
     */
    private static String lookupAs(Locale locale, ClassLoader loader) {
        Locale was = Locale.getDefault();
        try {
            Locale.setDefault(locale);
            ResourceBundle.clearCache(loader);
            // getBundle(name, locale, loader) with the default re-read: the
            // JDK has no (name, loader) overload, and this is exactly what
            // the two-argument form does — read Locale.getDefault() first
            return ResourceBundle.getBundle(BUNDLE, Locale.getDefault(), loader).getString(KEY);
        } finally {
            Locale.setDefault(was);
        }
    }
}
