package org.nmox.studio.dbstudio.engine;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The editability gate: a grid edits only when the backend speaks a
 * modeled SQL dialect, the statement is a simple single-table SELECT,
 * the table is a base table with a primary key, and every key column
 * is in the grid — every other outcome carries one short honest
 * reason. EXPLAIN output is pinned read-only here.
 */
class EditGateTest {

    private static final TableInfo USERS = new TableInfo("", "public", "users", "TABLE");
    private static final TableInfo V_USERS = new TableInfo("", "public", "v_users", "VIEW");
    private static final List<TableInfo> CONTAINERS = List.of(USERS, V_USERS);
    private static final List<ColumnInfo> WITH_PK = List.of(
            new ColumnInfo("id", "INTEGER", 10, false, true),
            new ColumnInfo("name", "TEXT", 0, true, false));
    private static final List<ColumnInfo> NO_PK = List.of(
            new ColumnInfo("id", "INTEGER", 10, false, false),
            new ColumnInfo("name", "TEXT", 0, true, false));

    private static QueryResult grid(String statement) {
        return grid(statement, List.of("id", "name"));
    }

    private static QueryResult grid(String statement, List<String> columns) {
        return new QueryResult(columns, List.of(List.of("1", "ada")),
                1, -1, false, 1, null, statement);
    }

    @Test
    @DisplayName("A single-table SELECT on a PK'd table is editable — no reason, armed session")
    void happyPathEditable() {
        EditGate.Decision decision = EditGate.decide(DbEngine.POSTGRES, DbEngine.Kind.SQL,
                grid("SELECT * FROM users;"), CONTAINERS, t -> WITH_PK);

        assertThat(decision.editable()).isTrue();
        assertThat(decision.reason()).isNull();
        assertThat(decision.session().table()).isEqualTo(USERS);
    }

