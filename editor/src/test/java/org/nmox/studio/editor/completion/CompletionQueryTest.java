package org.nmox.studio.editor.completion;

import java.util.ArrayList;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/**
 * Drives the three providers' query classes end to end against real
 * documents: the same path the completion popup runs, minus the popup.
 * The {@link CompletionResultSet} is final and platform-constructed, so
 * a Mockito stub collects the added items; assertions read each item's
 * sort text, which is the offered completion's name. What these pin is
 * the DISPATCH — that a given caret position yields the right mix of
 * items — since the individual matchers are covered elsewhere.
 */
class CompletionQueryTest {

    /** A result-set stand-in that collects items and remembers finish(). */
    private static final class Collected {
        final List<CompletionItem> items = new ArrayList<>();
        final boolean[] finished = {false};
        final CompletionResultSet resultSet;

        Collected() {
            resultSet = Mockito.mock(CompletionResultSet.class);
            Mockito.when(resultSet.addItem(any())).thenAnswer(inv -> {
                items.add(inv.getArgument(0));
                return true;
            });
            Mockito.doAnswer(inv -> {
                finished[0] = true;
                return null;
            }).when(resultSet).finish();
        }

        List<String> names() {
            return items.stream().map(i -> i.getSortText().toString()).toList();
        }
    }

    private static Document doc(String mime, String text) throws BadLocationException {
        DefaultStyledDocument d = new DefaultStyledDocument();
        d.insertString(0, text, null);
        d.putProperty("mimeType", mime);
        return d;
    }

    // ---- JavaScript / TypeScript -------------------------------------------

    @Test
    @DisplayName("JS general query mixes keywords, globals, snippets and document identifiers")
    void jsGeneralQuery() throws BadLocationException {
        String src = "const counter = 1;\nconst coordinate = 2;\nco";
        Document d = doc("text/javascript", src);
        Collected out = new Collected();

        new JavaScriptCompletionProvider.JavaScriptCompletionQuery()
                .query(out.resultSet, d, src.length());

        assertThat(out.names())
                .contains("const", "continue")        // keywords
                .contains("console")                  // global object
                .contains("computed")                 // snippet trigger
                .contains("counter", "coordinate");   // harvested identifiers
        // the fragment being typed is never offered back
        assertThat(out.names()).doesNotContain("co");
        assertThat(out.finished[0]).as("finish() always runs").isTrue();
    }

    @Test
    @DisplayName("JS dot access offers the object's methods and nothing else")
    void jsDotAccess() throws BadLocationException {
        String src = "console.lo";
        Document d = doc("text/javascript", src);
        Collected out = new Collected();

        new JavaScriptCompletionProvider.JavaScriptCompletionQuery()
                .query(out.resultSet, d, src.length());

        assertThat(out.names()).containsExactly("log");
        assertThat(out.items).allMatch(i -> i instanceof JavaScriptMethodCompletionItem);
        assertThat(out.finished[0]).isTrue();
    }

    @Test
    @DisplayName("JS identifier harvest lexes for real: names inside strings and comments never surface")
    void jsHarvestUsesTheLexer() throws BadLocationException {
        String src = "const s = \"stringy\";\n// commenty\nconst visible = 1;\nvi";
        Document d = doc("text/javascript", src);
        Collected out = new Collected();

        new JavaScriptCompletionProvider.JavaScriptCompletionQuery()
                .query(out.resultSet, d, src.length());

        assertThat(out.names()).contains("visible");
        assertThat(out.names()).doesNotContain("stringy", "commenty");
    }

    @Test
    @DisplayName("TS documents ride the TypeScript lexer through the same harvest")
    void tsHarvest() throws BadLocationException {
        String src = "interface Wire {}\nconst wiring = 1;\nwi";
        Document d = doc("text/typescript", src);
        Collected out = new Collected();

        new JavaScriptCompletionProvider.JavaScriptCompletionQuery()
                .query(out.resultSet, d, src.length());

        assertThat(out.names()).contains("wiring");
    }

    @Test
    @DisplayName("JS provider auto-pops on '.' and letters; only real queries create a task")
    void jsProviderGates() {
        JavaScriptCompletionProvider p = new JavaScriptCompletionProvider();
        assertThat(p.getAutoQueryTypes(null, ".")).isEqualTo(CompletionProvider.COMPLETION_QUERY_TYPE);
        assertThat(p.getAutoQueryTypes(null, "a")).isEqualTo(CompletionProvider.COMPLETION_QUERY_TYPE);
        assertThat(p.getAutoQueryTypes(null, ";")).isZero();
        assertThat(p.getAutoQueryTypes(null, "ab")).isZero();
        assertThat(p.createTask(CompletionProvider.DOCUMENTATION_QUERY_TYPE, null)).isNull();
        assertThat(p.createTask(CompletionProvider.COMPLETION_QUERY_TYPE, null)).isNotNull();
    }

    // ---- HTML --------------------------------------------------------------

    @Test
    @DisplayName("HTML tag position offers matching tags, void elements flagged")
    void htmlTags() throws BadLocationException {
        String src = "<di";
        Collected out = new Collected();

        new HtmlCompletionProvider.HtmlCompletionQuery()
                .query(out.resultSet, doc("text/html", src), src.length());

        assertThat(out.names()).contains("div", "dialog");
        assertThat(out.items).allMatch(i -> i instanceof HtmlTagCompletionItem);
        assertThat(out.finished[0]).isTrue();
    }

