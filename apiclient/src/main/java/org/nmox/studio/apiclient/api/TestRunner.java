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
        return switch (a.kind) {
            case STATUS_IS -> {
                boolean ok = String.valueOf(r.status()).equals(a.target.trim());
                yield new Result("Status is " + a.target, ok, "was " + r.status());
            }
            case TIME_UNDER_MS -> {
                long limit = parseLong(a.target, Long.MAX_VALUE);
                boolean ok = r.millis() < limit;
                yield new Result("Time under " + a.target + "ms", ok, r.millis() + "ms");
            }
            case BODY_CONTAINS -> {
                boolean ok = r.body() != null && r.body().contains(a.target);
                yield new Result("Body contains \"" + a.target + "\"", ok,
                        ok ? "found" : "not found");
            }
            case JSON_HAS_PATH -> {
                boolean ok = jsonHasPath(r.body(), a.target);
                yield new Result("JSON has " + a.target, ok, ok ? "present" : "missing");
            }
            case HEADER_PRESENT -> {
                boolean ok = r.hasHeader(a.target.trim());
                yield new Result("Header " + a.target + " present", ok,
                        ok ? "present" : "missing");
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
