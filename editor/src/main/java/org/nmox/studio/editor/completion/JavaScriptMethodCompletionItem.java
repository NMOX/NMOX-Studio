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
 * One row in the JS/TS completion popup offering a known method after a
 * dot ({@code console.} → log, error, …). Carries the method's signature
 * and one-line description for the popup's right-hand column, and per
 * the {@link CompletionItem} contract performs its own edit on accept:
 * the name plus call parentheses, caret placed between them ready for
 * arguments. Constructed per member-mode query by
 * {@link JavaScriptCompletionProvider} from its global-object tables.
 */
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
        // call parentheses included, caret between them
        if (CompletionEdits.replace(component.getDocument(), startOffset, length,
                method.name + "()")) {
            component.setCaretPosition(startOffset + method.name.length() + 1);
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