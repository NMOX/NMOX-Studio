package org.nmox.studio.dbstudio.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Turns a batch of schemaless documents into the rectangular grid a
 * {@link QueryResult} wants — the shared flattener behind both
 * document backends ({@code MongoBackend} feeds
 * {@code org.bson.Document}, which is a {@code Map}; {@code
 * CouchBackend} feeds {@code org.json.JSONObject}).
 *
 * <p>Rules, chosen to read honestly in a grid of heterogeneous
 * documents:
 * <ul>
 *   <li><b>columns</b> — the union of the kept documents' top-level
 *       keys in first-appearance order, with {@code "_id"} forced to
 *       the front when any document has one (both stores treat it as
 *       the primary key);</li>
 *   <li><b>scalar</b> values — {@code String.valueOf} (an ObjectId or
 *       date renders as its natural string);</li>
 *   <li><b>null</b> values — the string {@code "NULL"}, matching the
 *       JDBC path's convention;</li>
 *   <li><b>nested</b> objects and arrays — compact JSON;</li>
 *   <li><b>absent</b> keys — {@code ""}, visually distinct from an
 *       explicit null.</li>
 * </ul>
 *
 * <p>The row cap works like the JDBC one: at most {@code rowLimit}
 * rows are kept ({@code <= 0} means unlimited) and {@link
 * Grid#truncated()} says whether input documents were dropped —
 * columns are computed from the KEPT rows only, so a dropped
 * document's keys never produce an all-empty column.
 *
 * <p>Pure and static: no I/O, no drivers, exhaustively testable.
 */
public final class DocumentGrid {

    /** The flattened batch: column names, stringified rows, honest cap flag. */
    public record Grid(List<String> columnNames, List<List<String>> rows, boolean truncated) {

        public Grid {
            columnNames = List.copyOf(columnNames);
            rows = List.copyOf(rows);
        }
    }

    private static final String ID = "_id";

    private DocumentGrid() {
    }

    /**
     * Flattens map-shaped documents ({@code org.bson.Document}
     * implements {@code Map} with insertion order preserved).
     */
    public static Grid fromMaps(List<? extends Map<String, ?>> docs, int rowLimit) {
        List<Map<String, ?>> kept = new ArrayList<>();
        boolean truncated = false;
        for (Map<String, ?> doc : docs == null ? List.<Map<String, ?>>of() : docs) {
            if (rowLimit > 0 && kept.size() >= rowLimit) {
                truncated = true;
                break;
            }
            kept.add(doc == null ? Map.of() : doc);
        }
        return build(kept, truncated);
    }

    /**
     * Flattens {@code org.json.JSONObject} documents. {@code
     * JSONObject.NULL} is treated as a null value (renders as
     * {@code "NULL"}), matching what an explicit {@code null} in the
     * JSON meant.
     */
    public static Grid fromJsonObjects(List<JSONObject> docs, int rowLimit) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (JSONObject doc : docs == null ? List.<JSONObject>of() : docs) {
            maps.add(doc == null ? Map.of() : toMapView(doc));
        }
        return fromMaps(maps, rowLimit);
    }

    // ---- internals ------------------------------------------------

    private static Grid build(List<Map<String, ?>> kept, boolean truncated) {
        Set<String> columns = new LinkedHashSet<>();
        boolean hasId = false;
        for (Map<String, ?> doc : kept) {
            for (String key : doc.keySet()) {
                if (ID.equals(key)) {
                    hasId = true;
                } else {
                    columns.add(key);
                }
            }
        }
        List<String> names = new ArrayList<>();
        if (hasId) {
            names.add(ID);
        }
        names.addAll(columns);

        List<List<String>> rows = new ArrayList<>(kept.size());
        for (Map<String, ?> doc : kept) {
            List<String> row = new ArrayList<>(names.size());
            for (String name : names) {
                row.add(cell(doc, name));
            }
            rows.add(row);
        }
        return new Grid(names, rows, truncated);
    }

    private static String cell(Map<String, ?> doc, String key) {
        if (!doc.containsKey(key)) {
            return "";
        }
        Object value = doc.get(key);
        if (value == null || value == JSONObject.NULL) {
            return "NULL";
        }
        if (isNested(value)) {
            return compactJson(value);
        }
        return String.valueOf(value);
    }

    private static boolean isNested(Object value) {
        return value instanceof Map || value instanceof Collection
                || value instanceof JSONObject || value instanceof JSONArray
                || value instanceof Object[];
    }

    /**
     * Renders one nested value as compact JSON, preserving the source
     * ordering (bson Documents iterate in insertion order; org.json
     * types render themselves).
     */
    static String compactJson(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "null";
        }
        if (value instanceof JSONObject || value instanceof JSONArray) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(JSONObject.quote(String.valueOf(entry.getKey())))
                        .append(':').append(compactJson(entry.getValue()));
            }
            return sb.append('}').toString();
        }
        if (value instanceof Collection<?> collection) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object element : collection) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(compactJson(element));
            }
            return sb.append(']').toString();
        }
        if (value instanceof Object[] array) {
            return compactJson(List.of(array));
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        // strings — and driver types like ObjectId — as JSON strings
        return JSONObject.quote(String.valueOf(value));
    }

    private static Map<String, Object> toMapView(JSONObject doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : doc.keySet()) {
            map.put(key, doc.get(key));
        }
        return map;
    }
}
