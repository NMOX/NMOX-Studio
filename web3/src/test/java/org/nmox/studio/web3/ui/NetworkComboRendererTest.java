package org.nmox.studio.web3.ui;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.web3.model.Network;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The network combo (v2.164.0). Its closed face names the network only: the
 * face is width-capped, and an HTML label narrower than its text wraps, which
 * put the chain badge on a clipped second line once the toolbar grew. The
 * open list keeps the badge, where the rows are as wide as they need to be.
 */
class NetworkComboRendererTest {

    private static JLabel render(Network network, int index) {
        return (JLabel) new Web3StudioTopComponent.NetworkRenderer()
                .getListCellRendererComponent(new JList<>(), network, index, false, false);
    }

    @Test
    @DisplayName("the closed combo is one plain line: the name, no chain badge, no markup")
    void closedFaceIsTheNameAlone() {
        JLabel face = render(new Network("Local (anvil)", 31337, false, "http://127.0.0.1:8545"), -1);
        assertThat(face.getText()).contains("Local (anvil)").doesNotContain("31337").doesNotContain("<html>");
        assertThat(face.getClientProperty(BasicHTML.propertyKey)).isNull();
    }

    @Test
    @DisplayName("a list row still carries the chain badge")
    void listRowsKeepTheBadge() {
        assertThat(render(new Network("Local (anvil)", 31337, false, "http://127.0.0.1:8545"), 0).getText())
                .contains("31337");
    }

    @Test
    @DisplayName("a network named like markup paints as text on the closed face")
    void hostileNameNeverRenders() {
        JLabel face = render(new Network("<html><img src='http://x/'>", 1, false, "http://127.0.0.1:1"), -1);
        assertThat(face.getClientProperty(BasicHTML.propertyKey)).isNull();
    }
}
