package org.nmox.studio.ui.browser.devtools;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * The DevTools Network tab's data: a bounded ring of request rows fed
 * by the injected fetch/XMLHttpRequest wrappers (the page calls
 * {@code nmoxBridge.net(json)} with one small JSON object per finished
 * request). Pure model, EDT-confined in production, same hostile-page
 * laws as {@link ConsoleModel}: JSON from the page is untrusted — it
 * is parsed with {@link JsonLite} (never throws), URLs are capped at
 * {@link #URL_CAP} chars, the ring holds {@link #CAP} rows with the
 * oldest dropped and counted.
 *
 * <p>Honest limits (v1): only requests made AFTER the DevTools
 * injection are visible (the wrappers install on page-load success),
 * and response bodies are deliberately not captured — method, URL,
 * status, duration, and size-when-knowable only.
 */
public final class NetworkModel {

    /** Maximum rows kept; oldest dropped (and counted) past this. */
    public static final int CAP = 500;

    /** Maximum chars of URL stored per row. */
    public static final int URL_CAP = 500;

    /** One network row. size &lt; 0 means "unknown". */
    public record Entry(String method, String url, int status, boolean ok,
            long durationMillis, long sizeBytes) {
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    private long dropped;
    private Runnable listener;

    /**
     * Parses one bridge payload ({@code {"m":..,"u":..,"s":..,"ok":..,
     * "d":..,"z":..}}) into a row, or null when the JSON is not shaped
     * like one (hostile or garbled input is a no-row, never a throw).
     */
    public static Entry fromJson(String json) {
        Object v = JsonLite.parse(json);
        if (!(v instanceof Map)) {
            return null;
        }
        Map<String, Object> o = JsonLite.asObject(v);
        String method = JsonLite.str(o, "m", "GET");
        String url = JsonLite.str(o, "u", "");
        if (url.length() > URL_CAP) {
            url = url.substring(0, URL_CAP);
        }
        if (method.length() > 20) {
            method = method.substring(0, 20);
        }
        int status = JsonLite.num(o, "s", 0);
        boolean ok = Boolean.TRUE.equals(o.get("ok"));
        long duration = JsonLite.num(o, "d", 0);
        long size = JsonLite.num(o, "z", -1);
        return new Entry(method, url, status, ok, duration, size);
    }

    /** Adds a row parsed from bridge JSON; garbage adds nothing. */
    public void addFromJson(String json) {
        Entry e = fromJson(json);
        if (e == null) {
            return;
        }
        entries.addLast(e);
        while (entries.size() > CAP) {
            entries.removeFirst();
            dropped++;
        }
        fire();
    }

    /** Snapshot of the current rows, oldest first. */
    public List<Entry> entries() {
        return new ArrayList<>(entries);
    }

    /** How many older rows were evicted since the last clear. */
    public long droppedCount() {
        return dropped;
    }

    /** Empties the ring and resets the dropped counter. */
    public void clear() {
        entries.clear();
        dropped = 0;
        fire();
    }

    /** The one change listener (the rendering panel). */
    public void setListener(Runnable r) {
        this.listener = r;
    }

    private void fire() {
        if (listener != null) {
            listener.run();
        }
    }
}
