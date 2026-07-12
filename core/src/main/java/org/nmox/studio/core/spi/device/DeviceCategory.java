package org.nmox.studio.core.spi.device;

/**
 * The palette shelf a device sits on — the same seven categories the
 * built-in fleet uses, so extension devices file next to their kin.
 *
 * @since 1.55
 */
public enum DeviceCategory {
    /** Run &amp; Automate: launchers, sequencers, watchers. */
    AUTOMATE,
    /** Build &amp; Verify: compilers, tests, gates. */
    VERIFY,
    /** Serve &amp; Expose: servers, tunnels, probes. */
    SERVE,
    /** Framework consoles. */
    FRAMEWORKS,
    /** Observe: monitors, recorders, read-only viewers. */
    OBSERVE,
    /** Ship: deploys, audits, release gates. */
    SHIP,
    /** Utility: environment, selectors. */
    UTILITY
}
