package org.nmox.studio.editor.editing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.editing.BlockComments.Edit;
import org.nmox.studio.editor.editing.BlockComments.NoBlockComment;
import org.nmox.studio.editor.editing.BlockComments.Outcome;
import org.nmox.studio.editor.editing.BlockComments.Refusal;
import org.nmox.studio.editor.editing.BlockComments.Style;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toggle Block Comment as a rule over text: what is wrapped, what is
 * unwrapped, and what is refused rather than broken.
 */
class BlockCommentsTest {

    private static final Style C = BlockComments.styleFor("text/javascript");
    private static final Style RUST = BlockComments.styleFor("text/x-rust");

    /** The text after a toggle of {@code [s, e)}; fails when the toggle refused. */
    private static String toggled(String text, int s, int e, Style style) {
        Outcome out = BlockComments.toggle(text, s, e, style);
        assertThat(out).as("toggle of [" + s + "," + e + ") in " + text).isInstanceOf(Edit.class);
        return ((Edit) out).applyTo(text);
    }

    private static Edit edit(String text, int s, int e, Style style) {
        return (Edit) BlockComments.toggle(text, s, e, style);
    }

    @Test
    @DisplayName("a selection is wrapped in the pair, a space inside each delimiter, and stays selected")
    void wrapsASelection() {
        String text = "let a = 1;\nlet b = 2;";
        Edit e = edit(text, 4, 9, C);
        assertThat(e.applyTo(text)).isEqualTo("let /* a = 1 */;\nlet b = 2;");
        // the whole comment is selected, so the same press takes it off again
        assertThat(e.selStart()).isEqualTo(4);
        assertThat(e.applyTo(text).substring(e.selStart(), e.selEnd())).isEqualTo("/* a = 1 */");
    }

    @Test
    @DisplayName("a selection that is one block comment is unwrapped, with or without the inner spaces")
    void unwrapsWithAndWithoutSpaces() {
        assertThat(toggled("x /* a = 1 */ y", 2, 13, C)).isEqualTo("x a = 1 y");
        assertThat(toggled("x /*a = 1*/ y", 2, 11, C)).isEqualTo("x a = 1 y");
        // one space only on each side: a comment that kept two keeps one
        assertThat(toggled("/*  a  */", 0, 9, C)).isEqualTo(" a ");
        // the empty comment and the one-space comment both come off whole
        assertThat(toggled("/**/", 0, 4, C)).isEmpty();
        assertThat(toggled("/* */", 0, 5, C)).isEmpty();
    }

    @Test
    @DisplayName("wrapping and then toggling again gives the original text and selection back")
    void toggleTwiceIsTheOriginal() {
        String text = "  foo();\n  bar();\nbaz();";
        Edit first = edit(text, 2, 17, C);
        String wrapped = first.applyTo(text);
        assertThat(wrapped).isEqualTo("  /* foo();\n  bar(); */\nbaz();");
        Edit second = edit(wrapped, first.selStart(), first.selEnd(), C);
        assertThat(second.applyTo(wrapped)).isEqualTo(text);
        assertThat(second.selStart()).isEqualTo(2);
        assertThat(second.selEnd()).isEqualTo(17);
    }

    @Test
    @DisplayName("whitespace around a selected comment does not hide it")
    void unwrapsThroughSurroundingWhitespace() {
        assertThat(toggled("  /* a */  \nb", 0, 11, C)).isEqualTo("  a  \nb");
    }

    @Test
    @DisplayName("with no selection the line's content is toggled, the indentation left outside")
    void caretTogglesTheLine() {
        String text = "if (x) {\n    run();\n}";
        Edit e = edit(text, 15, 15, C);
        String wrapped = e.applyTo(text);
        assertThat(wrapped).isEqualTo("if (x) {\n    /* run(); */\n}");
        // the caret stays on the character it was on
        assertThat(wrapped.charAt(e.selStart())).isEqualTo(text.charAt(15));
        assertThat(e.selEnd()).isEqualTo(e.selStart());
        Edit back = edit(wrapped, e.selStart(), e.selStart(), C);
        assertThat(back.applyTo(wrapped)).isEqualTo(text);
        assertThat(back.selStart()).isEqualTo(15);
    }

