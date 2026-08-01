package org.nmox.studio.core.spi;

import java.io.File;
import org.openide.util.Lookup;

/**
 * Soft-dependency facade over the rack's Workspace Trust service
 * (v1.224.0): modules that must gate an inward execution flow —
 * running a project's own code — but deliberately carry no rack
 * dependency (web3 since v1.46.0) consult this instead. The rack
 * publishes the one implementation as a {@code @ServiceProvider};
 * a null {@link #find()} means the rack module is absent.
 *
 * <p>v1.46.0 recorded "TrustGate deliberately not facaded" because no
 * soft-dependency consumer needed it then. The v1.224.0 spawn-site
 * sweep found one: Contract Studio's forge build/test buttons execute
 * the aimed repo's Foundry project — and Foundry's {@code ffi}
 * cheatcode lets a test suite run arbitrary host commands, so those
 * buttons need the same gate every other project-code spawn has.
 */
public interface TrustGate {

    /**
     * Asks the user to trust {@code projectDir} (prompt-once; a prior
     * grant answers silently). True = trusted, proceed; false = the
     * user declined, run nothing.
     */
    boolean requestTrust(File projectDir);

    /** The rack's implementation, or null when the rack is absent. */
    static TrustGate find() {
        return Lookup.getDefault().lookup(TrustGate.class);
    }
}
