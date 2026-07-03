package org.nmox.studio.editor.completion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.completion.CssCompletionProvider.CssContext;
import org.nmox.studio.editor.completion.CssCompletionProvider.CssContextType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSS completion is context-sensitive: the same keystroke offers
 * selectors, property names, or property values depending on where the
 * caret sits relative to the braces and colon. These pin the context
 * classifier and the three dictionaries it draws from.
 */
class CssCompletionProviderTest {

    // ---- context classification --------------------------------------------

    @Test
    @DisplayName("Outside any rule block the caret is in selector context")
    void selectorContext() {
        // at the very start of the file the prefix scan begins at index 1,
        // so a leading '.' is dropped — a known quirk of the classifier
        CssContext c = CssCompletionProvider.analyzeContext(".ca");
        assertThat(c.type).isEqualTo(CssContextType.SELECTOR);
        assertThat(c.prefix).isEqualTo("ca");

        // after a closed rule we are back in selector context, prefix intact
        CssContext after = CssCompletionProvider.analyzeContext(".a { color: red; }\n.b");
        assertThat(after.type).isEqualTo(CssContextType.SELECTOR);
        assertThat(after.prefix).isEqualTo(".b");
    }

    @Test
    @DisplayName("Inside a rule block, before a colon, the caret names a property")
    void propertyNameContext() {
        CssContext c = CssCompletionProvider.analyzeContext(".card {\n  dis");
        assertThat(c.type).isEqualTo(CssContextType.PROPERTY_NAME);
        assertThat(c.prefix).isEqualTo("dis");

        // after a completed declaration, the next property still classifies as a name
        CssContext next = CssCompletionProvider.analyzeContext(".card {\n  color: red;\n  back");
        assertThat(next.type).isEqualTo(CssContextType.PROPERTY_NAME);
        assertThat(next.prefix).isEqualTo("back");
    }

    @Test
    @DisplayName("After the colon the caret is a value, and the owning property is recovered")
    void propertyValueContext() {
        CssContext c = CssCompletionProvider.analyzeContext(".card {\n  display: fl");
        assertThat(c.type).isEqualTo(CssContextType.PROPERTY_VALUE);
        assertThat(c.propertyName).isEqualTo("display");
        assertThat(c.prefix).isEqualTo("fl");
    }

    // ---- property matching -------------------------------------------------

    @Test
    @DisplayName("Property matching is prefix-based, spans all categories, and is de-duplicated")
    void matchingProperties() {
        assertThat(CssCompletionProvider.matchingProperties("dis")).containsExactly("display");
        // 'display' appears in both the layout and grid category lists — surfaced once
        assertThat(CssCompletionProvider.matchingProperties("display")).containsExactly("display");
        assertThat(CssCompletionProvider.matchingProperties("bord"))
                .contains("border", "border-radius", "border-color")
                .allMatch(p -> p.startsWith("bord"));
        // uppercase prefix is folded to lower case
        assertThat(CssCompletionProvider.matchingProperties("FLEX")).contains("flex", "flex-direction");
        assertThat(CssCompletionProvider.matchingProperties("zzz")).isEmpty();
    }

    // ---- value matching ----------------------------------------------------

    @Test
    @DisplayName("Value matching yields the property's own values then the CSS-wide keywords")
    void matchingValues() {
        // prefix matches the START of the value: "fl" hits flex and flow-root, not inline-flex
        assertThat(CssCompletionProvider.matchingValues("display", "fl")).contains("flex", "flow-root");
        assertThat(CssCompletionProvider.matchingValues("display", "inline")).contains("inline", "inline-flex", "inline-grid");
        assertThat(CssCompletionProvider.matchingValues("position", "")).contains(
                "static", "relative", "absolute", "fixed", "sticky",  // enumerated
                "inherit", "initial", "unset", "auto", "none");        // common
        // 'auto' is a common value even for a property with no enumerated set
        assertThat(CssCompletionProvider.matchingValues("z-index", "au")).containsExactly("auto");
        // an unknown property still offers the common keywords
        assertThat(CssCompletionProvider.matchingValues("no-such-prop", "in"))
                .containsExactly("inherit", "initial");
    }

    // ---- selector matching -------------------------------------------------

    @Test
    @DisplayName("Selector matching is case-sensitive prefix over the common-selector list")
    void matchingSelectors() {
        assertThat(CssCompletionProvider.matchingSelectors("::")).contains("::before", "::after");
        assertThat(CssCompletionProvider.matchingSelectors(":h")).containsExactly(":hover");
        assertThat(CssCompletionProvider.matchingSelectors("h")).contains("html", "h1", "h2", "h3");
        assertThat(CssCompletionProvider.matchingSelectors("")).contains("*", "body", "div");
    }

    // ---- auto-query trigger ------------------------------------------------

    @Test
    @DisplayName("Single-char typing triggers completion on letters and the css punctuation")
    void autoQueryTypes() {
        CssCompletionProvider p = new CssCompletionProvider();
        assertThat(p.getAutoQueryTypes(null, "d")).isNotZero();
        assertThat(p.getAutoQueryTypes(null, ":")).isNotZero();
        assertThat(p.getAutoQueryTypes(null, "{")).isNotZero();
        assertThat(p.getAutoQueryTypes(null, "5")).isZero();     // digit does not trigger
        assertThat(p.getAutoQueryTypes(null, "ab")).isZero();    // multi-char paste does not
    }
}