    @Test
    @DisplayName("a caret in the indentation or after the content stays where it was, relative to the text")
    void caretOutsideTheContentIsKept() {
        String text = "    run();  ";
        Edit atIndent = edit(text, 1, 1, C);
        assertThat(atIndent.applyTo(text)).isEqualTo("    /* run(); */  ");
        assertThat(atIndent.selStart()).isEqualTo(1);
        Edit atEnd = edit(text, 12, 12, C);
        assertThat(atEnd.selStart()).isEqualTo(18);
    }

    @Test
    @DisplayName("on a blank line the pair is inserted at the caret with the caret inside it")
    void blankLineGetsThePair() {
        String text = "a\n    \nb";
        Edit e = edit(text, 6, 6, C);
        String out = e.applyTo(text);
        assertThat(out).isEqualTo("a\n    /*  */\nb");
        assertThat(out.substring(0, e.selStart())).endsWith("/* ");
        assertThat(out.substring(e.selStart())).startsWith(" */");
        // and the same press on that line takes it away again
        assertThat(toggled(out, e.selStart(), e.selStart(), C)).isEqualTo(text);
    }

    @Test
    @DisplayName("a selection holding the close delimiter is refused by name: the comment would end early")
    void nestedCloseIsRefused() {
        String text = "a = 1; /* old */ b = 2;";
        Outcome out = BlockComments.toggle(text, 0, text.length(), C);
        assertThat(out).isEqualTo(new Refusal("*/"));
        // and so is a line, with no selection
        assertThat(BlockComments.toggle("x */ y", 2, 2, C)).isEqualTo(new Refusal("*/"));
    }

    @Test
    @DisplayName("a selection holding only the open delimiter is refused too: it starts a comment it does not finish")
    void strayOpenIsRefused() {
        String text = "/* a b */";
        assertThat(BlockComments.toggle(text, 0, 4, C)).isEqualTo(new Refusal("/*"));
    }

    @Test
    @DisplayName("two comments side by side are not one comment: nothing is unwrapped")
    void twoCommentsAreNotOne() {
        String text = "/* a */ b /* c */";
        assertThat(BlockComments.toggle(text, 0, text.length(), C)).isEqualTo(new Refusal("*/"));
    }

    @Test
    @DisplayName("where block comments nest, balanced inner comments are wrapped and unwrapped; unbalanced ones are refused")
    void nestingLanguages() {
        String text = "a /* b */ c";
        String wrapped = toggled(text, 0, text.length(), RUST);
        assertThat(wrapped).isEqualTo("/* a /* b */ c */");
        assertThat(toggled(wrapped, 0, wrapped.length(), RUST)).isEqualTo(text);
        assertThat(BlockComments.toggle("a */ b", 0, 6, RUST)).isEqualTo(new Refusal("*/"));
        assertThat(BlockComments.toggle("a /* b", 0, 6, RUST)).isEqualTo(new Refusal("/*"));
        // the same balanced text in a language that does not nest is refused
        assertThat(BlockComments.toggle(text, 0, text.length(), C)).isEqualTo(new Refusal("*/"));
    }

    @Test
    @DisplayName("a selection that ends at a line start closes the comment at the end of the line before")
    void selectionEndingAtALineStart() {
        String text = "one\ntwo\nthree";
        Edit e = edit(text, 0, 8, C);
        assertThat(e.applyTo(text)).isEqualTo("/* one\ntwo */\nthree");
        // the selection keeps its shape: it still ends at the start of "three"
        assertThat(e.applyTo(text).substring(e.selEnd())).isEqualTo("three");
        assertThat(toggled(e.applyTo(text), e.selStart(), e.selEnd(), C)).isEqualTo(text);
    }

    @Test
    @DisplayName("CRLF is one terminator: the close lands before it, and a line's content stops before it")
    void crlf() {
        String text = "one\r\ntwo\r\n";
        assertThat(toggled(text, 0, 5, C)).isEqualTo("/* one */\r\ntwo\r\n");
        assertThat(toggled(text, 6, 6, C)).isEqualTo("one\r\n/* two */\r\n");
        assertThat(toggled("/* two */\r\nx", 3, 3, C)).isEqualTo("two\r\nx");
    }

    @Test
    @DisplayName("a pair whose delimiters are the same string still wraps, unwraps and refuses")
    void sameDelimiterPair() {
        Style smalltalk = BlockComments.styleFor("text/x-smalltalk");
        assertThat(toggled("x := 3.", 0, 7, smalltalk)).isEqualTo("\" x := 3. \"");
        assertThat(toggled("\" x := 3. \"", 0, 11, smalltalk)).isEqualTo("x := 3.");
        assertThat(BlockComments.toggle("a \"b\" c", 0, 7, smalltalk)).isEqualTo(new Refusal("\""));
    }

