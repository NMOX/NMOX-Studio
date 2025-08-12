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

public class HtmlCompletionProvider implements CompletionProvider {
    
    private static final Map<String, List<String>> TAG_ATTRIBUTES = new HashMap<>();
    private static final Set<String> VOID_ELEMENTS = new HashSet<>();
    private static final Set<String> HTML_TAGS = new HashSet<>();
    
    static {
        // Initialize HTML5 tags
        HTML_TAGS.addAll(Arrays.asList(
            // Document
            "html", "head", "body", "title", "meta", "link", "script", "style", "base",
            // Sections
            "header", "footer", "nav", "main", "section", "article", "aside", "div", "span",
            // Headings
            "h1", "h2", "h3", "h4", "h5", "h6", "hgroup",
            // Text
            "p", "hr", "pre", "blockquote", "ol", "ul", "li", "dl", "dt", "dd",
            "figure", "figcaption", "address", "time", "mark", "details", "summary",
            // Inline text
            "a", "em", "strong", "small", "s", "cite", "q", "dfn", "abbr", "data",
            "code", "var", "samp", "kbd", "sub", "sup", "i", "b", "u", "bdi", "bdo",
            "ruby", "rt", "rp", "wbr",
            // Forms
            "form", "input", "button", "select", "optgroup", "option", "textarea",
            "label", "fieldset", "legend", "datalist", "output", "progress", "meter",
            // Tables
            "table", "caption", "colgroup", "col", "tbody", "thead", "tfoot", "tr", "td", "th",
            // Media
            "img", "picture", "source", "video", "audio", "track", "map", "area", "canvas",
            // Embedded
            "iframe", "embed", "object", "param", "portal",
            // Interactive
            "dialog", "menu", "menuitem", "template", "slot"
        ));
        
        // Void elements (self-closing)
        VOID_ELEMENTS.addAll(Arrays.asList(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr"
        ));
        
        // Global attributes (applicable to all elements)
        List<String> globalAttrs = Arrays.asList(
            "id", "class", "style", "title", "lang", "dir", "tabindex",
            "accesskey", "contenteditable", "contextmenu", "draggable",
            "dropzone", "hidden", "spellcheck", "translate",
            "data-*", "aria-*", "role"
        );
        
        // Element-specific attributes
        TAG_ATTRIBUTES.put("*", globalAttrs);
        TAG_ATTRIBUTES.put("a", Arrays.asList("href", "target", "rel", "download", "hreflang", "type", "ping"));
        TAG_ATTRIBUTES.put("img", Arrays.asList("src", "alt", "width", "height", "srcset", "sizes", "loading", "decoding"));
        TAG_ATTRIBUTES.put("input", Arrays.asList("type", "name", "value", "placeholder", "required", "disabled",
            "checked", "readonly", "multiple", "min", "max", "step", "pattern", "minlength", "maxlength",
            "size", "autocomplete", "autofocus", "form", "formaction", "formmethod"));
        TAG_ATTRIBUTES.put("form", Arrays.asList("action", "method", "enctype", "target", "novalidate", "autocomplete"));
        TAG_ATTRIBUTES.put("button", Arrays.asList("type", "name", "value", "disabled", "form", "formaction", "formmethod"));
        TAG_ATTRIBUTES.put("select", Arrays.asList("name", "multiple", "required", "disabled", "form", "size"));
        TAG_ATTRIBUTES.put("textarea", Arrays.asList("name", "rows", "cols", "wrap", "required", "disabled",
            "readonly", "maxlength", "minlength", "placeholder", "form"));
        TAG_ATTRIBUTES.put("label", Arrays.asList("for", "form"));
        TAG_ATTRIBUTES.put("link", Arrays.asList("href", "rel", "type", "media", "sizes", "as", "crossorigin"));
        TAG_ATTRIBUTES.put("script", Arrays.asList("src", "type", "async", "defer", "crossorigin", "integrity", "nomodule"));
        TAG_ATTRIBUTES.put("meta", Arrays.asList("name", "content", "charset", "http-equiv", "property"));
        TAG_ATTRIBUTES.put("video", Arrays.asList("src", "controls", "autoplay", "loop", "muted", "poster",
            "width", "height", "preload"));
        TAG_ATTRIBUTES.put("audio", Arrays.asList("src", "controls", "autoplay", "loop", "muted", "preload"));
    }
    
