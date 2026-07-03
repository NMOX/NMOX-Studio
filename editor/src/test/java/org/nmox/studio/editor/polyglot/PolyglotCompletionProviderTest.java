package org.nmox.studio.editor.polyglot;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The polyglot provider is deliberately non-semantic: for a given mime
 * it offers the language's keywords plus every identifier already in the
 * buffer, prefix-filtered. These pin the keyword table, the identifier
 * scan (which must skip the word being typed), and the prefix walk.
 */
class PolyglotCompletionProviderTest {

    // ---- keyword table -----------------------------------------------------

    @Test
    @DisplayName("Every registered mime has a non-trivial keyword set")
    void keywordTablePopulated() {
        assertThat(PolyglotCompletionProvider.KEYWORDS).hasSizeGreaterThanOrEqualTo(25);
        PolyglotCompletionProvider.KEYWORDS.forEach((mime, kw) ->
                assertThat(kw).as(mime).hasSizeGreaterThan(10));
        assertThat(PolyglotCompletionProvider.KEYWORDS.get("text/x-rust")).contains("fn", "impl", "match", "Vec");
        assertThat(PolyglotCompletionProvider.KEYWORDS.get("text/x-python")).contains("def", "class", "lambda");
        assertThat(PolyglotCompletionProvider.KEYWORDS.get("text/x-go")).contains("func", "chan", "go", "defer");
    }

    // ---- prefix walk -------------------------------------------------------

    @Test
    @DisplayName("prefixAt walks back over identifier characters from the offset")
    void prefixAt() {
        assertThat(PolyglotCompletionProvider.prefixAt("let foo", 7)).isEqualTo("foo");
        assertThat(PolyglotCompletionProvider.prefixAt("obj.field", 9)).isEqualTo("field"); // stops at the dot
        assertThat(PolyglotCompletionProvider.prefixAt("a_b$c2", 6)).isEqualTo("a_b$c2");   // _ and $ are part of it
        assertThat(PolyglotCompletionProvider.prefixAt("x = ", 4)).isEmpty();               // trailing space: no prefix
        // offset past the end is clamped
        assertThat(PolyglotCompletionProvider.prefixAt("abc", 99)).isEqualTo("abc");
    }

    // ---- keyword matching --------------------------------------------------

    @Test
    @DisplayName("Keyword matching is prefix-based, case-insensitive, and sorted")
    void matchingKeywords() {
        Set<String> rust = PolyglotCompletionProvider.KEYWORDS.get("text/x-rust");
        List<String> match = PolyglotCompletionProvider.matchingKeywords(rust, "f");
        assertThat(match).contains("fn", "false", "for", "format");
        assertThat(match).isSorted();
        // empty keyword set matches nothing
        assertThat(PolyglotCompletionProvider.matchingKeywords(Set.of(), "f")).isEmpty();
    }

    // ---- identifier scan ---------------------------------------------------

    @Test
    @DisplayName("Identifier scan collects buffer words, sorted, minus keywords and the word being typed")
    void matchingIdentifiers() {
        // 'compute' and 'compile' are user identifiers; caret is at end of the second 'comp'
        String text = "fn compute() {} fn compile() {} comp";
        Set<String> keywords = PolyglotCompletionProvider.KEYWORDS.get("text/x-rust");
        int caret = text.length();
        List<String> ids = PolyglotCompletionProvider.matchingIdentifiers(text, keywords, "comp", caret);
        // both earlier identifiers surface; the fragment 'comp' the caret ends on does not
        assertThat(ids).containsExactly("compile", "compute");
    }

    @Test
    @DisplayName("A keyword typed in the buffer is not re-offered as an identifier")
    void keywordsNotDoubledAsIdentifiers() {
        String text = "fn helper() {} fn";  // 'fn' is a rust keyword
        Set<String> keywords = PolyglotCompletionProvider.KEYWORDS.get("text/x-rust");
        // prefix 'f' at a caret away from any word end
        List<String> ids = PolyglotCompletionProvider.matchingIdentifiers(text, keywords, "f", 5);
        assertThat(ids).doesNotContain("fn");     // filtered as keyword
    }

    @Test
    @DisplayName("Single-letter tokens are ignored (the WORD pattern requires 2+ chars)")
    void singleLetterTokensIgnored() {
        String text = "let a = bcd";
        List<String> ids = PolyglotCompletionProvider.matchingIdentifiers(text, Set.of(), "", 0);
        assertThat(ids).contains("let", "bcd");
        assertThat(ids).doesNotContain("a");
    }

    // ---- auto-query trigger ------------------------------------------------

    @Test
    @DisplayName("Polyglot completion is explicit-only: never auto-pops")
    void neverAutoQueries() {
        PolyglotCompletionProvider p = new PolyglotCompletionProvider();
        assertThat(p.getAutoQueryTypes(null, "a")).isZero();
        assertThat(p.getAutoQueryTypes(null, ".")).isZero();
    }
}
