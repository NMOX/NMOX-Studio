package org.nmox.studio.editor.grammars;

import java.util.List;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.HintsController;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * A warning on a commit message's summary line when git's tools would
 * cut it (3.2.0; the rule is {@link GitSummaryLine}). The warning is the
 * platform's own hint — a squiggle, a stripe mark, a tooltip — through
 * {@link HintsController}, the same channel as every rack finding
 * ({@code RackSquiggler}); nothing here paints.
 *
 * <p><b>The hook.</b> A highlights-layer factory registered on the mime
 * is the per-document hook the editor offers without a parser: it is
 * called when an editor for the document is built, and it returns NO
 * layers — its only job is to attach one listener per document (a
 * document property marks it, so a split editor's second view does not
 * attach a second). The listener lives as long as the document and
 * nothing else holds it (the {@code CssColorHighlighter} idiom), and the
 * judgement rides a named lane 200 ms after the last keystroke, never
 * the paint thread.
 */
// The limit is written INTO the sentence rather than passed as {1}: a
// counted noun after a placeholder needs plural branches no ChoiceFormat
// can give Slavic or Arabic, while "72" is one fixed number each language
// can decline correctly once. GitSummaryLineTest holds the sentence to
// GitSummaryLine.LIMIT so the two cannot drift.
@NbBundle.Messages({
    "# {0} - the summary line's length in characters",
    "HINT_GitSummaryTooLong=The summary line runs past 72 characters, where git tools cut it (length {0})"
})
public final class GitSummaryLineHint implements DocumentListener {

    /** The hints layer this warning owns; setting it empty clears it. */
    static final String LAYER = "nmox-git-summary";

    private static final Object ATTACHED = new Object();
    private static final RequestProcessor RP = new RequestProcessor("nmox-git-summary", 1);

    private final Document doc;
    private RequestProcessor.Task pending;

    private GitSummaryLineHint(Document doc) {
        this.doc = doc;
    }

    /** Attaches the judge to {@code doc} once, and judges it now. */
    static void attach(Document doc) {
        // editors are built on the EDT, so two views of one document
        // arrive here one after the other; the property is the whole guard
        if (doc.getProperty(ATTACHED) != null) {
            return;
        }
        doc.putProperty(ATTACHED, Boolean.TRUE);
        GitSummaryLineHint hint = new GitSummaryLineHint(doc);
        doc.addDocumentListener(hint);
        hint.schedule(0);
    }

    private synchronized void schedule(int delayMillis) {
        if (pending != null) {
            pending.cancel();
        }
        pending = RP.post(this::judge, delayMillis);
    }

    private void judge() {
        HintsController.setErrors(doc, LAYER, describe(doc));
    }

    /**
     * The warnings {@code doc} deserves right now: none, or one over the
     * part of the summary line past the limit. Package-private so the
     * mapping from rule to hint is a unit test.
     */
    /** The most of line one ever read: past it the length only grows, and the warning already shows. */
    static final int FIRST_LINE_CAP = 64 * 1024;

    static List<ErrorDescription> describe(Document doc) {
        String[] text = {""};
        doc.render(() -> {
            try {
                // the first line only, bounded: under git commit -v the file
                // holds the whole diff, and the rule judges line one (3.2.0
                // review — each pause used to copy all of it)
                javax.swing.text.Element first = doc.getDefaultRootElement().getElement(0);
                int start = first.getStartOffset();
                int end = Math.min(first.getEndOffset(), doc.getLength());
                text[0] = doc.getText(start, Math.min(end - start, FIRST_LINE_CAP));
            } catch (BadLocationException ex) {
                text[0] = "";
            }
        });
        GitSummaryLine.Overflow over = GitSummaryLine.overflow(text[0]);
        if (over == null) {
            return List.of();
        }
        try {
            return List.of(ErrorDescriptionFactory.createErrorDescription(
                    Severity.WARNING, message(over.length()), doc,
                    doc.createPosition(over.start()), doc.createPosition(over.end())));
        } catch (BadLocationException ex) {
            // the document changed under the read; the next edit judges again
            return List.of();
        }
    }

    /** The warning's sentence, in the reader's language. */
    static String message(int length) {
        // a String, so a 1,000-character line never reads with a separator
        return Bundle.HINT_GitSummaryTooLong(Integer.toString(length));
    }

    /** Test barrier: drains the judging lane. */
    static void awaitQuiet() {
        RP.post(() -> {
        }).waitFinished();
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        schedule(200);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        schedule(200);
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        // attribute-only change: the text, and so the verdict, is the same
    }

    /** The per-document hook; see the class comment. */
    @MimeRegistration(mimeType = GitCommitGrammar.MIME, service = HighlightsLayerFactory.class)
    public static class Factory implements HighlightsLayerFactory {

        @Override
        public HighlightsLayer[] createLayers(Context context) {
            attach(context.getDocument());
            return new HighlightsLayer[0];
        }
    }
}
