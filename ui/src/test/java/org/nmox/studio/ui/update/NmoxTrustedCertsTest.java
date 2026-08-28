package org.nmox.studio.ui.update;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import org.netbeans.spi.autoupdate.KeyStoreProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The blessed certificate's laws: the provider is a real
 * {@link KeyStoreProvider} at TRUST level whose store holds exactly
 * the bundled certificate, and the bundled certificate IS the one
 * published in the repo-root KEYS file — the two can never drift.
 */
class NmoxTrustedCertsTest {

    @Test
    @DisplayName("TRUST-level store holding exactly the bundled certificate")
    void providerLaws() throws Exception {
        NmoxTrustedCerts p = new NmoxTrustedCerts();
        assertThat(p.getTrustLevel()).isEqualTo(KeyStoreProvider.TrustLevel.TRUST);
        KeyStore ks = p.getKeyStore();
        assertThat(ks.size()).isEqualTo(1);
        Certificate stored = ks.getCertificate("nmox");
        assertThat(stored).isNotNull();
        try (var in = NmoxTrustedCerts.class.getResourceAsStream(NmoxTrustedCerts.CERT_RESOURCE)) {
            Certificate pem = CertificateFactory.getInstance("X.509").generateCertificate(in);
            assertThat(stored).isEqualTo(pem);
        }
    }

    @Test
    @DisplayName("the bundled certificate equals the one published in KEYS")
    void keysParity() throws Exception {
        String keys = Files.readString(Path.of("..", "KEYS"), StandardCharsets.UTF_8);
        int at = keys.indexOf("-----BEGIN CERTIFICATE-----");
        int end = keys.indexOf("-----END CERTIFICATE-----");
        assertThat(at).as("KEYS carries the certificate").isPositive();
        String pemFromKeys = keys.substring(at, end + "-----END CERTIFICATE-----".length());
        Certificate published = CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(
                        pemFromKeys.getBytes(StandardCharsets.US_ASCII)));
        Certificate bundled = new NmoxTrustedCerts().getKeyStore().getCertificate("nmox");
        assertThat(bundled)
                .as("the blessed cert and the KEYS-published cert must never drift")
                .isEqualTo(published);
    }
}
