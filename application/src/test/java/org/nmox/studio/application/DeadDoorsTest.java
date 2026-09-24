package org.nmox.studio.application;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A door that can never open is not shown (3.1.0). The platform's
 * bug-tracking module ships its Team-menu rows ("Find Tasks...",
 * "Report Task..."), a chord and a Window-menu dashboard - and no connector
 * ships with it (no Bugzilla, no JIRA, no issue provider), so every one of
 * them is disabled or empty forever. 2.118.0 hid the dashboard; 3.1.0
 * found the Team rows in the same state, and the platform's own start-page
 * "Show Dashboard", which has no widgets to show and opened nothing.
 *
 * <p>Derived from the assembled cluster: every {@code Menu/}, {@code Shortcuts/}
 * and {@code Toolbars/} file the bug-tracking and tasks modules contribute
 * must be masked by a {@code _hidden} entry somewhere in the cluster. If a
 * connector module ever ships, this test says so by name, because then the
 * doors can open and the masks need a new decision.
 */
class DeadDoorsTest {

    private static final Path CLUSTER = Path.of("target", "nmoxstudio");
    private static final Set<String> OWNERS = Set.of(
            "org-netbeans-modules-bugtracking.jar", "org-netbeans-modules-tasks.jar",
            // the platform's start-page dashboard: a frame for widgets, and
            // no module in this cluster registers one - it opened nothing
            "org-netbeans-api-dashboard.jar");

    @Test
    @DisplayName("every bug-tracking door is hidden while no connector ships")
    void deadDoorsAreHidden() throws Exception {
        assertThat(CLUSTER).as("the assembled cluster (run after package)").isDirectory();
        TreeSet<String> doors = new TreeSet<>();
        TreeSet<String> masks = new TreeSet<>();
        List<String> connectors = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(CLUSTER)) {
            for (Path jar : walk.filter(p -> p.toString().endsWith(".jar") && p.toString().contains("modules"))
                    .toList()) {
                String name = jar.getFileName().toString();
                if (name.matches(".*(bugzilla|jira|odcs|redmine|github-issues).*")) {
                    connectors.add(name);
                }
                try (JarFile jf = new JarFile(jar.toFile())) {
                    String declared = jf.getManifest() == null ? null
                            : jf.getManifest().getMainAttributes().getValue("OpenIDE-Module-Layer");
                    for (String layer : new String[] {declared, "META-INF/generated-layer.xml"}) {
                        ZipEntry e = layer == null ? null : jf.getEntry(layer);
                        if (e != null) {
                            collect(parse(jf.getInputStream(e).readAllBytes()), "",
                                    OWNERS.contains(name), doors, masks);
                        }
                    }
                }
            }
        }
        assertThat(connectors).as("a bug-tracking connector ships: these doors can open now - decide again")
                .isEmpty();
        assertThat(doors).as("the census found the bug-tracking doors").isNotEmpty();
        TreeSet<String> open = new TreeSet<>(doors);
        open.removeAll(masks);
        assertThat(open).as("bug-tracking doors a user can see and never use").isEmpty();
    }

    /**
     * The "Set Project Configuration" combo and its Run-menu row fill from
     * a project type's {@code ProjectConfigurationProvider}, and none ships:
     * NMOX's WebProject offers no configurations and no Maven, Gradle or
     * Ant project module is in the cluster. Both are masked; this names the
     * provider or module that would make them useful again.
     */
    @Test
    @DisplayName("the empty project-configuration combo stays hidden while no project type has configurations")
    void noConfigurationsNoCombo() throws Exception {
        List<String> providers = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(Path.of(".."))) {
            walk.filter(p -> p.toString().endsWith(".java") && p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/.claude/"))
                    .forEach(p -> {
                        try {
                            if (Files.readString(p).contains("ProjectConfigurationProvider")) {
                                providers.add(p.toString());
                            }
                        } catch (java.io.IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });
        }
        try (Stream<Path> walk = Files.walk(CLUSTER)) {
            walk.filter(p -> p.getFileName().toString()
                    .matches("org-netbeans-modules-(maven|gradle|java-j2seproject|ant-freeform)\\.jar"))
                    .forEach(p -> providers.add(p.getFileName().toString()));
        }
        assertThat(providers).as("a project type with configurations ships: unmask the combo and its menu row")
                .isEmpty();
        String ui = Files.readString(Path.of("..", "ui", "src", "main", "resources", "org", "nmox", "studio", "ui", "layer.xml"));
        assertThat(ui.split("org-netbeans-modules-project-ui-actions-ActiveConfigAction.shadow_hidden", -1))
                .as("both the toolbar combo and the menu row are masked").hasSize(3);
    }

    private static void collect(Element el, String path, boolean owner, Set<String> doors, Set<String> masks) {
        for (Node n = el.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element c) {
                String tag = c.getTagName();
                if (!"folder".equals(tag) && !"file".equals(tag)) {
                    continue;
                }
                String p = path + c.getAttribute("name") + ("folder".equals(tag) ? "/" : "");
                boolean surface = p.startsWith("Menu/") || p.startsWith("Shortcuts/") || p.startsWith("Toolbars/");
                if ("file".equals(tag) && surface) {
                    if (p.endsWith("_hidden")) {
                        masks.add(p.substring(0, p.length() - "_hidden".length()));
                    } else if (owner) {
                        doors.add(p);
                    }
                }
                collect(c, p, owner, doors, masks);
            }
        }
    }

    private static Element parse(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setValidating(false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setExpandEntityReferences(false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
    }
}
