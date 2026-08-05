package org.nmox.studio.dbstudio.model;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The engine-noun law (v1.274.0, the Docker-persona walk): dbstudio
 * calls tree entries "containers" internally, and that word leaked
 * verbatim into the connect status — beside a connection that was
 * literally offered FROM a Docker container, "Connected: walk-postgres
 * (docker) — 0 containers" reads as a Docker statement, not a schema
 * one. Each engine now names what it actually lists.
 */
class DbEngineNounTest {

    @Test
    @DisplayName("each engine names what its tree lists, singular and plural")
    void enginesSpeakTheirOwnNoun() {
        assertThat(DbEngine.POSTGRES.containerNoun(0)).isEqualTo("tables");
        assertThat(DbEngine.MYSQL.containerNoun(1)).isEqualTo("table");
        assertThat(DbEngine.MARIADB.containerNoun(3)).isEqualTo("tables");
        assertThat(DbEngine.SQLITE.containerNoun(2)).isEqualTo("tables");
        assertThat(DbEngine.MONGODB.containerNoun(1)).isEqualTo("collection");
        assertThat(DbEngine.MONGODB.containerNoun(5)).isEqualTo("collections");
        assertThat(DbEngine.COUCHDB.containerNoun(1)).isEqualTo("database");
        assertThat(DbEngine.COUCHDB.containerNoun(2)).isEqualTo("databases");
    }

    @Test
    @DisplayName("the connect status uses the engine noun, never the internal word")
    void connectStatusUsesTheNoun() throws Exception {
        String src = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "dbstudio", "ui", "DbStudioTopComponent.java"),
                StandardCharsets.UTF_8);
        assertThat(src)
                .as("the status line asks the engine for its noun")
                .contains("containerNoun(");
        assertThat(src)
                .as("the internal word must not appear as a user-facing"
                        + " literal — it reads as Docker beside a"
                        + " container-sourced connection")
                .doesNotContain("\" containers\"")
                .doesNotContain("\" container\"");
    }
}
