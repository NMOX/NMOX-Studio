package org.nmox.studio.web3.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The bounded reassembly of WebSocket text frames. */
class WsFramesTest {

    @Test
    @DisplayName("parts reassemble into one message at the last part")
    void reassembles() {
        WsFrames frames = new WsFrames(100);
        assertThat(frames.append("{\"a\":", false).kind()).isEqualTo(WsFrames.Kind.PARTIAL);
        WsFrames.Result done = frames.append("1}", true);
        assertThat(done.kind()).isEqualTo(WsFrames.Kind.MESSAGE);
        assertThat(done.text()).isEqualTo("{\"a\":1}");
        assertThat(frames.append("x", true).text()).as("the buffer was reset").isEqualTo("x");
    }

    @Test
    @DisplayName("CAP: a message over the cap is refused once, its tail discarded, and the next message reads clean")
    void oversizeMessageIsRefused() {
        WsFrames frames = new WsFrames(10);
        assertThat(frames.append("12345", false).kind()).isEqualTo(WsFrames.Kind.PARTIAL);
        assertThat(frames.append("678901", false).kind())
                .as("crossing the cap").isEqualTo(WsFrames.Kind.OVERSIZE);
        assertThat(frames.append("more", false).kind()).isEqualTo(WsFrames.Kind.DISCARDED);
        assertThat(frames.append("end", true).kind()).isEqualTo(WsFrames.Kind.DISCARDED);
        WsFrames.Result next = frames.append("ok", true);
        assertThat(next.kind()).isEqualTo(WsFrames.Kind.MESSAGE);
        assertThat(next.text()).as("nothing of the refused message leaks in").isEqualTo("ok");
    }

    @Test
    @DisplayName("a single whole part over the cap is refused, and exactly the cap is allowed")
    void singlePartBoundary() {
        WsFrames frames = new WsFrames(4);
        assertThat(frames.append("12345", true).kind()).isEqualTo(WsFrames.Kind.OVERSIZE);
        assertThat(frames.append("1234", true).kind()).isEqualTo(WsFrames.Kind.MESSAGE);
    }

    @Test
    @DisplayName("the default cap is the published one, and a nonsense cap is refused")
    void defaults() {
        WsFrames frames = new WsFrames();
        assertThat(frames.append("x".repeat(WsFrames.MAX_MESSAGE_CHARS), true).kind())
                .isEqualTo(WsFrames.Kind.MESSAGE);
        assertThat(frames.append("x".repeat(WsFrames.MAX_MESSAGE_CHARS + 1), true).kind())
                .isEqualTo(WsFrames.Kind.OVERSIZE);
        assertThatThrownBy(() -> new WsFrames(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
