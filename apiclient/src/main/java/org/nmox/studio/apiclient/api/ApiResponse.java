package org.nmox.studio.apiclient.api;

import java.util.List;
import java.util.Map;

/**
 * The outcome of firing a request: the raw material for the response
 * viewer and the test runner. {@code status < 0} means the request
 * never reached a server (DNS, connection refused, timeout).
 */
public record ApiResponse(int status, long millis, long bytes,
        Map<String, List<String>> headers, String body, String error) {

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
}
