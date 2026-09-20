package org.nmox.studio.core.util;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The one home for "which directories does a walk skip" (ledger 110).
 *
 * <p>The population gate lives in {@code application}'s
 * {@code HeavyDirsLedgerTest}, which reads every module's sources and
 * fails when a fourteenth walk declares its own private answer. What is
 * tested HERE is the seam that made merging thirteen answers into one
 * possible: a caller differs from every other caller by ADDING, never by
 * re-declaring.
 */
class HeavyDirsTest {

    @Test
    @DisplayName("the base carries build output in every spelling the toolchains use")
    void theBaseCarriesBuildOutput() {
        assertThat(HeavyDirs.NAMES).contains(
                "node_modules", ".git", "dist", "build", "coverage",
                "target", "out", ".next", ".nuxt", ".svelte-kit", ".angular",
                "__pycache__", ".venv");
        assertThat(HeavyDirs.isHeavy("target")).isTrue();
        assertThat(HeavyDirs.isHeavy("out")).isTrue();
        assertThat(HeavyDirs.isHeavy("src")).isFalse();
    }

    /**
     * The three names deliberately left to their callers, each because
     * SOME walk must descend into it. A regression here is not a tidy-up:
     * promoting {@code vendor} would make the Project Studio tree refuse
     * to expand the directory the Classic Kit had just written jQuery
     * into, and promoting {@code .nmox} would hide a learning space from
     * the tree a person browses it with.
     */
    @Test
    @DisplayName("the names a caller owns stay out of the base")
    void theCallerOwnedNamesStayOut() {
        assertThat(HeavyDirs.NAMES)
                .as("the Classic Kit writes vendor/jquery-3.7.1.min.js and wires a "
                        + "script tag at it — the file tree must expand vendor/")
                .doesNotContain("vendor")
                .as(".nmox holds the IDE's own state, and a learning space under it "
                        + "is browsed with the very tree that reads these names")
                .doesNotContain(".nmox")
                .as(".cache and .idea are another tool's churn, and matter only to a "
                        + "watcher deciding whether the SOURCE changed")
                .doesNotContain(".cache", ".idea");
    }

    @Test
    @DisplayName("a caller's extras are added to the base, never instead of it")
    void plusOnlyEverAdds() {
        Set<String> withExtras = HeavyDirs.plus("vendor", ".nmox");

        assertThat(withExtras).containsAll(HeavyDirs.NAMES);
        assertThat(withExtras).contains("vendor", ".nmox");
        assertThat(withExtras).hasSize(HeavyDirs.NAMES.size() + 2);
    }

    @Test
    @DisplayName("no extras is the base itself, so a caller with none says so plainly")
    void noExtrasIsTheBase() {
        assertThat(HeavyDirs.plus()).isEqualTo(HeavyDirs.NAMES);
    }

    @Test
    @DisplayName("an extra that is already in the base changes nothing")
    void aRedundantExtraIsHarmless() {
        assertThat(HeavyDirs.plus("target")).isEqualTo(HeavyDirs.NAMES);
    }

    /**
     * Both the base and a caller's set are handed out to walks that run on
     * their own threads. If either could be mutated, one walk's extra
     * would silently become every walk's.
     */
    @Test
    @DisplayName("neither the base nor a caller's set can be mutated")
    void theSetsAreImmutable() {
        assertThatThrownBy(() -> HeavyDirs.NAMES.add("src"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> HeavyDirs.plus("vendor").add("src"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
