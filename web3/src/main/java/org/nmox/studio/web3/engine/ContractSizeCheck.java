package org.nmox.studio.web3.engine;

import java.util.Locale;
import org.nmox.studio.web3.model.ContractArtifact;

/**
 * The two protocol size limits every contract must respect:
 * EIP-170 caps <em>deployed</em> (runtime) bytecode at 24,576 bytes —
 * over it, mainnet-shaped chains refuse the deployment outright — and
 * EIP-3860 caps <em>initcode</em> at 49,152 bytes.
 */
public final class ContractSizeCheck {

    /** EIP-170: max deployed bytecode, bytes. */
    public static final int EIP170_LIMIT = 24_576;

    /** EIP-3860: max initcode, bytes. */
    public static final int EIP3860_LIMIT = 49_152;

    /**
     * One verdict line for the Oversight size table.
     *
     * @param contractName the artifact's name
     * @param sizeBytes    the measured bytecode size
     * @param limitBytes   the limit it was measured against
     * @param pct          size as a percentage of the limit
     * @param over         true when the contract exceeds the limit
     * @param message      status-bar-ready, e.g.
     *                     {@code "Counter: 1,204 / 24,576 bytes (4.9%)"}
     */
    public record Verdict(String contractName, int sizeBytes, int limitBytes,
            double pct, boolean over, String message) {
    }

    private ContractSizeCheck() {
    }

    /** The deployed (runtime) bytecode against EIP-170's 24,576 bytes. */
    public static Verdict check(ContractArtifact artifact) {
        return verdict(artifact.name(), byteLength(artifact.deployedBytecodeHex()),
                EIP170_LIMIT, "EIP-170");
    }

    /** The creation (init) bytecode against EIP-3860's 49,152 bytes. */
    public static Verdict checkInitcode(ContractArtifact artifact) {
        return verdict(artifact.name(), byteLength(artifact.bytecodeHex()),
                EIP3860_LIMIT, "EIP-3860");
    }

    private static Verdict verdict(String name, int size, int limit, String eip) {
        double pct = size * 100.0 / limit;
        boolean over = size > limit;
        String message = String.format(Locale.ROOT, "%s: %,d / %,d bytes (%.1f%%)",
                name, size, limit, pct);
        if (over) {
            message += " — over the " + eip + " limit";
        }
        return new Verdict(name, size, limit, pct, over, message);
    }

    /** Hex length → bytes; blank, {@code 0x}, or malformed hex count as 0. */
    private static int byteLength(String hex) {
        if (hex == null) {
            return 0;
        }
        String digits = Hex.strip0x(hex.trim());
        if (digits.isEmpty() || digits.length() % 2 != 0) {
            return 0;
        }
        return digits.length() / 2;
    }
}
