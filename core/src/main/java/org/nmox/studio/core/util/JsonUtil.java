package org.nmox.studio.core.util;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * JSON glances shared by every console that shows response bodies.
 * This class deliberately exposes only String signatures: each NBM
 * module wraps its own org.json copy, so org.json types must never
 * cross a module boundary.
 */
public final class JsonUtil {

    private JsonUtil() {
    }

    /** A cheap sniff: does this body look like a JSON object or array? */
    public static boolean looksJson(String body) {
        if (body == null) {
            return false;
        }
        String t = body.strip();
        return t.startsWith("{") || t.startsWith("[");
    }

    /** Indents a JSON body for display; anything unparseable passes through raw. */
    public static String pretty(String body) {
        if (body == null) {
            return "";
        }
        String t = body.strip();
        try {
            if (t.startsWith("{")) {
                return new JSONObject(t).toString(2);
            }
            if (t.startsWith("[")) {
                return new JSONArray(t).toString(2);
            }
        } catch (RuntimeException notJson) {
            // not valid JSON; show it raw
        }
        return body;
    }
}
