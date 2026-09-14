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
}
