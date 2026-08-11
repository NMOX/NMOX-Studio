package org.nmox.studio.editor.completion;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Component-selector completion (Angular-top arc): typing {@code <app-}
 * offers the project's own selectors. The prefix parser and the match
 * filter are pure; a wrong verdict either spams plain HTML (false
 * positive) or hides the project's components (false negative).
 */
class NgSelectorCompletionTest {

    @Test
    @DisplayName("tagPrefix: tag-name positions only")
    void tagPrefix() {
        assertThat(NgTemplateCompletion.tagPrefix("  <app-")).isEqualTo("app-");
        assertThat(NgTemplateCompletion.tagPrefix("<")).isEmpty();
        assertThat(NgTemplateCompletion.tagPrefix("<div><ap")).isEqualTo("ap");
        assertThat(NgTemplateCompletion.tagPrefix("</app-"))
                .as("closing tags belong to the HTML provider").isNull();
        assertThat(NgTemplateCompletion.tagPrefix("<div "))
                .as("past the tag name = attribute position").isNull();
        assertThat(NgTemplateCompletion.tagPrefix("<div>text"))
                .as("after a closed tag there is no open <").isNull();
        assertThat(NgTemplateCompletion.tagPrefix(null)).isNull();
    }

    @Test
    @DisplayName("selectorMatches: comma-lists split, attribute forms and dashless names out, deduped")
    void matches() {
        List<String> declared = List.of(
                "app-hero", "app-a, app-b", "[app-track]", "plain", "app-a");
        assertThat(NgTemplateCompletion.selectorMatches(declared, "app-"))
                .containsExactly("app-a", "app-b", "app-hero");
        assertThat(NgTemplateCompletion.selectorMatches(declared, "app-b"))
                .containsExactly("app-b");
        assertThat(NgTemplateCompletion.selectorMatches(declared, ""))
                .as("bare < offers every element selector — never a DASHED [attr] form, never dashless")
                .containsExactly("app-a", "app-b", "app-hero");
        assertThat(NgTemplateCompletion.selectorMatches(declared, "x"))
                .isEmpty();
    }
}
