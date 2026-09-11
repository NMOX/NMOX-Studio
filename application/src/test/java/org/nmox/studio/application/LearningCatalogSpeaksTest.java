package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.util.UiLocale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every learning space introduces itself in the reader's language.
 *
 * <p>The picker is a catalogue of 93 short pitches: a person scrolls them
 * to choose what to practise. They lived only in the catalogue's English,
 * so a fully translated build painted a German heading over English prose
 * — ledger 97, and the device shelf's class one surface over (v2.132.0).
 * No l10n gate could see it, because this English never passed through a
 * bundle at all; it is catalogue DATA.
 *
 * <p>Five laws. COMPLETE: a space the product ships is pitched in every
 * language the product offers, so a space added tomorrow — or a
 * fourteenth language — fails the build until the catalogue speaks it.
 * TRANSLATED: a pitch identical to the English is evidence of an entry
 * nobody wrote, not proof of one, so it must be blessed in writing.
 * RENDERS: a value carries no mojibake, no stray edge whitespace, and
 * cannot run away from the English it renders beside. ADDRESSED: a
 * {@code field.lang} sibling must name a field the parser reads and a
 * language the product offers — a typo like {@code blurb.pr} parses
 * cleanly and is never shown to anybody. WHOLE: a name translated into
 * some languages and not others reads as a bug in the ones it skipped.
 *
 * <p>Bound to {@code packaged-app-gates}: the population is the catalogue
 * the ASSEMBLED cluster actually ships, not the one in the source tree.
 */
class LearningCatalogSpeaksTest {

    private static final Path MODULES = Path.of("target", "nmoxstudio", "nmoxstudio", "modules");

    private static final String CATALOG =
            "org/nmox/studio/rack/projectstudio/learn-catalog.json";

    /** The fields {@code LearningCatalog.translations} reads. */
    private static final List<String> TRANSLATABLE = List.of("name", "blurb", "tutorial");

    /**
     * A translated pitch longer than this multiple of its English is a
     * paste, not a translation. Measured: the widest real value runs 1.46x
     * (Filipino needs more words than English), and Chinese runs 0.22x.
     */
    private static final double RUNAWAY = 2.0;

    /**
     * Pitches that legitimately read the same in a language as in English.
     * Empty, and it should stay that way: these are whole sentences, not
     * single words, so a match is an entry somebody forgot to write.
     */
    private static final List<String> COINCIDES_WITH_ENGLISH = List.of();

    /** The UTF-8 em dash read as ISO-8859-1 — the v2.129.0 signature. */
    private static final String MOJIBAKE = "â";

    @Test
    @DisplayName("every shipped space is pitched in every language the product offers")
    void everySpaceSpeaksEveryLanguage() throws IOException {
        List<JSONObject> spaces = shippedSpaces();
        assertThat(spaces).as("learning spaces the product ships").hasSizeGreaterThan(90);

        List<String> silent = new ArrayList<>();
        for (String lang : languages()) {
            for (JSONObject space : spaces) {
                String blurb = space.optString("blurb." + lang, "");
                if (blurb.isBlank()) {
                    silent.add(lang + ": " + space.getString("slug"));
                }
            }
        }
        assertThat(silent).as("spaces that would pitch themselves in English "
                + "under a translated heading").isEmpty();
    }

    @Test
    @DisplayName("no pitch is still the English one")
    void noPitchIsStillEnglish() throws IOException {
        List<String> same = new ArrayList<>();
        for (String lang : languages()) {
            for (JSONObject space : shippedSpaces()) {
                String slug = space.getString("slug");
                if (COINCIDES_WITH_ENGLISH.contains(lang + "/" + slug)) {
                    continue;
                }
                if (space.getString("blurb").equals(space.optString("blurb." + lang, ""))) {
                    same.add(lang + "/" + slug);
                }
            }
        }
        assertThat(same).as("a pitch equal to the English is an entry nobody wrote; "
                + "write it, or bless it in COINCIDES_WITH_ENGLISH").isEmpty();
    }

