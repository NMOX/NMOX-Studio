package org.nmox.studio.editor.completion;

import javax.swing.JEditorPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.completion.JavaScriptCompletionProvider.JavaScriptMethod;
import org.nmox.studio.editor.completion.JavaScriptCompletionProvider.JavaScriptSnippet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The completion items are plain data objects with one piece of real
 * behaviour: {@code defaultAction} rewrites the buffer and repositions
 * the caret. These pin both the metadata (sort text/priority/insert
 * prefix) and the exact edit each item makes against a live document,
 * so a developer accepting a suggestion lands where they expect.
 */
class CompletionItemTest {

    /**
     * Fixture: a styled document holding {@code before + prefix + after}
     * with the caret conceptually just past the prefix. Items are
     * constructed to replace the prefix span (its start offset and
     * length), which is how the providers wire them up.
     */
    private static class Fixture {
        final JTextComponent component;
        final StyledDocument doc;
        final int prefixStart;
        final int prefixLen;

        Fixture(String before, String prefix, String after) throws BadLocationException {
            doc = new DefaultStyledDocument();
            doc.insertString(0, before + prefix + after, null);
            prefixStart = before.length();
            prefixLen = prefix.length();
            JEditorPane pane = new JEditorPane();
            pane.setDocument(doc);
            pane.setCaretPosition(prefixStart + prefixLen);
            component = pane;
        }

        String text() throws BadLocationException {
            return doc.getText(0, doc.getLength());
        }

        int caret() {
            return component.getCaretPosition();
        }
    }

    // ---- CSS property ------------------------------------------------------

    @Test
    @DisplayName("CSS property item: metadata, and defaultAction inserts 'name: ' and lands after it")
    void cssProperty() throws BadLocationException {
        CssPropertyCompletionItem item = new CssPropertyCompletionItem("display", 5, 3);
        assertThat(item.getSortText()).isEqualTo("display");
        assertThat(item.getInsertPrefix()).isEqualTo("display");
        assertThat(item.getSortPriority()).isEqualTo(0);

        Fixture f = new Fixture(".card { ", "dis", "");
        new CssPropertyCompletionItem("display", f.prefixStart, f.prefixLen).defaultAction(f.component);
        assertThat(f.text()).isEqualTo(".card { display: ");
        assertThat(f.caret()).isEqualTo(f.text().length());
    }

    // ---- CSS value ---------------------------------------------------------

    @Test
    @DisplayName("CSS value item: defaultAction inserts 'value;' and lands after the semicolon")
    void cssValue() throws BadLocationException {
        CssValueCompletionItem item = new CssValueCompletionItem("flex", 0, 0);
        assertThat(item.getSortText()).isEqualTo("flex");
        assertThat(item.getSortPriority()).isEqualTo(0);

        Fixture f = new Fixture("display: ", "fl", "");
        new CssValueCompletionItem("flex", f.prefixStart, f.prefixLen).defaultAction(f.component);
        assertThat(f.text()).isEqualTo("display: flex;");
        assertThat(f.caret()).isEqualTo("display: flex;".length());
    }

    // ---- CSS selector ------------------------------------------------------

    @Test
    @DisplayName("CSS selector item: defaultAction opens a rule block, caret inside it")
    void cssSelector() throws BadLocationException {
        CssSelectorCompletionItem item = new CssSelectorCompletionItem(".card", 0, 0);
        assertThat(item.getInsertPrefix()).isEqualTo(".card");

        Fixture f = new Fixture("", ".ca", "");
        CssSelectorCompletionItem live = new CssSelectorCompletionItem(".card", f.prefixStart, f.prefixLen);
        live.defaultAction(f.component);
        assertThat(f.text()).isEqualTo(".card {\n    \n}");
        // caret sits inside the block: selector.length + 6 (" {\n    ")
        assertThat(f.caret()).isEqualTo(".card".length() + 6);
    }

    // ---- HTML tag ----------------------------------------------------------

    @Test
    @DisplayName("HTML tag item: non-void expands to a matched pair, caret between the tags")
    void htmlTagPaired() throws BadLocationException {
        Fixture f = new Fixture("<", "di", "");
        new HtmlTagCompletionItem("div", f.prefixStart, f.prefixLen, false).defaultAction(f.component);
        assertThat(f.text()).isEqualTo("<div></div>");
        // caret between '>' and '<': start + tag.length + 1
        assertThat(f.caret()).isEqualTo(1 + "div".length() + 1);
    }

    @Test
    @DisplayName("HTML tag item: void element self-closes, caret before the slash")
    void htmlTagVoid() throws BadLocationException {
        HtmlTagCompletionItem item = new HtmlTagCompletionItem("br", 0, 0, true);
        assertThat(item.getSortText()).isEqualTo("br");

        Fixture f = new Fixture("<", "b", "");
        new HtmlTagCompletionItem("br", f.prefixStart, f.prefixLen, true).defaultAction(f.component);
        assertThat(f.text()).isEqualTo("<br />");
        assertThat(f.caret()).isEqualTo(1 + "br".length() + 1);
    }

    // ---- HTML attribute ----------------------------------------------------

