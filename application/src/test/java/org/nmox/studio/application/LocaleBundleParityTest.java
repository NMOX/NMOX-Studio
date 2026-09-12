package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
 * The IDE speaks six languages by construction (v2.97.0): this gate reads
 * the ASSEMBLED cluster — the outcome, not the sources — and fails the
 * build when any localized package is missing a language, carries a key
 * the English bundle does not (or lacks one it does), or changes a
 * message's placeholder count in translation (a {@code {0}} dropped in
 * French is a MessageFormat exception in French only). The branding
 * overlays that localize the platform's menu names are held to the same
 * shape across the five locale jars. Runs at integration-test phase like
 * the site gate: the cluster exists only after {@code package}.
 */
class LocaleBundleParityTest {

    static final List<String> LOCALES = List.of("es", "fr", "de", "ru", "uk", "pl", "pt", "id", "tl", "vi", "zh", "hi");
    private static final Pattern LOCALIZED = Pattern.compile("(.*/)Bundle_(" + String.join("|", LOCALES) + ")\\.properties");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");
    /**
     * The cluster's own name for a product jar. NOT {@code org-nmox-studio-}:
     * the module code-name-base is {@code org.nmox.NMOX.Studio.<module>}, so
     * the assembled jar reads {@code org-nmox-NMOX-Studio-ui.jar}. The first
     * cut of this gate guessed the lower-cased form, matched nothing, and
     * measured perfect parity over zero jars — which is what the floor below
     * exists to catch, and did. Compared case-insensitively so a future
     * casing change fails the name assertion loudly instead of silently
     * emptying the scan again.
     */
    private static final String PRODUCT_JAR_PREFIX = "org-nmox-nmox-studio-";

    /**
     * The floor on localized packages across the product's own jars — a
     * translation directory deleted by accident fails the build by name.
     * Measured on the assembled cluster at v2.97.0: 71 packages across nine
     * module jars. Raise it when a new package is localized; never lower it.
     */
    static final int LOCALIZED_PACKAGES_FLOOR = 71;

    private static Path cluster() {
        return Path.of("target/nmoxstudio/nmoxstudio");
    }

