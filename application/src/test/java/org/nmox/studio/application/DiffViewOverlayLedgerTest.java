package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
 * The platform's diff view speaks the reader's language (3.2.0).
 *
 * <p>3.2.0 made that view a front door: {@code nmox -d left right} and
 * {@code git difftool} open two files in it ({@code DiffWindow} embeds
 * {@code DiffController.createEnhanced}, which is the platform's
 * {@code EditableDiffView}), and the Team menu's diffs are the same view. The
 * platform ships no localization, so its tabs, its tooltips, its placeholder
 * panels and the names a screen reader says read English in all fourteen
 * translated builds. They are overlaid in the branding+locale slot like every
 * other platform surface.
 *
 * <p><b>The population is derived, not picked.</b> v2.142.0's rule is to
 * overlay only what a surface PAINTS, and that was decided here by reading the
 * bytecode: a key the view can paint is a key some class of the view's package
 * names in its constant pool. So this gate reads the shipped jar, takes every
 * key of that package's {@code Bundle.properties} a class there names, and
 * requires each one in {@code diff-view-rows.txt} as OVERLAID (with its exact
 * English) or RECORDED (with the reason no reader meets it). A platform upgrade
 * that adds a painted key, drops one or rewords one fails here by name instead
 * of shipping an English word in a translated window.
 *
 * <p>No picture proves this: a branding overlay applies only inside the
 * assembled app, and the diff view needs two files and a running window. The
 * cluster is the proof — the key the view reads is there, saying what the
 * ledger records, and the overlay answers it in every language.
 */
class DiffViewOverlayLedgerTest {

    private static final Path CLUSTER = Path.of("target", "nmoxstudio");
    private static final String JAR = "org-netbeans-modules-diff.jar";
    private static final String PKG = "org/netbeans/modules/diff/builtin/visualizer/editable";
    private static final Path OVERLAY = Path.of("..", "branding", "src", "main", "nbm-branding",
            "modules", JAR).resolve(PKG);

    private static final List<String> LANGS = ShippedLocales.TRANSLATED;

    private record Row(String bucket, String key, String text) {}

