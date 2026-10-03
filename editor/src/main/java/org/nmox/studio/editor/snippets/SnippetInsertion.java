package org.nmox.studio.editor.snippets;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.BaseTextUI;
import org.netbeans.lib.editor.codetemplates.api.CodeTemplateManager;
import org.netbeans.modules.editor.indent.api.IndentUtils;
import org.nmox.studio.core.util.EditedFile;
import org.nmox.studio.editor.polyglot.LanguageComments;
import org.nmox.studio.editor.snippets.SnippetBody.Refused;
import org.nmox.studio.editor.snippets.SnippetTemplates.CodeTemplateText;
import org.nmox.studio.editor.snippets.VsCodeSnippets.Snippet;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Puts an accepted project snippet into the editor: the typed prefix
 * (and the rest of the token the caret was in) goes, the body arrives
 * through the platform's code-template engine with its tab stops live,
 * and the two are one undo.
 *
 * <p>Everything here runs on the event thread and touches no disk: the
 * snippet was parsed when the completion list was built, and what the
 * variables need (the file's path, the caret's line, the editor's
 * indentation) is already in memory. The one read that can stall, the
 * system clipboard when another program owns it, happens only for a
 * body that names {@code CLIPBOARD}, on its own lane, and is given a
 * quarter of a second before the variable counts as not set.
 *
 * <p>A snippet that cannot be honoured whole against this file (a
 * transform that runs past its bounds on this file's name, say) is not
 * inserted at all: the document is left as typed, the status line says
 * so and the log says why.
 *
 * <p><b>The keys are the engine's</b>, the ones every code template in
 * this IDE already has, and they are not quite VS Code's: Tab and
 * Shift+Tab go round the tab stops, first to last and back to the
 * first; Enter goes to the next stop and, after the last, leaves the
 * snippet with the caret at {@code $0} (measured in
 * {@code SnippetEngineTest}, read from
 * {@code TextRegionManager.tabAction} and {@code enterAction}). VS
 * Code's Tab leaves at {@code $0} after the last stop.
 */
final class SnippetInsertion {

    private static final Logger LOG = Logger.getLogger(SnippetInsertion.class.getName());

    private static final RequestProcessor CLIPBOARD = new RequestProcessor("nmox-snippet-clipboard", 1);

    /** How long a snippet waits for the clipboard, in milliseconds. */
    static final long CLIPBOARD_WAIT = 250;

    private static final SecureRandom DICE = new SecureRandom();

    private SnippetInsertion() {
    }

    /**
     * Replaces the {@code typed} characters at {@code start} with the
     * snippet.
     *
     * @param workspace the project folder, for {@code WORKSPACE_FOLDER} and {@code RELATIVE_FILEPATH}; may be null
     * @return whether the snippet was inserted
     */
    @Messages({
        "# {0} - the snippet's name",
        "SnippetInsertion_refused=Snippet “{0}” was not inserted: it cannot be honoured whole in this file. The log says why."
    })
    static boolean insert(JTextComponent component, Snippet snippet, File workspace, int start, int typed) {
        return insert(component, snippet, workspace, start, typed, SnippetInsertion::throughTheEngine);
    }

    /** How a translated snippet is handed to the code-template engine; a parameter so a test can make the engine fail. */
    interface Engine {
        void insert(Document doc, JTextComponent component, CodeTemplateText text);
    }

    /** The platform's engine, with the snippet announced on the component for {@link SnippetTemplateProcessor}. */
    private static void throughTheEngine(Document doc, JTextComponent component, CodeTemplateText text) {
        component.putClientProperty(SnippetTemplateProcessor.PENDING, text);
        try {
            CodeTemplateManager.get(doc).createTemporary(text.text()).insert(component);
        } finally {
            component.putClientProperty(SnippetTemplateProcessor.PENDING, null);
        }
    }

    static boolean insert(JTextComponent component, Snippet snippet, File workspace, int start, int typed,
            Engine engine) {
        Document doc = component.getDocument();
        CodeTemplateText text;
        String replaced;
        try {
            int caret = start + typed;
            text = SnippetTemplates.toCodeTemplate(snippet.body(), context(component, doc, caret, workspace));
            String after = doc.getText(caret, Math.min(SnippetPrefix.MAX_TAIL, doc.getLength() - caret));
            // only a token being typed has a tail to fold: at a fresh spot the
            // word after the caret is the user's, not the prefix's remainder
            int tail = typed > 0 && SnippetPrefix.wordChar(doc.getText(caret - 1, 1).charAt(0))
                    ? SnippetPrefix.tail(after) : 0;
            replaced = doc.getText(start, typed + tail);
        } catch (Refused refused) {
            LOG.log(Level.INFO, "Snippet \"{0}\" ({1}) was not inserted because {2}",
                    new Object[] {snippet.name(), snippet.source(), refused.getMessage()});
            StatusDisplayer.getDefault().setStatusText(Bundle.SnippetInsertion_refused(snippet.name()));
            return false;
        } catch (BadLocationException moved) {
            // the document changed between the list and the pick; the offsets are stale
            return false;
        }
        int remove = replaced.length();
        // the engine drives tab stops only on the platform's own text UI
        // (it reaches into BaseTextUI); any other editor gets the text
        if (doc instanceof BaseDocument base && component.getUI() instanceof BaseTextUI) {
            boolean[] landed = {false};
            try {
                // one atomic edit: the prefix going and the body arriving undo
                // together, and anything thrown inside it takes both back
                base.runAtomicAsUser(() -> {
                    try {
                        doc.remove(start, remove);
                    } catch (BadLocationException moved) {
                        return; // nothing has changed yet
                    }
                    component.setCaretPosition(start);
                    engine.insert(doc, component, text);
                    landed[0] = true;
                });
            } catch (RuntimeException engineFailed) {
                LOG.log(Level.WARNING, "The code-template engine did not take snippet \"" + snippet.name()
                        + "\"; it is inserted without tab stops", engineFailed);
            }
            if (landed[0]) {
                return true;
            }
        }
        return plain(component, doc, text, start, replaced);
    }

    /**
     * The snippet as text, caret at {@code $0}: for an editor the engine
     * cannot drive, and for the moment the engine fails. Only if the
     * document still holds, at {@code start}, exactly what was to be
     * replaced; after a failure nobody can vouch for, nothing is guessed.
     */
    private static boolean plain(JTextComponent component, Document doc, CodeTemplateText text, int start,
            String replaced) {
        boolean[] landed = {false};
        Runnable edit = () -> {
            try {
                if (doc.getLength() < start + replaced.length()
                        || !doc.getText(start, replaced.length()).equals(replaced)) {
                    return;
                }
                doc.remove(start, replaced.length());
                doc.insertString(start, text.plain(), null);
                landed[0] = true;
            } catch (BadLocationException moved) {
                // the offsets went stale; what was typed stays
            }
        };
        if (doc instanceof BaseDocument base) {
            base.runAtomicAsUser(edit);
        } else {
            edit.run();
        }
        if (landed[0]) {
            component.setCaretPosition(start + text.plainCaret());
        }
        return landed[0];
    }

    /** What the snippet's variables and indentation are answered from, read off the editor as it is now. */
    static SnippetContext context(JTextComponent component, Document doc, int caret, File workspace)
            throws BadLocationException {
        FileObject fo = EditedFile.of(doc);
        File file = fo == null ? null : FileUtil.toFile(fo);
        Element lines = doc.getDefaultRootElement();
        int index = lines.getElementIndex(caret);
        Element lineElement = lines.getElement(index);
        int lineStart = lineElement.getStartOffset();
        int lineEnd = Math.min(lineElement.getEndOffset(), doc.getLength());
        String line = doc.getText(lineStart, lineEnd - lineStart);
        if (line.endsWith("\n")) {
            line = line.substring(0, line.length() - 1);
        }
        int column = Math.min(caret - lineStart, line.length());
        int lead = 0;
        while (lead < column && (line.charAt(lead) == ' ' || line.charAt(lead) == '\t')) {
            lead++;
        }
        Object mime = doc.getProperty("mimeType");
        String mimeType = mime instanceof String m ? m : null;
        LanguageComments.BlockComment block = LanguageComments.blockPairFor(mimeType);
        return new SnippetContext(
                file == null ? null : file.getAbsolutePath(),
                workspace == null ? null : workspace.getAbsolutePath(),
                component.getSelectedText(),
                line,
                SnippetPrefix.wordAt(line, column),
                index,
                SnippetInsertion::clipboard,
                LanguageComments.lineCommentFor(mimeType),
                block == null ? null : block.open(),
                block == null ? null : block.close(),
                ZonedDateTime.now(),
                DICE,
                line.substring(0, lead),
                indentUnit(doc));
    }

    /** One level of the editor's indentation: a tab, or the document's own number of spaces. */
    private static String indentUnit(Document doc) {
        try {
            if (!IndentUtils.isExpandTabs(doc)) {
                return "\t";
            }
            return " ".repeat(Math.max(1, Math.min(16, IndentUtils.indentLevelSize(doc))));
        } catch (RuntimeException noSettings) {
            return "    ";
        }
    }

    /**
     * The clipboard's text, or null: no clipboard, nothing textual on it,
     * an owner that does not answer within {@link #CLIPBOARD_WAIT}, or any
     * of the ways a clipboard read fails. Never blocks longer than that,
     * never throws.
     */
    static String clipboard() {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }
        Future<String> reading = CLIPBOARD.submit(() -> {
            Clipboard platform = Lookup.getDefault().lookup(Clipboard.class);
            Clipboard clipboard = platform != null ? platform : Toolkit.getDefaultToolkit().getSystemClipboard();
            return clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)
                    ? (String) clipboard.getData(DataFlavor.stringFlavor) : null;
        });
        try {
            return reading.get(CLIPBOARD_WAIT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException | RuntimeException unreadable) {
            reading.cancel(true);
            return null;
        }
    }
}
