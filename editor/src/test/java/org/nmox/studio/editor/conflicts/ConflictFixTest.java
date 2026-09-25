package org.nmox.studio.editor.conflicts;

import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.undo.UndoManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.netbeans.editor.BaseDocument;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.Severity;
import org.nmox.studio.editor.conflicts.MergeConflicts.Resolution;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fixes on a real editor document: each is ONE undoable edit, in VS
 * Code's order, and a block that changed after it was offered is left
 * alone rather than guessed at.
 */
class ConflictFixTest {

    private static final String TEXT = "top\n<<<<<<< HEAD\nmine\n=======\ntheirs\n>>>>>>> topic\nend\n";

    private static BaseDocument doc(String text) throws BadLocationException {
        BaseDocument d = new BaseDocument(false, "text/plain");
        d.insertString(0, text, null);
        return d;
    }

    private static String text(BaseDocument d) throws BadLocationException {
        return d.getText(0, d.getLength());
    }

    private static ConflictFix fix(BaseDocument d, Resolution r) throws BadLocationException {
        String t = text(d);
        MergeConflicts.Block b = MergeConflicts.scan(t).get(0);
        return new ConflictFix(d, d.createPosition(b.start()), t.substring(b.start(), b.end()), r);
    }

    @Test
    @DisplayName("each choice replaces the block as ONE undoable edit, and one undo puts the block back")
    void oneUndoableEdit() throws Exception {
        for (Resolution r : Resolution.values()) {
            BaseDocument d = doc(TEXT);
            UndoManager undo = new UndoManager();
            d.addUndoableEditListener(undo);
            assertThat(fix(d, r).apply()).as(r.name()).isTrue();
            String expected = switch (r) {
                case CURRENT -> "top\nmine\nend\n";
                case INCOMING -> "top\ntheirs\nend\n";
                case BOTH -> "top\nmine\ntheirs\nend\n";
            };
            assertThat(text(d)).as(r.name()).isEqualTo(expected);
            undo.undo();
            assertThat(text(d)).as("one undo restores the whole block after " + r).isEqualTo(TEXT);
            assertThat(undo.canUndo()).as("nothing left to undo after " + r).isFalse();
        }
    }

    @Test
    @DisplayName("a block edited after the hint was offered is left exactly as it is")
    void staleOfferRefuses() throws Exception {
        BaseDocument d = doc(TEXT);
        ConflictFix f = fix(d, Resolution.INCOMING);
        // the user types inside the ours side after the hint appeared
        d.insertString(TEXT.indexOf("mine"), "still ", null);
        String before = text(d);
        assertThat(f.apply()).isFalse();
        assertThat(text(d)).isEqualTo(before);
    }

    @Test
    @DisplayName("text typed at the very start of the block's line is not joined onto the kept side")
    void typedBeforeTheMarkerRefuses() throws Exception {
        // the position sits at the <<<<<<< line's start; typing there moves it
        // one character on while the block's own text still matches — only the
        // line-start check stops the fix from gluing "x" onto the kept side
        BaseDocument d = doc(TEXT);
        ConflictFix f = fix(d, Resolution.CURRENT);
        d.insertString(TEXT.indexOf("<<<<<<<"), "x", null);
        String before = text(d);
        assertThat(f.apply()).isFalse();
        assertThat(text(d)).isEqualTo(before);
    }

    @Test
    @DisplayName("a block that ended the file refuses once text is typed after it")
    void eofBlockStillEndsTheFile() throws Exception {
        String t = "a\n<<<<<<< HEAD\nx\n=======\ny\n>>>>>>> b";
        BaseDocument d = doc(t);
        ConflictFix f = fix(d, Resolution.CURRENT);
        d.insertString(d.getLength(), "ranch", null);
        assertThat(f.apply()).isFalse();
        assertThat(text(d)).isEqualTo(t + "ranch");
    }

    @Test
    @DisplayName("the warning sits on the header line and offers VS Code's three choices, in its order")
    void describeOffersThreeFixes() throws Exception {
        BaseDocument d = doc(TEXT);
        ErrorDescription hint = ConflictWatcher.describeFor(d, TEXT, MergeConflicts.scan(TEXT).get(0));
        assertThat(hint).isNotNull();
        assertThat(hint.getSeverity()).isEqualTo(Severity.WARNING);
        assertThat(hint.getDescription()).startsWith("Merge conflict: ");
        List<Fix> fixes = hint.getFixes().getFixes();
        assertThat(fixes).extracting(Fix::getText).containsExactly(
                "Accept Current Change", "Accept Incoming Change", "Accept Both Changes");
        assertThat(fixes).extracting(f -> ((ConflictFix) f).getSortText().toString())
                .isSorted();
        assertThat(fixes).extracting(f -> ((ConflictFix) f).anchor()).containsOnly(TEXT.indexOf("<<<<<<<"));
    }
}
