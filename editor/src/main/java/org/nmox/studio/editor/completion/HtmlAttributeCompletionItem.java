package org.nmox.studio.editor.completion;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;
import org.openide.util.Exceptions;

public class HtmlAttributeCompletionItem implements CompletionItem {
    
    private final String attributeName;
    private final int startOffset;
    private final int length;
    
    public HtmlAttributeCompletionItem(String attributeName, int startOffset, int length) {
        this.attributeName = attributeName;
        this.startOffset = startOffset;
        this.length = length;
    }
    
    @Override
    public void defaultAction(JTextComponent component) {
        try {
            StyledDocument doc = (StyledDocument) component.getDocument();
            
            // Remove the partial text
            doc.remove(startOffset, length);
            
            // Insert the attribute with empty value
            String insertion = attributeName + "=\"\"";
            doc.insertString(startOffset, insertion, null);
            
            // Position cursor between quotes
            component.setCaretPosition(startOffset + attributeName.length() + 2);
            
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
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
            attributeName + " [attribute]", null, g, defaultFont);
    }
    
    @Override
    public void render(Graphics g, Font defaultFont, Color defaultColor, 
                      Color backgroundColor, int width, int height, boolean selected) {
        CompletionUtilities.renderHtml(
            null,
            attributeName,
            "[attr]",
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
        return 0;
    }
    
    @Override
    public CharSequence getSortText() {
        return attributeName;
    }
    
    @Override
    public CharSequence getInsertPrefix() {
        return attributeName;
    }
}