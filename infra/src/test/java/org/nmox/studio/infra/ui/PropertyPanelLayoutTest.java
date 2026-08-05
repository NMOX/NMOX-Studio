package org.nmox.studio.infra.ui;

import javax.swing.JScrollPane;
import javax.swing.Scrollable;

import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.NodeKind;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 75, second instance (v1.271.0's walk find, closed v1.273.0):
 * the property form sat in a JScrollPane as a plain panel, so it
 * rendered at PREFERRED width — the weightx=1 editor column never
 * squeezed and Name/Size clipped behind a horizontal scrollbar at the
 * default panel width. The form must track the viewport width so
 * GridBag shrinks the editors to fit.
 */
class PropertyPanelLayoutTest {

    @Test
    @DisplayName("the property form tracks the viewport width")
    void formTracksViewportWidth() {
        InfraGraph graph = new InfraGraph();
        PropertyPanel panel = new PropertyPanel(graph);
        panel.show(graph.addNode(NodeKind.DB_POSTGRES, 0, 0));

        JScrollPane scroll = find(panel);
        assertThat(scroll).as("the form rides a scroll pane").isNotNull();
        Object view = scroll.getViewport().getView();
        assertThat(view)
                .as("a plain panel in a viewport renders at preferred width"
                        + " — the ledger-75 clip; the form must be Scrollable")
                .isInstanceOf(Scrollable.class);
        assertThat(((Scrollable) view).getScrollableTracksViewportWidth())
                .as("tracking the width is what lets GridBag squeeze the"
                        + " editor column instead of growing a horizontal"
                        + " scrollbar")
                .isTrue();
        assertThat(((Scrollable) view).getScrollableTracksViewportHeight())
                .as("height must NOT track — vertical scrolling stays real"
                        + " for kinds with many properties")
                .isFalse();
    }

    private static JScrollPane find(java.awt.Container c) {
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof JScrollPane s) {
                return s;
            }
            if (child instanceof java.awt.Container inner) {
                JScrollPane s = find(inner);
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }
}
