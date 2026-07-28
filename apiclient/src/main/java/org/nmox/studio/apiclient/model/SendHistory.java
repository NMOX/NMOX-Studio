package org.nmox.studio.apiclient.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Send history (v1.197.0) — the DB Studio parity request: every send
 * leaves a row you can find ten minutes later, newest first, capped at
 * {@link #CAP}, persisted in .nmoxapi.json beside the collections.
 *
 * <p>The secrets law is STRUCTURAL here: an {@link Entry} stores the
 * AUTHORED request model — the {@code {{var}}} url exactly as typed,
 * the params/headers rows, the body — never the resolved values a send
 * produced, and there is deliberately NO token field on the type, so a
 * history entry cannot leak an auth secret into the committable
 * workspace file even by a future serialization bug. The auth TYPE is
 * kept (it is shape, not secret) so a restored request only needs its
 * token re-entered.
 */
public final class SendHistory {

    /** Same ceiling as DB Studio's query history. */
    public static final int CAP = 50;

    public static final class Entry {

        public long timestamp;
        public String name = "";
        public String method = "GET";
        /** The authored url, {@code {{vars}}} unresolved. */
        public String url = "";
        public final List<ApiModel.Pair> params = new ArrayList<>();
        public final List<ApiModel.Pair> headers = new ArrayList<>();
        public String body = "";
        /** Shape only; the token itself never enters history. */
        public ApiModel.AuthType authType = ApiModel.AuthType.NONE;
        /** HTTP status of the send; 0 = the send never reached a server. */
        public int status;
        public long durationMs;
    }

    private SendHistory() {
    }

    /** Snapshot of the authored request plus the outcome; token excluded by type. */
    public static Entry of(long timestamp, ApiModel.Request request, int status, long durationMs) {
        Entry e = new Entry();
        e.timestamp = timestamp;
        e.name = request.name;
        e.method = request.method;
        e.url = request.url;
        for (ApiModel.Pair p : request.params) {
            e.params.add(copy(p));
        }
        for (ApiModel.Pair p : request.headers) {
            e.headers.add(copy(p));
        }
        e.body = request.body;
        e.authType = request.authType;
        e.status = status;
        e.durationMs = durationMs;
        return e;
    }

    /** Newest first; the cap prunes from the tail. */
    public static void record(List<Entry> history, Entry entry) {
        history.add(0, entry);
        while (history.size() > CAP) {
            history.remove(history.size() - 1);
        }
    }

    /**
     * A history row back as a live request: fresh id (the id is the
     * keychain key — reusing one would cross-wire secrets, the v1.194.0
     * duplicate law), auth type kept, token deliberately absent — the
     * restored request says so via its auth panel.
     */
    public static ApiModel.Request restore(Entry e) {
        ApiModel.Request r = new ApiModel.Request();
        r.name = e.name == null || e.name.isBlank()
                ? e.method + " " + e.url : e.name;
        r.method = e.method;
        r.url = e.url;
        for (ApiModel.Pair p : e.params) {
            r.params.add(copy(p));
        }
        for (ApiModel.Pair p : e.headers) {
            r.headers.add(copy(p));
        }
        r.body = e.body;
        r.authType = e.authType;
        return r;
    }

    private static ApiModel.Pair copy(ApiModel.Pair p) {
        ApiModel.Pair c = new ApiModel.Pair(p.name, p.value);
        c.enabled = p.enabled;
        return c;
    }
}
