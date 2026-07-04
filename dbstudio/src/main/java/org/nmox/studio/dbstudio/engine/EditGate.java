package org.nmox.studio.dbstudio.engine;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.nmox.studio.dbstudio.model.ColumnInfo;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;

/**
 * Decides whether a result grid may be edited in place — and when it
 * may not, says exactly why in one short status-bar-ready sentence.
 * The gate is the honest counterpart of {@link UpdateBuilder}'s
 * refusals: everything that would make UpdateBuilder throw at Apply
 * time is caught here first, so the grid either edits cleanly or
 * carries a visible reason instead of silently ignoring clicks.
 *
 * <p>A grid is editable exactly when ALL of these hold:
 * <ul>
 *   <li>the backend speaks SQL ({@link DbEngine.Kind#SQL} — document
 *       engines have no UPDATE to build);</li>
 *   <li>DB Studio models the engine's dialect (a Services connection
 *       to Derby or Oracle reports a {@code null} engine — no dialect,
 *       no statement);</li>
 *   <li>{@link SimpleSelectParser} confirms the statement is a simple
 *       single-table SELECT (this naturally rejects {@code EXPLAIN}
 *       output, joins, aggregates, unions — anything whose rows don't
 *       map one-to-one onto table rows);</li>
 *   <li>the table is a base table, not a view (when the connection's
 *       container list can tell);</li>
 *   <li>the table's metadata is reachable and shows at least one
 *       primary-key column;</li>
 *   <li>every primary-key column appears in the result grid (the
 *       original key values are what address each row).</li>
 * </ul>
 *
 * <p>Pure: metadata access is injected as a function, so the decision
 * is unit-tested with canned metadata while the UI passes
 * {@code backend::columns} (already off-EDT when the gate runs).
 */
public final class EditGate {

    /**
     * The gate's verdict: an armed {@link EditSession} when the grid
     * is editable, otherwise a short human reason (never both).
     */
    public record Decision(EditSession session, String reason) {

        /** True when the grid may be edited ({@link #session} is armed). */
        public boolean editable() {
            return session != null;
        }

        static Decision readOnly(String reason) {
            return new Decision(null, reason);
        }
    }

    private EditGate() {
    }

    /**
     * Decides editability for one executed statement's grid.
     *
     * @param engine     the connection's modeled engine, or {@code null}
     *                   when DB Studio has no dialect for it (Services
     *                   connections to Derby, Oracle, …)
     * @param kind       the backend's family — pass
     *                   {@code backend.kind()}, which is SQL for every
     *                   Services connection even when {@code engine} is
     *                   null
     * @param result     the statement's result (only result sets can be
     *                   editable)
     * @param containers the connection's known containers, used to
     *                   resolve the parsed table name to real
     *                   catalog/schema and to spot views; may be empty —
     *                   the gate then synthesizes a lookup key from the
     *                   parsed name
     * @param columnsOf  table metadata access, typically
     *                   {@code backend::columns}; called at most once,
     *                   never for document engines
     */
    public static Decision decide(DbEngine engine, DbEngine.Kind kind, QueryResult result,
            List<TableInfo> containers, Function<TableInfo, List<ColumnInfo>> columnsOf) {
        if (result == null || !result.isResultSet()) {
            return Decision.readOnly("Read-only — not a result grid");
        }
        if (kind != DbEngine.Kind.SQL) {
            return Decision.readOnly("Read-only — document engine");
        }
        if (engine == null) {
            return Decision.readOnly("Read-only — SQL dialect not modeled");
        }
        Optional<String> parsed = SimpleSelectParser.singleTable(result.statement());
        if (parsed.isEmpty()) {
            return Decision.readOnly("Read-only — not a single-table SELECT");
        }
        TableInfo table = resolve(parsed.get(), containers);
        if (table.isView()) {
            return Decision.readOnly("Read-only — " + table.name() + " is a view");
        }
        List<ColumnInfo> columns = columnsOf.apply(table);
        if (columns == null || columns.isEmpty()) {
            return Decision.readOnly("Read-only — no column metadata for " + table.name());
        }
        List<ColumnInfo> pkColumns = columns.stream().filter(ColumnInfo::primaryKey).toList();
        if (pkColumns.isEmpty()) {
            return Decision.readOnly("Read-only — no primary key on " + table.name());
        }
        for (ColumnInfo pk : pkColumns) {
            if (!gridHas(result.columnNames(), pk.name())) {
                return Decision.readOnly("Read-only — primary key column " + pk.name()
                        + " not in the result (SELECT * always works)");
            }
        }
        return new Decision(new EditSession(result, table, columns), null);
    }

    /**
     * The parsed table reference resolved against the connection's
     * known containers (name, and schema when the reference is
     * qualified, both case-insensitively) — that recovers the real
     * catalog/schema/type. When the containers don't know it (cache
     * cold, exotic qualification) a plain lookup key is synthesized;
     * JDBC metadata treats its blank catalog/schema as wildcards.
     */
    static TableInfo resolve(String parsedName, List<TableInfo> containers) {
        int dot = parsedName.lastIndexOf('.');
        String schema = dot < 0 ? "" : parsedName.substring(0, dot);
        String name = dot < 0 ? parsedName : parsedName.substring(dot + 1);
        for (TableInfo container : containers) {
            if (container.name().equalsIgnoreCase(name)
                    && (schema.isEmpty() || schema.equalsIgnoreCase(container.schema()))) {
                return container;
            }
        }
        return new TableInfo("", schema, name, "TABLE");
    }

    private static boolean gridHas(List<String> columnNames, String name) {
        for (String columnName : columnNames) {
            if (columnName.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
