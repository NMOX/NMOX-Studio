package org.nmox.studio.editor.grammars;

import java.util.List;
import javax.swing.text.PlainDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.Severity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The summary-line rule (3.2.0): only the first line, only past 72,
 * never a {@code #} line, counted in characters a reader sees.
 */
class GitSummaryLineTest {

    private static String repeat(char c, int n) {
        return String.valueOf(c).repeat(n);
    }

    @Test
    @DisplayName("72 characters is fine; 73 is over, and the span is the part past 72")
    void boundary() {
        assertThat(GitSummaryLine.overflow(repeat('a', 72))).isNull();
        GitSummaryLine.Overflow over = GitSummaryLine.overflow(repeat('a', 73) + "\n\nbody");
        assertThat(over).isNotNull();
        assertThat(over.length()).isEqualTo(73);
        assertThat(over.start()).isEqualTo(72);
        assertThat(over.end()).isEqualTo(73);
    }

    @Test
    @DisplayName("only the first line is judged — a long body line is the author's business")
    void onlyTheFirstLine() {
        assertThat(GitSummaryLine.overflow("Short summary\n\n" + repeat('b', 200))).isNull();
    }

    @Test
    @DisplayName("a first line that is git's # template is not a summary")
    void commentFirstLine() {
        assertThat(GitSummaryLine.overflow("# " + repeat('c', 200))).isNull();
    }

    @Test
    @DisplayName("an empty message has nothing to judge")
    void empty() {
        assertThat(GitSummaryLine.overflow("")).isNull();
        assertThat(GitSummaryLine.overflow(null)).isNull();
        assertThat(GitSummaryLine.overflow("\n# Please enter the commit message")).isNull();
    }

    @Test
    @DisplayName("a CRLF line ending is not a character of the summary")
    void crlf() {
        assertThat(GitSummaryLine.overflow(repeat('a', 72) + "\r\nbody")).isNull();
        GitSummaryLine.Overflow over = GitSummaryLine.overflow(repeat('a', 74) + "\r\n");
        assertThat(over.length()).isEqualTo(74);
        assertThat(over.end()).isEqualTo(74);
    }

    @Test
    @DisplayName("characters are counted as a reader sees them: an emoji is one, never cut in half")
    void codePoints() {
        String emoji = "🚀"; // one character, two UTF-16 units
        String line = emoji.repeat(72);
        assertThat(GitSummaryLine.overflow(line)).as("72 rockets are 72 characters").isNull();
        GitSummaryLine.Overflow over = GitSummaryLine.overflow(emoji.repeat(73));
        assertThat(over.length()).isEqualTo(73);
        assertThat(over.start()).as("the span starts on a character boundary").isEqualTo(144);
    }

    @Test
    @DisplayName("the hint is one WARNING over the overflow, and none when the summary fits")
    void hintDescribesTheOverflow() throws Exception {
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, repeat('x', 80) + "\n\nbody\n# git's template", null);
        List<ErrorDescription> hints = GitSummaryLineHint.describe(doc);
        assertThat(hints).hasSize(1);
        ErrorDescription d = hints.get(0);
        assertThat(d.getSeverity()).isEqualTo(Severity.WARNING);
        // (the span's offsets are GitSummaryLine's, pinned in boundary():
        // an ErrorDescription over a document with no file carries no
        // PositionBounds to read them back from)
        assertThat(d.getDescription()).contains("80");

        PlainDocument fits = new PlainDocument();
        fits.insertString(0, "Fix the parser\n", null);
        assertThat(GitSummaryLineHint.describe(fits)).isEmpty();
    }

    @Test
    @DisplayName("the sentence names the limit the rule enforces, so the two cannot drift")
    void sentenceNamesTheLimit() {
        assertThat(GitSummaryLineHint.message(90))
                .contains(Integer.toString(GitSummaryLine.LIMIT))
                .contains("90");
    }
}