    @Override
    public CompletionTask createTask(int queryType, JTextComponent component) {
        if (queryType != CompletionProvider.COMPLETION_QUERY_TYPE) {
            return null;
        }
        return new AsyncCompletionTask(new HtmlCompletionQuery(), component);
    }
    
    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
        if (typedText.length() == 1) {
            char ch = typedText.charAt(0);
            if (ch == '<' || ch == ' ' || ch == '"' || ch == '=' || ch == '/') {
                return CompletionProvider.COMPLETION_QUERY_TYPE;
            }
        }
        return 0;
    }
    
    private static class HtmlCompletionQuery extends AsyncCompletionQuery {
        
        @Override
        protected void query(CompletionResultSet resultSet, Document doc, int caretOffset) {
            try {
                CompletionContext context = analyzeContext(doc, caretOffset);
                
                switch (context.type) {
                    case TAG_NAME:
                        addTagCompletions(resultSet, context, caretOffset);
                        break;
                    case ATTRIBUTE_NAME:
                        addAttributeCompletions(resultSet, context, caretOffset);
                        break;
                    case ATTRIBUTE_VALUE:
                        addAttributeValueCompletions(resultSet, context, caretOffset);
                        break;
                    case CLOSING_TAG:
                        addClosingTagCompletion(resultSet, context, caretOffset);
                        break;
                }
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                resultSet.finish();
            }
        }
        
        private CompletionContext analyzeContext(Document doc, int offset) throws BadLocationException {
            String text = doc.getText(0, offset);
            CompletionContext context = new CompletionContext();
            
            // Find last unclosed tag
            int lastTagStart = text.lastIndexOf('<');
            int lastTagEnd = text.lastIndexOf('>');
            
            if (lastTagStart > lastTagEnd) {
                // We're inside a tag
                String tagContent = text.substring(lastTagStart + 1);
                
                if (tagContent.startsWith("/")) {
                    context.type = ContextType.CLOSING_TAG;
                    context.prefix = tagContent.substring(1);
                } else if (tagContent.contains(" ")) {
                    // We're in attributes area
                    int spacePos = tagContent.indexOf(' ');
                    context.tagName = tagContent.substring(0, spacePos).trim();
                    String afterTag = tagContent.substring(spacePos + 1);
                    
                    if (afterTag.contains("=\"") && !afterTag.endsWith("\"")) {
                        context.type = ContextType.ATTRIBUTE_VALUE;
                        int lastQuote = afterTag.lastIndexOf("=\"");
                        context.prefix = afterTag.substring(lastQuote + 2);
                    } else {
                        context.type = ContextType.ATTRIBUTE_NAME;
                        int lastSpace = afterTag.lastIndexOf(' ');
                        context.prefix = afterTag.substring(lastSpace + 1);
                    }
                } else {
                    context.type = ContextType.TAG_NAME;
                    context.prefix = tagContent;
                }
            }
            
            return context;
        }
        
        private void addTagCompletions(CompletionResultSet resultSet, CompletionContext context, int caretOffset) {
            String prefix = context.prefix.toLowerCase();
            
            for (String tag : HTML_TAGS) {
                if (tag.startsWith(prefix)) {
                    boolean isVoid = VOID_ELEMENTS.contains(tag);
                    resultSet.addItem(new HtmlTagCompletionItem(tag, caretOffset - prefix.length(), prefix.length(), isVoid));
                }
            }
        }
        
        private void addAttributeCompletions(CompletionResultSet resultSet, CompletionContext context, int caretOffset) {
            String prefix = context.prefix.toLowerCase();
            Set<String> attributes = new HashSet<>();
            
            // Add global attributes
            attributes.addAll(TAG_ATTRIBUTES.get("*"));
            
            // Add tag-specific attributes
            if (context.tagName != null && TAG_ATTRIBUTES.containsKey(context.tagName)) {
                attributes.addAll(TAG_ATTRIBUTES.get(context.tagName));
            }
            
            for (String attr : attributes) {
                if (attr.startsWith(prefix)) {
                    resultSet.addItem(new HtmlAttributeCompletionItem(attr, caretOffset - prefix.length(), prefix.length()));
                }
            }
        }
        
        private void addAttributeValueCompletions(CompletionResultSet resultSet, CompletionContext context, int caretOffset) {
            // This could be expanded with specific value completions for certain attributes
            // For now, we'll just provide common values for specific attributes
        }
        
        private void addClosingTagCompletion(CompletionResultSet resultSet, CompletionContext context, int caretOffset) {
            // Find matching opening tag and suggest closing tag
            String prefix = context.prefix.toLowerCase();
            
            for (String tag : HTML_TAGS) {
                if (tag.startsWith(prefix) && !VOID_ELEMENTS.contains(tag)) {
                    resultSet.addItem(new HtmlClosingTagCompletionItem(tag, caretOffset - prefix.length(), prefix.length()));
                }
            }
        }
    }
    
    private static class CompletionContext {
        ContextType type = ContextType.NONE;
        String tagName = null;
        String prefix = "";
    }
    
    private enum ContextType {
        NONE, TAG_NAME, ATTRIBUTE_NAME, ATTRIBUTE_VALUE, CLOSING_TAG
    }
}