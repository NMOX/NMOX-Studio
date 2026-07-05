package org.nmox.studio.editor.classic;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;

/**
 * One classic-library completion: the entry name on the left, its
 * documented signature and library on the right. Accepting replaces the
 * matched span (a full prefix like {@code $.aj}, or just the
 * {@code .addC} tail after a receiver — see {@link ClassicApiMatcher})
 * with the entry name.
 */
public final class ClassicApiCompletionItem implements CompletionItem {

    private static final Color CLASSIC = new Color(0xB8, 0x86, 0x0B); // dark goldenrod: the vintage shelf

    private final ClassicApiCatalog.Entry entry;
    private final String libraryDisplay;
    private final int startOffset;
    private final int length;

    public ClassicApiCompletionItem(ClassicApiCatalog.Entry entry, String libraryDisplay,
            int startOffset, int length) {
        this.entry = entry;
        this.libraryDisplay = libraryDisplay;
        this.startOffset = startOffset;
        this.length = length;
    }

    @Override
    public void defaultAction(JTextComponent component) {
        try {
            Document doc = component.getDocument();
            doc.remove(startOffset, length);
            doc.insertString(startOffset, entry.name(), null);
            component.setCaretPosition(startOffset + entry.name().length());
        } catch (BadLocationException ex) {
            // the document changed under the popup; nothing to do
        }
        Completion.get().hideAll();
    }

    @Override
    public void processKeyEvent(KeyEvent evt) {
    }

    @Override
    public int getPreferredWidth(Graphics g, Font defaultFont) {
        return CompletionUtilities.getPreferredWidth(
                entry.name() + " " + rightText(), null, g, defaultFont);
    }

    @Override
    public void render(Graphics g, Font defaultFont, Color defaultColor,
            Color backgroundColor, int width, int height, boolean selected) {
        CompletionUtilities.renderHtml(null, entry.name(), rightText(), g, defaultFont,
                selected ? Color.WHITE : CLASSIC, width, height, selected);
    }

    /** The dim right-hand column: signature plus the library it came from. */
    String rightText() {
        return entry.sig() + "  [" + libraryDisplay + "]";
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
        return 0;
    }

    @Override
    public CharSequence getSortText() {
        // sort by the name without its sigil so $.each, .each and _.each interleave sanely
        String n = entry.name();
        int i = 0;
        while (i < n.length() && (n.charAt(i) == '$' || n.charAt(i) == '_' || n.charAt(i) == '.')) {
            i++;
        }
        return i < n.length() ? n.substring(i) : n;
    }

    @Override
    public CharSequence getInsertPrefix() {
        return entry.name();
    }
}
