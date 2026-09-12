package org.nmox.studio.ui.actions;

import java.util.List;
import org.nmox.studio.core.util.Bundles;
import org.nmox.studio.rack.projectstudio.ContractKit;

/**
 * The Contract Kit's chain list, in the reader's language.
 *
 * <p>Almost every row is pure technology — `Stellar — Soroban`,
 * `CosmWasm (Cosmos)`, `Cairo (Starknet)` — and a chain's name is the same
 * word everywhere; translating one would be a mistake, not a service. So
 * this seam exists for the rows that DO carry a word (`Solana — native
 * program`), and the rest pass through untouched, exactly as
 * {@code CatalogText} treats a family that is a name (v2.133.0).
 *
 * <p>It is a seam for one label today and for whichever chain arrives with
 * a word in it tomorrow, which is the point: the next chain is a
 * translation, not a code change.
 */
final class ChainText {

    private static final String CHAIN = "Chain_";

    /** The key family this seam owns; its English is {@code Chain.label}. */
    static final List<String> KEY_PREFIXES = List.of(CHAIN);

    private ChainText() {
    }

    /** A chain as the kit's combo shows it. */
    static String label(ContractKit.Chain chain) {
        return Bundles.optional(ChainText.class, CHAIN + chain.name(), chain.label);
    }
}
