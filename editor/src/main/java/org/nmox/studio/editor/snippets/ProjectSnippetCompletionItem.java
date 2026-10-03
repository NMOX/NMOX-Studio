package org.nmox.studio.editor.snippets;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.io.File;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;
import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.editor.snippets.VsCodeSnippets.Snippet;
import org.openide.util.NbBundle.Messages;

/**
 * One project snippet in the completion list:
 * {@code log — Log to console (Log output to console)} on the left and
 * the file it was committed in, {@code · team.code-snippets}, on the
 * right, so the list also says whose snippet this is.
 *
 * <p>Every word of that comes from a file in the repository, and the
 * list paints its rows as markup. So each piece is escaped before it is
 * placed ({@link PlainText#escape}): a snippet named
 * {@code <html><img src=…>} is shown as those characters, which is the
 * v2.86.0 law for text that somebody else wrote.
 */
final class ProjectSnippetCompletionItem implements CompletionItem {

    /** What the parenthesis shows of a description or a body's first line, in code points. */
    static final int HINT_CHARS = 60;

    private final Snippet snippet;
    private final String prefix;
    private final File workspace;
    private final int startOffset;
    private final int typed;

    ProjectSnippetCompletionItem(Snippet snippet, String prefix, File workspace, int startOffset, int typed) {
        this.snippet = snippet;
        this.prefix = prefix;
        this.workspace = workspace;
        this.startOffset = startOffset;
        this.typed = typed;
    }

    /** The snippet this row inserts. */
    Snippet snippet() {
        return snippet;
    }

    /** Where the text this row replaces begins. */
    int startOffset() {
        return startOffset;
    }

    @Override
    public void defaultAction(JTextComponent component) {
        Completion.get().hideAll();
        insertInto(component);
    }

    /** What accepting the row does to the editor, without the completion window: the tests' way in. */
    boolean insertInto(JTextComponent component) {
        return SnippetInsertion.insert(component, snippet, workspace, startOffset, typed);
    }

    /** The left column: the prefix, the name, and what the snippet says of itself; markup-safe. */
    @Messages({
        "# {0} - what is typed",
        "# {1} - the snippet's name",
        "# {2} - its description, or the first line of its body",
        "ProjectSnippetCompletionItem_label={0} — {1} ({2})",
        "# {0} - what is typed",
        "# {1} - the snippet's name",
        "ProjectSnippetCompletionItem_labelBare={0} — {1}"
    })
    static String label(Snippet snippet, String prefix) {
        String hint = snippet.description().isBlank() ? snippet.firstLine() : snippet.description();
        hint = clip(hint.strip().replace('\n', ' ').replace('\t', ' '));
        String name = clip(snippet.name().replace('\n', ' '));
        return hint.isEmpty() || hint.equals(name)
                ? Bundle.ProjectSnippetCompletionItem_labelBare(PlainText.escape(prefix), PlainText.escape(name))
                : Bundle.ProjectSnippetCompletionItem_label(PlainText.escape(prefix), PlainText.escape(name),
                        PlainText.escape(hint));
    }

    /** The right column: the file the snippet was committed in; markup-safe. */
    @Messages({
        "# {0} - the snippet file's name",
        "ProjectSnippetCompletionItem_source=· {0}"
    })
    static String provenance(Snippet snippet) {
        return Bundle.ProjectSnippetCompletionItem_source(PlainText.escape(snippet.source()));
    }

    private static String clip(String s) {
        int points = s.codePointCount(0, s.length());
        return points <= HINT_CHARS ? s : s.substring(0, s.offsetByCodePoints(0, HINT_CHARS - 1)) + "…";
    }

    @Override
    public void processKeyEvent(KeyEvent evt) {
    }

    @Override
    public int getPreferredWidth(Graphics g, Font defaultFont) {
        return CompletionUtilities.getPreferredWidth(label(snippet, prefix), provenance(snippet), g, defaultFont);
    }

    @Override
    public void render(Graphics g, Font defaultFont, Color defaultColor,
            Color backgroundColor, int width, int height, boolean selected) {
        CompletionUtilities.renderHtml(null, label(snippet, prefix), provenance(snippet),
                g, defaultFont, defaultColor, width, height, selected);
    }

    @Override
    public CompletionTask createDocumentationTask() {
        return null;
    }

    @Override
    public CompletionTask createToolTipTask() {
        return null;
    }

    @Override
    public boolean instantSubstitution(JTextComponent component) {
        return false;
    }

    @Override
    public int getSortPriority() {
        // after what the language itself offers at this spot
        return 400;
    }

    @Override
    public CharSequence getSortText() {
        return prefix;
    }

    @Override
    public CharSequence getInsertPrefix() {
        return prefix;
    }
}
