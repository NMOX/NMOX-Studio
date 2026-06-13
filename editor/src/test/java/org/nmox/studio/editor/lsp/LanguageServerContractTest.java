package org.nmox.studio.editor.lsp;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every language we ship a grammar for must also register a language
 * server provider. The check reads META-INF/generated-layer.xml - the
 * artifact the annotation processor actually produces and the platform
 * actually reads - so it fails both when a language is forgotten AND
 * when annotation processing silently stops running (the JDK 23
 * -proc:none regression that has bitten this project before).
 */
class LanguageServerContractTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "text/javascript", "text/typescript",
        "text/x-c", "text/x-cpp", "text/x-java", "text/x-csharp", "text/x-fsharp",
        "text/x-python", "text/x-ruby", "text/x-rust", "text/x-go", "text/x-php5",
        "text/x-dart", "text/x-scala", "text/x-kotlin", "text/x-swift",
        "text/x-haskell", "text/x-zig", "text/x-erlang", "text/x-elixir",
        "text/x-clojure", "text/x-lisp", "text/x-lua", "text/x-ocaml",
        "text/x-crystal", "text/x-julia", "text/x-r", "text/x-perl",
        "text/x-groovy", "text/sh", "text/x-json",
        // the config layer
        "text/x-yaml", "text/x-toml", "text/x-dockerfile", "text/x-graphql",
        "text/x-vue", "text/x-svelte", "text/x-astro", "text/x-prisma"
    })
    @DisplayName("Grammar language has a registered LanguageServerProvider")
    void languageServerRegistered(String mime) throws Exception {
        assertThat(mimeHasServerRegistration(mime))
                .as("generated-layer.xml registers a LanguageServers provider under Editors/" + mime)
                .isTrue();
    }

    private static boolean mimeHasServerRegistration(String mime) throws Exception {
        Enumeration<URL> layers = LanguageServerContractTest.class.getClassLoader()
                .getResources("META-INF/generated-layer.xml");
        for (URL url : Collections.list(layers)) {
            try (InputStream in = url.openStream()) {
                Element editors = childFolder(parse(in).getDocumentElement(), "Editors");
                Element mimeFolder = editors;
                for (String part : mime.split("/")) {
                    mimeFolder = mimeFolder == null ? null : childFolder(mimeFolder, part);
                }
                if (mimeFolder != null && hasServerFile(mimeFolder)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Document parse(InputStream in) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // the layer references the NetBeans filesystem DTD; never fetch it
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        return factory.newDocumentBuilder().parse(in);
    }

    private static Element childFolder(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element el && "folder".equals(el.getTagName())
                    && name.equals(el.getAttribute("name"))) {
                return el;
            }
        }
        return null;
    }

    /** A .instance file naming one of our LanguageServers inner classes. */
    private static boolean hasServerFile(Element mimeFolder) {
        NodeList files = mimeFolder.getElementsByTagName("file");
        for (int i = 0; i < files.getLength(); i++) {
            Element file = (Element) files.item(i);
            if (file.getAttribute("name").contains("LanguageServers")) {
                return true;
            }
        }
        return false;
    }
}
