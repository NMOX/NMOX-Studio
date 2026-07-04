package org.nmox.studio.dbstudio.io;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The .env connection suggester: the DB_* family and DATABASE_URL, the
 * enough-signal rule (a suggestion is never fabricated from noise), and
 * the secrecy expectation that a Suggestion's toString never shows the
 * password.
 */
class EnvConnectionsTest {

    // ---- the DB_* family ---------------------------------------------

    @Test
    @DisplayName("A full Laravel-style DB_* block suggests the complete connection")
    void fullDbBlock() {
        Optional<EnvConnections.Suggestion> suggestion = EnvConnections.fromEnv("""
                APP_NAME=shop
                DB_CONNECTION=mysql
                DB_HOST=db.internal
                DB_PORT=3307
                DB_DATABASE=shop
                DB_USERNAME=shop_rw
                DB_PASSWORD=s3cret
                """);

        assertThat(suggestion).isPresent();
        EnvConnections.Suggestion s = suggestion.orElseThrow();
        assertThat(s.engine()).isEqualTo(DbEngine.MYSQL);
        assertThat(s.host()).isEqualTo("db.internal");
        assertThat(s.port()).isEqualTo(3307);
        assertThat(s.database()).isEqualTo("shop");
        assertThat(s.user()).isEqualTo("shop_rw");
        assertThat(s.passwordOrNull()).isEqualTo("s3cret");
    }

    @Test
    @DisplayName("DB_CONNECTION spellings map to engines: mariadb, pgsql, postgres, postgresql")
    void connectionSpellings() {
        assertThat(EnvConnections.fromEnv("DB_CONNECTION=mariadb\nDB_DATABASE=d")
                .orElseThrow().engine()).isEqualTo(DbEngine.MARIADB);
        assertThat(EnvConnections.fromEnv("DB_CONNECTION=pgsql\nDB_DATABASE=d")
                .orElseThrow().engine()).isEqualTo(DbEngine.POSTGRES);
        assertThat(EnvConnections.fromEnv("DB_CONNECTION=postgres\nDB_DATABASE=d")
                .orElseThrow().engine()).isEqualTo(DbEngine.POSTGRES);
        assertThat(EnvConnections.fromEnv("DB_CONNECTION=postgresql\nDB_DATABASE=d")
                .orElseThrow().engine()).isEqualTo(DbEngine.POSTGRES);
        assertThat(EnvConnections.fromEnv("DB_CONNECTION=MySQL\nDB_DATABASE=d")
                .orElseThrow().engine()).as("case-insensitive").isEqualTo(DbEngine.MYSQL);
    }

