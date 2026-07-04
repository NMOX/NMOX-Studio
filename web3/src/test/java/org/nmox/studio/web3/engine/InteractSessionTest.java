package org.nmox.studio.web3.engine;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.AbiParam;
import org.nmox.studio.web3.model.ContractArtifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * The Interact pane's pure model: payload building delegates the
 * codec, argument mistakes surface as the codec's own refusals, and —
 * the security boundary's UI face — the disabled-reasons are exact,
 * test-pinned sentences.
 */
class InteractSessionTest {

    private static final AbiEntry CTOR = AbiEntry.constructor(
            List.of(AbiParam.of("start", "uint256")), "nonpayable");
    private static final AbiEntry NUMBER = AbiEntry.function("number",
            List.of(), List.of(AbiParam.of("", "uint256")), "view");
    private static final AbiEntry SET_NUMBER = AbiEntry.function("setNumber",
            List.of(AbiParam.of("newNumber", "uint256")), List.of(), "nonpayable");
    private static final AbiEntry DEPOSIT = AbiEntry.function("deposit",
            List.of(), List.of(), "payable");

    private static final ContractArtifact COUNTER = new ContractArtifact(
            "Counter", "src/Counter.sol",
            List.of(CTOR, NUMBER, SET_NUMBER, DEPOSIT),
            "0x6080aabb", "0x6080");

    private static final ContractArtifact IFACE = new ContractArtifact(
            "ICounter", "src/ICounter.sol", List.of(NUMBER), "0x", "0x");

    // ---- attachment states ----

    @Test
    @DisplayName("deploying() starts unattached; attachedTo() flips it with the address")
    void attachmentLifecycle() {
        InteractSession session = InteractSession.deploying(COUNTER, true);

        assertThat(session.attached()).isFalse();
        assertThat(session.address()).isNull();

        InteractSession attached = session.attachedTo("0x1234");
        assertThat(attached.attached()).isTrue();
        assertThat(attached.address()).isEqualTo("0x1234");
        assertThat(attached.artifact()).isSameAs(COUNTER);
    }

    @Test
    @DisplayName("a blank address means not attached")
    void blankAddressIsUnattached() {
        assertThat(InteractSession.attached(COUNTER, "  ", true).attached()).isFalse();
    }

    @Test
    @DisplayName("withAccounts() re-verdicts the same artifact and address")
    void withAccountsKeepsIdentity() {
        InteractSession readOnly = InteractSession.attached(COUNTER, "0xabc", false);

        InteractSession armed = readOnly.withAccounts(true);

        assertThat(armed.address()).isEqualTo("0xabc");
        assertThat(armed.hasUnlockedAccounts()).isTrue();
        assertThat(armed.sendDisabledReason()).isNull();
    }

    // ---- the honest disabled-reasons ----

    @Test
    @DisplayName("no unlocked accounts: deploy and send carry the exact read-only sentence")
    void readOnlyReason() {
        InteractSession session = InteractSession.deploying(COUNTER, false);

        assertThat(session.deployDisabledReason()).isEqualTo(
                "Read-only network — no unlocked accounts. Deploys and sends need "
                + "a local devnet (ANVIL) or your own wallet/CLI.");
        assertThat(session.sendDisabledReason())
                .isEqualTo(InteractSession.READ_ONLY_REASON);
    }

    @Test
    @DisplayName("an interface (bytecode 0x) can't deploy, and the reason names it")
    void interfaceCannotDeploy() {
        InteractSession session = InteractSession.deploying(IFACE, true);

        assertThat(session.deployDisabledReason()).isEqualTo(
                "ICounter has no creation bytecode — interfaces and abstract "
                + "contracts can't be deployed.");
    }

    @Test
    @DisplayName("unlocked accounts plus real bytecode: nothing is disabled")
    void armedSessionHasNoReasons() {
        InteractSession session = InteractSession.deploying(COUNTER, true);

        assertThat(session.deployDisabledReason()).isNull();
        assertThat(session.sendDisabledReason()).isNull();
    }

    @Test
    @DisplayName("deployData refuses (IllegalState) while deploy is disabled — belt and braces")
    void deployDataRefusesWhenDisabled() {
        InteractSession session = InteractSession.deploying(COUNTER, false);

        assertThatIllegalStateException()
                .isThrownBy(() -> session.deployData(List.of("1")))
                .withMessage(InteractSession.READ_ONLY_REASON);
    }

    // ---- constructor form ----

