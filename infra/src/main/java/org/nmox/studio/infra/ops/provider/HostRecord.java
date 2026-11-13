package org.nmox.studio.infra.ops.provider;

import java.util.Map;

/**
 * A host as a provider reports it. {@code phase} is the provider's own
 * status read as one of the three things the Hub can do with it;
 * {@code providerStatus} is the provider's word, kept verbatim so the
 * inspector and a "not running" reason can quote it. {@code address} is
 * null until the provider hands one out. Facts are what the inspector
 * shows (region, size, image, tags).
 *
 * <p>The phase is the PROVIDER's translation and not the engine's: the
 * engine once compared the word itself with DigitalOcean's {@code new},
 * which made every other provider's "being made" a powered-off server.
 */
public record HostRecord(String providerId, String name, String address, Phase phase,
        String providerStatus, Map<String, String> facts) {

    /** What a provider's status means to the Hub. */
    public enum Phase {
        /** The provider is still making or starting it (DigitalOcean {@code new}; Hetzner {@code initializing}, {@code starting}). */
        COMING_UP,
        /** The provider's own word for "running" (DigitalOcean {@code active}; Hetzner {@code running}). */
        RUNNING,
        /** It exists and is not coming up: off, archived, being deleted, or a word this product does not know. */
        NOT_RUNNING
    }

    public HostRecord {
        if (facts == null) {
            facts = Map.of();
        }
        if (phase == null) {
            phase = Phase.NOT_RUNNING;
        }
    }

    /** True when the provider says it runs. */
    public boolean active() {
        return phase == Phase.RUNNING;
    }

    /** True while the provider is still bringing it up. */
    public boolean comingUp() {
        return phase == Phase.COMING_UP;
    }
}
