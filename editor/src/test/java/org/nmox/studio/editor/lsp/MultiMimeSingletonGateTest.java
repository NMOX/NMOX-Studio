package org.nmox.studio.editor.lsp;

import java.lang.reflect.Method;
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
 * Ledger 83's build laws, derived from the GENERATED layer so every
 * provider — present or future — is covered by what the annotation
 * processor actually emitted, with no hand-kept list to rot.
 *
 * <p>For ANY LanguageServerProvider registration that appears under two
 * or more {@code Editors/<mime>} folders, however it is expressed:
 *
 * <ul>
 * <li><b>Method, not class:</b> it must be a {@code methodvalue}
 * registration. The platform's server-reuse map is keyed by provider
 * INSTANCE and a class {@code .instance} is instantiated once per mime
 * folder — two instances, two servers, the exact duplicate the
 * v1.356.0 walk measured. (The first version of this gate checked only
 * classes that already implemented MultiMime — and missed CssServer's
 * three-mime class registration entirely. The gate now keys on the
 * OUTCOME, a multi-mime registration, not the mechanism.)</li>
 * <li><b>Singleton:</b> invoking the factory twice returns the same
 * object.</li>
 * <li><b>MultiMime with parity:</b> the provider implements
 * {@link MultiMimeLanguageServerProvider} and {@code getMimeTypes()}
 * equals the registered folder set — wider would claim mimes whose
 * lookups never see it, narrower silently re-splits the server.</li>
 * </ul>
 */
class MultiMimeSingletonGateTest {

    /** One registration file: its Editors mimes and its attrs. */
    private record Registration(Set<String> mimes, String methodValue,
            String instanceOf) {
    }

    private static Map<String, Registration> registrations() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        // the layer's DOCTYPE names the netbeans.org DTD; never fetch it
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Document doc = dbf.newDocumentBuilder()
                .parse(Path.of("target/classes/META-INF/generated-layer.xml").toFile());
        Map<String, Registration> byFile = new HashMap<>();
        collect(doc.getDocumentElement(), "", byFile);
        return byFile;
    }

    private static void collect(Element el, String path, Map<String, Registration> byFile) {
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
                String method = null;
                String instanceOf = null;
                NodeList attrs = child.getElementsByTagName("attr");
                for (int a = 0; a < attrs.getLength(); a++) {
                    Element attr = (Element) attrs.item(a);
                    if ("instanceCreate".equals(attr.getAttribute("name"))
                            && attr.hasAttribute("methodvalue")) {
                        method = attr.getAttribute("methodvalue");
                    }
                    if ("instanceOf".equals(attr.getAttribute("name"))) {
                        instanceOf = attr.getAttribute("stringvalue");
                    }
                }
                String name = child.getAttribute("name");
                Registration prev = byFile.get(name);
                Set<String> mimes = prev != null ? prev.mimes() : new HashSet<>();
                mimes.add(mime);
                byFile.put(name, new Registration(mimes,
                        method != null ? method : (prev != null ? prev.methodValue() : null),
                        instanceOf != null ? instanceOf : (prev != null ? prev.instanceOf() : null)));
            }
        }
    }

    /** The multi-mime LanguageServerProvider registrations, from the layer. */
    private static Map<String, Registration> multiMime() throws Exception {
        Map<String, Registration> all = registrations();
        Map<String, Registration> multi = new HashMap<>();
        for (Map.Entry<String, Registration> e : all.entrySet()) {
            if (e.getValue().mimes().size() >= 2
                    && "org.netbeans.modules.lsp.client.spi.LanguageServerProvider"
                            .equals(e.getValue().instanceOf())) {
                multi.put(e.getKey(), e.getValue());
            }
        }
        return multi;
    }

    private static Object invokeFactory(String methodValue) throws Exception {
        int dot = methodValue.lastIndexOf('.');
        Class<?> owner = Class.forName(methodValue.substring(0, dot));
        Method m = owner.getMethod(methodValue.substring(dot + 1));
        return m.invoke(null);
    }

    @Test
    @DisplayName("every multi-mime registration is a MultiMime singleton with parity")
    void multiMimeRegistrationsAreLawful() throws Exception {
        Map<String, Registration> multi = multiMime();
        // floor: an empty parse must not fake green (the count-gate law) —
        // the six converted providers plus the css server are the minimum
        assertThat(multi.size())
                .as("the layer parse found the known multi-mime registrations")
                .isGreaterThanOrEqualTo(7);
        for (Map.Entry<String, Registration> e : multi.entrySet()) {
            Registration reg = e.getValue();
            assertThat(reg.methodValue())
                    .as(e.getKey() + ": a multi-mime provider must register through a "
                            + "factory METHOD — a class .instance is instantiated once "
                            + "per mime folder, so the platform's instance-keyed reuse "
                            + "misses and a second server starts")
                    .isNotNull();
            Object first = invokeFactory(reg.methodValue());
            Object second = invokeFactory(reg.methodValue());
            assertThat(second)
                    .as(e.getKey() + ": the factory must return a singleton")
                    .isSameAs(first);
            assertThat(first)
                    .as(e.getKey() + ": a provider on several mimes must declare them "
                            + "via MultiMimeLanguageServerProvider or each mime starts "
                            + "its own server")
                    .isInstanceOf(MultiMimeLanguageServerProvider.class);
            assertThat(((MultiMimeLanguageServerProvider) first).getMimeTypes())
                    .as(e.getKey() + ": getMimeTypes() == the registered mime folders")
                    .isEqualTo(reg.mimes());
        }
    }
}
