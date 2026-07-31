package org.nmox.studio.ui.irc.engine;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.208.0 arc review's M3 finding: a message longer than the RFC's
 * 512-byte wire line was sent whole, silently truncated by the SERVER,
 * while the local echo showed text the channel never received. Splitting
 * in the engine keeps the transcript truthful.
 *
 * <p>Lives in the engine package because {@code splitForWire} is
 * package-private — the split is an engine invariant, not public API.
 */
class WireSplitTest {

    @Test
    @DisplayName("a short message passes through unsplit")
    void shortMessageUnsplit() {
        assertThat(IrcClient.splitForWire("PRIVMSG #chan :", "hello world"))
                .containsExactly("hello world");
    }

    @Test
    @DisplayName("a long message splits into wire-legal pieces that reassemble")
    void longMessageSplitsAndReassembles() {
        String prefix = "PRIVMSG #channel :";
        String words = "lorem ipsum dolor sit amet ".repeat(60).trim();
        List<String> pieces = IrcClient.splitForWire(prefix, words);

        assertThat(pieces).hasSizeGreaterThan(1);
        int budget = 510 - prefix.getBytes(StandardCharsets.UTF_8).length;
        for (String p : pieces) {
            assertThat(p.getBytes(StandardCharsets.UTF_8).length)
                    .as("each piece fits the RFC line budget")
                    .isLessThanOrEqualTo(budget);
        }
        // word-boundary splits consume the separating space, so joining
        // with single spaces restores the original text exactly
        assertThat(String.join(" ", pieces)).isEqualTo(words);
    }

    @Test
    @DisplayName("splits never land mid-code-point — emoji survive whole")
    void splitIsCodePointSafe() {
        String emoji = "😀"; // U+1F600, 4 UTF-8 bytes, 2 chars
        List<String> pieces = IrcClient.splitForWire("PRIVMSG #c :", emoji.repeat(400));

        assertThat(pieces).hasSizeGreaterThan(1);
        for (String p : pieces) {
            assertThat(Character.isLowSurrogate(p.charAt(0)))
                    .as("no piece starts on a torn pair").isFalse();
            assertThat(Character.isHighSurrogate(p.charAt(p.length() - 1)))
                    .as("no piece ends on a torn pair").isFalse();
        }
        assertThat(String.join("", pieces)).isEqualTo(emoji.repeat(400));
    }

    @Test
    @DisplayName("a multi-byte body still fits the BYTE budget, not the char count")
    void multiByteBodyRespectsByteBudget() {
        // 400 CJK chars = 1200 UTF-8 bytes: a char-counting split would
        // wrongly emit one over-long line
        List<String> pieces = IrcClient.splitForWire("PRIVMSG #c :", "中".repeat(400));
        assertThat(pieces).hasSizeGreaterThan(1);
        for (String p : pieces) {
            assertThat(p.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(498);
        }
    }
}
