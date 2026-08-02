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

    /**
     * The WebView's HTTP/2-over-cleartext probe, disabled (v1.226.0,
     * ledger 70). JavaFX WebKit sends {@code Upgrade: h2c} +
     * {@code Connection: Upgrade, HTTP2-Settings} on EVERY plain-HTTP
     * request (RFC 7540 §3.2). Measured: Angular's esbuild dev server
     * (`ng serve`) accepts the connection and then NEVER RESPONDS to
     * such a request — reproduced headlessly with curl replaying the
     * captured headers (hangs with the header, 200 in 5 ms without it,
     * and a plain static server answers 200 either way, so the fault
     * is the dev server's). Turning the probe off costs nothing —
     * h2c upgrades are essentially never accepted in practice, and
     * every https:// page still negotiates HTTP/2 via ALPN, which this
     * property does not touch — and it makes the Browser able to load
     * `ng serve`, the single most common dev URL for this product's
     * chosen framework.
     *
     * <p>Set before ANY WebKit class initializes: {@link #available()}
     * is the first thing the Browser tab calls, and the property is
     * read when the native loader starts.
     */
    private static final String H2C_PROPERTY = "com.sun.webkit.useHTTP2Loader";

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
            if (a && System.getProperty(H2C_PROPERTY) == null) {
                // don't override a user's explicit -D choice
                System.setProperty(H2C_PROPERTY, "false");
            }
            available = a;
        }
        return a;
    }
}
