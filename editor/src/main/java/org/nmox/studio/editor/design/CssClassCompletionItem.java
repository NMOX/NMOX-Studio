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
        if (org.nmox.studio.editor.completion.CompletionEdits.replace(
                component.getDocument(), startOffset, prefixLength, name)) {
            component.setCaretPosition(startOffset + name.length());
        }
        Completion.get().hideAll();
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
