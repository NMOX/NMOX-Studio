package org.nmox.studio.apiclient.api;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel.Assertion;
import org.nmox.studio.apiclient.model.ApiModel.Request;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The public entry point: {@link TestRunner#run} turns a request's list
 * of assertions into one {@link TestRunner.Result} per line, in order,
 * so the Tests panel can paint green and red per assertion.
 */
class TestRunnerRunTest {

    private static ApiResponse response() {
        return new ApiResponse(200, 40, 12,
                Map.of("Content-Type", List.of("application/json")),
                "{\"data\":{\"id\":7}}", null);
    }

    @Test
    @DisplayName("run yields one result per assertion, in order, with mixed pass/fail")
    void runProducesOneResultPerAssertion() {
        Request r = new Request();
        r.tests.add(new Assertion(Assertion.Kind.STATUS_IS, "200"));       // pass
        r.tests.add(new Assertion(Assertion.Kind.STATUS_IS, "500"));       // fail
        r.tests.add(new Assertion(Assertion.Kind.BODY_CONTAINS, "data"));  // pass
        r.tests.add(new Assertion(Assertion.Kind.JSON_HAS_PATH, "data.id")); // pass
        r.tests.add(new Assertion(Assertion.Kind.HEADER_PRESENT, "x-none")); // fail

        List<TestRunner.Result> results = TestRunner.run(r, response());

        assertThat(results).hasSize(5);
        assertThat(results).extracting(TestRunner.Result::passed)
                .containsExactly(true, false, true, true, false);
        assertThat(results.get(0).description()).isEqualTo("Status is 200");
        assertThat(results.get(1).detail()).isEqualTo("was 200");
    }

    @Test
    @DisplayName("A request with no assertions runs to an empty result list")
    void noAssertionsYieldsEmpty() {
        assertThat(TestRunner.run(new Request(), response())).isEmpty();
    }

    @Test
    @DisplayName("A null target (hand-edited .nmoxapi.json) fails honestly, never NPEs")
    void nullTargetIsAFailedAssertionNotACrash() {
        Request r = new Request();
        for (Assertion.Kind kind : Assertion.Kind.values()) {
            r.tests.add(new Assertion(kind, null));
        }

        List<TestRunner.Result> results = TestRunner.run(r, response());

        assertThat(results).hasSize(Assertion.Kind.values().length);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.passed()).isFalse();
            assertThat(result.detail()).contains("no target");
        });
    }
}
