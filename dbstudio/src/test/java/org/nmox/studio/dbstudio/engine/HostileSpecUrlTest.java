package org.nmox.studio.dbstudio.engine;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A connection's host and database name are a stranger's text: a
 * {@code git clone} brings them in {@code .nmoxdb.json}, a {@code .env}
 * offers them. The driver URL is built from them by concatenation, and
 * 3.9.0 says of that URL that it "always states its mode". This class
 * holds the product to it against the drivers the build ships.
 *
 * <p>Nothing here dials: the refusals answer before any lookup (the hosts
 * end in {@code .invalid}), the driver facts come from the drivers' own
 * URL parsers, and the one live connection is a SQLite file in a temp dir.
 */
class HostileSpecUrlTest {

    private static ConnectionSpec spec(DbEngine engine, String host, String database, boolean secure) {
        return new ConnectionSpec("id", "t", engine, host, engine.defaultPort(), database, "u", null, secure);
    }

    private static Properties mysqlProps() {
        return new DbClient(spec(DbEngine.MYSQL, "h", "d", false), "pw".toCharArray()).credentials();
    }

    @Test
    @DisplayName("measured: both drivers read options from the first ?, and a URL option beats the Properties it was refused in")
    void whatTheShippedDriversDoWithANameThatHoldsAQuestionMark() throws Exception {
        // the URLs 3.9.0 built for these database names, written out by hand
        org.mariadb.jdbc.Configuration keyFetch = org.mariadb.jdbc.Configuration.parse(
                "jdbc:mariadb://db.example.invalid:3306/shop?allowPublicKeyRetrieval=true&x="
                + "?connectTimeout=5000&sslMode=disable", mysqlProps());
        assertThat(keyFetch.allowPublicKeyRetrieval())
                .as("a REMOTE plain connection with the key fetch on: the option 3.9.0 allows on the loopback only")
                .isTrue();
        org.mariadb.jdbc.Configuration infile = org.mariadb.jdbc.Configuration.parse(
                "jdbc:mariadb://db.example.invalid:3306/shop?allowLocalInfile=true&x="
                + "?connectTimeout=5000&sslMode=disable", mysqlProps());
        assertThat(infile.allowLocalInfile())
                .as("LOAD DATA LOCAL INFILE back on: the URL beats the Properties that refuse it (ledger 54 L3)")
                .isTrue();
        // a repeated option: the LAST one wins, which is why the stated sslMode itself held
        assertThat(org.mariadb.jdbc.Configuration.parse(
                "jdbc:mariadb://h:3306/d?sslMode=disable&sslMode=verify-full").sslMode())
                .isEqualTo(org.mariadb.jdbc.export.SslMode.VERIFY_FULL);
        assertThat(org.mariadb.jdbc.Configuration.parse(
                "jdbc:mariadb://h:3306/d?sslMode=verify-full&sslMode=disable").sslMode())
                .isEqualTo(org.mariadb.jdbc.export.SslMode.DISABLE);

        Properties pg = org.postgresql.Driver.parseURL(
                "jdbc:postgresql://db.internal.invalid:5432/shop?PGHOST=elsewhere.invalid&x=?connectTimeout=5", null);
        assertThat(pg.getProperty("PGHOST"))
                .as("PostgreSQL: the database field moved the connection to a host the dialog never showed")
                .isEqualTo("elsewhere.invalid");
        assertThat(org.postgresql.Driver.parseURL(
                "jdbc:postgresql://h:5432/d?sslmode=verify-full&sslmode=disable", null).getProperty("sslmode"))
                .as("pgJDBC: the last one wins too").isEqualTo("disable");
    }

