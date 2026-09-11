package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One vocabulary, in every language (v2.118.0, the coherence pass).
 *
 * <p>The product tells the user where to go — "NPM Explorer ▸ Install
 * first", "see Output ▸ Rack, or Tools ▸ Environment Doctor". Measured on
 * the assembled 2.108.0 cluster, <b>73 of those directions pointed at doors
 * that do not exist by that name</b> in the language the reader was reading:
 * a Spanish user was sent to "Herramientas ▸ Environment Doctor" while the
 * menu item reads <i>Doctor del entorno</i>; a German user to "Tools ▸ …"
 * while the German menu is <i>Extras</i>; and seven languages named the
 * Output window "Salida"/"Wyjście"/"输出" when the platform does not
 * localize that window at all, so it is titled "Output" in every build.
 * The translators were careful — they were guessing which names the product
 * translates, and no gate could tell them.
 *
 * <p>Three rules, each derived from the assembled cluster rather than a
 * hand-kept list, so a tenth wayfinding string is covered on the day it
 * ships:
 *
 * <ol>
 *   <li><b>A name the product translates must appear translated.</b> If
 *       {@code CTL_EnvironmentDoctorAction} reads "Doctor del entorno" in
 *       Spanish, no Spanish string may say "Environment Doctor".</li>
 *   <li><b>A menu named before an arrow must be that locale's menu name</b>,
 *       read from the branding+locale overlays that localize the platform
 *       menu bar (v2.97.0).</li>
 *   <li><b>A platform surface the product does NOT localize must survive
 *       verbatim.</b> The Output window and the Window ▸ IDE Tools submenu
 *       come from platform modules the overlays do not cover; translating
 *       their names in prose invents a door. Revisit this list if an
 *       overlay ever reaches those modules.</li>
 * </ol>
 *
 * <p>And a fourth rule for the same class one layer up: a label that names
 * one of the product's own windows must use that window's own name. The
 * Workbench's tooling shelf called the Docker Panel "Docker Manager" in all
 * thirteen languages — one window, two names, its own tooltip disagreeing
 * with its own label. The pairs are derived from the source lines that bind
 * a label to a window id, and the window's name from the generated layer's
 * Window action, so a new shelf row is covered without touching this test.
 */
class WayfindingVocabularyTest {

    /** The arrow the product uses for a menu path. */
    private static final String ARROW = "▸";

    private static final List<String> LOCALES = LocaleBundleParityTest.LOCALES;

    /**
     * Platform surfaces the product references and does NOT localize: their
     * modules are outside the branding+locale overlay set, so they read
     * English in every build. Naming them in the reader's language points at
     * a door that is not there.
     */
    private static final List<String> ENGLISH_BY_CONSTRUCTION = List.of("Output", "IDE Tools");

    /** The menu-bar folders the overlays localize, by their English name. */
    private static final Map<String, String> MENUS = Map.of(
            "File", "File", "Edit", "Edit", "View", "View",
            "Tools", "Tools", "Help", "Help", "Window", "Window");

    private static Path cluster() {
        return Path.of("target/nmoxstudio/nmoxstudio");
    }

    // ---- the vocabulary, read from the assembled cluster ----------------

