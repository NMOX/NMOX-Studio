package org.nmox.studio.rack.service;

import java.util.function.Predicate;
import org.nmox.studio.core.spi.EmbeddedBrowser;

/**
 * The one way a local URL is opened from the product's own doors — the
 * status line's ⇄ chip, ⌘I "Live Servers", the Docker panel's published
 * port, SONAR's Browse (v2.70.0). A
 * pick opens in the product's own Browser (v2.69.19 taught the chip;
 * the review found ⌘I still sending its twin to the system browser —
 * the sibling-registration lens); the system browser is the fallback
 * when no in-app Browser is wired up or it declines the URL.
 * {@link ServingLinksGateTest} keeps every registry front door on this
 * path.
 */
public final class ServingLinks {

    private ServingLinks() {
    }

    /**
     * Opens {@code url} in the in-app Browser, else the system browser.
     * @return false when neither took it — the caller may say so (the
     *         Docker panel tells "the browser refused" apart from "no
     *         port"); a bare click site may ignore it.
     */
    public static boolean open(String url) {
        return open(url, EmbeddedBrowser.find(), ServingLinks::systemBrowse);
    }

    /** The seam: in-app first, system browser when absent or declining. */
    static boolean open(String url, EmbeddedBrowser inApp, Predicate<String> system) {
        if (inApp != null && inApp.open(url)) {
            return true;
        }
        return system.test(url);
    }

    static boolean systemBrowse(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                return true;
            }
        } catch (Exception ignored) {
            // no browser available: the caller decides whether to say so
        }
        return false;
    }
}
