package org.nmox.studio.application;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The boot census, as a build law. The platform warns on every boot when a
 * layer folder mixes positioned and unpositioned rows (their order is then
 * undefined) or when two rows share a position; the v2.69.6 walk read three
 * such warnings naming OUR rows in the shipped app — the Tests window's
 * unpositioned wstcref beside its positioned siblings, and two menu
 * collisions (Menu/File 122, Menu/Window 258) between modules that each
 * looked clean alone. Per-module gates cannot see a cross-module collision,
 * so this one reads every shipped module's layers out of the assembled
 * cluster (integration-test phase, after package) and fails by folder and
 * name.
 *
 * <p>v2.104.0 closed its own written blind spot. It used to say a collision
 * with a PLATFORM row was "the boot log's to name" — and the boot log duly
 * named one: our Save Screenshot sat at Menu/Tools position 100, where the
 * platform's own Tools row already sat, and the gate could not see it
 * because only one of the two rows was ours. The census now also reads the
 * platform and ide clusters, but only for the folders NMOX actually writes
 * into, so the check stays about our own registrations while seeing
 * everything they collide with. A gate that reads half the population is a
 * gate that goes green on a real collision.
 */
class LayerPositionCensusTest {

    private static final Path MODULES = Path.of("target/nmoxstudio/nmoxstudio/modules");

    /** The clusters whose rows ours land beside in the same folders. */
    private static final List<Path> NEIGHBOUR_CLUSTERS = List.of(
            Path.of("target/nmoxstudio/platform/modules"),
            Path.of("target/nmoxstudio/ide/modules"),
            Path.of("target/nmoxstudio/java/modules"),
            Path.of("target/nmoxstudio/extra/modules"));

    /** folder path -> (file name -> position or null), across every module's layers. */
    private static Map<String, Map<String, Integer>> census() throws Exception {
        Map<String, Map<String, Integer>> folders = new LinkedHashMap<>();
        List<Path> jars;
        try (Stream<Path> s = Files.list(MODULES)) {
            jars = s.filter(p -> p.getFileName().toString().startsWith("org-nmox-")
                    && p.toString().endsWith(".jar")).sorted().toList();
        }
        assertThat(jars).as("the assembled cluster's NMOX module jars").hasSize(11);
        for (Path jar : jars) {
            try (JarFile jf = new JarFile(jar.toFile())) {
                List<String> layers = new ArrayList<>();
                layers.add("META-INF/generated-layer.xml");
                String declared = jf.getManifest().getMainAttributes().getValue("OpenIDE-Module-Layer");
                if (declared != null) {
                    layers.add(declared);
                }
                for (String name : layers) {
                    if (jf.getEntry(name) == null) {
                        continue;
                    }
                    try (InputStream in = jf.getInputStream(jf.getEntry(name))) {
                        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
                        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
                        Element root = f.newDocumentBuilder().parse(in).getDocumentElement();
                        walk(root, "", folders, jar.getFileName().toString());
                    }
                }
            }
        }
        // and every row the PLATFORM puts in a folder we write into: those are
        // what our positions actually compete with at boot (v2.104.0)
        for (Path cluster : NEIGHBOUR_CLUSTERS) {
            if (!Files.isDirectory(cluster)) {
                continue;
            }
            List<Path> theirs;
            try (Stream<Path> s = Files.walk(cluster)) {
                theirs = s.filter(p -> p.toString().endsWith(".jar")).sorted().toList();
            }
            for (Path jar : theirs) {
                try (JarFile jf = new JarFile(jar.toFile())) {
                    Map<String, Map<String, Integer>> mine = new LinkedHashMap<>();
                    for (String name : layerNames(jf)) {
                        if (jf.getEntry(name) == null) {
                            continue;
                        }
                        try (InputStream in = jf.getInputStream(jf.getEntry(name))) {
                            Element root = builder().parse(in).getDocumentElement();
                            walk(root, "", mine, jar.getFileName().toString());
                        }
                    }
                    mine.forEach((folder, rows) -> {
                        if (folders.containsKey(folder)) {
                            folders.get(folder).putAll(rows);
                        }
                    });
                } catch (Exception unreadable) {
                    // a platform jar with no layer, or one this parser cannot
                    // read, is not this gate's business — it fails on OUR rows
                }
            }
        }
        return folders;
    }

