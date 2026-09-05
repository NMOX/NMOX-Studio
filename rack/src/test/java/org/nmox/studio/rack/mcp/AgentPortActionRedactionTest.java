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
}
