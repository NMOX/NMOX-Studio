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

@org.netbeans.api.editor.mimelookup.MimeRegistrations({
    @org.netbeans.api.editor.mimelookup.MimeRegistration(
            mimeType = "text/css", service = CompletionProvider.class),
    @org.netbeans.api.editor.mimelookup.MimeRegistration(
            mimeType = "text/x-scss", service = CompletionProvider.class),
    @org.netbeans.api.editor.mimelookup.MimeRegistration(
            mimeType = "text/x-less", service = CompletionProvider.class)
})
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

        PROPERTY_VALUES.put("border-style", Arrays.asList(
            "none", "solid", "dashed", "dotted", "double", "groove",
            "ridge", "inset", "outset", "hidden"
        ));

        PROPERTY_VALUES.put("flex-wrap", Arrays.asList(
            "nowrap", "wrap", "wrap-reverse"
        ));

        PROPERTY_VALUES.put("align-content", Arrays.asList(
            "stretch", "flex-start", "flex-end", "center",
            "space-between", "space-around", "space-evenly"
        ));

        PROPERTY_VALUES.put("align-self", Arrays.asList(
            "auto", "stretch", "flex-start", "flex-end", "center", "baseline"
        ));

        PROPERTY_VALUES.put("text-decoration", Arrays.asList(
            "none", "underline", "overline", "line-through"
        ));

        PROPERTY_VALUES.put("text-transform", Arrays.asList(
            "none", "capitalize", "uppercase", "lowercase"
        ));

        PROPERTY_VALUES.put("font-style", Arrays.asList(
            "normal", "italic", "oblique"
        ));

        PROPERTY_VALUES.put("white-space", Arrays.asList(
            "normal", "nowrap", "pre", "pre-wrap", "pre-line", "break-spaces"
        ));

        PROPERTY_VALUES.put("word-break", Arrays.asList(
            "normal", "break-all", "keep-all", "break-word"
        ));

        PROPERTY_VALUES.put("text-overflow", Arrays.asList(
            "clip", "ellipsis"
        ));

        PROPERTY_VALUES.put("vertical-align", Arrays.asList(
            "baseline", "top", "middle", "bottom", "text-top", "text-bottom",
            "sub", "super"
        ));

        PROPERTY_VALUES.put("visibility", Arrays.asList(
            "visible", "hidden", "collapse"
        ));

        PROPERTY_VALUES.put("box-sizing", Arrays.asList(
            "content-box", "border-box"
        ));

        PROPERTY_VALUES.put("float", Arrays.asList(
            "none", "left", "right", "inline-start", "inline-end"
        ));

        PROPERTY_VALUES.put("clear", Arrays.asList(
            "none", "left", "right", "both", "inline-start", "inline-end"
        ));

        PROPERTY_VALUES.put("background-repeat", Arrays.asList(
            "repeat", "repeat-x", "repeat-y", "no-repeat", "space", "round"
        ));

        PROPERTY_VALUES.put("background-size", Arrays.asList(
            "auto", "cover", "contain"
        ));

        PROPERTY_VALUES.put("background-attachment", Arrays.asList(
            "scroll", "fixed", "local"
        ));

        PROPERTY_VALUES.put("background-position", Arrays.asList(
            "top", "bottom", "left", "right", "center"
        ));

        PROPERTY_VALUES.put("object-fit", Arrays.asList(
            "fill", "contain", "cover", "none", "scale-down"
        ));

        PROPERTY_VALUES.put("pointer-events", Arrays.asList(
            "auto", "none"
        ));

        PROPERTY_VALUES.put("user-select", Arrays.asList(
            "auto", "none", "text", "all"
        ));

        PROPERTY_VALUES.put("animation-direction", Arrays.asList(
            "normal", "reverse", "alternate", "alternate-reverse"
        ));

        PROPERTY_VALUES.put("animation-fill-mode", Arrays.asList(
            "none", "forwards", "backwards", "both"
        ));

        PROPERTY_VALUES.put("animation-timing-function", Arrays.asList(
            "ease", "ease-in", "ease-out", "ease-in-out", "linear",
            "step-start", "step-end"
        ));

        PROPERTY_VALUES.put("transition-timing-function", Arrays.asList(
            "ease", "ease-in", "ease-out", "ease-in-out", "linear",
            "step-start", "step-end"
        ));

        PROPERTY_VALUES.put("overflow-x", Arrays.asList(
            "visible", "hidden", "scroll", "auto", "clip"
        ));

        PROPERTY_VALUES.put("overflow-y", Arrays.asList(
            "visible", "hidden", "scroll", "auto", "clip"
        ));

        PROPERTY_VALUES.put("font-variant", Arrays.asList(
            "normal", "small-caps"
        ));

        PROPERTY_VALUES.put("list-style-type", Arrays.asList(
            "none", "disc", "circle", "square", "decimal",
            "lower-alpha", "upper-alpha", "lower-roman", "upper-roman"
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
    
    /** Common CSS-wide keyword values offered for every property. */
    static final List<String> COMMON_VALUES = Arrays.asList(
        "inherit", "initial", "unset", "revert", "auto", "none");

    /** Common selectors offered in the selector area. */
    static final List<String> COMMON_SELECTORS = Arrays.asList(
        "*", "body", "html", "div", "span", "p", "a", "h1", "h2", "h3",
        ".class", "#id", ":hover", ":focus", ":active", ":visited",
        "::before", "::after", ":first-child", ":last-child", ":nth-child()");

    /**
     * Where the caret sits in the stylesheet, derived from the text
     * before it. Package-visible and pure so the context rules are
     * testable without a live document.
     */
    static CssContext analyzeContext(String text) {
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

    /** Property names (any category) whose name starts with the prefix, sorted. */
    static List<String> matchingProperties(String prefix) {
        String p = prefix.toLowerCase();
        Set<String> allProperties = new TreeSet<>();
        for (List<String> props : CSS_PROPERTIES.values()) {
            allProperties.addAll(props);
        }
        List<String> out = new ArrayList<>();
        for (String property : allProperties) {
            if (property.startsWith(p)) {
                out.add(property);
            }
        }
        return out;
    }

    /**
     * Values offered for a property: the property's enumerated values (if
     * any) followed by the CSS-wide common keywords, all prefix-filtered.
     */
    static List<String> matchingValues(String propertyName, String prefix) {
        String p = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        if (propertyName != null && PROPERTY_VALUES.containsKey(propertyName)) {
            for (String value : PROPERTY_VALUES.get(propertyName)) {
                if (value.startsWith(p)) {
                    out.add(value);
                }
            }
        }
        for (String value : COMMON_VALUES) {
            if (value.startsWith(p)) {
                out.add(value);
            }
        }
        return out;
    }

    /** Common selectors whose text starts with the (case-sensitive) prefix. */
    static List<String> matchingSelectors(String prefix) {
        List<String> out = new ArrayList<>();
        for (String selector : COMMON_SELECTORS) {
            if (selector.startsWith(prefix)) {
                out.add(selector);
            }
        }
        return out;
    }

    private static class CssCompletionQuery extends AsyncCompletionQuery {

        @Override
        protected void query(CompletionResultSet resultSet, Document doc, int caretOffset) {
            try {
                CssContext context = analyzeContext(doc.getText(0, caretOffset));

                switch (context.type) {
                    case PROPERTY_NAME:
                        for (String property : matchingProperties(context.prefix)) {
                            resultSet.addItem(new CssPropertyCompletionItem(
                                property, caretOffset - context.prefix.length(), context.prefix.length()));
                        }
                        break;
                    case PROPERTY_VALUE:
                        for (String value : matchingValues(context.propertyName, context.prefix)) {
                            resultSet.addItem(new CssValueCompletionItem(
                                value, caretOffset - context.prefix.length(), context.prefix.length()));
                        }
                        break;
                    case SELECTOR:
                        for (String selector : matchingSelectors(context.prefix)) {
                            resultSet.addItem(new CssSelectorCompletionItem(
                                selector, caretOffset - context.prefix.length(), context.prefix.length()));
                        }
                        break;
                }
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                resultSet.finish();
            }
        }
    }

    static class CssContext {
        CssContextType type = CssContextType.SELECTOR;
        String propertyName = null;
        String prefix = "";
    }

    enum CssContextType {
        SELECTOR, PROPERTY_NAME, PROPERTY_VALUE
    }
}