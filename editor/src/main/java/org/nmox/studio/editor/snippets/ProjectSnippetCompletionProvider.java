package org.nmox.studio.editor.snippets;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;
import org.nmox.studio.core.util.EditedFile;
import org.nmox.studio.editor.lsp.LspLanguageIds;
import org.nmox.studio.editor.snippets.VsCodeSnippets.Snippet;
import org.nmox.studio.editor.standards.VsCodeSettings;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * A repository's own snippets in the completion list: type a prefix a
 * team committed in {@code .vscode/*.code-snippets}, press ⌃Space, and
 * the snippet is offered with its name and its file; accepting it
 * inserts the body with its tab stops ({@link SnippetInsertion}).
 *
 * <p>Registered once, for every language, because a snippet with no
 * {@code scope} applies to every language; a snippet WITH a scope is
 * offered only in files of the languages it names. The language of a
 * file is asked of the one vocabulary the product has for VS Code's
 * language ids, {@link VsCodeSettings#languageId} over
 * {@link LspLanguageIds} (the table that tells a language server what a
 * file is), so {@code "scope": "typescript"} and a
 * {@code "[typescript]"} settings block mean the same files.
 *
 * <p>The list is built on the completion thread, and the files are read
 * on {@link ProjectSnippets}' own lane with a bounded wait; nothing here
 * touches disk on the event thread. The provider joins a list that is
 * opening (⌃Space, or the editor's own automatic popup); it does not
 * open one by itself, because knowing whether what was just typed is a
 * prefix would mean reading the project on every keystroke.
 */
@MimeRegistration(mimeType = "", service = CompletionProvider.class, position = 590)
public class ProjectSnippetCompletionProvider implements CompletionProvider {

    /** How long a completion waits for the snippet files, in milliseconds. */
    static final long WAIT = 1500;

    /** How much of the caret's line is looked at for a prefix. */
    static final int WINDOW = 200;

    @Override
    public CompletionTask createTask(int queryType, JTextComponent component) {
        // COMPLETION (1) or COMPLETION_ALL (9): a second ⌃Space while the
        // list shows re-queries as ALL, and an equality gate would drop
        // every snippet on that press (the v2.58.1 walk find)
        if ((queryType & COMPLETION_QUERY_TYPE) == 0) {
            return null;
        }
        return new AsyncCompletionTask(new Query(), component);
    }

    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
        return 0;
    }

    /**
     * VS Code's language id for the document: what its editor is (the
     * document's own mime type), with the file's name deciding the two
     * ids VS Code keeps apart; null when neither says.
     */
    static String languageId(Document doc, File file) {
        String mime = doc.getProperty("mimeType") instanceof String m ? m : null;
        return VsCodeSettings.languageId(file == null ? null : file.getName(), mime);
    }

    /**
     * The snippets of {@code all} offered when {@code before} is the
     * caret line up to the caret, in a file of language
     * {@code languageId}: one row a snippet, under the prefix that
     * matches the most of what was typed. Pure.
     */
    static List<ProjectSnippetCompletionItem> items(List<Snippet> all, String languageId, String before,
            File workspace, int caret) {
        List<ProjectSnippetCompletionItem> out = new ArrayList<>();
        for (Snippet snippet : all) {
            if (!snippet.appliesTo(languageId)) {
                continue;
            }
            String best = null;
            int most = -1;
            for (String prefix : snippet.prefixes()) {
                int typed = SnippetPrefix.typed(before, prefix);
                if (typed > most) {
                    most = typed;
                    best = prefix;
                }
            }
            if (best != null) {
                out.add(new ProjectSnippetCompletionItem(snippet, best, workspace, caret - most, most));
            }
        }
        return out;
    }

    private static final class Query extends AsyncCompletionQuery {

        @Override
        protected void query(CompletionResultSet result, Document doc, int caret) {
            try {
                FileObject fo = EditedFile.of(doc);
                File file = fo == null ? null : FileUtil.toFile(fo);
                if (file == null) {
                    return; // a document with no file has no project to have snippets
                }
                ProjectSnippets.Found found = ProjectSnippets.within(file, WAIT);
                if (found.snippets().isEmpty()) {
                    return;
                }
                int from = Math.max(0, caret - WINDOW);
                String before = doc.getText(from, caret - from);
                before = before.substring(before.lastIndexOf('\n') + 1);
                result.addAllItems(items(found.snippets(), languageId(doc, file), before,
                        found.workspace(), caret));
            } catch (BadLocationException moved) {
                // the document changed under the query; offer nothing
            } finally {
                result.finish();
            }
        }
    }
}
