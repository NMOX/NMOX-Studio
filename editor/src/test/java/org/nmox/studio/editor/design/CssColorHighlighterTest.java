package org.nmox.studio.editor.design;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.text.AttributeSet;
import javax.swing.text.PlainDocument;
import javax.swing.text.StyleConstants;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;

/**
 * The swatch layer's bag contents (v1.229.0, added while widening the
 * testable surface): a recompute paints every literal with its own
 * color as the Background and the luminance-chosen readable text as
 * the Foreground, at the literal's exact offsets — the part of the
 * highlighter that is pure data, no editor pane needed.
 */
class CssColorHighlighterTest {

    private record Painted(int start, int end, Color background, Color foreground) {
    }

    private static List<Painted> paint(String css) throws Exception {
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, css, null);
        OffsetsBag bag = new OffsetsBag(doc);
        CssColorHighlighter highlighter = new CssColorHighlighter(doc, bag);
        CssColorHighlighter.awaitQuiet(); // the ctor's scheduled recompute must not race ours
        highlighter.recompute();
        List<Painted> out = new ArrayList<>();
        HighlightsSequence seq = bag.getHighlights(0, doc.getLength());
        while (seq.moveNext()) {
            AttributeSet attrs = seq.getAttributes();
            out.add(new Painted(seq.getStartOffset(), seq.getEndOffset(),
                    (Color) attrs.getAttribute(StyleConstants.Background),
                    (Color) attrs.getAttribute(StyleConstants.Foreground)));
        }
        return out;
    }

    @Test
    @DisplayName("each literal is painted AS its color, with readable text on top")
    void literalsPainted() throws Exception {
        String css = "a{color:#336699; background: tomato}";
        List<Painted> painted = paint(css);
        assertThat(painted).hasSize(2);

        Painted hex = painted.get(0);
        assertThat(hex.start()).isEqualTo(css.indexOf("#336699"));
        assertThat(hex.background()).isEqualTo(new Color(0x33, 0x66, 0x99));
        assertThat(hex.foreground()).isEqualTo(Color.WHITE); // dark blue → white text

        Painted named = painted.get(1);
        assertThat(named.start()).isEqualTo(css.indexOf("tomato"));
        assertThat(named.background()).isEqualTo(new Color(0xFF6347));
        assertThat(named.foreground()).isEqualTo(Color.WHITE); // tomato's luminance ≈ 0.31 — dark side of the cut
    }

    @Test
    @DisplayName("alpha literals paint the opaque color — no blending in a text run")
    void alphaPaintsOpaque() throws Exception {
        List<Painted> painted = paint("a{c:#33669980}");
        assertThat(painted).hasSize(1);
        assertThat(painted.get(0).background()).isEqualTo(new Color(0x33, 0x66, 0x99));
    }

    @Test
    @DisplayName("an edit reschedules without throwing; a comment-only sheet paints nothing")
    void edgeBehaviour() throws Exception {
        assertThat(paint("/* red #fff */ a{}")).isEmpty();
        PlainDocument doc = new PlainDocument();
        OffsetsBag bag = new OffsetsBag(doc);
        CssColorHighlighter highlighter = new CssColorHighlighter(doc, bag);
        doc.insertString(0, "a{color:red}", null); // fires insertUpdate → schedule
        CssColorHighlighter.awaitQuiet();
        highlighter.recompute();
        HighlightsSequence seq = bag.getHighlights(0, doc.getLength());
        assertThat(seq.moveNext()).isTrue();
    }
}
