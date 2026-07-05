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

public class CssSelectorCompletionItem implements CompletionItem {
    
    private final String selector;
    private final int startOffset;
    private final int length;
    
    public CssSelectorCompletionItem(String selector, int startOffset, int length) {
        this.selector = selector;
        this.startOffset = startOffset;
        this.length = length;
    }
    
    @Override
    public void defaultAction(JTextComponent component) {
        // selector with an opened rule block, caret on the indented line
        if (CompletionEdits.replace(component.getDocument(), startOffset, length,
                selector + " {\n    \n}")) {
            component.setCaretPosition(startOffset + selector.length() + 6);
        }
        Completion.get().hideAll();
    }
    
    @Override
    public void processKeyEvent(KeyEvent evt) {
        // Default processing
    }
    
    @Override
    public int getPreferredWidth(Graphics g, Font defaultFont) {
        return CompletionUtilities.getPreferredWidth(
            selector + " [selector]", null, g, defaultFont);
    }
    
    @Override
    public void render(Graphics g, Font defaultFont, Color defaultColor, 
                      Color backgroundColor, int width, int height, boolean selected) {
        CompletionUtilities.renderHtml(
            null,
            selector,
            "[sel]",
            g,
            defaultFont,
            selected ? Color.WHITE : new Color(0, 150, 0),
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
        return 0;
    }
    
    @Override
    public CharSequence getSortText() {
        return selector;
    }
    
    @Override
    public CharSequence getInsertPrefix() {
        return selector;
    }
}