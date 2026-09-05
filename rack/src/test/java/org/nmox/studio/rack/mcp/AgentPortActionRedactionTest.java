package org.nmox.studio.rack.mcp;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The docs forge never photographs a token (v2.84.0): under nmox.shots.dir the dialog shows a placeholder. */
class AgentPortActionRedactionTest {

    @Test
    @DisplayName("the shown token is the real one, except under the forge, where it is the placeholder")
    void redactsUnderTheForge() throws Exception {
        AgentPort port = AgentPort.start(new McpTools(List.of()), "test");
        try {
            System.clearProperty("nmox.shots.dir");
            assertThat(AgentPortAction.shownToken(port)).isEqualTo(port.token()).hasSize(64);
            System.setProperty("nmox.shots.dir", "/tmp/shots");
            assertThat(AgentPortAction.shownToken(port)).isEqualTo("TOKEN");
        } finally {
            System.clearProperty("nmox.shots.dir");
            port.stop();
        }
    }

    @Test
    @DisplayName("the disclosure wraps: a twelve-tool roster never makes the label wider than its bounded body (the walk's clipped-sentence find)")
    void disclosureWraps() {
        StringBuilder roster = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            roster.append(i > 0 ? ", " : "").append("a_rather_long_tool_name_").append(i);
        }
        javax.swing.JLabel label = new javax.swing.JLabel(AgentPortAction.disclosureHtml(55988, roster.toString()));
        int width = label.getPreferredSize().width;
        assertThat(width).as("wrapped to the bounded body, not one long line")
                .isLessThan(AgentPortAction.LABEL_WIDTH + 40).isGreaterThan(AgentPortAction.LABEL_WIDTH / 2);
        assertThat(label.getPreferredSize().height).as("several lines tall").isGreaterThan(60);
        assertThat(AgentPortAction.disclosureHtml(1, "x")).contains("127.0.0.1:1").contains("<i>x</i>").contains("hear a run's own output");
    }
}
