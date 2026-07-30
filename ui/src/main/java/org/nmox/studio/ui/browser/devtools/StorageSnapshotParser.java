package org.nmox.studio.ui.browser.devtools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns the {@link DevScripts#STORAGE_SNAPSHOT} JSON into rows for the
 * Storage tab's read-only table (area / key / value). Never throws;
 * malformed input is an empty list; values are re-capped at 500 chars
 * on the Java side (the page could hand back anything).
 */
public final class StorageSnapshotParser {

    /** One storage row: which store, the key, the (capped) value. */
    public record Row(String area, String key, String value) {
    }

    private StorageSnapshotParser() {
    }

    /** Parses the snapshot into rows: localStorage, sessionStorage, cookies. */
    public static List<Row> parse(String json) {
        List<Row> out = new ArrayList<>();
        Object v = JsonLite.parse(json);
        if (!(v instanceof Map)) {
            return out;
        }
        Map<String, Object> o = JsonLite.asObject(v);
        addKeyed(out, "localStorage", o.get("l"));
        addKeyed(out, "sessionStorage", o.get("s"));
        for (Object c : JsonLite.asArray(o.get("c"))) {
            if (c instanceof String s && !s.isBlank()) {
                int eq = s.indexOf('=');
                String key = eq >= 0 ? s.substring(0, eq) : s;
                String val = eq >= 0 ? s.substring(eq + 1) : "";
                out.add(new Row("cookie", cap(key), cap(val)));
            }
            if (out.size() >= 2000) {
                break;
            }
        }
        return out;
    }

    private static void addKeyed(List<Row> out, String area, Object arr) {
        for (Object e : JsonLite.asArray(arr)) {
            if (e instanceof Map) {
                Map<String, Object> m = JsonLite.asObject(e);
                out.add(new Row(area,
                        cap(JsonLite.str(m, "k", "")),
                        cap(JsonLite.str(m, "v", ""))));
            }
            if (out.size() >= 2000) {
                return;
            }
        }
    }

    private static String cap(String s) {
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
