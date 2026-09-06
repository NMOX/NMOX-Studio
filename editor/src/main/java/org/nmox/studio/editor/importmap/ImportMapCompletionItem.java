package org.nmox.studio.editor.importmap;

import org.nmox.studio.core.util.PlainText;
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
 * One mapped specifier in the import completion popup: the bare name
 * on the left, its mapped target on the right — the popup doubles as a
 * view of the page's import map. The target is PAGE CONTENT and the
 * renderer speaks HTML, so angle brackets are escaped at construction
 * (the v1.306.0 render law).
 */
public class ImportMapCompletionItem implements CompletionItem {

    private final String specifier;
    private final String target;
    private final int startOffset;
    private final int prefixLength;

    public ImportMapCompletionItem(String specifier, String target,
            int startOffset, int prefixLength) {
        this.specifier = specifier;
        this.target = PlainText.escape(target);
        this.startOffset = startOffset;
        this.prefixLength = prefixLength;
    }

    @Override
    public void defaultAction(JTextComponent component) {
        javax.swing.text.Document doc = component.getDocument();
        if (org.nmox.studio.editor.completion.CompletionEdits.replace(
                doc, startOffset, prefixLength, specifier)) {
            component.setCaretPosition(startOffset + specifier.length());
        }
        Completion.get().hideAll();
    }

    @Override
    public void processKeyEvent(KeyEvent evt) {
    }

    @Override
    public int getPreferredWidth(Graphics g, Font defaultFont) {
        return CompletionUtilities.getPreferredWidth(specifier, target, g, defaultFont);
    }

    @Override
    public void render(Graphics g, Font defaultFont, Color defaultColor,
            Color backgroundColor, int width, int height, boolean selected) {
        CompletionUtilities.renderHtml(null, specifier, target,
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
        return 85;
    }

    @Override
    public CharSequence getSortText() {
        return specifier;
    }

    @Override
    public CharSequence getInsertPrefix() {
        return specifier;
    }
}
