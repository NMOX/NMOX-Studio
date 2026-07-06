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

public class JavaScriptObjectCompletionItem implements CompletionItem {
    
    private final String objectName;
    private final int startOffset;
    private final int length;
    
    public JavaScriptObjectCompletionItem(String objectName, int startOffset, int length) {
        this.objectName = objectName;
        this.startOffset = startOffset;
        this.length = length;
    }
    
    @Override
    public void defaultAction(JTextComponent component) {
        // object name with the member dot, caret after it — member
        // completion re-triggers from there
        String insertion = objectName + ".";
        if (CompletionEdits.replace(component.getDocument(), startOffset, length, insertion)) {
            component.setCaretPosition(startOffset + insertion.length());
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
            objectName + " [object]", null, g, defaultFont);
    }
    
    @Override
    public void render(Graphics g, Font defaultFont, Color defaultColor, 
                      Color backgroundColor, int width, int height, boolean selected) {
        CompletionUtilities.renderHtml(
            null,
            objectName,
            "[object]",
            g,
            defaultFont,
            selected ? Color.WHITE : new Color(255, 140, 0),
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
        return 2; // Lower priority than keywords and methods
    }
    
    @Override
    public CharSequence getSortText() {
        return objectName;
    }
    
    @Override
    public CharSequence getInsertPrefix() {
        return objectName;
    }
}