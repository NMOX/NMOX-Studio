package org.nmox.studio.rack.service;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.service.ServingRegistry.Kind;
import org.nmox.studio.rack.service.ServingRegistry.Serving;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The status line's serving chip: what it says for zero, one, and many
 * servings — the model logic behind the label, tested without Swing.
 */
class RackStatusLineChipTest {

    private static Serving serving(String title, String url) {
        return new Serving(title + "@1", title, url, Kind.WEB, new File("/tmp/p"));
    }

    @Test
    @DisplayName("no servings: no chip")
    void emptyIsNull() {
        assertThat(RackStatusLine.chipText(List.of())).isNull();
        assertThat(RackStatusLine.chipTooltip(List.of())).isNull();
    }

    @Test
    @DisplayName("one serving: the URL, no +N")
    void single() {
        assertThat(RackStatusLine.chipText(List.of(
                serving("SURGE", "http://localhost:5173"))))
                .isEqualTo("⇄ serving: http://localhost:5173");
    }

    @Test
    @DisplayName("a serving a run registered under its own id gets a Stop in the chip's menu; a rack device's server does not (v2.73.0)")
    void stopBesideOpen() throws Exception {
        java.util.List<org.nmox.studio.core.spi.LiveRuns.Run> live = java.util.List.of(
                new org.nmox.studio.core.spi.LiveRuns.Run("ide-run:/p#1", "Run — shop", () -> { }));
        assertThat(RackStatusLine.runOwning(new Serving("ide-run:/p#1", "Run — shop", "http://localhost:3000/", Kind.WEB, new File("/p")), live))
                .isNotNull();
        assertThat(RackStatusLine.runOwning(serving("SURGE", "http://localhost:5173"), live)).as("a rack device has its own STOP").isNull();
        String src = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/org/nmox/studio/rack/service/RackStatusLine.java"));
        assertThat(src).contains("runOwning(s, live) != null").contains("LiveRuns.stop(s.deviceId())");
    }

    @Test
    @DisplayName("the chip's tooltip escapes titles and URLs — a script-name serving cannot make the tooltip fetch (v2.75.0)")
    void tooltipEscapes() {
        String tip = RackStatusLine.chipTooltip(List.of(
                serving("npm run <img src=\"http://evil/x\"> — shop", "http://localhost:5173/<b>")));
        assertThat(tip).startsWith("<html>")
                .contains("npm run &lt;img src=&quot;http://evil/x&quot;&gt; — shop")
                .contains("http://localhost:5173/&lt;b&gt;")
                .doesNotContain("<img").doesNotContain("<b>");
    }

    @Test
    @DisplayName("many servings: first URL +N, tooltip lists them all")
    void many() {
        List<Serving> servings = List.of(
                serving("SURGE", "http://localhost:5173"),
                serving("ARTISAN", "http://127.0.0.1:8000"),
                serving("ANVIL", "http://127.0.0.1:8545"));
        assertThat(RackStatusLine.chipText(servings))
                .isEqualTo("⇄ serving: http://localhost:5173 +2");
        assertThat(RackStatusLine.chipTooltip(servings))
                .contains("SURGE — http://localhost:5173")
                .contains("ARTISAN — http://127.0.0.1:8000")
                .contains("ANVIL — http://127.0.0.1:8545");
    }

    @Test
    @DisplayName("the Agent Port chip shows while the port listens, with the stream count in its tooltip, and is absent when off (v2.84.0)")
    void agentPortChip() throws Exception {
        assertThat(RackStatusLine.agentChipText(null)).isNull();
        assertThat(RackStatusLine.agentChipTooltip(null)).isNull();
        assertThat(RackStatusLine.agentChipText(new int[]{55725, 0})).isEqualTo("⌁ agent port :55725");
        assertThat(RackStatusLine.agentChipTooltip(new int[]{55725, 0})).contains("127.0.0.1:55725").contains("no agent streaming").contains("read-only");
        assertThat(RackStatusLine.agentChipTooltip(new int[]{55725, 1})).contains("one agent streaming");
        assertThat(RackStatusLine.agentChipTooltip(new int[]{55725, 3})).contains("3 agents streaming");
        // v2.85.0: a POST-only agent never streams — the last request is the liveness a user can read
        assertThat(RackStatusLine.agentChipTooltip(new int[]{55725, 0, -1})).contains("no request yet");
        assertThat(RackStatusLine.agentChipTooltip(new int[]{55725, 0, 1})).contains("a request just now");
        assertThat(RackStatusLine.agentChipTooltip(new int[]{55725, 0, 12})).contains("last request 12 s ago");
        assertThat(RackStatusLine.agentChipTooltip(new int[]{55725, 0, 200})).contains("last request 3 min ago");
        assertThat(RackStatusLine.agentChipTooltip(new int[]{55725, 0, 7_500})).contains("last request 2 h ago");
        // the wiring: the strip reads the action's listening() on every refresh and the chip opens the action
        String src = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/org/nmox/studio/rack/service/RackStatusLine.java"));
        assertThat(src).contains("AgentPortAction.listening()").contains("agentChipText(listening)")
                .contains("new org.nmox.studio.rack.mcp.AgentPortAction().actionPerformed(");
    }
}
