package org.nmox.studio.dbstudio.engine;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The in-grid edit builder is the one class that writes SQL the user
 * never sees before it runs — every rendering rule and every refusal
 * is pinned here. A wrong WHERE clause is data loss; the refusal cases
 * matter as much as the happy paths.
 */
class UpdateBuilderTest {

    private static final TableInfo USERS = new TableInfo("", "", "users", "TABLE");

    private static List<ColumnInfo> usersColumns() {
        return List.of(
                new ColumnInfo("id", "INTEGER", 10, false, true),
                new ColumnInfo("name", "VARCHAR", 255, true, false),
                new ColumnInfo("age", "INT", 10, true, false),
                new ColumnInfo("active", "BOOLEAN", 1, true, false),
                new ColumnInfo("bio", "TEXT", 65535, true, false));
    }

    private static final List<String> GRID = List.of("id", "name", "age", "active", "bio");

    private static List<String> row(String... cells) {
        return Arrays.asList(cells);
    }

    // ---- the happy path, both quoting families -----------------------

    @Test
    @DisplayName("MySQL edit: backtick identifiers, string quoted, PK bare in WHERE")
    void mysqlSingleEdit() {
        String sql = UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(), GRID,
                row("7", "Ann", "30", "1", "hi"), Map.of(1, "Bob"));

