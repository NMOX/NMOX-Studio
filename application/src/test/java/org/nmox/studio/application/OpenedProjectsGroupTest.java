package org.nmox.studio.application;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What opens when a project opens (3.1.0). Aiming a project opens it as a
 * platform project, and the platform's {@code OpenedProjects} window group
 * then opens every member: Projects, Files, Favorites, Services and the
 * Navigator. Four of them are more explorers of the folder Project Studio
 * already shows, and they crowded the left dock until the product's own
 * tabs read "W...". NMOX masks those four; the Navigator (the outline)
 * stays.
 *
 * <p>The population is DERIVED from every layer in the assembled cluster,
 * so a platform upgrade that adds a member to the group fails here by name
 * until someone decides whether it should open with a project.
 */
class OpenedProjectsGroupTest {

    private static final Path CLUSTER = Path.of("target", "nmoxstudio");
    private static final String GROUP = "Windows2/Groups/OpenedProjects/";

    @Test
    @DisplayName("opening a project opens the Navigator and no other platform explorer")
    void onlyTheNavigatorOpensWithAProject() throws Exception {
        assertThat(CLUSTER).as("the assembled cluster (run after package)").isDirectory();
        Map<String, String> members = new TreeMap<>();
        TreeSet<String> masked = new TreeSet<>();
        int layers = 0;
        try (Stream<Path> jars = Files.walk(CLUSTER)) {
            for (Path jar : jars.filter(p -> p.toString().endsWith(".jar") && p.toString().contains("modules"))
                    .toList()) {
                try (JarFile jf = new JarFile(jar.toFile())) {
                    String declared = jf.getManifest() == null ? null
                            : jf.getManifest().getMainAttributes().getValue("OpenIDE-Module-Layer");
                    for (String name : new String[] {declared, "META-INF/generated-layer.xml"}) {
                        ZipEntry e = name == null ? null : jf.getEntry(name);
                        if (e == null) {
                            continue;
                        }
                        layers++;
                        Element root = parse(jf.getInputStream(e).readAllBytes());
                        collect(root, "", jar.getFileName().toString(), members, masked);
                    }
                }
            }
        }
        assertThat(layers).as("the census read the cluster's layers").isGreaterThan(100);
        assertThat(members).as("the platform still declares the group's members")
                .containsKeys("projectTabLogical_tc", "projectTab_tc", "favorites", "services", "navigatorTC");
        TreeSet<String> opens = new TreeSet<>(members.keySet());
        opens.removeAll(masked);
        assertThat(opens).as("what opens with a project; a new member needs a decision")
                .containsExactly("navigatorTC");
    }

    private static void collect(Element el, String path, String jar, Map<String, String> members, TreeSet<String> masked) {
        for (Node n = el.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element c && ("folder".equals(c.getTagName()) || "file".equals(c.getTagName()))) {
                String p = path + c.getAttribute("name") + ("folder".equals(c.getTagName()) ? "/" : "");
                if ("file".equals(c.getTagName()) && p.startsWith(GROUP)) {
                    String file = p.substring(GROUP.length());
                    if (file.endsWith(".wstcgrp_hidden")) {
                        masked.add(file.substring(0, file.length() - ".wstcgrp_hidden".length()));
                    } else if (file.endsWith(".wstcgrp")) {
                        members.put(file.substring(0, file.length() - ".wstcgrp".length()), jar);
                    }
                }
                collect(c, p, jar, members, masked);
            }
        }
    }

    private static Element parse(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setValidating(false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        f.setExpandEntityReferences(false);
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new ByteArrayInputStream(xml)).getDocumentElement();
    }
}
