package org.nmox.studio.web3.engine;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.AbiParam;
import org.nmox.studio.web3.model.ContractArtifact;

/**
 * The pure model behind the Interact pane: one contract artifact,
 * optionally attached to a deployed address, on a network that either
 * has unlocked accounts (a local devnet) or doesn't (a remote RPC).
 * It builds the call/deploy payloads (delegating {@link AbiCodec}),
 * validates argument lists, and — the part the UI must never invent —
 * produces the honest reasons why a write action is disabled.
 *
 * <p><b>The security boundary, part three:</b> a session never sees a
 * key. Deploys and sends are {@code eth_sendTransaction} payloads for
 * the node's own unlocked accounts; when the node offers none, the
 * write surface is {@link #READ_ONLY_REASON disabled with the reason},
 * not worked around.
 *
 * <p>Immutable — connection refreshes swap in a new session via
 * {@link #withAccounts}, a successful deploy via {@link #attachedTo}.
 */
public final class InteractSession {

    /** Why SEND/Deploy are off on a network without unlocked accounts. */
    public static final String READ_ONLY_REASON =
            "Read-only network — no unlocked accounts. Deploys and sends need "
            + "a local devnet (ANVIL) or your own wallet/CLI.";

    /** The deploy-form hint when the constructor takes nothing. */
    public static final String NO_CONSTRUCTOR_PARAMS = "Constructor has no parameters";

    private final ContractArtifact artifact;
    private final String address;
    private final boolean unlockedAccounts;

    private InteractSession(ContractArtifact artifact, String address,
            boolean unlockedAccounts) {
        this.artifact = Objects.requireNonNull(artifact, "artifact");
        this.address = address == null || address.isBlank() ? null : address.trim();
        this.unlockedAccounts = unlockedAccounts;
    }

    /** A session for deploying the artifact — not attached to an address yet. */
    public static InteractSession deploying(ContractArtifact artifact,
            boolean unlockedAccounts) {
        return new InteractSession(artifact, null, unlockedAccounts);
    }

    /** A session attached to a live instance at {@code address}. */
    public static InteractSession attached(ContractArtifact artifact, String address,
            boolean unlockedAccounts) {
        return new InteractSession(artifact, address, unlockedAccounts);
    }

    /** The same session with a fresh unlocked-accounts verdict (connection refresh). */
    public InteractSession withAccounts(boolean hasUnlockedAccounts) {
        return new InteractSession(artifact, address, hasUnlockedAccounts);
    }

    /** The same session attached to {@code deployedAddress} (post-deploy). */
    public InteractSession attachedTo(String deployedAddress) {
        return new InteractSession(artifact, deployedAddress, unlockedAccounts);
    }

    public ContractArtifact artifact() {
        return artifact;
    }

    /** The attached address, or null while in deploy mode. */
    public String address() {
        return address;
    }

    /** True once the session points at a live instance. */
    public boolean attached() {
        return address != null;
    }

    /** True when the node offered unlocked accounts (eth_accounts non-empty). */
    public boolean hasUnlockedAccounts() {
        return unlockedAccounts;
    }

    // ---- the constructor form ------------------------------------------

    /** The constructor's parameters; empty when the ABI declares none. */
    public List<AbiParam> constructorParams() {
        return artifact.constructor().map(AbiEntry::inputs).orElse(List.of());
    }

    /** True when the constructor accepts ETH. */
    public boolean constructorPayable() {
        return artifact.constructor()
                .map(c -> "payable".equals(c.stateMutability()))
                .orElse(false);
    }

    /** {@value #NO_CONSTRUCTOR_PARAMS} when the form has no fields; else null. */
    public String constructorHint() {
        return constructorParams().isEmpty() ? NO_CONSTRUCTOR_PARAMS : null;
    }

    // ---- the function lists ----------------------------------------------

    /** view/pure functions — safe to eth_call. */
    public List<AbiEntry> readFunctions() {
        return artifact.functions().stream().filter(AbiEntry::readOnly).toList();
    }

    /** State-changing functions — eth_sendTransaction territory. */
    public List<AbiEntry> writeFunctions() {
        return artifact.functions().stream().filter(f -> !f.readOnly()).toList();
    }

    // ---- the honest disabled-reasons ---------------------------------------

    /**
     * Why Deploy is disabled, or null when it may fire: no unlocked
     * accounts ({@link #READ_ONLY_REASON}), or an artifact with no
     * creation bytecode (an interface or abstract contract).
     */
    public String deployDisabledReason() {
        if (!unlockedAccounts) {
            return READ_ONLY_REASON;
        }
        if (!hasBytecode()) {
            return artifact.name() + " has no creation bytecode — interfaces and "
                    + "abstract contracts can't be deployed.";
        }
        return null;
    }

    /** Why SEND is disabled, or null when writes may fire. */
    public String sendDisabledReason() {
        return unlockedAccounts ? null : READ_ONLY_REASON;
    }

    private boolean hasBytecode() {
        return artifact.bytecodeHex() != null
                && !Hex.strip0x(artifact.bytecodeHex().trim()).isEmpty();
    }

    // ---- payloads -----------------------------------------------------------

    /**
     * The {@code eth_sendTransaction} data for deploying this artifact:
     * {@code 0x} + creation bytecode + ABI-encoded constructor args.
     *
     * @throws IllegalStateException    when {@link #deployDisabledReason()}
     *         is non-null (the UI gates; this is the belt and braces)
     * @throws IllegalArgumentException with the codec's status-bar-ready
     *         refusal when an argument doesn't encode
     */
    public String deployData(List<String> args) {
        String reason = deployDisabledReason();
        if (reason != null) {
            throw new IllegalStateException(reason);
        }
        byte[] encodedArgs = AbiCodec.encodeArgs(constructorParams(), args);
        return "0x" + Hex.strip0x(artifact.bytecodeHex().trim())
                + Hex.toHex(encodedArgs);
    }

    /**
     * The calldata for one function: selector + encoded args. Works for
     * reads (eth_call) and writes (eth_sendTransaction) alike.
     *
     * @throws IllegalArgumentException with the codec's refusal message
     *         when an argument doesn't encode (tuples, range overflows, …)
     */
    public String callData(AbiEntry function, List<String> args) {
        return AbiCodec.encodeCall(function, args);
    }

    /**
     * A payable-value field's text as a 0x-hex wei quantity, or null
     * when the field is blank (no value attached).
     *
     * @throws IllegalArgumentException with {@link Units#parseEth}'s
     *         human message on non-numbers and sub-wei fractions
     */
    public static String valueWeiHex(String ethText) {
        if (ethText == null || ethText.isBlank()) {
            return null;
        }
        BigInteger wei = Units.parseEth(ethText);
        return "0x" + wei.toString(16);
    }
}
