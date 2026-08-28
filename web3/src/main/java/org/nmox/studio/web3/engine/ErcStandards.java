package org.nmox.studio.web3.engine;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.AbiParam;

/**
 * Recognizes the token standards an ABI actually implements (v2.44.0,
 * the token tranche): ERC-20, ERC-721, ERC-1155 — detected by the
 * presence of EVERY required function signature from the standard,
 * never by name similarity or a partial match (the wrong-guess-mutates
 * law: a mushy match would put a token face on a non-token contract).
 * The optional metadata extensions (name/symbol/decimals) deliberately
 * do NOT gate detection — the standard makes them optional, so the
 * token strip reads them only when the ABI carries them and shows an
 * honest absent otherwise.
 *
 * <p>Detection order 1155 → 721 → 20 is deterministic but the sets are
 * disjoint in practice: ERC-721 has no {@code transfer(address,uint256)},
 * ERC-20 has no {@code ownerOf(uint256)}, ERC-1155's balanceOf takes
 * two arguments.
 *
 * <p>Also home to the CANONICAL token events every EVM tool knows, so
 * the transaction inspector can decode Transfer/Approval logs even
 * when no workspace artifact carries the ABI.
 */
public final class ErcStandards {

    public enum Standard {
        ERC20("ERC-20"), ERC721("ERC-721"), ERC1155("ERC-1155");

        private final String label;

        Standard(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final Set<String> ERC20_REQUIRED = Set.of(
            "totalSupply()",
            "balanceOf(address)",
            "transfer(address,uint256)",
            "transferFrom(address,address,uint256)",
            "approve(address,uint256)",
            "allowance(address,address)");

    private static final Set<String> ERC721_REQUIRED = Set.of(
            "balanceOf(address)",
            "ownerOf(uint256)",
            "safeTransferFrom(address,address,uint256)",
            "transferFrom(address,address,uint256)",
            "approve(address,uint256)",
            "getApproved(uint256)",
            "setApprovalForAll(address,bool)",
            "isApprovedForAll(address,address)");

    private static final Set<String> ERC1155_REQUIRED = Set.of(
            "balanceOf(address,uint256)",
            "balanceOfBatch(address[],uint256[])",
            "setApprovalForAll(address,bool)",
            "isApprovedForAll(address,address)",
            "safeTransferFrom(address,address,uint256,uint256,bytes)",
            "safeBatchTransferFrom(address,address,uint256[],uint256[],bytes)");

    /** ERC-20 Transfer(from indexed, to indexed, value). */
    public static final AbiEntry ERC20_TRANSFER = AbiEntry.event("Transfer", List.of(
            new AbiParam("from", "address", true),
            new AbiParam("to", "address", true),
            new AbiParam("value", "uint256", false)));

    /** ERC-20 Approval(owner indexed, spender indexed, value). */
    public static final AbiEntry ERC20_APPROVAL = AbiEntry.event("Approval", List.of(
            new AbiParam("owner", "address", true),
            new AbiParam("spender", "address", true),
            new AbiParam("value", "uint256", false)));

    /** ERC-721 Transfer — same signature as ERC-20's but all-indexed. */
    public static final AbiEntry ERC721_TRANSFER = AbiEntry.event("Transfer", List.of(
            new AbiParam("from", "address", true),
            new AbiParam("to", "address", true),
            new AbiParam("tokenId", "uint256", true)));

    private ErcStandards() {
    }

    /**
     * The standard this ABI fully implements, or null. All required
     * signatures must be present as functions — nothing less counts.
     */
    public static Standard detect(List<AbiEntry> abi) {
        Set<String> functions = abi.stream()
                .filter(e -> e.kind() == AbiEntry.Kind.FUNCTION)
                .map(AbiEntry::signature)
                .collect(Collectors.toSet());
        if (functions.containsAll(ERC1155_REQUIRED)) {
            return Standard.ERC1155;
        }
        if (functions.containsAll(ERC721_REQUIRED)) {
            return Standard.ERC721;
        }
        if (functions.containsAll(ERC20_REQUIRED)) {
            return Standard.ERC20;
        }
        return null;
    }

    /**
     * The zero-argument metadata reader with this name, when the ABI
     * carries one ({@code name()}, {@code symbol()}, {@code decimals()}
     * are optional extensions) — else null, and the caller shows the
     * absence honestly instead of inventing a call.
     */
    public static AbiEntry metadataReader(List<AbiEntry> abi, String name) {
        return abi.stream()
                .filter(e -> e.kind() == AbiEntry.Kind.FUNCTION)
                .filter(e -> e.name().equals(name) && e.inputs().isEmpty())
                .findFirst().orElse(null);
    }
}
