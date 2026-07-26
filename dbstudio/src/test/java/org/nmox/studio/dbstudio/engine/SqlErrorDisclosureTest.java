package org.nmox.studio.dbstudio.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The disclosure is a promise: whatever these tests allow through is
 * exactly what leaves the machine when a user presses Explain on a
 * failed statement.
 */
class SqlErrorDisclosureTest {

    @Test
    @DisplayName("The body carries the statement, the error and the engine — nothing else")
    void bodyCarriesTheThreeThings() {
        String body = SqlErrorDisclosure.body("postgresql",
                "SELECT * FROM orders WHERE qty = 'abc'",
                "ERROR: invalid input syntax for type integer: \"abc\"");
        assertThat(body)
                .contains("postgresql")
                .contains("SELECT * FROM orders WHERE qty = 'abc'")
                .contains("invalid input syntax");
    }

    @Test
    @DisplayName("The consent line names the literals honestly and the exclusions")
    void consentLineIsHonest() {
        String what = SqlErrorDisclosure.what("mysql");
        assertThat(what)
                .as("literals are NOT masked, so the line must say they go")
                .contains("including any literal values")
                .contains("mysql")
                .contains("no connection details")
                .contains("no password")
                .contains("no result rows");
    }

    @Test
    @DisplayName("A blank engine kind is named, never printed as null")
    void blankEngineNamed() {
        assertThat(SqlErrorDisclosure.what(null)).contains("unknown").doesNotContain("null");
        assertThat(SqlErrorDisclosure.body(null, "SELECT 1", "boom"))
                .contains("Engine: unknown");
    }

    @Test
    @DisplayName("A huge generated statement is capped and marked")
    void hugeStatementCapped() {
        String huge = "INSERT INTO t VALUES " + "(1),".repeat(5_000);
        String body = SqlErrorDisclosure.body("sqlite", huge, "syntax error");
        assertThat(body).contains("[statement truncated]");
        assertThat(body.length()).isLessThan(huge.length());
    }

    @Test
    @DisplayName("Caps are code-point-safe — no lone surrogate ever ships")
    void capsAreCodePointSafe() {
        String emoji = "🧨".repeat(4_000); // 2 chars each: the cap lands mid-pair
        String capped = SqlErrorDisclosure.cap(emoji, SqlErrorDisclosure.MAX_SQL_CHARS);
        assertThat(Character.isHighSurrogate(capped.charAt(capped.length() - 1))).isFalse();
    }

    @Test
    @DisplayName("A driver error with no message still produces an honest body")
    void emptyErrorIsHonest() {
        String body = SqlErrorDisclosure.body("couchdb", "SELECT 1", "");
        assertThat(body).contains("(no message)");
    }
}
