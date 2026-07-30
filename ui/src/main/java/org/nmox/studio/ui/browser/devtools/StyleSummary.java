package org.nmox.studio.ui.browser.devtools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The curated computed-style vocabulary for the DevTools DOM tab.
 * getComputedStyle answers ~300 properties; showing them all buries
 * the useful ones, so the details pane asks for exactly these fifteen
 * (display/position, the box metrics, type, and color) — the ones a
 * web developer reaches for first when a layout looks wrong.
 */
public final class StyleSummary {

    /** Exactly the 15 computed-style properties the DOM details pane shows. */
    public static final List<String> KEYS = List.of(
            "display",
            "position",
            "width",
            "height",
            "margin",
            "padding",
            "border",
            "box-sizing",
            "font-family",
            "font-size",
            "font-weight",
            "line-height",
            "color",
            "background-color",
            "z-index");

    private StyleSummary() {
    }

    /**
     * Parses the computed-style snapshot JSON ({@code {"display":"block",
     * ...}}) into an ordered key→value map holding only curated keys —
     * hostile or malformed input yields an empty map, never a throw.
     */
    public static Map<String, String> parse(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        Object v = JsonLite.parse(json);
        Map<String, Object> o = JsonLite.asObject(v);
        for (String key : KEYS) {
            Object val = o.get(key);
            if (val instanceof String s) {
                out.put(key, s.length() > 200 ? s.substring(0, 200) : s);
            }
        }
        return out;
    }
}
