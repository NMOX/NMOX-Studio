package org.nmox.studio.editor.spell;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole point of the code spellchecker is that it checks words in
 * comments and nowhere else. That hinges on recognising a comment token
 * across both lexers we ship: the custom JS/TS lexer names the id
 * category "comment", while TextMate gives every token the single id
 * TEXTMATE and exposes the real scope stack as a "categories" property.
 *
 * Reading the wrong property key (the bug this guards) silently turned
 * every config file - .editorconfig, .env, nginx.conf - back into prose,
 * so keys and values like "charset" and "indent_style" were flagged as
 * misspellings.
 */
class CodeSpellTokenListProviderTest {

    @Test
    @DisplayName("Custom-lexer comment category is recognised")
    void customLexerComment() {
        assertThat(CodeSpellTokenListProvider.isCommentScope("comment", null)).isTrue();
        assertThat(CodeSpellTokenListProvider.isCommentScope("line-comment", null)).isTrue();
    }

    @Test
    @DisplayName("TextMate comment scope in the categories property is recognised")
    void textmateComment() {
        // what the TextMate lexer attaches for a '#' line in an .editorconfig
        Object categories = List.of("source.ini", "comment.line.number-sign.ini");
        assertThat(CodeSpellTokenListProvider.isCommentScope("textmate", categories)).isTrue();
    }

    @Test
    @DisplayName("Config keys and values are NOT comments - no prose check leaks in")
    void configValuesAreNotComments() {
        // 'charset = utf-8' tokenises with keyword/value scopes, never comment
        assertThat(CodeSpellTokenListProvider.isCommentScope("textmate",
                List.of("source.ini", "keyword.other.definition.ini"))).isFalse();
        assertThat(CodeSpellTokenListProvider.isCommentScope("textmate",
                List.of("source.ini", "string.unquoted.ini"))).isFalse();
        assertThat(CodeSpellTokenListProvider.isCommentScope("identifier", null)).isFalse();
        assertThat(CodeSpellTokenListProvider.isCommentScope(null, null)).isFalse();
    }
}
