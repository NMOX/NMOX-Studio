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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every translated mnemonic reaches a key (after 3.2.0, ledger 122). The
 * platform's {@code org.openide.awt.Mnemonics} gives a key only to A-Z and
 * 0-9 and asks a {@code Mnemonics_<lang>.properties} table in an unbranded
 * locale jar ({@code modules/locale/org-openide-awt_<lang>.jar}) for any
 * other letter. Until the branding module shipped tables, about 340 values
 * each in Russian and Ukrainian, and a few dozen accented ones elsewhere
 * (French {@code &Édition}, Vietnamese {@code &Đóng}), underlined a letter
 * no key reached; Hindi underlined Devanagari on its whole top menu bar,
 * where its own convention appends a Latin letter.
 *
 * <p>The outcome is measured the way the product meets it: the platform's
 * own {@code Mnemonics} class, loaded from the assembled cluster together
 * with the language's table, under that language's default locale, is
 * handed every value in the cluster that underlines a letter outside ASCII
 * and must give each one a key. A language with no table (the ones that
 * append a Latin letter by convention) must underline no such letter at
 * all. The population is the cluster's own bundles, our modules and the
 * platform overlays alike, so a translation that underlines a letter no
 * table maps fails by name.
 */
class TranslatedMnemonicsTest {

    private static final Path CLUSTER = Path.of("target", "nmoxstudio");
    private static final Path MODULES = CLUSTER.resolve("nmoxstudio").resolve("modules");

    /** A mnemonic: an ampersand before a letter, not a doubled one. */
    private static final Pattern MNEMONIC = Pattern.compile("(?<!&)&(\\p{L})");

    /** A top menu bar entry's key: {@code Menu/File}, one segment. */
    private static final Pattern TOP_MENU = Pattern.compile("Menu/[A-Za-z]+");

    static List<String> languages() {
        return ShippedLocales.TRANSLATED;
    }

    @ParameterizedTest
    @MethodSource("languages")
    @DisplayName("every mnemonic a language underlines reaches a key")
    void everyMnemonicReachesAKey(String lang) throws Exception {
        List<String[]> values = underlined(lang);
        if (!Files.isRegularFile(table(lang))) {
            assertThat(values).as("%s ships no mnemonic table, so a mnemonic on a letter outside ASCII "
                    + "reaches no key — append the Latin letter, as the language's convention says", lang)
                    .extracting(v -> v[0] + ": \"" + v[1] + "\"").isEmpty();
            return;
        }
        List<String> dead = new ArrayList<>();
        withMnemonics(lang, set -> {
            for (String[] v : values) {
                javax.swing.JMenuItem item = new javax.swing.JMenuItem();
                set.invoke(null, item, v[1]);
                int k = item.getMnemonic();
                if (k == 0) {
                    dead.add(v[0] + ": \"" + v[1] + "\"");
                } else if (!letterOrDigitKey(k)) {
                    // Swing selects an item of an open menu by the typed
                    // character and ignores one that is not a letter or digit
                    // (BasicPopupMenuUI.MenuKeyboardHelper), so a punctuation
                    // key would underline a key that does nothing there
                    dead.add(v[0] + ": \"" + v[1] + "\" reaches key " + k + ", a punctuation key an open menu ignores");
                }
            }
            return null;
        });
        assertThat(dead).as("%s mnemonics that underline a letter no key reaches — "
                + "add the letter to branding/src/main/nbm-mnemonics/%s", lang, lang).isEmpty();
    }

    @Test
    @DisplayName("the languages that need a table have one, and Russian and Ukrainian use theirs hundreds of times")
    void tablesAreWhereTheyAreNeeded() throws Exception {
        for (String lang : List.of("ru", "uk")) {
            assertThat(underlined(lang)).as("%s values the table serves", lang).hasSizeGreaterThan(200);
        }
        for (String lang : List.of("es", "fr", "de", "pl", "pt", "id", "tl", "vi", "ru", "uk")) {
            assertThat(table(lang)).as("the %s table in the cluster", lang).isRegularFile();
        }
        for (String lang : List.of("zh", "hi", "he", "ar")) {
            assertThat(table(lang)).as("%s appends a Latin letter and ships no table", lang).doesNotExist();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "uk"})
    @DisplayName("a Cyrillic letter reaches the key it sits on in the language's own layout")
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

