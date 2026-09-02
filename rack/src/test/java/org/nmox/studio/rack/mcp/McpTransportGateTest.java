package org.nmox.studio.rack.mcp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source gate for the transport's last-resort net (v2.56.1): the call
 * into McpProtocol.handle must sit inside a try that answers a
 * JSON-RPC -32603 on RuntimeException. Every protocol path guards its
 * own handlers today, so no behavior can reach this net — which is
 * exactly why it is pinned at the source: defense in depth that a
 * behavioral test cannot exercise must not silently vanish.
 */
class McpTransportGateTest {

    @Test
    @DisplayName("AgentPort answers -32603 instead of dropping the connection on a throw")
    void handleIsNetted() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/mcp/AgentPort.java"),
                StandardCharsets.UTF_8);
        int call = src.indexOf("McpProtocol.handle(");
        assertThat(call).as("the protocol call exists").isPositive();
        int tryAt = src.lastIndexOf("try {", call);
        assertThat(tryAt).as("handle() sits inside a try").isPositive();
        int catchAt = src.indexOf("catch (RuntimeException", call);
        assertThat(catchAt).as("the try catches RuntimeException").isPositive();
        assertThat(src.indexOf("-32603", catchAt))
                .as("the catch answers JSON-RPC -32603, never silence")
                .isPositive();
    }
}
