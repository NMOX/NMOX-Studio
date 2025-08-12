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

public class JavaScriptSnippetCompletionItem implements CompletionItem {
    
    private final JavaScriptCompletionProvider.JavaScriptSnippet snippet;
    private final int startOffset;
    private final int length;
    
    public JavaScriptSnippetCompletionItem(JavaScriptCompletionProvider.JavaScriptSnippet snippet, int startOffset, int length) {
        this.snippet = snippet;
        this.startOffset = startOffset;
        this.length = length;
    }
    
    @Override
    public void defaultAction(JTextComponent component) {
        try {
            StyledDocument doc = (StyledDocument) component.getDocument();
            
            // Remove the partial text
            doc.remove(startOffset, length);
            
            // Process the snippet code to expand template variables
            String expandedCode = expandSnippet(snippet.code);
            
            // Insert the expanded snippet
            doc.insertString(startOffset, expandedCode, null);
            
            // Find first placeholder position for cursor placement
            int firstPlaceholder = findFirstPlaceholder(expandedCode);
            if (firstPlaceholder >= 0) {
                component.setCaretPosition(startOffset + firstPlaceholder);
            } else {
                component.setCaretPosition(startOffset + expandedCode.length());
            }
            
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
        
        Completion.get().hideAll();
    }
    
    private String expandSnippet(String template) {
        // Simple template expansion - replace ${n:placeholder} with placeholder text
        String result = template;
        
        // Replace numbered placeholders with their default text
        result = result.replaceAll("\\$\\{\\d+:([^}]*)\\}", "$1");
        
        // Replace simple numbered placeholders with empty string
        result = result.replaceAll("\\$\\{\\d+\\}", "");
        
        return result;
    }
    
    private int findFirstPlaceholder(String code) {
        // Find position where first placeholder would be for cursor positioning
        // This is a simplified implementation - in a real IDE this would be more sophisticated
        int braceIndex = code.indexOf('{');
        if (braceIndex >= 0) {
            return braceIndex + 1;
        }
        
        int parenIndex = code.indexOf('(');
        if (parenIndex >= 0) {
            return parenIndex + 1;
        }
        
        return -1;
    }
    
    @Override
    public void processKeyEvent(KeyEvent evt) {
        // Default processing
    }
    
    @Override
    public int getPreferredWidth(Graphics g, Font defaultFont) {
        return CompletionUtilities.getPreferredWidth(
            snippet.trigger + " " + snippet.description, null, g, defaultFont);
    }
    
    @Override
    public void render(Graphics g, Font defaultFont, Color defaultColor, 
                      Color backgroundColor, int width, int height, boolean selected) {
        CompletionUtilities.renderHtml(
            null,
            snippet.trigger,
            "[snippet] " + snippet.description,
            g,
            defaultFont,
            selected ? Color.WHITE : new Color(34, 139, 34),
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
        return 3; // Lowest priority - snippets shown last
    }
    
    @Override
    public CharSequence getSortText() {
        return snippet.trigger;
    }
    
    @Override
    public CharSequence getInsertPrefix() {
        return snippet.trigger;
    }
}