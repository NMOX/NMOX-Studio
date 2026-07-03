package org.nmox.studio.ui.actions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The learning-space picker's pure helpers: the search box's filter
 * discipline (query hits name, family, slug, or blurb) and the HTML
 * escaping the cell renderer leans on so a space's own text can't inject
 * markup into the list.
 */
class NewLearningSpaceActionTest {

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
