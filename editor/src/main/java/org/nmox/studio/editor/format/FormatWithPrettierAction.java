package org.nmox.studio.editor.format;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.nmox.studio.editor.format.PrettierFormatter.OnDemand;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Format with Prettier, on demand (v1.196.0) — the senior-dev request
 * beside format-on-save: "format this file NOW", from the editor's
 * right-click menu, whether or not the project opted into the save
 * hook. The engine is the SAME {@link PrettierFormatter} (size cap,
 * trust-gated binary, capped output) and the edit is the same
 * caret-preserving {@link FormatOnSave#applyMinimalEdit}; only the
 * opt-in differs, because here the user asked for Prettier by name —
 * and a defaults-only run says so on the status line.
 *
 * <p>The format runs OFF the EDT (it spawns a process); the result is
 * applied back only if the document still matches the snapshot the
 * formatter saw — typing during the run refuses quietly rather than
 * clobbering the newer text.
 */
@ActionID(category = "Source", id = "org.nmox.studio.editor.format.FormatWithPrettierAction")
@ActionRegistration(displayName = "#CTL_FormatWithPrettier", lazy = true)
@ActionReferences({
    @ActionReference(path = "Editors/text/javascript/Popup", position = 1850),
    @ActionReference(path = "Editors/text/typescript/Popup", position = 1850),
    @ActionReference(path = "Editors/text/css/Popup", position = 1850),
    @ActionReference(path = "Editors/text/x-scss/Popup", position = 1850),
    @ActionReference(path = "Editors/text/x-less/Popup", position = 1850),
    @ActionReference(path = "Editors/text/html/Popup", position = 1850),
    @ActionReference(path = "Editors/text/x-json/Popup", position = 1850),
    @ActionReference(path = "Editors/text/x-yaml/Popup", position = 1850),
    @ActionReference(path = "Editors/text/x-markdown/Popup", position = 1850),
    @ActionReference(path = "Editors/text/markdown/Popup", position = 1850),
    @ActionReference(path = "Editors/text/x-vue/Popup", position = 1850),
    @ActionReference(path = "Editors/text/x-graphql/Popup", position = 1850),
    @ActionReference(path = "Editors/text/x-svelte/Popup", position = 1850),
    @ActionReference(path = "Editors/text/x-astro/Popup", position = 1850)
})
@Messages("CTL_FormatWithPrettier=Format with Prettier")
public final class FormatWithPrettierAction implements ActionListener {

    /** One interruptible lane; a second request queues behind the first. */
    private static final RequestProcessor RP =
            new RequestProcessor("nmox-prettier-on-demand", 1, true);

    private final DataObject context;

    public FormatWithPrettierAction(DataObject context) {
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        EditorCookie ec = context.getLookup().lookup(EditorCookie.class);
        Document doc = ec == null ? null : ec.getDocument();
        File file = FileUtil.toFile(context.getPrimaryFile());
        if (doc == null || file == null) {
            status("No editor buffer to format.");
            return;
        }
        final String snapshot;
        try {
            snapshot = doc.getText(0, doc.getLength());
        } catch (BadLocationException ex) {
            return;
        }
        RP.post(() -> {
            OnDemand result = new PrettierFormatter().formatOnDemand(snapshot, file);
            EventQueue.invokeLater(() -> report(doc, snapshot, result));
        });
    }

    private static void report(Document doc, String snapshot, OnDemand result) {
        switch (result.outcome()) {
            case FORMATTED -> {
                boolean applied;
                try {
                    applied = applyIfUnchanged(doc, snapshot, result.text());
                } catch (BadLocationException ex) {
                    status("Could not apply the formatted text.");
                    return;
                }
                if (!applied) {
                    status("Document changed while formatting — run it again.");
                } else if (result.optedIn()) {
                    status("Formatted with Prettier.");
                } else {
                    status("Formatted with Prettier defaults — the project has no Prettier config.");
                }
            }
            case ALREADY_FORMATTED -> status("Already formatted.");
            case TOO_LARGE -> status("File too large for Prettier — saved size limit applies here too.");
            case NO_PRETTIER -> status("Prettier not found — install it in the project or globally on PATH.");
            case FAILED -> status("Prettier could not format this file (syntax error?).");
        }
    }

    /**
     * Applies the formatted text only when the document still equals the
     * snapshot the formatter ran on; false means the user typed while
     * Prettier ran and nothing was touched.
     */
    static boolean applyIfUnchanged(Document doc, String snapshot, String formatted)
            throws BadLocationException {
        if (!doc.getText(0, doc.getLength()).equals(snapshot)) {
            return false;
        }
        FormatOnSave.applyMinimalEdit(doc, snapshot, formatted);
        return true;
    }

    private static void status(String message) {
        StatusDisplayer.getDefault().setStatusText(message);
    }
}