    /** locale → key → value, over every product bundle in the cluster. */
    private static Map<String, Map<String, String>> productStrings() throws IOException {
        Map<String, Map<String, String>> out = new TreeMap<>();
        out.put("en", new TreeMap<>());
        LOCALES.forEach(l -> out.put(l, new TreeMap<>()));
        for (Path jar : productJars()) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                for (ZipEntry e : zip.stream().collect(Collectors.toList())) {
                    String n = e.getName();
                    Matcher m = Pattern.compile(".*/Bundle(?:_([a-z]{2}))?\\.properties").matcher(n);
                    if (!m.matches()) {
                        continue;
                    }
                    String loc = m.group(1) == null ? "en" : m.group(1);
                    if (!out.containsKey(loc)) {
                        continue;
                    }
                    Properties p = utf8(zip, e);
                    p.stringPropertyNames().forEach(k -> out.get(loc).put(k, p.getProperty(k)));
                }
            }
        }
        return out;
    }

    private static List<Path> productJars() throws IOException {
        Path dir = cluster().resolve("modules");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                            .startsWith("org-nmox-nmox-studio-"))
                    .sorted().collect(Collectors.toList());
        }
    }

    /** locale → English menu name → that locale's menu name, from the overlays. */
    private static Map<String, Map<String, String>> menuNames() throws IOException {
        Map<String, Map<String, String>> out = new TreeMap<>();
        Path dir = cluster().resolve("modules").resolve("locale");
        for (String loc : LOCALES) {
            Map<String, String> names = new TreeMap<>();
            if (Files.isDirectory(dir)) {
                try (Stream<Path> s = Files.list(dir)) {
                    for (Path jar : s.filter(p -> p.getFileName().toString()
                            .endsWith("_nmoxstudio_" + loc + ".jar")).toList()) {
                        try (ZipFile zip = new ZipFile(jar.toFile())) {
                            for (ZipEntry e : zip.stream().collect(Collectors.toList())) {
                                if (!e.getName().endsWith(".properties")) {
                                    continue;
                                }
                                Properties p = utf8(zip, e);
                                for (String k : p.stringPropertyNames()) {
                                    if (k.startsWith("Menu/")) {
                                        names.put(k.substring("Menu/".length()), plainMenu(p.getProperty(k)));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            out.put(loc, names);
        }
        return out;
    }

    /**
     * Bundles are UTF-8; {@link Properties#load(InputStream)} is ISO-8859-1.
     * The first cut of this gate read them the platform's way and reported
     * every locale's ellipsis as a name mismatch — a gate that mis-reads its
     * own evidence fails on the truth.
     */
    private static Properties utf8(ZipFile zip, ZipEntry e) throws IOException {
        Properties p = new Properties();
        try (InputStream in = zip.getInputStream(e)) {
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    /** "文件(&amp;F)" and "&amp;Datei" both name the same menu. */
    private static String plainMenu(String raw) {
        return raw.replaceAll("\\(&.\\)", "").replace("&", "").trim();
    }

    // ---- rule 1-3: the directions the product gives ---------------------

    @Test
    @DisplayName("a menu path names doors that exist in the reader's own language")
    void wayfindingUsesTheLocaleVocabulary() throws IOException {
        Map<String, Map<String, String>> strings = productStrings();
        assertThat(strings.get("en")).as("the assembled cluster's English bundles").isNotEmpty();
        Map<String, Map<String, String>> menus = menuNames();

        List<String> wayfinding = strings.get("en").entrySet().stream()
                .filter(e -> e.getValue().contains(ARROW))
                .map(Map.Entry::getKey).sorted().toList();
        assertThat(wayfinding)
                .as("strings that tell the user where to go — an empty scan would prove nothing")
                .hasSizeGreaterThanOrEqualTo(9);

        List<String> problems = new ArrayList<>();
        for (String loc : LOCALES) {
            Map<String, String> here = strings.get(loc);
            for (String key : wayfinding) {
                String value = here.get(key);
                if (value == null) {
                    continue; // parity is LocaleBundleParityTest's job
                }
                // 1. a product name this locale translates must not survive in English
                for (Map.Entry<String, String> ctl : strings.get("en").entrySet()) {
                    String name = ctl.getKey().startsWith("CTL_") ? trimName(ctl.getValue()) : null;
                    if (name == null || !name.contains(" ") || name.length() < 6) {
                        continue; // one-word names are ordinary words in some languages
                    }
                    String translated = trimName(here.get(ctl.getKey()));
                    if (translated == null || translated.equals(name)) {
                        continue; // this locale keeps the English name: nothing to check
                    }
                    if (Pattern.compile("(?<![\\w-])" + Pattern.quote(name) + "(?![\\w])").matcher(value).find()) {
                        problems.add(loc + " " + key + ": says \"" + name
                                + "\" where this build's menu reads \"" + translated + "\"");
                    }
                }
                // 2. the menu before an arrow is the localized menu bar's own name
                for (String english : MENUS.keySet()) {
                    String localized = menus.get(loc).get(english);
                    if (localized == null || localized.equals(english)) {
                        continue;
                    }
                    if (Pattern.compile("(?<![\\w-])(?<!IDE )" + Pattern.quote(english) + "\\s*" + ARROW)
                            .matcher(value).find()) {
                        problems.add(loc + " " + key + ": names the \"" + english
                                + "\" menu, which reads \"" + localized + "\" here");
                    }
                }
                // 3. a platform surface the product does not localize survives verbatim
                for (String english : ENGLISH_BY_CONSTRUCTION) {
                    if (strings.get("en").get(key).contains(english) && !value.contains(english)) {
                        problems.add(loc + " " + key + ": translated \"" + english
                                + "\", a window the product does not localize");
                    }
                }
            }
        }
        assertThat(problems)
                .as("directions that point at a door with the wrong name")
                .isEmpty();
    }

    // ---- rule 4: one window, one name -----------------------------------

    @Test
    @DisplayName("a label that names one of our own windows uses that window's own name")
    void crossWindowLabelsUseTheWindowsName() throws IOException {
        Map<String, String> pairs = windowReferences();
        assertThat(pairs)
                .as("labels bound to a window id — an empty scan would prove nothing")
                .hasSizeGreaterThanOrEqualTo(16);
        Map<String, String> windowNameKeys = windowNameKeys();
        assertThat(windowNameKeys)
                .as("windows with a Window-menu action — an empty scan would prove nothing")
                .hasSizeGreaterThanOrEqualTo(14);
        Map<String, Map<String, String>> strings = productStrings();

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> pair : pairs.entrySet()) {
            String nameKey = windowNameKeys.get(pair.getValue());
            if (nameKey == null) {
                continue; // not a window with a Window-menu action (the Terminal row)
            }
            for (String loc : new LinkedHashSet<>(concat("en", LOCALES))) {
                String label = strings.get(loc).get(pair.getKey());
                String window = strings.get(loc).get(nameKey);
                if (label == null || window == null) {
                    continue;
                }
                // the Welcome's links carry the chord after two spaces
                String head = label.split(" {2}")[0].trim();
                if (!head.equals(window.trim())) {
                    problems.add(loc + " " + pair.getKey() + ": \"" + head
                            + "\" names the window called \"" + window.trim() + "\"");
                }
            }
        }
        assertThat(problems).as("one window, two names").isEmpty();
    }

    private static List<String> concat(String first, List<String> rest) {
        List<String> out = new ArrayList<>();
        out.add(first);
        out.addAll(rest);
        return out;
    }

    /**
     * Label key → TopComponent id, read from the source lines that bind the
     * two. Both surfaces put the pair within a few lines of each other, so
     * the population is derived: add a shelf row and this test covers it.
     */
    private static Map<String, String> windowReferences() throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        String welcome = read(Path.of("..", "ui", "src", "main", "java", "org", "nmox",
                "studio", "ui", "MainWindow.java"));
        Matcher m = Pattern.compile(
                "windowLink\\(Bundle\\.(\\w+)\\(\\),\\s*\"(\\w+TopComponent)\"").matcher(welcome);
        while (m.find()) {
            out.put(m.group(1), m.group(2));
        }
        String workbench = read(Path.of("..", "project", "src", "main", "java", "org", "nmox",
                "studio", "project", "ProjectExplorerTopComponent.java"));
        Matcher r = Pattern.compile(
                "row\\(Bundle\\.(\\w+)\\(\\),[\\s\\S]{0,400}?(?:openWindow\\(\"(\\w+TopComponent)\"\\)"
                        + "|(\\w+TopComponent)\\.openPanel\\(\\))").matcher(workbench);
        while (r.find()) {
            out.put(r.group(1), r.group(2) != null ? r.group(2) : r.group(3));
        }
        return out;
    }

    /**
     * TopComponent id → the bundle key its Window-menu action displays. Read
     * from each module's GENERATED layer inside the assembled jar: that entry
     * is what the Window menu paints, so it is the window's own name by
     * construction rather than by convention.
     */
    private static Map<String, String> windowNameKeys() throws IOException {
        Map<String, String> out = new TreeMap<>();
        // one <file …instance"> block per Window action; attribute ORDER is the
        // processor's business, not ours (the first cut of this gate assumed
        // name= came before bundlevalue= and matched nothing — a gate that
        // measures zero cases passes for the wrong reason, which is why the
        // populations below carry floors)
        Pattern block = Pattern.compile("<file name=\"[^\"]+\\.instance\">([\\s\\S]*?)</file>");
        Pattern preferred = Pattern.compile("name=\"preferredID\"\\s+stringvalue=\"(\\w+)\"");
        Pattern display = Pattern.compile("bundlevalue=\"[^\"#]+#(\\w+)\"\\s+name=\"displayName\"");
        for (Path jar : productJars()) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                ZipEntry e = zip.getEntry("META-INF/generated-layer.xml");
                if (e == null) {
                    continue;
                }
                String xml;
                try (InputStream in = zip.getInputStream(e)) {
                    xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                // a layer carries TWO "Window" folders — the Menu shadows and
                // the Actions instances — so anchor on the instance shape, not
                // on the first folder of that name (which for a single-window
                // module is the menu one, and lost three studios)
                Matcher m = block.matcher(xml);
                while (m.find()) {
                    Matcher id = preferred.matcher(m.group(1));
                    Matcher d = display.matcher(m.group(1));
                    if (id.find() && d.find()) {
                        out.put(id.group(1), d.group(1));
                    }
                }
            }
        }
        return out;
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String trimName(String raw) {
        return raw == null ? null : raw.replace("…", "").replace("...", "").trim();
    }
}
