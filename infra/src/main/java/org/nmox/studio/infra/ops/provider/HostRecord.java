package org.nmox.studio.infra.ops.provider;

import java.util.Map;

/**
 * A host as a provider reports it. {@code active} is the provider's own
 * word for "running"; {@code address} is null until the provider hands
 * one out. Facts are what the inspector shows (region, size, image, tags).
 */
public record HostRecord(String providerId, String name, String address, boolean active,
        String providerStatus, Map<String, String> facts) {

    public HostRecord {
        if (facts == null) {
            facts = Map.of();
        }
    }
}
