package org.nmox.studio.editor.polyglot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one piece of language knowledge the typing tools need: the line-
 * comment prefix per mime. A wrong or missing entry breaks comment
 * toggling for that language, so the headline families are pinned.
 */
class LanguageCommentsTest {

    @Test
    @DisplayName("C-family and friends use //")
    void slashSlash() {
        assertThat(LanguageComments.lineCommentFor("text/javascript")).isEqualTo("//");
        assertThat(LanguageComments.lineCommentFor("text/typescript")).isEqualTo("//");
        assertThat(LanguageComments.lineCommentFor("text/x-java")).isEqualTo("//");
        assertThat(LanguageComments.lineCommentFor("text/x-rust")).isEqualTo("//");
        assertThat(LanguageComments.lineCommentFor("text/x-go")).isEqualTo("//");
    }

    @Test
    @DisplayName("Scripting and config families use #")
    void hash() {
        assertThat(LanguageComments.lineCommentFor("text/x-python")).isEqualTo("#");
        assertThat(LanguageComments.lineCommentFor("text/x-ruby")).isEqualTo("#");
        assertThat(LanguageComments.lineCommentFor("text/sh")).isEqualTo("#");
        assertThat(LanguageComments.lineCommentFor("text/x-yaml")).isEqualTo("#");
        assertThat(LanguageComments.lineCommentFor("text/x-toml")).isEqualTo("#");
    }

    @Test
    @DisplayName("Lisps use ;;, Lua/Haskell use --, Erlang uses %")
    void others() {
        assertThat(LanguageComments.lineCommentFor("text/x-clojure")).isEqualTo(";;");
        assertThat(LanguageComments.lineCommentFor("text/x-lisp")).isEqualTo(";;");
        assertThat(LanguageComments.lineCommentFor("text/x-lua")).isEqualTo("--");
        assertThat(LanguageComments.lineCommentFor("text/x-haskell")).isEqualTo("--");
        assertThat(LanguageComments.lineCommentFor("text/x-sql")).isEqualTo("--");
        assertThat(LanguageComments.lineCommentFor("text/x-erlang")).isEqualTo("%");
    }

    @Test
    @DisplayName("An unknown or null mime has no comment prefix")
    void unknownIsNull() {
        assertThat(LanguageComments.lineCommentFor("text/x-nonesuch")).isNull();
        assertThat(LanguageComments.lineCommentFor(null)).isNull();
    }
}
