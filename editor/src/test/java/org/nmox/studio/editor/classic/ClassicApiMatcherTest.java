package org.nmox.studio.editor.classic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The matcher is what makes {@code $.aj} find {@code $.ajax} - the
 * standard identifier walk stops dead at the dot. Pure function pins:
 * the dotted prefix walk, the direct rule, the dot-tail rule for
 * instance methods after a receiver, and the replace lengths that keep
 * accepted completions from eating the receiver.
 */
class ClassicApiMatcherTest {

    // ---- prefix walk -------------------------------------------------------

    @Test
    @DisplayName("prefixAt walks dots and sigils, stopping at other punctuation")
    void prefixWalk() {
        assertThat(ClassicApiMatcher.prefixAt("$.aj", 4)).isEqualTo("$.aj");
        assertThat(ClassicApiMatcher.prefixAt("_.deb", 5)).isEqualTo("_.deb");
        assertThat(ClassicApiMatcher.prefixAt("x = $(li).addC", 14)).isEqualTo(".addC"); // paren stops it
        assertThat(ClassicApiMatcher.prefixAt("myEl.addC", 9)).isEqualTo("myEl.addC");
        assertThat(ClassicApiMatcher.prefixAt("$$", 2)).isEqualTo("$$");
        assertThat(ClassicApiMatcher.prefixAt("f( ", 3)).isEmpty();
        assertThat(ClassicApiMatcher.prefixAt("ab", 99)).isEqualTo("ab"); // clamped
    }

    // ---- direct rule -------------------------------------------------------

    @Test
    @DisplayName("Direct rule: entry starts with the typed prefix, case-insensitively")
    void directRule() {
        assertThat(ClassicApiMatcher.matchLength("$.ajax", "$.aj")).isEqualTo(4);
        assertThat(ClassicApiMatcher.matchLength("_.debounce", "_.deb")).isEqualTo(5);
        assertThat(ClassicApiMatcher.matchLength("ko.observable", "ko.observ")).isEqualTo(9);
        assertThat(ClassicApiMatcher.matchLength("$.getJSON", "$.getjson")).isEqualTo(9);
        assertThat(ClassicApiMatcher.matchLength("Backbone.Model.extend", "Backbone.Model.ext")).isEqualTo(18);
        assertThat(ClassicApiMatcher.matchLength("$$", "$$")).isEqualTo(2);
        assertThat(ClassicApiMatcher.matchLength(".addClass", ".addC")).isEqualTo(5);
    }

    // ---- dot-tail rule -----------------------------------------------------

    @Test
    @DisplayName("Dot-tail rule: instance entries match the last .segment after a receiver")
    void dotTailRule() {
        // replace only ".addC" (5 chars) so the receiver survives the accept
        assertThat(ClassicApiMatcher.matchLength(".addClass", "myEl.addC")).isEqualTo(5);
        assertThat(ClassicApiMatcher.matchLength(".fadeIn", "container.fadeI")).isEqualTo(6);
        // a bare dot after a receiver browses every instance method
        assertThat(ClassicApiMatcher.matchLength(".on", "el.")).isEqualTo(1);
        // statics never match via the tail: "el.ajax" should not offer $.ajax
        assertThat(ClassicApiMatcher.matchLength("$.ajax", "el.ajax")).isEqualTo(-1);
    }

    // ---- non-matches -------------------------------------------------------

    @Test
    @DisplayName("Unrelated prefixes do not match; empty prefix matches everything")
    void nonMatches() {
        assertThat(ClassicApiMatcher.matchLength("$.ajax", "xyz")).isEqualTo(-1);
        assertThat(ClassicApiMatcher.matchLength(".addClass", "removeC")).isEqualTo(-1);
        assertThat(ClassicApiMatcher.matchLength("_.each", "$.each")).isEqualTo(-1);
        assertThat(ClassicApiMatcher.matchLength("$.ajax", "")).isZero();
        assertThat(ClassicApiMatcher.matchLength(".addClass", "")).isZero();
    }
}
