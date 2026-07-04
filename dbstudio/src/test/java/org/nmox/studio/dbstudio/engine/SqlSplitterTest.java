package org.nmox.studio.dbstudio.engine;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The splitter is the most delicate pure logic in DB Studio: one
 * mis-read quote and a script executes garbage halves of statements.
 * Every syntax the class claims to understand is pinned down here.
 */
class SqlSplitterTest {

    // ---- the plain cases -------------------------------------------

    @Test
    @DisplayName("Two simple statements split on the semicolon")
    void twoSimpleStatements() {
        assertThat(SqlSplitter.split("SELECT 1;SELECT 2;"))
                .containsExactly("SELECT 1", "SELECT 2");
    }

    @Test
    @DisplayName("A trailing statement without a terminating semicolon is included")
    void trailingStatementWithoutSemicolon() {
        assertThat(SqlSplitter.split("SELECT 1; SELECT 2"))
                .containsExactly("SELECT 1", "SELECT 2");
    }

    @Test
    @DisplayName("Statements keep their internal newlines and spacing (only edges trimmed)")
    void multiLineStatementPreserved() {
        String script = "CREATE TABLE t (\n  id INTEGER,\n  name TEXT\n);\n";
        assertThat(SqlSplitter.split(script))
                .containsExactly("CREATE TABLE t (\n  id INTEGER,\n  name TEXT\n)");
    }

    @Test
    @DisplayName("null, empty and whitespace-only scripts yield no statements")
    void degenerateInputs() {
        assertThat(SqlSplitter.split(null)).isEmpty();
        assertThat(SqlSplitter.split("")).isEmpty();
        assertThat(SqlSplitter.split("   \n\t  ")).isEmpty();
    }

    @Test
    @DisplayName("Runs of empty statements (;;;) are dropped")
    void emptyStatementsDropped() {
        assertThat(SqlSplitter.split(";;;")).isEmpty();
        assertThat(SqlSplitter.split("SELECT 1;;;SELECT 2;"))
                .containsExactly("SELECT 1", "SELECT 2");
        assertThat(SqlSplitter.split("SELECT 1; ; \n ; SELECT 2"))
                .containsExactly("SELECT 1", "SELECT 2");
    }

    // ---- semicolons hiding inside quoted things ----------------------

    @Test
    @DisplayName("A semicolon inside a single-quoted string does not split")
    void semicolonInsideString() {
        assertThat(SqlSplitter.split("INSERT INTO t VALUES ('a;b');SELECT 1;"))
                .containsExactly("INSERT INTO t VALUES ('a;b')", "SELECT 1");
    }

    @Test
    @DisplayName("The standard '' escape stays inside the string: 'It''s; tricky'")
    void escapedQuoteInsideString() {
        assertThat(SqlSplitter.split("SELECT 'It''s; tricky';SELECT 2;"))
                .containsExactly("SELECT 'It''s; tricky'", "SELECT 2");
    }

    @Test
    @DisplayName("A string ending in an escaped quote closes correctly: 'ends'''")
    void stringEndingInEscapedQuote() {
        assertThat(SqlSplitter.split("SELECT 'ends''';SELECT 2;"))
                .containsExactly("SELECT 'ends'''", "SELECT 2");
    }

    @Test
    @DisplayName("A semicolon inside a double-quoted identifier does not split")
    void semicolonInsideDoubleQuotedIdentifier() {
        assertThat(SqlSplitter.split("SELECT \"weird;column\" FROM t;SELECT 2;"))
                .containsExactly("SELECT \"weird;column\" FROM t", "SELECT 2");
    }

    @Test
    @DisplayName("A semicolon inside a backtick identifier does not split (MySQL)")
    void semicolonInsideBacktickIdentifier() {
        assertThat(SqlSplitter.split("SELECT `weird;column` FROM t;SELECT 2;"))
                .containsExactly("SELECT `weird;column` FROM t", "SELECT 2");
    }

    @Test
    @DisplayName("Comment starters inside a string are just text: 'a--b' and 'a/*b'")
    void commentMarkersInsideStringAreText() {
        assertThat(SqlSplitter.split("SELECT 'a--b; c';SELECT 'a/*b;*/c';"))
                .containsExactly("SELECT 'a--b; c'", "SELECT 'a/*b;*/c'");
    }

    // ---- comments ----------------------------------------------------

    @Test
    @DisplayName("A semicolon inside a -- line comment does not split")
    void semicolonInsideDashComment() {
        assertThat(SqlSplitter.split("SELECT 1 -- trailing; note\n;SELECT 2;"))
                .containsExactly("SELECT 1 -- trailing; note", "SELECT 2");
    }

