package org.nmox.studio.infra.ops.provider;

import java.util.List;

/**
 * The menu a provider offers for a new host. Prices are the provider's
 * numbers, shown before anything is billed, and {@code currency} is the
 * ISO 4217 code they are stated in (DigitalOcean: USD; Hetzner says its
 * own, EUR for most accounts) — a figure is never shown under another
 * currency's sign.
 */
public record ProviderOptions(List<Region> regions, List<Size> sizes, List<Image> images, List<SshKey> sshKeys,
        String currency) {

    /**
     * The sign a price is written with: {@code $} for USD, {@code €} for
     * EUR, and for anything else the code itself — an unfamiliar currency
     * is named, never dressed as a familiar one.
     */
    public String currencySign() {
        return switch (currency == null ? "" : currency) {
            case "USD" -> "$";
            case "EUR" -> "\u20ac";
            default -> currency == null ? "" : currency;
        };
    }

    public record Region(String slug, String name, boolean available) {
    }

    /** {@code priceMonthly} as the provider states it, in the menu's currency; {@code regions} where it can be had. */
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
