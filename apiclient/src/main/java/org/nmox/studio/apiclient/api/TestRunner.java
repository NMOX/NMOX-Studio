package org.nmox.studio.apiclient.api;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.apiclient.model.ApiModel.Assertion;
import org.nmox.studio.apiclient.model.ApiModel.Request;

/**
 * Judges a response against a request's assertions - the "Tests" tab
 * that turns a probe into a check. Every assertion yields one
 * {@link Result} so the panel can show green and red per line.
 */
@org.openide.util.NbBundle.Messages({
    "TestRunner_noTarget={0} (no target)",
    "TestRunner_noTargetDetail=assertion has no target — fix it in the Tests tab or .nmoxapi.json",
    "TestRunner_statusIs=Status is {0}",
    "TestRunner_was=was {0,number,0}",
    "TestRunner_timeUnder=Time under {0}ms",
    "TestRunner_millis={0,number,0}ms",
    "TestRunner_bodyContains=Body contains \"{0}\"",
    "TestRunner_found=found",
    "TestRunner_notFound=not found",
    "TestRunner_jsonHas=JSON has {0}",
    "TestRunner_present=present",
    "TestRunner_missing=missing",
    "TestRunner_headerPresent=Header {0} present"
})
public final class TestRunner {

    private TestRunner() {
    }

    public record Result(String description, boolean passed, String detail) {
    }

    public static List<Result> run(Request request, ApiResponse response) {
        List<Result> results = new ArrayList<>();
        for (Assertion a : request.tests) {
            results.add(evaluate(a, response));
        }
        return results;
    }

    static Result evaluate(Assertion a, ApiResponse r) {
        if (a.target == null) {
            // a hand-edited .nmoxapi.json can carry "target": null — that's a
            // failed assertion with an honest message, never an NPE that kills
            // the send worker and leaves the Send button dead
            return new Result(Bundle.TestRunner_noTarget(a.kind), false,
                    Bundle.TestRunner_noTargetDetail());
        }
        return switch (a.kind) {
            case STATUS_IS -> {
                boolean ok = String.valueOf(r.status()).equals(a.target.trim());
                yield new Result(Bundle.TestRunner_statusIs(a.target), ok, Bundle.TestRunner_was(r.status()));
            }
            case TIME_UNDER_MS -> {
                long limit = parseLong(a.target, Long.MAX_VALUE);
                boolean ok = r.millis() < limit;
                yield new Result(Bundle.TestRunner_timeUnder(a.target), ok, Bundle.TestRunner_millis(r.millis()));
            }
            case BODY_CONTAINS -> {
                boolean ok = r.body() != null && r.body().contains(a.target);
                yield new Result(Bundle.TestRunner_bodyContains(a.target), ok,
                        ok ? Bundle.TestRunner_found() : Bundle.TestRunner_notFound());
            }
            case JSON_HAS_PATH -> {
                boolean ok = jsonHasPath(r.body(), a.target);
                yield new Result(Bundle.TestRunner_jsonHas(a.target), ok, ok ? Bundle.TestRunner_present() : Bundle.TestRunner_missing());
            }
            case HEADER_PRESENT -> {
                boolean ok = r.hasHeader(a.target.trim());
                yield new Result(Bundle.TestRunner_headerPresent(a.target), ok,
                        ok ? Bundle.TestRunner_present() : Bundle.TestRunner_missing());
            }
        };
    }

    /**
     * Dotted-path presence check: {@code data.user.id}, with
     * {@code items.0} indexing arrays. Presence only - enough for the
     * common "did the field come back" assertion without a query DSL.
     */
    static boolean jsonHasPath(String body, String path) {
        if (body == null || path == null || path.isBlank()) {
            return false;
        }
        try {
            Object current = body.strip().startsWith("[")
                    ? new JSONArray(body) : new JSONObject(body);
            for (String segment : path.split("\\.")) {
                if (current instanceof JSONObject obj && obj.has(segment)) {
                    current = obj.get(segment);
                } else if (current instanceof JSONArray arr && isInt(segment)
                        && Integer.parseInt(segment) < arr.length()) {
                    current = arr.get(Integer.parseInt(segment));
                } else {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException notJson) {
            return false;
        }
    }

    private static boolean isInt(String s) {
        return s.chars().allMatch(Character::isDigit) && !s.isEmpty();
    }

    private static long parseLong(String s, long fallback) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
