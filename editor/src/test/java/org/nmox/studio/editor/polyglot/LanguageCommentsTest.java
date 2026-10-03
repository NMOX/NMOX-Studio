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
        assertThat(LanguageComments.lineCommentFor("text/x-solidity")).isEqualTo("//");
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

    @Test
    @DisplayName("Svelte and Vue carry the HTML block pair, not a line prefix")
    void markupDialectsUseBlockPair() {
        for (String mime : new String[]{"text/x-svelte", "text/x-vue"}) {
            assertThat(LanguageComments.lineCommentFor(mime)).as(mime).isNull();
            LanguageComments.BlockComment block = LanguageComments.blockCommentFor(mime);
            assertThat(block).as(mime).isNotNull();
            assertThat(block.open()).isEqualTo("<!--");
            assertThat(block.close()).isEqualTo("-->");
        }
    }

    @Test
    @DisplayName("The block pair of a language that also has a line comment is answered separately, only where it is certain")
    void blockPairOfLineCommentLanguages() {
        for (String mime : new String[]{"text/javascript", "text/typescript", "text/x-java", "text/css", "text/x-scss"}) {
            LanguageComments.BlockComment block = LanguageComments.blockPairFor(mime);
            assertThat(block).as(mime).isNotNull();
            assertThat(block.open()).as(mime).isEqualTo("/*");
            assertThat(block.close()).as(mime).isEqualTo("*/");
        }
        for (String mime : new String[]{"text/html", "text/x-ng-template", "text/x-vue", "text/x-svelte"}) {
            assertThat(LanguageComments.blockPairFor(mime).open()).as(mime).isEqualTo("<!--");
            assertThat(LanguageComments.blockPairFor(mime).close()).as(mime).isEqualTo("-->");
        }
        // a line comment does not imply the C pair: these have none, or another
        for (String mime : new String[]{"text/x-python", "text/x-gleam", "text/x-prisma", "text/x-fsharp",
            "text/x-pascal", "text/x-nonesuch", null}) {
            assertThat(LanguageComments.blockPairFor(mime)).as(String.valueOf(mime)).isNull();
        }
        assertThat(LanguageComments.blockCommentFor("text/javascript"))
                .as("the toggle's table is untouched: a line-comment language still toggles by line").isNull();
    }

    @Test
    @DisplayName("An unknown or null mime has no block pair either")
    void unknownHasNoBlockPair() {
        assertThat(LanguageComments.blockCommentFor("text/x-nonesuch")).isNull();
        assertThat(LanguageComments.blockCommentFor("text/javascript")).isNull();
        assertThat(LanguageComments.blockCommentFor(null)).isNull();
    }
}
