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

public class HtmlTagCompletionItem implements CompletionItem {
    
    private final String tagName;
    private final int startOffset;
    private final int length;
    private final boolean isVoid;
    
    public HtmlTagCompletionItem(String tagName, int startOffset, int length, boolean isVoid) {
        this.tagName = tagName;
        this.startOffset = startOffset;
        this.length = length;
        this.isVoid = isVoid;
    }
    
    @Override
    public void defaultAction(JTextComponent component) {
        try {
            StyledDocument doc = (StyledDocument) component.getDocument();
            
            // Remove the partial text
            doc.remove(startOffset, length);
            
            // Insert the tag
            String insertion;
            int newCaretPos;
            
            if (isVoid) {
                // Self-closing tag
                insertion = tagName + " />";
                newCaretPos = startOffset + tagName.length() + 1;
            } else {
                // Regular tag with closing tag
                insertion = tagName + "></" + tagName + ">";
                newCaretPos = startOffset + tagName.length() + 1;
            }
            
            doc.insertString(startOffset, insertion, null);
            component.setCaretPosition(newCaretPos);
            
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
            tagName + " <" + tagName + ">", null, g, defaultFont);
    }
    
    @Override
    public void render(Graphics g, Font defaultFont, Color defaultColor, 
                      Color backgroundColor, int width, int height, boolean selected) {
        CompletionUtilities.renderHtml(
            null,
            tagName,
            isVoid ? "[void]" : "[tag]",
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
        return tagName;
    }
    
    @Override
    public CharSequence getInsertPrefix() {
        return tagName;
    }
}