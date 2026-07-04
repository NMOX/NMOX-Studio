package org.nmox.studio.dbstudio.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * The engine catalog: URL recipes, default ports, driver classes and
 * the SQL/DOCUMENT kind split — the constants the whole access layer
 * hangs off.
 */
class DbEngineTest {

    private static ConnectionSpec server(DbEngine engine, int port) {
        return new ConnectionSpec("id-1", "n", engine, "db.example.com", port, "appdb", "app", "");
    }

    @Test
    @DisplayName("MySQL builds a jdbc:mariadb URL — the MariaDB driver speaks the MySQL protocol")
    void mysqlUrl() {
        assertThat(DbEngine.MYSQL.jdbcUrl(server(DbEngine.MYSQL, 3306)))
                .isEqualTo("jdbc:mariadb://db.example.com:3306/appdb");
    }

    @Test
    @DisplayName("MariaDB builds a jdbc:mariadb URL")
    void mariadbUrl() {
        assertThat(DbEngine.MARIADB.jdbcUrl(server(DbEngine.MARIADB, 3307)))
                .isEqualTo("jdbc:mariadb://db.example.com:3307/appdb");
    }

    @Test
    @DisplayName("PostgreSQL builds a jdbc:postgresql URL")
    void postgresUrl() {
        assertThat(DbEngine.POSTGRES.jdbcUrl(server(DbEngine.POSTGRES, 5432)))
                .isEqualTo("jdbc:postgresql://db.example.com:5432/appdb");
    }

    @Test
    @DisplayName("SQLite builds a jdbc:sqlite URL from the file path, ignoring host/port")
    void sqliteUrl() {
        ConnectionSpec spec = new ConnectionSpec("id-2", "local", DbEngine.SQLITE,
                "ignored", 9999, "ignored", "ignored", "/tmp/data/app.db");
        assertThat(DbEngine.SQLITE.jdbcUrl(spec)).isEqualTo("jdbc:sqlite:/tmp/data/app.db");
    }

    @Test
    @DisplayName("A spec port of <= 0 falls back to the engine default in the URL")
    void defaultPortFallback() {
        assertThat(DbEngine.MYSQL.jdbcUrl(server(DbEngine.MYSQL, 0)))
                .isEqualTo("jdbc:mariadb://db.example.com:3306/appdb");
        assertThat(DbEngine.POSTGRES.jdbcUrl(server(DbEngine.POSTGRES, -1)))
                .isEqualTo("jdbc:postgresql://db.example.com:5432/appdb");
    }

    @Test
    @DisplayName("Default ports: 3306/3306/5432, -1 for file-based SQLite, 27017/5984 for the document engines")
    void defaultPorts() {
        assertThat(DbEngine.MYSQL.defaultPort()).isEqualTo(3306);
        assertThat(DbEngine.MARIADB.defaultPort()).isEqualTo(3306);
        assertThat(DbEngine.POSTGRES.defaultPort()).isEqualTo(5432);
        assertThat(DbEngine.SQLITE.defaultPort()).isEqualTo(-1);
        assertThat(DbEngine.MONGODB.defaultPort()).isEqualTo(27017);
        assertThat(DbEngine.COUCHDB.defaultPort()).isEqualTo(5984);
    }

    @Test
    @DisplayName("Kinds: the four originals are SQL, MongoDB and CouchDB are DOCUMENT")
    void kinds() {
        assertThat(DbEngine.MYSQL.kind()).isEqualTo(DbEngine.Kind.SQL);
        assertThat(DbEngine.MARIADB.kind()).isEqualTo(DbEngine.Kind.SQL);
        assertThat(DbEngine.POSTGRES.kind()).isEqualTo(DbEngine.Kind.SQL);
        assertThat(DbEngine.SQLITE.kind()).isEqualTo(DbEngine.Kind.SQL);
        assertThat(DbEngine.MONGODB.kind()).isEqualTo(DbEngine.Kind.DOCUMENT);
        assertThat(DbEngine.COUCHDB.kind()).isEqualTo(DbEngine.Kind.DOCUMENT);
    }

    @Test
    @DisplayName("Document engines refuse jdbcUrl loudly — they don't do JDBC")
    void documentEnginesRefuseJdbcUrl() {
        assertThatIllegalStateException()
                .isThrownBy(() -> DbEngine.MONGODB.jdbcUrl(server(DbEngine.MONGODB, 27017)))
                .withMessageContaining("MongoDB")
                .withMessageContaining("no JDBC URL")
                .withMessageContaining("DbBackend.create");
        assertThatIllegalStateException()
                .isThrownBy(() -> DbEngine.COUCHDB.jdbcUrl(server(DbEngine.COUCHDB, 5984)))
                .withMessageContaining("CouchDB")
                .withMessageContaining("no JDBC URL");
    }

    @Test
    @DisplayName("Driver classes: one MariaDB driver serves MySQL and MariaDB; document engines have none")
    void driverClasses() {
        assertThat(DbEngine.MYSQL.driverClass()).isEqualTo("org.mariadb.jdbc.Driver");
        assertThat(DbEngine.MARIADB.driverClass()).isEqualTo("org.mariadb.jdbc.Driver");
        assertThat(DbEngine.POSTGRES.driverClass()).isEqualTo("org.postgresql.Driver");
        assertThat(DbEngine.SQLITE.driverClass()).isEqualTo("org.sqlite.JDBC");
        assertThat(DbEngine.MONGODB.driverClass()).as("no JDBC driver").isEmpty();
        assertThat(DbEngine.COUCHDB.driverClass()).as("no JDBC driver").isEmpty();
    }

    @Test
    @DisplayName("Every SQL engine's bundled driver class is actually on the classpath")
    void driversAreBundled() throws Exception {
        for (DbEngine engine : DbEngine.values()) {
            if (engine.kind() == DbEngine.Kind.SQL) {
                assertThat(Class.forName(engine.driverClass())).isNotNull();
            }
        }
    }

    @Test
    @DisplayName("Display names read like products, not enum constants")
    void displayNames() {
        assertThat(DbEngine.MYSQL.displayName()).isEqualTo("MySQL");
        assertThat(DbEngine.MARIADB.displayName()).isEqualTo("MariaDB");
        assertThat(DbEngine.POSTGRES.displayName()).isEqualTo("PostgreSQL");
        assertThat(DbEngine.SQLITE.displayName()).isEqualTo("SQLite");
        assertThat(DbEngine.MONGODB.displayName()).isEqualTo("MongoDB");
        assertThat(DbEngine.COUCHDB.displayName()).isEqualTo("CouchDB");
    }

    @Test
    @DisplayName("Null host/database degrade to empty strings in the URL, never 'null'")
    void nullFieldsDegrade() {
        ConnectionSpec nulls = new ConnectionSpec("id-3", "n", DbEngine.POSTGRES,
                null, 0, null, null, null);
        assertThat(DbEngine.POSTGRES.jdbcUrl(nulls)).isEqualTo("jdbc:postgresql://:5432/");
    }
}
