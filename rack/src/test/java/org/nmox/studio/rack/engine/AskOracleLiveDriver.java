package org.nmox.studio.rack.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.nmox.studio.rack.engine.OracleClient.CodeQuestion;
import org.nmox.studio.rack.service.OracleKeys;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Not a test: the Ask ORACLE live proof, run by hand against the real
 * Anthropic API before ship ({@code -Dnmox.oracle.live=1} with an
 * ANTHROPIC_API_KEY/CLAUDE_API_KEY in the environment). CI never sets
 * the property; the key is read through {@link OracleKeys}' normal env
 * fallback and never logged.
 */
class AskOracleLiveDriver {

    @Test
    @EnabledIfSystemProperty(named = "nmox.oracle.live", matches = ".+")
    void liveAsk() {
        AskOracleEngine engine = new AskOracleEngine(new OracleClient(),
                OracleKeys::read, unused -> true);
        CodeQuestion q = new CodeQuestion("counter.clar", "text/x-clarity",
                "(define-public (reset)\n  (begin\n"
                + "    (asserts! (is-eq tx-sender contract-owner) err-owner-only)\n"
                + "    (var-set count u0)\n    (ok true)))",
                "Who can successfully call this function, and what happens to others?");
        AskOracleEngine.Result r = engine.answer(q, OracleClient.MODEL_HAIKU);
        System.out.println("LIVE STATUS: " + r.status());
        System.out.println("LIVE ANSWER:\n" + r.text());
        assertThat(r.status()).isEqualTo(AskOracleEngine.Status.ANSWERED);
        assertThat(r.text()).isNotBlank();
    }
}
