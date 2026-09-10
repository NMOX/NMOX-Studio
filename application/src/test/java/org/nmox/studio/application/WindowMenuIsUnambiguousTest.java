package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two rows in one menu may not answer to the same name (v2.118.0, the
 * coherence pass).
 *
 * <p>Measured live in the shipped 2.108.0 menu bar: <b>Window carried two
 * items both labelled "Tasks"</b> — the product's own per-project board and
 * the platform's bug-tracking dashboard, which cannot show anything here
 * because this cluster ships no issue connector. A user reaching for one
 * had no way to tell which was which, and the product's own window was the
 * one that lost. Per-module gates could never see it: each module's rows
 * are unique on their own, and the collision only exists in the assembled
 * menu.
 *
 * <p>So this reads the ASSEMBLED clusters — ours and the four we inherit —
 * resolves every visible Window-menu row to the display name the menu bar
 * actually paints, and fails on a repeat. Hiding a platform row (the v1.11
 * VCS-museum idiom) removes it from the population, which is the honest fix
 * when the row is chrome for a feature this product does not ship; renaming
 * ours is the honest fix when both belong.
 */
class WindowMenuIsUnambiguousTest {

    private static final Path OURS = Path.of("target/nmoxstudio/nmoxstudio/modules");

    private static final List<Path> ALL_CLUSTERS = List.of(
            Path.of("target/nmoxstudio/nmoxstudio/modules"),
            Path.of("target/nmoxstudio/platform/modules"),
            Path.of("target/nmoxstudio/ide/modules"),
            Path.of("target/nmoxstudio/java/modules"),
            Path.of("target/nmoxstudio/extra/modules"));

    private static final Pattern SHADOW = Pattern.compile(
            "<file name=\"([^\"]+)\\.shadow\">([\\s\\S]*?)</file>");
    private static final Pattern HIDDEN = Pattern.compile(
            "<file name=\"([^\"]+)\\.shadow_hidden\"\\s*/>");
    private static final Pattern ORIGINAL = Pattern.compile(
            "name=\"originalFile\"\\s+stringvalue=\"([^\"]+)\"");
    private static final Pattern INSTANCE = Pattern.compile(
            "<file name=\"([^\"]+\\.instance)\">([\\s\\S]*?)</file>");
    /** The processor writes the attributes in either order; do not assume one. */
    private static final Pattern DISPLAY = Pattern.compile(
            "bundlevalue=\"([^\"#]+)#(\\w+)\"\\s+name=\"displayName\""
            + "|name=\"displayName\"\\s+bundlevalue=\"([^\"#]+)#(\\w+)\"");

