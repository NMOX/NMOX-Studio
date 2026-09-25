package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every Russian and Ukrainian mnemonic reaches a key (after 3.2.0, ledger
 * 122). The platform's {@code org.openide.awt.Mnemonics} gives a key only to
 * A-Z and 0-9 and asks a {@code Mnemonics_<lang>.properties} table for any
 * other letter; until the branding module shipped one in
 * {@code modules/locale/org-openide-awt_<lang>.jar}, about 340 values in
 * each language underlined a Cyrillic letter that no key reached.
 *
 * <p>The outcome is measured the way the product meets it: the platform's
 * own {@code Mnemonics} class, loaded from the assembled cluster together
 * with that locale jar, is handed every value in the cluster that
 * underlines a letter outside ASCII, under that language's default locale,
 * and must give each one a key. The population is the cluster's own
 * bundles (our modules and the platform overlays), so a translation that
 * underlines a letter the table lacks fails by name.
 */
class CyrillicMnemonicsTest {

    private static final Path CLUSTER = Path.of("target", "nmoxstudio");
    private static final Path MODULES = CLUSTER.resolve("nmoxstudio").resolve("modules");

    /** A mnemonic: an ampersand before a letter, not a doubled one. */
    private static final Pattern MNEMONIC = Pattern.compile("(?<!&)&(\\p{L})");

    @ParameterizedTest
    @ValueSource(strings = {"ru", "uk"})
    @DisplayName("every mnemonic the language underlines reaches a key through the shipped table")
    void everyMnemonicReachesAKey(String lang) throws Exception {
        List<String[]> values = underlined(lang);
        assertThat(values).as("%s values that underline a non-ASCII letter, read from the cluster", lang)
                .hasSizeGreaterThan(200);
        List<String> dead = new ArrayList<>();
        TreeMap<Integer, Integer> keys = new TreeMap<>();
        withMnemonics(lang, set -> {
            for (String[] v : values) {
                javax.swing.JMenuItem item = new javax.swing.JMenuItem();
                set.invoke(null, item, v[1]);
                if (item.getMnemonic() == 0) {
                    dead.add(v[0] + ": \"" + v[1] + "\"");
                }
                keys.merge(item.getMnemonic(), 1, Integer::sum);
            }
            return null;
        });
        assertThat(dead).as("%s mnemonics that underline a letter no key reaches — "
                + "add the letter to branding/src/main/nbm-mnemonics/%s", lang, lang).isEmpty();
        assertThat(keys.size()).as("distinct keys the %s mnemonics reach", lang).isGreaterThan(20);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "uk"})
    @DisplayName("a letter reaches the key it sits on in the language's own layout")
    void theKeyIsTheLayoutPosition(String lang) throws Exception {
        withMnemonics(lang, set -> {
            for (String[] c : new String[][] {{"&Файл", "A"}, {"&Правка", "G"}, {"&Вид", "D"},
                    {"С&правка", "G"}, {"&Окно", "J"}, {"&Найти", "Y"}}) {
                javax.swing.JMenuItem item = new javax.swing.JMenuItem();
                set.invoke(null, item, c[0]);
                assertThat(item.getMnemonic()).as(lang + " " + c[0]).isEqualTo((int) c[1].charAt(0));
                assertThat(item.getDisplayedMnemonicIndex()).as("the underline stays on the letter written")
                        .isEqualTo(c[0].indexOf('&'));
            }
            javax.swing.JMenuItem item = new javax.swing.JMenuItem();
            set.invoke(null, item, lang.equals("uk") ? "&Історія" : "&Ыы");
            assertThat(item.getMnemonic()).as("the letter each language has on S").isEqualTo('S');
            return null;
        });
    }

    private interface Body {
        Object run(Method setLocalizedText) throws Exception;
    }

    /**
     * Runs {@code body} with the platform's {@code Mnemonics} loaded from the
     * cluster beside {@code org-openide-awt_<lang>.jar}, under that
     * language's default locale and a look and feel that has mnemonics
     * (Aqua turns them off entirely). The loader's parent is the JDK's, so
     * the class cannot come from the test's own classpath without the table.
     */
    private static void withMnemonics(String lang, Body body) throws Exception {
        List<URL> jars = new ArrayList<>();
        try (Stream<Path> s = Files.walk(CLUSTER)) {
            for (Path jar : s.filter(p -> {
                String n = p.getFileName().toString();
                return n.equals("org-openide-awt.jar") || n.equals("org-openide-util.jar")
                        || n.equals("org-openide-util-ui.jar") || n.equals("org-openide-util-lookup.jar")
                        || n.equals("org-openide-awt_" + lang + ".jar");
            }).toList()) {
                jars.add(jar.toUri().toURL());
            }
        }
        assertThat(jars).as("the platform's awt and util jars and the %s table in the cluster", lang).hasSize(5);
        Locale before = Locale.getDefault();
        javax.swing.LookAndFeel laf = javax.swing.UIManager.getLookAndFeel();
        javax.swing.UIManager.setLookAndFeel(new javax.swing.plaf.metal.MetalLookAndFeel());
        Locale.setDefault(Locale.forLanguageTag(lang));
        try (URLClassLoader cl = new URLClassLoader(jars.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            body.run(Class.forName("org.openide.awt.Mnemonics", true, cl)
                    .getMethod("setLocalizedText", javax.swing.AbstractButton.class, String.class));
        } finally {
            Locale.setDefault(before);
            javax.swing.UIManager.setLookAndFeel(laf);
        }
    }

    /** {jar!entry key, value} for every {@code lang} value that underlines a letter outside ASCII. */
    private static List<String[]> underlined(String lang) throws IOException {
        Pattern bundle = Pattern.compile("(?:^|/)[A-Za-z]+(?:_nmoxstudio)?_" + lang + "\\.properties$");
        List<Path> jars = new ArrayList<>();
        try (Stream<Path> s = Files.list(MODULES)) {
            s.filter(p -> p.getFileName().toString().startsWith("org-nmox-NMOX-Studio")
                    && p.getFileName().toString().endsWith(".jar")).sorted().forEach(jars::add);
        }
        try (Stream<Path> s = Files.list(MODULES.resolve("locale"))) {
            s.filter(p -> p.getFileName().toString().endsWith("_nmoxstudio_" + lang + ".jar")).sorted().forEach(jars::add);
        }
        List<String[]> out = new ArrayList<>();
        for (Path jar : jars) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                for (ZipEntry e : zip.stream().toList()) {
                    if (!bundle.matcher(e.getName()).find()) {
                        continue;
                    }
                    Properties p = new Properties();
                    try (InputStream in = zip.getInputStream(e)) {
                        p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                    }
                    for (String key : p.stringPropertyNames()) {
                        String v = p.getProperty(key);
                        Matcher m = MNEMONIC.matcher(v);
                        if (m.find() && m.group(1).charAt(0) >= 128) {
                            out.add(new String[] {jar.getFileName() + "!" + e.getName() + " " + key, v});
                        }
                    }
                }
            }
        }
        return out;
    }
}
