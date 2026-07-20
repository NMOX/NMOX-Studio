package org.nmox.studio.dbstudio.engine;

import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the export formats byte-for-byte: RFC 4180's quoting rules for
 * CSV (the rules spreadsheets actually parse by), and the JSON shape
 * with null and duplicate-column handling.
 */
class ResultExportsTest {

    private static QueryResult grid(List<String> names, List<List<String>> rows) {
        return new QueryResult(names, rows, rows.size(), -1, false, 0L, null, "SELECT ...");
    }

    // ---- CSV ----------------------------------------------------------

    @Test
    @DisplayName("Plain grid: header row plus data rows, CRLF endings throughout")
    void csvPlain() {
        QueryResult r = grid(List.of("id", "name"),
                List.of(List.of("1", "Ann"), List.of("2", "Bob")));

        assertThat(ResultExports.toCsv(r))
                .isEqualTo("id,name\r\n1,Ann\r\n2,Bob\r\n");
    }

    @Test
    @DisplayName("Formula-injection: a cell starting =+-@ is neutralized with a leading apostrophe")
    void csvFormulaInjectionNeutralized() {
        QueryResult r = grid(List.of("payload"),
                List.of(List.of("=cmd|'/c calc'!A1"),
                        List.of("+1+1"), List.of("-2"), List.of("@SUM(A1)"),
                        List.of("safe")));

        String csv = ResultExports.toCsv(r);
        // the risky cells are prefixed with ' and then quoted (the ' plus
        // any original quoting); the benign one is untouched
        assertThat(csv).contains("\"'=cmd|'/c calc'!A1\"");
        assertThat(csv).contains("\"'+1+1\"");
        assertThat(csv).contains("\"'-2\"");
        assertThat(csv).contains("\"'@SUM(A1)\"");
        assertThat(csv).contains("\r\nsafe\r\n");
    }

    @Test
    @DisplayName("Fields containing commas are double-quoted")
    void csvCommaQuoting() {
        QueryResult r = grid(List.of("name", "city"),
                List.of(List.of("Doe, Jane", "Basel")));

        assertThat(ResultExports.toCsv(r))
                .isEqualTo("name,city\r\n\"Doe, Jane\",Basel\r\n");
    }

    @Test
    @DisplayName("Embedded double quotes are doubled inside a quoted field")
    void csvQuoteDoubling() {
        QueryResult r = grid(List.of("quote"),
                List.of(List.of("He said \"hi\"")));

        assertThat(ResultExports.toCsv(r))
                .isEqualTo("quote\r\n\"He said \"\"hi\"\"\"\r\n");
    }

    @Test
    @DisplayName("Embedded newlines and carriage returns force quoting")
    void csvNewlineQuoting() {
        QueryResult r = grid(List.of("note"),
                List.of(List.of("line one\nline two"), List.of("mac\rstyle")));

        assertThat(ResultExports.toCsv(r))
                .isEqualTo("note\r\n\"line one\nline two\"\r\n\"mac\rstyle\"\r\n");
    }

    @Test
    @DisplayName("A quoted header cell follows the same rules as any field")
    void csvHeaderQuoting() {
        QueryResult r = grid(List.of("first, last"), List.of(List.of("x")));

        assertThat(ResultExports.toCsv(r)).startsWith("\"first, last\"\r\n");
    }

    @Test
    @DisplayName("A null cell exports as an empty field; short rows pad out")
    void csvNulls() {
        QueryResult r = grid(List.of("a", "b", "c"),
                List.of(Arrays.asList("1", null, "3"), Arrays.asList("only")));

        assertThat(ResultExports.toCsv(r))
                .isEqualTo("a,b,c\r\n1,,3\r\nonly,,\r\n");
    }

    @Test
    @DisplayName("An empty result exports as just the header row")
    void csvEmptyResult() {
        QueryResult r = grid(List.of("id", "name"), List.of());

        assertThat(ResultExports.toCsv(r)).isEqualTo("id,name\r\n");
    }

    // ---- JSON ---------------------------------------------------------

