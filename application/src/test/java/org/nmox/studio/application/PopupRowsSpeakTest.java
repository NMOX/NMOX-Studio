package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rows a right-click paints speak every language.
 *
 * <p>This is the third population of the same surface and the only one that
 * could not be SEEN. {@code MenuRowsSpeakTest} derives its rows from the
 * layer; {@code CodeNamedMenuRowsTest} keeps by hand the rows whose names live
 * in action code, checked against the cluster. A context menu is worse than
 * both: it is assembled at popup time from {@code Editors/<mime>/Popup}, and a
 * right-click on a Swing-painted editor pane is a gesture this automation has
 * never delivered, so no walk could ever confirm what it says.
 *
 * <p>{@code PopupCensus} (v2.145.0) is the answer — it asks the platform to
 * build the popup and reads the items — and its first run paid for itself
 * twice. The fold family paints the bare action id in a popup while the menu
 * bar paints the {@code _menu_text} sibling, and {@code Go to Declaration}
 * turned out to paint a FOURTH spelling of one action's name. Both corrections
 * came from the instrument after the guess had already been written.
 *
 * <p>The laws here are the ledger's, not the census's: a hand-kept population
 * cannot prove itself complete, but every claim in it is checked against the
 * cluster this build assembled.
 *
 * <ul>
 *   <li>every recorded key still exists in its shipped jar, saying exactly
 *       what the ledger records — so a platform upgrade that renames a key or
 *       rewords a label fails the build rather than quietly shipping a
 *       translation the popup will never read;
 *   <li>every recorded key is overlaid in all twelve languages;
 *   <li>no value invents a mnemonic English does not assign (the v2.144.0 law:
 *       the platform's own English does not collide, so inheriting its letters
 *       inherits that property);
 *   <li>no value carries a straight apostrophe, which would open a
 *       {@code MessageFormat} quote.
 * </ul>
 */
class PopupRowsSpeakTest {

    private static final Path CLUSTER = Path.of("target", "nmoxstudio");
    private static final Path OVERLAYS = Path.of("..", "branding", "src", "main",
            "nbm-branding", "modules");

    private static final List<String> LANGS = List.of(
            "es", "fr", "de", "ru", "uk", "pl", "pt", "id", "tl", "vi", "zh", "hi");

    private record Row(String jar, String pkg, String key, String english) {

        String bundle(String lang) {
            return pkg + "/Bundle_" + lang + ".properties";
        }

        boolean hasMnemonic() {
            int i = english.indexOf('&');
            return i >= 0 && i < english.length() - 1;
        }
    }

    private static List<Row> ledger() {
        List<Row> rows = new ArrayList<>();
        try (InputStream in = PopupRowsSpeakTest.class.getResourceAsStream("popup-rows.txt")) {
            assertThat(in).as("the popup ledger resource").isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : text.replace("\r\n", "\n").split("\n")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\|", 4);
                assertThat(p).as("ledger line: " + line).hasSize(4);
                rows.add(new Row(p[0], p[1], p[2], p[3]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }

    /** The shipped jar by name, found anywhere in the cluster — never one path. */
    private static Path findJar(String name) throws IOException {
        try (var walk = Files.walk(CLUSTER)) {
            return walk.filter(p -> p.getFileName().toString().equals(name))
                    // a locale sibling carries the same name with a suffix, so an
                    // exact match is enough; the translated jars never collide
                    .findFirst().orElse(null);
        }
    }

    private static Properties baseBundle(Path jar, String pkg) throws IOException {
        try (JarFile jf = new JarFile(jar.toFile())) {
            ZipEntry e = jf.getEntry(pkg + "/Bundle.properties");
            if (e == null) {
                return null;
            }
            Properties p = new Properties();
            try (InputStream in = jf.getInputStream(e)) {
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
        assertThat(rows).as("recorded popup rows").hasSizeGreaterThan(40);

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
            Properties base = baseBundle(jar, row.pkg());
            if (base == null) {
                problems.add(row.key() + ": no bundle at " + row.pkg());
                continue;
            }
            String actual = base.getProperty(row.key());
            if (actual == null) {
                problems.add(row.key() + " [" + row.jar() + "]: the key is gone; the popup row "
                        + "it names is painted from somewhere else now");
            } else if (!actual.trim().equals(row.english())) {
                problems.add(row.key() + ": the platform now says \"" + actual.trim()
                        + "\", the ledger records \"" + row.english() + "\"");
            }
        }
        assertThat(problems).as("ledger claims the shipped cluster contradicts").isEmpty();
    }

    @Test
    @DisplayName("every recorded popup row is overlaid in all twelve languages")
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
        assertThat(problems).as("popup rows a reader would meet in English").isEmpty();
    }

    @Test
    @DisplayName("no popup row invents a mnemonic English does not assign, or opens a quote")
    void valuesAreWellFormed() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Row row : ledger()) {
            for (String lang : LANGS) {
                Properties p = overlay(row, lang);
                String value = p == null ? null : p.getProperty(row.key());
                if (value == null) {
                    continue;   // everyRowSpeaks owns that verdict
                }
                if (value.indexOf('\'') >= 0) {
                    problems.add(lang + " " + row.key() + ": a straight apostrophe opens a "
                            + "MessageFormat quote; use ’");
                }
                int i = value.indexOf('&');
                boolean ours = i >= 0 && i < value.length() - 1;
                if (ours && !row.hasMnemonic()) {
                    problems.add(lang + " " + row.key() + ": English assigns no mnemonic and "
                            + "this invents one (\"" + value + "\")");
                }
            }
        }
        assertThat(problems).as("popup values the renderer would mangle").isEmpty();
    }
}
