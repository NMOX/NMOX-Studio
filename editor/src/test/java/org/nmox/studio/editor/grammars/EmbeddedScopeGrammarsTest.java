package org.nmox.studio.editor.grammars;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TM4E resolves cross-grammar includes through a registry keyed by
 * scopeName, built from the generated layer. These tests read that
 * layer — the artifact the platform actually loads — so a vanished
 * registration or a scope/file mismatch fails here instead of as 191
 * silently pruned markdown rules at runtime.
 */
class EmbeddedScopeGrammarsTest {

    /** The scopes the platform markdown grammar includes that we must supply. */
    private static final Set<String> REQUIRED_EMBED_SCOPES = Set.of(
            "source.yaml", "source.js", "source.ts", "source.tsx",
            "text.html.derivative");

    @Test
    @DisplayName("The generated layer registers a grammar for every markdown-embedded scope")
    void embeddedScopesAreRegistered() throws Exception {
        Map<String, String> scopeToFile = registeredGrammars();

        assertThat(scopeToFile.keySet()).containsAll(REQUIRED_EMBED_SCOPES);
        // and they carry the embed-only synthetic mimes, not a real editor mime
        assertThat(scopeToFile.get("source.js")).contains("x-nmox-embed-js");
        assertThat(scopeToFile.get("source.yaml")).contains("x-nmox-embed-yaml");
    }

    @Test
    @DisplayName("Every registered grammar file exists and its scopeName matches its registration")
    void grammarFilesMatchTheirScopes() throws Exception {
        for (Map.Entry<String, String> entry : registeredGrammars().entrySet()) {
            String resource = entry.getValue().substring(entry.getValue().indexOf('|') + 1);
            try (InputStream in = EmbeddedScopeGrammars.class.getResourceAsStream(
                    "/" + resource)) {
                assertThat(in).as("grammar resource %s must exist", resource).isNotNull();
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Matcher scope = Pattern.compile("\"scopeName\"\\s*:\\s*\"([^\"]+)\"")
                        .matcher(json);
                assertThat(scope.find())
                        .as("grammar %s must declare a scopeName", resource).isTrue();
                assertThat(scope.group(1))
                        .as("scopeName inside %s must match its registration", resource)
                        .isEqualTo(entry.getKey());
            }
        }
    }

    /**
     * scopeName → "mimeFolderPath|resourcePath" for every file in the
     * generated layer carrying the textmate-grammar attribute.
     */
    private static Map<String, String> registeredGrammars() throws Exception {
        Map<String, String> result = new HashMap<>();
        try (InputStream layer = EmbeddedScopeGrammars.class
                .getResourceAsStream("/META-INF/generated-layer.xml")) {
            assertThat(layer).as("generated-layer.xml must exist").isNotNull();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            collect(dbf.newDocumentBuilder().parse(layer).getDocumentElement(), "", result);
        }
        return result;
    }

    private static void collect(Element element, String path, Map<String, String> result) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element el)) {
                continue;
            }
            if ("folder".equals(el.getTagName())) {
                collect(el, path + "/" + el.getAttribute("name"), result);
            } else if ("file".equals(el.getTagName())) {
                String scope = grammarScope(el);
                if (scope != null) {
                    String url = el.getAttribute("url").replace("nbresloc:/", "");
                    result.put(scope, path + "|" + url);
                }
            }
        }
    }

    private static String grammarScope(Element file) {
        NodeList attrs = file.getElementsByTagName("attr");
        for (int i = 0; i < attrs.getLength(); i++) {
            Element attr = (Element) attrs.item(i);
            if ("textmate-grammar".equals(attr.getAttribute("name"))) {
                return attr.getAttribute("stringvalue");
            }
        }
        return null;
    }

    @Test
    @DisplayName("Embed-only mimes never gain a real editor binding (no loader, no CSL)")
    void embedMimesStayEditorless() throws Exception {
        // the failure mode this pins: someone binds an editor to a
        // synthetic mime and files start opening with a broken kit
        Set<String> embedFolders = new HashSet<>();
        for (String value : registeredGrammars().values()) {
            String folder = value.substring(0, value.indexOf('|'));
            if (folder.contains("x-nmox-embed-")) {
                embedFolders.add(folder);
            }
        }
        assertThat(embedFolders).hasSize(5);
        try (InputStream layer = EmbeddedScopeGrammars.class
                .getResourceAsStream("/META-INF/generated-layer.xml")) {
            String xml = new String(layer.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(xml).doesNotContain("Loaders/text/x-nmox-embed-");
        }
    }
}
