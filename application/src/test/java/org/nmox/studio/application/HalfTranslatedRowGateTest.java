package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A row a person reads is translated whole, or not at all.
 *
 * <p>Every l10n gate in this product measures whether a KEY exists. None of
 * them could see whether its VALUE was ever translated, and that blind spot
 * shipped a surface nobody would have accepted on sight: all sixteen rack
 * PRESET NAMES read English in German, Russian, Ukrainian and Polish while
 * the description under each one read in the user's own language. Eight
 * other languages translated both halves. Sixteen keys, four languages, and
 * a green build — the v2.132.0 shape exactly, where a German heading sat
 * above fifty-nine English cards.
 *
 * <p>Guessing at "is this value translated?" key by key is hopeless: 226 of
 * the product's 2,848 keys hold a value identical to their English in some
 * language, and almost every one of them is honest — {@code Name:} is
 * {@code Name:} in German, {@code Message} and {@code Structure} are French
 * words, and Filipino borrows freely. A gate demanding a blessing for each
 * would be a chore that decides nothing.
 *
 * <p>The signal that IS clean is structural. A {@code …Name} and its
 * {@code …Description} are two halves of ONE row on screen, so when the
 * description is translated and the name is not, the reader sees a sentence
 * in their language under a heading that is not — and that cannot be a
 * coincidence of vocabulary, because the same translator rendered the
 * sentence beneath it. The population derives itself from the shipped
 * bundles: every key whose name ends in a half this product pairs.
 */
class HalfTranslatedRowGateTest {

    private static final List<String> LOCALES = List.of(
            "es", "fr", "de", "ru", "uk", "pl", "pt", "id", "tl", "vi", "zh", "hi");

    /** The suffix pairs that make one row: a heading and the line under it. */
    private static final Map<String, String> ROW_HALVES = new LinkedHashMap<>();

    static {
        ROW_HALVES.put("Name", "Description");
        ROW_HALVES.put("Title", "Description");
    }

    /**
     * Rows whose heading is deliberately the same word in some language.
     *
     * <p>A blessing here says the English heading IS the right heading for
     * that reader — a product or technology name — not that nobody got to
     * it yet.
     */
    private static final Map<String, String> SAME_HEADING = new LinkedHashMap<>();

    static {
        SAME_HEADING.put("MinimapSideBar_accessibleName",
                "\"Minimap\" is the accepted word for this control in German, Indonesian "
                + "and Filipino developer usage; the languages that did translate it "
                + "chose a descriptive phrase, and both readings are defensible for a "
                + "control whose own description explains it.");
        SAME_HEADING.put("StickyScrollSideBar_accessibleName",
                "\"Sticky scroll\" entered Filipino developer usage from the editors "
                + "that named the feature; the description beside it carries the "
                + "explanation in Filipino.");
    }