    @Test
    @DisplayName("Document engines stay read-only")
    void documentEngineReadOnly() {
        EditGate.Decision decision = EditGate.decide(DbEngine.MONGODB,
                DbEngine.Kind.DOCUMENT, grid("{\"find\": \"users\"}"),
                List.of(), t -> WITH_PK);

        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).isEqualTo("Read-only — document engine");
    }

    @Test
    @DisplayName("A Services connection without a modeled dialect stays read-only")
    void unmodeledDialectReadOnly() {
        EditGate.Decision decision = EditGate.decide(null, DbEngine.Kind.SQL,
                grid("SELECT * FROM users"), CONTAINERS, t -> WITH_PK);

        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).isEqualTo("Read-only — SQL dialect not modeled");
    }

    @Test
    @DisplayName("Joins are not a single-table SELECT")
    void joinReadOnly() {
        EditGate.Decision decision = EditGate.decide(DbEngine.POSTGRES, DbEngine.Kind.SQL,
                grid("SELECT * FROM users u JOIN orders o ON o.user_id = u.id;"),
                CONTAINERS, t -> WITH_PK);

        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).isEqualTo("Read-only — not a single-table SELECT");
    }

    @Test
    @DisplayName("An EXPLAIN result never qualifies for editing")
    void explainReadOnly() {
        EditGate.Decision plain = EditGate.decide(DbEngine.POSTGRES, DbEngine.Kind.SQL,
                grid(ExplainQueries.explain(DbEngine.POSTGRES, "SELECT * FROM users;")),
                CONTAINERS, t -> WITH_PK);
        EditGate.Decision sqlite = EditGate.decide(DbEngine.SQLITE, DbEngine.Kind.SQL,
                grid(ExplainQueries.explain(DbEngine.SQLITE, "SELECT * FROM users;")),
                CONTAINERS, t -> WITH_PK);

        assertThat(plain.editable()).isFalse();
        assertThat(plain.reason()).isEqualTo("Read-only — not a single-table SELECT");
        assertThat(sqlite.editable()).isFalse();
        assertThat(sqlite.reason()).isEqualTo("Read-only — not a single-table SELECT");
    }

    @Test
    @DisplayName("Errors and update counts are not editable grids")
    void nonGridsReadOnly() {
        QueryResult error = new QueryResult(List.of(), List.of(), 0, -1, false, 1,
                "boom", "SELECT * FROM users;");
        QueryResult update = new QueryResult(List.of(), List.of(), 0, 3, false, 1,
                null, "UPDATE users SET name = 'x';");

        assertThat(EditGate.decide(DbEngine.SQLITE, DbEngine.Kind.SQL, error,
                List.of(), t -> WITH_PK).reason()).isEqualTo("Read-only — not a result grid");
        assertThat(EditGate.decide(DbEngine.SQLITE, DbEngine.Kind.SQL, update,
                List.of(), t -> WITH_PK).reason()).isEqualTo("Read-only — not a result grid");
        assertThat(EditGate.decide(DbEngine.SQLITE, DbEngine.Kind.SQL, null,
                List.of(), t -> WITH_PK).reason()).isEqualTo("Read-only — not a result grid");
    }

    @Test
    @DisplayName("Views stay read-only when the container list knows them")
    void viewReadOnly() {
        EditGate.Decision decision = EditGate.decide(DbEngine.POSTGRES, DbEngine.Kind.SQL,
                grid("SELECT * FROM v_users;"), CONTAINERS, t -> WITH_PK);

        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).isEqualTo("Read-only — v_users is a view");
    }

    @Test
    @DisplayName("Unreachable metadata stays read-only")
    void noMetadataReadOnly() {
        EditGate.Decision decision = EditGate.decide(DbEngine.POSTGRES, DbEngine.Kind.SQL,
                grid("SELECT * FROM users;"), CONTAINERS, t -> List.of());

        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).isEqualTo("Read-only — no column metadata for users");
    }

    @Test
    @DisplayName("A table without a primary key stays read-only")
    void noPrimaryKeyReadOnly() {
        EditGate.Decision decision = EditGate.decide(DbEngine.POSTGRES, DbEngine.Kind.SQL,
                grid("SELECT * FROM users;"), CONTAINERS, t -> NO_PK);

        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).isEqualTo("Read-only — no primary key on users");
    }

    @Test
    @DisplayName("A grid missing a primary-key column stays read-only, and says which")
    void pkNotSelectedReadOnly() {
        EditGate.Decision decision = EditGate.decide(DbEngine.POSTGRES, DbEngine.Kind.SQL,
                grid("SELECT name FROM users;", List.of("name")), CONTAINERS, t -> WITH_PK);

        assertThat(decision.editable()).isFalse();
        assertThat(decision.reason()).isEqualTo(
                "Read-only — primary key column id not in the result (SELECT * always works)");
    }

    @Test
    @DisplayName("A qualified table resolves to the container's real TableInfo")
    void qualifiedNameResolvesFromContainers() {
        AtomicReference<TableInfo> asked = new AtomicReference<>();
        EditGate.Decision decision = EditGate.decide(DbEngine.POSTGRES, DbEngine.Kind.SQL,
                grid("SELECT * FROM public.users;"), CONTAINERS,
                t -> {
                    asked.set(t);
                    return WITH_PK;
                });

        assertThat(decision.editable()).isTrue();
        assertThat(asked.get()).isEqualTo(USERS); // catalog/schema/type recovered
    }

    @Test
    @DisplayName("Resolution is case-insensitive, like SQL identifiers")
    void caseInsensitiveResolution() {
        EditGate.Decision decision = EditGate.decide(DbEngine.POSTGRES, DbEngine.Kind.SQL,
                grid("SELECT * FROM USERS;"), CONTAINERS, t -> WITH_PK);

        assertThat(decision.editable()).isTrue();
        assertThat(decision.session().table()).isEqualTo(USERS);
    }

    @Test
    @DisplayName("A cold container cache synthesizes a wildcard lookup key from the parsed name")
    void coldCacheSynthesizesLookup() {
        AtomicReference<TableInfo> asked = new AtomicReference<>();
        EditGate.Decision decision = EditGate.decide(DbEngine.POSTGRES, DbEngine.Kind.SQL,
                grid("SELECT * FROM public.users;"), List.of(),
                t -> {
                    asked.set(t);
                    return WITH_PK;
                });

        assertThat(decision.editable()).isTrue();
        assertThat(asked.get().name()).isEqualTo("users");
        assertThat(asked.get().schema()).isEqualTo("public");
        assertThat(asked.get().catalog()).isEmpty();
    }
}
