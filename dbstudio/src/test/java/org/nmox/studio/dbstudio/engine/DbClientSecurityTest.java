package org.nmox.studio.dbstudio.engine;

import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB Studio connection hardening (ledger 54 L3 + L5): MySQL/MariaDB
 * connects explicitly refuse LOAD DATA LOCAL INFILE so a malicious server
 * can't read a client file, and close() zeroes the in-memory password
 * clone.
 */
class DbClientSecurityTest {

    private static ConnectionSpec spec(DbEngine engine) {
        return new ConnectionSpec("id", "t", engine, "localhost",
                engine.defaultPort(), "db", "user", null);
    }

    @Test
    @DisplayName("MySQL/MariaDB connects refuse local-infile; other engines don't set it")
    void localInfileRefusedForMysqlFamily() {
        for (DbEngine e : new DbEngine[]{DbEngine.MYSQL, DbEngine.MARIADB}) {
            Properties p = new DbClient(spec(e), "pw".toCharArray()).credentials();
            assertThat(p.getProperty("allowLoadLocalInfile"))
                    .as("%s refuses LOAD DATA LOCAL INFILE", e).isEqualTo("false");
            assertThat(p.getProperty("allowLocalInfile")).isEqualTo("false");
        }
        // PostgreSQL isn't vulnerable to this MySQL-ism — no need to set it
        Properties pg = new DbClient(spec(DbEngine.POSTGRES), "pw".toCharArray()).credentials();
        assertThat(pg.getProperty("allowLocalInfile")).isNull();
    }

    @Test
    @DisplayName("close() zeroes the password clone")
    void closeZeroesPassword() {
        char[] secret = "hunter2".toCharArray();
        DbClient client = new DbClient(spec(DbEngine.POSTGRES), secret);
        // the caller's array is untouched (the client cloned it)
        assertThat(secret).containsExactly("hunter2".toCharArray());
        // before close the client's clone still carries the secret
        assertThat(client.credentials().getProperty("password")).isEqualTo("hunter2");
        client.close();
        // after close the clone is wiped — credentials now yields all-NUL
        String afterClose = client.credentials().getProperty("password");
        assertThat(afterClose).isEqualTo("\0\0\0\0\0\0\0");
        // the caller's own array was never our concern to wipe, still intact
        assertThat(secret).containsExactly("hunter2".toCharArray());
    }
}
