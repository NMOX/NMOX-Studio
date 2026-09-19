package org.nmox.studio.infra.api;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A hand-edited {@code .nmoxinfra.json} cannot throw out of a deploy planner.
 *
 * <p>These properties are a checked-in file's contents — the user keeps
 * {@code .nmoxinfra.json} in their repository — and this planner read them
 * with bare {@code Integer.parseInt} at eight sites and an unchecked
 * {@code rule[1]} at a ninth. So {@code "nodeCount": ""} threw a
 * {@code NumberFormatException} and {@code "forwardingRule": "https"} threw an
 * {@code ArrayIndexOutOfBoundsException}, both from inside the code that
 * builds a cloud deployment, and neither said anything a user could act on.
 *
 * <p>{@code NodeKind} had read the very same properties tolerantly all along,
 * for its cost estimate, with its own private copy of the fallback. The
 * defect was two readers of one fact disagreeing about what a bad value
 * means — and only one of them was on the path that spends money.
 */
class PlannerReadsPropertiesSafelyTest {

    @Test
    @DisplayName("a whole-number property that will not parse falls back to its declared default")
    void unparseableNumbersFallBack() {
        assertThat(DeployPlanner.intProp(Map.of("nodeCount", ""), "nodeCount", 3)).isEqualTo(3);
        assertThat(DeployPlanner.intProp(Map.of("nodeCount", "three"), "nodeCount", 3)).isEqualTo(3);
        assertThat(DeployPlanner.intProp(Map.of("nodeCount", "9e9"), "nodeCount", 3)).isEqualTo(3);
        assertThat(DeployPlanner.intProp(Map.of(), "nodeCount", 3)).isEqualTo(3);
    }

    @Test
    @DisplayName("a good value is still read, and surrounding whitespace does not defeat it")
    void goodNumbersAreRead() {
        assertThat(DeployPlanner.intProp(Map.of("nodeCount", "7"), "nodeCount", 3)).isEqualTo(7);
        assertThat(DeployPlanner.intProp(Map.of("nodeCount", " 7 "), "nodeCount", 3)).isEqualTo(7);
        assertThat(DeployPlanner.intProp(Map.of("nodeCount", "-1"), "nodeCount", 3))
                .as("negative is a value the API can refuse for itself; it is not a parse failure")
                .isEqualTo(-1);
    }

    @Test
    @DisplayName("a forwarding rule without a port falls back to http-80 instead of indexing past the end")
    void malformedForwardingRuleFallsBack() {
        assertThat(DeployPlanner.forwardingRule(Map.of("forwardingRule", "https")))
                .as("the exact value that used to throw ArrayIndexOutOfBounds")
                .containsExactly("http", "80");
        assertThat(DeployPlanner.forwardingRule(Map.of("forwardingRule", "https-")))
                .containsExactly("http", "80");
        assertThat(DeployPlanner.forwardingRule(Map.of("forwardingRule", "-443")))
                .containsExactly("http", "80");
        assertThat(DeployPlanner.forwardingRule(Map.of("forwardingRule", "https-port")))
                .containsExactly("http", "80");
        assertThat(DeployPlanner.forwardingRule(Map.of()))
                .containsExactly("http", "80");
    }

    @Test
    @DisplayName("a well-formed rule is read as written, including a protocol with its own hyphen")
    void goodForwardingRuleIsRead() {
        assertThat(DeployPlanner.forwardingRule(Map.of("forwardingRule", "https-443")))
                .containsExactly("https", "443");
        assertThat(DeployPlanner.forwardingRule(Map.of("forwardingRule", "http2-8080")))
                .containsExactly("http2", "8080");
    }
}