    @Test
    @DisplayName("Rows become objects keyed by column names; values are JSON strings")
    void jsonShape() {
        QueryResult r = grid(List.of("id", "name"),
                List.of(List.of("1", "Ann"), List.of("2", "Bob")));

        JSONArray parsed = new JSONArray(ResultExports.toJson(r));

        assertThat(parsed.length()).isEqualTo(2);
        assertThat(parsed.getJSONObject(0).getString("id")).isEqualTo("1");
        assertThat(parsed.getJSONObject(0).getString("name")).isEqualTo("Ann");
        assertThat(parsed.getJSONObject(1).getString("name")).isEqualTo("Bob");
        // values stay strings — the grid is stringly, the export says so
        assertThat(parsed.getJSONObject(0).get("id")).isInstanceOf(String.class);
    }

    @Test
    @DisplayName("A null cell becomes JSON null, not the string \"null\"")
    void jsonNulls() {
        QueryResult r = grid(List.of("a", "b"),
                List.of(Arrays.asList("x", null)));

        JSONArray parsed = new JSONArray(ResultExports.toJson(r));

        assertThat(parsed.getJSONObject(0).isNull("b")).isTrue();
        assertThat(parsed.getJSONObject(0).has("b")).as("the key is present").isTrue();
    }

    @Test
    @DisplayName("Duplicate column names get _2, _3 suffixes so no cell is dropped")
    void jsonDuplicateColumns() {
        QueryResult r = grid(List.of("id", "id", "name"),
                List.of(List.of("1", "7", "Ann")));

        JSONArray parsed = new JSONArray(ResultExports.toJson(r));
        JSONObject row = parsed.getJSONObject(0);

        assertThat(row.getString("id")).isEqualTo("1");
        assertThat(row.getString("id_2")).isEqualTo("7");
        assertThat(row.getString("name")).isEqualTo("Ann");
        assertThat(row.keySet()).hasSize(3);
    }

    @Test
    @DisplayName("Suffixing never collides with a real column of the suffixed name")
    void jsonSuffixCollision() {
        // columns literally named id, id, id_2 — the disambiguated keys stay unique
        assertThat(ResultExports.uniqueKeys(List.of("id", "id", "id_2")))
                .containsExactly("id", "id_2", "id_2_2");
        assertThat(ResultExports.uniqueKeys(List.of("a", "a", "a")))
                .containsExactly("a", "a_2", "a_3");
    }

    @Test
    @DisplayName("An empty result exports as an empty JSON array")
    void jsonEmptyResult() {
        QueryResult r = grid(List.of("id"), List.of());

        assertThat(new JSONArray(ResultExports.toJson(r)).length()).isZero();
    }

    @Test
    @DisplayName("Short rows pad with JSON null for the missing columns")
    void jsonShortRows() {
        QueryResult r = grid(List.of("a", "b"), List.of(List.of("only")));

        JSONArray parsed = new JSONArray(ResultExports.toJson(r));

        assertThat(parsed.getJSONObject(0).getString("a")).isEqualTo("only");
        assertThat(parsed.getJSONObject(0).isNull("b")).isTrue();
    }

    // ---- suggestedBaseName ----

    @Test
    @DisplayName("The export base name is the table of a simple single-table SELECT")
    void baseNameFromSimpleSelect() {
        assertThat(ResultExports.suggestedBaseName("SELECT * FROM users;"))
                .isEqualTo("users");
        assertThat(ResultExports.suggestedBaseName("select id from Orders limit 5"))
                .isEqualTo("Orders");
    }

    @Test
    @DisplayName("A qualified table keeps only its last segment")
    void baseNameDropsQualification() {
        assertThat(ResultExports.suggestedBaseName("SELECT * FROM public.users;"))
                .isEqualTo("users");
        assertThat(ResultExports.suggestedBaseName("SELECT * FROM `shop`.`order items`;"))
                .isEqualTo("order_items"); // hostile-for-filenames chars become _
    }

    @Test
    @DisplayName("Anything that isn't a single-table SELECT falls back to \"results\"")
    void baseNameFallsBack() {
        assertThat(ResultExports.suggestedBaseName(
                "SELECT * FROM a JOIN b ON a.id = b.id;")).isEqualTo("results");
        assertThat(ResultExports.suggestedBaseName("EXPLAIN SELECT * FROM users;"))
                .isEqualTo("results");
        assertThat(ResultExports.suggestedBaseName(null)).isEqualTo("results");
        assertThat(ResultExports.suggestedBaseName("")).isEqualTo("results");
    }
}
