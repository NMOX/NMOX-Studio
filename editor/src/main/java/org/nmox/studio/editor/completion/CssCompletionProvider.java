package org.nmox.studio.editor.completion;

import java.util.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;
import org.openide.util.Exceptions;

public class CssCompletionProvider implements CompletionProvider {
    
    private static final Map<String, List<String>> CSS_PROPERTIES = new HashMap<>();
    private static final Map<String, List<String>> PROPERTY_VALUES = new HashMap<>();
    
    static {
        // Initialize CSS properties by category
        
        // Layout properties
        CSS_PROPERTIES.put("layout", Arrays.asList(
            "display", "position", "top", "right", "bottom", "left",
            "float", "clear", "overflow", "overflow-x", "overflow-y",
            "z-index", "visibility", "opacity"
        ));
        
        // Flexbox properties
        CSS_PROPERTIES.put("flexbox", Arrays.asList(
            "flex", "flex-direction", "flex-wrap", "flex-flow",
            "justify-content", "align-items", "align-content",
            "align-self", "flex-grow", "flex-shrink", "flex-basis",
            "order", "gap", "row-gap", "column-gap"
        ));
        
        // Grid properties
        CSS_PROPERTIES.put("grid", Arrays.asList(
            "display", "grid-template-columns", "grid-template-rows",
            "grid-template-areas", "grid-template", "grid-auto-columns",
            "grid-auto-rows", "grid-auto-flow", "grid", "grid-row-start",
            "grid-column-start", "grid-row-end", "grid-column-end",
            "grid-row", "grid-column", "grid-area", "justify-self",
            "place-items", "place-content", "place-self"
        ));
        
        // Box model properties
        CSS_PROPERTIES.put("box", Arrays.asList(
            "width", "height", "min-width", "max-width", "min-height", "max-height",
            "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
            "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
            "border", "border-width", "border-style", "border-color",
            "border-top", "border-right", "border-bottom", "border-left",
            "border-radius", "box-sizing", "box-shadow"
        ));
        
        // Typography properties
        CSS_PROPERTIES.put("typography", Arrays.asList(
            "font", "font-family", "font-size", "font-weight", "font-style",
            "font-variant", "line-height", "letter-spacing", "word-spacing",
            "text-align", "text-decoration", "text-transform", "text-indent",
            "text-shadow", "white-space", "word-wrap", "word-break",
            "text-overflow", "vertical-align"
        ));
        
        // Color and background properties
        CSS_PROPERTIES.put("color", Arrays.asList(
            "color", "background", "background-color", "background-image",
            "background-repeat", "background-position", "background-size",
            "background-attachment", "background-clip", "background-origin",
            "background-blend-mode", "mix-blend-mode"
        ));
        
        // Animation and transition properties
        CSS_PROPERTIES.put("animation", Arrays.asList(
            "animation", "animation-name", "animation-duration",
            "animation-timing-function", "animation-delay",
            "animation-iteration-count", "animation-direction",
            "animation-fill-mode", "animation-play-state",
            "transition", "transition-property", "transition-duration",
            "transition-timing-function", "transition-delay",
            "transform", "transform-origin", "transform-style",
            "perspective", "perspective-origin", "backface-visibility"
        ));
        
        // Initialize common property values
        PROPERTY_VALUES.put("display", Arrays.asList(
            "none", "block", "inline", "inline-block", "flex", "inline-flex",
            "grid", "inline-grid", "table", "table-cell", "table-row",
            "list-item", "contents", "flow-root"
        ));
        
        PROPERTY_VALUES.put("position", Arrays.asList(
            "static", "relative", "absolute", "fixed", "sticky"
        ));
        
        PROPERTY_VALUES.put("flex-direction", Arrays.asList(
            "row", "row-reverse", "column", "column-reverse"
        ));
        
        PROPERTY_VALUES.put("justify-content", Arrays.asList(
            "flex-start", "flex-end", "center", "space-between",
            "space-around", "space-evenly", "start", "end"
        ));
        
        PROPERTY_VALUES.put("align-items", Arrays.asList(
            "stretch", "flex-start", "flex-end", "center", "baseline"
        ));
        
        PROPERTY_VALUES.put("font-weight", Arrays.asList(
            "normal", "bold", "bolder", "lighter",
            "100", "200", "300", "400", "500", "600", "700", "800", "900"
        ));
        
        PROPERTY_VALUES.put("text-align", Arrays.asList(
            "left", "right", "center", "justify", "start", "end"
        ));
        
        PROPERTY_VALUES.put("overflow", Arrays.asList(
            "visible", "hidden", "scroll", "auto", "clip"
        ));
        
        PROPERTY_VALUES.put("cursor", Arrays.asList(
            "auto", "default", "none", "context-menu", "help", "pointer",
            "progress", "wait", "cell", "crosshair", "text", "vertical-text",
            "alias", "copy", "move", "no-drop", "not-allowed", "grab", "grabbing",
            "all-scroll", "col-resize", "row-resize", "n-resize", "e-resize",
            "s-resize", "w-resize", "ne-resize", "nw-resize", "se-resize", "sw-resize"
        ));
    }
    
