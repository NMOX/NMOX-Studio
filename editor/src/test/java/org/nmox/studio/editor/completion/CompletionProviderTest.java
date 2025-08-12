package org.nmox.studio.editor.completion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for completion providers.
 */
@DisplayName("CompletionProvider Tests")
class CompletionProviderTest {
    
    private HtmlCompletionProvider htmlProvider;
    private CssCompletionProvider cssProvider;
    private JavaScriptCompletionProvider jsProvider;
    
    @BeforeEach
    void setUp() {
        htmlProvider = new HtmlCompletionProvider();
        cssProvider = new CssCompletionProvider();
        jsProvider = new JavaScriptCompletionProvider();
    }
    
    @Test
    @DisplayName("Should have HTML completion items")
    void testHtmlCompletionItems() {
        assertThat(HtmlCompletionItem.HTML_TAGS).isNotEmpty();
        assertThat(HtmlCompletionItem.HTML_TAGS).contains(
            "div", "span", "p", "a", "img", "form", "input", "button"
        );
    }
    
    @Test
    @DisplayName("Should have HTML5 semantic tags")
    void testHtml5SemanticTags() {
        assertThat(HtmlCompletionItem.HTML_TAGS).contains(
            "header", "footer", "nav", "main", "section", "article", "aside"
        );
    }
    
    @Test
    @DisplayName("Should have HTML tag attributes")
    void testHtmlTagAttributes() {
        assertThat(HtmlCompletionItem.TAG_ATTRIBUTES).isNotEmpty();
        assertThat(HtmlCompletionItem.TAG_ATTRIBUTES).containsKeys("a", "img", "input", "form");
        
        assertThat(HtmlCompletionItem.TAG_ATTRIBUTES.get("a")).contains("href", "target");
        assertThat(HtmlCompletionItem.TAG_ATTRIBUTES.get("img")).contains("src", "alt");
        assertThat(HtmlCompletionItem.TAG_ATTRIBUTES.get("input")).contains("type", "name", "value");
    }
    
    @Test
    @DisplayName("Should have global HTML attributes")
    void testGlobalHtmlAttributes() {
        assertThat(HtmlCompletionItem.GLOBAL_ATTRIBUTES).isNotEmpty();
        assertThat(HtmlCompletionItem.GLOBAL_ATTRIBUTES).contains(
            "id", "class", "style", "title", "data-", "aria-label"
        );
    }
    
    @Test
    @DisplayName("Should have CSS properties")
    void testCssProperties() {
        assertThat(CssCompletionItem.CSS_PROPERTIES).isNotEmpty();
        assertThat(CssCompletionItem.CSS_PROPERTIES).contains(
            "display", "position", "margin", "padding", "color", "background-color",
            "width", "height", "font-size", "border"
        );
    }
    
    @Test
    @DisplayName("Should have CSS Flexbox properties")
    void testCssFlexboxProperties() {
        assertThat(CssCompletionItem.CSS_PROPERTIES).contains(
            "flex", "flex-direction", "flex-wrap", "justify-content", 
            "align-items", "align-content"
        );
    }
    
    @Test
    @DisplayName("Should have CSS Grid properties")
    void testCssGridProperties() {
        assertThat(CssCompletionItem.CSS_PROPERTIES).contains(
            "grid", "grid-template-columns", "grid-template-rows",
            "grid-gap", "grid-area"
        );
    }
    
    @Test
    @DisplayName("Should have CSS property values")
    void testCssPropertyValues() {
        assertThat(CssCompletionItem.PROPERTY_VALUES).isNotEmpty();
        assertThat(CssCompletionItem.PROPERTY_VALUES).containsKeys("display", "position");
        
        assertThat(CssCompletionItem.PROPERTY_VALUES.get("display")).contains(
            "block", "inline", "inline-block", "flex", "grid", "none"
        );
        assertThat(CssCompletionItem.PROPERTY_VALUES.get("position")).contains(
            "static", "relative", "absolute", "fixed", "sticky"
        );
    }
    
    @Test
    @DisplayName("Should have JavaScript global objects")
    void testJavaScriptGlobalObjects() {
        assertThat(JavaScriptCompletionItem.GLOBAL_OBJECTS).isNotEmpty();
        assertThat(JavaScriptCompletionItem.GLOBAL_OBJECTS).containsKeys(
            "console", "document", "window", "Array", "Object", "String", "Math"
        );
    }
    
    @Test
    @DisplayName("Should have JavaScript console methods")
    void testJavaScriptConsoleMethods() {
        assertThat(JavaScriptCompletionItem.GLOBAL_OBJECTS.get("console")).contains(
            "log", "error", "warn", "info", "debug", "table", "time", "timeEnd"
        );
    }
    
    @Test
    @DisplayName("Should have JavaScript Array methods")
    void testJavaScriptArrayMethods() {
        assertThat(JavaScriptCompletionItem.GLOBAL_OBJECTS.get("Array")).contains(
            "map", "filter", "reduce", "forEach", "find", "findIndex",
            "push", "pop", "shift", "unshift", "slice", "splice"
        );
    }
    
    @Test
    @DisplayName("Should have JavaScript keywords")
    void testJavaScriptKeywords() {
        assertThat(JavaScriptCompletionItem.KEYWORDS).isNotEmpty();
        assertThat(JavaScriptCompletionItem.KEYWORDS).contains(
            "const", "let", "var", "function", "class", "if", "else",
            "for", "while", "return", "async", "await", "import", "export"
        );
    }
    
    @Test
    @DisplayName("Should have JavaScript snippets")
    void testJavaScriptSnippets() {
        assertThat(JavaScriptCompletionItem.SNIPPETS).isNotEmpty();
        
        boolean hasFunction = JavaScriptCompletionItem.SNIPPETS.stream()
            .anyMatch(s -> s.getTrigger().equals("func"));
        assertThat(hasFunction).isTrue();
        
        boolean hasArrow = JavaScriptCompletionItem.SNIPPETS.stream()
            .anyMatch(s -> s.getTrigger().equals("arrow"));
        assertThat(hasArrow).isTrue();
        
        boolean hasClass = JavaScriptCompletionItem.SNIPPETS.stream()
            .anyMatch(s -> s.getTrigger().equals("class"));
        assertThat(hasClass).isTrue();
    }
    
    @Test
    @DisplayName("Should create HTML completion item with text")
    void testCreateHtmlCompletionItem() {
        HtmlCompletionItem item = new HtmlCompletionItem("div", 0);
        assertThat(item.getText()).isEqualTo("div");
        assertThat(item.getSortPriority()).isEqualTo(0);
    }
    
    @Test
    @DisplayName("Should create CSS completion item with text")
    void testCreateCssCompletionItem() {
        CssCompletionItem item = new CssCompletionItem("display", 0);
        assertThat(item.getText()).isEqualTo("display");
        assertThat(item.getSortPriority()).isEqualTo(0);
    }
    
    @Test
    @DisplayName("Should create JavaScript completion item with text")
    void testCreateJavaScriptCompletionItem() {
        JavaScriptCompletionItem item = new JavaScriptCompletionItem("console", 0);
        assertThat(item.getText()).isEqualTo("console");
        assertThat(item.getSortPriority()).isEqualTo(0);
    }
}