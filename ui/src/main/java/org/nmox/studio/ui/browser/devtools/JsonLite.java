package org.nmox.studio.ui.browser.devtools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny, hostile-input-safe JSON reader for the Browser DevTools
 * snapshots (ui module only — core deliberately does not export its
 * org.json copy across the module boundary, so parsing JSON here means
 * owning a reader; the snapshots are strings WE generate in injected
 * page JS via {@code JSON.stringify}, but the page can tamper with
 * anything, so this parser treats every input as untrusted).
 *
 * <p>Guarantees: {@link #parse} NEVER throws — malformed input returns
 * {@code null}; recursion is depth-capped at {@link #MAX_DEPTH} so a
 * 10k-deep nesting bomb is a {@code null}, not a StackOverflowError;
 * input longer than {@link #MAX_INPUT} chars is refused outright.
 *
 * <p>Value mapping: object → {@code Map<String,Object>} (insertion
 * order kept), array → {@code List<Object>}, string → {@code String},
 * number → {@code Double}, true/false → {@code Boolean}, null →
 * {@link #NULL} (a sentinel, so "absent" and "JSON null" stay
 * distinguishable from a failed parse).
 */
public final class JsonLite {

    /** Recursion ceiling: DOM snapshots need ~70, Vue ~60; 200 is generous. */
    public static final int MAX_DEPTH = 200;

    /** Input ceiling (chars): snapshots are capped upstream; 8 M is a backstop. */
    public static final int MAX_INPUT = 8_000_000;

    /** Sentinel for a JSON {@code null} value (parse failure returns Java null). */
    public static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };

    private final String s;
    private int i;

    private JsonLite(String s) {
        this.s = s;
    }

    /**
     * Parses {@code text}; returns the value tree, or {@code null} when
     * the text is not a single well-formed JSON value.
     */
    public static Object parse(String text) {
        if (text == null || text.length() > MAX_INPUT) {
            return null;
        }
        JsonLite p = new JsonLite(text);
        try {
            p.ws();
            Object v = p.value(0);
            p.ws();
            if (p.i != text.length()) {
                return null; // trailing garbage
            }
            return v;
        } catch (Bad bad) {
            return null;
        }
    }

    /** Convenience: the map when {@code v} is an object, else an empty map. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : Map.of();
    }

    /** Convenience: the list when {@code v} is an array, else an empty list. */
    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object v) {
        return v instanceof List ? (List<Object>) v : List.of();
    }

    /** Convenience: the member as a String, else {@code fallback}. */
    public static String str(Map<String, Object> o, String key, String fallback) {
        Object v = o.get(key);
        return v instanceof String s ? s : fallback;
    }

    /** Convenience: the member as an int, else {@code fallback}. */
    public static int num(Map<String, Object> o, String key, int fallback) {
        Object v = o.get(key);
        if (v instanceof Double d) {
            return (int) d.doubleValue();
        }
        return fallback;
    }

    private static final class Bad extends RuntimeException {
        Bad() {
            super(null, null, false, false);
        }
    }

    private static final Bad BAD = new Bad();

    private Object value(int depth) {
        if (depth > MAX_DEPTH || i >= s.length()) {
            throw BAD;
        }
        char c = s.charAt(i);
        switch (c) {
            case '{':
                return object(depth);
            case '[':
                return array(depth);
            case '"':
                return string();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return NULL;
            default:
                return number();
        }
    }

    private Map<String, Object> object(int depth) {
        i++; // {
        Map<String, Object> out = new LinkedHashMap<>();
        ws();
        if (peek() == '}') {
            i++;
            return out;
        }
        while (true) {
            ws();
            if (peek() != '"') {
                throw BAD;
            }
            String key = string();
            ws();
            if (peek() != ':') {
                throw BAD;
            }
            i++;
            ws();
            out.put(key, value(depth + 1));
            ws();
            char c = peek();
            if (c == ',') {
                i++;
            } else if (c == '}') {
                i++;
                return out;
            } else {
                throw BAD;
            }
        }
    }

    private List<Object> array(int depth) {
        i++; // [
        List<Object> out = new ArrayList<>();
        ws();
        if (peek() == ']') {
            i++;
            return out;
        }
        while (true) {
            ws();
            out.add(value(depth + 1));
            ws();
            char c = peek();
            if (c == ',') {
                i++;
            } else if (c == ']') {
                i++;
                return out;
            } else {
                throw BAD;
            }
        }
    }

    private String string() {
        i++; // opening quote
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= s.length()) {
                throw BAD;
            }
            char c = s.charAt(i++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (i >= s.length()) {
                    throw BAD;
                }
                char e = s.charAt(i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) {
                            throw BAD;
                        }
                        try {
                            sb.append((char) Integer.parseInt(s, i, i + 4, 16));
                        } catch (NumberFormatException nfe) {
                            throw BAD;
                        }
                        i += 4;
                        break;
                    default:
                        throw BAD;
                }
            } else if (c < 0x20) {
                throw BAD; // raw control char inside a string
            } else {
                sb.append(c);
            }
        }
    }

    private Double number() {
        int start = i;
        if (peek() == '-') {
            i++;
        }
        while (i < s.length() && "0123456789+-.eE".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        if (i == start) {
            throw BAD;
        }
        try {
            return Double.valueOf(s.substring(start, i));
        } catch (NumberFormatException nfe) {
            throw BAD;
        }
    }

    private void expect(String literal) {
        if (!s.startsWith(literal, i)) {
            throw BAD;
        }
        i += literal.length();
    }

    private char peek() {
        if (i >= s.length()) {
            throw BAD;
        }
        return s.charAt(i);
    }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                i++;
            } else {
                return;
            }
        }
    }
}
