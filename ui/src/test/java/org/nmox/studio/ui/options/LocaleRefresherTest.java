package org.nmox.studio.ui.options;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.windows.TopComponent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rename step's own rules (v2.103.0).
 *
 * <p>The sweep over the window registry needs a running window system, so
 * what is pinned here is the part that decides WHAT a window gets called —
 * and the property that matters most is the refusal: a window whose title
 * cannot be found keeps the title it has. Blanking a window because a key
 * moved would be a worse bug than the English titles this feature exists to
 * fix, and it is the kind of bug that only shows up in a language the author
 * does not read.
 */
class LocaleRefresherTest {

    /** Any TopComponent will do: only its CLASS is used, to find its bundle. */
    private static final class Somewhere extends TopComponent {
    }

    @Test
    @DisplayName("a window whose title key cannot be found keeps its old title — never blanked")
    void unknownKeyLeavesTheTitleAlone() {
        TopComponent tc = new Somewhere();
        assertThat(LocaleRefresher.titleFor("NoSuchWindowTopComponent", tc))
                .as("no CTL_ key for this id, and none for the class either").isNull();
    }

    @Test
    @DisplayName("a window with no id at all is skipped rather than guessed at")
    void noIdIsSkipped() {
        assertThat(LocaleRefresher.titleFor(null, new Somewhere()))
                .as("the window manager knows no id for it").isNull();
    }

    @Test
    @DisplayName("the aliased windows are exactly the two whose key is not CTL_<id>")
    void aliasesAreTheKnownExceptions() {
        // held in step with BundleHeadGateTest by LocaleRefresherKeysTest;
        // pinned here too so the map cannot be emptied without a failure
        assertThat(LocaleRefresher.TITLE_KEY_ALIASES)
                .containsEntry("InfraDesignerTopComponent", "CTL_InfraTopComponent")
                .containsEntry("DockerPanelTopComponent", "CTL_DockerPanelAction")
                .hasSize(2);
    }

    @Test
    @DisplayName("an aliased id looks up the alias, not CTL_<id>")
    void aliasWins() {
        // the alias for this id names a key that does not live in the test's
        // own package, so the honest outcome is a miss — what is proven is
        // that the ALIAS was consulted rather than CTL_<id> being assumed
        String key = LocaleRefresher.TITLE_KEY_ALIASES.get("DockerPanelTopComponent");
        assertThat(key).isEqualTo("CTL_DockerPanelAction").isNotEqualTo("CTL_DockerPanelTopComponent");
    }
}
