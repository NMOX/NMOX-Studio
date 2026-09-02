package org.nmox.studio.editor.outline;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StickyScopeTest {

    private static final String JS = String.join("\n",
            "class Cart {",                 // 0
            "  total() {",                  // 1
            "    let s = 0; // } not a brace",
            "    return s;",                // 3
            "  }",                          // 4
            "  clear() {",                  // 5
            "    this.items = [];",         // 6
            "  }",                          // 7
            "}",                            // 8
            "",                             // 9
            "function helper() {",          // 10
            "  return '{';",                // 11
            "}",                            // 12
            "const after = 1;");            // 13

    private static List<String> lines(String text) {
        return List.of(text.split("\n", -1));
    }

    @Test
    @DisplayName("Brace families end where the brace balance closes; strings and comments are blanked")
    void braceRanges() {
        List<OutlineModel.Item> items = OutlineModel.extract("text/javascript", JS);
        List<String> lines = lines(JS);
        int[] ends = StickyScope.endLines("js", lines, items);
        assertThat(items).extracting(OutlineModel.Item::name).contains("Cart", "total", "clear", "helper");
        int cart = indexOf(items, "Cart");
        int total = indexOf(items, "total");
        int helper = indexOf(items, "helper");
        assertThat(ends[cart]).isEqualTo(8);
        assertThat(ends[total]).isEqualTo(4);
        assertThat(ends[helper]).isEqualTo(12);
    }

    @Test
    @DisplayName("The enclosing chain is outermost-first, contains the line, and a closed scope is never pinned")
    void enclosingChain() {
        List<OutlineModel.Item> items = OutlineModel.extract("text/javascript", JS);
        int[] ends = StickyScope.endLines("js", lines(JS), items);
        assertThat(StickyScope.enclosing(items, ends, 3, 3)).extracting(OutlineModel.Item::name)
                .containsExactly("Cart", "total");
        assertThat(StickyScope.enclosing(items, ends, 6, 3)).extracting(OutlineModel.Item::name)
                .containsExactly("Cart", "clear");
        assertThat(StickyScope.enclosing(items, ends, 9, 3)).isEmpty();
        assertThat(StickyScope.enclosing(items, ends, 11, 3)).extracting(OutlineModel.Item::name)
                .containsExactly("helper");
        assertThat(StickyScope.enclosing(items, ends, 13, 3)).isEmpty();
    }

    @Test
    @DisplayName("A row budget keeps the innermost rows")
    void rowBudgetKeepsInnermost() {
        List<OutlineModel.Item> items = OutlineModel.extract("text/javascript", JS);
        int[] ends = StickyScope.endLines("js", lines(JS), items);
        assertThat(StickyScope.enclosing(items, ends, 3, 1)).extracting(OutlineModel.Item::name)
                .containsExactly("total");
    }

    @Test
    @DisplayName("Indentation families end at the last deeper-indented line, blank lines riding along")
    void indentRanges() {
        String py = String.join("\n",
                "class A:",          // 0
                "    def m(self):",  // 1
                "        pass",      // 2
                "",                  // 3
                "        return 1",  // 4
                "    x = 2",         // 5
                "top = 3");          // 6
        List<String> lines = lines(py);
        assertThat(StickyScope.indentEnd(lines, 0)).isEqualTo(5);
        assertThat(StickyScope.indentEnd(lines, 1)).isEqualTo(4);
    }

    @Test
    @DisplayName("Other families end where the next same-or-shallower item begins")
    void nextItemRule() {
        String md = String.join("\n", "# One", "text", "## Sub", "more", "# Two", "tail");
        List<OutlineModel.Item> items = OutlineModel.extract("text/x-markdown", md);
        int[] ends = StickyScope.endLines("markdown", lines(md), items);
        int one = indexOf(items, "One");
        int sub = indexOf(items, "Sub");
        int two = indexOf(items, "Two");
        assertThat(ends[one]).isEqualTo(3);
        assertThat(ends[sub]).isEqualTo(3);
        assertThat(ends[two]).isEqualTo(5);
        assertThat(StickyScope.enclosing(items, ends, 3, 3)).extracting(OutlineModel.Item::name)
                .containsExactly("One", "Sub");
    }

    @Test
    @DisplayName("Quoted braces never count; an escaped quote does not end the string")
    void quotedBraces() {
        assertThat(StickyScope.blankQuotes("a = '{'; b = \"}\"; c")).isEqualTo("a =    ; b =    ; c");
        assertThat(StickyScope.blankQuotes("s = 'it\\'s {'; x")).isEqualTo("s =          ; x");
        List<String> lines = List.of("function f() {", "  return '{';", "}", "after");
        assertThat(StickyScope.braceEnd(lines, 0)).isEqualTo(2);
    }

    private static int indexOf(List<OutlineModel.Item> items, String name) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).name().equals(name)) {
                return i;
            }
        }
        throw new AssertionError("no item " + name + " in " + items);
    }
}
