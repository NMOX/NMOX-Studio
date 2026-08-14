package org.nmox.studio.editor.completion;

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
 * One enumerated attribute value, inserted as plain text inside the
 * quotes — the row you see when completing {@code type="…"} on an
 * {@code <input>}. This is the smallest useful example of the
 * platform's {@code CompletionItem} contract: the provider creates one
 * per candidate, {@code defaultAction} replaces the document span from
 * {@code startOffset} to the caret, and
 * {@code CompletionUtilities.renderHtml} paints the row. If you are
 * writing a new completion, copy this shape before the fancier items.
 */
public class HtmlAttributeValueCompletionItem implements CompletionItem {

    private final String value;
    private final int startOffset;
    private final int length;

    public HtmlAttributeValueCompletionItem(String value, int startOffset, int length) {
        this.value = value;
        this.startOffset = startOffset;
        this.length = length;
    }

    @Override
    public void defaultAction(JTextComponent component) {
        CompletionEdits.replace(component.getDocument(), startOffset, length, value);
        Completion.get().hideAll();
    }

    @Override
    public void processKeyEvent(KeyEvent evt) {
        // Default processing
    }

    @Override
    public int getPreferredWidth(Graphics g, Font defaultFont) {
        return CompletionUtilities.getPreferredWidth(value + " [value]", null, g, defaultFont);
    }

    @Override
    public void render(Graphics g, Font defaultFont, Color defaultColor,
                      Color backgroundColor, int width, int height, boolean selected) {
        CompletionUtilities.renderHtml(
            null,
            value,
            "[value]",
            g,
            defaultFont,
            selected ? Color.WHITE : defaultColor,
            width,
            height,
            selected
        );
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
        return 50;
    }

    @Override
    public CharSequence getSortText() {
        return value;
    }

    @Override
    public CharSequence getInsertPrefix() {
        return value;
    }
}