    @Test
    @DisplayName("constructor params and payability come straight from the ABI")
    void constructorShape() {
        InteractSession session = InteractSession.deploying(COUNTER, true);

        assertThat(session.constructorParams()).hasSize(1);
        assertThat(session.constructorParams().get(0).name()).isEqualTo("start");
        assertThat(session.constructorPayable()).isFalse();
        assertThat(session.constructorHint()).isNull();
    }

    @Test
    @DisplayName("a parameterless constructor yields the exact form hint")
    void parameterlessConstructorHint() {
        ContractArtifact noCtor = new ContractArtifact("Plain", "", List.of(NUMBER),
                "0xdead", "0xbeef");

        InteractSession session = InteractSession.deploying(noCtor, true);

        assertThat(session.constructorHint()).isEqualTo("Constructor has no parameters");
        assertThat(session.constructorParams()).isEmpty();
    }

    @Test
    @DisplayName("a payable constructor reports payable")
    void payableConstructor() {
        ContractArtifact payable = new ContractArtifact("Vault", "",
                List.of(AbiEntry.constructor(List.of(), "payable")), "0xaa", "0xbb");

        assertThat(InteractSession.deploying(payable, true).constructorPayable()).isTrue();
    }

    // ---- function partitions ----

    @Test
    @DisplayName("read functions are view/pure; everything else is a write")
    void functionPartition() {
        InteractSession session = InteractSession.deploying(COUNTER, true);

        assertThat(session.readFunctions()).extracting(AbiEntry::name)
                .containsExactly("number");
        assertThat(session.writeFunctions()).extracting(AbiEntry::name)
                .containsExactly("setNumber", "deposit");
    }

    // ---- payloads ----

    @Test
    @DisplayName("deployData = 0x + creation bytecode + encoded constructor args")
    void deployDataShape() {
        InteractSession session = InteractSession.deploying(COUNTER, true);

        String data = session.deployData(List.of("42"));

        assertThat(data).startsWith("0x6080aabb");
        assertThat(data).endsWith("2a"); // 42 in the tail word
        assertThat(data).hasSize(2 + 8 + 64); // 0x + 4 bytecode bytes + one word
    }

    @Test
    @DisplayName("a 0x-prefixed bytecode isn't double-prefixed")
    void deployDataSinglePrefix() {
        ContractArtifact noCtor = new ContractArtifact("Plain", "", List.of(),
                "0xdeadbeef", "0x");

        assertThat(InteractSession.deploying(noCtor, true).deployData(List.of()))
                .isEqualTo("0xdeadbeef");
    }

    @Test
    @DisplayName("wrong argument count surfaces the codec's own message")
    void deployArgCountRefusal() {
        InteractSession session = InteractSession.deploying(COUNTER, true);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> session.deployData(List.of()))
                .withMessageContaining("Expected 1 argument");
    }

    @Test
    @DisplayName("callData = selector + encoded args, matching the signature hash")
    void callDataShape() {
        InteractSession session = InteractSession.attached(COUNTER, "0xabc", true);

        String data = session.callData(SET_NUMBER, List.of("7"));

        String selector = Hex.toHex(Keccak256.selector("setNumber(uint256)"));
        assertThat(data).startsWith("0x" + selector);
        assertThat(data).hasSize(2 + 8 + 64);
    }

    @Test
    @DisplayName("tuple parameters pass the codec's honest refusal through verbatim")
    void tupleRefusalPassesThrough() {
        AbiEntry tupleFn = AbiEntry.function("configure",
                List.of(AbiParam.of("config", "tuple")), List.of(), "nonpayable");
        InteractSession session = InteractSession.attached(COUNTER, "0xabc", true);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> session.callData(tupleFn, List.of("{}")))
                .withMessage("Tuple parameters aren't supported yet — use cast for this call.");
    }

    // ---- the payable value field ----

    @Test
    @DisplayName("valueWeiHex: blank means no value, ETH parses to 0x wei")
    void valueWeiHex() {
        assertThat(InteractSession.valueWeiHex(null)).isNull();
        assertThat(InteractSession.valueWeiHex("   ")).isNull();
        assertThat(InteractSession.valueWeiHex("1.5")).isEqualTo("0x14d1120d7b160000");
        assertThat(InteractSession.valueWeiHex("0")).isEqualTo("0x0");
    }

    @Test
    @DisplayName("a non-number value field surfaces Units' human message")
    void valueWeiHexRefusal() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> InteractSession.valueWeiHex("lots"))
                .withMessageContaining("not a number");
    }
}