    @Test
    @DisplayName("no row is translated on one line and English on the other")
    void everyRowIsTranslatedWhole() throws IOException {
        List<Path> jars = productJars();
        assertThat(jars).as("product jars in the assembled cluster — an empty scan "
                + "would find perfect rows over nothing").isNotEmpty();

        Set<String> rowsSeen = new LinkedHashSet<>();
        List<String> half = new ArrayList<>();
        for (Path jar : jars) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                Map<String, Properties> base = new TreeMap<>();
                Map<String, Map<String, Properties>> byLocale = new TreeMap<>();
                for (ZipEntry e : zip.stream().toList()) {
                    String n = e.getName();
                    if (!n.endsWith(".properties") || !n.contains("/Bundle")) {
                        continue;
                    }
                    String pkg = n.substring(0, n.lastIndexOf('/'));
                    String file = n.substring(n.lastIndexOf('/') + 1);
                    if (file.equals("Bundle.properties")) {
                        base.put(pkg, read(zip, e));
                    } else {
                        for (String loc : LOCALES) {
                            if (file.equals("Bundle_" + loc + ".properties")) {
                                byLocale.computeIfAbsent(pkg, k -> new TreeMap<>())
                                        .put(loc, read(zip, e));
                            }
                        }
                    }
                }
                for (Map.Entry<String, Properties> pkg : base.entrySet()) {
                    Properties en = pkg.getValue();
                    Map<String, Properties> locales =
                            byLocale.getOrDefault(pkg.getKey(), Map.of());
                    for (String key : en.stringPropertyNames()) {
                        String descKey = describedBy(key, en);
                        if (descKey == null) {
                            continue;
                        }
                        rowsSeen.add(key);
                        if (SAME_HEADING.containsKey(shortName(key))) {
                            continue;
                        }
                        for (Map.Entry<String, Properties> loc : locales.entrySet()) {
                            Properties tr = loc.getValue();
                            String name = tr.getProperty(key);
                            String desc = tr.getProperty(descKey);
                            if (name == null || desc == null) {
                                continue;
                            }
                            boolean headingIsEnglish = name.equals(en.getProperty(key));
                            boolean lineIsTranslated = !desc.equals(en.getProperty(descKey));
                            if (headingIsEnglish && lineIsTranslated) {
                                half.add(loc.getKey() + " " + shortName(key)
                                        + " = \"" + name + "\" over a translated line");
                            }
                        }
                    }
                }
            }
        }
        assertThat(rowsSeen).as("name/description rows in the shipped bundles")
                .isNotEmpty();
        assertThat(half).as("a heading left in English above a line in the reader's own "
                + "language. Translate the heading, or record in SAME_HEADING why that "
                + "English word IS the right heading for that reader").isEmpty();
    }

    @Test
    @DisplayName("a heading blessed as the same word gives a reason, and the row still exists")
    void everyBlessingIsADecision() throws IOException {
        Set<String> rows = new LinkedHashSet<>();
        for (Path jar : productJars()) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                for (ZipEntry e : zip.stream().toList()) {
                    if (!e.getName().endsWith("/Bundle.properties")) {
                        continue;
                    }
                    Properties en = read(zip, e);
                    for (String key : en.stringPropertyNames()) {
                        if (describedBy(key, en) != null) {
                            rows.add(shortName(key));
                        }
                    }
                }
            }
        }
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, String> e : SAME_HEADING.entrySet()) {
            if (e.getValue().length() < 60) {
                stale.add(e.getKey() + ": a reason this short decides nothing");
            }
            if (!rows.contains(e.getKey())) {
                stale.add(e.getKey() + ": blessed, but no such row ships any more");
            }
        }
        assertThat(stale).as("a blessing nobody can check has stopped being a decision")
                .isEmpty();
    }

    /** The key holding this key's second line, or null when it heads no row. */
    private static String describedBy(String key, Properties en) {
        for (Map.Entry<String, String> half : ROW_HALVES.entrySet()) {
            if (key.endsWith(half.getKey())) {
                String desc = key.substring(0, key.length() - half.getKey().length())
                        + half.getValue();
                if (en.containsKey(desc)) {
                    return desc;
                }
            }
        }
        return null;
    }

    /**
     * A key as a blessing names it — the whole key, not a fragment.
     *
     * <p>The first cut of this gate blessed a row by the text after its last
     * underscore, which for {@code MinimapSideBar_accessibleName} is
     * {@code accessibleName} — a fragment shared by a dozen unrelated rows and
     * matching the blessing's own spelling of none of them. It said so on its
     * first run.
     */
    private static String shortName(String key) {
        return key;
    }

    private static Properties read(ZipFile zip, ZipEntry e) throws IOException {
        Properties p = new Properties();
        try (InputStream in = zip.getInputStream(e)) {
            // NMOX bundles ship raw UTF-8; Properties.load(InputStream) is
            // ISO-8859-1 by contract and would compare its own decoding
            // rather than the product's prose (the v2.129.0 defect)
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    /** The assembled cluster's own module jars — the same home every sibling gate reads. */
    private static List<Path> productJars() throws IOException {
        Path modules = Path.of("target/nmoxstudio/nmoxstudio/modules");
        if (!Files.isDirectory(modules)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(modules)) {
            return s.filter(p -> p.getFileName().toString().startsWith("org-nmox-NMOX-Studio")
                    && p.getFileName().toString().endsWith(".jar")).sorted().toList();
        }
    }
}
