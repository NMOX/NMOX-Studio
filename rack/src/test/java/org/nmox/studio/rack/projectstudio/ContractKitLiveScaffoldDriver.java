package org.nmox.studio.rack.projectstudio;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Not a test: a driver the live-proof script uses to scaffold all chains
 *  into -Dnmox.kit.live.dir for real-toolchain runs. CI never sets it. */
class ContractKitLiveScaffoldDriver {

    @Test
    @EnabledIfSystemProperty(named = "nmox.kit.live.dir", matches = ".+")
    void scaffoldAll() throws Exception {
        File root = new File(System.getProperty("nmox.kit.live.dir"));
        for (ContractKit.Chain chain : ContractKit.Chain.values()) {
            File dir = new File(root, chain.name().toLowerCase());
            if (!dir.mkdirs()) {
                throw new IllegalStateException("mkdir " + dir);
            }
            ContractKit.scaffold(dir, chain, "SkyVault");
        }
    }
}
