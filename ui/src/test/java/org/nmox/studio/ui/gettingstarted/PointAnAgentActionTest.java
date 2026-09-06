package org.nmox.studio.ui.gettingstarted;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.awt.StatusDisplayer;

import static org.assertj.core.api.Assertions.assertThat;

/** The Welcome's door to the Agent Port (v2.84.0): the rack's action when it exists, a spoken miss when it does not. */
class PointAnAgentActionTest {

    @Test
    @DisplayName("without the rack's action the door says so on the status line")
    void speaksWithoutRack() {
        new PointAnAgentAction((category, id) -> null).actionPerformed(null);
        assertThat(StatusDisplayer.getDefault().getStatusText()).contains("Agent Port").contains("not installed");
    }

    @Test
    @DisplayName("with the rack's action the door presses it, by its real id")
    void pressesTheRackAction() {
        String[] asked = {null};
        ActionEvent[] got = {null};
        ActionEvent ev = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "welcome");
        new PointAnAgentAction((category, id) -> {
            asked[0] = category + "/" + id;
            return new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    got[0] = e;
                }
            };
        }).actionPerformed(ev);
        assertThat(asked[0]).isEqualTo("Tools/org.nmox.studio.rack.mcp.AgentPortAction");
        assertThat(got[0]).isSameAs(ev);
    }
}
