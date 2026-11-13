package org.nmox.studio.dbstudio.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where the 3.9.0 TLS sentences belong, and how much of a server's words
 * the product repeats.
 *
 * <p>{@code JdbcCore.humanize} was written for one moment, a MySQL or
 * MariaDB connection being refused, and was then called for every error
 * of every statement on every engine. Its third rule is loose on purpose
 * (any message holding "ssl" or "tls" beside "certificate", "hostname",
 * "pkix" or "handshake"), and table names hold such words.
 */
class HumanizeScopeTest {

    private static final String TLS_SENTENCE_HEAD = Bundle.JdbcCore_tlsCertificate("").trim();

    @Test
    @DisplayName("a query against a table named ssl_certificates fails in the driver's words, not as a certificate that could not be verified")
    void aStatementErrorIsNotATlsRefusal(@TempDir Path dir) {
        ConnectionSpec s = new ConnectionSpec("id", "t", DbEngine.SQLITE, "", -1, "", "",
                dir.resolve("shop.db").toString());
        try (DbClient client = new DbClient(s, null)) {
            List<QueryResult> run = client.runScript("SELECT * FROM ssl_certificates", 10);
            assertThat(run).hasSize(1);
            String error = run.get(0).error();
            assertThat(error).as("the driver's own words, whole").startsWith("[SQLITE_ERROR]")
                    .contains("no such table: ssl_certificates");
            assertThat(error).as("a SQLite file has no TLS to speak of")
                    .doesNotContain(TLS_SENTENCE_HEAD).doesNotContain("TLS");
        }
    }

    @Test
    @DisplayName("a SQLite file that will not open under a folder named tls-certificates is not a server with a bad certificate")
    void aConnectFailureOnAnEngineWithoutTheBoxIsLeftAlone(@TempDir Path dir) throws Exception {
        Path notADirectory = Files.writeString(dir.resolve("tls-certificates"), "a file where a folder is wanted");
        ConnectionSpec s = new ConnectionSpec("id", "t", DbEngine.SQLITE, "", -1, "", "",
                notADirectory.resolve("shop.db").toString());
        try (DbClient client = new DbClient(s, null)) {
            String error = client.open();
            assertThat(error).as("it does not open").isNotNull();
            assertThat(error).as("and says so as the driver did: %s", error).doesNotContain(TLS_SENTENCE_HEAD);
        }
        // the rule itself, each engine alone
        SQLException loose = new SQLException("could not open tls-certificates/shop.db: bad hostname");
        for (DbEngine engine : new DbEngine[]{DbEngine.SQLITE, DbEngine.POSTGRES}) {
            assertThat(DbClient.connectFailure(engine, loose)).as("%s", engine).isEqualTo(loose.getMessage());
        }
    }

    @Test
    @DisplayName("MySQL and MariaDB connect failures still get all three sentences, each branch alone")
    void theThreeRefusalsStillSpeakWhereTheBoxIs() {
        for (DbEngine engine : new DbEngine[]{DbEngine.MYSQL, DbEngine.MARIADB}) {
            assertThat(DbClient.connectFailure(engine, new SQLException(
                    "Trying to connect with ssl, but ssl not enabled in the server")))
                    .as("%s: no TLS at the server", engine)
                    .isEqualTo(Bundle.JdbcCore_tlsNotOffered("Trying to connect with ssl, but ssl not enabled in the server"));
            assertThat(DbClient.connectFailure(engine, new SQLException(
                    "RSA public key is not available client side (option serverRsaPublicKeyFile not set)")))
                    .as("%s: the plain remote first login", engine)
                    .isEqualTo(Bundle.JdbcCore_tlsPublicKey(
                            "RSA public key is not available client side (option serverRsaPublicKeyFile not set)"));
            for (String certificate : new String[]{
                "SSL: unable to verify the server certificate", "TLS: PKIX path building failed",
                "ssl: hostname mismatch", "tls handshake failed"}) {
                assertThat(DbClient.connectFailure(engine, new SQLException(certificate)))
                        .as("%s: %s", engine, certificate).isEqualTo(Bundle.JdbcCore_tlsCertificate(certificate));
            }
            assertThat(DbClient.connectFailure(engine, new SQLException("Access denied for user 'u'@'h'")))
                    .isEqualTo("Access denied for user 'u'@'h'");
        }
    }

    @Test
    @DisplayName("a server's message is repeated up to a bound, cut with an ellipsis and never inside a surrogate pair")
    void aServersMessageIsBounded() {
        String huge = "x".repeat(2_000_000);
        String said = JdbcCore.message(new SQLException(huge));
        assertThat(said.length()).isEqualTo(JdbcCore.MAX_MESSAGE_CHARS + 1);
        assertThat(said).endsWith("…");
        // a pair straddling the cut is dropped whole
        String straddling = "y".repeat(JdbcCore.MAX_MESSAGE_CHARS - 1) + "😀" + "z".repeat(50);
        String cut = JdbcCore.message(new SQLException(straddling));
        assertThat(cut).isEqualTo("y".repeat(JdbcCore.MAX_MESSAGE_CHARS - 1) + "…");
        // the sentences carry the same bound, and a short message is untouched
        assertThat(JdbcCore.humanize(new SQLException("ssl not enabled in the server " + huge)).length())
                .isLessThan(JdbcCore.MAX_MESSAGE_CHARS + 400);
        assertThat(JdbcCore.message(new SQLException("  Table 'shop.orders' doesn't exist \n")))
                .isEqualTo("Table 'shop.orders' doesn't exist");
        assertThat(JdbcCore.message(new SQLException((String) null))).isEqualTo("SQLException");
    }

    @Test
    @DisplayName("measured: pgJDBC puts the SERVER's severity at the head of its message, so a driver message can begin <html>")
    void aDriverMessageCanBeginWithTheServersText() {
        // the fields of a PostgreSQL ErrorResponse, as a server sends them: S(everity), M(essage)
        org.postgresql.util.PSQLException fromServer = new org.postgresql.util.PSQLException(
                new org.postgresql.util.ServerErrorMessage("S<html><img src=\"http://elsewhere.invalid/x\">\0Mboom\0"));
        String said = DbClient.connectFailure(DbEngine.POSTGRES, fromServer);
        assertThat(said).as("which is why every sink of it is a plain-text sink (NotificationDetailIsPlainTest)")
                .startsWith("<html>");
        assertThat(org.nmox.studio.core.util.PlainText.plain(said)).doesNotStartWith("<html>");
    }
}
