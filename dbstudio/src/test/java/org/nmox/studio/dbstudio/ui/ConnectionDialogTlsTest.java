package org.nmox.studio.dbstudio.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The connection dialog's <i>Use TLS</i> box reaches MySQL and MariaDB
 * (3.9.0) beside CouchDB (v1.122.0), says what it means for each, and
 * stays out of the way for the engines that carry their own TLS story.
 * Headless Swing: the panel is built, never shown.
 */
class ConnectionDialogTlsTest {

    private static ConnectionSpec spec(DbEngine engine, boolean secure) {
        return new ConnectionSpec("id", "t", engine, "db.example", engine.defaultPort(),
                "appdb", "u", "", secure);
    }

    @Test
    @DisplayName("the box shows for CouchDB, MySQL and MariaDB and hides for PostgreSQL, SQLite and MongoDB")
    void visibilityFollowsOffersTls() {
        ConnectionDialog d = new ConnectionDialog(null);
        for (DbEngine e : DbEngine.values()) {
            d.selectEngine(e);
            assertThat(d.tlsBox().isVisible()).as("Use TLS shown for %s", e).isEqualTo(e.offersTls());
        }
        assertThat(DbEngine.MYSQL.offersTls()).isTrue();
        assertThat(DbEngine.MARIADB.offersTls()).isTrue();
        assertThat(DbEngine.COUCHDB.offersTls()).isTrue();
        assertThat(DbEngine.POSTGRES.offersTls()).isFalse();
        assertThat(DbEngine.SQLITE.offersTls()).isFalse();
        assertThat(DbEngine.MONGODB.offersTls()).isFalse();
    }

    @Test
    @DisplayName("the box says https for CouchDB and names the certificate check for MySQL/MariaDB")
    void labelNamesWhatTheFlagDoes() {
        ConnectionDialog d = new ConnectionDialog(null);
        d.selectEngine(DbEngine.COUCHDB);
        assertThat(d.tlsBox().getText()).isEqualTo(Bundle.ConnectionDialog_useTls()).contains("https");
        assertThat(d.tlsBox().getToolTipText()).isEqualTo(Bundle.ConnectionDialog_tlsTooltip());
        d.selectEngine(DbEngine.MARIADB);
        assertThat(d.tlsBox().getText()).isEqualTo(Bundle.ConnectionDialog_useTlsSql()).doesNotContain("https");
        assertThat(d.tlsBox().getToolTipText()).isEqualTo(Bundle.ConnectionDialog_tlsSqlTooltip());
    }

    @Test
    @DisplayName("a ticked box reaches the spec for MySQL/MariaDB and is dropped for an engine that has no use for it")
    void specCarriesTheFlagOnlyWhereItMeansSomething() {
        ConnectionDialog d = new ConnectionDialog(spec(DbEngine.MYSQL, true));
        assertThat(d.tlsBox().isSelected()).as("an existing secure spec arrives ticked").isTrue();
        assertThat(d.specForTest().secure()).isTrue();
        d.selectEngine(DbEngine.POSTGRES);
        assertThat(d.specForTest().secure()).as("PostgreSQL ignores the flag, so the spec never carries it").isFalse();
        d.selectEngine(DbEngine.MARIADB);
        assertThat(d.specForTest().secure()).isTrue();
        d.tlsBox().setSelected(false);
        assertThat(d.specForTest().secure()).isFalse();
    }
}