    @Test
    @DisplayName("Lua's close delimiter is ordinary code too, and a range holding it is refused")
    void luaIndexing() {
        Style lua = BlockComments.styleFor("text/x-lua");
        assertThat(toggled("local x = 1", 0, 11, lua)).isEqualTo("--[[ local x = 1 ]]");
        assertThat(BlockComments.toggle("t[u[1]] = 2", 0, 11, lua)).isEqualTo(new Refusal("]]"));
    }

    @Test
    @DisplayName("markup comments its own text with <!-- -->, and a script or style block with the block's pair")
    void markupHoldsOtherLanguages() {
        String head = "<template>\n  <p>hi</p>\n</template>\n<script setup>\n";
        String script = "const a = 1;\n";
        // in the template: markup
        assertThat(((Edit) BlockComments.toggle("text/x-vue", "<template>\n", "  <p>hi</p>\n", 4, 4))
                .applyTo("  <p>hi</p>\n")).isEqualTo("  <!-- <p>hi</p> -->\n");
        // in the script block: JavaScript
        assertThat(((Edit) BlockComments.toggle("text/x-vue", head, script, 0, 12)).applyTo(script))
                .isEqualTo("/* const a = 1; */\n");
        // after the block closed: markup again
        String after = head + script + "</script>\n";
        assertThat(((Edit) BlockComments.toggle("text/x-vue", after, "<p>x</p>", 0, 8)).applyTo("<p>x</p>"))
                .isEqualTo("<!-- <p>x</p> -->");
        // a style block is CSS
        assertThat(((Edit) BlockComments.toggle("text/html", "<style>\n", "p { }\n", 0, 5)).applyTo("p { }\n"))
                .isEqualTo("/* p { } */\n");
    }

    @Test
    @DisplayName("a tag that only begins like script or style opens no block")
    void lookalikeTags() {
        assertThat(BlockComments.blockAt("<scripted>\n")).isNull();
        assertThat(BlockComments.blockAt("<styles>\n")).isNull();
        assertThat(BlockComments.blockAt("<SCRIPT lang=\"ts\">\n")).isEqualTo("script lang=\"ts\"");
        // inside the opening tag itself the document is still markup
        assertThat(BlockComments.blockAt("<script lang=")).isNull();
        // the later block wins
        assertThat(BlockComments.blockAt("<style>a{}</style><script>")).isEqualTo("script");
    }

    @Test
    @DisplayName("an indented-Sass style block has no pair, and a range that leaves its block is refused by the tag")
    void markupRefusals() {
        assertThat(BlockComments.toggle("text/x-vue", "<style lang=\"sass\">\n", "a\n  b: c\n", 0, 1))
                .isInstanceOf(NoBlockComment.class);
        Outcome crossing = BlockComments.toggle("text/html", "<script>\n", "a();\n</script>\n<p>", 0, 18);
        assertThat(crossing).isEqualTo(new Refusal("</script>"));
    }

    @Test
    @DisplayName("a language with no pair, or one nobody has decided, answers that it has none")
    void noBlockComment() {
        assertThat(BlockComments.toggle("text/x-python", "", "x = 1", 0, 5)).isInstanceOf(NoBlockComment.class);
        assertThat(BlockComments.toggle("text/x-not-a-language", "", "x", 0, 1)).isInstanceOf(NoBlockComment.class);
        assertThat(BlockComments.toggle(null, "", "x", 0, 1)).isInstanceOf(NoBlockComment.class);
    }

    @Test
    @DisplayName("offsets outside the text are brought inside it, and a reversed selection is the same selection")
    void offsetsAreClamped() {
        assertThat(toggled("abc", 3, 0, C)).isEqualTo("/* abc */");
        assertThat(toggled("abc", -5, 99, C)).isEqualTo("/* abc */");
    }

    /** The text after a toggle in a whole document of {@code mime}, the range given as offsets into it. */
    private static Outcome inDocument(String mime, String doc, int s, int e) {
        // the action hands the rule the text before the line the range starts on, and the lines it touches
        int base = doc.lastIndexOf('\n', Math.max(0, Math.min(s, e) - 1)) + 1;
        if (s == e) {
            int end = doc.indexOf('\n', s);
            String line = doc.substring(base, end < 0 ? doc.length() : end + 1);
            return BlockComments.toggle(mime, doc.substring(0, base), line, s - base, e - base);
        }
        return BlockComments.toggle(mime, doc.substring(0, s), doc.substring(s, e), 0, e - s);
    }

