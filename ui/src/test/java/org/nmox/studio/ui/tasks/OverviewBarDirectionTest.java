package org.nmox.studio.ui.tasks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A column's count bar grows away from the column's name (v2.151.0).
 *
 * <p>The row puts the name at the line start with logical sides, so in a
 * right-to-left board the name sits on the right; a bar still filling from
 * x = 0 would grow toward the count instead of out of the name.
 */
class OverviewBarDirectionTest {

    @Test
    @DisplayName("the fill starts at the reader's line start")
    void fillStartsAtTheLineStart() {
        assertThat(OverviewPanel.fillStart(true, 80, 30)).isZero();
        assertThat(OverviewPanel.fillStart(false, 80, 30))
                .as("right to left: the fill ends at the right edge").isEqualTo(50);
        assertThat(OverviewPanel.fillStart(false, 80, 80)).isZero();
    }
}
