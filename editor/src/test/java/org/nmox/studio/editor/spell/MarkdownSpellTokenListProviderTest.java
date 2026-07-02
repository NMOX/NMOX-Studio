package org.nmox.studio.editor.spell;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Markdown spellcheck reads prose and skips code. The scope stacks here
 * are the ones the platform's markdown grammar
 * (org-netbeans-modules-markdown, text.html.markdown) actually emits for
 * a README's parts — the exact fixture that surfaced the bug was a
 * fenced ```js block whose `const` was squiggled as a typo.
 */
class MarkdownSpellTokenListProviderTest {

    private static Object stack(String... scopes) {
        return List.of(scopes);
    }

    @Test
    @DisplayName("Prose is checked: paragraphs, headings, emphasis, quotes, list items")
    void proseIsChecked() {
        assertThat(MarkdownSpellTokenListProvider.isProseScope(null)).isTrue();
        assertThat(MarkdownSpellTokenListProvider.isProseScope(
                stack("text.html.markdown", "meta.paragraph.markdown"))).isTrue();
        assertThat(MarkdownSpellTokenListProvider.isProseScope(
                stack("text.html.markdown", "markup.heading.markdown",
                        "entity.name.section.markdown"))).isTrue();
        assertThat(MarkdownSpellTokenListProvider.isProseScope(
                stack("text.html.markdown", "markup.bold.markdown"))).isTrue();
        assertThat(MarkdownSpellTokenListProvider.isProseScope(
                stack("text.html.markdown", "markup.quote.markdown"))).isTrue();
        assertThat(MarkdownSpellTokenListProvider.isProseScope(
                stack("text.html.markdown", "markup.list.unnumbered.markdown"))).isTrue();
    }

    @Test
    @DisplayName("Fenced code blocks are not prose — the ```js const that started this")
    void fencedCodeIsSkipped() {
        assertThat(MarkdownSpellTokenListProvider.isProseScope(
                stack("text.html.markdown", "markup.fenced_code.block.markdown"))).isFalse();
        // the language tag on the fence line
        assertThat(MarkdownSpellTokenListProvider.isProseScope(
                stack("text.html.markdown", "markup.fenced_code.block.markdown",
                        "fenced_code.block.language.markdown"))).isFalse();
        // embedded-language content keeps the fence scope plus source.*
        assertThat(MarkdownSpellTokenListProvider.isProseScope(
                stack("text.html.markdown", "markup.fenced_code.block.markdown",
                        "meta.embedded.block.javascript", "source.js"))).isFalse();
    }

    @Test
    @DisplayName("Inline code spans and indented code blocks are not prose")
    void rawSpansAreSkipped() {
        assertThat(MarkdownSpellTokenListProvider.isProseScope(
                stack("text.html.markdown", "markup.inline.raw.string.markdown"))).isFalse();
        assertThat(MarkdownSpellTokenListProvider.isProseScope(
                stack("text.html.markdown", "markup.raw.block.markdown"))).isFalse();
    }

    @Test
    @DisplayName("Link destinations and YAML front matter are not prose")
    void linksAndFrontMatterAreSkipped() {
        assertThat(MarkdownSpellTokenListProvider.isProseScope(
                stack("text.html.markdown", "meta.link.inline.markdown",
                        "markup.underline.link.markdown"))).isFalse();
        assertThat(MarkdownSpellTokenListProvider.isProseScope(
                stack("text.html.markdown", "markup.underline.link.image.markdown"))).isFalse();
        // the grammar scopes front matter as markup.raw.yaml.front-matter
        assertThat(MarkdownSpellTokenListProvider.isProseScope(
                stack("text.html.markdown", "markup.raw.yaml.front-matter"))).isFalse();
        // ...and its CONTENT as meta.embedded.block.frontmatter + yaml
        // scopes, none of which mention markup.raw or source. — the exact
        // stack observed once the source.yaml grammar resolves
        assertThat(MarkdownSpellTokenListProvider.isProseScope(
                stack("text.html.markdown", "meta.embedded.block.frontmatter",
                        "meta.flow-sequence.yaml", "string.unquoted.plain.in.yaml"))).isFalse();
    }

    @Test
    @DisplayName("Link TEXT stays prose even though the destination is skipped")
    void linkTextStaysProse() {
        // [visible words](https://example.com) — the visible words carry
        // meta.link + string.other.link.title, never markup.underline.link
        assertThat(MarkdownSpellTokenListProvider.isProseScope(
                stack("text.html.markdown", "meta.link.inline.markdown",
                        "string.other.link.title.markdown"))).isTrue();
    }
}