        assertThat(sql).isEqualTo("UPDATE `users` SET `name` = 'Bob' WHERE `id` = 7;");
    }

    @Test
    @DisplayName("MariaDB rides the same backtick dialect as MySQL")
    void mariadbMatchesMysql() {
        String sql = UpdateBuilder.update(DbEngine.MARIADB, USERS, usersColumns(), GRID,
                row("7", "Ann", "30", "1", "hi"), Map.of(1, "Bob"));

        assertThat(sql).isEqualTo("UPDATE `users` SET `name` = 'Bob' WHERE `id` = 7;");
    }

    @Test
    @DisplayName("PostgreSQL and SQLite quote with the SQL-standard double quote")
    void doubleQuoteFamily() {
        String postgres = UpdateBuilder.update(DbEngine.POSTGRES, USERS, usersColumns(), GRID,
                row("7", "Ann", "30", "1", "hi"), Map.of(1, "Bob"));
        String sqlite = UpdateBuilder.update(DbEngine.SQLITE, USERS, usersColumns(), GRID,
                row("7", "Ann", "30", "1", "hi"), Map.of(1, "Bob"));

        assertThat(postgres)
                .isEqualTo("UPDATE \"users\" SET \"name\" = 'Bob' WHERE \"id\" = 7;");
        assertThat(sqlite).isEqualTo(postgres);
    }

    @Test
    @DisplayName("A schema-qualified table quotes both parts")
    void schemaQualifiedTable() {
        TableInfo shopUsers = new TableInfo("", "shop", "users", "TABLE");

        String sql = UpdateBuilder.update(DbEngine.POSTGRES, shopUsers, usersColumns(), GRID,
                row("7", "Ann", "30", "1", "hi"), Map.of(1, "Bob"));

        assertThat(sql).startsWith("UPDATE \"shop\".\"users\" SET ");
    }

    @Test
    @DisplayName("Composite PK: WHERE carries EVERY key column, original values, AND-joined")
    void compositePrimaryKey() {
        TableInfo items = new TableInfo("", "", "order_items", "TABLE");
        List<ColumnInfo> columns = List.of(
                new ColumnInfo("order_id", "INT", 10, false, true),
                new ColumnInfo("line_no", "INT", 10, false, true),
                new ColumnInfo("qty", "INT", 10, true, false));

        String sql = UpdateBuilder.update(DbEngine.MYSQL, items, columns,
                List.of("order_id", "line_no", "qty"), row("5", "2", "1"), Map.of(2, "3"));

        assertThat(sql).isEqualTo("UPDATE `order_items` SET `qty` = 3"
                + " WHERE `order_id` = 5 AND `line_no` = 2;");
    }

    @Test
    @DisplayName("Multiple edits render in grid order regardless of map iteration order")
    void multipleEditsInGridOrder() {
        Map<Integer, String> edits = new HashMap<>();
        edits.put(4, "new bio");
        edits.put(1, "Bob");

        String sql = UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(), GRID,
                row("7", "Ann", "30", "1", "hi"), edits);

        assertThat(sql).isEqualTo("UPDATE `users` SET `name` = 'Bob', `bio` = 'new bio'"
                + " WHERE `id` = 7;");
    }

    @Test
    @DisplayName("WHERE uses the ORIGINAL row values — never the edited ones")
    void whereUsesOriginalValues() {
        // the PK is not edited here, but the edited cell's original ("Ann")
        // must appear nowhere: only the new value and the original key
        String sql = UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(), GRID,
                row("7", "Ann", "30", "1", "hi"), Map.of(1, "Bob"));

        assertThat(sql).doesNotContain("Ann");
        assertThat(sql).contains("WHERE `id` = 7");
    }

    @Test
    @DisplayName("Grid labels match table columns case-insensitively (Postgres lowercases)")
    void caseInsensitiveColumnMatching() {
        String sql = UpdateBuilder.update(DbEngine.POSTGRES, USERS, usersColumns(),
                List.of("ID", "NAME"), row("7", "Ann"), Map.of(1, "Bob"));

        // the statement uses the METADATA's spelling, not the grid label's
        assertThat(sql).isEqualTo("UPDATE \"users\" SET \"name\" = 'Bob' WHERE \"id\" = 7;");
    }

    // ---- value rendering by type -------------------------------------

    @Test
    @DisplayName("Numeric columns render bare: integers, decimals, negatives, exponents")
    void numericRendering() {
        assertThat(UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(), GRID,
                row("7", "Ann", "30", "1", "hi"), Map.of(2, "42")))
                .contains("SET `age` = 42 ");
        assertThat(UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(), GRID,
                row("7", "Ann", "30", "1", "hi"), Map.of(2, "-2.5e3")))
                .contains("SET `age` = -2.5e3 ");
        assertThat(UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(), GRID,
                row("7", "Ann", "30", "1", "hi"), Map.of(2, " 3.14 ")))
                .as("surrounding whitespace trimmed").contains("SET `age` = 3.14 ");
    }

    @Test
    @DisplayName("A non-number into a numeric column refuses with the column and type named")
    void numericRefusesGarbage() {
        assertThatThrownBy(() -> UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(),
                GRID, row("7", "Ann", "30", "1", "hi"), Map.of(2, "abc")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a number")
                .hasMessageContaining("age")
                .hasMessageContaining("INT");
        // a sneaky injection attempt is just another non-number
        assertThatThrownBy(() -> UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(),
                GRID, row("7", "Ann", "30", "1", "hi"), Map.of(2, "1; DROP TABLE users")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a number");
    }

    @Test
    @DisplayName("Type names classify prefix-tolerantly: INT UNSIGNED, DECIMAL(10,2), int4")
    void typeNameTolerance() {
        assertThat(UpdateBuilder.baseType("INT UNSIGNED")).isEqualTo("INT");
        assertThat(UpdateBuilder.baseType("DECIMAL(10,2)")).isEqualTo("DECIMAL");
        assertThat(UpdateBuilder.baseType("int4")).isEqualTo("INT");
        assertThat(UpdateBuilder.baseType("int8")).isEqualTo("INT");
        assertThat(UpdateBuilder.baseType("float8")).isEqualTo("FLOAT");
        assertThat(UpdateBuilder.baseType("double precision")).isEqualTo("DOUBLE");
        assertThat(UpdateBuilder.baseType("TINYINT(1)")).isEqualTo("TINYINT");
        assertThat(UpdateBuilder.baseType("bool")).isEqualTo("BOOL");
        // INTERVAL must NOT classify as INT — it is textual
        assertThat(UpdateBuilder.baseType("INTERVAL")).isEqualTo("INTERVAL");
        assertThat(UpdateBuilder.baseType("varchar")).isEqualTo("VARCHAR");
        assertThat(UpdateBuilder.baseType(null)).isEmpty();
    }

    @Test
    @DisplayName("The numeric-literal scan: signs, decimals, exponents — and only those")
    void numericLiteralShapes() {
        assertThat(UpdateBuilder.isNumericLiteral("0")).isTrue();
        assertThat(UpdateBuilder.isNumericLiteral("42")).isTrue();
        assertThat(UpdateBuilder.isNumericLiteral("-7")).isTrue();
        assertThat(UpdateBuilder.isNumericLiteral("+3")).isTrue();
        assertThat(UpdateBuilder.isNumericLiteral("3.14")).isTrue();
        assertThat(UpdateBuilder.isNumericLiteral(".5")).isTrue();
        assertThat(UpdateBuilder.isNumericLiteral("1.")).isTrue();
        assertThat(UpdateBuilder.isNumericLiteral("-2.5e3")).isTrue();
        assertThat(UpdateBuilder.isNumericLiteral("1E-10")).isTrue();
        assertThat(UpdateBuilder.isNumericLiteral("6.02e+23")).isTrue();

        assertThat(UpdateBuilder.isNumericLiteral("")).isFalse();
        assertThat(UpdateBuilder.isNumericLiteral(".")).isFalse();
        assertThat(UpdateBuilder.isNumericLiteral("-")).isFalse();
        assertThat(UpdateBuilder.isNumericLiteral("1e")).isFalse();
        assertThat(UpdateBuilder.isNumericLiteral("1e+")).isFalse();
        assertThat(UpdateBuilder.isNumericLiteral("1.2.3")).isFalse();
        assertThat(UpdateBuilder.isNumericLiteral("42abc")).isFalse();
        assertThat(UpdateBuilder.isNumericLiteral("0x1F")).isFalse();
        assertThat(UpdateBuilder.isNumericLiteral("1 OR 1=1")).isFalse();
        assertThat(UpdateBuilder.isNumericLiteral("١٢٣")).as("ASCII digits only").isFalse();
    }

    @Test
    @DisplayName("A Postgres int4 column renders bare like any numeric")
    void postgresInt4RendersBare() {
        List<ColumnInfo> columns = List.of(
                new ColumnInfo("id", "int4", 10, false, true),
                new ColumnInfo("score", "float8", 17, true, false));

        String sql = UpdateBuilder.update(DbEngine.POSTGRES, USERS, columns,
                List.of("id", "score"), row("7", "1.5"), Map.of(1, "2.5"));

        assertThat(sql).isEqualTo("UPDATE \"users\" SET \"score\" = 2.5 WHERE \"id\" = 7;");
    }

    @Test
    @DisplayName("Boolean columns pass TRUE/FALSE/0/1 through as typed")
    void booleanPassthrough() {
        assertThat(UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(), GRID,
                row("7", "Ann", "30", "1", "hi"), Map.of(3, "TRUE")))
                .contains("SET `active` = TRUE ");
        assertThat(UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(), GRID,
                row("7", "Ann", "30", "1", "hi"), Map.of(3, "false")))
                .as("passthrough keeps the user's case").contains("SET `active` = false ");
        assertThat(UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(), GRID,
                row("7", "Ann", "30", "1", "hi"), Map.of(3, "0")))
                .contains("SET `active` = 0 ");
        assertThat(UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(), GRID,
                row("7", "Ann", "30", "1", "hi"), Map.of(3, "1")))
                .contains("SET `active` = 1 ");
    }

    @Test
    @DisplayName("A non-boolean into a boolean column refuses and names the accepted forms")
    void booleanRefusesGarbage() {
        assertThatThrownBy(() -> UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(),
                GRID, row("7", "Ann", "30", "1", "hi"), Map.of(3, "maybe")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a boolean")
                .hasMessageContaining("active")
                .hasMessageContaining("TRUE, FALSE, 0 or 1");
    }

    @Test
    @DisplayName("Text values are single-quoted with '' escaping — the classic O'Brien")
    void stringEscaping() {
        String sql = UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(), GRID,
                row("7", "Ann", "30", "1", "hi"), Map.of(1, "O'Brien"));

        assertThat(sql).contains("SET `name` = 'O''Brien' ");
    }

    @Test
    @DisplayName("A hostile string value cannot break out of its quotes")
    void hostileStringStaysQuoted() {
        String sql = UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(), GRID,
                row("7", "Ann", "30", "1", "hi"), Map.of(1, "x'; DROP TABLE users; --"));

        assertThat(sql).contains("SET `name` = 'x''; DROP TABLE users; --' ");
    }

    @Test
    @DisplayName("A Java null edit (the user typed NULL) renders the NULL keyword")
    void nullRendersKeyword() {
        Map<Integer, String> edits = new HashMap<>();
        edits.put(4, null);

        String sql = UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(), GRID,
                row("7", "Ann", "30", "1", "hi"), edits);

        assertThat(sql).isEqualTo("UPDATE `users` SET `bio` = NULL WHERE `id` = 7;");
    }

    @Test
    @DisplayName("Every statement ends with a semicolon")
    void alwaysTerminated() {
        String sql = UpdateBuilder.update(DbEngine.POSTGRES, USERS, usersColumns(), GRID,
                row("7", "Ann", "30", "1", "hi"), Map.of(1, "Bob"));

        assertThat(sql).endsWith(";");
    }

    @Test
    @DisplayName("An identifier containing the quote character is escaped by doubling")
    void identifierQuoteEscaping() {
        List<ColumnInfo> columns = List.of(
                new ColumnInfo("id", "INT", 10, false, true),
                new ColumnInfo("we`ird", "TEXT", 10, true, false));

        String sql = UpdateBuilder.update(DbEngine.MYSQL, USERS, columns,
                List.of("id", "we`ird"), row("7", "x"), Map.of(1, "y"));

        assertThat(sql).contains("SET `we``ird` = 'y' ");
    }

    // ---- the refusal cases — each with a human message ----------------

    @Test
    @DisplayName("REFUSE: a table without a primary key")
    void refusesWithoutPrimaryKey() {
        List<ColumnInfo> noPk = List.of(
                new ColumnInfo("a", "INT", 10, true, false),
                new ColumnInfo("b", "TEXT", 10, true, false));

        assertThatThrownBy(() -> UpdateBuilder.update(DbEngine.MYSQL, USERS, noPk,
                List.of("a", "b"), row("1", "x"), Map.of(1, "y")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no primary key")
                .hasMessageContaining("users");
    }

    @Test
    @DisplayName("REFUSE: editing a primary-key column (v1 rule)")
    void refusesPrimaryKeyEdit() {
        assertThatThrownBy(() -> UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(),
                GRID, row("7", "Ann", "30", "1", "hi"), Map.of(0, "8")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id")
                .hasMessageContaining("primary key")
                .hasMessageContaining("not supported");
    }

    @Test
    @DisplayName("REFUSE: a PK whose original value is NULL — as Java null or as the grid's \"NULL\"")
    void refusesNullPrimaryKeyValue() {
        assertThatThrownBy(() -> UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(),
                GRID, row("NULL", "Ann", "30", "1", "hi"), Map.of(1, "Bob")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id")
                .hasMessageContaining("NULL");
        assertThatThrownBy(() -> UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(),
                GRID, row(null, "Ann", "30", "1", "hi"), Map.of(1, "Bob")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NULL");
    }

    @Test
    @DisplayName("REFUSE: a PK column missing from the result grid")
    void refusesPrimaryKeyNotInGrid() {
        assertThatThrownBy(() -> UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(),
                List.of("name", "age"), row("Ann", "30"), Map.of(0, "Bob")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id")
                .hasMessageContaining("not in the result grid");
    }

    @Test
    @DisplayName("REFUSE: an edited grid column that is not a real table column")
    void refusesNonTableColumn() {
        assertThatThrownBy(() -> UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(),
                List.of("id", "total_price"), row("7", "99"), Map.of(1, "100")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("total_price")
                .hasMessageContaining("not a column");
    }

    @Test
    @DisplayName("REFUSE: document engines — there is no SQL to build")
    void refusesDocumentEngines() {
        assertThatThrownBy(() -> UpdateBuilder.update(DbEngine.MONGODB, USERS, usersColumns(),
                GRID, row("7", "Ann", "30", "1", "hi"), Map.of(1, "Bob")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MongoDB")
                .hasMessageContaining("document engine");
        assertThatThrownBy(() -> UpdateBuilder.update(DbEngine.COUCHDB, USERS, usersColumns(),
                GRID, row("7", "Ann", "30", "1", "hi"), Map.of(1, "Bob")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CouchDB");
    }

    @Test
    @DisplayName("REFUSE: an empty (or null) edit set — no UPDATE to build")
    void refusesEmptyEdits() {
        assertThatThrownBy(() -> UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(),
                GRID, row("7", "Ann", "30", "1", "hi"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nothing was edited");
        assertThatThrownBy(() -> UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(),
                GRID, row("7", "Ann", "30", "1", "hi"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nothing was edited");
    }

    @Test
    @DisplayName("REFUSE: an edit index outside the grid")
    void refusesOutOfRangeIndex() {
        assertThatThrownBy(() -> UpdateBuilder.update(DbEngine.MYSQL, USERS, usersColumns(),
                GRID, row("7", "Ann", "30", "1", "hi"), Map.of(9, "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the grid");
    }
}
