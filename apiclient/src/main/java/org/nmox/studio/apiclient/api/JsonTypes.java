package org.nmox.studio.apiclient.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * JSON → TypeScript interfaces (v2.33.0, granting the v2.31.0 recorded
 * wish): the response on screen becomes the types the client code
 * needs, one click. Pure — a JSON text in, a TypeScript declaration
 * block out — so every emission rule is a unit test.
 *
 * <p>The rules, stated: objects become interfaces named after their
 * key (PascalCased, the root named by the caller); arrays take their
 * ELEMENT type from the union of all elements' shapes (object elements
 * merge their keys, a key absent from some elements goes optional);
 * {@code null} values type as {@code | null} unions where a non-null
 * sibling exists, bare {@code unknown} otherwise; empty arrays are
 * {@code unknown[]} — a guess would be worse than the honest unknown.
 * Duplicate interface names from different shapes get numeric
 * suffixes; identical shapes reuse one interface.
 */
public final class JsonTypes {

    private JsonTypes() {
    }

    /**
     * The TypeScript declarations for {@code json}, root interface
     * named {@code rootName}. Returns null when the text is not a JSON
     * object or array of objects — the caller's cue to disable, not
     * throw.
     */
    public static String interfacesFor(String json, String rootName) {
        if (json == null) {
            return null;
        }
        String trimmed = json.strip();
        try {
            Emitter e = new Emitter();
            if (trimmed.startsWith("{")) {
                e.interfaceOf(new JSONObject(trimmed), pascal(rootName));
            } else if (trimmed.startsWith("[")) {
                JSONArray arr = new JSONArray(trimmed);
                String elem = e.typeOfArray(arr, pascal(rootName));
                if (e.out.isEmpty()) {
                    return null;        // an array of primitives has no shape
                }
                e.header = "type " + pascal(rootName) + "s = " + elem + "[];\n\n";
            } else {
                return null;
            }
            return e.render();
        } catch (org.json.JSONException notJson) {
            return null;
        }
    }

    private static final class Emitter {

        final Map<String, String> out = new LinkedHashMap<>();  // name -> body
        final Map<String, String> byShape = new LinkedHashMap<>(); // shape -> name
        String header = "";

        String render() {
            StringBuilder sb = new StringBuilder(header);
            List<String> names = new ArrayList<>(out.keySet());
            for (int i = names.size() - 1; i >= 0; i--) {
                sb.append(out.get(names.get(i)));
                if (i > 0) {
                    sb.append('\n');
                }
            }
            return sb.toString();
        }

        /** Emits (or reuses) the interface for one object; returns its name. */
        String interfaceOf(JSONObject o, String wantedName) {
            String shape = shapeOf(o);
            String existing = byShape.get(shape);
            if (existing != null) {
                return existing;
            }
            String name = wantedName;
            for (int n = 2; out.containsKey(name); n++) {
                name = wantedName + n;
            }
            byShape.put(shape, name);
            out.put(name, "");          // reserve before recursing (cycles)
            StringBuilder b = new StringBuilder("interface " + name + " {\n");
            // SORTED: org.json's key order is undefined, and a codec
            // whose output shuffles between runs cannot be test-pinned
            for (String key : new java.util.TreeSet<>(o.keySet())) {
                b.append("  ").append(propName(key)).append(": ")
                        .append(typeOf(o.get(key), key)).append(";\n");
            }
            b.append("}\n");
            out.put(name, b.toString());
            return name;
        }

        String typeOf(Object v, String key) {
            if (v == null || v == JSONObject.NULL) {
                return "unknown";
            }
            if (v instanceof JSONObject o) {
                return interfaceOf(o, pascal(key));
            }
            if (v instanceof JSONArray a) {
                return typeOfArray(a, pascal(singular(key))) + "[]";
            }
            if (v instanceof String) {
                return "string";
            }
            if (v instanceof Boolean) {
                return "boolean";
            }
            return "number";
        }

