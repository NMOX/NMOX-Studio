package org.nmox.studio.web3.engine;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.AbiParam;
import org.nmox.studio.web3.model.ContractArtifact;

/**
 * topic0 → event lookup across every scanned artifact, built once per
 * scan: {@code Keccak256(signature)} of each event entry keyed to the
 * artifact that declares it. The Watch poller asks {@link #match} for
 * each log's first topic; unknown topics return null and are skipped —
 * a chain full of other people's events is normal, not an error.
 *
 * <p>Immutable and safe to share between the EDT and the poller thread.
 */
public final class EventMatcher {

    /** One known event: the artifact declaring it plus its ABI entry. */
    public record Match(String contractName, AbiEntry event) {
    }

    private final Map<String, Match> byTopic0;

    private EventMatcher(Map<String, Match> byTopic0) {
        this.byTopic0 = Map.copyOf(byTopic0);
    }

    /** An index over every event of every artifact; first declaration wins a name clash. */
    public static EventMatcher build(List<ContractArtifact> artifacts) {
        Map<String, Match> map = new HashMap<>();
        if (artifacts != null) {
            for (ContractArtifact artifact : artifacts) {
                for (AbiEntry event : artifact.events()) {
                    map.putIfAbsent(Keccak256.hashHex(event.signature()),
                            new Match(artifact.name(), event));
                }
            }
        }
        return new EventMatcher(map);
    }

    /** The empty index — before the first scan. */
    public static EventMatcher empty() {
        return new EventMatcher(Map.of());
    }

    /** How many distinct event signatures are known. */
    public int size() {
        return byTopic0.size();
    }

    /** The event behind a log's topic0, or null when no artifact declares it. */
    public Match match(String topic0) {
        if (topic0 == null) {
            return null;
        }
        return byTopic0.get(Hex.strip0x(topic0.trim()).toLowerCase(Locale.ROOT));
    }

    /**
     * Decodes one log against the matched event and applies the display
     * rule ({@link DisplayValues#display}: wei-named uint256s in ETH
     * units, addresses shortened). Parameter order follows the
     * declaration; unnamed parameters are {@code arg0}, {@code arg1}, …
     *
     * @throws IllegalArgumentException when the log doesn't fit the
     *         event (too few topics, malformed data) — callers wanting
     *         a never-throws string use {@link #describe}
     */
    public Map<String, String> decodedDisplay(Match match, List<String> topics,
            String data) {
        Map<String, String> raw = AbiCodec.decodeEventLog(match.event(), topics, data);
        Map<String, String> out = new LinkedHashMap<>();
        List<AbiParam> inputs = match.event().inputs();
        for (int i = 0; i < inputs.size(); i++) {
            AbiParam param = inputs.get(i);
            String key = param.name() == null || param.name().isBlank()
                    ? "arg" + i : param.name();
            out.put(key, DisplayValues.display(key, param.type(), raw.get(key)));
        }
        return out;
    }

    /**
     * The feed line for one matched log:
     * {@code Transfer(from: 0x70997970…79C8, to: 0x3C44CdDd…293BC, value: 1.5 ETH)}.
     * A log that doesn't decode yields an honest note, never a throw —
     * the Watch poller must survive any garbage the chain sends.
     */
    public String describe(Match match, List<String> topics, String data) {
        try {
            StringJoiner joiner = new StringJoiner(", ",
                    match.event().name() + "(", ")");
            decodedDisplay(match, topics, data)
                    .forEach((name, value) -> joiner.add(name + ": " + value));
            return joiner.toString();
        } catch (RuntimeException malformed) {
            return match.event().name() + "(decode failed: "
                    + malformed.getMessage() + ")";
        }
    }
}
