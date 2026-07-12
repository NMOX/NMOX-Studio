package org.nmox.studio.rack.options;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cloud API tokens are secrets and must reach the OS keychain, never
 * plaintext preferences. The v1.56 review found this Options panel — the
 * primary token-entry UI — writing them to {@code NbPreferences} node
 * {@code nmox/cloud}, the one keyring bypass the v1.36 sweep missed. This
 * source-gate pins the fix: the panel writes tokens through the platform
 * {@link org.netbeans.api.keyring.Keyring} under the same
 * {@code "nmox.cloud." + key} scheme the infra designer reads, and never
 * puts a token value into preferences. (Behavioral assertion would race
 * the off-EDT keychain write and degrade to the in-memory fallback in a
 * headless run — the invariant that matters is that no plaintext path
 * exists at all.)
 */
class CloudTokenStorageTest {

    @Test
    @DisplayName("tokens go to the keychain under nmox.cloud.*, never to plaintext preferences")
    void tokensNeverHitPlaintextPreferences() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/options/RackOptionsPanelController.java"));

        // the keyring write, under the scheme CloudTokens reads back
        assertThat(src)
                .as("tokens must be saved through the platform Keyring")
                .contains("Keyring.save(\"nmox.cloud.\" + key");

        // and NO token value may be written to the plaintext prefs node —
        // the exact leak the review found
        assertThat(src)
                .as("no token value may be put into the nmox/cloud preferences node")
                .doesNotContain("node(\"nmox/cloud\").put");
        assertThat(src)
                .as("no NbPreferences path should carry a token at all")
                .doesNotContain("NbPreferences.root().node(\"nmox/cloud\")");

        // the password char[] is zeroed after use (no lingering secret)
        assertThat(src).contains("Arrays.fill(password");
    }
}