    private static List<Path> jars() throws IOException {
        List<Path> out = new ArrayList<>();
        for (Path dir : ALL_CLUSTERS) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.toString().endsWith(".jar")).sorted().forEach(out::add);
            }
        }
        return out;
    }

    /** Every layer document in a module jar: the hand-written one and the generated one. */
    private static List<String> layers(ZipFile zip) throws IOException {
        List<String> out = new ArrayList<>();
        for (ZipEntry e : zip.stream().toList()) {
            if (e.getName().endsWith("layer.xml")) {
                try (InputStream in = zip.getInputStream(e)) {
                    out.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return out;
    }

    /** The section of a layer document inside {@code <folder name="Window">} under Menu. */
    private static String menuWindowSection(String xml) {
        int menu = xml.indexOf("<folder name=\"Menu\">");
        if (menu < 0) {
            return "";
        }
        int window = xml.indexOf("<folder name=\"Window\">", menu);
        if (window < 0) {
            return "";
        }
        int end = xml.indexOf("</folder>", window);
        return end < 0 ? xml.substring(window) : xml.substring(window, end);
    }

    @Test
    @DisplayName("no two visible Window-menu rows answer to the same name")
    void windowMenuNamesAreUnique() throws IOException {
        List<Path> jars = jars();
        assertThat(jars).as("the assembled clusters exist after package").isNotEmpty();

        Map<String, String> instanceDisplay = new HashMap<>();   // instance path -> bundle#key
        Map<String, Properties> bundles = new HashMap<>();       // bundle fqn -> values
        Map<String, String> rows = new LinkedHashMap<>();        // shadow name -> instance path
        Set<String> hidden = new LinkedHashSet<>();

        for (Path jar : jars) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                for (ZipEntry e : zip.stream().toList()) {
                    String n = e.getName();
                    if (n.endsWith("/Bundle.properties")) {
                        Properties p = new Properties();
                        try (InputStream in = zip.getInputStream(e)) {
                            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
                        }
                        bundles.put(n.substring(0, n.length() - ".properties".length())
                                .replace('/', '.'), p);
                    }
                }
                for (String xml : layers(zip)) {
                    Matcher i = INSTANCE.matcher(xml);
                    while (i.find()) {
                        Matcher d = DISPLAY.matcher(i.group(2));
                        if (d.find()) {
                            String bundle = d.group(1) != null ? d.group(1) : d.group(3);
                            String key = d.group(2) != null ? d.group(2) : d.group(4);
                            instanceDisplay.put(leaf(i.group(1)), bundle + "#" + key);
                        }
                    }
                    String section = menuWindowSection(xml);
                    Matcher h = HIDDEN.matcher(section);
                    while (h.find()) {
                        hidden.add(h.group(1));
                    }
                    Matcher s = SHADOW.matcher(section);
                    while (s.find()) {
                        Matcher o = ORIGINAL.matcher(s.group(2));
                        if (o.find()) {
                            rows.put(s.group(1), o.group(1));
                        }
                    }
                }
            }
        }

        assertThat(rows)
                .as("Window-menu rows across the assembled clusters — an empty scan would prove nothing")
                .hasSizeGreaterThanOrEqualTo(20);

        Map<String, List<String>> byName = new TreeMap<>();
        for (Map.Entry<String, String> row : rows.entrySet()) {
            if (hidden.contains(row.getKey())) {
                continue; // hidden: the menu never paints it
            }
            String display = instanceDisplay.get(leaf(row.getValue()));
            if (display == null) {
                continue; // a row whose name the layer does not carry (methodvalue label)
            }
            int hash = display.indexOf('#');
            Properties p = bundles.get(display.substring(0, hash));
            if (p == null) {
                continue;
            }
            String label = p.getProperty(display.substring(hash + 1));
            if (label == null) {
                continue;
            }
            byName.computeIfAbsent(plain(label), k -> new ArrayList<>()).add(row.getKey());
        }

        assertThat(byName)
                .as("resolved Window-menu names — an empty scan would prove nothing")
                .hasSizeGreaterThanOrEqualTo(20);

        List<String> collisions = byName.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> "\"" + e.getKey() + "\" is the name of " + e.getValue())
                .toList();
        assertThat(collisions)
                .as("two rows in the Window menu with one name — the user cannot choose between them")
                .isEmpty();
    }

    /** A shadow's originalFile is a full layer path; an instance is a file name. */
    private static String leaf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    /** "Tas&amp;ks" and "Tasks" are the same row to a reader. */
    private static String plain(String raw) {
        return raw.replace("&", "").trim();
    }

    @Test
    @DisplayName("every window the product ships is reachable from the Window menu")
    void ourWindowsAreAllListed() throws IOException {
        Set<String> listed = new LinkedHashSet<>();
        try (Stream<Path> s = Files.list(OURS)) {
            for (Path jar : s.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (ZipFile zip = new ZipFile(jar.toFile())) {
                    for (String xml : layers(zip)) {
                        Matcher m = SHADOW.matcher(menuWindowSection(xml));
                        while (m.find()) {
                            listed.add(m.group(1));
                        }
                    }
                }
            }
        }
        assertThat(listed)
                .as("the product's own windows in the Window menu — every one is a door "
                        + "the Welcome and the chords also open, and the menu is the one "
                        + "surface that lists them all")
                .hasSizeGreaterThanOrEqualTo(15);
    }
}
