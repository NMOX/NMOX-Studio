package org.nmox.studio.ui.browser.fx;

/**
 * The one question the Browser tab asks before touching JavaFX: is it
 * on this runtime at all? Installed builds always say yes (the
 * bundled jlinked image has carried the OpenJFX modules since
 * v1.199.0); a dev launch on a plain JDK says no and the tab shows
 * its honest unavailable panel instead.
 *
 * <p>This class deliberately has NO javafx imports — it must be
 * loadable everywhere. {@code FxBrowserPanel} (which extends into
 * javafx.embed.swing) is only referenced AFTER {@link #available()}
 * answers true, so its class never links on an FX-less runtime.
 */
public final class FxAvailability {

    private static volatile Boolean available;

    private FxAvailability() {
    }

    /** True when the JavaFX Swing embed (and so javafx.web) is present. */
    public static boolean available() {
        Boolean a = available;
        if (a == null) {
            try {
                Class.forName("javafx.embed.swing.JFXPanel");
                a = Boolean.TRUE;
            } catch (Throwable missing) {
                a = Boolean.FALSE;
            }
            available = a;
        }
        return a;
    }
}