    @Test
    @DisplayName("a database name holding ? is refused in words before anything is dialed, on every JDBC server engine")
    void aDatabaseNameCannotCarryDriverOptions() {
        for (DbEngine engine : new DbEngine[]{DbEngine.MYSQL, DbEngine.MARIADB, DbEngine.POSTGRES}) {
            for (String database : new String[]{
                "shop?allowPublicKeyRetrieval=true&x=", "shop?allowLocalInfile=true&x=",
                "shop?PGHOST=elsewhere.invalid&x=", "shop?sslfactory=org.postgresql.ssl.NonValidatingFactory&x=",
                "shop?socketFactory=some.Class&x=", "shop?serverSslCert=evil.pem&x=", "shop?"}) {
                ConnectionSpec s = spec(engine, "db.example.invalid", database, false);
                assertThat(DbClient.carriesDriverOptions(s)).as("%s %s", engine, database).isTrue();
                try (DbClient client = new DbClient(s, "pw".toCharArray())) {
                    assertThat(client.test()).as("Test, %s %s", engine, database)
                            .isEqualTo(Bundle.DbClient_carriesOptions());
                    assertThat(client.open()).as("Connect, %s %s", engine, database)
                            .isEqualTo(Bundle.DbClient_carriesOptions());
                    assertThat(client.isOpen()).isFalse();
                    List<QueryResult> run = client.runScript("SELECT 1", 10);
                    assertThat(run).hasSize(1);
                    assertThat(run.get(0).error()).contains(Bundle.DbClient_carriesOptions());
                }
                assertThatThrownBy(() -> DbClient.connectionUrl(s))
                        .as("and no URL is built from it at all").isInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    @Test
    @DisplayName("a host holding URL syntax is refused the same way")
    void aHostCannotCarryDriverOptions() {
        for (DbEngine engine : new DbEngine[]{DbEngine.MYSQL, DbEngine.MARIADB, DbEngine.POSTGRES}) {
            for (String host : new String[]{
                "elsewhere.invalid/x?sslMode=disable&y=", "elsewhere.invalid?sslMode=disable&y=",
                "127.0.0.1/x?a=", "address=(host=elsewhere.invalid)(port=3306)", "h#x", "h&x=1",
                "h\\x", "db example.invalid", "h\tx", "h\nx"}) {
                ConnectionSpec s = spec(engine, host, "shop", true);
                assertThat(DbClient.carriesDriverOptions(s)).as("%s %s", engine, host).isTrue();
                try (DbClient client = new DbClient(s, "pw".toCharArray())) {
                    assertThat(client.test()).as("%s %s", engine, host).isEqualTo(Bundle.DbClient_carriesOptions());
                    assertThat(client.open()).as("%s %s", engine, host).isEqualTo(Bundle.DbClient_carriesOptions());
                }
            }
        }
    }

    @Test
    @DisplayName("ordinary names are names: nothing a real host or database is called is refused, and the mode is still stated")
    void ordinaryNamesAreLeftAlone() throws Exception {
        for (String host : new String[]{"localhost", "db.example.com", "my_host", "db-1.internal", "10.0.0.5",
            "::1", "[::1]", "fe80::1", "h1,h2", "bücher.example", ""}) {
            for (String database : new String[]{"shop", "my shop", "shop-2026", "shop.v2", "shop#1", "shop;x",
                "a&b", "a=b", "données", "", null}) {
                for (DbEngine engine : new DbEngine[]{DbEngine.MYSQL, DbEngine.MARIADB, DbEngine.POSTGRES}) {
                    assertThat(DbClient.carriesDriverOptions(spec(engine, host, database, false)))
                            .as("%s host=%s database=%s", engine, host, database).isFalse();
                }
            }
        }
        // what those characters do in the driver's hands: they stay inside the name
        org.mariadb.jdbc.Configuration conf = org.mariadb.jdbc.Configuration.parse(
                DbClient.connectionUrl(spec(DbEngine.MARIADB, "db.example.com", "shop;sslMode=disable&a=b#1", true)),
                mysqlProps());
        assertThat(conf.database()).isEqualTo("shop;sslMode=disable&a=b#1");
        assertThat(conf.sslMode()).isEqualTo(org.mariadb.jdbc.export.SslMode.VERIFY_FULL);
        assertThat(conf.allowLocalInfile()).isFalse();
        assertThat(conf.allowPublicKeyRetrieval()).isFalse();
        Properties pg = org.postgresql.Driver.parseURL(
                DbClient.connectionUrl(spec(DbEngine.POSTGRES, "db.example.com", "shop;sslmode=disable&a=b#1", false)), null);
        assertThat(pg.getProperty("PGDBNAME")).isEqualTo("shop;sslmode=disable&a=b#1");
        assertThat(pg.getProperty("PGHOST")).isEqualTo("db.example.com");
        assertThat(pg.getProperty("sslmode")).isNull();
        // SQLite has no host and its path is not this rule's business
        assertThat(DbClient.carriesDriverOptions(new ConnectionSpec("id", "t", DbEngine.SQLITE,
                "", -1, "", "", "/tmp/a.db?mode=ro"))).isFalse();
    }

    @Test
    @DisplayName("a SQLite path cannot switch extension loading on: load_extension() stays unauthorized")
    void aSqlitePathCannotEnableExtensionLoading(@TempDir Path dir) {
        for (String path : new String[]{
            dir.resolve("plain.db").toString(),
            dir.resolve("opt.db") + "?enable_load_extension=true",
            "file:" + dir.resolve("uri.db") + "?enable_load_extension=true"}) {
            ConnectionSpec s = new ConnectionSpec("id", "t", DbEngine.SQLITE, "", -1, "", "", path);
            try (DbClient client = new DbClient(s, null)) {
                assertThat(client.open()).as(path).isNull();
                List<QueryResult> run = client.runScript(
                        "SELECT load_extension('" + dir.resolve("no-such-library") + "')", 10);
                assertThat(run).hasSize(1);
                assertThat(run.get(0).error()).as("SQLite refuses the function itself, it never looks for the file: %s", path)
                        .contains("not authorized");
                // and the connection is otherwise the one it was
                assertThat(client.runScript("SELECT 1", 10).get(0).isError()).isFalse();
            }
        }
    }
}
