package org.nmox.studio.editor.conflicts;

import java.awt.Color;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyleConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.netbeans.editor.BaseDocument;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The watcher on a real document: it tints both sides of every block and
 * nothing else, clears when the conflict is resolved, never reads a
 * document past its bound, and is registered for every mime.
 */
class ConflictWatcherTest {

    private record Span(int start, int end, Color background) {
    }

    private static BaseDocument doc(String text) throws BadLocationException {
        BaseDocument d = new BaseDocument(false, "text/plain");
        d.insertString(0, text, null);
        return d;
    }

    private static List<Span> spans(ConflictWatcher w, int length) {
        List<Span> out = new ArrayList<>();
        HighlightsSequence seq = w.bag().getHighlights(0, length);
        while (seq.moveNext()) {
            AttributeSet a = seq.getAttributes();
            out.add(new Span(seq.getStartOffset(), seq.getEndOffset(),
                    (Color) a.getAttribute(StyleConstants.Background)));
        }
        return out;
    }

    @Test
    @DisplayName("ours (with its header) and theirs (with its trailer) are tinted; the divider and the rest are not")
    void tintsBothSides() throws Exception {
        String t = "top\n<<<<<<< HEAD\nmine\n=======\ntheirs\n>>>>>>> topic\nend\n";
        BaseDocument d = doc(t);
        ConflictWatcher w = ConflictWatcher.of(d);
        ConflictWatcher.awaitQuiet();
        List<Span> s = spans(w, d.getLength());
        assertThat(s).hasSize(2);
        assertThat(t.substring(s.get(0).start(), s.get(0).end())).isEqualTo("<<<<<<< HEAD\nmine\n");
        assertThat(t.substring(s.get(1).start(), s.get(1).end())).isEqualTo("theirs\n>>>>>>> topic\n");
        assertThat(s.get(0).background()).isNotNull().isNotEqualTo(s.get(1).background());
    }

    @Test
    @DisplayName("a diff3 base section wears the third tint")
    void baseTint() throws Exception {
        String t = "<<<<<<< HEAD\na\n||||||| base\nb\n=======\nc\n>>>>>>> x\n";
        BaseDocument d = doc(t);
        ConflictWatcher w = ConflictWatcher.of(d);
        ConflictWatcher.awaitQuiet();
        List<Span> s = spans(w, d.getLength());
        assertThat(s).hasSize(3);
        assertThat(t.substring(s.get(1).start(), s.get(1).end())).isEqualTo("||||||| base\nb\n");
    }

    @Test
    @DisplayName("resolving the conflict by hand clears the tints; one watcher per document")
    void clearsWhenResolved() throws Exception {
        String t = "<<<<<<< HEAD\na\n=======\nb\n>>>>>>> x\n";
        BaseDocument d = doc(t);
        ConflictWatcher w = ConflictWatcher.of(d);
        assertThat(ConflictWatcher.of(d)).isSameAs(w);
        ConflictWatcher.awaitQuiet();
        assertThat(spans(w, d.getLength())).isNotEmpty();
        d.remove(0, d.getLength());
        d.insertString(0, "a\n", null);
        w.scan(); // the lane would do this 250 ms later
        assertThat(spans(w, d.getLength())).isEmpty();
    }

    @Test
    @DisplayName("a document past the bound is never read, conflict or not")
    void boundedRead() throws Exception {
        StringBuilder big = new StringBuilder("<<<<<<< HEAD\na\n=======\nb\n>>>>>>> x\n");
        String pad = "x".repeat(1023) + "\n";
        while (big.length() <= ConflictWatcher.MAX_CHARS) {
            big.append(pad);
        }
        BaseDocument d = doc(big.toString());
        ConflictWatcher w = ConflictWatcher.of(d);
        ConflictWatcher.awaitQuiet();
        assertThat(spans(w, d.getLength())).isEmpty();
    }

    @Test
    @DisplayName("the layer factory is registered for every mime, in the generated layer")
    void registeredForEveryMime() throws Exception {
        var dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Document layer;
        try (InputStream in = ConflictWatcher.class.getResourceAsStream("/META-INF/generated-layer.xml")) {
            assertThat(in).isNotNull();
            layer = dbf.newDocumentBuilder().parse(in);
        }
        Element editors = null;
        var kids = layer.getDocumentElement().getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element el && "folder".equals(el.getTagName())
                    && "Editors".equals(el.getAttribute("name"))) {
                editors = el;
            }
        }
        assertThat(editors).as("an Editors folder").isNotNull();
        List<String> direct = new ArrayList<>();
        var rows = editors.getChildNodes();
        for (int i = 0; i < rows.getLength(); i++) {
            if (rows.item(i) instanceof Element el && "file".equals(el.getTagName())) {
                direct.add(el.getAttribute("name"));
            }
        }
        // directly under Editors/, not under Editors/<mime>/: every editor gets it
        assertThat(direct).contains("org-nmox-studio-editor-conflicts-ConflictWatcher$Factory.instance");
    }

    @Test
    @DisplayName("the tints are highlight colorings in the light and dark profiles, named in the bundle")
    void colorsRegisteredPerProfile() throws Exception {
        String layer;
        try (InputStream in = ConflictWatcher.class.getResourceAsStream("/org/nmox/studio/editor/layer.xml")) {
            layer = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertThat(layer.split("url=\"conflicts/merge-conflict-colors.xml\"", -1)).hasSize(4);
        String colors;
        try (InputStream in = ConflictWatcher.class.getResourceAsStream("merge-conflict-colors.xml")) {
            colors = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        for (String name : List.of(ConflictWatcher.CURRENT, ConflictWatcher.INCOMING, ConflictWatcher.BASE)) {
            assertThat(colors).contains("name=\"" + name + "\"");
            // the Options panel names a coloring by its key in the localizing bundle
            assertThat(org.openide.util.NbBundle.getMessage(ConflictWatcher.class, name)).startsWith("Merge Conflict: ");
        }
    }
}
