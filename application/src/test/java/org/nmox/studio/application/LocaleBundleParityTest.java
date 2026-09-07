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

    static final List<String> LOCALES = List.of("es", "fr", "de", "ru", "hi");
    private static final Pattern LOCALIZED = Pattern.compile("(.*/)Bundle_(" + String.join("|", LOCALES) + ")\\.properties");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");
    /**
     * The floor on localized packages across the product's own jars — a
     * translation directory deleted by accident fails the build by name.
     * Raise it when a new package is localized; never lower it.
     */
    static final int LOCALIZED_PACKAGES_FLOOR = 1;

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
        for (Path jar : jars(cluster().resolve("modules"))) {
            if (!jar.getFileName().toString().startsWith("org-nmox-studio-")) {
                continue;
            }
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
                    for (String locale : LOCALES) {
                        Properties t = pkg.getValue().get(locale);
                        if (t == null) {
                            problems.add(where + ": missing Bundle_" + locale + ".properties");
                            continue;
                        }
                        Set<String> want = new TreeSet<>(english.stringPropertyNames());
                        Set<String> have = new TreeSet<>(t.stringPropertyNames());
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
