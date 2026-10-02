package org.nmox.studio.core.util;

import java.util.function.ToIntFunction;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A sentence cut to a width keeps its beginning and ends where a word
 * does (3.5.2). The measure in the pure tests is ten pixels a character, so
 * a room of 300 is thirty characters, ellipsis included.
 */
class FitLabelTest {

    private static final ToIntFunction<String> TEN_EACH = s -> s.codePointCount(0, s.length()) * 10;

    private static final String RACK = "devices, cables, pipelines — Tab flips it";

    @Test
    @DisplayName("text that fits is shown whole")
    void whole() {
        assertThat(FitLabel.fitEnd(RACK, TEN_EACH, 410)).isEqualTo(RACK);
        assertThat(FitLabel.fitEnd("", TEN_EACH, 10)).isEmpty();
        assertThat(FitLabel.fitEnd(null, TEN_EACH, 10)).isEmpty();
    }

    @Test
    @DisplayName("a sentence ends at the end of a word, not inside one")
    void endsOnAWord() {
        // the text is 41 characters; the 38-character cut of old gave "…Tab flip…"
        assertThat(FitLabel.fitEnd(RACK, TEN_EACH, 400)).isEqualTo("devices, cables, pipelines — Tab flips…");
        assertThat(FitLabel.fitEnd(RACK, TEN_EACH, 390)).as("38 characters and the ellipsis: exactly the room")
                .isEqualTo("devices, cables, pipelines — Tab flips…");
        assertThat(FitLabel.fitEnd(RACK, TEN_EACH, 380)).as("one short of a word: the word goes")
                .isEqualTo("devices, cables, pipelines — Tab…");
        assertThat(FitLabel.fitEnd(RACK, TEN_EACH, 200)).isEqualTo("devices, cables…");
    }

    @Test
    @DisplayName("separators left at the end are dropped: a comma or a dash before the ellipsis promises more")
    void noDanglingSeparator() {
        assertThat(FitLabel.fitEnd(RACK, TEN_EACH, 300)).isEqualTo("devices, cables, pipelines…");
        assertThat(FitLabel.fitEnd(RACK, TEN_EACH, 170)).isEqualTo("devices, cables…");
        assertThat(FitLabel.fitEnd("DigitalOcean · Hetzner · Cloudflare for DNS", TEN_EACH, 260))
                .isEqualTo("DigitalOcean · Hetzner…");
    }

    @Test
    @DisplayName("what is shown always fits, and is the longest word-ended beginning that does")
    void alwaysFits() {
        for (int room = 20; room <= 420; room += 10) {
            String shown = FitLabel.fitEnd(RACK, TEN_EACH, room);
            assertThat(TEN_EACH.applyAsInt(shown)).as("room " + room + ": " + shown).isLessThanOrEqualTo(room);
            assertThat(RACK).as("a beginning of the text").startsWith(shown.replace("…", ""));
        }
    }

    @Test
    @DisplayName("one long word, or a language written without spaces, is cut at a letter and not to nothing")
    void noWordEndNear() {
        assertThat(FitLabel.fitEnd("Donaudampfschifffahrtsgesellschaft", TEN_EACH, 120)).isEqualTo("Donaudampfs…");
        assertThat(FitLabel.fitEnd("设备线缆流水线按键翻转机架的背面", TEN_EACH, 90)).isEqualTo("设备线缆流水线按…");
        // a short first word and then a long one: the word end is far behind, so the letter wins
        assertThat(FitLabel.fitEnd("a Donaudampfschifffahrtsgesellschaft", TEN_EACH, 200))
                .isEqualTo("a Donaudampfschifff…");
    }

    @Test
    @DisplayName("a mark stays with its letter and a surrogate pair is never split")
    void marksAndPairs() {
        // क + ा: cutting between them would show a different syllable
        String hindi = "काकाकाका";
        String shown = FitLabel.fitEnd(hindi, TEN_EACH, 40);
        String kept = shown.replace("…", "");
        assertThat(kept.codePointCount(0, kept.length()) % 2).as(shown).isZero();

        String emoji = "🚀🚀🚀🚀🚀";
        String cut = FitLabel.fitEnd(emoji, TEN_EACH, 30);
        assertThat(cut).isEqualTo("🚀🚀…");
    }

    @Test
    @DisplayName("the label: whole with room, cut and on the tooltip without, and it says which")
    void theLabel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FitLabel label = new FitLabel(FitLabel.Cut.END, 200, true);
            label.setFull(RACK);
            assertThat(label.getPreferredSize().width).as("asks for no more than its cap").isLessThanOrEqualTo(200);
            assertThat(label.getMinimumSize().width).isZero();
            assertThat(label.getMaximumSize().width).as("takes what is left over").isGreaterThan(2000);
            assertThat(label.getAccessibleContext().getAccessibleDescription()).isEqualTo(RACK);

            resize(label, 2000);
            assertThat(label.isCut()).isFalse();
            assertThat(label.getText()).isEqualTo(RACK);
            assertThat(label.getToolTipText()).as("nothing cut, nothing to add").isNull();

            resize(label, 120);
            assertThat(label.isCut()).isTrue();
            assertThat(label.getText()).endsWith("…").startsWith("devices");
            assertThat(label.getToolTipText()).isEqualTo(RACK);
            assertThat(label.getFull()).isEqualTo(RACK);

            resize(label, 2000);
            assertThat(label.isCut()).as("room again").isFalse();
            assertThat(label.getToolTipText()).isNull();
        });
    }

    @Test
    @DisplayName("the same label can hold a list where a path was, and cuts it as a list")
    void anotherKind() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FitLabel label = new FitLabel(FitLabel.Cut.MIDDLE, 200, true);
            label.setFull("/Users/someone/code/a/deep/folder/of/projects");
            resize(label, 120);
            assertThat(label.getText()).as("a path keeps its ends").contains("…").doesNotEndWith("…");

            label.setFull("node · typescript · docker · make", FitLabel.Cut.END);
            assertThat(label.getText()).as("a list keeps its beginning").startsWith("node").endsWith("…");
            assertThat(label.getText()).as("and no half name").doesNotContain("typ…").doesNotContain("· …");
        });
    }

    @Test
    @DisplayName("text is text, never markup, cut or whole")
    void plain() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FitLabel label = new FitLabel(FitLabel.Cut.END, 200, false);
            label.setFull("<html><img src=http://x/> and a long tail of words that will not fit");
            assertThat(label.getText()).doesNotStartWith("<html");
            resize(label, 150);
            assertThat(label.getText()).doesNotStartWith("<html");
            assertThat(label.getToolTipText()).doesNotStartWith("<html");
        });
    }

    private static void resize(FitLabel label, int width) {
        label.setBounds(0, 0, width, 20);
        label.dispatchEvent(new java.awt.event.ComponentEvent(label, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
    }
}
