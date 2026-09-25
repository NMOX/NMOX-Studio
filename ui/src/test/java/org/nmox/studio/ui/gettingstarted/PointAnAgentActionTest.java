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
        // The platform's NbStatusDisplayer.add reads messages.get(0) inside its
        // index loop, so when the head of its list is a message an earlier test
        // set and nobody held (collected), the NEXT plain setStatusText lands
        // behind an older live message and getStatusText keeps answering the
        // older one (measured on RELEASE310: "second", importance-5 message
        // collected, "third" → still reads "second"; "fourth" → "fourth").
        // getStatusText purges a dead head, so read once first; the product's
        // exposure is recorded, this test is about the door's own sentence.
        StatusDisplayer.getDefault().getStatusText();
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
