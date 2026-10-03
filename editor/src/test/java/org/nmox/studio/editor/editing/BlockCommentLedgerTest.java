package org.nmox.studio.editor.editing;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.polyglot.LanguageComments;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every language the editor serves has been asked whether it has a block
 * comment. Toggle Block Comment is one action for every editor, so its
 * delimiter table is the only place a language can be missing from: a
 * grammar added next month would get "This language has no block comment"
 * without anybody having looked. This ledger makes that a decision.
 *
 * <p>The population is derived, not listed: every {@code Editors/text/…}
 * folder in the module's generated layer and its hand-written layer (a
 * mime the product registers any editor feature for: a grammar, a
 * completion provider, a language server), plus every mime the line
 * comment table knows. Each must be in {@link BlockComments}' table of
 * pairs or in its written list of languages that have none. The
 * embedded-grammar pseudo mimes ({@code x-nmox-embed-…}) and the
 * {@code textmate} colouring folder are not files anybody opens.
 */
class BlockCommentLedgerTest {

    private static final Path GENERATED = Path.of("target/classes/META-INF/generated-layer.xml");
    private static final Path HAND = Path.of("src/main/resources/org/nmox/studio/editor/layer.xml");

    /** The mimes of the {@code Editors/text} folders of a layer file. */
    private static Set<String> editorMimes(Path layer) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Element root = f.newDocumentBuilder().parse(layer.toFile()).getDocumentElement();
        Set<String> out = new TreeSet<>();
        for (Element editors : folders(root, "Editors")) {
            for (Element text : folders(editors, "text")) {
                for (Element mime : folders(text, null)) {
                    out.add("text/" + mime.getAttribute("name"));
                }
            }
        }
        return out;
    }

    /** The child folders of {@code parent} called {@code name}, or all of them when it is null. */
    private static java.util.List<Element> folders(Element parent, String name) {
        java.util.List<Element> out = new java.util.ArrayList<>();
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n instanceof Element e && e.getTagName().equals("folder")
                    && (name == null || name.equals(e.getAttribute("name")))) {
                out.add(e);
            }
        }
        return out;
    }

    private static Set<String> population() throws Exception {
        Set<String> mimes = new TreeSet<>(editorMimes(GENERATED));
        mimes.addAll(editorMimes(HAND));
        mimes.addAll(LanguageComments.mimes());
        mimes.removeIf(m -> m.startsWith("text/x-nmox-embed-") || m.equals("text/textmate"));
        return mimes;
    }

    @Test
    @DisplayName("every language the editor serves has a block-comment pair or is written down as having none")
    void everyEditorMimeIsDecided() throws Exception {
        Set<String> population = population();
        assertThat(population).as("the editor mimes were read from the layers").hasSizeGreaterThan(80);
        Set<String> undecided = new TreeSet<>(population);
        undecided.removeAll(BlockComments.styled());
        undecided.removeAll(BlockComments.NONE);
        assertThat(undecided)
                .as("languages nobody has decided: give each its pair in BlockComments.STYLES, "
                        + "or add it to BlockComments.NONE beside the reason it has no block comment")
                .isEmpty();
    }

    @Test
    @DisplayName("no language is in both lists, and a pair is two delimiters")
    void theTwoListsAreDisjointAndWellFormed() {
        Set<String> both = new TreeSet<>(BlockComments.styled());
        both.retainAll(BlockComments.NONE);
        assertThat(both).as("a language cannot both have a block comment and have none").isEmpty();
        for (String mime : BlockComments.styled()) {
            BlockComments.Style style = BlockComments.styleFor(mime);
            assertThat(style.open()).as(mime + " open").isNotBlank();
            assertThat(style.close()).as(mime + " close").isNotBlank();
        }
    }

    @Test
    @DisplayName("the markup that holds script and style blocks is markup in the table")
    void embeddingMimesAreMarkup() {
        for (String mime : BlockComments.EMBEDDING) {
            assertThat(BlockComments.styleFor(mime)).as(mime).isEqualTo(BlockComments.styleFor("text/html"));
        }
    }

    @Test
    @DisplayName("the census reads real folders: a control mime it must find, and one it must not")
    void theCensusSeesWhatItShould() throws Exception {
        Set<String> population = population();
        assertThat(population).contains("text/typescript", "text/x-python", "text/x-vue", "text/x-sql");
        assertThat(population).noneMatch(m -> m.contains("nmox-embed"));
    }
}
