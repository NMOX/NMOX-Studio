package org.nmox.studio.editor.lsp;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.netbeans.modules.lsp.client.spi.MultiMimeLanguageServerProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 83's two build laws, pinned against the GENERATED layer so the
 * annotation processor's real output is what's checked, not the source.
 *
 * <p><b>Parity:</b> a {@link MultiMimeLanguageServerProvider}'s
 * {@code getMimeTypes()} must equal the set of Editors/&lt;mime&gt;
 * folders its factory is registered under. The platform files a started
 * server under every mime the provider DECLARES; a declaration wider
 * than the registrations would claim mimes whose lookups never see this
 * provider, and a narrower one silently re-splits the server per mime —
 * the duplicate ledger 83 measured.
 *
 * <p><b>Identity:</b> every multi-mime registration must be a METHOD
 * registration ({@code methodvalue}) resolving to a singleton, never a
 * class {@code .instance}. The platform's reuse map is keyed by provider
 * INSTANCE, and a class registration is instantiated once per mime
 * folder — two instances, two servers, the bug back by construction.
 */
class MultiMimeSingletonGateTest {

    /** factory simple name → the provider it must return. */
    private static final Map<String, MultiMimeLanguageServerProvider> FACTORIES = Map.of(
            "typeScriptServer", new LanguageServers.TypeScriptServer(),
            "denoServer", new LanguageServers.DenoServer(),
            "eslintServer", new LanguageServers.EslintServer(),
            "stylelintServer", new LanguageServers.StylelintServer(),
            "angularServer", new LanguageServers.AngularServer(),
            "clangdServer", new LanguageServers.ClangdServer());

    /** Editors/<mime> folders per registered file name, from the real layer. */
    private static Map<String, Set<String>> registrationsByFile() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        // the layer's DOCTYPE names the netbeans.org DTD; never fetch it
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Document doc = dbf.newDocumentBuilder()
                .parse(Path.of("target/classes/META-INF/generated-layer.xml").toFile());
        Map<String, Set<String>> byFile = new HashMap<>();
        collect(doc.getDocumentElement(), "", byFile);
        return byFile;
    }

    private static void collect(Element el, String path, Map<String, Set<String>> byFile) {
        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element child)) {
                continue;
            }
            if ("folder".equals(child.getTagName())) {
                collect(child, path.isEmpty()
                        ? child.getAttribute("name")
                        : path + "/" + child.getAttribute("name"), byFile);
            } else if ("file".equals(child.getTagName()) && path.startsWith("Editors/")) {
                String mime = path.substring("Editors/".length());
                byFile.computeIfAbsent(child.getAttribute("name"), f -> new HashSet<>())
                        .add(mime);
            }
        }
    }

    @Test
    @DisplayName("getMimeTypes() equals the layer registrations, per provider")
    void mimeParity() throws Exception {
        Map<String, Set<String>> byFile = registrationsByFile();
        for (Map.Entry<String, MultiMimeLanguageServerProvider> e : FACTORIES.entrySet()) {
            String file = "org-nmox-studio-editor-lsp-LanguageServers-"
                    + e.getKey() + ".instance";
            assertThat(byFile.get(file))
                    .as(e.getKey() + ": registered mimes == getMimeTypes()")
                    .isEqualTo(e.getValue().getMimeTypes());
        }
    }

    @Test
    @DisplayName("multi-mime providers register through methods, never as class instances")
    void noClassInstanceBackdoor() throws Exception {
        Map<String, Set<String>> byFile = registrationsByFile();
        for (String file : byFile.keySet()) {
            for (Class<?> inner : LanguageServers.class.getDeclaredClasses()) {
                if (MultiMimeLanguageServerProvider.class.isAssignableFrom(inner)) {
                    assertThat(file)
                            .as("a class .instance for " + inner.getSimpleName()
                                    + " would create one instance PER mime folder")
                            .isNotEqualTo("org-nmox-studio-editor-lsp-LanguageServers$"
                                    + inner.getSimpleName() + ".instance");
                }
            }
        }
    }

    @Test
    @DisplayName("each factory returns the same instance every call")
    void factoriesAreSingletons() {
        assertThat(LanguageServers.typeScriptServer())
                .isSameAs(LanguageServers.typeScriptServer());
        assertThat(LanguageServers.denoServer())
                .isSameAs(LanguageServers.denoServer());
        assertThat(LanguageServers.eslintServer())
                .isSameAs(LanguageServers.eslintServer());
        assertThat(LanguageServers.stylelintServer())
                .isSameAs(LanguageServers.stylelintServer());
        assertThat(LanguageServers.angularServer())
                .isSameAs(LanguageServers.angularServer());
        assertThat(LanguageServers.clangdServer())
                .isSameAs(LanguageServers.clangdServer());
    }

    @Test
    @DisplayName("the factories return providers of the classes they promise")
    void factoriesReturnTheirProviders() {
        assertThat(LanguageServers.typeScriptServer())
                .isInstanceOf(LanguageServers.TypeScriptServer.class);
        assertThat(LanguageServers.denoServer())
                .isInstanceOf(LanguageServers.DenoServer.class);
        assertThat(LanguageServers.eslintServer())
                .isInstanceOf(LanguageServers.EslintServer.class);
        assertThat(LanguageServers.stylelintServer())
                .isInstanceOf(LanguageServers.StylelintServer.class);
        assertThat(LanguageServers.angularServer())
                .isInstanceOf(LanguageServers.AngularServer.class);
        assertThat(LanguageServers.clangdServer())
                .isInstanceOf(LanguageServers.ClangdServer.class);
    }
}
