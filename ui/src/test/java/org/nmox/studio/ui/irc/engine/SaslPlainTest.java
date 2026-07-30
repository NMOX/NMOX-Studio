package org.nmox.studio.ui.irc.engine;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SASL PLAIN chunker: payload shape (NUL account NUL password,
 * base64), the 400-byte AUTHENTICATE ceiling, and the spec's two edge
 * rules — an exactly-full final chunk is followed by a bare {@code +},
 * and an empty payload IS a bare {@code +}.
 */
class SaslPlainTest {

    @Test
    @DisplayName("A short credential is one chunk: base64 of NUL account NUL password")
    void shortCredentialOneChunk() {
        List<String> chunks = SaslPlain.chunks("alice", "hunter2");
        assertThat(chunks).hasSize(1);
        byte[] decoded = Base64.getDecoder().decode(chunks.get(0));
        assertThat(new String(decoded, StandardCharsets.UTF_8))
                .isEqualTo("\0alice\0hunter2");
    }

    @Test
    @DisplayName("The payload splits at 400 base64 chars per AUTHENTICATE line")
    void longCredentialSplitsAt400() {
        // raw length 1 + 100 + 1 + 500 = 602 bytes → base64 804 chars
        List<String> chunks = SaslPlain.chunks("a".repeat(100), "x".repeat(500));
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).hasSize(400);
        assertThat(chunks.get(1)).hasSize(400);
        assertThat(chunks.get(2)).hasSize(4);
        String whole = chunks.get(0) + chunks.get(1) + chunks.get(2);
        assertThat(new String(Base64.getDecoder().decode(whole), StandardCharsets.UTF_8))
                .startsWith("\0" + "a".repeat(100) + "\0");
    }

    @Test
    @DisplayName("An exactly-full final chunk is followed by the spec's bare +")
    void exactlyFullChunkGetsPlusTerminator() {
        // raw 300 bytes → base64 exactly 400 chars → chunk then "+"
        List<String> chunks = SaslPlain.chunks("a".repeat(100), "x".repeat(198));
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).hasSize(400);
        assertThat(chunks.get(1)).isEqualTo("+");
    }

    @Test
    @DisplayName("Unicode credentials encode as UTF-8 and round-trip")
    void unicodeRoundTrips() {
        List<String> chunks = SaslPlain.chunks("dävê", "pässwörd☕");
        String joined = String.join("", chunks);
        assertThat(new String(Base64.getDecoder().decode(joined), StandardCharsets.UTF_8))
                .isEqualTo("\0dävê\0pässwörd☕");
    }
}
