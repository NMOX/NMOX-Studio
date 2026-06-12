package org.nmox.studio.infra.api;

import org.openide.util.NbPreferences;

/**
 * The clouds the designer can deploy to. Each provider carries its API
 * base, its token storage, and the env var honored as a fallback - so
 * a single canvas can hold DigitalOcean droplets, Hetzner servers and
 * the Cloudflare DNS records that front them.
 */
public enum CloudProvider {

    DIGITALOCEAN("https://api.digitalocean.com", "doToken", "DIGITALOCEAN_TOKEN"),
    HETZNER("https://api.hetzner.cloud/v1", "hetznerToken", "HCLOUD_TOKEN"),
    CLOUDFLARE("https://api.cloudflare.com/client/v4", "cloudflareToken", "CLOUDFLARE_API_TOKEN");

    private final String apiBase;
    private final String prefKey;
    private final String envVar;

    CloudProvider(String apiBase, String prefKey, String envVar) {
        this.apiBase = apiBase;
        this.prefKey = prefKey;
        this.envVar = envVar;
    }

    public String apiBase() {
        return apiBase;
    }

    public String displayName() {
        return switch (this) {
            case DIGITALOCEAN -> "DigitalOcean";
            case HETZNER -> "Hetzner Cloud";
            case CLOUDFLARE -> "Cloudflare";
        };
    }

    /** Stored token, else the provider's env var, else null. */
    public String token() {
        String stored = NbPreferences.forModule(CloudProvider.class).get(prefKey, "");
        if (!stored.isBlank()) {
            return stored;
        }
        String env = System.getenv(envVar);
        return env == null || env.isBlank() ? null : env;
    }

    public void storeToken(String token) {
        NbPreferences.forModule(CloudProvider.class).put(prefKey, token == null ? "" : token.trim());
    }

    public boolean hasToken() {
        return token() != null;
    }
}