    @Override
    public CompletionTask createTask(int queryType, JTextComponent component) {
        if (queryType != CompletionProvider.COMPLETION_QUERY_TYPE) {
            return null;
        }
        return new AsyncCompletionTask(new CssCompletionQuery(), component);
    }
    
    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
        if (typedText.length() == 1) {
            char ch = typedText.charAt(0);
            if (ch == ':' || ch == ';' || ch == '{' || Character.isLetter(ch)) {
                return CompletionProvider.COMPLETION_QUERY_TYPE;
            }
        }
        return 0;
    }
    
    private static class CssCompletionQuery extends AsyncCompletionQuery {
        
        @Override
        protected void query(CompletionResultSet resultSet, Document doc, int caretOffset) {
            try {
                CssContext context = analyzeContext(doc, caretOffset);
                
                switch (context.type) {
                    case PROPERTY_NAME:
                        addPropertyCompletions(resultSet, context, caretOffset);
                        break;
                    case PROPERTY_VALUE:
                        addValueCompletions(resultSet, context, caretOffset);
                        break;
                    case SELECTOR:
                        addSelectorCompletions(resultSet, context, caretOffset);
                        break;
                }
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                resultSet.finish();
            }
        }
        
        private CssContext analyzeContext(Document doc, int offset) throws BadLocationException {
            String text = doc.getText(0, offset);
            CssContext context = new CssContext();
            
            // Find context within CSS
            int lastBrace = text.lastIndexOf('{');
            int lastCloseBrace = text.lastIndexOf('}');
            int lastColon = text.lastIndexOf(':');
            int lastSemicolon = text.lastIndexOf(';');
            
            if (lastBrace > lastCloseBrace) {
                // We're inside a rule block
                if (lastColon > lastSemicolon && lastColon > lastBrace) {
                    // We're in a property value
                    context.type = CssContextType.PROPERTY_VALUE;
                    String propLine = text.substring(Math.max(lastSemicolon, lastBrace) + 1, lastColon).trim();
                    context.propertyName = propLine;
                    context.prefix = text.substring(lastColon + 1).trim();
                } else {
                    // We're typing a property name
                    context.type = CssContextType.PROPERTY_NAME;
                    int start = Math.max(lastSemicolon, lastBrace) + 1;
                    context.prefix = text.substring(start).trim();
                }
            } else {
                // We're outside a rule block (selector area)
                context.type = CssContextType.SELECTOR;
                int start = Math.max(lastCloseBrace, 0) + 1;
                context.prefix = text.substring(start).trim();
            }
            
            return context;
        }
        
        private void addPropertyCompletions(CompletionResultSet resultSet, CssContext context, int caretOffset) {
            String prefix = context.prefix.toLowerCase();
            Set<String> allProperties = new HashSet<>();
            
            // Collect all properties
            for (List<String> props : CSS_PROPERTIES.values()) {
                allProperties.addAll(props);
            }
            
            // Filter and add matching properties
            for (String property : allProperties) {
                if (property.startsWith(prefix)) {
                    resultSet.addItem(new CssPropertyCompletionItem(
                        property, caretOffset - prefix.length(), prefix.length()));
                }
            }
        }
        
        private void addValueCompletions(CompletionResultSet resultSet, CssContext context, int caretOffset) {
            String prefix = context.prefix.toLowerCase();
            
            // Get predefined values for this property
            if (context.propertyName != null && PROPERTY_VALUES.containsKey(context.propertyName)) {
                for (String value : PROPERTY_VALUES.get(context.propertyName)) {
                    if (value.startsWith(prefix)) {
                        resultSet.addItem(new CssValueCompletionItem(
                            value, caretOffset - prefix.length(), prefix.length()));
                    }
                }
            }
            
            // Add common values
            List<String> commonValues = Arrays.asList(
                "inherit", "initial", "unset", "revert", "auto", "none"
            );
            
            for (String value : commonValues) {
                if (value.startsWith(prefix)) {
                    resultSet.addItem(new CssValueCompletionItem(
                        value, caretOffset - prefix.length(), prefix.length()));
                }
            }
        }
        
        private void addSelectorCompletions(CompletionResultSet resultSet, CssContext context, int caretOffset) {
            String prefix = context.prefix;
            
            // Add common selectors
            List<String> selectors = Arrays.asList(
                "*", "body", "html", "div", "span", "p", "a", "h1", "h2", "h3",
                ".class", "#id", ":hover", ":focus", ":active", ":visited",
                "::before", "::after", ":first-child", ":last-child", ":nth-child()"
            );
            
            for (String selector : selectors) {
                if (selector.startsWith(prefix)) {
                    resultSet.addItem(new CssSelectorCompletionItem(
                        selector, caretOffset - prefix.length(), prefix.length()));
                }
            }
        }
    }
    
    private static class CssContext {
        CssContextType type = CssContextType.SELECTOR;
        String propertyName = null;
        String prefix = "";
    }
    
    private enum CssContextType {
        SELECTOR, PROPERTY_NAME, PROPERTY_VALUE
    }
}