    private static List<String> layerNames(JarFile jf) throws Exception {
        List<String> layers = new ArrayList<>();
        layers.add("META-INF/generated-layer.xml");
        String declared = jf.getManifest() == null ? null
                : jf.getManifest().getMainAttributes().getValue("OpenIDE-Module-Layer");
        if (declared != null) {
            layers.add(declared);
        }
        return layers;
    }

    private static javax.xml.parsers.DocumentBuilder builder() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        return f.newDocumentBuilder();
    }

    private static void walk(Element folder, String path, Map<String, Map<String, Integer>> out, String module) {
        NodeList kids = folder.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (!(n instanceof Element e)) {
                continue;
            }
            if ("folder".equals(e.getTagName())) {
                walk(e, path + e.getAttribute("name") + "/", out, module);
            } else if ("file".equals(e.getTagName()) && !e.getAttribute("name").endsWith("_hidden")) {
                // a *_hidden mask suppresses a platform row; it carries no position
                // by design and the platform's Ordering census ignores it too
                out.computeIfAbsent(path, k -> new LinkedHashMap<>())
                        .put(e.getAttribute("name") + " (" + module + ")", position(e));
            }
        }
    }

    /** "Foo.shadow (some-module.jar)" -> "Foo.shadow". */
    private static String rowName(String censusKey) {
        int paren = censusKey.indexOf(" (");
        return paren < 0 ? censusKey : censusKey.substring(0, paren);
    }

    private static Integer position(Element file) {
        NodeList attrs = file.getElementsByTagName("attr");
        for (int i = 0; i < attrs.getLength(); i++) {
            Element a = (Element) attrs.item(i);
            if ("position".equals(a.getAttribute("name")) && a.getParentNode() == file) {
                return Integer.valueOf(a.getAttribute("intvalue"));
            }
        }
        return null;
    }

    @Test
    @DisplayName("No layer folder mixes positioned and unpositioned NMOX rows (the Ordering warning's first shape)")
    void noMixedPositioning() throws Exception {
        List<String> mixed = new ArrayList<>();
        for (var f : census().entrySet()) {
            // the CSL processor emits PLATFORM rows (org-netbeans-*) into our
            // generated layer without positions — we cannot position them,
            // blessed in place since v2.28.0; the mixed check reads our own rows
            Map<String, Integer> ours = new LinkedHashMap<>();
            f.getValue().forEach((name, pos) -> {
                // the marker is the OWNING JAR, not the row's own name: the CSL
                // processor emits platform-named rows into our layer, and since
                // v2.104.0 the census also carries the platform's own rows in
                // folders we write into — neither is ours to position
                // both halves matter: the row must come from one of OUR jars
                // (since v2.104.0 the census also carries the platform's own
                // rows in folders we write into), and it must not be one of
                // the platform-named rows the CSL processor emits into our
                // generated layer, which we cannot position — blessed v2.28.0
                if (name.contains("(org-nmox-") && !name.startsWith("org-netbeans-")) {
                    ours.put(name, pos);
                }
            });
            long positioned = ours.values().stream().filter(v -> v != null).count();
            if (positioned > 0 && positioned < ours.size()) {
                ours.forEach((name, pos) -> {
                    if (pos == null) {
                        mixed.add(f.getKey() + name);
                    }
                });
            }
        }
        assertThat(mixed).as("unpositioned rows beside positioned siblings — give each a position").isEmpty();
    }

    @Test
    @DisplayName("No NMOX row shares a position with anything in its folder (the Ordering warning's second shape)")
    void noDuplicatePositions() throws Exception {
        List<String> dups = new ArrayList<>();
        for (var f : census().entrySet()) {
            Map<Integer, String> seen = new LinkedHashMap<>();
            f.getValue().forEach((name, pos) -> {
                if (pos == null) {
                    return;
                }
                String other = seen.putIfAbsent(pos, name);
                if (other == null) {
                    return;
                }
                // two modules declaring the same file name in the same folder
                // is ONE row the platform merges, not two rows competing — the
                // shared separator every CSL popup contributes is the case
                if (rowName(other).equals(rowName(name))) {
                    return;
                }
                // and a collision only matters if one of the two is ours
                if (!other.contains("(org-nmox-") && !name.contains("(org-nmox-")) {
                    return;
                }
                dups.add(f.getKey() + " @" + pos + ": " + other + " vs " + name);
            });
        }
        assertThat(dups).as("same position, same folder — the platform picks an order and warns").isEmpty();
    }
}
