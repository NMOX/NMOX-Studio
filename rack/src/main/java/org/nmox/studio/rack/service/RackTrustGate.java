package org.nmox.studio.rack.service;

import java.io.File;
import org.nmox.studio.core.spi.TrustGate;
import org.openide.util.lookup.ServiceProvider;

/**
 * The rack's {@link TrustGate} provider (v1.224.0): a pure delegation
 * to {@link WorkspaceTrust#requestTrust} so modules with no rack
 * dependency (web3 since v1.46.0) can gate their own project-code
 * spawns through the ONE trust mechanism. No logic lives here — the
 * prompt-once semantics, the prefs storage, and the headless behavior
 * stay exactly {@code WorkspaceTrust}'s.
 */
@ServiceProvider(service = TrustGate.class)
public final class RackTrustGate implements TrustGate {

    @Override
    public boolean requestTrust(File projectDir) {
        return WorkspaceTrust.requestTrust(projectDir);
    }
}
