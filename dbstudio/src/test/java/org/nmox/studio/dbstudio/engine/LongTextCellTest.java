package org.nmox.studio.dbstudio.engine;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The per-cell ceiling (ledger 54 M4) promises that a LOB column's length
 * is read from metadata BEFORE the value, so a giant cell is never fully
 * pulled into a String. It covered {@code CLOB}/{@code NCLOB} only — and
 * MySQL and MariaDB never report those: read from the shipped MariaDB
 * Connector/J 3.5, {@code BlobColumn.getColumnType} answers
 * {@code Types.LONGVARCHAR} (-1) for a non-binary blob column above the
 * medium size, so {@code MEDIUMTEXT} (16 MiB) and {@code LONGTEXT}
 * (4 GiB) — the only two text types on those engines that can actually
 * take the heap — fell straight through to {@code getString}. The
 * asymmetry gave it away: {@code LONGVARBINARY} was in the binary branch
 * and its text twin was in no branch at all.
 *
 * <p>These tests drive {@code cell} against a stand-in {@link ResultSet}
 * because the shipped drivers that report {@code LONGVARCHAR} need a live
 * MySQL or MariaDB server; the branch is also proven end to end against a
 * real SQLite {@code CLOB} column below.
 */
class LongTextCellTest {

    /** A one-column result set that answers exactly what a test asks it to. */
    private static final class FakeRow implements InvocationHandler {
        private final int type;
        private final String value;
        private final boolean serveClob;
        boolean getStringCalled;

        FakeRow(int type, String value, boolean serveClob) {
            this.type = type;
            this.value = value;
            this.serveClob = serveClob;
        }

        ResultSet resultSet() {
            return (ResultSet) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{ResultSet.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "getMetaData":
                    return Proxy.newProxyInstance(getClass().getClassLoader(),
                            new Class<?>[]{ResultSetMetaData.class},
                            (m, mm, margs) -> "getColumnType".equals(mm.getName())
                                    ? Integer.valueOf(type)
                                    : refuse(mm));
                case "getClob":
                    if (!serveClob) {
                        // pgjdbc reads the value as a large-object OID and
                        // fails; sqlite-jdbc refuses some LOB calls outright
                        throw new SQLFeatureNotSupportedException("no clob here");
                    }
                    return value == null ? null : new javax.sql.rowset.serial.SerialClob(
                            value.toCharArray());
                case "getString":
                    getStringCalled = true;
                    return value;
                default:
                    return refuse(method);
            }
        }

        private static Object refuse(Method method) throws SQLException {
            throw new SQLException("the stand-in answers no " + method.getName());
        }
    }

    @Test
    @DisplayName("a LONGVARCHAR cell is read through its length, never as a whole String")
    void longVarcharIsCappedWithoutGetString() throws Exception {
        String huge = "z".repeat(JdbcCore.MAX_CELL_CHARS * 3);
        FakeRow row = new FakeRow(Types.LONGVARCHAR, huge, true);

        String cell = JdbcCore.cell(row.resultSet(), 1);

        assertThat(cell).startsWith("zzz").contains("truncated")
                .hasSizeLessThanOrEqualTo(JdbcCore.MAX_CELL_CHARS + 40);
        assertThat(row.getStringCalled)
                .as("the whole point: the 4 GiB a LONGTEXT may hold is never"
                        + " materialized as a String just to cut it back down")
                .isFalse();
    }

    @Test
    @DisplayName("LONGNVARCHAR takes the same route as its non-national twin")
    void longNVarcharIsCappedToo() throws Exception {
        String huge = "n".repeat(JdbcCore.MAX_CELL_CHARS * 2);
        FakeRow row = new FakeRow(Types.LONGNVARCHAR, huge, true);

        assertThat(JdbcCore.cell(row.resultSet(), 1)).contains("truncated");
        assertThat(row.getStringCalled).isFalse();
    }

    @Test
    @DisplayName("a driver that refuses getClob falls back to getString, still truncated")
    void refusingDriverFallsBackHonestly() throws Exception {
        String huge = "q".repeat(JdbcCore.MAX_CELL_CHARS * 2);
        FakeRow row = new FakeRow(Types.LONGVARCHAR, huge, false);

        String cell = JdbcCore.cell(row.resultSet(), 1);

        assertThat(cell).startsWith("qqq").contains("truncated");
        assertThat(row.getStringCalled)
                .as("LONGVARCHAR carries no obligation to serve getClob, so a"
                        + " refusal must not become an error in the grid")
                .isTrue();
    }

    @Test
    @DisplayName("SQL NULL in a long-text column reads NULL from ONE read of the column")
    void nullLongTextReadsNull() throws Exception {
        FakeRow row = new FakeRow(Types.LONGVARCHAR, null, true);
        assertThat(JdbcCore.cell(row.resultSet(), 1)).isEqualTo("NULL");
        // A null Clob is the getClob contract's way of saying SQL NULL, and
        // it is an ANSWER, not a refusal. Letting it fall through to
        // getString would print the same "NULL" — so the value alone cannot
        // tell the two apart — while reading the same column twice, which
        // a streaming driver need not allow. One cell, one read.
        assertThat(row.getStringCalled)
                .as("a null Clob already answered; the fallback is for a"
                        + " driver that REFUSED, not for a column that is null")
                .isFalse();
    }

    @Test
    @DisplayName("an ordinary short LONGVARCHAR comes back whole")
    void shortLongTextIsNotTruncated() throws Exception {
        FakeRow row = new FakeRow(Types.LONGVARCHAR, "a short note", true);
        assertThat(JdbcCore.cell(row.resultSet(), 1)).isEqualTo("a short note");
    }

    @Test
    @DisplayName("on a real driver: an oversize CLOB column is capped end to end")
    void realClobColumnIsCapped(@TempDir Path dir) throws SQLException {
        try (Connection c = DriverManager.getConnection(
                "jdbc:sqlite:" + dir.resolve("clob.db"))) {
            JdbcCore.CancelHook hook = new JdbcCore.CancelHook();
            JdbcCore.runStatements(c,
                    SqlSplitter.split("CREATE TABLE notes (body CLOB);"), 10, hook);
            int huge = JdbcCore.MAX_CELL_CHARS * 3;
            try (var st = c.prepareStatement("INSERT INTO notes VALUES (?)")) {
                st.setString(1, "c".repeat(huge));
                st.executeUpdate();
            }
            QueryResult r = JdbcCore.runStatements(c,
                    SqlSplitter.split("SELECT body FROM notes;"), 10, hook).get(0);
            assertThat(r.rows().get(0).get(0))
                    .startsWith("ccc").contains("truncated")
                    .hasSizeLessThanOrEqualTo(JdbcCore.MAX_CELL_CHARS + 40);
        }
    }
}
