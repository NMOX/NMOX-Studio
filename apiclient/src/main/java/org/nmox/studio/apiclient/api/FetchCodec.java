package org.nmox.studio.apiclient.api;

import java.util.Map;

import org.nmox.studio.apiclient.model.ApiModel;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Pair;

/**
 * Copy as fetch (v2.31.0, the full-stack wishlist): {@link CurlCodec}
 * speaks to the terminal; this codec speaks to the CODE the request
 * will live in — a {@code fetch()} call with the same URL, headers,
 * auth, and body {@code send()} would use, {@code {{vars}}} resolved.
 *
 * <p>Same auth stance as the curl side: the Authorization value lands
 * INLINE because the developer asked for a runnable snippet — the
 * consent is the click — but the string placed on the clipboard is
 * theirs to guard, exactly like Copy curl's.
 */
public final class FetchCodec {

    private FetchCodec() {
    }

    /** The fetch() call a client file would make for this request. */
    public static String render(ApiModel.Request r, Map<String, String> vars) {
        StringBuilder sb = new StringBuilder("const response = await fetch(");
        sb.append(js(ApiClient.appendParams(
                Variables.resolve(r.url, vars), r.params, vars)));
        boolean bodied = r.body != null && !r.body.isBlank();
        boolean hasHeaders = r.headers.stream()
                .anyMatch(h -> h.enabled && h.name != null && !h.name.isBlank())
                || (r.authType == AuthType.BEARER && !r.authToken.isBlank())
                || (r.authType == AuthType.BASIC && !r.authToken.isBlank());
        boolean plainGet = "GET".equals(r.method) && !bodied && !hasHeaders;
        if (plainGet) {
            return sb.append(");\n").toString();
        }
        sb.append(", {\n");
        if (!"GET".equals(r.method) || bodied) {
            sb.append("  method: ").append(js(r.method)).append(",\n");
        }
        if (hasHeaders) {
            sb.append("  headers: {\n");
            for (Pair h : r.headers) {
                if (h.enabled && h.name != null && !h.name.isBlank()) {
                    sb.append("    ").append(js(h.name)).append(": ")
                            .append(js(Variables.resolve(
                                    h.value == null ? "" : h.value, vars)))
                            .append(",\n");
                }
            }
            if (r.authType == AuthType.BEARER && !r.authToken.isBlank()) {
                sb.append("    ").append(js("Authorization")).append(": ")
                        .append(js("Bearer "
                                + Variables.resolve(r.authToken, vars).trim()))
                        .append(",\n");
            } else if (r.authType == AuthType.BASIC && !r.authToken.isBlank()) {
                sb.append("    ").append(js("Authorization")).append(": ")
                        .append("\"Basic \" + btoa(")
                        .append(js(Variables.resolve(r.authToken, vars)))
                        .append("),\n");
            }
            sb.append("  },\n");
        }
        if (bodied) {
            sb.append("  body: ").append(js(Variables.resolve(r.body, vars)))
                    .append(",\n");
        }
        return sb.append("});\n").toString();
    }

    /** A JS double-quoted string literal, escapes complete. */
    static String js(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
