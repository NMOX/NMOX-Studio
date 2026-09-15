import java.io.File;
import java.security.CodeSigner;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.TrustAnchor;
import java.util.*;
import org.netbeans.modules.autoupdate.services.Utilities;
import org.netbeans.spi.autoupdate.KeyStoreProvider;

/** Runs the platform's own NBM trust decision (the one InstallSupportImpl.verifyNbm feeds) on a shipped NBM. */
public class TrustProbe {
    public static void main(String[] a) throws Exception {
        File nbm = new File(a[0]);
        Set<Certificate> trusted = new HashSet<>();
        for (KeyStore ks : Utilities.getKeyStore(KeyStoreProvider.TrustLevel.TRUST)) {
            trusted.addAll(Utilities.getCertificates(ks));
        }
        System.out.println("TRUST-level certificates from KeyStoreProviders: " + trusted.size());
        Collection<CodeSigner> signers = Utilities.getNbmCertificates(nbm);
        System.out.println("NBM signers: " + (signers == null ? "unsigned" : signers.size()));
        for (CodeSigner s : signers) {
            String withProvider = Utilities.verifyCertificates(s, trusted, Set.<TrustAnchor>of(), List.<Certificate>of(), Set.<TrustAnchor>of());
            String withoutProvider = Utilities.verifyCertificates(s, List.<Certificate>of(), Set.<TrustAnchor>of(), List.<Certificate>of(), Set.<TrustAnchor>of());
            System.out.println("verdict with shipped provider: " + withProvider);
            System.out.println("verdict without it (control):  " + withoutProvider);
        }
    }
}
