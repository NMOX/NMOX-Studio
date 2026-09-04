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
 * name. Runs over the modules' own layers: a collision with a platform row
 * is still the boot log's to name.
 */
class LayerPositionCensusTest {

    private static final Path MODULES = Path.of("target/nmoxstudio/nmoxstudio/modules");

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
        return folders;
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
            long positioned = f.getValue().values().stream().filter(v -> v != null).count();
            if (positioned > 0 && positioned < f.getValue().size()) {
                f.getValue().forEach((name, pos) -> {
                    if (pos == null) {
                        mixed.add(f.getKey() + name);
                    }
                });
            }
        }
        assertThat(mixed).as("unpositioned rows beside positioned siblings — give each a position").isEmpty();
    }

    @Test
    @DisplayName("No two NMOX rows share a position in a folder (the Ordering warning's second shape)")
    void noDuplicatePositions() throws Exception {
        List<String> dups = new ArrayList<>();
        for (var f : census().entrySet()) {
            Map<Integer, String> seen = new LinkedHashMap<>();
            f.getValue().forEach((name, pos) -> {
                if (pos != null) {
                    String other = seen.putIfAbsent(pos, name);
                    if (other != null) {
                        dups.add(f.getKey() + " @" + pos + ": " + other + " vs " + name);
                    }
                }
            });
        }
        assertThat(dups).as("same position, same folder — the platform picks an order and warns").isEmpty();
    }
}
