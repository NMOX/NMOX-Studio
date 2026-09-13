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
import java.util.Set;
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

    /**
     * The population is the LAYER's, not a hand-picked sample.
     *
     * <p>v2.145.0 censused twelve mimes chosen by hand and translated what they
     * showed. The layer states the real population — every
     * {@code Editors/<mime>/Popup} that adds rows beyond the root — and it can
     * be parsed, so the sample was never the population. Widening it to all 84
     * found four more English rows, one of which had a ledger entry already:
     * {@code text/xhtml} paints openide-AWT's {@code View} while
     * {@code text/xml} paints openide-ACTIONS' — the same word, two bundles,
     * and only the surface says which.
     *
     * <p>So the ledger records the population and this law re-derives it from
     * the cluster every build. A platform upgrade that gives a new mime a popup
     * fails HERE, named, instead of shipping an English row nobody looked at.
     */
    @Test
    @DisplayName("the mime ledger names every mime whose popup the layer extends")
    void thePopulationIsDerivedNotSampled() throws IOException {
        assertThat(CLUSTER).as("the assembled cluster").exists();

        Map<String, String> declared = new HashMap<>();          // mime -> bucket
        Map<String, String> reason = new HashMap<>();
        try (InputStream in = PopupRowsSpeakTest.class.getResourceAsStream("popup-mimes.txt")) {
            assertThat(in).as("the popup mime ledger").isNotNull();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").split("\n")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\|", 4);
                assertThat(p).as("ledger line: " + line).hasSizeGreaterThanOrEqualTo(3);
                declared.put(p[0], p[1]);
                reason.put(p[0], p.length > 3 ? p[3] : "");
            }
        }

        Set<String> adders = mimesWhosePopupTheLayerExtends();
        List<String> problems = new ArrayList<>();
        for (String mime : adders) {
            if (!declared.containsKey(mime)) {
                problems.add(mime + ": the layer gives this mime popup rows and the "
                        + "ledger does not name it — census it, or record why no file reaches it");
            }
        }
        for (String mime : declared.keySet()) {
            if (!adders.contains(mime)) {
                problems.add(mime + ": the ledger names it, but the layer gives it no "
                        + "popup rows of its own any more — drop the row");
            }
        }
        // a bucket that is not DERIVED is a DECISION, and a decision carries a reason
        for (Map.Entry<String, String> e : declared.entrySet()) {
            if (!"DERIVED".equals(e.getValue()) && reason.get(e.getKey()).isBlank()) {
                problems.add(e.getKey() + " is " + e.getValue()
                        + " with no reason written down; a person must be able to disagree with it");
            }
        }
        assertThat(problems).as("the popup census population").isEmpty();
        assertThat(adders).as("the layer's own popup-extending mimes").hasSizeGreaterThan(60);
    }

    /** Every {@code Editors/<mime>/Popup} in the cluster that adds rows beyond the root. */
    private static Set<String> mimesWhosePopupTheLayerExtends() throws IOException {
        Map<String, Set<String>> rows = new HashMap<>();
        try (var walk = Files.walk(CLUSTER)) {
            for (Path jar : walk.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (JarFile jf = new JarFile(jar.toFile())) {
                    for (var en = jf.entries(); en.hasMoreElements();) {
                        ZipEntry e = en.nextElement();
                        if (!e.getName().endsWith("layer.xml")) {
                            continue;
                        }
                        try (InputStream in = jf.getInputStream(e)) {
                            readPopupFolders(in, rows);
                        } catch (RuntimeException ignored) {
                            // a layer this parser cannot read is not a popup claim
                        }
                    }
                } catch (IOException ignored) {
                    // not every file under the cluster is a readable jar
                }
            }
        }
        Set<String> root = rows.getOrDefault("", Set.of());
        Set<String> out = new java.util.TreeSet<>();
        rows.forEach((mime, names) -> {
            if (!mime.isEmpty() && !root.containsAll(names)) {
                out.add(mime);
            }
        });
        return out;
    }

    private static void readPopupFolders(InputStream in, Map<String, Set<String>> rows) {
        org.w3c.dom.Document doc;
        try {
            javax.xml.parsers.DocumentBuilderFactory f =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            // a layer names the DTD it was written against; never fetch it
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setValidating(false);
            doc = f.newDocumentBuilder().parse(in);
        } catch (Exception e) {
            return;
        }
        collect(doc.getDocumentElement(), "", rows);
    }

    private static void collect(org.w3c.dom.Element el, String path, Map<String, Set<String>> rows) {
        var kids = el.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (!(kids.item(i) instanceof org.w3c.dom.Element c) || !"folder".equals(c.getTagName())) {
                continue;
            }
            String name = c.getAttribute("name");
            String here = path.isEmpty() ? name : path + "/" + name;
            if ("Popup".equals(name) && path.startsWith("Editors")) {
                // "Editors" -> the root popup; "Editors/text/html" -> that mime's
                String mime = path.length() > "Editors".length()
                        ? path.substring("Editors/".length()) : "";
                rows.computeIfAbsent(mime, k -> new java.util.HashSet<>()).addAll(namesUnder(c));
            }
            collect(c, here, rows);
        }
    }

    private static Set<String> namesUnder(org.w3c.dom.Element folder) {
        Set<String> out = new java.util.HashSet<>();
        var kids = folder.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof org.w3c.dom.Element c) {
                out.add(c.getAttribute("name"));
            }
        }
        return out;
    }
}
