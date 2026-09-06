package org.nmox.studio.core.spi;

import org.openide.util.Lookup;

/**
 * Soft-dependency seam to the in-app web browser (v1.199.0) — the
 * KvasirAsk idiom: consumers (the rack's SCOPE device, the serving
 * status chip) look this up and fall back to the system browser when
 * no provider is installed or the embedded engine is unavailable.
 * The ui module publishes the implementation; no module gains a hard
 * dependency on a window it only wants to point at.
 */
public interface EmbeddedBrowser {

    /**
     * Opens {@code url} in the in-app browser window, fronting it.
     * @return false when the embedded engine is unavailable (dev build
     *         on a JavaFX-less JDK) — the caller should fall back to
     *         the system browser and say nothing dramatic.
     */
    boolean open(String url);

    /** The registered provider, or null when the ui module is absent. */
    static EmbeddedBrowser find() {
        return Lookup.getDefault().lookup(EmbeddedBrowser.class);
    }
}