    @Test
    @DisplayName("SQLite: DB_DATABASE is the file path; no host, port -1")
    void sqliteShape() {
        EnvConnections.Suggestion s = EnvConnections.fromEnv("""
                DB_CONNECTION=sqlite
                DB_DATABASE=/var/app/database.sqlite
                """).orElseThrow();

        assertThat(s.engine()).isEqualTo(DbEngine.SQLITE);
        assertThat(s.database()).isEqualTo("/var/app/database.sqlite");
        assertThat(s.host()).isEmpty();
        assertThat(s.port()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Without DB_CONNECTION the engine falls back to the port: 3306 and 5432")
    void portFallback() {
        assertThat(EnvConnections.fromEnv("DB_HOST=h\nDB_PORT=3306\nDB_DATABASE=d")
                .orElseThrow().engine()).isEqualTo(DbEngine.MYSQL);
        assertThat(EnvConnections.fromEnv("DB_HOST=h\nDB_PORT=5432\nDB_DATABASE=d")
                .orElseThrow().engine()).isEqualTo(DbEngine.POSTGRES);
    }

    @Test
    @DisplayName("CONNECTION+DATABASE without a host suggests localhost and the default port")
    void hostAndPortDefaults() {
        EnvConnections.Suggestion s = EnvConnections.fromEnv(
                "DB_CONNECTION=pgsql\nDB_DATABASE=warehouse").orElseThrow();

        assertThat(s.host()).isEqualTo("localhost");
        assertThat(s.port()).isEqualTo(5432);
        assertThat(s.user()).isEmpty();
        assertThat(s.passwordOrNull()).isNull();
    }

    @Test
    @DisplayName("Too little signal yields empty — a suggestion is never fabricated")
    void insufficientSignal() {
        assertThat(EnvConnections.fromEnv(null)).isEmpty();
        assertThat(EnvConnections.fromEnv("")).isEmpty();
        assertThat(EnvConnections.fromEnv("APP_NAME=x\nMAIL_HOST=smtp")).isEmpty();
        // host alone: no database, no engine
        assertThat(EnvConnections.fromEnv("DB_HOST=h")).isEmpty();
        // connection alone: no database
        assertThat(EnvConnections.fromEnv("DB_CONNECTION=mysql")).isEmpty();
        // host+database but an underivable engine (unknown port)
        assertThat(EnvConnections.fromEnv("DB_HOST=h\nDB_PORT=1234\nDB_DATABASE=d")).isEmpty();
        assertThat(EnvConnections.fromEnv("DB_HOST=h\nDB_DATABASE=d")).isEmpty();
        // port+database without host or connection: not enough
        assertThat(EnvConnections.fromEnv("DB_PORT=5432\nDB_DATABASE=d")).isEmpty();
        // an unsupported engine named explicitly stays unsupported
        assertThat(EnvConnections.fromEnv("DB_CONNECTION=sqlsrv\nDB_DATABASE=d")).isEmpty();
    }

    // ---- .env parsing rules --------------------------------------------

    @Test
    @DisplayName("Quoted values are unwrapped: double quotes, single quotes, inner spaces kept")
    void quotedValues() {
        EnvConnections.Suggestion s = EnvConnections.fromEnv("""
                DB_CONNECTION="mysql"
                DB_DATABASE='my shop'
                DB_PASSWORD="p@ss word"
                """).orElseThrow();

        assertThat(s.database()).isEqualTo("my shop");
        assertThat(s.passwordOrNull()).isEqualTo("p@ss word");
    }

    @Test
    @DisplayName("Comments and blank lines are ignored; later duplicate keys win")
    void commentsAndDuplicates() {
        EnvConnections.Suggestion s = EnvConnections.fromEnv("""
                # database settings
                DB_CONNECTION=mysql

                DB_DATABASE=old_name
                DB_DATABASE=new_name
                """).orElseThrow();

        assertThat(s.database()).isEqualTo("new_name");
    }

    @Test
    @DisplayName("An export prefix and surrounding whitespace are tolerated")
    void exportPrefixAndWhitespace() {
        EnvConnections.Suggestion s = EnvConnections.fromEnv(
                "  export DB_CONNECTION = mysql \n export DB_DATABASE= shop ").orElseThrow();

        assertThat(s.engine()).isEqualTo(DbEngine.MYSQL);
        assertThat(s.database()).isEqualTo("shop");
    }

    // ---- DATABASE_URL ---------------------------------------------------

    @Test
    @DisplayName("DATABASE_URL mysql:// parses host, port, database, credentials")
    void databaseUrlMysql() {
        EnvConnections.Suggestion s = EnvConnections.fromEnv(
                "DATABASE_URL=mysql://root:hunter2@db.example.com:3307/shop").orElseThrow();

        assertThat(s.engine()).isEqualTo(DbEngine.MYSQL);
        assertThat(s.host()).isEqualTo("db.example.com");
        assertThat(s.port()).isEqualTo(3307);
        assertThat(s.database()).isEqualTo("shop");
        assertThat(s.user()).isEqualTo("root");
        assertThat(s.passwordOrNull()).isEqualTo("hunter2");
    }

    @Test
    @DisplayName("postgres:// and postgresql:// both map to POSTGRES; a query string rides along")
    void databaseUrlPostgres() {
        assertThat(EnvConnections.fromEnv("DATABASE_URL=postgres://u:p@h/db")
                .orElseThrow().engine()).isEqualTo(DbEngine.POSTGRES);
        EnvConnections.Suggestion s = EnvConnections.fromEnv(
                "DATABASE_URL=postgresql://u:p@h:5433/db?sslmode=require").orElseThrow();
        assertThat(s.engine()).isEqualTo(DbEngine.POSTGRES);
        assertThat(s.port()).isEqualTo(5433);
        assertThat(s.database()).isEqualTo("db");
    }

    @Test
    @DisplayName("A missing port in the URL means the engine's default")
    void databaseUrlDefaultPort() {
        assertThat(EnvConnections.fromEnv("DATABASE_URL=mysql://u:p@h/db")
                .orElseThrow().port()).isEqualTo(3306);
        assertThat(EnvConnections.fromEnv("DATABASE_URL=postgres://u:p@h/db")
                .orElseThrow().port()).isEqualTo(5432);
    }

    @Test
    @DisplayName("User and password are URL-decoded")
    void databaseUrlDecoding() {
        EnvConnections.Suggestion s = EnvConnections.fromEnv(
                "DATABASE_URL=postgres://ana%40corp:p%40ss%2Fword@h/db").orElseThrow();

        assertThat(s.user()).isEqualTo("ana@corp");
        assertThat(s.passwordOrNull()).isEqualTo("p@ss/word");
    }

    @Test
    @DisplayName("A URL without credentials suggests an empty user and a null password")
    void databaseUrlNoCredentials() {
        EnvConnections.Suggestion s = EnvConnections.fromEnv(
                "DATABASE_URL=postgres://h:5432/db").orElseThrow();

        assertThat(s.user()).isEmpty();
        assertThat(s.passwordOrNull()).isNull();
    }

    @Test
    @DisplayName("Unusable URLs yield empty: wrong scheme, no database, no host, garbage")
    void databaseUrlRejects() {
        assertThat(EnvConnections.fromEnv("DATABASE_URL=redis://localhost:6379")).isEmpty();
        assertThat(EnvConnections.fromEnv("DATABASE_URL=mysql://host:3306")).isEmpty();
        assertThat(EnvConnections.fromEnv("DATABASE_URL=mysql://host:3306/")).isEmpty();
        assertThat(EnvConnections.fromEnv("DATABASE_URL=not a url at all")).isEmpty();
        assertThat(EnvConnections.fromEnv("DATABASE_URL=")).isEmpty();
    }

    @Test
    @DisplayName("DB_* wins over DATABASE_URL when both carry a full connection")
    void dbFamilyWins() {
        EnvConnections.Suggestion s = EnvConnections.fromEnv("""
                DB_CONNECTION=pgsql
                DB_HOST=pg.internal
                DB_DATABASE=warehouse
                DATABASE_URL=mysql://root:x@other:3306/ignored
                """).orElseThrow();

        assertThat(s.engine()).isEqualTo(DbEngine.POSTGRES);
        assertThat(s.host()).isEqualTo("pg.internal");
        assertThat(s.database()).isEqualTo("warehouse");
    }

    @Test
    @DisplayName("When the DB_* family is too thin, DATABASE_URL steps in")
    void databaseUrlAsFallback() {
        EnvConnections.Suggestion s = EnvConnections.fromEnv("""
                DB_HOST=only-a-host
                DATABASE_URL=mysql://u:p@h:3306/real_db
                """).orElseThrow();

        assertThat(s.engine()).isEqualTo(DbEngine.MYSQL);
        assertThat(s.database()).isEqualTo("real_db");
    }

    // ---- secrecy ---------------------------------------------------------

    @Test
    @DisplayName("A Suggestion's toString never shows the password")
    void toStringRedactsPassword() {
        EnvConnections.Suggestion s = EnvConnections.fromEnv("""
                DB_CONNECTION=mysql
                DB_DATABASE=shop
                DB_PASSWORD=hunter2-super-secret
                """).orElseThrow();

        assertThat(s.toString()).doesNotContain("hunter2-super-secret");
        assertThat(s.toString()).contains("shop");
        // and a null password prints as null, not as a fake redaction
        EnvConnections.Suggestion noPass = EnvConnections.fromEnv(
                "DB_CONNECTION=mysql\nDB_DATABASE=shop").orElseThrow();
        assertThat(noPass.toString()).contains("passwordOrNull=null");
    }
}
