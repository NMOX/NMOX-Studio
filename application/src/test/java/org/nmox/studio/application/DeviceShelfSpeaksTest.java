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
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every device on the shelf describes itself in the reader's language, in
 * the space the card actually has.
 *
 * <p>The shelf is a catalogue: a person reads 53 short descriptions to
 * choose a device. Those lived only in {@code DeviceType}'s English, so a
 * fully translated build showed a German heading over English cards — no
 * l10n gate could see it, because the English never passed through a
 * bundle (the ledger-88 shape; v2.130.0's seed class one surface over).
 *
 * <p>Two laws, and the second is why the first is not enough. COMPLETE: a
 * device the product ships must be described in all twelve languages —
 * the population is the catalogue itself, so a device added tomorrow fails
 * the build until it is described. SHORT ENOUGH: the card paints ONE line
 * of the gloss, and the first German photograph read
 * "…OK auslösen, wenn alle Spuren bes" — hard-clipped, no ellipsis.
 * {@code PalettePanel.fitTo} now elides honestly, but a shelf where half
 * the cards trail off is a worse catalogue than one written to fit.
 *
 * <p>Bound to {@code packaged-app-gates}: the population comes from the
 * ASSEMBLED cluster's own device catalogue and bundles.
 */
class DeviceShelfSpeaksTest {

    private static final Path MODULES = Path.of("target", "nmoxstudio", "nmoxstudio", "modules");

    private static final Path REPO = Path.of("..");

    private static final List<String> LOCALES =
            List.of("es", "fr", "de", "ru", "uk", "pl", "pt", "id", "tl", "vi", "zh", "hi");

    /**
     * Characters of gloss the card paints before it must elide, measured
     * against the real 210 px card at the shelf's tiny font. Generous: the
     * widest scripts here run narrower per character than Latin.
     */
    private static final int GLOSS_BUDGET = 42;

    private static final Pattern ENUM_ROW = Pattern.compile(
            "(?m)^\\s{4}[A-Z_0-9]+\\(\"([a-z0-9-]+)\",\\s*\"[^\"]+\",\\s*\"([^\"]+)\"");

    @Test
    @DisplayName("every shipped device is described in all twelve languages")
    void everyDeviceSpeaks() throws IOException {
        List<String> ids = shippedDeviceIds();
        assertThat(ids).as("devices the product ships").hasSizeGreaterThan(50);

        List<String> missing = new ArrayList<>();
        for (String locale : LOCALES) {
            Properties shelf = shelfBundle(locale);
            for (String id : ids) {
                if (shelf.getProperty("DeviceDesc_" + id) == null) {
                    missing.add(locale + ": " + id);
                }
            }
        }
        assertThat(missing).as("devices whose shelf card would read English "
                + "under a translated heading").isEmpty();
    }

    @Test
    @DisplayName("every gloss fits the card it is painted on")
    void everyGlossFits() throws IOException {
        List<String> over = new ArrayList<>();
        int measured = 0;
        for (String locale : LOCALES) {
            Properties shelf = shelfBundle(locale);
            for (String key : shelf.stringPropertyNames()) {
                if (!key.startsWith("DeviceDesc_")) {
                    continue;
                }
                String value = shelf.getProperty(key);
                int dash = value.indexOf('—');
                assertThat(dash).as("%s [%s] should read \"Noun — what it does\"", key, locale)
                        .isGreaterThan(0);
                String gloss = value.substring(dash + 1).strip();
                measured++;
                if (gloss.length() > GLOSS_BUDGET) {
                    over.add(locale + " " + key + ": " + gloss.length() + " > "
                            + GLOSS_BUDGET + " — " + gloss);
                }
            }
        }
        assertThat(measured).as("the gate should measure every device in every language")
                .isGreaterThan(600);
        assertThat(over).as("a gloss wider than its card trails off where the reader "
                + "is scanning; write a shorter one").isEmpty();
    }

    /** Device ids as the product ships them: the enum plus the bundled gallery. */
    private static List<String> shippedDeviceIds() throws IOException {
        List<String> ids = new ArrayList<>();
        String enumSource = Files.readString(REPO.resolve("rack/src/main/java/org/nmox/studio"
                + "/rack/devices/DeviceType.java"), StandardCharsets.UTF_8);
        Matcher m = ENUM_ROW.matcher(enumSource);
        while (m.find()) {
            ids.add(m.group(1));
        }
        // the gallery ships installed and active (v2.1.0), so its cards are
        // the product speaking too — a user's own drop-in is not, and has no
        // key here on purpose
        Path gallery = REPO.resolve("examples/devices.d");
        try (Stream<Path> files = Files.list(gallery)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                Matcher id = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"")
                        .matcher(Files.readString(f, StandardCharsets.UTF_8));
                if (id.find()) {
                    ids.add(id.group(1));
                }
            }
        }
        return ids;
    }

    /** The shelf's own bundle, from the assembled cluster. */
    private static Properties shelfBundle(String locale) throws IOException {
        assertThat(MODULES).as("the assembled cluster's modules").isDirectory();
        String entry = "org/nmox/studio/rack/ui/Bundle_" + locale + ".properties";
        try (Stream<Path> jars = Files.list(MODULES)) {
            for (Path jarPath : jars.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (JarFile jar = new JarFile(jarPath.toFile())) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry e = entries.nextElement();
                        if (!e.getName().equals(entry)) {
                            continue;
                        }
                        Properties props = new Properties();
                        try (InputStream in = jar.getInputStream(e)) {
                            // UTF-8: Properties.load(InputStream) is ISO-8859-1
                            // by contract and these bundles ship raw UTF-8
                            // (v2.129.0 — a gate that measured its own decoding)
                            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                        }
                        return props;
                    }
                }
            }
        }
        throw new AssertionError("no shipped " + entry + " — the shelf lost a language");
    }
}