    @Test
    @DisplayName("HTML attribute position merges global and tag-specific attributes")
    void htmlAttributes() throws BadLocationException {
        String src = "<img s";
        Collected out = new Collected();

        new HtmlCompletionProvider.HtmlCompletionQuery()
                .query(out.resultSet, doc("text/html", src), src.length());

        assertThat(out.names())
                .contains("src", "srcset", "sizes") // img-specific
                .contains("style", "spellcheck");   // global
        assertThat(out.names()).doesNotContain("href");
    }

    @Test
    @DisplayName("HTML enumerated attribute value completes; free-form values stay silent")
    void htmlAttributeValues() throws BadLocationException {
        String enumerated = "<form method=\"g";
        Collected out = new Collected();
        new HtmlCompletionProvider.HtmlCompletionQuery()
                .query(out.resultSet, doc("text/html", enumerated), enumerated.length());
        assertThat(out.names()).containsExactly("get");

        String freeForm = "<a href=\"ht";
        Collected none = new Collected();
        new HtmlCompletionProvider.HtmlCompletionQuery()
                .query(none.resultSet, doc("text/html", freeForm), freeForm.length());
        assertThat(none.items).isEmpty();
        assertThat(none.finished[0]).as("finish() runs even with nothing to offer").isTrue();
    }

    @Test
    @DisplayName("HTML closing-tag position skips void elements — they have no closing form")
    void htmlClosingTags() throws BadLocationException {
        String src = "<div></d";
        Collected out = new Collected();

        new HtmlCompletionProvider.HtmlCompletionQuery()
                .query(out.resultSet, doc("text/html", src), src.length());

        assertThat(out.names()).contains("div", "details", "dialog");
        assertThat(out.names()).doesNotContain("br", "img", "input");
        assertThat(out.items).allMatch(i -> i instanceof HtmlClosingTagCompletionItem);
    }

    @Test
    @DisplayName("HTML provider auto-pops on markup characters only")
    void htmlProviderGates() {
        HtmlCompletionProvider p = new HtmlCompletionProvider();
        assertThat(p.getAutoQueryTypes(null, "<")).isEqualTo(CompletionProvider.COMPLETION_QUERY_TYPE);
        assertThat(p.getAutoQueryTypes(null, "\"")).isEqualTo(CompletionProvider.COMPLETION_QUERY_TYPE);
        assertThat(p.getAutoQueryTypes(null, "x")).isZero();
        assertThat(p.createTask(CompletionProvider.DOCUMENTATION_QUERY_TYPE, null)).isNull();
        assertThat(p.createTask(CompletionProvider.COMPLETION_QUERY_TYPE, null)).isNotNull();
    }

    // ---- CSS ---------------------------------------------------------------

    @Test
    @DisplayName("CSS inside a rule block offers property names")
    void cssProperties() throws BadLocationException {
        String src = ".card {\n  dis";
        Collected out = new Collected();

        new CssCompletionProvider.CssCompletionQuery()
                .query(out.resultSet, doc("text/css", src), src.length());

        assertThat(out.names()).contains("display");
        assertThat(out.items).allMatch(i -> i instanceof CssPropertyCompletionItem);
        assertThat(out.finished[0]).isTrue();
    }

    @Test
    @DisplayName("CSS after a colon offers the property's enumerated values")
    void cssValues() throws BadLocationException {
        String src = ".card {\n  display: fl";
        Collected out = new Collected();

        new CssCompletionProvider.CssCompletionQuery()
                .query(out.resultSet, doc("text/css", src), src.length());

        assertThat(out.names()).contains("flex");
        assertThat(out.items).allMatch(i -> i instanceof CssValueCompletionItem);
    }

    @Test
    @DisplayName("CSS at top level offers selectors")
    void cssSelectors() throws BadLocationException {
        String src = "p {\n  color: red;\n}\nbo";
        Collected out = new Collected();

        new CssCompletionProvider.CssCompletionQuery()
                .query(out.resultSet, doc("text/css", src), src.length());

        assertThat(out.names()).contains("body");
        assertThat(out.items).allMatch(i -> i instanceof CssSelectorCompletionItem);
    }

    @Test
    @DisplayName("CSS provider auto-pops on ':', ';', '{' and letters")
    void cssProviderGates() {
        CssCompletionProvider p = new CssCompletionProvider();
        assertThat(p.getAutoQueryTypes(null, ":")).isEqualTo(CompletionProvider.COMPLETION_QUERY_TYPE);
        assertThat(p.getAutoQueryTypes(null, "{")).isEqualTo(CompletionProvider.COMPLETION_QUERY_TYPE);
        assertThat(p.getAutoQueryTypes(null, "a")).isEqualTo(CompletionProvider.COMPLETION_QUERY_TYPE);
        assertThat(p.getAutoQueryTypes(null, ")")).isZero();
        assertThat(p.createTask(CompletionProvider.DOCUMENTATION_QUERY_TYPE, null)).isNull();
        assertThat(p.createTask(CompletionProvider.COMPLETION_QUERY_TYPE, null)).isNotNull();
    }
}
