package org.nmox.studio.ui.search;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first real layout of a search-results split moves a divider too narrow
 * to read (the platform saves a clamp from a near-zero first layout: 14 px,
 * read from a fresh userdir), and never overrules one the user chose.
 */
class SearchResultsSplitTest {

    private static JSplitPane split(int width, int divider) {
        JSplitPane s = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JLabel("tree"), new JLabel("preview"));
        s.setSize(width, 200);
        s.setDividerLocation(divider);
        return s;
    }

    @Test
    @DisplayName("a divider nobody can read moves to the platform's 250 px, or 40% of a wide window")
    void theRule() {
        assertThat(SearchResultsSplit.divider(900, 14)).isEqualTo(360);
        assertThat(SearchResultsSplit.divider(560, 14)).isEqualTo(250);
        assertThat(SearchResultsSplit.divider(900, 300)).as("a readable divider stays").isEqualTo(300);
        assertThat(SearchResultsSplit.divider(900, SearchResultsSplit.MIN_READABLE)).isEqualTo(SearchResultsSplit.MIN_READABLE);
    }

    @Test
    @DisplayName("healed once, at the first real width, and not again when the user drags it narrow")
    void onceAtTheFirstRealWidth() {
        JSplitPane s = split(40, 14);
        SearchResultsSplit.heal(s);
        assertThat(s.getDividerLocation()).as("a split not yet laid out at a real width waits").isEqualTo(14);
        s.setSize(900, 200);
        SearchResultsSplit.heal(s);
        assertThat(s.getDividerLocation()).isEqualTo(360);
        s.setDividerLocation(30);
        SearchResultsSplit.heal(s);
        assertThat(s.getDividerLocation()).as("the user's own choice").isEqualTo(30);
    }

    @Test
    @DisplayName("with the preview off the tree has the whole width, and nothing is moved")
    void previewOff() {
        JSplitPane s = split(900, 14);
        s.setRightComponent(null);
        SearchResultsSplit.heal(s);
        assertThat(s.getDividerLocation()).isEqualTo(14);
    }

    @Test
    @DisplayName("only the platform's results panels are touched: another split keeps its divider")
    void onlySearchResults() {
        JPanel host = new JPanel();
        JSplitPane s = split(900, 14);
        host.add(s);
        assertThat(SearchResultsSplit.inSearchResults(s)).isFalse();
    }
}
