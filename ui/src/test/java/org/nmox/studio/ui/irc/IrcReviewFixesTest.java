package org.nmox.studio.ui.irc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.ui.irc.protocol.IrcMessage;
import org.nmox.studio.ui.irc.protocol.MircFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins for the v1.208.0 arc-review fixes on the IRC surface. Each test
 * names the finding it kills; deleting the corresponding fix must turn
 * the test red (mutation-proven at review time).
 */
class IrcReviewFixesTest {

    // ---- H1: TLS hostname verification (source gate) --------------------

    @Test
    @DisplayName("TLS connect enables endpoint identification (H1 source gate)")
    void tlsConnectVerifiesHostname() throws IOException {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/ui/irc/engine/IrcClient.java"),
                StandardCharsets.UTF_8);
        // A raw SSLSocket validates the certificate CHAIN but not the
        // HOSTNAME; without this parameter any CA-valid certificate for
        // any domain would pass and an on-path attacker would receive
        // the SASL/NickServ credentials. The gate pins the mechanism.
        assertThat(src).contains("setEndpointIdentificationAlgorithm(\"HTTPS\")");
    }

    // ---- H2: transcript retention cap -----------------------------------

    @Test
    @DisplayName("transcript trims whole head lines back under the cap (H2)")
    void transcriptTrimsHeadLines() throws BadLocationException {
        StyledDocument doc = new DefaultStyledDocument();
        String line = "x".repeat(999) + "\n"; // 1000 chars per line
        while (doc.getLength() <= IrcTopComponent.TRANSCRIPT_CAP_CHARS + 5_000) {
            doc.insertString(doc.getLength(), line, null);
        }
        IrcTopComponent.trimTranscript(doc);
        assertThat(doc.getLength()).isLessThanOrEqualTo(IrcTopComponent.TRANSCRIPT_CAP_CHARS);
        // trimming removes WHOLE lines: what remains still starts at a
        // line boundary (an 'x' line, not a torn tail)
        assertThat(doc.getText(0, 1)).isEqualTo("x");
        // and the newest content survives — the head fell off, not the tail
        assertThat(doc.getText(doc.getLength() - 1, 1)).isEqualTo("\n");
    }

    // ---- M1d: WHOIS channel accumulation cap ----------------------------

    @Test
    @DisplayName("endless 319 lines cannot grow WHOIS channels past the cap (M1)")
    void whoisChannelsCapped() {
        WhoisCollector c = new WhoisCollector();
        c.accept(IrcMessage.parse(":srv 311 me nick user host * :Real Name"));
        for (int i = 0; i < WhoisCollector.CHANNELS_CAP; i++) {
            c.accept(IrcMessage.parse(":srv 319 me nick :#a" + i + " #b" + i));
        }
        WhoisCollector.WhoisInfo w = c.accept(IrcMessage.parse(":srv 318 me nick :End of WHOIS"));
        assertThat(w).isNotNull();
        assertThat(w.channels()).hasSizeLessThanOrEqualTo(WhoisCollector.CHANNELS_CAP);
    }

    // ---- L3: control characters never reach the transcript --------------

    @Test
    @DisplayName("unhandled C0 controls are dropped by the format parser (L3)")
    void controlCharsDropped() {
        List<MircFormat.Span> spans = MircFormat.parse("be\u0007ep ba\bck t\tab");
        String text = spans.stream().map(MircFormat.Span::text)
                .reduce("", String::concat);
        assertThat(text).isEqualTo("beep back t\tab");
    }

    // ---- L4: verbs cannot smuggle a second wire line ---------------------

    @Test
    @DisplayName("embedded CR/LF in outbound text is flattened (L4 source gate)")
    void crlfFlattenedInSend() throws IOException {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/ui/irc/engine/IrcClient.java"),
                StandardCharsets.UTF_8);
        assertThat(src)
                .as("send() strips CR/LF before the wire")
                .contains("line.replace(\"\\r\", \" \").replace(\"\\n\", \" \")");
    }
}
