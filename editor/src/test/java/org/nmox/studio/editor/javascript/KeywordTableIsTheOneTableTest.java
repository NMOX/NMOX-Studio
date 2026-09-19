package org.nmox.studio.editor.javascript;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * There is one JavaScript keyword table, and {@code keyword} is not a keyword.
 *
 * <p>{@code JavaScriptLexer} carried a second one: a {@code KEYWORD_CACHE}
 * built by walking {@code JavaScriptTokenId} for constants whose
 * {@code primaryCategory()} starts with "keyword", keyed by the constant's own
 * name lowercased. Exactly one constant matches — {@code KEYWORD("keyword")} —
 * so the whole map was the single entry {@code {"keyword" -> KEYWORD}}, keyed
 * by the name of an enum constant rather than by any word in the language.
 *
 * <p>It was consulted before the real table on every identifier, so it missed
 * every actual keyword and fell through. That made it dead — except for one
 * input, where it was worse than dead: the identifier {@code keyword} hit it
 * and was emitted as {@code KEYWORD}, so {@code const keyword = 1;} painted
 * {@code keyword} in keyword colour. It is an ordinary identifier in
 * JavaScript, reserved nowhere.
 *
 * <p>The class javadoc's "optimized for performance with caching" described
 * work that was never finished. The real table is
 * {@code JavaScriptLanguageHierarchy}'s flyweight map, already a static
 * {@code HashMap} — there was nothing left to cache.
 */
class KeywordTableIsTheOneTableTest {

    @Test
    @DisplayName("`keyword` is an ordinary identifier, not a JavaScript keyword")
    void keywordIsNotAKeyword() {
        assertThat(JavaScriptLanguageHierarchy.getToken("keyword"))
                .as("nothing reserves the word `keyword` in JavaScript; "
                        + "a lexer that colours it is colouring a variable name")
                .isNull();
    }

    @Test
    @DisplayName("the words that ARE keywords resolve through that one table")
    void therealKeywordsResolve() {
        for (String word : new String[]{"const", "function", "let", "return", "class",
            "typeof", "await", "this", "super"}) {
            assertThat(JavaScriptLanguageHierarchy.getToken(word))
                    .as("%s is a JavaScript keyword and the one table must know it", word)
                    .isEqualTo(JavaScriptTokenId.KEYWORD);
        }
    }

    @Test
    @DisplayName("only one token id is categorised as a keyword, which is why a by-category map could only ever hold one wrong entry")
    void onlyOneTokenIdIsCategorisedKeyword() {
        long keywordCategory = java.util.Arrays.stream(JavaScriptTokenId.values())
                .filter(t -> t.primaryCategory().startsWith("keyword"))
                .count();
        assertThat(keywordCategory)
                .as("the deleted cache derived its keys from this set; one constant means one entry")
                .isEqualTo(1);
    }
}
