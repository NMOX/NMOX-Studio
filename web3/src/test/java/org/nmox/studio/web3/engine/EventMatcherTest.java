package org.nmox.studio.web3.engine;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.AbiParam;
import org.nmox.studio.web3.model.ContractArtifact;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The topic0 → event index the Watch poller consults: known signatures
 * decode with the display rule applied, unknown topics return null (a
 * chain full of other people's events is normal), and malformed logs
 * describe honestly instead of throwing.
 */
class EventMatcherTest {

    private static final AbiEntry INCREMENT = AbiEntry.event("Increment",
            List.of(AbiParam.of("newNumber", "uint256")));
    private static final AbiEntry TRANSFER = AbiEntry.event("Transfer", List.of(
            new AbiParam("from", "address", true),
            new AbiParam("to", "address", true),
            new AbiParam("value", "uint256", false)));

    private static final ContractArtifact COUNTER = new ContractArtifact(
            "Counter", "src/Counter.sol", List.of(INCREMENT), "0xaa", "0xbb");
    private static final ContractArtifact TOKEN = new ContractArtifact(
            "Token", "src/Token.sol", List.of(TRANSFER), "0xcc", "0xdd");

    private static final String FROM = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private static final String TO = "0x3c44cdddb6a900fa2b585dd299e03d12fa4293bc";

    private static String topic0(AbiEntry event) {
        return "0x" + Keccak256.hashHex(event.signature());
    }

    private static String addressTopic(String address) {
        return "0x" + "0".repeat(24) + Hex.strip0x(address).toLowerCase(Locale.ROOT);
    }

    private static String word(BigInteger value) {
        return "0x" + String.format("%064x", value);
    }

    @Test
    @DisplayName("build() indexes every event of every artifact by its signature hash")
    void buildsIndex() {
        EventMatcher matcher = EventMatcher.build(List.of(COUNTER, TOKEN));

        assertThat(matcher.size()).isEqualTo(2);
        EventMatcher.Match match = matcher.match(topic0(INCREMENT));
        assertThat(match).isNotNull();
        assertThat(match.contractName()).isEqualTo("Counter");
        assertThat(match.event().name()).isEqualTo("Increment");
    }

    @Test
    @DisplayName("unknown and null topics return null — the poller skips them")
    void unknownTopicsSkip() {
        EventMatcher matcher = EventMatcher.build(List.of(COUNTER));

        assertThat(matcher.match("0x" + "ab".repeat(32))).isNull();
        assertThat(matcher.match(null)).isNull();
        assertThat(EventMatcher.empty().match(topic0(INCREMENT))).isNull();
    }

    @Test
    @DisplayName("matching is 0x- and case-tolerant on the topic hex")
    void topicNormalization() {
        EventMatcher matcher = EventMatcher.build(List.of(COUNTER));
        String bare = Keccak256.hashHex(INCREMENT.signature()).toUpperCase(Locale.ROOT);

        assertThat(matcher.match(bare)).isNotNull();
    }

    @Test
    @DisplayName("a data-only event decodes with raw numbers (no wei name, no unit)")
    void decodesDataOnlyEvent() {
        EventMatcher matcher = EventMatcher.build(List.of(COUNTER));
        EventMatcher.Match match = matcher.match(topic0(INCREMENT));

        Map<String, String> decoded = matcher.decodedDisplay(match,
                List.of(topic0(INCREMENT)), word(BigInteger.valueOf(5)));

        assertThat(decoded).containsExactly(Map.entry("newNumber", "5"));
    }

    @Test
    @DisplayName("indexed addresses shorten and a value param shows in ETH units")
    void decodesTransferWithDisplayRule() {
        EventMatcher matcher = EventMatcher.build(List.of(TOKEN));
        EventMatcher.Match match = matcher.match(topic0(TRANSFER));

        Map<String, String> decoded = matcher.decodedDisplay(match,
                List.of(topic0(TRANSFER), addressTopic(FROM), addressTopic(TO)),
                word(new BigInteger("1500000000000000000")));

        assertThat(decoded).containsExactly(
                Map.entry("from", "0x709979…79c8"),
                Map.entry("to", "0x3c44cd…93bc"),
                Map.entry("value", "1.5 ETH"));
    }

    @Test
    @DisplayName("describe() renders the one-line feed text")
    void describeRendersLine() {
        EventMatcher matcher = EventMatcher.build(List.of(TOKEN));
        EventMatcher.Match match = matcher.match(topic0(TRANSFER));

        String line = matcher.describe(match,
                List.of(topic0(TRANSFER), addressTopic(FROM), addressTopic(TO)),
                word(new BigInteger("1500000000000000000")));

        assertThat(line).isEqualTo(
                "Transfer(from: 0x709979…79c8, to: 0x3c44cd…93bc, value: 1.5 ETH)");
    }

    @Test
    @DisplayName("a malformed log describes honestly instead of throwing")
    void describeSurvivesGarbage() {
        EventMatcher matcher = EventMatcher.build(List.of(TOKEN));
        EventMatcher.Match match = matcher.match(topic0(TRANSFER));

        // missing both indexed topics
        String line = matcher.describe(match, List.of(topic0(TRANSFER)), "0x");

        assertThat(line).startsWith("Transfer(decode failed: ");
    }

    @Test
    @DisplayName("build(null) and empty() are empty, not exceptions")
    void emptyInputs() {
        assertThat(EventMatcher.build(null).size()).isZero();
        assertThat(EventMatcher.empty().size()).isZero();
    }
}
