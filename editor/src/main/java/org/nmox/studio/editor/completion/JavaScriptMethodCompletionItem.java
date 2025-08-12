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

public class JavaScriptMethodCompletionItem implements CompletionItem {
    
    private final JavaScriptCompletionProvider.JavaScriptMethod method;
    private final int startOffset;
    private final int length;
    
    public JavaScriptMethodCompletionItem(JavaScriptCompletionProvider.JavaScriptMethod method, int startOffset, int length) {
        this.method = method;
        this.startOffset = startOffset;
        this.length = length;
    }
    
    @Override
    public void defaultAction(JTextComponent component) {
        try {
            StyledDocument doc = (StyledDocument) component.getDocument();
            
            // Remove the partial text
            doc.remove(startOffset, length);
            
            // Insert the method name with parentheses
            String insertion = method.name + "()";
            doc.insertString(startOffset, insertion, null);
            
            // Position cursor between parentheses
            component.setCaretPosition(startOffset + method.name.length() + 1);
            
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
            method.name + " " + method.signature, null, g, defaultFont);
    }
    
    @Override
    public void render(Graphics g, Font defaultFont, Color defaultColor, 
                      Color backgroundColor, int width, int height, boolean selected) {
        CompletionUtilities.renderHtml(
            null,
            method.name,
            "[method] " + method.signature,
            g,
            defaultFont,
            selected ? Color.WHITE : new Color(138, 43, 226),
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
        return method.name;
    }
    
    @Override
    public CharSequence getInsertPrefix() {
        return method.name;
    }
}