        /** The element type of an array: the union of its members' shapes. */
        String typeOfArray(JSONArray a, String elemName) {
            if (a.isEmpty()) {
                return "unknown";
            }
            // object elements merge into ONE interface: union of keys,
            // keys absent somewhere go optional
            List<JSONObject> objects = new ArrayList<>();
            Set<String> primitives = new LinkedHashSet<>();
            for (int i = 0; i < a.length(); i++) {
                Object v = a.get(i);
                if (v instanceof JSONObject o) {
                    objects.add(o);
                } else {
                    primitives.add(typeOf(v, elemName));
                }
            }
            if (!objects.isEmpty()) {
                primitives.add(mergedInterface(objects, elemName));
            }
            return String.join(" | ", primitives);
        }

        private String mergedInterface(List<JSONObject> objects, String name) {
            if (objects.size() == 1) {
                return interfaceOf(objects.get(0), name);
            }
            Set<String> allKeys = new java.util.TreeSet<>();
            for (JSONObject o : objects) {
                allKeys.addAll(o.keySet());
            }
            JSONObject merged = new JSONObject();
            Set<String> optional = new LinkedHashSet<>();
            Set<String> nullable = new LinkedHashSet<>();
            for (String key : allKeys) {
                Object sample = JSONObject.NULL;
                for (JSONObject o : objects) {
                    if (o.has(key)) {
                        Object v = o.get(key);
                        if (v == JSONObject.NULL) {
                            // the null sibling must SURVIVE into the type —
                            // the v2.33.1 review found it silently dropped,
                            // emitting `string` for data that holds nulls
                            nullable.add(key);
                        } else if (sample == JSONObject.NULL) {
                            sample = v;
                        }
                    } else {
                        optional.add(key);
                    }
                }
                merged.put(key, sample);
            }
            String shape = shapeOf(merged) + "?" + optional + "~" + nullable;
            String existing = byShape.get(shape);
            if (existing != null) {
                return existing;
            }
            String n = name;
            for (int k = 2; out.containsKey(n); k++) {
                n = name + k;
            }
            byShape.put(shape, n);
            out.put(n, "");
            StringBuilder b = new StringBuilder("interface " + n + " {\n");
            for (String key : allKeys) {
                String type = typeOf(merged.get(key), key);
                if (nullable.contains(key) && !"unknown".equals(type)) {
                    type += " | null";
                }
                b.append("  ").append(propName(key))
                        .append(optional.contains(key) ? "?" : "").append(": ")
                        .append(type).append(";\n");
            }
            b.append("}\n");
            out.put(n, b.toString());
            return n;
        }

        private String shapeOf(JSONObject o) {
            StringBuilder b = new StringBuilder("{");
            for (String key : new java.util.TreeSet<>(o.keySet())) {
                Object v = o.get(key);
                b.append(key).append(':')
                        .append(v instanceof JSONObject nested ? shapeOf(nested)
                                : v instanceof JSONArray ? "[]"
                                : v == JSONObject.NULL ? "null"
                                : v.getClass().getSimpleName())
                        .append(',');
            }
            return b.append('}').toString();
        }
    }

    /** PascalCase for interface names: user_id / user-id / userId → UserId. */
    static String pascal(String key) {
        StringBuilder b = new StringBuilder();
        boolean up = true;
        for (char c : key.toCharArray()) {
            if (c == '_' || c == '-' || c == ' ') {
                up = true;
            } else {
                b.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return b.length() == 0 ? "Root" : b.toString();
    }

    /** users → User, addresses → Address; naive but honest. */
    static String singular(String key) {
        if (key.endsWith("ies") && key.length() > 3) {
            return key.substring(0, key.length() - 3) + "y";
        }
        if (key.endsWith("s") && key.length() > 1 && !key.endsWith("ss")) {
            return key.substring(0, key.length() - 1);
        }
        return key;
    }

    /** A property name, quoted only when TS requires it. */
    static String propName(String key) {
        if (key.isEmpty() || !Character.isJavaIdentifierStart(key.charAt(0))) {
            return quoted(key);
        }
        for (char c : key.toCharArray()) {
            if (!Character.isJavaIdentifierPart(c)) {
                return quoted(key);
            }
        }
        return key;
    }

    /**
     * A TS string-literal property name. Backslash and the quote itself
     * escape — the hostile-input probe (v2.36.5) fed a key containing a
     * double-quote and the emitted interface was SYNTACTICALLY BROKEN
     * TypeScript: the literal terminated mid-name. A generated type
     * must never be invalid source, whatever the JSON held.
     */
    private static String quoted(String key) {
        return "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
