package org.nmox.studio.ui.actions;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.projectstudio.LearningCatalog;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The learning-space picker's pure helpers: the search box's filter
 * discipline (query hits name, family, slug, or blurb), the HTML
 * escaping the cell renderer leans on so a space's own text can't inject
 * markup into the list, and the availability line's requires-tool
 * extraction and rendering.
 */
class NewLearningSpaceActionTest {

    private static LearningCatalog.Space space(LearningCatalog.DriverKind kind,
            List<String> command) {
        return new LearningCatalog.Space("x", "X", LearningCatalog.Category.LANGUAGE,
                "Family", "blurb",
                new LearningCatalog.Driver(kind, command, ">", List.of()),
                Map.of(), List.of(), "tutorial");
    }

    @Test
    @DisplayName("The required tool is the driver's first command token, repl or run alike")
    void requiredToolIsFirstCommandToken() {
        assertThat(NewLearningSpaceAction.requiredTool(
                space(LearningCatalog.DriverKind.REPL, List.of("clisp"))))
                .isEqualTo("clisp");
        assertThat(NewLearningSpaceAction.requiredTool(
                space(LearningCatalog.DriverKind.RUN, List.of("npm", "run", "dev"))))
                .isEqualTo("npm");
    }

    @Test
    @DisplayName("No probe target: empty commands and project-relative scripts yield null")
    void requiredToolNullWhenNothingToProbe() {
        assertThat(NewLearningSpaceAction.requiredTool(
                space(LearningCatalog.DriverKind.REPL, List.of()))).isNull();
        assertThat(NewLearningSpaceAction.requiredTool(
                space(LearningCatalog.DriverKind.REPL, List.of("bin/rails", "console"))))
                .as("bin/rails cannot exist before the space is generated").isNull();
        assertThat(NewLearningSpaceAction.requiredTool(
                space(LearningCatalog.DriverKind.REPL, List.of("  ")))).isNull();
    }

    @Test
    @DisplayName("The availability line: found is a plain checkmark verdict")
    void availabilityTextFound() {
        assertThat(NewLearningSpaceAction.availabilityText("clisp", true, "brew install clisp"))
                .isEqualTo("requires clisp — ✓ found");
    }

    @Test
    @DisplayName("The availability line: a miss shows the OS-appropriate install command")
    void availabilityTextMissingWithHint() {
        assertThat(NewLearningSpaceAction.availabilityText("clisp", false, "brew install clisp"))
                .isEqualTo("requires clisp — ✗ not found · brew install clisp");
    }

    @Test
    @DisplayName("The availability line: a miss without a hint stays honest and bare")
    void availabilityTextMissingWithoutHint() {
        assertThat(NewLearningSpaceAction.availabilityText("clisp", false, ""))
                .isEqualTo("requires clisp — ✗ not found");
        assertThat(NewLearningSpaceAction.availabilityText("clisp", false, null))
                .isEqualTo("requires clisp — ✗ not found");
    }

    @Test
    @DisplayName("A query matches on the space name (case-insensitive)")
    void matchesOnName() {
        assertThat(NewLearningSpaceAction.matches(
                "Rust", "Systems", "rust", "A fast systems language", "rus"))
                .isTrue();
    }

    @Test
    @DisplayName("A query matches on the family")
    void matchesOnFamily() {
        assertThat(NewLearningSpaceAction.matches(
                "Svelte", "JavaScript", "svelte", "A compiler-first framework", "javascript"))
                .isTrue();
    }

    @Test
    @DisplayName("A query matches on the slug (already lower-cased)")
    void matchesOnSlug() {
        assertThat(NewLearningSpaceAction.matches(
                "Ruby on Rails", "Ruby", "rails", "Convention over configuration", "rails"))
                .isTrue();
    }

    @Test
    @DisplayName("A query matches on the blurb")
    void matchesOnBlurb() {
        assertThat(NewLearningSpaceAction.matches(
                "Go", "Systems", "go", "Goroutines and channels", "goroutine"))
                .isTrue();
    }

    @Test
    @DisplayName("A query that appears in none of the four fields does not match")
    void noMatchWhenAbsent() {
        assertThat(NewLearningSpaceAction.matches(
                "Python", "Scripting", "python", "Batteries included", "haskell"))
                .isFalse();
    }

    @Test
    @DisplayName("Escaping neutralizes the three markup-significant characters")
    void escapesMarkup() {
        assertThat(NewLearningSpaceAction.escape("a & b < c > d"))
                .isEqualTo("a &amp; b &lt; c &gt; d");
    }

    @Test
    @DisplayName("Ampersands are escaped first so entities don't double-escape")
    void escapesAmpersandBeforeAngles() {
        assertThat(NewLearningSpaceAction.escape("<tag>&"))
                .isEqualTo("&lt;tag&gt;&amp;");
    }

    @Test
    @DisplayName("Text with no special characters passes through untouched")
    void leavesPlainTextAlone() {
        assertThat(NewLearningSpaceAction.escape("plain text 123"))
                .isEqualTo("plain text 123");
    }
}
