package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.util.UiLocale;
import org.nmox.studio.rack.projectstudio.LearningCatalog;

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

    @Test
    @DisplayName("every category the catalogue uses is named in every language")
    void everyCategoryIsNamedInEveryLanguage() throws IOException {
        // the ENUM, not the spaces: a category with no built-in space today
        // is still a heading a drop-in can file itself under tomorrow, and
        // the first run of this gate found exactly one (STACK)
        List<String> categories = categories();
        assertThat(categories).as("the picker's top-level groups").hasSizeGreaterThan(3);

        List<String> silent = new ArrayList<>();
        for (String lang : languages()) {
            Properties words = groupingWords(lang);
            for (String category : categories) {
                if (words.getProperty("LearnCategory_" + category) == null) {
                    silent.add(lang + ": " + category);
                }
            }
        }
        assertThat(silent).as("a group heading in English over translated rows").isEmpty();
    }

    @Test
    @DisplayName("a family translated in one language is translated in all of them")
    void aFamilyTranslatedAnywhereIsTranslatedEverywhere() throws IOException {
        TreeSet<String> families = new TreeSet<>();
        for (JSONObject space : shippedSpaces()) {
            families.add(familyKey(space.getString("family")));
        }
        List<String> languages = languages();

        List<String> half = new ArrayList<>();
        for (String family : families) {
            List<String> have = new ArrayList<>();
            for (String lang : languages) {
                if (groupingWords(lang).getProperty("LearnFamily_" + family) != null) {
                    have.add(lang);
                }
            }
            // a family is either prose (all twelve) or a name (none of them);
            // anything between is a reader seeing English where their
            // neighbour sees their own language
            if (!have.isEmpty() && have.size() != languages.size()) {
                half.add(family + ": " + have);
            }
        }
        assertThat(half).as("a family is prose in every language or a name in none")
                .isEmpty();
    }

    @Test
    @DisplayName("no grouping word is written for a group that does not exist")
    void noGroupingKeyIsDead() throws IOException {
        TreeSet<String> live = new TreeSet<>();
        for (String category : categories()) {
            live.add("LearnCategory_" + category);
        }
        for (JSONObject space : shippedSpaces()) {
            live.add("LearnFamily_" + familyKey(space.getString("family")));
        }

        List<String> dead = new ArrayList<>();
        for (String lang : languages()) {
            for (String key : groupingWords(lang).stringPropertyNames()) {
                if (!key.startsWith("LearnCategory_") && !key.startsWith("LearnFamily_")) {
                    continue;
                }
                if (!live.contains(key)) {
                    dead.add(lang + ": " + key);
                }
            }
        }
        assertThat(dead).as("a grouping word nobody reads usually means the group "
                + "it was meant for is spelled differently and reads English").isEmpty();
    }

    /** Every heading the picker can group under. */
    private static List<String> categories() {
        return java.util.Arrays.stream(LearningCatalog.Category.values())
                .map(Enum::name).toList();
    }

    /** {@code CatalogText.key}: the properties-safe spelling of a family. */
    private static String familyKey(String family) {
        return family.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }

    /** The picker's own bundle, from the assembled cluster. */
    private static Properties groupingWords(String lang) throws IOException {
        String entry = "org/nmox/studio/ui/actions/Bundle_" + lang + ".properties";
        try (InputStream in = clusterResource(entry)) {
            Properties props = new Properties();
            // UTF-8: Properties.load(InputStream) is ISO-8859-1 by contract
            // and these bundles ship raw UTF-8 (v2.129.0)
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return props;
        }
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
        String json;
        try (InputStream in = clusterResource(CATALOG)) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        JSONArray arr = new JSONObject(json).getJSONArray("spaces");
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            out.add(arr.getJSONObject(i));
        }
        return out;
    }

    /** One named resource, out of whichever shipped module jar carries it. */
    private static InputStream clusterResource(String entry) throws IOException {
        assertThat(MODULES).as("the assembled cluster's modules").isDirectory();
        try (Stream<Path> jars = Files.list(MODULES)) {
            for (Path jarPath : jars.filter(p -> p.toString().endsWith(".jar")).toList()) {
                JarFile jar = new JarFile(jarPath.toFile());
                boolean keep = false;
                try {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry e = entries.nextElement();
                        if (e.getName().equals(entry)) {
                            keep = true;
                            return jar.getInputStream(e);
                        }
                    }
                } finally {
                    if (!keep) {
                        jar.close();
                    }
                }
            }
        }
        throw new AssertionError("no shipped " + entry + " — the picker lost a language");
    }
}
