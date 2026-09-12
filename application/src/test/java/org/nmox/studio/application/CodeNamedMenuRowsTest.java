package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The menu rows whose names live in action CODE speak every language.
 *
 * <p>{@link MenuRowsSpeakTest} derives its population by parsing the assembled
 * application's layers: a row declared there names an action that declares a
 * {@code displayName}, and the gate can enumerate every one. These rows declare
 * nothing. Their names are {@code NbBundle} lookups inside the action classes,
 * so the only instrument that finds them is a WALK of a translated build, and
 * the only way to place one is to grep every shipped bundle for a value equal
 * to the English label.
 *
 * <p>That makes this a LEDGER, the {@code PlatformDialogLedgerTest} shape: the
 * population is hand-kept in {@code code-named-menu-rows.txt} and every claim
 * it makes is checked here against the cluster the build just assembled. A
 * hand-kept list cannot prove itself complete — a row nobody has walked is
 * simply absent — but it can be kept HONEST, and these laws do that:
 *
 * <ul>
 *   <li>every recorded key exists in the shipped jar, with exactly the English
 *       value recorded beside it, so a platform upgrade that renames a key or
 *       rewords a label fails the build instead of quietly shipping a
 *       translation nothing reads;
 *   <li>every recorded key is overlaid in all twelve languages;
 *   <li>the mnemonic MIRRORS English — present where English has one, absent
 *       where it does not. That is what makes the letters safe: the platform's
 *       own English menus do not collide, so inheriting the letter inherits
 *       the property;
 *   <li>no two rows of one menu claim the same letter, counting the rows
 *       v2.143.0 already lettered;
 *   <li>a value whose English is a {@code MessageFormat} choice pattern is
 *       still one, with the same branch count, and no value contains a
 *       straight apostrophe (the v2.97.0 hazard: {@code '} opens a quote and
 *       eats the placeholder after it).
 * </ul>
 *
 * <p>The letters themselves are NOT checked here. A mnemonic law that saw only
 * this population could not see a collision with the rows v2.143.0 lettered —
 * and that is the collision the Hindi walk actually found — so both the mirror
 * law and the no-collision law live in {@link MenuRowsSpeakTest}, which reads
 * this ledger alongside the population it derives. One law, one home.
 */
class CodeNamedMenuRowsTest {

    private static final Path CLUSTER = Path.of("target", "nmoxstudio");
    private static final Path OVERLAYS = Path.of("..", "branding", "src", "main",
            "nbm-branding", "modules");

    private static final List<String> LANGS = List.of(
            "es", "fr", "de", "ru", "uk", "pl", "pt", "id", "tl", "vi", "zh", "hi");

    /**
     * Languages whose choice branches are allowed to read alike, because the
     * language has no grammatical plural (the v2.99.0 decision: Indonesian and
     * Chinese say the same noun for one file and for seven). The pattern is
     * still written out so the argument has somewhere to land.
     */
    private static final Set<String> NO_PLURAL = Set.of("id", "zh");

    /** One recorded row: where its name lives, and what English says. */
    private record Row(String menu, String jar, String pkg, String key, String english) {

        String bundle(String lang) {
            return pkg + "/Bundle_" + lang + ".properties";
        }

        /** The mnemonic letter English assigns, or 0 when it assigns none. */
        char letter() {
            int i = english.indexOf('&');
            return i < 0 || i == english.length() - 1 ? 0
                    : Character.toUpperCase(english.charAt(i + 1));
        }
    }

    private static List<Row> ledger() {
        List<Row> rows = new ArrayList<>();
        try (InputStream in = CodeNamedMenuRowsTest.class
                .getResourceAsStream("code-named-menu-rows.txt")) {
            assertThat(in).as("the ledger resource").isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : text.replace("\r\n", "\n").split("\n")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\|", 5);
                assertThat(p).as("ledger line: " + line).hasSize(5);
                rows.add(new Row(p[0], p[1], p[2], p[3], p[4]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }

    /** The shipped jar by file name, found anywhere in the assembled cluster. */
    private static Path findJar(String name) throws IOException {
        try (var walk = Files.walk(CLUSTER)) {
            return walk.filter(p -> p.getFileName().toString().equals(name))
                    .findFirst().orElse(null);
        }
    }

    private static Properties entry(Path jar, String path) throws IOException {
        try (JarFile jf = new JarFile(jar.toFile())) {
            ZipEntry e = jf.getEntry(path);
            if (e == null) {
                return null;
            }
            Properties p = new Properties();
            try (InputStream in = jf.getInputStream(e)) {
                // Properties.load is ISO-8859-1 by contract and these files are
                // written as escaped ASCII, so the two agree (v2.129.0's scar).
                p.load(in);
            }
            return p;
        }
    }

    private static Properties overlay(Row row, String lang) throws IOException {
        Path p = OVERLAYS.resolve(row.jar()).resolve(row.bundle(lang));
        if (!Files.exists(p)) {
            return null;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(p)) {
            props.load(in);
        }
        return props;
    }

    @Test
    @DisplayName("every recorded key still exists, still saying what the ledger records")
    void theLedgerTellsTheTruthAboutTheCluster() throws IOException {
        assertThat(CLUSTER).as("the assembled cluster").exists();
        List<Row> rows = ledger();
        assertThat(rows).as("recorded rows").hasSizeGreaterThan(90);

        List<String> problems = new ArrayList<>();
        Map<String, Path> jars = new HashMap<>();
        for (Row row : rows) {
            Path jar = jars.computeIfAbsent(row.jar(), n -> {
                try {
                    return findJar(n);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            if (jar == null) {
                problems.add(row.key() + ": " + row.jar() + " is not in the cluster");
                continue;
            }
            Properties base = entry(jar, row.pkg() + "/Bundle.properties");
            if (base == null) {
                problems.add(row.key() + ": no bundle at " + row.pkg());
                continue;
            }
            String actual = base.getProperty(row.key());
            if (actual == null) {
                problems.add(row.key() + " [" + row.jar() + "]: the key is gone; the row it "
                        + "names is painted from somewhere else now");
            } else if (!actual.trim().equals(row.english())) {
                problems.add(row.key() + ": the platform now says \"" + actual.trim()
                        + "\", the ledger records \"" + row.english() + "\"");
            }
        }
        assertThat(problems).as("ledger claims the shipped cluster contradicts").isEmpty();
    }

    @Test
    @DisplayName("every recorded row is overlaid in all twelve languages")
    void everyRowSpeaks() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Row row : ledger()) {
            for (String lang : LANGS) {
                Properties p = overlay(row, lang);
                if (p == null) {
                    problems.add(row.key() + " [" + lang + "]: no overlay bundle at "
                            + row.jar() + "/" + row.bundle(lang));
                } else if (p.getProperty(row.key()) == null) {
                    problems.add(row.key() + " [" + lang + "]: not overlaid");
                } else if (p.getProperty(row.key()).isBlank()) {
                    problems.add(row.key() + " [" + lang + "]: overlaid with nothing");
                }
            }
        }
        assertThat(problems).as("menu rows that would read English").isEmpty();
    }

    @Test
    @DisplayName("a choice pattern stays a choice pattern, and no value can eat a placeholder")
    void valuesAreWellFormed() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Row row : ledger()) {
            boolean pattern = row.english().contains("{0");
            for (String lang : LANGS) {
                Properties p = overlay(row, lang);
                String value = p == null ? null : p.getProperty(row.key());
                if (value == null) {
                    continue;
                }
                if (value.indexOf('\'') >= 0) {
                    problems.add(lang + " " + row.key() + ": a straight apostrophe opens a "
                            + "MessageFormat quote; use ’");
                }
                if (!pattern) {
                    if (value.contains("{0")) {
                        problems.add(lang + " " + row.key() + ": an argument English does not have");
                    }
                    continue;
                }
                if (!value.contains("{0,choice,")) {
                    problems.add(lang + " " + row.key() + ": English is a choice pattern and "
                            + "this is not, so the count can never reach the reader");
                    continue;
                }
                try {
                    Set<String> rendered = new TreeSet<>();
                    for (int n : new int[] {0, 1, 2, 7}) {
                        String out = new MessageFormat(value).format(new Object[] {n});
                        if (out.isBlank()) {
                            problems.add(lang + " " + row.key() + ": renders empty at " + n);
                        }
                        rendered.add(out);
                    }
                    if (rendered.size() < 2 && !NO_PLURAL.contains(lang)) {
                        problems.add(lang + " " + row.key() + ": every branch reads the same, so "
                                + "the choice decides nothing — say so in the ledger or drop it");
                    }
                } catch (IllegalArgumentException e) {
                    problems.add(lang + " " + row.key() + ": not a MessageFormat pattern ("
                            + e.getMessage() + ")");
                }
            }
        }
        assertThat(problems).as("values the renderer would mangle").isEmpty();
    }
}
