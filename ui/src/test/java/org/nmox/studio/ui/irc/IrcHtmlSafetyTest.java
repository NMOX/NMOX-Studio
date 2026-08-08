package org.nmox.studio.ui.irc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The IRC client shows text a HOSTILE server chooses — nicknames, the
 * channel/network tree, and the channel topic — through JLabel-based
 * Swing components. A JLabel RENDERS a value starting with
 * {@code <html>}, so a nick or topic of {@code <html><img src=http://…>}
 * makes the IDE's own JVM fetch that URL at paint time. That is the
 * v1.208.0 bug class (found then in Browser DevTools); v1.307.0 closes
 * it in the IRC client.
 *
 * <p>The behavioral proof that {@code html.disable} works — and that it
 * must be set at construction, before the first {@code setText} — lives
 * in core's {@code PlainTablesTest}. This gate pins that the three IRC
 * surfaces actually route through {@link
 * org.nmox.studio.core.util.PlainTables#plain}, so a refactor cannot
 * silently drop the defense.
 */
class IrcHtmlSafetyTest {

    @Test
    @DisplayName("the nick list, channel tree, and topic label disable HTML")
    void ircExternalTextSurfacesArePlain() throws Exception {
        // CRLF checkouts (the windows lane) — normalize before asserting
        String src = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "ui", "irc", "IrcTopComponent.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");

        // three PlainTables.plain(...) calls: the two renderer constructors
        // (nick list, channel tree) and the topic label. Fewer than three
        // means a server-controlled surface lost its defense.
        int plainCalls = src.split("PlainTables\\.plain\\(", -1).length - 1;
        assertThat(plainCalls)
                .as("nick list + channel tree + topic label = three server-text"
                        + " surfaces, each must route through PlainTables.plain")
                .isGreaterThanOrEqualTo(3);

        // the renderers must set it at CONSTRUCTION, not per-render (setting
        // html.disable after super's setText is too late — the view is built)
        assertThat(src)
                .as("the nick renderer disables HTML in its constructor")
                .contains("AwayAwareNickRenderer() {");
        assertThat(src)
                .as("the tree renderer disables HTML in its constructor")
                .contains("UnreadBoldRenderer() {");
    }
}
