package org.nmox.studio.editor.symbols.search;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.outline.OutlineKind;
import org.nmox.studio.editor.outline.OutlineModel;
import org.nmox.studio.editor.outline.OutlineModel.Item;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which of a file's symbols answer a Quick Search query: the product's
 * one matcher, VS Code's {@code @}, the order and the bound.
 */
class FileSymbolsTest {

    private static Item fn(String name, int line) {
        return new Item(OutlineKind.FUNCTION, name, null, line, 0);
    }

    private static List<String> names(String typed, List<Item> items) {
        return FileSymbols.matching(typed, items).stream().map(Item::name).toList();
    }

    private static final List<Item> FILE = List.of(
            fn("getUserById", 3),
            fn("saveUser", 10),
            fn("users", 20),
            new Item(OutlineKind.SELECTOR, ".hero-banner", null, 30, 0),
            new Item(OutlineKind.TARGET, "GET /health", "healthCheck", 40, 0),
            fn("log", 50));

    @Test
    @DisplayName("it is the product's matcher: words in any order, camelCase and hyphens split, plurals reach singulars")
    void usesTheSharedMatcher() {
        // a phrase whose words are apart and out of order in the name: no substring test finds this
        assertThat(names("id user", FILE)).containsExactly("getUserById");
        // a hyphenated selector by one of its words, sigil and all
        assertThat(names("banner", FILE)).containsExactly(".hero-banner");
        // a plural query reaches the singular word
        assertThat(names("logs", FILE)).containsExactly("log");
        // a two-letter term must start a word: "er" is inside "user" and "banner", and finds nothing
        assertThat(names("er", FILE)).isEmpty();
        // the outline's detail is searched too
        assertThat(names("health check", FILE)).containsExactly("GET /health");
    }

    @Test
    @DisplayName("a symbol whose words match whole lists before one that matches by prefix, each group in file order")
    void wholeWordsFirst() {
        assertThat(names("user", FILE)).containsExactly("getUserById", "saveUser", "users");
        List<Item> reordered = List.of(fn("users", 1), fn("userName", 2), fn("superuser", 3), fn("user", 4));
        // "superuser" only holds the word inside another; the plural, the camelCase part and the word itself are it
        assertThat(names("user", reordered)).containsExactly("users", "userName", "user", "superuser");
    }

    @Test
    @DisplayName("@ is VS Code's prefix: it is dropped before matching, and alone it lists the file from the top")
    void theAtPrefix() {
        assertThat(names("@user", FILE)).containsExactly("getUserById", "saveUser", "users");
        assertThat(names("@ user", FILE)).containsExactly("getUserById", "saveUser", "users");
        assertThat(names("@", FILE)).containsExactly(
                "getUserById", "saveUser", "users", ".hero-banner", "GET /health", "log");
        // a CSS at-rule is still found by its name
        List<Item> css = List.of(new Item(OutlineKind.RULE, "@media (min-width: 40em)", null, 1, 0));
        assertThat(names("@media", css)).containsExactly("@media (min-width: 40em)");
    }

    @Test
    @DisplayName("without the prefix one character lists nothing; with it one character searches")
    void shortQueries() {
        assertThat(names("l", FILE)).isEmpty();
        assertThat(names("@l", FILE)).containsExactly("log");
        assertThat(names("", FILE)).isEmpty();
        assertThat(names("   ", FILE)).isEmpty();
        assertThat(names(null, FILE)).isEmpty();
    }

    @Test
    @DisplayName("at most thirty rows, whichever way they were found")
    void bounded() {
        List<Item> many = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            many.add(fn("handler" + i, i));
        }
        assertThat(FileSymbols.matching("@", many)).hasSize(FileSymbols.MAX_RESULTS);
        assertThat(FileSymbols.matching("handler", many)).hasSize(FileSymbols.MAX_RESULTS);
        assertThat(FileSymbols.matching("@", many).get(0).name()).isEqualTo("handler0");
    }

    @Test
    @DisplayName("the rows come from the Navigator's own outline of the text")
    void fromARealOutline() {
        List<Item> items = OutlineModel.extract("text/javascript",
                "class Cart {\n  addItem(item) {\n  }\n}\nfunction checkout() {\n}\n");
        assertThat(names("@", items)).contains("Cart", "addItem", "checkout");
        assertThat(names("add item", items)).containsExactly("addItem");
    }
}
