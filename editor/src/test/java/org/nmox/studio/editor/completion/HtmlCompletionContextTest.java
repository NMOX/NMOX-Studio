package org.nmox.studio.editor.completion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.completion.HtmlCompletionProvider.CompletionContext;
import org.nmox.studio.editor.completion.HtmlCompletionProvider.ContextType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The markup-context rules behind HTML completion: what the caret
 * position means decides which dictionary the popup draws from.
 */
class HtmlCompletionContextTest {

    @Test
    @DisplayName("Inside '<di' the caret names a tag")
    void tagContext() {
        CompletionContext c = HtmlCompletionProvider.analyzeContext("<p>x</p><di");
        assertThat(c.type).isEqualTo(ContextType.TAG_NAME);
        assertThat(c.prefix).isEqualTo("di");
    }

    @Test
    @DisplayName("After the tag name and a space, attributes complete, scoped to the tag")
    void attributeContext() {
        CompletionContext c = HtmlCompletionProvider.analyzeContext("<input ty");
        assertThat(c.type).isEqualTo(ContextType.ATTRIBUTE_NAME);
        assertThat(c.tagName).isEqualTo("input");
        assertThat(c.prefix).isEqualTo("ty");
    }

    @Test
    @DisplayName("Inside type=\"…\" the owning attribute is recovered for value lookup")
    void attributeValueContext() {
        CompletionContext c = HtmlCompletionProvider.analyzeContext("<input type=\"che");
        assertThat(c.type).isEqualTo(ContextType.ATTRIBUTE_VALUE);
        assertThat(c.attributeName).isEqualTo("type");
        assertThat(c.prefix).isEqualTo("che");
        assertThat(HtmlCompletionProvider.valuesFor("type")).contains("checkbox", "radio");
    }

    @Test
    @DisplayName("Free-form attributes (href) enumerate nothing")
    void freeFormAttributeHasNoDictionary() {
        assertThat(HtmlCompletionProvider.valuesFor("href")).isNull();
    }

    @Test
    @DisplayName("A second attribute name still completes after the first is quoted")
    void attributeNameAfterAQuotedAttribute() {
        // regression: a fully-written earlier attribute used to force value-mode,
        // killing attribute-name completion for every following attribute
        CompletionContext c = HtmlCompletionProvider.analyzeContext("<a href=\"x\" tar");
        assertThat(c.type).isEqualTo(ContextType.ATTRIBUTE_NAME);
        assertThat(c.tagName).isEqualTo("a");
        assertThat(c.prefix).isEqualTo("tar");
    }

    @Test
    @DisplayName("A later attribute's value is still a value, owner recovered")
    void valueOfSecondAttribute() {
        CompletionContext c = HtmlCompletionProvider.analyzeContext("<a href=\"x\" target=\"_bl");
        assertThat(c.type).isEqualTo(ContextType.ATTRIBUTE_VALUE);
        assertThat(c.attributeName).isEqualTo("target");
        assertThat(c.prefix).isEqualTo("_bl");
    }

    @Test
    @DisplayName("'</d' offers closing tags; outside any tag, nothing")
    void closingAndNone() {
        CompletionContext closing = HtmlCompletionProvider.analyzeContext("<div>text</d");
        assertThat(closing.type).isEqualTo(ContextType.CLOSING_TAG);
        assertThat(closing.prefix).isEqualTo("d");

        CompletionContext none = HtmlCompletionProvider.analyzeContext("<p>plain text</p> and more");
        assertThat(none.type).isEqualTo(ContextType.NONE);
    }
}
