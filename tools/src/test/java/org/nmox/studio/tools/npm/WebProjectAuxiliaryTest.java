package org.nmox.studio.tools.npm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.spi.project.AuxiliaryConfiguration;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the platform remembers about a WebProject is kept under names the
 * platform's folder ordering does not read (3.5.2). The laws: a fragment
 * comes back as it went in; no attribute this class writes holds a slash,
 * whatever the namespace; shared and private never answer for each other;
 * stored text that is not the asked-for element is no answer; and through
 * the platform's own facade the old slashed attribute is read once and
 * removed by the next write, which is the migration this class relies on
 * and does not perform itself.
 */
class WebProjectAuxiliaryTest {

    private static final String BOOKMARKS_NS = "http://www.netbeans.org/ns/editor-bookmarks/2";
    private static final String LEGACY = "org.netbeans.spi.project.AuxiliaryConfiguration.";

    private FileObject dir;
    private WebProjectAuxiliary aux;

    @BeforeEach
    void project() throws IOException {
        dir = FileUtil.createMemoryFileSystem().getRoot().createFolder("shop");
        aux = new WebProjectAuxiliary(dir);
    }

    private static Element fragment(String name, String namespace, String id) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc = factory.newDocumentBuilder().newDocument();
        Element e = doc.createElementNS(namespace, name);
        e.setAttribute("lastBookmarkId", id);
        Element file = doc.createElementNS(namespace, "file");
        file.setTextContent("src/app.js <&> שלום");
        e.appendChild(file);
        return e;
    }

    /** The attributes that hold something: the memory filesystem keeps a cleared name with a null value. */
    private List<String> attributeNames() {
        List<String> names = new ArrayList<>(Collections.list(dir.getAttributes()));
        names.removeIf(name -> dir.getAttribute(name) == null);
        return names;
    }

    @Test
    @DisplayName("a fragment comes back as it went in")
    void roundTrip() throws Exception {
        aux.putConfigurationFragment(fragment("editor-bookmarks", BOOKMARKS_NS, "7"), false);

        Element back = aux.getConfigurationFragment("editor-bookmarks", BOOKMARKS_NS, false);

        assertThat(back).isNotNull();
        assertThat(back.getLocalName()).isEqualTo("editor-bookmarks");
        assertThat(back.getNamespaceURI()).isEqualTo(BOOKMARKS_NS);
        assertThat(back.getAttribute("lastBookmarkId")).isEqualTo("7");
        assertThat(back.getTextContent()).isEqualTo("src/app.js <&> שלום");
    }

    @Test
    @DisplayName("no attribute it writes holds a slash, so folder ordering never reads one")
    void namesHoldNoSlash() throws Exception {
        aux.putConfigurationFragment(fragment("editor-bookmarks", BOOKMARKS_NS, "1"), false);
        aux.putConfigurationFragment(fragment("open-files", "http://www.netbeans.org/ns/projectui-open-files/2", "1"), false);
        aux.putConfigurationFragment(fragment("odd.name", "urn:x/y#z?q=1 2/", "1"), true);

        assertThat(attributeNames()).hasSize(3).allSatisfy(name -> assertThat(name).doesNotContain("/"));
    }

    @Test
    @DisplayName("shared and private fragments never answer for each other")
    void sharedAndPrivateAreApart() throws Exception {
        aux.putConfigurationFragment(fragment("editor-bookmarks", BOOKMARKS_NS, "private"), false);

        assertThat(aux.getConfigurationFragment("editor-bookmarks", BOOKMARKS_NS, true)).isNull();

        aux.putConfigurationFragment(fragment("editor-bookmarks", BOOKMARKS_NS, "shared"), true);
        assertThat(aux.getConfigurationFragment("editor-bookmarks", BOOKMARKS_NS, false).getAttribute("lastBookmarkId"))
                .isEqualTo("private");
        assertThat(aux.getConfigurationFragment("editor-bookmarks", BOOKMARKS_NS, true).getAttribute("lastBookmarkId"))
                .isEqualTo("shared");
    }

    @Test
    @DisplayName("a second write replaces the first, and a removed fragment is gone")
    void replaceAndRemove() throws Exception {
        aux.putConfigurationFragment(fragment("editor-bookmarks", BOOKMARKS_NS, "1"), false);
        aux.putConfigurationFragment(fragment("editor-bookmarks", BOOKMARKS_NS, "2"), false);
        assertThat(aux.getConfigurationFragment("editor-bookmarks", BOOKMARKS_NS, false).getAttribute("lastBookmarkId"))
                .isEqualTo("2");
        assertThat(attributeNames()).hasSize(1);

        assertThat(aux.removeConfigurationFragment("editor-bookmarks", BOOKMARKS_NS, false)).isTrue();
        assertThat(aux.getConfigurationFragment("editor-bookmarks", BOOKMARKS_NS, false)).isNull();
        assertThat(aux.removeConfigurationFragment("editor-bookmarks", BOOKMARKS_NS, false)).isFalse();
        assertThat(attributeNames()).isEmpty();
    }

    @Test
    @DisplayName("stored text that is not the element asked for is no answer")
    void strangeStoredTextIsNoAnswer() throws Exception {
        String attribute = WebProjectAuxiliary.attributeName("editor-bookmarks", BOOKMARKS_NS, false);

        dir.setAttribute(attribute, "not xml at all <");
        assertThat(aux.getConfigurationFragment("editor-bookmarks", BOOKMARKS_NS, false)).isNull();

        dir.setAttribute(attribute, "<other xmlns=\"" + BOOKMARKS_NS + "\"/>");
        assertThat(aux.getConfigurationFragment("editor-bookmarks", BOOKMARKS_NS, false)).isNull();

        dir.setAttribute(attribute, "<editor-bookmarks xmlns=\"urn:elsewhere\"/>");
        assertThat(aux.getConfigurationFragment("editor-bookmarks", BOOKMARKS_NS, false)).isNull();

        dir.setAttribute(attribute, "<!DOCTYPE editor-bookmarks [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<editor-bookmarks xmlns=\"" + BOOKMARKS_NS + "\">&x;</editor-bookmarks>");
        assertThat(aux.getConfigurationFragment("editor-bookmarks", BOOKMARKS_NS, false)).isNull();

        dir.setAttribute(attribute, Boolean.TRUE);
        assertThat(aux.getConfigurationFragment("editor-bookmarks", BOOKMARKS_NS, false)).isNull();

        dir.setAttribute(attribute, "<editor-bookmarks xmlns=\"" + BOOKMARKS_NS + "\">"
                + "x".repeat(WebProjectAuxiliary.MAX_FRAGMENT_CHARS) + "</editor-bookmarks>");
        assertThat(aux.getConfigurationFragment("editor-bookmarks", BOOKMARKS_NS, false)).isNull();
    }

    @Test
    @DisplayName("a fragment with no namespace is refused, as the interface says")
    void namespaceIsRequired() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element bare = doc.createElement("bare");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> aux.putConfigurationFragment(bare, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(attributeNames()).isEmpty();
    }

    @Test
    @DisplayName("through the platform's facade: the old slashed attribute is read once, then removed by the next write")
    void thePlatformMovesTheOldAttribute() throws Exception {
        WebProject project = new WebProject(dir, new ProjectState() {
            @Override
            public void markModified() {
            }

            @Override
            public void notifyDeleted() {
            }
        });
        assertThat(project.getLookup().lookup(AuxiliaryConfiguration.class))
                .as("the project answers AuxiliaryConfiguration itself").isInstanceOf(WebProjectAuxiliary.class);
        // what an install from before 3.5.2 left behind
        String legacy = LEGACY + BOOKMARKS_NS + "#editor-bookmarks";
        dir.setAttribute(legacy, "<editor-bookmarks xmlns=\"" + BOOKMARKS_NS + "\" lastBookmarkId=\"41\"/>");
        AuxiliaryConfiguration facade = ProjectUtils.getAuxiliaryConfiguration(project);

        assertThat(facade.getConfigurationFragment("editor-bookmarks", BOOKMARKS_NS, false).getAttribute("lastBookmarkId"))
                .as("the old record is still read").isEqualTo("41");

        facade.putConfigurationFragment(fragment("editor-bookmarks", BOOKMARKS_NS, "42"), false);

        assertThat(attributeNames()).as("only the new name remains")
                .containsExactly(WebProjectAuxiliary.attributeName("editor-bookmarks", BOOKMARKS_NS, false));
        assertThat(facade.getConfigurationFragment("editor-bookmarks", BOOKMARKS_NS, false).getAttribute("lastBookmarkId"))
                .isEqualTo("42");
    }
}
