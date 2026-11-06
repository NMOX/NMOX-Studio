package org.nmox.studio.infra.ops.provider;

import java.util.List;

/**
 * The menu a provider offers for a new host. Prices are the provider's
 * numbers, shown before anything is billed.
 */
public record ProviderOptions(List<Region> regions, List<Size> sizes, List<Image> images, List<SshKey> sshKeys) {

    public record Region(String slug, String name, boolean available) {
    }

    /** {@code priceMonthly} in USD as the provider states it; {@code regions} where it can be had. */
    public record Size(String slug, double priceMonthly, double priceHourly, int vcpus, int memoryMb, int diskGb,
            List<String> regions) {

        public String describe() {
            return slug + " · " + vcpus + " vCPU · " + (memoryMb >= 1024 ? (memoryMb / 1024) + " GB" : memoryMb + " MB")
                    + " · " + diskGb + " GB";
        }
    }

    public record Image(String slug, String name, String distribution) {
    }

    public record SshKey(String id, String name, String fingerprint) {
    }

    public Size size(String slug) {
        for (Size s : sizes) {
            if (s.slug().equals(slug)) {
                return s;
            }
        }
        return null;
    }
}
