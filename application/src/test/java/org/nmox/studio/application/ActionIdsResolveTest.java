package org.nmox.studio.application;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every action the product looks up by id exists (3.1.0).
 * {@code Actions.forID(category, id)} answers null for an id nobody
 * registered, and the caller's fallback runs as if nothing were wrong:
 * Project Studio's Terminal button asked for
 * {@code Tools/org.netbeans.modules.terminal.nodes.OpenInTerminalAction}
 * from 1.212.0 to 3.1.0 - the real one is
 * {@code Window/org.netbeans.modules.dlight.terminal.action.OpenInTerminalAction}
 * - so "a terminal in the project" opened in the IDE's own directory for
 * four hundred releases, with a comment above it describing the feature.
 *
 * <p>The population is every {@code Actions.forID} in the product's main
 * sources whose two arguments are string literals or {@code static final
 * String} constants of the same file; each must name an
 * {@code Actions/<category>/<id with dots as dashes>.instance} in the
 * assembled cluster.
 */
class ActionIdsResolveTest {

    private static final Path CLUSTER = Path.of("target", "nmoxstudio");
    private static final Pattern CALL = Pattern.compile("Actions\\.forID\\(\\s*([^,()]+?)\\s*,\\s*([^,()]+?)\\s*\\)");
    /** The Welcome's door helper, {@code actionLink(label, category, id)}, which looks the id up for you. */
    private static final Pattern LINK = Pattern.compile("actionLink\\([^;]*?,\\s*(\"[^\"]+\")\\s*,\\s*(\"[^\"]+\")\\s*\\)");
    /**
     * Quick Search's VS Code table, {@code cmd("VS Code title", category, id)},
     * each row resolved through {@code Actions::forID} (3.1.0).
     */
    private static final Pattern CMD = Pattern.compile("\\bcmd\\(\\s*\"[^\"]*\"\\s*,\\s*(\"[^\"]+\")\\s*,\\s*(\"[^\"]+\")\\s*\\)");
    private static final Pattern CONSTANT = Pattern.compile("static\\s+final\\s+String\\s+(\\w+)\\s*=\\s*\"([^\"]*)\"");

    @Test
    @DisplayName("every Actions.forID the product calls names an action the cluster registers")
    void everyLookedUpActionExists() throws Exception {
        Set<String> registered = registeredActions();
        assertThat(registered).as("the census read the cluster's Actions folder").hasSizeGreaterThan(300);
        List<String> missing = new ArrayList<>();
        int calls = 0;
        try (Stream<Path> walk = Files.walk(Path.of(".."))) {
            for (Path p : walk.filter(x -> x.toString().endsWith(".java") && x.toString().replace('\\', '/').contains("/src/main/java/")
                    && !x.toString().replace('\\', '/').contains("/.claude/")).toList()) {
                String src = Files.readString(p);
                if (!src.contains("Actions.forID(") && !src.contains("Actions::forID")) {
                    continue;
                }
                Map<String, String> constants = new HashMap<>();
                Matcher c = CONSTANT.matcher(src);
                while (c.find()) {
                    constants.put(c.group(1), c.group(2));
                }
                List<String[]> pairs = new ArrayList<>();
                for (Pattern shape : new Pattern[] {CALL, LINK, CMD}) {
                    Matcher m = shape.matcher(src);
                    while (m.find()) {
                        pairs.add(new String[] {m.group(1), m.group(2)});
                    }
                }
                for (String[] pair : pairs) {
                    String category = value(pair[0], constants);
                    String id = value(pair[1], constants);
                    if (category == null || id == null) {
                        continue; // computed at run time: out of a static census's reach
                    }
                    calls++;
                    String file = "Actions/" + category + "/" + id.replace('.', '-') + ".instance";
                    if (!registered.contains(file)) {
                        missing.add(p.getFileName() + ": " + category + " / " + id);
                    }
                }
            }
        }
        assertThat(calls).as("the census found the product's lookups").isGreaterThanOrEqualTo(11);
        assertThat(missing).as("action ids that answer null, so a fallback runs in silence").isEmpty();
    }

    private static String value(String expr, Map<String, String> constants) {
        expr = expr.strip();
        if (expr.startsWith("\"") && expr.endsWith("\"")) {
            return expr.substring(1, expr.length() - 1);
        }
        return constants.get(expr);
    }

    private static Set<String> registeredActions() throws Exception {
        Set<String> out = new HashSet<>();
        try (Stream<Path> jars = Files.walk(CLUSTER)) {
            for (Path jar : jars.filter(p -> p.toString().endsWith(".jar") && p.toString().contains("modules")).toList()) {
                try (JarFile jf = new JarFile(jar.toFile())) {
                    String declared = jf.getManifest() == null ? null
                            : jf.getManifest().getMainAttributes().getValue("OpenIDE-Module-Layer");
                    for (String name : new String[] {declared, "META-INF/generated-layer.xml"}) {
                        ZipEntry e = name == null ? null : jf.getEntry(name);
                        if (e != null) {
                            collect(parse(jf.getInputStream(e).readAllBytes()), "", out);
                        }
                    }
                }
            }
        }
        return out;
    }

    private static void collect(Element el, String path, Set<String> out) {
        for (Node n = el.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element c && ("folder".equals(c.getTagName()) || "file".equals(c.getTagName()))) {
                String p = path + c.getAttribute("name") + ("folder".equals(c.getTagName()) ? "/" : "");
                if ("file".equals(c.getTagName()) && p.startsWith("Actions/")) {
                    out.add(p);
                }
                collect(c, p, out);
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
