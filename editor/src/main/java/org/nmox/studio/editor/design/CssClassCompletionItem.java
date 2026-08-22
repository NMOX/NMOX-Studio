package org.nmox.studio.editor.design;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;

import javax.swing.text.JTextComponent;

import org.netbeans.api.editor.completion.Completion;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;

/**
 * One stylesheet class in the {@code class="…"} completion popup
 * (v2.27.0): the class name on the left, the declaring stylesheet on
 * the right — the popup doubles as a map of where the project's
 * styling lives. Accepting inserts the bare name; the attribute's
 * quotes and any sibling classes stay untouched.
 */
public class CssClassCompletionItem implements CompletionItem {

    private final String name;
    private final String provenance;
    private final int startOffset;
    private final int prefixLength;

    public CssClassCompletionItem(String name, String provenance,
            int startOffset, int prefixLength) {
        this.name = name;
        this.provenance = provenance;
        this.startOffset = startOffset;
        this.prefixLength = prefixLength;
    }

    @Override
    public void defaultAction(JTextComponent component) {
        javax.swing.text.Document doc = component.getDocument();
        // mid-token accept (v2.30.0 walk find): the caret can sit INSIDE
        // the token ("h|e" typing "hero"), and replacing only the typed
        // prefix strands the tail as ".heroe" — the span extends past
        // the caret over the token's remaining name chars (the v1.332.0
        // auto-pair fold, one class over)
        int tail = 0;
        try {
            String after = doc.getText(startOffset + prefixLength,
                    Math.min(64, doc.getLength() - startOffset - prefixLength));
            tail = tailLength(after);
        } catch (javax.swing.text.BadLocationException ignore) {
            // fall through with tail 0: worst case is the old behavior
        }
        if (org.nmox.studio.editor.completion.CompletionEdits.replace(
                doc, startOffset, prefixLength + tail, name)) {
            component.setCaretPosition(startOffset + name.length());
        }
        Completion.get().hideAll();
    }

    /** Name chars at the head of {@code after} — the token's remainder. */
    static int tailLength(String after) {
        int i = 0;
        while (i < after.length() && (Character.isLetterOrDigit(after.charAt(i))
                || after.charAt(i) == '-' || after.charAt(i) == '_')) {
            i++;
        }
        return i;
    }

    @Override
    public void processKeyEvent(KeyEvent evt) {
    }

    @Override
    public int getPreferredWidth(Graphics g, Font defaultFont) {
        return CompletionUtilities.getPreferredWidth(name, provenance, g, defaultFont);
    }

    @Override
    public void render(Graphics g, Font defaultFont, Color defaultColor,
            Color backgroundColor, int width, int height, boolean selected) {
        CompletionUtilities.renderHtml(null, name, provenance,
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
        return 90;
    }

    @Override
    public CharSequence getSortText() {
        return name;
    }

    @Override
    public CharSequence getInsertPrefix() {
        return name;
    }
}
