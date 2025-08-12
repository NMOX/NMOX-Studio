package org.nmox.studio.editor.javascript;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import javax.swing.ImageIcon;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;

/**
 * Provides code completion for JavaScript files.
 * This is a simple implementation with common JavaScript snippets.
 */
@MimeRegistration(mimeType = "text/javascript", service = CompletionProvider.class)
public class JavaScriptCompletionProvider implements CompletionProvider {

    // Common JavaScript snippets
    private static final String[] KEYWORDS = {
        "const", "let", "var", "function", "if", "else", "for", "while", "do",
        "switch", "case", "break", "continue", "return", "try", "catch", "finally",
        "throw", "async", "await", "class", "extends", "import", "export", "default",
        "new", "this", "super", "typeof", "instanceof", "delete", "void", "yield"
    };
    
    private static final String[] SNIPPETS = {
        "console.log()", "console.error()", "console.warn()", "console.info()",
        "document.getElementById()", "document.querySelector()", 
        "addEventListener()", "setTimeout()", "setInterval()",
        "Promise.resolve()", "Promise.reject()", "Promise.all()",
        "JSON.parse()", "JSON.stringify()",
        "Array.from()", "Object.keys()", "Object.values()", "Object.entries()",
        "Math.random()", "Math.floor()", "Math.ceil()", "Math.round()",
        "Date.now()", "new Date()"
    };
    
    private static final String[] TEMPLATES = {
        "if (condition) {\n    \n}",
        "for (let i = 0; i < array.length; i++) {\n    \n}",
        "for (const item of array) {\n    \n}",
        "for (const key in object) {\n    \n}",
        "function name(params) {\n    \n}",
        "const name = () => {\n    \n}",
        "try {\n    \n} catch (error) {\n    \n}",
        "class ClassName {\n    constructor() {\n        \n    }\n}",
        "async function name() {\n    \n}",
        "import { } from ''",
        "export default ",
        "export { }"
    };

    @Override
    public CompletionTask createTask(int queryType, JTextComponent component) {
        if (queryType != CompletionProvider.COMPLETION_QUERY_TYPE) {
            return null;
        }
        
        return new AsyncCompletionTask(new AsyncCompletionQuery() {
            @Override
            protected void query(CompletionResultSet resultSet, Document doc, int caretOffset) {
                try {
                    // Get the text before the caret
                    String prefix = getPrefix(doc, caretOffset);
                    
                    // Add matching keywords
                    for (String keyword : KEYWORDS) {
                        if (keyword.startsWith(prefix.toLowerCase())) {
                            resultSet.addItem(new JavaScriptCompletionItem(keyword, caretOffset - prefix.length(), prefix.length(), CompletionType.KEYWORD));
                        }
                    }
                    
                    // Add matching snippets
                    for (String snippet : SNIPPETS) {
                        if (snippet.toLowerCase().startsWith(prefix.toLowerCase())) {
                            resultSet.addItem(new JavaScriptCompletionItem(snippet, caretOffset - prefix.length(), prefix.length(), CompletionType.SNIPPET));
                        }
                    }
                    
                    // Add templates if prefix is empty or very short
                    if (prefix.length() <= 2) {
                        for (String template : TEMPLATES) {
                            resultSet.addItem(new JavaScriptCompletionItem(template, caretOffset - prefix.length(), prefix.length(), CompletionType.TEMPLATE));
                        }
                    }
                    
                } catch (BadLocationException ex) {
                    // Ignore
                } finally {
                    resultSet.finish();
                }
            }
            
            private String getPrefix(Document doc, int offset) throws BadLocationException {
                int start = offset;
                String text = doc.getText(0, offset);
                
                // Find the start of the current word
                while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
                    start--;
                }
                
                return text.substring(start, offset);
            }
        }, component);
    }

    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
        // Trigger completion on dot or after typing 2 characters
        if (".".equals(typedText) || typedText.length() == 1 && Character.isJavaIdentifierPart(typedText.charAt(0))) {
            return COMPLETION_QUERY_TYPE;
        }
        return 0;
    }
    
    /**
     * Completion item implementation
     */
    private static class JavaScriptCompletionItem implements CompletionItem {
        private final String text;
        private final int startOffset;
        private final int length;
        private final CompletionType type;
        
        public JavaScriptCompletionItem(String text, int startOffset, int length, CompletionType type) {
            this.text = text;
            this.startOffset = startOffset;
            this.length = length;
            this.type = type;
        }

        @Override
        public void defaultAction(JTextComponent component) {
            try {
                StyledDocument doc = (StyledDocument) component.getDocument();
                doc.remove(startOffset, length);
                doc.insertString(startOffset, text, null);
                
                // Position cursor intelligently for snippets
                if (text.contains("()")) {
                    component.setCaretPosition(startOffset + text.indexOf("(") + 1);
                } else if (text.contains("{\n")) {
                    component.setCaretPosition(startOffset + text.indexOf("\n") + 1);
                } else {
                    component.setCaretPosition(startOffset + text.length());
                }
            } catch (BadLocationException ex) {
                // Ignore
            }
        }

        @Override
        public void processKeyEvent(KeyEvent evt) {
            // Default processing
        }

        @Override
        public int getPreferredWidth(Graphics g, Font defaultFont) {
            return CompletionUtilities.getPreferredWidth(getLeftText(), getRightText(), g, defaultFont);
        }

        @Override
        public void render(Graphics g, Font defaultFont, Color defaultColor, Color backgroundColor, int width, int height, boolean selected) {
            CompletionUtilities.renderHtml(null, getLeftText(), getRightText(), g, defaultFont, 
                    selected ? Color.WHITE : getColor(), width, height, selected);
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
            switch (type) {
                case KEYWORD:
                    return 100;
                case SNIPPET:
                    return 200;
                case TEMPLATE:
                    return 300;
                default:
                    return 1000;
            }
        }

        @Override
        public CharSequence getSortText() {
            return text;
        }

        @Override
        public CharSequence getInsertPrefix() {
            return text;
        }
        
        private String getLeftText() {
            return text;
        }
        
        private String getRightText() {
            return type.toString();
        }
        
        private Color getColor() {
            switch (type) {
                case KEYWORD:
                    return new Color(86, 156, 214); // Blue
                case SNIPPET:
                    return new Color(220, 220, 170); // Yellow
                case TEMPLATE:
                    return new Color(78, 201, 176); // Green
                default:
                    return Color.GRAY;
            }
        }
    }
    
    private enum CompletionType {
        KEYWORD, SNIPPET, TEMPLATE
    }
}