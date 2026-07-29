package org.nmox.studio.infra.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The provider enum's public face: stable API bases, human display
 * names, and token resolution through the {@link CloudTokens} session
 * cache. Runs entirely on the degraded (in-memory) token store — the
 * keyring seam is forced off and cleanup goes through
 * {@link CloudTokens#forgetForTest}, so a developer machine's real
 * keychain and legacy preferences are never read, written, or removed.
 */
class CloudProviderTest {

    @BeforeEach
    void forceInMemoryFallback() {
        CloudTokens.keyringUsable = false;
    }

    @AfterEach
    void forgetPrimedTokens() {
        CloudTokens.forgetForTest("doToken");
        CloudTokens.forgetForTest("hetznerToken");
        CloudTokens.forgetForTest("cloudflareToken");
    }

    @Test
    @DisplayName("each provider carries its real API base")
    void apiBases() {
        assertThat(CloudProvider.DIGITALOCEAN.apiBase()).isEqualTo("https://api.digitalocean.com");
        assertThat(CloudProvider.HETZNER.apiBase()).isEqualTo("https://api.hetzner.cloud/v1");
        assertThat(CloudProvider.CLOUDFLARE.apiBase()).isEqualTo("https://api.cloudflare.com/client/v4");
    }

    @Test
    @DisplayName("display names are the human brand names, not enum spellings")
    void displayNames() {
        assertThat(CloudProvider.DIGITALOCEAN.displayName()).isEqualTo("DigitalOcean");
        assertThat(CloudProvider.HETZNER.displayName()).isEqualTo("Hetzner Cloud");
        assertThat(CloudProvider.CLOUDFLARE.displayName()).isEqualTo("Cloudflare");
    }

    @Test
    @DisplayName("a stored token is what token() resolves, and hasToken() agrees")
    void storedTokenResolves() {
        CloudProvider.DIGITALOCEAN.storeToken("unit-token-do");

        assertThat(CloudProvider.DIGITALOCEAN.token()).isEqualTo("unit-token-do");
        assertThat(CloudProvider.DIGITALOCEAN.hasToken()).isTrue();
    }

    @Test
    @DisplayName("without a stored token, token() and hasToken() stay consistent")
    void unstoredTokenIsConsistent() {
        // the machine may carry an env-var fallback; the contract under test
        // is consistency, not a particular value
        String token = CloudProvider.HETZNER.token();
        assertThat(CloudProvider.HETZNER.hasToken()).isEqualTo(token != null);
        if (token != null) {
            assertThat(token).isNotBlank();
        }
    }
}
