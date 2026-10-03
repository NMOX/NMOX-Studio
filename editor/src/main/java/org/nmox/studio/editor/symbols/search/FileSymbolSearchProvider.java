package org.nmox.studio.editor.symbols.search;

import java.awt.EventQueue;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.core.util.EditedFile;
import org.nmox.studio.core.util.PlainStatus;
import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.editor.outline.OutlineModel;
import org.openide.awt.StatusDisplayer;
import org.openide.cookies.LineCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.util.Mutex;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;

/**
 * Quick Search (⌘I) into the file being edited: VS Code's Go to Symbol in
 * Editor. The category "Symbols in This File" lists the outline items of
 * the editor last typed in (the Navigator's own extractor,
 * {@link OutlineModel}) that match what was typed, and Enter puts the
 * caret on the symbol's line. {@code @name} is accepted, as in VS Code;
 * {@link FileSymbols} has the matching.
 *
 * <p>The platform calls {@link #evaluate} on its own background lane, once
 * per keystroke. The editor and whether it is still showing are asked on
 * the event thread (window state lives there); the text is copied under
 * the document's read lock on this lane, bounded by {@link #MAX_CHARS},
 * and the outline is extracted here too, so the event thread never waits
 * on a parse. A file past the bound is outlined as far as the bound
 * reaches, which is what the Navigator does with a file past its own.
 */
public class FileSymbolSearchProvider implements SearchProvider {

    /** The most text one search copies out of the editor. */
    static final int MAX_CHARS = 2_000_000;

    /** The editor last typed in, when it is on screen; a seam for tests. */
    static Supplier<JTextComponent> activeEditor = () -> Mutex.EVENT.readAccess(() -> {
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        return editor != null && editor.isShowing() ? editor : null;
    });

    /** Where a jump that could not land is said; a seam for tests. */
    static Consumer<String> status = text -> StatusDisplayer.getDefault().setStatusText(PlainStatus.text(text));

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        JTextComponent editor = activeEditor.get();
        Document doc = editor == null ? null : editor.getDocument();
        if (doc == null) {
            return;
        }
        for (OutlineModel.Item item : FileSymbols.matching(request.getText(), outlineOf(doc))) {
            if (!response.addResult(() -> EventQueue.invokeLater(() -> jump(editor, doc, item)),
                    PlainText.escape(label(item)))) {
                return;
            }
        }
    }

    /** The outline of what {@code doc} holds now, read under its lock and bounded. */
    static List<OutlineModel.Item> outlineOf(Document doc) {
        String[] text = new String[1];
        doc.render(() -> {
            try {
                text[0] = doc.getText(0, Math.min(doc.getLength(), MAX_CHARS));
            } catch (BadLocationException shrank) {
                text[0] = "";
            }
        });
        return OutlineModel.extract(mimeOf(doc), text[0]);
    }

    /** The language the outline reads the file as: the file's own type, as the Navigator asks, else the editor kit's. */
    static String mimeOf(Document doc) {
        FileObject file = EditedFile.of(doc);
        if (file != null) {
            return file.getMIMEType();
        }
        Object mime = doc.getProperty("mimeType");
        return mime instanceof String m ? m : "text/plain";
    }

    /** The row for a symbol: its name, what the outline says about it, and its line. */
    static String label(OutlineModel.Item item) {
        String line = String.valueOf(item.line() + 1);
        return item.detail() == null || item.detail().isBlank()
                ? NbBundle.getMessage(FileSymbolSearchProvider.class, "FileSymbols_row", item.name(), line)
                : NbBundle.getMessage(FileSymbolSearchProvider.class, "FileSymbols_rowDetail",
                        item.name(), item.detail(), line);
    }

    /**
     * Puts the caret on the symbol's line and gives the editor the focus.
     * The file may have changed since the search: a line that is no longer
     * there is said, not approximated, and an editor that has since been
     * given another document is left alone.
     */
    static void jump(JTextComponent editor, Document doc, OutlineModel.Item item) {
        if (editor.getDocument() != doc) {
            return;
        }
        Element root = doc.getDefaultRootElement();
        if (item.line() >= root.getElementCount()) {
            status.accept(NbBundle.getMessage(FileSymbolSearchProvider.class, "FileSymbols_gone",
                    String.valueOf(item.line() + 1)));
            return;
        }
        if (doc.getProperty(Document.StreamDescriptionProperty) instanceof DataObject data) {
            LineCookie lines = data.getLookup().lookup(LineCookie.class);
            if (lines != null) {
                try {
                    // the platform's own jump: opens, scrolls and focuses the file's editor
                    lines.getLineSet().getCurrent(item.line())
                            .show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
                    return;
                } catch (IndexOutOfBoundsException shrank) {
                    // the line set is behind the document; the caret below is not
                }
            }
        }
        editor.setCaretPosition(root.getElement(item.line()).getStartOffset());
        TopComponent tab = (TopComponent) SwingUtilities.getAncestorOfClass(TopComponent.class, editor);
        if (tab != null) {
            tab.requestActive();
        }
        editor.requestFocusInWindow();
    }
}
