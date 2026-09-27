package org.nmox.studio.dbstudio.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A teammate's clone holds none of this person's passwords (3.4): a
 * connection that names a user and has none stored here says so, in front
 * of the driver's own words, instead of reading like a wrong password.
 */
class MissingPasswordTest {

    private static ConnectionSpec pg(String user) {
        // port 1 on loopback refuses at once: a real driver failure, fast
        return new ConnectionSpec("id", "orders", DbEngine.POSTGRES, "127.0.0.1", 1, "app", user, "");
    }

    @Test
    @DisplayName("no stored password for a user-named connection leads the driver's error")
    void noneStoredSpeaks() {
        String said = MissingPassword.explain(pg("app"), null, "password authentication failed");
        assertThat(said).contains("No password").contains("keychain")
                .endsWith("password authentication failed");
        assertThat(MissingPassword.explain(pg("app"), new char[0], "x")).contains("No password");
    }

    @Test
    @DisplayName("a stored password, no user, or a SQLite file leave the error as it is")
    void otherwiseUntouched() {
        assertThat(MissingPassword.explain(pg("app"), "s3cret".toCharArray(), "boom")).isEqualTo("boom");
        assertThat(MissingPassword.explain(pg(""), null, "boom")).isEqualTo("boom");
        ConnectionSpec sqlite = new ConnectionSpec("id", "f", DbEngine.SQLITE, "", -1, "", "u", "x.db");
        assertThat(MissingPassword.explain(sqlite, null, "boom")).isEqualTo("boom");
        assertThat(MissingPassword.explain(pg("app"), null, null)).as("success stays success").isNull();
    }

    @Test
    @DisplayName("the JDBC client says it when its connection fails")
    void jdbcClientSaysIt() {
        DbClient client = new DbClient(pg("app"), null);
        try {
            assertThat(client.open()).startsWith("No password");
            assertThat(client.test()).startsWith("No password");
        } finally {
            client.close();
        }
    }
}
