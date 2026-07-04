package org.nmox.studio.dbstudio.engine;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The dirty-cell session behind in-grid editing: difference tracking
 * (edit-back-to-original un-dirties), the grid's NULL text convention
 * in both directions, deterministic UPDATE ordering, and verbatim
 * propagation of {@link UpdateBuilder}'s refusals.
 */
class EditSessionTest {

    private static final TableInfo USERS = new TableInfo("", "", "users", "TABLE");
    private static final List<ColumnInfo> COLUMNS = List.of(
            new ColumnInfo("id", "INTEGER", 10, false, true),
            new ColumnInfo("name", "TEXT", 0, true, false),
            new ColumnInfo("age", "INT", 10, true, false));

    private static QueryResult grid() {
        return new QueryResult(List.of("id", "name", "age"),
                List.of(List.of("1", "ada", "36"),
                        List.of("2", "grace", "45"),
                        Arrays.asList("3", null, "NULL")),
                3, -1, false, 1, null, "SELECT * FROM users;");
    }

    private static EditSession session() {
        return new EditSession(grid(), USERS, COLUMNS);
    }

    @Test
    @DisplayName("An edit dirties exactly its cell and shows through valueAt")
    void editDirtiesCell() {
        EditSession session = session();
        session.edit(0, 1, "lovelace");

        assertThat(session.dirtyCount()).isEqualTo(1);
        assertThat(session.isDirty(0, 1)).isTrue();
        assertThat(session.isDirty(0, 2)).isFalse();
        assertThat(session.isDirty(1, 1)).isFalse();
        assertThat(session.valueAt(0, 1)).isEqualTo("lovelace");
        assertThat(session.valueAt(1, 1)).isEqualTo("grace");
    }

    @Test
    @DisplayName("Editing a cell back to its original text clears its dirty state")
    void editBackToOriginalCleans() {
        EditSession session = session();
        session.edit(0, 1, "lovelace");
        session.edit(0, 1, "ada");

        assertThat(session.dirtyCount()).isZero();
        assertThat(session.isDirty(0, 1)).isFalse();
        assertThat(session.valueAt(0, 1)).isEqualTo("ada");
    }

    @Test
    @DisplayName("Re-editing a dirty cell keeps one entry — the last value wins")
    void reEditKeepsLastValue() {
        EditSession session = session();
        session.edit(0, 1, "first");
        session.edit(0, 1, "second");

        assertThat(session.dirtyCount()).isEqualTo(1);
        assertThat(session.valueAt(0, 1)).isEqualTo("second");
    }

    @Test
    @DisplayName("revert forgets every edit; originals show again")
    void revertForgetsEverything() {
        EditSession session = session();
        session.edit(0, 1, "x");
        session.edit(1, 2, "50");
        session.revert();

        assertThat(session.dirtyCount()).isZero();
        assertThat(session.valueAt(0, 1)).isEqualTo("ada");
        assertThat(session.valueAt(1, 2)).isEqualTo("45");
    }

    @Test
    @DisplayName("Multi-row edits build one UPDATE per row, rows in ascending grid order")
    void multiRowStatementsDeterministic() {
        EditSession session = session();
        session.edit(1, 1, "hopper"); // second row edited FIRST
        session.edit(0, 2, "37");

        List<String> statements = session.statements(DbEngine.SQLITE);

        assertThat(statements).containsExactly(
                "UPDATE \"users\" SET \"age\" = 37 WHERE \"id\" = 1;",
                "UPDATE \"users\" SET \"name\" = 'hopper' WHERE \"id\" = 2;");
    }

