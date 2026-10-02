package org.nmox.studio.tools.npm;

import java.io.IOException;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.netbeans.spi.project.AuxiliaryConfiguration;
import org.openide.filesystems.FileObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Where the platform's own features keep what they remember about a
 * WebProject: the editor's bookmarks, the files that were open when the
 * project closed (3.5.2).
 *
 * <p><b>Why the project answers this itself.</b> A project that offers no
 * {@link AuxiliaryConfiguration} gets the platform's fallback, read from
 * the RELEASE310 bytecode ({@code AuxiliaryConfigImpl}): a private
 * fragment becomes an attribute of the project directory named
 * {@code org.netbeans.spi.project.AuxiliaryConfiguration.<namespace>#<element>}
 * — and every namespace is a URL. The platform's folder ordering
 * ({@code Ordering.getOrder}) reads ANY attribute whose name holds a slash
 * as a relative-ordering instruction and warns when its value is not a
 * boolean, so each listing of a project folder that carried a bookmark or
 * an open-files record wrote a WARNING quoting the whole fragment: seven
 * per session in the walk, on all three systems, in every log a person
 * attaches to a report.
 *
 * <p><b>The same store, under a name ordering does not read.</b> The
 * fragment stays an attribute of the project directory, which the platform
 * keeps in the user directory and never in the project, exactly as the
 * fallback did. Only the name changes: the namespace is percent-encoded,
 * so it holds no slash. Nothing is migrated by hand, because the platform
 * does it: a read this class cannot answer falls through to the old
 * attribute, and the next write here removes the old one.
 *
 * <p><b>Shared fragments stay on this machine, and that is said.</b> The
 * fallback would write a "shared" fragment into {@code .netbeans.xml}
 * inside the project. A project that answers this interface is handed
 * those too, and the platform then deletes its own copy, so that file
 * cannot be the home. They are kept beside the private ones under their
 * own name, so the two never answer for each other, and the first one
 * written is logged. No {@code .netbeans.xml} has been found in any
 * project this product opened, so no feature in the shipped set is known
 * to write one.
 */
final class WebProjectAuxiliary implements AuxiliaryConfiguration {

    private static final Logger LOG = Logger.getLogger(WebProjectAuxiliary.class.getName());

    /** Attribute-name heads; neither holds a slash, and neither does what follows. */
    static final String PRIVATE = "nmox.aux.private.";
    static final String SHARED = "nmox.aux.shared.";

    /** A fragment is a few hundred characters; one far past that is not ours to parse. */
    static final int MAX_FRAGMENT_CHARS = 1 << 20;

    private final FileObject projectDir;
    private boolean sharedSaid;

    WebProjectAuxiliary(FileObject projectDir) {
        this.projectDir = projectDir;
    }

    /** The attribute a fragment is kept under: no slash, whatever the namespace. */
    static String attributeName(String elementName, String namespace, boolean shared) {
        return (shared ? SHARED : PRIVATE)
                + URLEncoder.encode(namespace, StandardCharsets.UTF_8) + "#"
                + URLEncoder.encode(elementName, StandardCharsets.UTF_8);
    }

    @Override
    public Element getConfigurationFragment(String elementName, String namespace, boolean shared) {
        Object stored = projectDir.getAttribute(attributeName(elementName, namespace, shared));
        if (!(stored instanceof String text) || text.length() > MAX_FRAGMENT_CHARS) {
            return null;
        }
        Element root = parse(text);
        // an attribute holding some other element is not an answer to this question
        return root != null && elementName.equals(root.getLocalName())
                && namespace.equals(root.getNamespaceURI()) ? root : null;
    }

    @Override
    public void putConfigurationFragment(Element fragment, boolean shared) throws IllegalArgumentException {
        String name = fragment.getLocalName();
        String namespace = fragment.getNamespaceURI();
        if (name == null || namespace == null) {
            throw new IllegalArgumentException("a configuration fragment needs a name and a namespace");
        }
        try {
            projectDir.setAttribute(attributeName(name, namespace, shared), serialize(fragment));
            if (shared && !sharedSaid) {
                sharedSaid = true;
                LOG.log(Level.INFO, "{0}: the shared setting <{1}> is kept on this machine, not in the project",
                        new Object[] {projectDir.getNameExt(), name});
            }
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "could not remember <" + name + "> for " + projectDir.getNameExt(), ex);
        }
    }

    @Override
    public boolean removeConfigurationFragment(String elementName, String namespace, boolean shared)
            throws IllegalArgumentException {
        String attribute = attributeName(elementName, namespace, shared);
        if (projectDir.getAttribute(attribute) == null) {
            return false;
        }
        try {
            projectDir.setAttribute(attribute, null);
            return true;
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "could not forget <" + elementName + "> for " + projectDir.getNameExt(), ex);
            return false;
        }
    }

    /** The fragment as text, without a declaration, as the platform's fallback writes it. */
    static String serialize(Element fragment) {
        Document doc = builder().newDocument();
        doc.appendChild(doc.importNode(fragment, true));
        DOMImplementationLS ls = (DOMImplementationLS) doc.getImplementation().getFeature("LS", "3.0");
        LSSerializer serializer = ls.createLSSerializer();
        serializer.getDomConfig().setParameter("xml-declaration", Boolean.FALSE);
        return serializer.writeToString(doc);
    }

    /** The stored text as an element, or null when it is not one; nothing outside the text is ever fetched. */
    static Element parse(String text) {
        try {
            DocumentBuilder builder = builder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            builder.setErrorHandler(null);
            return builder.parse(new InputSource(new StringReader(text))).getDocumentElement();
        } catch (SAXException | IOException ex) {
            LOG.log(Level.FINE, "a stored fragment did not parse", ex);
            return null;
        }
    }

    private static DocumentBuilder builder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder();
        } catch (ParserConfigurationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