    @Test
    @DisplayName("A semicolon inside a # line comment does not split (MySQL idiom)")
    void semicolonInsideHashComment() {
        assertThat(SqlSplitter.split("SELECT 1 # trailing; note\n;SELECT 2;"))
                .containsExactly("SELECT 1 # trailing; note", "SELECT 2");
    }

    @Test
    @DisplayName("A semicolon inside a /* block comment */ does not split")
    void semicolonInsideBlockComment() {
        assertThat(SqlSplitter.split("SELECT /* not; here */ 1;SELECT 2;"))
                .containsExactly("SELECT /* not; here */ 1", "SELECT 2");
    }

    @Test
    @DisplayName("A block comment spanning lines with semicolons stays one statement")
    void multiLineBlockComment() {
        String script = "SELECT 1 /* line one;\nline two;\nline three */;SELECT 2;";
        assertThat(SqlSplitter.split(script))
                .containsExactly("SELECT 1 /* line one;\nline two;\nline three */", "SELECT 2");
    }

    @Test
    @DisplayName("A line comment ends at the newline; the next line splits normally")
    void lineCommentEndsAtNewline() {
        assertThat(SqlSplitter.split("-- header\nSELECT 1;\nSELECT 2;"))
                .containsExactly("-- header\nSELECT 1", "SELECT 2");
    }

    @Test
    @DisplayName("Comment-only statements are dropped: --, #, and /* */ variants")
    void commentOnlyStatementsDropped() {
        assertThat(SqlSplitter.split("-- just a note\n;")).isEmpty();
        assertThat(SqlSplitter.split("# just a note\n;")).isEmpty();
        assertThat(SqlSplitter.split("/* just a note */;")).isEmpty();
        assertThat(SqlSplitter.split("/* note */ ; -- tail\n")).isEmpty();
        assertThat(SqlSplitter.split("-- a\n-- b\nSELECT 1; /* c */"))
                .containsExactly("-- a\n-- b\nSELECT 1");
    }

    @Test
    @DisplayName("Comments embedded in a real statement are kept verbatim (they can be hints)")
    void embeddedCommentsKeptVerbatim() {
        List<String> statements = SqlSplitter.split("SELECT /*+ INDEX(t i) */ * FROM t;");
        assertThat(statements).hasSize(1);
        assertThat(statements.get(0)).isEqualTo("SELECT /*+ INDEX(t i) */ * FROM t");
    }

    @Test
    @DisplayName("A single dash is an operator, not a comment: SELECT 5-3")
    void singleDashIsNotComment() {
        assertThat(SqlSplitter.split("SELECT 5-3;SELECT 2;"))
                .containsExactly("SELECT 5-3", "SELECT 2");
    }

    // ---- rough edges ---------------------------------------------------

    @Test
    @DisplayName("An unterminated string swallows the rest of the script into one statement")
    void unterminatedStringSwallowsRest() {
        assertThat(SqlSplitter.split("SELECT 'oops; SELECT 2;"))
                .containsExactly("SELECT 'oops; SELECT 2;");
    }

    @Test
    @DisplayName("An unterminated block comment leaves a comment-only tail dropped")
    void unterminatedBlockCommentDropped() {
        assertThat(SqlSplitter.split("SELECT 1; /* dangling; comment"))
                .containsExactly("SELECT 1");
    }

    @Test
    @DisplayName("A line comment at end of script without a newline still terminates")
    void lineCommentAtEndOfScript() {
        assertThat(SqlSplitter.split("SELECT 1; -- done"))
                .containsExactly("SELECT 1");
    }

    @Test
    @DisplayName("A realistic mixed script splits into exactly its real statements")
    void realisticMixedScript() {
        String script = """
                -- schema
                CREATE TABLE names (id INTEGER, label TEXT); # inline note
                INSERT INTO names VALUES (1, 'It''s; fine');
                /* seed the second row;
                   with care */
                INSERT INTO names VALUES (2, "a;b");
                SELECT * FROM names
                """;
        List<String> statements = SqlSplitter.split(script);
        assertThat(statements).hasSize(4);
        assertThat(statements.get(0)).startsWith("-- schema").endsWith("label TEXT)");
        assertThat(statements.get(1)).startsWith("# inline note")
                .endsWith("VALUES (1, 'It''s; fine')");
        assertThat(statements.get(2)).contains("with care */")
                .endsWith("VALUES (2, \"a;b\")");
        assertThat(statements.get(3)).isEqualTo("SELECT * FROM names");
    }
}
