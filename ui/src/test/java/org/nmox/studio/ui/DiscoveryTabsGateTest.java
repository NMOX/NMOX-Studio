package org.nmox.studio.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Browser is open on first launch; IRC is not (v2.118.0, David's call
 * after the coherence pass, amending v1.211.0).
 *
 * <p>v1.211.0 opened both so a newcomer would find out the IDE has them.
 * The coherence pass measured what that cost by first launch: ten editor
 * tabs, four of them for technologies the opened project cannot use and a
 * fifth a chat client, with the user's own file arriving eleventh. It also
 * measured that discovery already has three surfaces — the Welcome's
 * TOOLING column, the Window menu, and the ⌥⌘ chords — so the tab strip was
 * the redundant one. Seven tabs closed; the Browser stayed, because a Run
 * lands a serving in it (OpenOnServe, v1.212.0) and it is where the page
 * the user just started appears.
 *
 * <p>The rest of the law is unchanged and now covers a wider population: a
 * default-open tab's {@code componentOpened} fires during startup, so
 * neither the Browser (which boots the whole JavaFX platform and fetches a
 * page) nor IRC (a large Swing tree) may do its work there. Both build on
 * first SHOW — the idiom DB Studio has used since v1.35.1 — which is also
 * what makes a now-closed tab cost nothing when the user opens it.
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
    @DisplayName("the Browser opens on first launch — a Run has to land somewhere")
    void browserOpensAtStartup() throws IOException {
        assertThat(src(BROWSER))
                .as("Run arms OpenOnServe and the served page appears here")
                .contains("openAtStartup = true");
    }

    @Test
    @DisplayName("IRC does not open on first launch — a chat client is not the first thing a work IDE shows")
    void ircDoesNotOpenAtStartup() throws IOException {
        assertThat(src(IRC))
                .as("⌥⌘3, the Welcome's TOOLING column and the Window menu are the three doors")
                .contains("openAtStartup = false");
    }

    @Test
    @DisplayName("neither builds in componentOpened — an open tab must cost nothing at boot")
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
