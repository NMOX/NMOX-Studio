package org.nmox.studio.apiclient.api;

import java.util.List;
import java.util.Map;

/**
 * The outcome of firing a request: the raw material for the response
 * viewer and the test runner. {@code status < 0} means the request
 * never reached a server (DNS, connection refused, timeout).
 */
public record ApiResponse(int status, long millis, long bytes,
        Map<String, List<String>> headers, String body, String error,
        boolean truncated, int headStatus) {

    /**
     * A response with no head-before-break status: every construction but
     * {@link #bodyBroken}. {@code headStatus} is the status line that
     * arrived before the body broke off, or 0 — so {@code status < 0} still
     * means "no usable response" to every reader (tests, Save, Explain),
     * while the verdict can say the server DID answer (3.4: a 200 whose
     * body dropped mid-transfer read "No route — closed").
     */
    public ApiResponse(int status, long millis, long bytes,
            Map<String, List<String>> headers, String body, String error,
            boolean truncated) {
        this(status, millis, bytes, headers, body, error, truncated, 0);
    }

    /** Full body captured — the common case. */
    public ApiResponse(int status, long millis, long bytes,
            Map<String, List<String>> headers, String body, String error) {
        this(status, millis, bytes, headers, body, error, false);
    }

    public boolean reached() {
        return status >= 0;
    }

    public boolean ok() {
        return status >= 200 && status < 400;
    }

    public boolean hasHeader(String name) {
        if (headers == null) {
            return false;
        }
        for (String key : headers.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public static ApiResponse failure(long millis, String error) {
        return new ApiResponse(-1, millis, 0, Map.of(), "", error);
    }

    /**
     * The status line arrived, then the body broke off or stalled: not a
     * usable response (tests and Save must not treat a torn body as the
     * answer), but not "no route" either.
     */
    public static ApiResponse bodyBroken(long millis, int headStatus,
            Map<String, List<String>> headers, String error) {
        return new ApiResponse(-1, millis, 0, headers == null ? Map.of() : headers, "",
                error, false, headStatus);
    }

    /** True when a status arrived and then the body did not. */
    public boolean bodyBroke() {
        return status < 0 && headStatus > 0;
    }
}