    @Test
    @DisplayName("HTML attribute item: inserts name=\"\" with caret between the quotes")
    void htmlAttribute() throws BadLocationException {
        HtmlAttributeCompletionItem item = new HtmlAttributeCompletionItem("href", 0, 0);
        assertThat(item.getInsertPrefix()).isEqualTo("href");

        Fixture f = new Fixture("<a ", "hr", "");
        new HtmlAttributeCompletionItem("href", f.prefixStart, f.prefixLen).defaultAction(f.component);
        assertThat(f.text()).isEqualTo("<a href=\"\"");
        // caret between the quotes: start + name.length + 2 (=\")
        assertThat(f.caret()).isEqualTo("<a ".length() + "href".length() + 2);
    }

    @Test
    @DisplayName("HTML attribute-value item: replaces the fragment with the bare value")
    void htmlAttributeValue() throws BadLocationException {
        HtmlAttributeValueCompletionItem item = new HtmlAttributeValueCompletionItem("checkbox", 0, 0);
        assertThat(item.getSortPriority()).isEqualTo(50);

        Fixture f = new Fixture("<input type=\"", "che", "\"");
        new HtmlAttributeValueCompletionItem("checkbox", f.prefixStart, f.prefixLen).defaultAction(f.component);
        assertThat(f.text()).isEqualTo("<input type=\"checkbox\"");
    }

    // ---- HTML closing tag --------------------------------------------------

    @Test
    @DisplayName("HTML closing-tag item: higher sort priority, inserts 'name>'")
    void htmlClosingTag() throws BadLocationException {
        HtmlClosingTagCompletionItem item = new HtmlClosingTagCompletionItem("div", 0, 0);
        assertThat(item.getSortPriority()).isEqualTo(-1);
        assertThat(item.getSortText()).isEqualTo("div");

        Fixture f = new Fixture("<div>x</", "di", "");
        new HtmlClosingTagCompletionItem("div", f.prefixStart, f.prefixLen).defaultAction(f.component);
        assertThat(f.text()).isEqualTo("<div>x</div>");
        assertThat(f.caret()).isEqualTo(f.text().length());
    }

    // ---- JavaScript keyword ------------------------------------------------

    @Test
    @DisplayName("JS keyword item: priority above methods, inserts 'keyword '")
    void jsKeyword() throws BadLocationException {
        JavaScriptKeywordCompletionItem item = new JavaScriptKeywordCompletionItem("const", 0, 0);
        assertThat(item.getSortPriority()).isEqualTo(1);
        assertThat(item.getSortText()).isEqualTo("const");

        Fixture f = new Fixture("", "con", "");
        new JavaScriptKeywordCompletionItem("const", f.prefixStart, f.prefixLen).defaultAction(f.component);
        assertThat(f.text()).isEqualTo("const ");
        assertThat(f.caret()).isEqualTo("const ".length());
    }

    // ---- JavaScript method -------------------------------------------------

    @Test
    @DisplayName("JS method item: inserts 'name()' with caret between the parens")
    void jsMethod() throws BadLocationException {
        JavaScriptMethod log = new JavaScriptMethod("log", "(...data): void", "Logs");
        JavaScriptMethodCompletionItem item = new JavaScriptMethodCompletionItem(log, 0, 0);
        assertThat(item.getSortText()).isEqualTo("log");
        assertThat(item.getInsertPrefix()).isEqualTo("log");
        assertThat(item.getSortPriority()).isEqualTo(0);

        Fixture f = new Fixture("console.", "lo", "");
        new JavaScriptMethodCompletionItem(log, f.prefixStart, f.prefixLen).defaultAction(f.component);
        assertThat(f.text()).isEqualTo("console.log()");
        // caret between the parens: start + name.length + 1
        assertThat(f.caret()).isEqualTo("console.".length() + "log".length() + 1);
    }

    // ---- JavaScript object -------------------------------------------------

    @Test
    @DisplayName("JS object item: lowest of the identifier priorities, inserts 'name.'")
    void jsObject() throws BadLocationException {
        JavaScriptObjectCompletionItem item = new JavaScriptObjectCompletionItem("Math", 0, 0);
        assertThat(item.getSortPriority()).isEqualTo(2);
        assertThat(item.getSortText()).isEqualTo("Math");

        Fixture f = new Fixture("", "Ma", "");
        new JavaScriptObjectCompletionItem("Math", f.prefixStart, f.prefixLen).defaultAction(f.component);
        assertThat(f.text()).isEqualTo("Math.");
        assertThat(f.caret()).isEqualTo("Math.".length());
    }

    // ---- JavaScript snippet ------------------------------------------------

    @Test
    @DisplayName("JS snippet item: lowest priority, expands ${n:placeholder} to its default text")
    void jsSnippet() throws BadLocationException {
        JavaScriptSnippet snip = new JavaScriptSnippet("func",
                "function ${1:name}(${2:params}) {\n    ${3}\n}", "Function declaration");
        JavaScriptSnippetCompletionItem item = new JavaScriptSnippetCompletionItem(snip, 0, 0);
        assertThat(item.getSortPriority()).isEqualTo(3);
        assertThat(item.getSortText()).isEqualTo("func");
        assertThat(item.getInsertPrefix()).isEqualTo("func");

        Fixture f = new Fixture("", "fu", "");
        new JavaScriptSnippetCompletionItem(snip, f.prefixStart, f.prefixLen).defaultAction(f.component);
        // ${1:name} -> name, ${2:params} -> params, ${3} -> ''
        assertThat(f.text()).isEqualTo("function name(params) {\n    \n}");
    }
}