    private static List<Path> jars(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".jar")).sorted().collect(Collectors.toList());
        }
    }

    private static Properties load(ZipFile zip, ZipEntry e) throws IOException {
        Properties p = new Properties();
        try (InputStream in = zip.getInputStream(e)) {
            p.load(in);
        }
        return p;
    }

    /** Drops the module-descriptor keys this arc leaves to the manifests. */
    /**
     * A translated key whose English is NOT in the base bundle, because it
     * has one home somewhere better.
     *
     * <p>Five catalogue seams (v2.132.0–v2.134.0) share one shape: the
     * English is the RECORD — {@code DeviceType}, which generates
     * {@code docs/devices.md}; the learning catalogue's own
     * {@code Category.label} and {@code family} strings; the template,
     * block and chain enums this codebase reads — and a copy in a base
     * bundle would be the second home v2.131.0 spent a release removing.
     *
     * <p>This gate used to keep its own list of their key prefixes, and
     * that list was itself a second home: it went stale twice (v2.132.0
     * taught it two families, v2.134.0 four more), and each time the
     * symptom was this gate failing on keys that were perfectly correct,
     * with a base-bundle copy as the tempting way to quiet it. The fact
     * lives at the seams now — see {@link SeamKeyPrefixes} — so a sixth
     * seam is exempt the moment it declares what it owns, and a seam that
     * declares the WRONG prefix still fails here, by name, on its real
     * keys.
     *
     * <p>Skipping a family is not leaving it unchecked:
     * {@code DeviceShelfSpeaksTest}, {@code LearningCatalogSpeaksTest} and
     * {@code CatalogueEnumsSpeakTest} hold these to a STRICTER population
     * than this gate could — the shipped catalogues and enums themselves.
     */
    private static boolean baseLivesElsewhere(String key) {
        return BASE_LIVES_ELSEWHERE.stream().anyMatch(key::startsWith);
    }

    private static final java.util.Set<String> BASE_LIVES_ELSEWHERE = SeamKeyPrefixes.all();

    private static Set<Integer> placeholders(String value) {
        Set<Integer> out = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(value == null ? "" : value);
        while (m.find()) {
            out.add(Integer.parseInt(m.group(1)));
        }
        return out;
    }

    @Test
    @DisplayName("every localized package in the product's own jars carries all five locales with the English key set and placeholders")
    void productBundlesAgree() throws IOException {
        assertThat(cluster().resolve("modules")).as("the assembled cluster exists after package").isDirectory();
        List<String> problems = new ArrayList<>();
        int localizedPackages = 0;
        List<Path> productJars = jars(cluster().resolve("modules")).stream()
                .filter(j -> j.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                        .startsWith(PRODUCT_JAR_PREFIX))
                .toList();
        assertThat(productJars)
                .as("product jars in the cluster matching '" + PRODUCT_JAR_PREFIX
                        + "' — an empty scan would measure perfect parity over nothing")
                .isNotEmpty();
        for (Path jar : productJars) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                Map<String, Map<String, Properties>> byPackage = new TreeMap<>();
                Map<String, Properties> base = new HashMap<>();
                for (ZipEntry e : zip.stream().collect(Collectors.toList())) {
                    String n = e.getName();
                    Matcher m = LOCALIZED.matcher(n);
                    if (m.matches()) {
                        byPackage.computeIfAbsent(m.group(1), k -> new TreeMap<>()).put(m.group(2), load(zip, e));
                    } else if (n.endsWith("/Bundle.properties")) {
                        base.put(n.substring(0, n.length() - "Bundle.properties".length()), load(zip, e));
                    }
                }
                for (Map.Entry<String, Map<String, Properties>> pkg : byPackage.entrySet()) {
                    localizedPackages++;
                    String where = jar.getFileName() + "!" + pkg.getKey();
                    Properties english = base.get(pkg.getKey());
                    if (english == null) {
                        problems.add(where + ": localized bundles with no English Bundle.properties");
                        continue;
                    }
                    // Module descriptors (OpenIDE-Module-Name and friends) are
                    // ordinary keys since v2.140.0: every module declares its
                    // localizing bundle in the manifest, so the Plugin Manager
                    // reads the name and descriptions through the same locale
                    // siblings as the chrome, and this gate holds them to the
                    // same parity. ModuleDescriptorsSpeakTest holds the manifest
                    // half — that the bundle IS declared and no English copy is
                    // burned into the jar beside it.
                    if (english.isEmpty()) {
                        continue;
                    }
                    for (String locale : LOCALES) {
                        Properties t = pkg.getValue().get(locale);
                        if (t == null) {
                            problems.add(where + ": missing Bundle_" + locale + ".properties");
                            continue;
                        }
                        Set<String> want = new TreeSet<>(english.stringPropertyNames());
                        Set<String> have = new TreeSet<>(t.stringPropertyNames());
                        have.removeIf(LocaleBundleParityTest::baseLivesElsewhere);
                        if (!want.equals(have)) {
                            Set<String> missing = new TreeSet<>(want);
                            missing.removeAll(have);
                            Set<String> extra = new TreeSet<>(have);
                            extra.removeAll(want);
                            problems.add(where + " " + locale + ": missing " + missing + " extra " + extra);
                        }
                        for (String key : want) {
                            String value = t.getProperty(key);
                            if (value == null) {
                                continue;
                            }
                            if (value.isBlank()) {
                                problems.add(where + " " + locale + ": blank value for " + key);
                            }
                            if (!placeholders(value).equals(placeholders(english.getProperty(key)))) {
                                problems.add(where + " " + locale + ": placeholders differ for " + key);
                            }
                        }
                    }
                }
            }
        }
        assertThat(problems).as("locale bundle parity").isEmpty();
        assertThat(localizedPackages).as("localized packages (raise the floor when you add one)")
                .isGreaterThanOrEqualTo(LOCALIZED_PACKAGES_FLOOR);
    }

    @Test
    @DisplayName("the branding overlays that localize the platform's menus exist for all five locales with identical shapes")
    void brandingOverlaysAgree() throws IOException {
        List<Path> localeJars = new ArrayList<>();
        localeJars.addAll(jars(cluster().resolve("modules/locale")));
        localeJars.addAll(jars(cluster().resolve("core/locale")));
        // base jar name -> locale -> entry -> keys
        Map<String, Map<String, Map<String, Set<String>>>> shapes = new TreeMap<>();
        Pattern named = Pattern.compile("(.+)_nmoxstudio_(" + String.join("|", LOCALES) + ")\\.jar");
        for (Path jar : localeJars) {
            Matcher m = named.matcher(jar.getFileName().toString());
            if (!m.matches()) {
                continue;
            }
            Map<String, Set<String>> entries = new TreeMap<>();
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                for (ZipEntry e : zip.stream().collect(Collectors.toList())) {
                    if (e.getName().endsWith(".properties")) {
                        String entry = e.getName().replace("_nmoxstudio_" + m.group(2) + ".properties", ".properties");
                        entries.put(entry, new TreeSet<>(load(zip, e).stringPropertyNames()));
                    }
                }
            }
            shapes.computeIfAbsent(m.group(1), k -> new TreeMap<>()).put(m.group(2), entries);
        }
        assertThat(shapes).as("at least the core-ui menu overlay is localized").isNotEmpty();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, Set<String>>>> base : shapes.entrySet()) {
            if (!base.getValue().keySet().containsAll(LOCALES)) {
                problems.add(base.getKey() + ": locales " + base.getValue().keySet() + " != " + LOCALES);
                continue;
            }
            Map<String, Set<String>> reference = base.getValue().get(LOCALES.get(0));
            for (String locale : LOCALES) {
                if (!base.getValue().get(locale).equals(reference)) {
                    problems.add(base.getKey() + " " + locale + ": entries/keys differ from " + LOCALES.get(0));
                }
            }
        }
        assertThat(problems).as("branding overlay parity").isEmpty();
    }
}
