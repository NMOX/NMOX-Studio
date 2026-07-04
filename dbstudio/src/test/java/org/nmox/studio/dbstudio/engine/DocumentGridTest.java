package org.nmox.studio.dbstudio.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.engine.DocumentGrid.Grid;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared document flattener: key-union columns with {@code _id}
 * first, honest cell rendering (NULL vs absent vs nested), the row cap
 * — for both input flavors (bson Documents from Mongo, JSONObjects
 * from Couch).
 */
class DocumentGridTest {

    @Test
    @DisplayName("columns are the union of top-level keys in first-appearance order")
    void columnUnionInFirstAppearanceOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("name", "ada");
        first.put("age", 36);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("name", "grace");
        second.put("rank", "admiral"); // new key appears after the first doc's keys

        Grid grid = DocumentGrid.fromMaps(List.of(first, second), 0);

        assertThat(grid.columnNames()).containsExactly("name", "age", "rank");
    }

    @Test
    @DisplayName("_id is forced to the front even when documents put it elsewhere")
    void idForcedFirst() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("title", "late id");
        doc.put("_id", "abc123");

        Grid grid = DocumentGrid.fromMaps(List.of(doc), 0);

        assertThat(grid.columnNames()).containsExactly("_id", "title");
        assertThat(grid.rows()).containsExactly(List.of("abc123", "late id"));
    }

    @Test
    @DisplayName("scalars stringify, explicit null renders NULL, an absent key renders empty")
    void nullVersusAbsent() {
        Map<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("a", 1);
        withNull.put("b", null);
        Map<String, Object> withoutB = new LinkedHashMap<>();
        withoutB.put("a", 2);

        Grid grid = DocumentGrid.fromMaps(List.of(withNull, withoutB), 0);

        assertThat(grid.columnNames()).containsExactly("a", "b");
        assertThat(grid.rows().get(0)).containsExactly("1", "NULL");
        assertThat(grid.rows().get(1)).as("absent is blank, not NULL").containsExactly("2", "");
    }

    @Test
    @DisplayName("nested objects and arrays render as compact JSON")
    void nestedCompactJson() {
        Document doc = Document.parse(
                "{\"addr\": {\"city\": \"Oslo\", \"zip\": 42}, \"tags\": [\"a\", 2, true]}");

        Grid grid = DocumentGrid.fromMaps(List.of(doc), 0);

        assertThat(grid.columnNames()).containsExactly("addr", "tags");
        assertThat(grid.rows().get(0).get(0)).isEqualTo("{\"city\":\"Oslo\",\"zip\":42}");
        assertThat(grid.rows().get(0).get(1)).isEqualTo("[\"a\",2,true]");
    }

    @Test
    @DisplayName("the row cap keeps rowLimit rows, flags truncated, and ignores dropped rows' keys")
    void rowCapAndTruncation() {
        Map<String, Object> a = Map.of("x", 1);
        Map<String, Object> b = Map.of("x", 2);
        Map<String, Object> dropped = Map.of("x", 3, "onlyInDropped", true);

        Grid grid = DocumentGrid.fromMaps(List.of(a, b, dropped), 2);

        assertThat(grid.rows()).hasSize(2);
        assertThat(grid.truncated()).isTrue();
        assertThat(grid.columnNames()).as("dropped doc's keys don't make columns")
                .containsExactly("x");

        Grid exact = DocumentGrid.fromMaps(List.of(a, b), 2);
        assertThat(exact.truncated()).as("exact fit is not truncated").isFalse();

        Grid unlimited = DocumentGrid.fromMaps(List.of(a, b, dropped), 0);
        assertThat(unlimited.rows()).hasSize(3);
        assertThat(unlimited.truncated()).isFalse();
    }

    @Test
    @DisplayName("the JSONObject flavor behaves identically, including JSONObject.NULL as NULL")
    void jsonObjectFlavor() {
        JSONObject first = new JSONObject("{\"_id\": \"d1\", \"note\": null}");
        JSONObject second = new JSONObject("{\"_id\": \"d2\", \"nested\": {\"k\": \"v\"}}");

        Grid grid = DocumentGrid.fromJsonObjects(List.of(first, second), 0);

        assertThat(grid.columnNames()).startsWith("_id");
        assertThat(grid.columnNames()).containsExactlyInAnyOrder("_id", "note", "nested");
        int note = grid.columnNames().indexOf("note");
        int nested = grid.columnNames().indexOf("nested");
        assertThat(grid.rows().get(0).get(0)).isEqualTo("d1");
        assertThat(grid.rows().get(0).get(note)).as("explicit JSON null").isEqualTo("NULL");
        assertThat(grid.rows().get(0).get(nested)).as("absent key").isEmpty();
        assertThat(grid.rows().get(1).get(nested)).isEqualTo("{\"k\":\"v\"}");
        assertThat(grid.rows().get(1).get(note)).isEmpty();
    }

    @Test
    @DisplayName("bson Documents flatten as maps: ObjectId-ish scalars via String.valueOf")
    void bsonDocumentFlavor() {
        Document doc = Document.parse(
                "{\"_id\": {\"$oid\": \"507f1f77bcf86cd799439011\"}, \"n\": 7}");

        Grid grid = DocumentGrid.fromMaps(List.of(doc), 0);

        assertThat(grid.columnNames()).containsExactly("_id", "n");
        assertThat(grid.rows().get(0).get(0)).isEqualTo("507f1f77bcf86cd799439011");
        assertThat(grid.rows().get(0).get(1)).isEqualTo("7");
    }

    @Test
    @DisplayName("empty and null inputs make an empty grid, never a throw")
    void degenerateInputs() {
        assertThat(DocumentGrid.fromMaps(List.of(), 10).columnNames()).isEmpty();
        assertThat(DocumentGrid.fromMaps(List.of(), 10).rows()).isEmpty();
        assertThat(DocumentGrid.fromMaps(null, 10).rows()).isEmpty();
        assertThat(DocumentGrid.fromJsonObjects(null, 10).rows()).isEmpty();
        assertThat(DocumentGrid.fromMaps(List.of(Map.of()), 10).rows())
                .as("an empty document is still a row").hasSize(1);
    }

    @Test
    @DisplayName("compactJson escapes strings and handles deep nesting")
    void compactJsonEscaping() {
        Map<String, Object> tricky = new LinkedHashMap<>();
        tricky.put("q", "say \"hi\"");
        tricky.put("deep", Map.of("inner", List.of(1, 2)));

        String json = DocumentGrid.compactJson(tricky);

        assertThat(json).contains("\"say \\\"hi\\\"\"");
        assertThat(json).contains("\"deep\":{\"inner\":[1,2]}");
        assertThat(DocumentGrid.compactJson(null)).isEqualTo("null");
    }
}
