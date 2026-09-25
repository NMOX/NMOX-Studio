package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every row of the menu bar reads in the user's language.
 *
 * <p>v2.97.0 overlaid the menu bar's top-level NAMES, and nothing ever
 * overlaid the rows inside them — so a Hindi build showed translated menus
 * whose contents were English. Unlike the dialogs of v2.142.0, this
 * population IS derivable: a menu row is a {@code Menu/**.shadow} in the
 * assembled cluster's own layers, pointing at an action whose
 * {@code displayName} is a bundle key.
 *
 * <p>What this gate holds:
 *
 * <ul>
 *   <li>every visible platform row has a branding+locale overlay for its
 *       bundle key in all twelve languages — a platform module added
 *       tomorrow fails the build until its rows are translated;
 *   <li>within one menu, no two rows claim the same mnemonic letter, per
 *       language (a collision means one of them cannot be reached by
 *       keyboard);
 *   <li>a declared mnemonic really occurs in the label it marks;
 *   <li>a mnemonic is a letter the platform can map to a key, {@code A-Z}
 *       or {@code 0-9} (3.2.0, measured: any other letter gives no mnemonic);
 *   <li>in a language written in letters, a mnemonic is underlined IN the
 *       label ({@code Edito&r}), and a Latin letter appended in parentheses
 *       ({@code Editor(&J)}, the Chinese convention) survives only where
 *       every mappable letter of the label is claimed by another row of the
 *       same menu;
 *   <li>no value carries a bare ASCII apostrophe (the v2.98.0 hazard).
 * </ul>
 *
 * <p>Rows our own layers HIDE are excluded: the population is what a user
 * sees, not what the cluster registers. Rows our own modules own are excluded
 * too — their strings ride our ordinary bundles and the parity gate.
 */
class MenuRowsSpeakTest {

    private static final Path BRANDING =
            Path.of("..", "branding", "src", "main", "nbm-branding", "modules");

    private static final Path CLUSTER = Path.of("target", "nmoxstudio");

    private static final List<String> LOCALES = ShippedLocales.TRANSLATED;

    /** One row of the menu bar: where it sits, and the bundle key that names it. */
    private record Row(String menu, String jar, String pkg, String key, String english) { }

    /**
     * Keys whose MENU text is read from a sibling. The layer's
     * {@code displayName} is only a fallback: where the action is a real class
     * with its own name, the menu paints the sibling instead — so the sibling
     * is what must exist, and the sibling is what carries the mnemonic. Found
     * by WALKING a translated build and reading the menu bar (v2.143.0); a
     * static read of the layer cannot tell you which of the two a user sees.
     */
    private static final Map<String, String> PAINTED_INSTEAD = Map.of(
            "toggle-line-numbers", "toggle-line-numbers_menu_text",
            "toggle-non-printable-characters", "toggle-non-printable-characters_menu_text",
            "toggle-toolbar", "toggle-toolbar_menu_text");

    /** The key the menu actually paints for this row. */
    private static String paintedKey(Row row) {
        return PAINTED_INSTEAD.getOrDefault(row.key(), row.key());
    }

    @Test
    @DisplayName("every platform menu row the user can see is translated into all twelve languages")
    void everyRowSpeaks() throws IOException {
        List<Row> rows = rows();
        assertThat(rows).as("platform menu rows derived from the assembled cluster")
                .hasSizeGreaterThan(74);   // measured 80 at v2.143.0, minus a margin
        List<String> missing = new ArrayList<>();
        for (Row row : rows) {
            for (String locale : LOCALES) {
                Properties p = overlay(row, locale);
                if (p == null) {
                    missing.add(row.menu() + " " + row.key() + " [" + locale + "]: no overlay file");
                } else if (p.getProperty(row.key()) == null) {
                    missing.add(row.menu() + " " + row.key() + " [" + locale + "]: key not overlaid");
                } else if (p.getProperty(paintedKey(row)) == null) {
                    missing.add(row.menu() + " " + paintedKey(row) + " [" + locale
                            + "]: the key the menu paints is not overlaid");
                }
            }
        }
        assertThat(missing).as("menu rows a reader would meet in English").isEmpty();
    }

    @Test
    @DisplayName("within one menu no two rows claim the same mnemonic, in any language")
    void mnemonicsDoNotCollide() throws IOException {
        List<String> clashes = new ArrayList<>();
        for (String locale : LOCALES) {
            Map<String, Map<String, String>> perMenu = new HashMap<>();
            for (Row row : allRows()) {
                Properties p = overlay(row, locale);
                String value = p == null ? null : p.getProperty(paintedKey(row));
                if (value == null) {
                    continue;   // everyRowSpeaks owns the missing verdict
                }
                String letter = mnemonicOf(value);
                if (letter == null) {
                    continue;
                }
                Map<String, String> taken = perMenu.computeIfAbsent(row.menu(), m -> new TreeMap<>());
                String other = taken.putIfAbsent(letter, value);
                if (other != null) {
                    clashes.add(locale + " " + row.menu() + ": \"" + value + "\" and \"" + other
                            + "\" both claim " + letter);
                }
            }
        }
        assertThat(clashes).as("two rows of one menu a keyboard cannot tell apart").isEmpty();
    }

    @Test
    @DisplayName("no row invents a mnemonic English does not assign")
    void mnemonicsMirrorEnglish() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (String locale : LOCALES) {
            for (Row row : allRows()) {
                Properties p = overlay(row, locale);
                String value = p == null ? null : p.getProperty(paintedKey(row));
                if (value == null) {
                    continue;
                }
                // One direction only. Inventing a letter English does not
                // offer is a defect — two File rows painted a literal "(&P)"
                // because the code that builds them never processes mnemonics,
                // and the Hindi walk is what showed it. Going the other way is
                // not: a menu can hold more rows than the alphabet has letters,
                // and the Window menu does, so a row there may end up with none.
                if (mnemonicOf(row.english()) == null && mnemonicOf(value) != null) {
                    wrong.add(locale + " " + row.key() + ": English assigns no mnemonic and "
                            + "this invents one (\"" + value + "\")");
                }
            }
        }
        assertThat(wrong).as("mnemonics no English row offers").isEmpty();
    }

    @Test
    @DisplayName("a declared mnemonic occurs in its own label, and no value opens a MessageFormat quote")
    void valuesAreWellFormed() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (String locale : LOCALES) {
            for (Row row : rows()) {
                Properties p = overlay(row, locale);
                String value = p == null ? null : p.getProperty(paintedKey(row));
                if (value == null) {
                    continue;
                }
                if (value.contains("'")) {
                    wrong.add(locale + " " + row.key() + ": bare ASCII apostrophe");
                }
                String letter = mnemonicOf(value);
                if (letter != null && !plain(value).toUpperCase(Locale.ROOT).contains(letter)
                        && !value.contains("(&")) {
                    wrong.add(locale + " " + row.key() + ": mnemonic " + letter
                            + " is not in \"" + plain(value) + "\"");
                }
            }
        }
        assertThat(wrong).as("menu row values that would misrender or accelerate nothing").isEmpty();
    }

    /** An appended mnemonic, {@code Editor(&J)}: the letter in parentheses. */
    private static final Pattern APPENDED = Pattern.compile("\\(&([^)])\\)");

    /**
     * The languages exempt from the mapping law, and why. Their overlays
     * underline a Cyrillic letter ({@code &Файл}), and no Cyrillic letter maps
     * to a key (see {@link #onlyLatinLettersAndDigitsMapToAKey}), so about
     * 138 Russian and 136 Ukrainian menu rows carry a mnemonic that does
     * nothing. That is OPEN, not blessed: the fix is either a shipped
     * Cyrillic-to-keycode table for {@code org.openide.awt.Mnemonics} or
     * Latin letters, and it is recorded in {@code docs/i18n/conventions.md}.
     * An exemption that turns out to be empty fails, so it cannot outlive the fix.
     */
    private static final Set<String> CYRILLIC_UNMAPPED = Set.of("ru", "uk");

    @Test
    @DisplayName("a mnemonic is a letter the platform maps to a key: A-Z or 0-9")
    void mnemonicsAreLettersThePlatformMaps() throws IOException {
        List<String> wrong = new ArrayList<>();
        Map<String, Integer> exempt = new TreeMap<>();
        for (String locale : LOCALES) {
            for (Row row : allRows()) {
                Properties p = overlay(row, locale);
                String value = p == null ? null : p.getProperty(paintedKey(row));
                if (value == null) {
                    continue;
                }
                int i = value.indexOf('&');
                if (i < 0 || i == value.length() - 1 || mapsToAKey(value.charAt(i + 1))) {
                    continue;
                }
                if (CYRILLIC_UNMAPPED.contains(locale)) {
                    exempt.merge(locale, 1, Integer::sum);
                    continue;
                }
                wrong.add(locale + " " + row.menu() + " " + paintedKey(row) + ": \"" + value
                        + "\" underlines '" + value.charAt(i + 1) + "', which maps to no key, so the row "
                        + "has no mnemonic at all (and the platform logs it every time the menu is built)");
            }
        }
        assertThat(wrong).as("mnemonics that do nothing — underline a letter A-Z or a digit").isEmpty();
        assertThat(exempt.keySet()).as("the Cyrillic exemption still describes something; "
                + "when it does not, delete it").isEqualTo(CYRILLIC_UNMAPPED);
    }

    @Test
    @DisplayName("in a language written in letters, a mnemonic is appended only when no letter of its label is free")
    void appendedMnemonicOnlyWhenNoLetterIsFree() throws IOException {
        List<String> wrong = new ArrayList<>();
        List<Row> all = allRows();
        for (String locale : LOCALES) {
            if (NativeTypographyGateTest.appendsMnemonic(locale)) {
                continue;   // zh, hi, he, ar: appended Latin IS the convention
            }
            for (Row row : all) {
                Properties p = overlay(row, locale);
                String value = p == null ? null : p.getProperty(paintedKey(row));
                if (value == null) {
                    continue;
                }
                Matcher m = APPENDED.matcher(value);
                if (!m.find()) {
                    continue;
                }
                String label = value.substring(0, m.start()) + value.substring(m.end());
                Set<String> taken = new HashSet<>();
                for (Row other : all) {
                    if (!other.menu().equals(row.menu()) || sameKey(other, row)) {
                        continue;
                    }
                    Properties op = overlay(other, locale);
                    String ov = op == null ? null : op.getProperty(paintedKey(other));
                    String letter = ov == null ? null : mnemonicOf(ov);
                    if (letter != null) {
                        taken.add(letter);
                    }
                }
                Set<String> free = new java.util.TreeSet<>(mappableLetters(label));
                free.removeAll(taken);
                if (!free.isEmpty()) {
                    wrong.add(locale + " " + row.menu() + " " + paintedKey(row) + ": \"" + value
                            + "\" appends " + m.group(1) + " while " + free
                            + " of its own label are free in this menu — underline one in place");
                }
            }
        }
        assertThat(wrong).as("a Latin letter appended in parentheses where the label had its own "
                + "letter to underline (on macOS the reader sees the stray \"(J)\")").isEmpty();
    }

    /**
     * The premise of the mapping law, measured on the platform this build
     * ships rather than remembered: {@code Mnemonics.setLocalizedText} gives a
     * key for an ASCII letter and NONE for an accented, Polish, Vietnamese or
     * Cyrillic one. If a platform upgrade learns to map them, this fails, and
     * the mapping law (and the Cyrillic exemption) should be revisited.
     */
    @Test
    @DisplayName("the platform maps only A-Z and 0-9 to a mnemonic key (measured on the shipped jar)")
    void onlyLatinLettersAndDigitsMapToAKey() throws Exception {
        List<java.net.URL> jars = new ArrayList<>();
        try (Stream<Path> s = Files.walk(CLUSTER)) {
            for (Path jar : s.filter(p -> {
                String n = p.getFileName().toString();
                return n.equals("org-openide-awt.jar") || n.equals("org-openide-util.jar")
                        || n.equals("org-openide-util-ui.jar") || n.equals("org-openide-util-lookup.jar");
            }).toList()) {
                jars.add(jar.toUri().toURL());
            }
        }
        assertThat(jars).as("the platform's awt and util jars in the assembled cluster").hasSize(4);
        javax.swing.LookAndFeel before = javax.swing.UIManager.getLookAndFeel();
        // Aqua turns mnemonics off entirely, so measure under a look and feel
        // that has them, as Windows and Linux do; restore it afterwards.
        javax.swing.UIManager.setLookAndFeel(new javax.swing.plaf.metal.MetalLookAndFeel());
        java.util.logging.Logger log = java.util.logging.Logger.getLogger("org.openide.awt.Mnemonics");
        java.util.logging.Level level = log.getLevel();
        log.setLevel(java.util.logging.Level.WARNING);   // the refusal we are measuring logs at INFO
        try (java.net.URLClassLoader cl = new java.net.URLClassLoader(
                jars.toArray(java.net.URL[]::new), getClass().getClassLoader())) {
            java.lang.reflect.Method set = Class.forName("org.openide.awt.Mnemonics", true, cl)
                    .getMethod("setLocalizedText", javax.swing.AbstractButton.class, String.class);
            Map<String, Integer> got = new LinkedHashMap<>();
            for (String label : List.of("&Editor", "Edito&r", "Tab &9", "&Éditeur", "Pozosta&łe", "&Đóng", "&Файл")) {
                javax.swing.JMenuItem item = new javax.swing.JMenuItem();
                set.invoke(null, item, label);
                got.put(label, item.getMnemonic());
            }
            assertThat(got).as("mnemonic key per label").containsEntry("&Editor", (int) 'E')
                    .containsEntry("Edito&r", (int) 'R').containsEntry("Tab &9", (int) '9')
                    .containsEntry("&Éditeur", 0).containsEntry("Pozosta&łe", 0)
                    .containsEntry("&Đóng", 0).containsEntry("&Файл", 0);
            for (Map.Entry<String, Integer> e : got.entrySet()) {
                int amp = e.getKey().indexOf('&');
                if (amp >= 0 && amp < e.getKey().length() - 1) {
                    assertThat(e.getValue() != 0).as("the gate's own rule agrees with the platform for "
                            + e.getKey()).isEqualTo(mapsToAKey(e.getKey().charAt(amp + 1)));
                }
            }
        } finally {
            log.setLevel(level);
            javax.swing.UIManager.setLookAndFeel(before);
        }
    }

    /** Only these reach a key without a branded table the product does not ship. */
    private static boolean mapsToAKey(char c) {
        return c < 128 && Character.isLetterOrDigit(c);
    }

    /** The letters of a label a mnemonic could underline: ASCII, outside any {@code {…}} pattern. */
    private static Set<String> mappableLetters(String label) {
        Set<String> out = new HashSet<>();
        int depth = 0;
        for (char c : label.toCharArray()) {
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && mapsToAKey(c)) {
                out.add(String.valueOf(c).toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static boolean sameKey(Row a, Row b) {
        return a.jar().equals(b.jar()) && a.pkg().equals(b.pkg()) && paintedKey(a).equals(paintedKey(b));
    }

    /**
     * Both populations of the menu bar: the rows this gate DERIVES from the
     * layer, and the rows {@code CodeNamedMenuRowsTest} keeps by hand because
     * they declare nothing to derive from. The mnemonic law has to see both or
     * it cannot see a collision BETWEEN them — which is exactly what the Hindi
     * walk found, two View rows both reading (T), while this gate stayed green.
     */
    private static List<Row> allRows() throws IOException {
        List<Row> all = new ArrayList<>(rows());
        try (InputStream in = MenuRowsSpeakTest.class
                .getResourceAsStream("code-named-menu-rows.txt")) {
            assertThat(in).as("the code-named ledger").isNotNull();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").split("\n")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split("\\|", 5);
                all.add(new Row(f[0], f[1], f[2], f[3], f[4]));
            }
        }
        return all;
    }

    /** {@code A&bc} and {@code abc(&B)} both declare B; no marker gives null. */
    private static String mnemonicOf(String value) {
        int i = value.indexOf('&');
        return i < 0 || i == value.length() - 1
                ? null : value.substring(i + 1, i + 2).toUpperCase(Locale.ROOT);
    }

    private static String plain(String value) {
        return value.replace("&", "");
    }

    private static final Map<String, Properties> OVERLAY_CACHE = new HashMap<>();

    private static Properties overlay(Row row, String locale) throws IOException {
        Path p = BRANDING.resolve(row.jar()).resolve(row.pkg())
                .resolve("Bundle_" + locale + ".properties");
        String cacheKey = p.toString();
        if (OVERLAY_CACHE.containsKey(cacheKey)) {
            return OVERLAY_CACHE.get(cacheKey);
        }
        Properties props = null;
        if (Files.isRegularFile(p)) {
            props = new Properties();
            try (InputStream in = Files.newInputStream(p)) {
                props.load(in);   // .properties are ISO-8859-1 with \\uXXXX escapes
            }
        }
        OVERLAY_CACHE.put(cacheKey, props);
        return props;
    }

    private static List<Row> cached;

    /**
     * The population, read from the shipped artifact: every visible
     * {@code Menu/**.shadow} whose action names itself through a platform
     * bundle. Folder nesting is tracked so a row knows which menu it is in,
     * and {@code *_hidden} entries are removed the way the window system
     * removes them.
     */
    private static synchronized List<Row> rows() throws IOException {
        if (cached != null) {
            return cached;
        }
        Map<String, String> displayName = new LinkedHashMap<>();
        Map<String, String> originalFile = new LinkedHashMap<>();
        Map<String, String> ownedBy = new LinkedHashMap<>();
        Set<String> hidden = new HashSet<>();
        Map<String, Map<String, String>> bundles = new LinkedHashMap<>();

        assertThat(CLUSTER).as("the assembled cluster").exists();
        // a real XML parse, not a regex: the layer writer is free to order
        // attributes as it likes, and a gate that assumed their order is
        // exactly what went wrong in v2.118.0 (and again here, on its
        // first run — it saw 20 rows of 80)
        javax.xml.parsers.DocumentBuilderFactory dbf =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
        try {
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (javax.xml.parsers.ParserConfigurationException unsupported) {
            throw new IOException(unsupported);
        }
        try (Stream<Path> all = Files.walk(CLUSTER)) {
            for (Path jar : all.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (JarFile jf = new JarFile(jar.toFile())) {
                    String jarName = jar.getFileName().toString();
                    for (JarEntry e : jf.stream().toList()) {
                        String n = e.getName();
                        if (n.endsWith("Bundle.properties") && n.contains("/")) {
                            Properties p = new Properties();
                            try (InputStream in = jf.getInputStream(e)) {
                                p.load(in);
                            }
                            String pkg = n.substring(0, n.lastIndexOf('/'));
                            Map<String, String> into = bundles.computeIfAbsent(
                                    jarName + "!" + pkg, k -> new LinkedHashMap<>());
                            for (String k : p.stringPropertyNames()) {
                                into.putIfAbsent(k, p.getProperty(k));
                            }
                            continue;
                        }
                        if (!n.endsWith("layer.xml")) {
                            continue;
                        }
                        org.w3c.dom.Document doc;
                        try (InputStream in = jf.getInputStream(e)) {
                            doc = dbf.newDocumentBuilder().parse(in);
                        } catch (Exception malformed) {
                            continue;
                        }
                        collect(doc.getDocumentElement(), new ArrayList<>(), jarName,
                                displayName, originalFile, ownedBy, hidden);
                    }
                } catch (IOException unreadable) {
                    // a jar we cannot open contributes nothing
                }
            }
        }

        List<Row> out = new ArrayList<>();
        for (Map.Entry<String, String> e : originalFile.entrySet()) {
            String path = e.getKey();
            if (!path.startsWith("Menu/") || !path.endsWith(".shadow") || hidden.contains(path)) {
                continue;
            }
            String spec = displayName.get(e.getValue());
            if (spec == null || ownedBy.getOrDefault(e.getValue(), "").contains("nmox")) {
                continue;
            }
            int hash = spec.indexOf('#');
            if (hash < 0) {
                continue;
            }
            String pkg = spec.substring(0, hash);
            if (pkg.endsWith(".Bundle")) {
                pkg = pkg.substring(0, pkg.length() - ".Bundle".length());
            }
            pkg = pkg.replace('.', '/');
            String key = spec.substring(hash + 1);
            for (Map.Entry<String, Map<String, String>> b : bundles.entrySet()) {
                String jarPkg = b.getKey();
                if (!jarPkg.endsWith("!" + pkg) || !b.getValue().containsKey(key)) {
                    continue;
                }
                String menu = path.substring("Menu/".length());
                menu = menu.contains("/") ? menu.substring(0, menu.lastIndexOf('/')) : "";
                out.add(new Row(menu, jarPkg.substring(0, jarPkg.indexOf('!')), pkg, key,
                        b.getValue().get(key)));
                break;
            }
        }
        cached = out;
        return out;
    }

    /** Walk one layer document, recording file paths, their attributes and the hidden set. */
    private static void collect(org.w3c.dom.Element elem, List<String> path, String jarName,
            Map<String, String> displayName, Map<String, String> originalFile,
            Map<String, String> ownedBy, Set<String> hidden) {
        org.w3c.dom.NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof org.w3c.dom.Element child)) {
                continue;
            }
            String tag = child.getTagName();
            String name = child.getAttribute("name");
            if ("folder".equals(tag)) {
                List<String> deeper = new ArrayList<>(path);
                deeper.add(name);
                collect(child, deeper, jarName, displayName, originalFile, ownedBy, hidden);
            } else if ("file".equals(tag)) {
                List<String> parts = new ArrayList<>(path);
                if (name.endsWith("_hidden")) {
                    parts.add(name.substring(0, name.length() - "_hidden".length()));
                    hidden.add(String.join("/", parts));
                    continue;
                }
                parts.add(name);
                String full = String.join("/", parts);
                ownedBy.put(full, jarName);
                org.w3c.dom.NodeList attrs = child.getChildNodes();
                for (int j = 0; j < attrs.getLength(); j++) {
                    if (!(attrs.item(j) instanceof org.w3c.dom.Element a)
                            || !"attr".equals(a.getTagName())) {
                        continue;
                    }
                    String an = a.getAttribute("name");
                    String value = a.hasAttribute("bundlevalue") ? a.getAttribute("bundlevalue")
                            : a.hasAttribute("stringvalue") ? a.getAttribute("stringvalue") : null;
                    if (value == null) {
                        continue;
                    }
                    if ("displayName".equals(an)) {
                        displayName.put(full, value);
                    } else if ("originalFile".equals(an)) {
                        originalFile.put(full, value);
                    }
                }
            }
        }
    }
}
