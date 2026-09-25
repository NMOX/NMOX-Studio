package org.nmox.studio.editor.spell;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A commit message is prose, and the only prose in the file (3.2.0): the
 * message scope is checked, git's template and a verbose diff are not.
 * The scope stacks here are the ones GitGrammarsTokenizeTest reads out of
 * TM4E for the vendored grammar.
 */
class GitMessageSpellTokenListProviderTest {

    @Test
    @DisplayName("the message the author writes is checked")
    void messageIsProse() {
        assertThat(GitMessageSpellTokenListProvider.isMessageScope(
                List.of("text.git-commit", "meta.scope.message.git-commit"))).isTrue();
        assertThat(GitMessageSpellTokenListProvider.isMessageScope(
                List.of("text.git-commit", "meta.scope.message.git-commit",
                        "meta.scope.subject.git-commit"))).isTrue();
    }

    @Test
    @DisplayName("git's # template lines are not checked — they are git's words, not the author's")
    void templateIsNot() {
        assertThat(GitMessageSpellTokenListProvider.isMessageScope(
                List.of("text.git-commit", "meta.scope.metadata.git-commit",
                        "comment.line.number-sign.git-commit"))).isFalse();
    }

    @Test
    @DisplayName("a commit -v diff is code, never prose")
    void diffIsNot() {
        assertThat(GitMessageSpellTokenListProvider.isMessageScope(
                List.of("text.git-commit", "meta.embedded.diff.git-commit", "source.diff")))
                .isFalse();
    }

    @Test
    @DisplayName("an unlexed token is not evidence of a sentence")
    void unlexedIsNot() {
        assertThat(GitMessageSpellTokenListProvider.isMessageScope(null)).isFalse();
    }
}
