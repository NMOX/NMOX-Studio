package org.nmox.studio.dbstudio.ui;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.DocsFixtures;
import org.nmox.studio.dbstudio.io.DbWorkspaceIO;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The database every language's DB Studio picture queries. The grid in that
 * picture is what SQLite returned, so the database is read back here through
 * a real JDBC connection and the connection through the product's own loader.
 */
class DocsDbTest {

    private static final String FIXTURES = """
            {"en": {"db": {
               "query": "SELECT name, city, orders FROM customers ORDER BY orders DESC;",
               "rows": [["Ada Marsh", "Bristol", "412"], ["Noor Haddad", "Leeds", "377"],
                        ["Tom Rivera", "Cardiff", "298"], ["Ines Duarte", "Belfast", "265"]]}}}
            """;

    @Test
    @DisplayName("the staged SQLite database answers the fixture's own query with the fixture's rows, in order")
    void databaseAnswersTheQuery(@TempDir Path home) throws Exception {
        File dir = new DocsDb().stage(home.toFile(), FIXTURES, "en");
        assertThat(dir).isEqualTo(DocsFixtures.projectDir(home.toFile()));
        File db = new File(dir, DocsDb.DB_FILE);
        assertThat(db).isFile();

        List<String> rows = new ArrayList<>();
        Class.forName(DbEngine.SQLITE.driverClass());
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(DocsFixtures.text(FIXTURES, "en", "db", "query"))) {
            while (r.next()) {
                rows.add(r.getString(1) + "|" + r.getString(2) + "|" + r.getInt(3));
            }
        }
        assertThat(rows).containsExactly("Ada Marsh|Bristol|412", "Noor Haddad|Leeds|377",
                "Tom Rivera|Cardiff|298", "Ines Duarte|Belfast|265");
    }

    @Test
    @DisplayName("the saved connection is one SQLite spec pointing at that database")
    void workspaceNamesTheDatabase(@TempDir Path home) throws Exception {
        File dir = new DocsDb().stage(home.toFile(), FIXTURES, "en");
        List<ConnectionSpec> specs = DbWorkspaceIO.load(dir);
        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).engine()).isEqualTo(DbEngine.SQLITE);
        assertThat(new File(specs.get(0).filePath())).isEqualTo(new File(dir, DocsDb.DB_FILE).getAbsoluteFile());
    }

    @Test
    @DisplayName("staging twice replaces the database rather than doubling its rows")
    void restagingDoesNotDuplicateRows(@TempDir Path home) throws Exception {
        DocsDb scene = new DocsDb();
        scene.stage(home.toFile(), FIXTURES, "en");
        File dir = scene.stage(home.toFile(), FIXTURES, "en");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + new File(dir, DocsDb.DB_FILE));
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + DocsDb.TABLE)) {
            assertThat(r.getInt(1)).isEqualTo(4);
        }
    }

    @Test
    @DisplayName("arranging with no DB Studio window open does nothing and throws nothing")
    void arrangeWithoutAWindowIsQuiet() {
        new DocsDb().arrange();
    }
}
