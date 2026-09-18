package org.nmox.studio.editor.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The argument set across dialects: i18next's double braces (with the
 * unescape sigil and a format), ICU's single braces and typed
 * arguments (name kept, option bodies skipped), numbered and printf and
 * Ruby-style forms, and the refusals — an unbalanced brace, an empty
 * pair, a quoted ICU brace — contribute nothing.
 */
class IcuArgsTest {

    @Test
    @DisplayName("i18next: {{name}}, {{- html}}, {{count, number}} → the names")
    void i18nextForms() {
        assertThat(IcuArgs.names("Hi {{name}}, {{- html}} and {{count, number}}"))
                .containsExactly("count", "html", "name");
    }

    @Test
    @DisplayName("ICU: the typed argument's NAME is collected, its option bodies are not")
    void icuTypedArguments() {
        String icu = "{count, plural, one {# item for {who}} other {# items}} by {author}";
        assertThat(IcuArgs.names(icu)).containsExactly("author", "count");
        assertThat(IcuArgs.hasIcu(icu)).isTrue();
        assertThat(IcuArgs.hasIcu("{gender, select, male {he} other {they}}")).isTrue();
        assertThat(IcuArgs.hasIcu("{n, selectordinal, one {#st} other {#th}}")).isTrue();
        assertThat(IcuArgs.hasIcu("Hi {name}")).isFalse();
        assertThat(IcuArgs.hasIcu("{n, number}")).isFalse();
    }

    @Test
    @DisplayName("numbered, printf and Ruby-style placeholders")
    void otherDialects() {
        assertThat(IcuArgs.names("{0} of {1}")).containsExactly("0", "1");
        assertThat(IcuArgs.names("%s has %d items")).containsExactly("%d", "%s");
        assertThat(IcuArgs.names("%1$s wins")).containsExactly("%1$s");
        assertThat(IcuArgs.names("Hallo %{name}")).containsExactly("name");
    }

    @Test
    @DisplayName("refusals: unbalanced, empty, quoted and whitespace-bearing bodies are prose, not arguments")
    void refusals() {
        assertThat(IcuArgs.names("a { b")).isEmpty();
        assertThat(IcuArgs.names("a {} b")).isEmpty();
        assertThat(IcuArgs.names("literal '{' brace")).isEmpty();
        assertThat(IcuArgs.names("{not an arg}")).isEmpty();
        assertThat(IcuArgs.names(null)).isEmpty();
        assertThat(IcuArgs.hasIcu(null)).isFalse();
        assertThat(IcuArgs.hasIcu("a { b")).isFalse();
    }

    @Test
    @DisplayName("the set is a set: order and repetition do not make a difference")
    void setSemantics() {
        assertThat(IcuArgs.names("{b} {a} {b}")).isEqualTo(IcuArgs.names("{a} and {b}"));
    }
}
