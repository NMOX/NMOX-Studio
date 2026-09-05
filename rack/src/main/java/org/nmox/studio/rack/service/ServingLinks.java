package org.nmox.studio.rack.service;

import java.util.function.Consumer;
import org.nmox.studio.core.spi.EmbeddedBrowser;

/**
 * The one way a live serving's URL is opened from the registry's front
 * doors — the status line's ⇄ chip and ⌘I "Live Servers" (v2.70.0). A
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

    /** Opens {@code url} in the in-app Browser, else the system browser. */
    public static void open(String url) {
        open(url, EmbeddedBrowser.find(), ServingLinks::systemBrowse);
    }

    /** The seam: in-app first, system browser when absent or declining. */
    static void open(String url, EmbeddedBrowser inApp, Consumer<String> system) {
        if (inApp != null && inApp.open(url)) {
            return;
        }
        system.accept(url);
    }

    static void systemBrowse(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            }
        } catch (Exception ignored) {
            // no browser available; the click just does nothing
        }
    }
}
