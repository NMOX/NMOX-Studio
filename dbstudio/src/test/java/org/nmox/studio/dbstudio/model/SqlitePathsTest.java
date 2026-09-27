package org.nmox.studio.dbstudio.model;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A SQLite path in the shared {@code .nmoxdb.json} names the same file on
 * every clone (3.4). Before, the chooser stored {@code /Users/alice/…} and a
 * relative path resolved against the IDE's working directory, so a
 * teammate got {@code [SQLITE_CANTOPEN]} — or a fresh EMPTY database.
 */
class SqlitePathsTest {

    @Test
    @DisplayName("a file inside the project is stored relative to it, with / separators")
    void insideIsStoredRelative(@TempDir File project) {
        String chosen = new File(new File(project, "data"), "dev.db").getAbsolutePath();
        assertThat(SqlitePaths.stored(project, chosen)).isEqualTo("data/dev.db");
    }

    @Test
    @DisplayName("a file outside the project, and a relative one, are stored as chosen")
    void outsideIsStoredAsIs(@TempDir File project, @TempDir File elsewhere) {
        String outside = new File(elsewhere, "shared.db").getAbsolutePath();
        assertThat(SqlitePaths.stored(project, outside)).isEqualTo(outside);
        assertThat(SqlitePaths.stored(project, "data/dev.db")).isEqualTo("data/dev.db");
        assertThat(SqlitePaths.stored(project, ":memory:")).isEqualTo(":memory:");
    }

    @Test
    @DisplayName("a relative path opens against the PROJECT, not the IDE's working directory")
    void relativeResolvesAgainstTheProject(@TempDir File project) {
        String opened = SqlitePaths.resolved(project, "data/dev.db");
        assertThat(new File(opened)).isEqualTo(
                new File(new File(project.getAbsoluteFile(), "data"), "dev.db"));
    }

    @Test
    @DisplayName("an absolute path an older version stored still opens exactly as stored")
    void absoluteStillLoads(@TempDir File project) {
        String old = new File(project, "legacy.db").getAbsolutePath();
        assertThat(SqlitePaths.resolved(project, old)).isEqualTo(old);
        assertThat(SqlitePaths.resolved(project, "file:x.db?mode=ro")).isEqualTo("file:x.db?mode=ro");
    }

    @Test
    @DisplayName("only SQLite specs are rewritten, and the round trip lands on the chosen file")
    void specsRoundTrip(@TempDir File project) {
        String chosen = new File(project, "shop.db").getAbsolutePath();
        ConnectionSpec spec = new ConnectionSpec("a", "shop", DbEngine.SQLITE, "", -1, "", "", chosen);
        ConnectionSpec stored = SqlitePaths.forStoring(project, spec);
        assertThat(stored.filePath()).isEqualTo("shop.db");
        assertThat(new File(SqlitePaths.forOpening(project, stored).filePath()))
                .isEqualTo(new File(chosen));

        ConnectionSpec pg = new ConnectionSpec("b", "pg", DbEngine.POSTGRES, "h", 5432, "d", "u", "rel");
        assertThat(SqlitePaths.forOpening(project, pg)).isSameAs(pg);
    }
}
