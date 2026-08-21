package org.nmox.studio.editor.emmet;

import javax.swing.JEditorPane;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.netbeans.editor.BaseDocument;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Emmet editor plumbing driven on real headless Swing (the pure
 * grammars have their own suites; this pins the ACTION's branch
 * dispatch, indentation math, and caret landing — the half that was
 * only ever proven live, and the half the windows JaCoCo lane needs on
 * the measured surface).
 */
class ExpandAbbreviationActionTest {

    private static JEditorPane pane(String mime, String text, int caret)
            throws Exception {
        BaseDocument doc = new BaseDocument(false, mime);
        // the real app's kit-installed documents carry the mime as the
        // "mimeType" property; the family branch keys on it
        doc.putProperty("mimeType", mime);
        doc.insertString(0, text, null);
        JEditorPane pane = new JEditorPane();
        pane.setDocument(doc);
        pane.setCaretPosition(caret);
        return pane;
    }

    @Test
    @DisplayName("markup: ul>li*2 expands at the line's indent, caret inside the first li")
    void markupExpands() throws Exception {
        JEditorPane p = pane("text/html", "  ul>li*2", 9);
        new ExpandAbbreviationAction().actionPerformed(null, p);
        String out = p.getDocument().getText(0, p.getDocument().getLength());
        assertThat(out).contains("<ul>").contains("<li></li>").contains("</ul>");
        assertThat(out.lines().filter(l -> l.contains("<li>")).findFirst().orElse(""))
                .as("continuation lines carry the abbreviation line's indent")
                .startsWith("    ");
        assertThat(p.getCaretPosition())
                .isEqualTo(out.indexOf("<li>") + 4);
    }

    @Test
    @DisplayName("family: a .vue style block expands CSS with the clip law, template markup untouched")
    void vueStyleBlockExpandsCss() throws Exception {
        String doc = "<template><p>x</p></template>\n<style>\n.a { bgc:tomato }\n</style>\n";
        int caret = doc.indexOf("bgc:tomato") + "bgc:tomato".length();
        JEditorPane p = pane("text/x-vue", doc, caret);
        new ExpandAbbreviationAction().actionPerformed(null, p);
        String out = p.getDocument().getText(0, p.getDocument().getLength());
        assertThat(out)
                .as("CSS expansion inside the region; the slice never swallows markup")
                .contains("background-color: tomato;")
                .contains("<template><p>x</p></template>");
        assertThat(out).doesNotContain("bgc:tomato");
    }

    @Test
    @DisplayName("markup refusal leaves the text byte-identical")
    void markupRefuses() throws Exception {
        JEditorPane p = pane("text/html", "just prose here", 15);
        new ExpandAbbreviationAction().actionPerformed(null, p);
        assertThat(p.getDocument().getText(0, p.getDocument().getLength()))
                .isEqualTo("just prose here");
    }

    @Test
    @DisplayName("css: a declaration replaces the token on its own line")
    void cssExpands() throws Exception {
        JEditorPane p = pane("text/css", "  df", 4);
        new ExpandAbbreviationAction().actionPerformed(null, p);
        String out = p.getDocument().getText(0, p.getDocument().getLength());
        assertThat(out).contains("display: flex;");
        JEditorPane refuse = pane("text/css", "  zzznot", 8);
        new ExpandAbbreviationAction().actionPerformed(null, refuse);
        assertThat(refuse.getDocument().getText(0, refuse.getDocument().getLength()))
                .isEqualTo("  zzznot");
    }

    @Test
    @DisplayName("typescript: expands inside the inline template, refuses outside it")
    void typescriptGate() throws Exception {
        String ts = "@Component({ template: `\n  ul>li*2\n` })\nlet x = 1;\n";
        int inTemplate = ts.indexOf("ul>li*2") + 7;
        JEditorPane p = pane("text/typescript", ts, inTemplate);
        new ExpandAbbreviationAction().actionPerformed(null, p);
        String out = p.getDocument().getText(0, p.getDocument().getLength());
        assertThat(out).contains("<ul>").contains("</ul>");

        String code = "@Component({ template: `x` })\nul>li*2\n";
        JEditorPane outside = pane("text/typescript", code, code.indexOf("*2") + 2);
        new ExpandAbbreviationAction().actionPerformed(null, outside);
        assertThat(outside.getDocument().getText(0,
                outside.getDocument().getLength()))
                .as("outside the literal the chord must never mangle code")
                .isEqualTo(code);
    }

    @Test
    @DisplayName("a null / read-only target declines without touching anything")
    void guards() throws Exception {
        new ExpandAbbreviationAction().actionPerformed(null, null);
        JEditorPane p = pane("text/html", "ul", 2);
        p.setEditable(false);
        new ExpandAbbreviationAction().actionPerformed(null, p);
        assertThat(p.getDocument().getText(0, p.getDocument().getLength()))
                .isEqualTo("ul");
    }
}
