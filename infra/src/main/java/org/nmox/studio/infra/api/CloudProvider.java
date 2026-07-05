package org.nmox.studio.infra.api;

/**
 * The clouds the designer can deploy to. Each provider carries its API
 * base, its token storage, and the env var honored as a fallback - so
 * a single canvas can hold DigitalOcean droplets, Hetzner servers and
 * the Cloudflare DNS records that front them.
 *
 * <p>Tokens live in the OS keychain via {@link CloudTokens} (the DB
 * Studio / Contract Studio rule; pre-v1.36 plaintext preferences are
 * migrated on first read). The resolved value is cached per provider so
 * EDT callers ({@code hasToken()} gating buttons and plans) never block
 * on an OS keychain call — the designer primes the cache off the EDT.
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
        String stored = CloudTokens.readCached(prefKey, keychainDescription());
        if (!stored.isBlank()) {
            return stored;
        }
        String env = System.getenv(envVar);
        return env == null || env.isBlank() ? null : env;
    }

    public void storeToken(String token) {
        CloudTokens.store(prefKey, token, keychainDescription());
    }

    public boolean hasToken() {
        return token() != null;
    }

    private String keychainDescription() {
        return "NMOX Studio " + displayName() + " API token";
    }
}
