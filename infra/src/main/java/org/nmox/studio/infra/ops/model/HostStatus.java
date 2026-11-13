package org.nmox.studio.infra.ops.model;

/**
 * Where a host is on its way from a request to a server you can use. Every
 * step is an OBSERVED fact, never a timer: the provider says it exists, the
 * provider says it is active and has an address, ssh answers, cloud-init
 * reports done. A status the Hub cannot observe is {@link #UNREACHABLE}, not
 * a guess.
 */
public enum HostStatus {
    /** The create request was accepted; the provider has not listed it yet. */
    REQUESTED,
    /** The provider lists it and is still bringing it up (DigitalOcean: {@code new}; Hetzner: {@code initializing}, {@code starting}). */
    PROVISIONING,
    /** Active with an address, and ssh does not answer yet. */
    BOOTING,
    /** ssh answers; cloud-init is still configuring (docker, mosh). */
    CONFIGURING,
    /** ssh answers and the first-boot configuration is complete. */
    READY,
    /** It answered once and no longer does. */
    UNREACHABLE,
    /** A destroy was requested; the provider may still list it. */
    DESTROYING,
    /** The provider no longer lists it. */
    GONE;

    /** True while the host is still becoming usable — the HUD sweeps its ring. */
    public boolean inProgress() {
        return this == REQUESTED || this == PROVISIONING || this == BOOTING
                || this == CONFIGURING || this == DESTROYING;
    }

    /** True when ssh is known to answer. */
    public boolean reachable() {
        return this == CONFIGURING || this == READY;
    }
}