    private static Map<String, Row> ledger() throws IOException {
        Map<String, Row> rows = new LinkedHashMap<>();
        try (InputStream in = DiffViewOverlayLedgerTest.class.getResourceAsStream("diff-view-rows.txt")) {
            assertThat(in).as("the diff view ledger resource").isNotNull();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\|", 3);
                assertThat(p).as("ledger line: " + line).hasSize(3);
                assertThat(p[0]).as("bucket of " + p[1]).isIn("OVERLAID", "RECORDED");
                assertThat(rows.put(p[1], new Row(p[0], p[1], p[2])))
                        .as("a key recorded twice: " + p[1]).isNull();
            }
        }
        return rows;
    }

    private static Path diffJar() throws IOException {
        assertThat(CLUSTER).as("the assembled cluster (build from the root first)").exists();
        try (var walk = Files.walk(CLUSTER)) {
            Path jar = walk.filter(p -> p.getFileName().toString().equals(JAR)).findFirst().orElse(null);
            assertThat(jar).as(JAR + " in the assembled cluster").isNotNull();
            return jar;
        }
    }

    /** The view package's shipped English bundle. */
    private static Properties shippedBundle(JarFile jf) throws IOException {
        ZipEntry e = jf.getEntry(PKG + "/Bundle.properties");
        assertThat(e).as(PKG + "/Bundle.properties in " + JAR).isNotNull();
        Properties p = new Properties();
        try (InputStream in = jf.getInputStream(e)) {
            p.load(in);
        }
        return p;
    }

    /** Every string a class directly in the view package names in its constant pool. */
    private static Set<String> namedByTheView(JarFile jf) throws IOException {
        Set<String> out = new TreeSet<>();
        for (ZipEntry e : Collections.list(jf.entries())) {
            String n = e.getName();
            if (n.startsWith(PKG + "/") && n.endsWith(".class") && n.indexOf('/', PKG.length() + 1) < 0) {
                try (InputStream in = jf.getInputStream(e)) {
                    out.addAll(FileTreeRowsSpeakTest.utf8Constants(in.readAllBytes()));
                }
            }
        }
        return out;
    }

    private static Properties overlay(String lang) throws IOException {
        Path p = OVERLAY.resolve("Bundle_" + lang + ".properties");
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
    @DisplayName("every key the view can paint is in the ledger, and every ledger key is one it can paint")
    void thePopulationIsDerivedFromTheShippedView() throws IOException {
        Map<String, Row> rows = ledger();
        List<String> problems = new ArrayList<>();
        try (JarFile jf = new JarFile(diffJar().toFile())) {
            Properties bundle = shippedBundle(jf);
            Set<String> named = namedByTheView(jf);
            Set<String> paintable = new TreeSet<>();
            for (String key : bundle.stringPropertyNames()) {
                if (named.contains(key)) {
                    paintable.add(key);
                }
            }
            assertThat(paintable).as("the constant-pool scan found the view's keys at all")
                    .contains("EditableDiffView.viewGraphical.title", "TT_DiffPanel_JumpToCurrent");

            for (String key : paintable) {
                if (!rows.containsKey(key)) {
                    problems.add(key + " = \"" + bundle.getProperty(key).trim() + "\": the diff view names "
                            + "this key and the ledger does not; read what paints it, then overlay it in "
                            + "every language or record why no reader meets it");
                }
            }
            for (Row row : rows.values()) {
                if (!paintable.contains(row.key())) {
                    problems.add(row.key() + ": the ledger records it, but no class of the shipped view "
                            + "names it any more (or the bundle lost it); drop the row and its overlays");
                } else if ("OVERLAID".equals(row.bucket())
                        && !bundle.getProperty(row.key()).trim().equals(row.text())) {
                    problems.add(row.key() + ": the platform now says \"" + bundle.getProperty(row.key()).trim()
                            + "\", the ledger records \"" + row.text() + "\"; re-translate");
                } else if ("RECORDED".equals(row.bucket()) && row.text().isBlank()) {
                    problems.add(row.key() + " is RECORDED with no reason; a person must be able to "
                            + "disagree with it");
                }
            }
        }
        assertThat(problems).as("the diff view's keys against the shipped jar").isEmpty();
    }

    @Test
    @DisplayName("every overlaid key speaks every translated language, well formed")
    void everyOverlaidKeySpeaks() throws IOException {
        Map<String, Row> rows = ledger();
        List<String> problems = new ArrayList<>();
        for (String lang : LANGS) {
            Properties p = overlay(lang);
            if (p == null) {
                problems.add(lang + ": no overlay bundle at " + OVERLAY.resolve("Bundle_" + lang + ".properties"));
                continue;
            }
            for (Row row : rows.values()) {
                if (!"OVERLAID".equals(row.bucket())) {
                    continue;
                }
                String value = p.getProperty(row.key());
                if (value == null) {
                    problems.add(row.key() + " [" + lang + "]: not overlaid");
                } else if (value.isBlank()) {
                    problems.add(row.key() + " [" + lang + "]: overlaid with nothing");
                } else if (value.indexOf('\'') >= 0) {
                    problems.add(row.key() + " [" + lang + "]: a straight apostrophe; use ’");
                } else if (value.contains("&") && !row.text().contains("&")) {
                    problems.add(row.key() + " [" + lang + "]: English assigns no mnemonic and this "
                            + "invents one (\"" + value + "\")");
                }
            }
            for (String key : p.stringPropertyNames()) {
                Row row = rows.get(key);
                if (row == null || !"OVERLAID".equals(row.bucket())) {
                    problems.add(key + " [" + lang + "]: overlaid, but the ledger does not say the view "
                            + "paints it");
                }
            }
        }
        assertThat(problems).as("diff view words a reader would meet in English").isEmpty();
    }
}
