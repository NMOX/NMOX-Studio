package org.nmox.studio.ui.search;

import java.awt.ComponentOrientation;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.netbeans.modules.search.ui.BasicStandInResultsPanel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first real layout of a search-results split gives a tree nobody could
 * read the platform's 250 px (the platform saves a clamp from a near-zero
 * first layout: 14 px, read from a fresh userdir), and never overrules one
 * the user chose.
 */
class SearchResultsSplitTest {

    private static JSplitPane split(int width, int divider) {
        JSplitPane s = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JLabel("tree"), new JLabel("preview"));
        s.setSize(width, 200);
        s.setDividerLocation(divider);
        return s;
    }

    @Test
    @DisplayName("a tree nobody can read gets the platform's 250 px, never a fraction of the window")
    void theRule() {
        assertThat(SearchResultsSplit.divider(900, 5, 14, true)).isEqualTo(250);
        assertThat(SearchResultsSplit.divider(2560, 5, 14, true)).as("a wide monitor saves no wide value").isEqualTo(250);
        assertThat(SearchResultsSplit.divider(900, 5, 300, true)).as("a readable tree stays").isEqualTo(300);
        assertThat(SearchResultsSplit.divider(900, 5, SearchResultsSplit.MIN_READABLE, true))
                .isEqualTo(SearchResultsSplit.MIN_READABLE);
    }

    @Test
    @DisplayName("in a mirrored window the tree is right of the divider: its width is what the preview leaves")
    void mirrored() {
        assertThat(SearchResultsSplit.divider(900, 5, 881, false)).as("a 14 px tree on the right").isEqualTo(645);
        assertThat(SearchResultsSplit.divider(900, 5, 360, false)).as("a readable tree, a narrow preview: left alone")
                .isEqualTo(360);
    }

    @Test
    @DisplayName("healed once, at the first real width, and not again when the user drags it narrow")
    void onceAtTheFirstRealWidth() {
        JSplitPane s = split(40, 14);
        SearchResultsSplit.heal(s);
        assertThat(s.getDividerLocation()).as("a split not yet laid out at a real width waits").isEqualTo(14);
        s.setSize(900, 200);
        SearchResultsSplit.heal(s);
        assertThat(s.getDividerLocation()).isEqualTo(250);
        s.setDividerLocation(30);
        SearchResultsSplit.heal(s);
        assertThat(s.getDividerLocation()).as("the user's own choice").isEqualTo(30);
    }

    @Test
    @DisplayName("a mirrored split heals the tree's side")
    void mirroredSplitHeals() {
        JSplitPane s = split(900, 881);
        s.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        SearchResultsSplit.heal(s);
        assertThat(s.getDividerLocation()).isEqualTo(900 - s.getDividerSize() - 250);
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
    @DisplayName("watching the window finds a results split added later, and heals it when it gets its width")
    void watchFollowsLaterTabs() {
        JPanel window = new JPanel();
        SearchResultsSplit.watch(window);
        BasicStandInResultsPanel results = new BasicStandInResultsPanel();
        JSplitPane s = split(40, 14);
        results.add(s);
        window.add(results);   // a later search adds its tab
        s.setSize(900, 200);
        for (java.awt.event.ComponentListener l : s.getComponentListeners()) {
            l.componentResized(new java.awt.event.ComponentEvent(s, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
        }
        assertThat(s.getDividerLocation()).isEqualTo(250);
    }

    @Test
    @DisplayName("only the platform's results panels are touched: another split keeps its divider")
    void onlySearchResults() {
        JPanel host = new JPanel();
        JSplitPane s = split(900, 14);
        host.add(s);
        assertThat(SearchResultsSplit.inSearchResults(s)).isFalse();
        SearchResultsSplit.watch(host);
        assertThat(s.getComponentListeners()).as("no resize listener on a split that is not the platform's")
                .hasSameSizeAs(new JSplitPane().getComponentListeners());
        assertThat(s.getDividerLocation()).isEqualTo(14);
    }
}
