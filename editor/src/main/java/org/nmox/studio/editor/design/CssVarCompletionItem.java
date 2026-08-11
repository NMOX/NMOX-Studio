package org.nmox.studio.editor.design;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.netbeans.api.editor.completion.Completion;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;

/**
 * One design token in the completion popup (v1.330.0): the name on the
 * left — behind a swatch when its value is a color — and the value on
 * the right, so the popup doubles as a legend of the design system.
 * Accepting inserts the name and closes the {@code var(} call when the
 * closing paren is not already there.
 */
public class CssVarCompletionItem implements CompletionItem {

    private final String name;
    private final String value;
    private final int startOffset;
    private final int prefixLength;
    private final Color swatch;

    public CssVarCompletionItem(String name, String value,
            int startOffset, int prefixLength) {
        this.name = name;
        this.value = value;
        this.startOffset = startOffset;
        this.prefixLength = prefixLength;
        List<CssColors.ColorSpan> colors = CssColors.scan(value);
        this.swatch = colors.size() == 1 ? colors.get(0).color() : null;
    }

    @Override
    public void defaultAction(JTextComponent component) {
        try {
            Document doc = component.getDocument();
            String after = doc.getText(startOffset + prefixLength,
                    Math.min(1, doc.getLength() - startOffset - prefixLength));
            String insertion = name + (after.startsWith(")") ? "" : ")");
            // the ONE splice every completion item performs (v1.333.0
            // review: this item hand-rolled the remove+insert and would
            // have repositioned the caret on stale offsets when the
            // document moved under the pick — CompletionEdits is the
            // house law, its false return the skip signal)
            if (org.nmox.studio.editor.completion.CompletionEdits.replace(
                    doc, startOffset, prefixLength, insertion)) {
                component.setCaretPosition(startOffset + insertion.length()
                        + (after.startsWith(")") ? 1 : 0));
            }
        } catch (BadLocationException ignore) {
            // the probe read raced an edit; offer nothing
        }
        Completion.get().hideAll();
    }

    @Override
    public void processKeyEvent(KeyEvent evt) {
    }

    @Override
    public int getPreferredWidth(Graphics g, Font defaultFont) {
        return CompletionUtilities.getPreferredWidth(name, value, g, defaultFont);
    }

    @Override
    public void render(Graphics g, Font defaultFont, Color defaultColor,
            Color backgroundColor, int width, int height, boolean selected) {
        CompletionUtilities.renderHtml(icon(), name, value, g, defaultFont,
                selected ? Color.WHITE : defaultColor, width, height, selected);
    }

    /** A 12×12 solid swatch of the token's color; null for non-colors. */
    private ImageIcon icon() {
        if (swatch == null) {
            return null;
        }
        BufferedImage img = new BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB);
        Graphics g = img.getGraphics();
        g.setColor(new Color(swatch.getRed(), swatch.getGreen(), swatch.getBlue()));
        g.fillRect(0, 0, 12, 12);
        g.setColor(Color.GRAY);
        g.drawRect(0, 0, 11, 11);
        g.dispose();
        return new ImageIcon(img);
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
        return 5;                 // tokens above the generic value tables
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
