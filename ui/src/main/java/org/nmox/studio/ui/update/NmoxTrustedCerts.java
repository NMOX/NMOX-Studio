package org.nmox.studio.ui.update;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.spi.autoupdate.KeyStoreProvider;
import org.openide.util.lookup.ServiceProvider;

/**
 * The blessed certificate (v2.43.0, ledger 86's TRUSTED remainder):
 * the product's own NBM-signing certificate, offered to the platform's
 * update machinery as a trusted key store, the way Apache NetBeans
 * ships its certificate inside the IDE. Without this, a signed NBM
 * from our own update center shows as an unknown certificate to
 * accept; with it, {@code Utilities.verifyCertificates} finds the
 * signer in a {@link KeyStoreProvider.TrustLevel#TRUST} store and the
 * Plugin Manager shows the update TRUSTED.
 *
 * <p>The certificate is the PUBLIC half only, bundled as PEM beside
 * this class and parity-gated against the repo-root {@code KEYS} file
 * (the two must never drift — {@code NmoxTrustedCertsTest}). Trust is
 * for this EXACT certificate ({@code TRUST}), never a CA delegation.
 * Self-signed by decision until v3.0 (official certificates are a
 * recorded milestone); when the v3.0 certificate lands, this resource
 * is the one place to swap.
 */
@ServiceProvider(service = KeyStoreProvider.class)
public final class NmoxTrustedCerts implements KeyStoreProvider {

    private static final Logger LOG = Logger.getLogger(NmoxTrustedCerts.class.getName());
    static final String CERT_RESOURCE = "nmox-signing-cert.pem";

    private volatile KeyStore cached;

    @Override
    public KeyStore getKeyStore() {
        KeyStore ks = cached;
        if (ks == null) {
            ks = load();
            cached = ks;
        }
        return ks;
    }

    @Override
    public TrustLevel getTrustLevel() {
        return TrustLevel.TRUST;
    }

    /** Builds the trust store from the bundled PEM. */
    static KeyStore load() {
        try (InputStream in = NmoxTrustedCerts.class.getResourceAsStream(CERT_RESOURCE)) {
            return load(in);
        } catch (IOException closing) {
            return load((InputStream) null);
        }
    }

    /** Builds an in-memory trust store from the given PEM stream. A
     *  broken or missing resource yields an EMPTY store (the platform
     *  treats it as no extra trust — signed NBMs degrade to
     *  accept-once, never fail); the seam exists so that law is a
     *  test, not a comment (v2.43.3). */
    static KeyStore load(InputStream in) {
        try {
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            if (in == null) {
                LOG.log(Level.WARNING, "nmox-signing-cert.pem missing from the module");
                return ks;
            }
            Certificate cert = CertificateFactory.getInstance("X.509").generateCertificate(in);
            ks.setCertificateEntry("nmox", cert);
            return ks;
        } catch (IOException | java.security.GeneralSecurityException broken) {
            LOG.log(Level.WARNING, "could not load the NMOX signing certificate", broken);
            try {
                KeyStore empty = KeyStore.getInstance(KeyStore.getDefaultType());
                empty.load(null, null);
                return empty;
            } catch (IOException | java.security.GeneralSecurityException unreachable) {
                throw new IllegalStateException(unreachable);
            }
        }
    }
}
