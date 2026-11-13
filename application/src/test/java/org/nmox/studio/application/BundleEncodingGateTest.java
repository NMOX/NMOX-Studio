package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No shipped string is UTF-8 that was decoded twice.
 *
 * <p>v2.150.1: the NPM Explorer's English sentence reached the jar as
 * {@code {0} is already running â Stop Script…}. The source file was correct
 * UTF-8. {@code tools/npm} keeps a hand-written {@code Bundle.properties} beside
 * {@code @NbBundle.Messages}, and the annotation processor merges the hand file
 * into the generated bundle as ISO-8859-1. Each byte of the em dash became its
 * own character, {@code â}, and that is what users read.
 * v2.102.1 had recorded exactly this as a transient, misattributed failure.
 *
 * <p>The defect is invisible in the source and in every source-reading gate.
 * Only the assembled jar shows it, so the population is the ASSEMBLED cluster:
 * every {@code Bundle*.properties} in every NMOX jar, English and all
 * translations, product bundles and platform overlays alike.
 *
 * <p>The signature is precise. UTF-8 read as Latin-1 turns every continuation
 * byte into a C1 control character (U+0080–U+009F), which no real sentence
 * contains. It turns a two-byte Latin letter into {@code Ã} followed by a
 * Latin-1 supplement character ({@code Ã©} for {@code é}), which no language
 * we ship writes either.
 */
class BundleEncodingGateTest {

    private static final Path CLUSTER = Path.of("target", "nmoxstudio");
    private static final Pattern DOUBLE_DECODED = Pattern.compile("[\\u0080-\\u009f]|\\u00c3[\\u00a0-\\u00bf]");

    @Test
    @DisplayName("no shipped bundle value is UTF-8 decoded twice")
    void noValueIsDoubleDecoded() throws IOException {
        List<String> broken = new ArrayList<>();
        int bundles = 0;
        for (Path jar : jars()) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                for (ZipEntry e : zip.stream().toList()) {
                    String n = e.getName();
                    if (!n.endsWith(".properties") || !n.substring(n.lastIndexOf('/') + 1).startsWith("Bundle")) {
                        continue;
                    }
                    bundles++;
                    Properties p = new Properties();
                    try (InputStream in = zip.getInputStream(e)) {
                        p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                    }
                    for (String key : p.stringPropertyNames()) {
                        String value = p.getProperty(key);
                        Matcher m = DOUBLE_DECODED.matcher(value);
                        if (m.find()) {
                            int from = Math.max(0, m.start() - 24);
                            broken.add(jar.getFileName() + "!" + n + " " + key + " … "
                                    + escape(value.substring(from, Math.min(value.length(), m.end() + 12))));
                        }
                    }
                }
            }
        }
        assertThat(bundles).as("bundles read from the assembled cluster — a scan that reads none "
                + "finds every file perfectly encoded").isGreaterThan(500);
        assertThat(broken).as("values carrying double-decoded UTF-8: a hand-written Bundle.properties "
                + "merged by the @Messages processor must hold only ASCII (write \\uXXXX)").isEmpty();
    }

    /**
     * No shipped string prints its own line break.
     *
     * <p>3.11.0's two most serious questions — "Billable: create a server?" and
     * "Destroy a server" — reached the user as {@code Destroy alpha at
     * DigitalOcean?\n\nEverything on it is gone for good}, the backslash and the
     * {@code n} printed as characters, in English and in fourteen translations.
     * The {@code @Messages} strings were written with a DOUBLED backslash; the
     * processor escaped it faithfully, and the translations were copied from the
     * English. Found when the next release's bundle was read for its translators.
     * Every other message in the product writes one backslash; nothing compared.
     *
     * <p>A value that MEANS a backslash followed by {@code n} or {@code t} (a
     * Windows path in a sentence, a regular expression shown to the user) would
     * be a real exception and is to be named here with its reason. There is none.
     */
    @Test
    @DisplayName("no shipped bundle value shows a line break or a tab as backslash-and-letter")
    void noValuePrintsItsOwnLineBreak() throws IOException {
        Pattern escaped = Pattern.compile("\\\\[nt](?![A-Za-z0-9])");
        List<String> broken = new ArrayList<>();
        int bundles = 0;
        for (Path jar : jars()) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                for (ZipEntry e : zip.stream().toList()) {
                    String n = e.getName();
                    if (!n.endsWith(".properties") || !n.substring(n.lastIndexOf('/') + 1).startsWith("Bundle")) {
                        continue;
                    }
                    bundles++;
                    Properties p = new Properties();
                    try (InputStream in = zip.getInputStream(e)) {
                        p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                    }
                    for (String key : p.stringPropertyNames()) {
                        Matcher m = escaped.matcher(p.getProperty(key));
                        if (m.find()) {
                            String value = p.getProperty(key);
                            broken.add(jar.getFileName() + "!" + n + " " + key + " … "
                                    + value.substring(Math.max(0, m.start() - 24), Math.min(value.length(), m.end() + 12)));
                        }
                    }
                }
            }
        }
        assertThat(bundles).as("bundles read from the assembled cluster").isGreaterThan(500);
        assertThat(broken).as("values a reader will see a literal \\n or \\t in: write ONE backslash in the "
                + "@Messages string and in the .properties file").isEmpty();
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            b.append(c < 0x20 || (c >= 0x7f && c <= 0x9f) ? String.format("\\u%04x", (int) c) : String.valueOf(c));
        }
        return b.toString();
    }

    private static List<Path> jars() throws IOException {
        if (!Files.isDirectory(CLUSTER)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(CLUSTER)) {
            return s.filter(p -> {
                String f = p.getFileName().toString();
                return f.endsWith(".jar") && (f.startsWith("org-nmox-NMOX-Studio") || f.contains("_nmoxstudio_"));
            }).sorted().toList();
        }
    }

    /**
     * A platform overlay is escaped ASCII, every one.
     *
     * <p>The overlays under {@code branding/src/main/nbm-branding} are read by
     * a dozen gates with {@code Properties.load(InputStream)}, which is
     * ISO-8859-1 by contract; their comments say the files are "written as
     * escaped ASCII, so the two agree". 124 of 2,159 were not (102 of them
     * Arabic, written raw by the translator of v2.152.0 and by every later
     * hand that kept its neighbour's encoding). The platform reads raw UTF-8
     * correctly, so nothing was wrong on screen (walked in Spanish, 3.13.0:
     * <em>Repetir compilación/ejecución</em>), but those gates were judging
     * Arabic as mojibake, and their laws about it held by accident. Measured
     * in BYTES: a first count with a regex over code points U+0080 to U+00FF
     * found six files and missed every Arabic one.
     */
    @Test
    @DisplayName("every platform overlay under nbm-branding is escaped ASCII, so a Latin-1 reader and the platform agree")
    void overlaysAreEscapedAscii() throws IOException {
        Path overlays = Path.of("..", "branding", "src", "main", "nbm-branding");
        assertThat(overlays).as("the platform overlays").isDirectory();
        List<String> raw = new ArrayList<>();
        int files = 0;
        try (Stream<Path> walk = Files.walk(overlays)) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".properties")).toList()) {
                files++;
                for (byte b : Files.readAllBytes(p)) {
                    if (b < 0) {
                        raw.add(overlays.relativize(p).toString().replace('\\', '/'));
                        break;
                    }
                }
            }
        }
        assertThat(files).as("the overlay population (a walk that finds nothing proves nothing)")
                .isGreaterThan(2000);
        assertThat(raw).as("overlays holding a raw non-ASCII byte: write the character as a \\uXXXX escape")
                .isEmpty();
    }
}