    @Test
    @DisplayName("Two edits in the same row fold into one UPDATE, SETs in grid order")
    void sameRowFoldsIntoOneStatement() {
        EditSession session = session();
        session.edit(0, 2, "40"); // later grid column edited first
        session.edit(0, 1, "countess");

        assertThat(session.statements(DbEngine.SQLITE)).containsExactly(
                "UPDATE \"users\" SET \"name\" = 'countess', \"age\" = 40 WHERE \"id\" = 1;");
        assertThat(session.dirtyRowCount()).isEqualTo(1);
        assertThat(session.dirtyCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Typing NULL into a cell means SQL NULL in the statement")
    void typedNullBecomesSqlNull() {
        EditSession session = session();
        session.edit(0, 1, "NULL");

        assertThat(session.statements(DbEngine.SQLITE)).containsExactly(
                "UPDATE \"users\" SET \"name\" = NULL WHERE \"id\" = 1;");
    }

    @Test
    @DisplayName("A NULL original renders as the text NULL, and typing NULL there is not an edit")
    void nullOriginalsSpeakTheGridConvention() {
        EditSession session = session();

        assertThat(session.valueAt(2, 1)).isEqualTo("NULL"); // raw null cell
        assertThat(session.valueAt(2, 2)).isEqualTo("NULL"); // engine-stringified NULL

        session.edit(2, 1, "NULL");
        assertThat(session.dirtyCount()).isZero();
    }

    @Test
    @DisplayName("A Java null edit is normalized to the NULL text, same as typing it")
    void nullEditNormalized() {
        EditSession session = session();
        session.edit(0, 1, null);

        assertThat(session.valueAt(0, 1)).isEqualTo("NULL");
        assertThat(session.statements(DbEngine.SQLITE)).containsExactly(
                "UPDATE \"users\" SET \"name\" = NULL WHERE \"id\" = 1;");
    }

    @Test
    @DisplayName("MySQL statements ride the backtick dialect")
    void mysqlDialect() {
        EditSession session = session();
        session.edit(0, 1, "x");

        assertThat(session.statements(DbEngine.MYSQL)).containsExactly(
                "UPDATE `users` SET `name` = 'x' WHERE `id` = 1;");
    }

    @Test
    @DisplayName("No edits, no statements")
    void cleanSessionBuildsNothing() {
        assertThat(session().statements(DbEngine.SQLITE)).isEmpty();
        assertThat(session().dirtyRowCount()).isZero();
    }

    @Test
    @DisplayName("UpdateBuilder's primary-key refusal propagates verbatim")
    void pkEditRefusalPropagates() {
        EditSession session = session();
        session.edit(0, 0, "9"); // the UI never allows this; the model still refuses

        assertThatThrownBy(() -> session.statements(DbEngine.SQLITE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("part of the primary key");
    }

    @Test
    @DisplayName("An edited alias/expression column refuses through UpdateBuilder's message")
    void aliasColumnRefusalPropagates() {
        QueryResult aliased = new QueryResult(List.of("id", "shout"),
                List.of(List.of("1", "ADA")), 1, -1, false, 1, null,
                "SELECT id, upper(name) shout FROM users;");
        EditSession session = new EditSession(aliased, USERS, COLUMNS);
        session.edit(0, 1, "GRACE");

        assertThatThrownBy(() -> session.statements(DbEngine.SQLITE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a column of table");
    }

    @Test
    @DisplayName("A document engine refuses through UpdateBuilder's message")
    void documentEngineRefusalPropagates() {
        EditSession session = session();
        session.edit(0, 1, "x");

        assertThatThrownBy(() -> session.statements(DbEngine.MONGODB))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document engine");
    }

    @Test
    @DisplayName("editableColumn: real non-key columns yes; primary key and unknowns no")
    void editableColumns() {
        EditSession session = session();

        assertThat(session.editableColumn(0)).isFalse(); // primary key
        assertThat(session.editableColumn(1)).isTrue();
        assertThat(session.editableColumn(2)).isTrue();
        assertThat(session.editableColumn(-1)).isFalse();
        assertThat(session.editableColumn(3)).isFalse(); // outside the grid

        QueryResult aliased = new QueryResult(List.of("id", "shout"),
                List.of(List.of("1", "ADA")), 1, -1, false, 1, null, "x");
        assertThat(new EditSession(aliased, USERS, COLUMNS).editableColumn(1))
                .isFalse(); // not a real table column
    }

    @Test
    @DisplayName("Grid accessors mirror the result")
    void accessors() {
        EditSession session = session();

        assertThat(session.rowCount()).isEqualTo(3);
        assertThat(session.columnNames()).containsExactly("id", "name", "age");
        assertThat(session.table()).isEqualTo(USERS);
    }
}