    @Test
    @DisplayName("an accented Latin letter reaches its base letter's key")
    void anAccentedLetterReachesItsBase() throws Exception {
        Map<String, String[]> cases = new LinkedHashMap<>();
        cases.put("fr", new String[] {"&Édition", "E"});
        cases.put("vi", new String[] {"&Đóng", "D"});
        cases.put("pl", new String[] {"Zwi&ń wszystko", "N"});
        cases.put("de", new String[] {"Gro&ß", "S"});
        cases.put("es", new String[] {"&Ámbito:", "A"});
        for (Map.Entry<String, String[]> c : cases.entrySet()) {
            withMnemonics(c.getKey(), set -> {
                javax.swing.JMenuItem item = new javax.swing.JMenuItem();
                set.invoke(null, item, c.getValue()[0]);
                assertThat(item.getMnemonic()).as(c.getKey() + " " + c.getValue()[0])
                        .isEqualTo((int) c.getValue()[1].charAt(0));
                return null;
            });
        }
    }

    @Test
    @DisplayName("each Latin table maps every letter to the letter it decomposes to, or the few named ones")
    void latinTablesFollowTheirRule() throws Exception {
        Map<Character, Character> named = Map.ofEntries(Map.entry('Đ', 'D'), Map.entry('đ', 'D'),
                Map.entry('Ł', 'L'), Map.entry('ł', 'L'), Map.entry('ß', 'S'), Map.entry('ẞ', 'S'),
                Map.entry('Ø', 'O'), Map.entry('ø', 'O'), Map.entry('Ħ', 'H'), Map.entry('ħ', 'H'),
                Map.entry('Ŧ', 'T'), Map.entry('ŧ', 'T'), Map.entry('Ɨ', 'I'), Map.entry('ı', 'I'),
                Map.entry('Ƶ', 'Z'), Map.entry('ƶ', 'Z'), Map.entry('Ɓ', 'B'), Map.entry('Ɗ', 'D'));
        Properties first = null;
        for (String lang : List.of("es", "fr", "de", "pl", "pt", "id", "tl", "vi")) {
            Properties p = tableEntries(lang);
            if (first == null) {
                first = p;
                assertThat(p.size()).as("letters in the Latin table").isGreaterThan(400);
                for (String key : p.stringPropertyNames()) {
                    char c = key.charAt(key.length() - 1);
                    char base = named.containsKey(c) ? named.get(c)
                            : Character.toUpperCase(Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).charAt(0));
                    assertThat(p.getProperty(key)).as("the key for " + c).isEqualTo(String.valueOf(base));
                }
                for (char c : "ÉéÁáĐđńưặởấúß".toCharArray()) {
                    assertThat(p).as("the table covers " + c).containsKey("MNEMONIC_" + c);
                }
            } else {
                assertThat(p).as("the %s table is the same table", lang).isEqualTo(first);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("languages")
    @DisplayName("no two entries of the top menu bar press the same key")
    void topBarKeysAreDistinct(String lang) throws Exception {
        Map<String, String> bar = new TreeMap<>();
        for (String[] v : values(lang)) {
            String key = v[0].substring(v[0].lastIndexOf(' ') + 1);
            if (TOP_MENU.matcher(key).matches()) {
                bar.put(key, v[1]);
            }
        }
        assertThat(bar).as("%s top menu bar entries read from the cluster", lang).hasSizeGreaterThanOrEqualTo(10);
        Map<Integer, String> byKey = new TreeMap<>();
        List<String> clashes = new ArrayList<>();
        boolean hasTable = Files.isRegularFile(table(lang));
        withMnemonics(hasTable ? lang : null, set -> {
            for (Map.Entry<String, String> e : bar.entrySet()) {
                javax.swing.JMenuItem item = new javax.swing.JMenuItem();
                set.invoke(null, item, e.getValue());
                int k = item.getMnemonic();
                if (k == 0) {
                    continue;   // everyMnemonicReachesAKey owns a dead one
                }
                String other = byKey.putIfAbsent(k, e.getValue());
                if (other != null) {
                    clashes.add("\"" + other + "\" and \"" + e.getValue() + "\" both press " + (char) k);
                }
            }
            return null;
        });
        assertThat(clashes).as("%s top menu bar entries Alt+letter cannot tell apart", lang).isEmpty();
    }

    /** A key an open menu can select by: A-Z or 0-9. */
    private static boolean letterOrDigitKey(int k) {
        return (k >= 'A' && k <= 'Z') || (k >= '0' && k <= '9');
    }

    /**
     * The Cyrillic tables, entry for entry, against the layouts written out
     * here independently: each letter on a letter key of the standard
     * JCUKEN layout (Russian; Ukrainian Enhanced puts I on S and has no
     * letter where Russian has hard sign or E) maps to the Latin letter on
     * that key, both cases, and nothing else is in the table.
     */
    @ParameterizedTest
    @ValueSource(strings = {"ru", "uk"})
    @DisplayName("the Cyrillic table is exactly the layout's letter keys")
    void cyrillicTableIsTheLayout(String lang) throws Exception {
        String[][] rows = lang.equals("ru")
                ? new String[][] {{"ЙЦУКЕНГШЩЗ", "QWERTYUIOP"}, {"ФЫВАПРОЛД", "ASDFGHJKL"}, {"ЯЧСМИТЬ", "ZXCVBNM"}}
                : new String[][] {{"ЙЦУКЕНГШЩЗ", "QWERTYUIOP"}, {"ФІВАПРОЛД", "ASDFGHJKL"}, {"ЯЧСМИТЬ", "ZXCVBNM"}};
        Map<String, String> expected = new TreeMap<>();
        for (String[] r : rows) {
            for (int i = 0; i < r[0].length(); i++) {
                String latin = String.valueOf(r[1].charAt(i));
                expected.put("MNEMONIC_" + r[0].charAt(i), latin);
                expected.put("MNEMONIC_" + Character.toLowerCase(r[0].charAt(i)), latin);
            }
        }
        Properties p = tableEntries(lang);
        Map<String, String> actual = new TreeMap<>();
        for (String key : p.stringPropertyNames()) {
            actual.put(key, p.getProperty(key));
        }
        assertThat(actual).as("the %s table", lang).isEqualTo(expected);
    }

    private interface Body {
        Object run(Method setLocalizedText) throws Exception;
    }

    private static Path table(String lang) {
        return MODULES.resolve("locale").resolve("org-openide-awt_" + lang + ".jar");
    }

    private static Properties tableEntries(String lang) throws IOException {
        try (ZipFile zip = new ZipFile(table(lang).toFile())) {
            ZipEntry e = zip.getEntry("org/openide/awt/Mnemonics_" + lang + ".properties");
            assertThat(e).as("the %s table's entry, named as the platform looks it up", lang).isNotNull();
            Properties p = new Properties();
            try (InputStream in = zip.getInputStream(e)) {
                p.load(in);
            }
            return p;
        }
    }

    /**
     * Runs {@code body} with the platform's {@code Mnemonics} loaded from the
     * cluster beside {@code org-openide-awt_<lang>.jar} (none for a null
     * {@code lang}), under that language's default locale and a look and
     * feel that has mnemonics (Aqua turns them off entirely). The loader's
     * parent is the JDK's, so the class cannot come from the test's own
     * classpath without the table.
     */
    private static void withMnemonics(String lang, Body body) throws Exception {
        List<URL> jars = new ArrayList<>();
        try (Stream<Path> s = Files.walk(CLUSTER)) {
            for (Path jar : s.filter(p -> {
                String n = p.getFileName().toString();
                return n.equals("org-openide-awt.jar") || n.equals("org-openide-util.jar")
                        || n.equals("org-openide-util-ui.jar") || n.equals("org-openide-util-lookup.jar")
                        || (lang != null && n.equals("org-openide-awt_" + lang + ".jar"));
            }).toList()) {
                jars.add(jar.toUri().toURL());
            }
        }
        assertThat(jars).as("the platform's awt and util jars, and the %s table, in the cluster", lang)
                .hasSize(lang == null ? 4 : 5);
        Locale before = Locale.getDefault();
        javax.swing.LookAndFeel laf = javax.swing.UIManager.getLookAndFeel();
        java.util.logging.Logger log = java.util.logging.Logger.getLogger("org.openide.awt.Mnemonics");
        java.util.logging.Level level = log.getLevel();
        log.setLevel(java.util.logging.Level.WARNING);   // a refusal logs at INFO; the item says it
        javax.swing.UIManager.setLookAndFeel(new javax.swing.plaf.metal.MetalLookAndFeel());
        if (lang != null) {
            Locale.setDefault(Locale.forLanguageTag(lang));
        }
        try (URLClassLoader cl = new URLClassLoader(jars.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            body.run(Class.forName("org.openide.awt.Mnemonics", true, cl)
                    .getMethod("setLocalizedText", javax.swing.AbstractButton.class, String.class));
        } finally {
            Locale.setDefault(before);
            log.setLevel(level);
            javax.swing.UIManager.setLookAndFeel(laf);
        }
    }

    /** {jar!entry key, value} for every {@code lang} value that underlines a letter outside ASCII. */
    private static List<String[]> underlined(String lang) throws IOException {
        List<String[]> out = new ArrayList<>();
        for (String[] v : values(lang)) {
            Matcher m = MNEMONIC.matcher(v[1]);
            if (m.find() && m.group(1).charAt(0) >= 128) {
                out.add(v);
            }
        }
        return out;
    }

    /** {jar!entry key, value} for every {@code lang} value in the cluster's bundles. */
    private static List<String[]> values(String lang) throws IOException {
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
                        out.add(new String[] {jar.getFileName() + "!" + e.getName() + " " + key, p.getProperty(key)});
                    }
                }
            }
        }
        return out;
    }
}
