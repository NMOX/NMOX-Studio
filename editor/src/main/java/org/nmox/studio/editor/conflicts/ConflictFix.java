package org.nmox.studio.editor.conflicts;

import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.netbeans.editor.BaseDocument;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.EnhancedFix;
import org.nmox.studio.editor.conflicts.MergeConflicts.Block;
import org.nmox.studio.editor.conflicts.MergeConflicts.Resolution;
import org.openide.awt.StatusDisplayer;
import org.openide.text.NbDocument;

/**
 * One of VS Code's three choices on one conflict block, as the platform's
 * hint fix (⌥↩ / Alt+Enter on the {@code <<<<<<<} line, or the gutter
 * bulb). The replacement is ONE undoable edit: the block's text out and
 * the kept side in, under {@link NbDocument#runAtomicAsUser}, so ⌘Z puts
 * the whole block back.
 *
 * <p><b>Stale offers refuse.</b> The platform runs a fix on a background
 * thread, later than the hint was computed, and the user may have typed
 * into the block meanwhile. The fix therefore carries the block's exact
 * text as it was offered and replaces only when the document still holds
 * exactly that text at that place — checked INSIDE the atomic edit, so
 * nothing can change between the check and the replacement. Otherwise
 * it replaces nothing and says so on the status line.
 */
final class ConflictFix implements EnhancedFix {

    private final Document doc;
    private final Position start;
    private final String blockText;
    private final Resolution choice;

    ConflictFix(Document doc, Position start, String blockText, Resolution choice) {
        this.doc = doc;
        this.start = start;
        this.blockText = blockText;
        this.choice = choice;
    }

    @Override
    public String getText() {
        return switch (choice) {
            case CURRENT -> Bundle.MergeConflict_acceptCurrent();
            case INCOMING -> Bundle.MergeConflict_acceptIncoming();
            case BOTH -> Bundle.MergeConflict_acceptBoth();
        };
    }

    /** VS Code's order, which the platform would otherwise sort alphabetically. */
    @Override
    public CharSequence getSortText() {
        return Integer.toString(choice.ordinal());
    }

    @Override
    public ChangeInfo implement() throws Exception {
        if (!apply()) {
            StatusDisplayer.getDefault().setStatusText(Bundle.MergeConflict_changed());
        }
        return null;
    }

    /** Replaces the block, or nothing; true when it replaced. Package-private for the tests. */
    boolean apply() throws BadLocationException {
        boolean[] done = {false};
        Runnable edit = () -> {
            try {
                done[0] = replaceIfUnchanged();
            } catch (BadLocationException ex) {
                // the document moved under the check; nothing was replaced
                done[0] = false;
            }
        };
        // one undo unit: the editor's own documents are BaseDocuments, whose
        // atomic section folds the removal and the insertion into one edit
        // (the GhostText idiom); NbDocument's route only groups for a
        // document that implements its WriteLockable
        if (doc instanceof BaseDocument base) {
            base.runAtomicAsUser(edit);
        } else if (doc instanceof StyledDocument styled) {
            NbDocument.runAtomicAsUser(styled, edit);
        } else {
            edit.run();
        }
        return done[0];
    }

    /** Where the offered block began, as the document now places it; for the tests. */
    int anchor() {
        return start.getOffset();
    }

    private boolean replaceIfUnchanged() throws BadLocationException {
        int at = start.getOffset();
        int length = blockText.length();
        if (at + length > doc.getLength()) {
            return false;
        }
        // a marker is a marker only at a line start
        if (at > 0 && !"\n".equals(doc.getText(at - 1, 1))) {
            return false;
        }
        if (!blockText.equals(doc.getText(at, length))) {
            return false;
        }
        List<Block> blocks = MergeConflicts.scan(blockText);
        if (blocks.size() != 1 || blocks.get(0).end() != length) {
            return false;
        }
        Block block = blocks.get(0);
        // a block offered at the end of the file must still end it: text
        // typed after its last marker line makes that line something else
        if (block.endsAtEof() && at + length != doc.getLength()) {
            return false;
        }
        String kept = MergeConflicts.replacement(blockText, block, choice);
        doc.remove(at, length);
        if (!kept.isEmpty()) {
            doc.insertString(at, kept, null);
        }
        return true;
    }
}
