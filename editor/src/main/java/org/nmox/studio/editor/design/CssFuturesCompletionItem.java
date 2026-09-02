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
 * One 2031 CSS property or value in the popup: the name on the left,
 * its one-line meaning on the right (a property popup that TEACHES the
 * new vocabulary, since the platform's docs cannot). Accepting inserts
 * the text over the typed prefix plus any token tail under the caret
 * (the v2.30.0 mid-token accept cure).
 */
public class CssFuturesCompletionItem implements CompletionItem {

    private final String insert;
    private final String note;
    private final int startOffset;
    private final int prefixLength;

    public CssFuturesCompletionItem(String insert, String note,
            int startOffset, int prefixLength) {
        this.insert = insert;
        this.note = note;
        this.startOffset = startOffset;
        this.prefixLength = prefixLength;
    }

    @Override
    public void defaultAction(JTextComponent component) {
        javax.swing.text.Document doc = component.getDocument();
        int tail = 0;
        try {
            String after = doc.getText(startOffset + prefixLength,
                    Math.min(64, doc.getLength() - startOffset - prefixLength));
            tail = CssClassCompletionItem.tailLength(after);
        } catch (javax.swing.text.BadLocationException ignore) {
            // fall through with tail 0
        }
        if (org.nmox.studio.editor.completion.CompletionEdits.replace(
                doc, startOffset, prefixLength + tail, insert)) {
            component.setCaretPosition(startOffset + insert.length());
        }
        Completion.get().hideAll();
    }

    @Override
    public void processKeyEvent(KeyEvent evt) {
    }

    @Override
    public int getPreferredWidth(Graphics g, Font defaultFont) {
        return CompletionUtilities.getPreferredWidth(insert, note, g, defaultFont);
    }

    @Override
    public void render(Graphics g, Font defaultFont, Color defaultColor,
            Color backgroundColor, int width, int height, boolean selected) {
        CompletionUtilities.renderHtml(null, insert, note,
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
        return 80;
    }

    @Override
    public CharSequence getSortText() {
        return insert;
    }

    @Override
    public CharSequence getInsertPrefix() {
        return insert;
    }
}