    @Test
    @DisplayName("every translated value renders as prose beside its English")
    void everyValueRenders() throws IOException {
        List<String> broken = new ArrayList<>();
        int measured = 0;
        for (String lang : languages()) {
            for (JSONObject space : shippedSpaces()) {
                for (String field : TRANSLATABLE) {
                    String value = space.optString(field + "." + lang, null);
                    if (value == null) {
                        continue;
                    }
                    measured++;
                    String where = lang + "/" + space.getString("slug") + "." + field;
                    if (value.contains(MOJIBAKE) || value.indexOf('�') >= 0) {
                        broken.add(where + ": mojibake");
                    }
                    if (!value.equals(value.strip())) {
                        broken.add(where + ": edge whitespace");
                    }
                    String english = space.getString(field);
                    if (!english.isEmpty() && value.length() > english.length() * RUNAWAY) {
                        broken.add(where + ": " + value.length() + " chars against "
                                + english.length() + " in English");
                    }
                }
            }
        }
        assertThat(measured).as("the gate should measure every space in every language")
                .isGreaterThan(1000);
        assertThat(broken).as("values a reader would see as damage").isEmpty();
    }

    @Test
    @DisplayName("every translation sibling names a real field and a real language")
    void everySiblingIsAddressed() throws IOException {
        List<String> unreachable = new ArrayList<>();
        List<String> languages = languages();
        for (JSONObject space : shippedSpaces()) {
            for (String key : space.keySet()) {
                int dot = key.indexOf('.');
                if (dot <= 0) {
                    continue;
                }
                String field = key.substring(0, dot);
                String lang = key.substring(dot + 1);
                if (!TRANSLATABLE.contains(field)) {
                    unreachable.add(space.getString("slug") + ": " + key + " — no such field");
                } else if (!languages.contains(lang)) {
                    unreachable.add(space.getString("slug") + ": " + key + " — no such language");
                }
            }
        }
        assertThat(unreachable).as("a sibling nobody can read is prose written for no one")
                .isEmpty();
    }

    @Test
    @DisplayName("a space whose name is translated is translated everywhere")
    void translatedNamesAreAllOrNothing() throws IOException {
        List<String> half = new ArrayList<>();
        List<String> languages = languages();
        for (JSONObject space : shippedSpaces()) {
            List<String> have = languages.stream()
                    .filter(l -> !space.optString("name." + l, "").isBlank()).toList();
            if (!have.isEmpty() && have.size() != languages.size()) {
                half.add(space.getString("slug") + ": " + have);
            }
        }
        assertThat(half).as("a name translated into some languages and not others "
                + "reads as a bug in the languages it skipped").isEmpty();
    }

    /** The languages the product offers, English and System aside. */
    private static List<String> languages() {
        return UiLocale.SUPPORTED.stream()
                .filter(c -> !c.isSystem() && !"en".equals(c.code()))
                .map(UiLocale.Choice::code)
                .toList();
    }

    /** The catalogue the assembled cluster ships. */
    private static List<JSONObject> shippedSpaces() throws IOException {
        assertThat(MODULES).as("the assembled cluster's modules").isDirectory();
        try (Stream<Path> jars = Files.list(MODULES)) {
            for (Path jarPath : jars.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (JarFile jar = new JarFile(jarPath.toFile())) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry e = entries.nextElement();
                        if (!e.getName().equals(CATALOG)) {
                            continue;
                        }
                        String json;
                        try (InputStream in = jar.getInputStream(e)) {
                            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        }
                        JSONArray arr = new JSONObject(json).getJSONArray("spaces");
                        List<JSONObject> out = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            out.add(arr.getJSONObject(i));
                        }
                        return out;
                    }
                }
            }
        }
        throw new AssertionError("no shipped " + CATALOG + " — the picker lost its catalogue");
    }
}
