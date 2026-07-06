package org.nmox.studio.editor.completion;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 * The one document splice every completion item performs: remove the
 * typed prefix, insert the completion text. A BadLocationException here
 * is not a defect — the user kept typing (or an undo landed) between
 * the query snapshot and the pick, so the offsets no longer fit the
 * document; the completion simply doesn't apply. That routine race must
 * never raise the exception dialog.
 */
final class CompletionEdits {

    private static final Logger LOG = Logger.getLogger(CompletionEdits.class.getName());

    private CompletionEdits() {
    }

    /**
     * Replaces {@code len} characters at {@code start} with {@code text}.
     *
     * @return true when the splice landed; false when the document had
     *         moved on under the completion — callers must skip caret
     *         repositioning in that case, the offsets are stale
     */
    static boolean replace(Document doc, int start, int len, String text) {
        try {
            doc.remove(start, len);
            doc.insertString(start, text, null);
            return true;
        } catch (BadLocationException ex) {
            LOG.log(Level.INFO, "completion edit skipped: {0}", ex.getMessage());
            return false;
        }
    }
}
