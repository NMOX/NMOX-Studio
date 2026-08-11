package org.nmox.studio.ui.irc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.swing.text.StyledDocument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reattach gap marker (v1.344.0), the two-proof way: the seam is
 * driven headless here, and the wiring gate pins that componentShowing
 * actually calls it on the reattach branch. Found by the live Libera
 * walk of shipped 1.343.0: with the window closed the engine kept
 * logging (IrcLogTap, v1.322.0), but the reopened scrollback silently
 * omitted the closed-period messages.
 */
class IrcGapMarkerTest {

    @Test
    @DisplayName("the marker lands once in the status and each channel transcript")
    void markerAppends() throws Exception {
        IrcTopComponent tc = new IrcTopComponent();
        tc.appendGapMarkers("libera", List.of("#a", "#b"));

        for (String target : new String[] {"", "#a", "#b"}) {
            StyledDocument doc = tc.docForKeyTest("libera", target);
            String text = doc.getText(0, doc.getLength());
            assertThat(text)
                    .as("transcript for %s carries exactly one marker",
                            target.isEmpty() ? "status" : target)
                    .isEqualTo(IrcTopComponent.GAP_MARKER + "\n");
        }
        // an unrelated network's transcript stays untouched
        assertThat(tc.docForKeyTest("oftc", "").getLength()).isZero();
    }

    @Test
    @DisplayName("componentShowing marks ONLY the newly-bridged (reopen) case")
    void wiringGate() throws Exception {
        String src = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "ui", "irc", "IrcTopComponent.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        // the discriminator: a live session with no bridge is a reopen —
        // bridges survive tab switches, only componentClosed clears them
        assertThat(src)
                .contains("boolean reattach = !bridges.containsKey(")
                .contains("if (reattach) {")
                .contains("appendGapMarkers(e.getKey(), e.getValue().joinedChannels())");
        // the marker is bookkeeping, not a message: it must NOT ride
        // append() (which bolds the tree and counts unread/mentions)
        String seam = src.substring(src.indexOf("void appendGapMarkers"));
        seam = seam.substring(0, seam.indexOf("private void append("));
        assertThat(seam)
                .as("the seam writes documents directly, never through append()")
                .doesNotContain("append(k")
                .contains("insertString");
    }
}
