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
            "text.html.derivative",
            // v1.195.1: the 1.195.0 smoke test's "No grammar source for
            // scope" pair — text.xml (http/ruby/php/perl heredoc embeds)
            // and source.js.jsx (vue/graphql embeds)
            "text.xml", "source.js.jsx",
            // v2.85.0: the scope STUBS — includes we ship no grammar for,
            // resolved to empty grammars so the log stops warning and the
            // including rules stop being pruned
            "source.x86_64", "source.x86", "source.asm", "source.arm", "source.sql",
            "source.sassdoc", "source.glsl", "source.stylus", "source.dockerfile",
            "source.batchfile", "source.diff",
            // the second batch (the boot proof's remaining 132 lines)
            "source.js.regexp", "source.js.jquery", "source.c++", "text.html.elixir", "text.elixir",
            "source.regexp.python", "source.postscript", "source.less", "source.cpp.embedded.macro",
            "text.xml.xsl", "text.tex.latex", "text.log", "text.git-rebase", "text.git-commit",
            "text.bibtex", "source.twig", "source.powershell", "source.perl.6", "source.objc",
            "source.json.comments", "source.go", "source.asp.vb.net", "source.css.postcss",
            "text.html.javadoc", "source.toml", "source.postcss", "source.openesql",
            "source.ocaml.ocamldoc", "source.ocaml.interface", "source.json5", "regexp");

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
    @DisplayName("every scope is registered by exactly one grammar — a stub must go the day a real grammar arrives for its scope (v2.85.0)")
    void scopesRegisteredOnce() throws Exception {
        java.util.Map<String, Integer> seen = new java.util.HashMap<>();
        try (InputStream layer = EmbeddedScopeGrammars.class.getResourceAsStream("/META-INF/generated-layer.xml")) {
            String xml = new String(layer.readAllBytes(), StandardCharsets.UTF_8);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("grammar\\.json\\.tmLanguage|stub-[A-Za-z0-9.+-]+\\.json|[A-Za-z0-9-]+\\.tmLanguage\\.json").matcher(xml);
            // scope → count, read from every registered grammar FILE's own scopeName
            for (String file : registeredGrammars().values().stream().map(v -> v.substring(v.indexOf('|') + 1)).toList()) {
                try (InputStream in = EmbeddedScopeGrammars.class.getClassLoader().getResourceAsStream(file)) {
                    if (in == null) {
                        continue;
                    }
                    java.util.regex.Matcher scope = Pattern.compile("\"scopeName\"\\s*:\\s*\"([^\"]+)\"")
                            .matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    if (scope.find()) {
                        seen.merge(scope.group(1), 1, Integer::sum);
                    }
                }
            }
        }
        assertThat(seen).as("scopes registered more than once").allSatisfy((scope, n) -> assertThat(n).as(scope).isEqualTo(1));
        assertThat(seen).containsKeys("source.go", "source.less", "source.js.regexp");
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
        // yaml, js, ts, tsx, html-derivative + the v1.195.1 pair
        // (xml for text.xml, jsx for source.js.jsx) + ng-expression
        // (v1.217.0: expression.ng is include-only — injecting it stomped
        // host HTML, so it rides the embed idiom like the others)
        // + the forty-two v2.85.0 scope stubs (11 + 31)
        assertThat(embedFolders).hasSize(50);
        try (InputStream layer = EmbeddedScopeGrammars.class
                .getResourceAsStream("/META-INF/generated-layer.xml")) {
            String xml = new String(layer.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(xml).doesNotContain("Loaders/text/x-nmox-embed-");
        }
    }
}
