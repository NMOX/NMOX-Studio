package org.nmox.studio.ui.actions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.projectstudio.LearningCatalog;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The picker's rows fit the list they are drawn in (v2.120.0).
 *
 * <p>The walk of a fresh install photographed the catalog's front door with
 * its blurbs clipped mid-word and a horizontal scrollbar underneath: an
 * unbounded HTML label is as wide as its longest line, so the list asked
 * for more room than it had. Bounding the label makes the blurb wrap, which
 * beats eliding it — nothing is cut at all.
 */
class LearningSpacePickerCellTest {

    private static LearningCatalog.Space longest() {
        LearningCatalog.Space worst = null;
        for (LearningCatalog.Space s : LearningCatalog.all()) {
            if (worst == null || s.blurb().length() > worst.blurb().length()) {
                worst = s;
            }
        }
        return worst;
    }

    @Test
    @DisplayName("a row is bound to the width it is given, so its blurb wraps instead of clipping")
    void theRowIsBoundToItsList() {
        LearningCatalog.Space s = longest();
        assertThat(s).as("the catalog has spaces to draw").isNotNull();
        String html = NewLearningSpaceAction.cellHtml(s, 480);
        assertThat(html)
                .as("without a width the list's preferred size runs past its own viewport")
                .contains("width: 480");
        assertThat(html)
                .as("Swing's CSS honours a unitless width and IGNORES px (v2.84.0) — "
                        + "a px here is the same as no bound at all")
                .doesNotContain("px");
        assertThat(html)
                .as("wrapping keeps the whole blurb; this is not an elision")
                .contains(s.blurb().substring(s.blurb().length() - 12));
    }

    @Test
    @DisplayName("the width it is given is the width it uses")
    void theBoundFollowsTheList() {
        LearningCatalog.Space s = longest();
        assertThat(NewLearningSpaceAction.cellHtml(s, 320)).contains("width: 320");
        assertThat(NewLearningSpaceAction.cellHtml(s, 900)).contains("width: 900");
    }
}
