package org.nmox.studio.core.spi;

import java.io.File;
import java.util.List;
import org.openide.util.Lookup;

/**
 * Who is serving what, live — the soft-dependency face of the rack's
 * {@code ServingRegistry} for studios that only READ servings (API
 * Studio's {{baseUrl}} offer, Contract Studio's ANVIL auto-connect).
 *
 * <p>Same contract as the registry, restated here so consumers need no
 * rack classes (tech-debt ledger 30): listeners are deliberately coarse
 * ({@link Listener#servingChanged()} carries no payload, re-read
 * {@link #snapshot()}), and notifications arrive on the registry's own
 * single background thread — never the caller's thread, never the EDT;
 * consumers marshal to the EDT themselves. {@link #find()} returning
 * null means the rack is absent (plain unit tests, a stripped
 * platform): no servings, feature quietly off.
 */
public interface LiveServings {

    /** The provider registered by the rack module, or null when absent. */
    static LiveServings find() {
        return Lookup.getDefault().lookup(LiveServings.class);
    }

    /** What kind of thing the URL fronts. */
    enum Kind { WEB, CHAIN }

    /** One live serving: a device, its URL, and the project it serves. */
    record Serving(String deviceId, String deviceTitle, String url,
            Kind kind, File projectDir) {
    }

    /** Coarse change notification; re-read {@link #snapshot()}. */
    interface Listener {
        void servingChanged();
    }

    /** All current servings, registration-ordered; never null. */
    List<Serving> snapshot();

    /**
     * Subscribes. Adding a listener already subscribed is a no-op — the
     * adapter must never double-deliver.
     */
    void addListener(Listener listener);

    /** Unsubscribes; unknown listeners no-op. */
    void removeListener(Listener listener);
}
