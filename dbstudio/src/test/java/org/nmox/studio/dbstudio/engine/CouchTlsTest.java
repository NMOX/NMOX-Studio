package org.nmox.studio.dbstudio.engine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.io.DbWorkspaceIO;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 54 L2 closed: CouchDB can opt into TLS via the spec's
 * {@code secure} flag — round-tripped through the workspace file, absent
 * in old files means the cleartext behavior those workspaces always had.
 */
class CouchTlsTest {

    private static ConnectionSpec couch(boolean secure) {
        return new ConnectionSpec("c1", "docs", DbEngine.COUCHDB,
                "couch.example", 6984, "invoices", "admin", "", secure);
    }

    @Test
    @DisplayName("The secure flag flips CouchBackend's scheme to https")
    void secureFlipsScheme() {
        assertThat(new CouchBackend(couch(true), null).baseUrl())
                .isEqualTo("https://couch.example:6984");
        assertThat(new CouchBackend(couch(false), null).baseUrl())
                .isEqualTo("http://couch.example:6984");
    }

    @Test
    @DisplayName("secure round-trips through .nmoxdb.json")
    void secureRoundTrips(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        File project = dir.toFile();
        DbWorkspaceIO.save(project, new DbWorkspaceIO.Workspace(
                List.of(couch(true)), List.of(), List.of()));
        DbWorkspaceIO.Workspace loaded =
                DbWorkspaceIO.loadWorkspaceGuarded(project).workspace();
        assertThat(loaded.connections()).hasSize(1);
        assertThat(loaded.connections().get(0).secure()).isTrue();
    }

    @Test
    @DisplayName("A pre-v1.122.0 file without the key loads as cleartext — no migration needed")
    void oldFileDefaultsToCleartext(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(DbWorkspaceIO.FILENAME), """
                {"version":1,"connections":[{"id":"old","name":"legacy",
                 "engine":"COUCHDB","host":"localhost","port":5984,
                 "database":"db","user":"u","filePath":""}],
                 "history":[],"saved":[]}
                """);
        DbWorkspaceIO.Workspace loaded =
                DbWorkspaceIO.loadWorkspaceGuarded(dir.toFile()).workspace();
        assertThat(loaded.connections()).hasSize(1);
        assertThat(loaded.connections().get(0).secure())
                .as("absent key = the cleartext behavior the file always had")
                .isFalse();
        // and the 8-arg compat constructor defaults to cleartext too
        assertThat(new ConnectionSpec("i", "n", DbEngine.COUCHDB,
                "h", 5984, "d", "u", "").secure()).isFalse();
    }
}
