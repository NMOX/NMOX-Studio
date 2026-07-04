package org.nmox.studio.web3.model;

import java.util.List;
import java.util.Optional;

/**
 * One compiled contract as read from a Foundry {@code out/} or Hardhat
 * {@code artifacts/} JSON file — everything Contract Studio needs to
 * deploy and interact, nothing it doesn't (no metadata blob, no AST).
 *
 * @param name                the contract name, e.g. {@code Counter}
 * @param sourcePath          the source it was compiled from, e.g.
 *                            {@code src/Counter.sol}; {@code ""} when the
 *                            artifact doesn't say
 * @param abi                 the parsed ABI entries (defensively copied)
 * @param bytecodeHex         the creation (init) bytecode, 0x-prefixed
 *                            hex; may be {@code "0x"} for interfaces and
 *                            abstract contracts
 * @param deployedBytecodeHex the runtime bytecode, 0x-prefixed hex — the
 *                            thing EIP-170 limits
 */
public record ContractArtifact(
        String name,
        String sourcePath,
        List<AbiEntry> abi,
        String bytecodeHex,
        String deployedBytecodeHex) {

    public ContractArtifact {
        abi = List.copyOf(abi);
    }

    /** The constructor entry, when the ABI declares one. */
    public Optional<AbiEntry> constructor() {
        return abi.stream()
                .filter(e -> e.kind() == AbiEntry.Kind.CONSTRUCTOR)
                .findFirst();
    }

    /** All function entries, in ABI order. */
    public List<AbiEntry> functions() {
        return abi.stream()
                .filter(e -> e.kind() == AbiEntry.Kind.FUNCTION)
                .toList();
    }

    /** All event entries, in ABI order. */
    public List<AbiEntry> events() {
        return abi.stream()
                .filter(e -> e.kind() == AbiEntry.Kind.EVENT)
                .toList();
    }

    /** All custom error entries, in ABI order. */
    public List<AbiEntry> errors() {
        return abi.stream()
                .filter(e -> e.kind() == AbiEntry.Kind.ERROR)
                .toList();
    }
}
