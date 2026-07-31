package org.nmox.studio.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The discovery-tab law (v1.211.0, David's call): the Browser and IRC open
 * by DEFAULT so a newcomer finds out the IDE has them — and, because a
 * default-open tab's {@code componentOpened} fires during startup, neither
 * may do its work there.
 *
 * <p>These two are the expensive cases: the Browser boots the whole JavaFX
 * platform and fetches its home page, and IRC builds a large Swing tree.
 * Doing either at boot would undo the v1.38.0 startup work (window paints
 * in 1.4–2.7s, ZERO processes spawned, no network at boot). Both build on
 * first SHOW instead — the idiom DB Studio has used since v1.35.1.
 */
class DiscoveryTabsGateTest {

    private static String src(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }

    private static final String BROWSER =
            "src/main/java/org/nmox/studio/ui/browser/WebBrowserTopComponent.java";
    private static final String IRC =
            "src/main/java/org/nmox/studio/ui/irc/IrcTopComponent.java";

    @Test
    @DisplayName("the Browser and IRC are open on first launch — that is the discovery decision")
    void bothOpenAtStartup() throws IOException {
        assertThat(src(BROWSER))
                .as("a newcomer should find the in-app browser without reading docs")
                .contains("openAtStartup = true");
        assertThat(src(IRC))
                .as("same for the chat client")
                .contains("openAtStartup = true");
    }

    @Test
    @DisplayName("neither builds in componentOpened — a default-open tab must cost nothing at boot")
    void neitherWorksAtBoot() throws IOException {
        String browser = src(BROWSER);
        // the FX panel (which boots the JavaFX platform) and the home-page
        // load must live in componentShowing, never componentOpened
        int opened = browser.indexOf("protected void componentOpened()");
        int showing = browser.indexOf("protected void componentShowing()");
        assertThat(opened).isGreaterThan(0);
        assertThat(showing).as("Browser builds on first show").isGreaterThan(opened);
        assertThat(browser.substring(opened, showing))
                .as("componentOpened must not construct the FX panel or load a URL")
                .doesNotContain("new FxBrowserPanel")
                .doesNotContain("loadUrl");

        String irc = src(IRC);
        int ircOpened = irc.indexOf("protected void componentOpened()");
        int ircShowing = irc.indexOf("protected void componentShowing()");
        assertThat(ircOpened).isGreaterThan(0);
        assertThat(ircShowing).as("IRC builds on first show").isGreaterThan(ircOpened);
        assertThat(irc.substring(ircOpened, ircShowing))
                .as("componentOpened must not build the UI")
                .doesNotContain("buildUi()");
    }

    @Test
    @DisplayName("opening the IRC tab still talks to nobody — no auto-connect")
    void ircStillDoesNotAutoConnect() throws IOException {
        String irc = src(IRC);
        int showing = irc.indexOf("protected void componentShowing()");
        int closed = irc.indexOf("protected void componentClosed()");
        assertThat(showing).isGreaterThan(0);
        assertThat(closed).isGreaterThan(showing);
        assertThat(irc.substring(showing, closed))
                .as("a chat client that dials out because a tab exists would be "
                        + "a surprise, and the default network is a real public server")
                .doesNotContain("client.connect()");
    }
}