    @Test
    @DisplayName("a markup range that ends inside a script block is refused: its --> would land mid-script")
    void markupRangeEndingInsideAScript() {
        String html = "<p>intro</p>\n<script>\nlet a = 1;\nlet b = 2;\n</script>\n";
        assertThat(inDocument("text/html", html, 0, html.indexOf("let b"))).isEqualTo(new Refusal("<script>"));
        // a style block the same
        String css = "<p>x</p>\n<style>\np { color: red }\n</style>\n";
        assertThat(inDocument("text/html", css, 0, css.indexOf("p {") + 1)).isEqualTo(new Refusal("<style>"));
        // stopping inside the opening tag itself is refused too
        String tag = "<p>x</p>\n<script src=\"a.js\"></script>\n";
        assertThat(inDocument("text/html", tag, 0, tag.indexOf("src"))).isEqualTo(new Refusal("<script>"));
        // a whole script element is markup, and <!-- --> around it is sound
        Outcome whole = inDocument("text/html", tag, tag.indexOf("<script"), tag.indexOf("<script"));
        assertThat(((Edit) whole).applyTo(tag.substring(tag.indexOf("<script"))))
                .startsWith("<!-- <script src=\"a.js\"></script> -->");
    }

    @Test
    @DisplayName("a commented-out <script> opens no block: the markup after it is still markup")
    void commentedOutScriptOpensNothing() {
        String doc = "<!-- <script> -->\n<p>hello</p>\n";
        int p = doc.indexOf("<p>");
        Outcome out = inDocument("text/html", doc, p, p);
        assertThat(((Edit) out).applyTo("<p>hello</p>\n")).isEqualTo("<!-- <p>hello</p> -->\n");
        assertThat(BlockComments.blockAt("<!-- <script> -->\n<p>")).isNull();
        // a <!-- inside a script is the script's text, not a comment that hides the block's end
        assertThat(BlockComments.blockAt("<script>s = '<!--';</script>\n<p>")).isNull();
        assertThat(BlockComments.blockAt("<script>s = '<!--';\n")).isEqualTo("script");
        // an unclosed comment is markup
        assertThat(BlockComments.blockAt("<!-- <script>\n")).isNull();
    }

    @Test
    @DisplayName("Astro's frontmatter is TypeScript: /* */ between the fences, refused on a fence, markup after it")
    void astroFrontmatter() {
        String astro = "---\nconst title = 'Hi';\n---\n<h1>{title}</h1>\n";
        int line = astro.indexOf("const");
        Outcome ts = inDocument("text/x-astro", astro, line, line);
        assertThat(((Edit) ts).applyTo("const title = 'Hi';\n")).isEqualTo("/* const title = 'Hi'; */\n");
        // a selection of the script's whole lines, ending where the closing fence starts
        Outcome lines = inDocument("text/x-astro", astro, line, astro.indexOf("---", 3));
        assertThat(((Edit) lines).applyTo("const title = 'Hi';\n")).isEqualTo("/* const title = 'Hi'; */\n");
        // on a fence, or across one, neither pair is sound
        assertThat(inDocument("text/x-astro", astro, 0, 0)).isEqualTo(new Refusal("---"));
        assertThat(inDocument("text/x-astro", astro, line, astro.indexOf("<h1>"))).isEqualTo(new Refusal("---"));
        // after the closing fence the component's markup is markup
        int h1 = astro.indexOf("<h1>");
        assertThat(((Edit) inDocument("text/x-astro", astro, h1, h1)).applyTo("<h1>{title}</h1>\n"))
                .isEqualTo("<!-- <h1>{title}</h1> -->\n");
        // a <script> written in the frontmatter's TypeScript opens no markup block
        String tricky = "---\nconst s = '<script>';\n---\n<p>x</p>\n";
        int px = tricky.indexOf("<p>");
        assertThat(((Edit) inDocument("text/x-astro", tricky, px, px)).applyTo("<p>x</p>\n"))
                .isEqualTo("<!-- <p>x</p> -->\n");
        // a file with no frontmatter is markup from the top
        assertThat(BlockComments.frontmatter("<h1>x</h1>\n---\n")).isNull();
        // an unclosed fence runs to the end
        assertThat(BlockComments.frontmatter("\n---\nlet a;\n")).containsExactly(1, 5, 12, 12);
    }
}
