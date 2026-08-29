package org.nmox.studio.web3.model;

import java.util.Objects;

/**
 * A contract the user brought in BY ABI rather than by building it
 * (v2.45.0, the definitive-engagement arc): the door to interacting
 * with any deployed contract on any chain — USDC on a mainnet fork,
 * a teammate's deployment, a protocol you never compiled. The ABI is
 * kept as the VERBATIM JSON the user pasted (fidelity over
 * re-serialization) and parsed by the same {@code ArtifactScanner}
 * parser every built artifact goes through — one parser, one truth.
 *
 * @param name    the display name; workspace-unique (the parse-time
 *                heal keeps the first occurrence on a merge collision)
 * @param abiJson the ABI as a JSON array string, verbatim
 * @param address the deployed address this ABI was imported for, or
 *                {@code ""} when the user wants to attach later
 */
public record ImportedContract(String name, String abiJson, String address) {

    public ImportedContract {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(abiJson, "abiJson");
        address = address == null ? "" : address.trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("Imported contract needs a name");
        }
    }
}
