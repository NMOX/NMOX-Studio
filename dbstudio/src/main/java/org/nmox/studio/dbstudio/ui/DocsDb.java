package org.nmox.studio.dbstudio.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.nmox.studio.core.spi.DocsScene;
import org.nmox.studio.core.util.DocsFixtures;
import org.nmox.studio.dbstudio.io.DbWorkspaceIO;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Stages the DB Studio picture (v2.163.0): a real SQLite database holding
 * this language's own customers, connected, queried, and answering in a
 * grid.
 *
 * <p>The database is REAL — built here through the driver DB Studio already
 * bundles, so the grid shows what the engine returned rather than a painted
 * imitation, and the row count, timing and column names in the picture are
 * all true. No external tool is needed: an earlier plan had the forge script
 * shell out to {@code sqlite3}, which would have made the picture depend on
 * whatever happened to be installed.
 *
 * <p>The SQL is the same in every language on purpose — keywords and
 * identifiers are code a developer retypes (the v2.104.0 rule). The ROWS are
 * the reader's world: their own names and cities.
 */
@ServiceProvider(service = DocsScene.class)
public final class DocsDb implements DocsScene {

    /** The scene's name, as its picture is named. */
    public static final String ID = "db-studio";

    /** The database file, beside the project it belongs to. */
    static final String DB_FILE = "shop.db";
    /** The one table the query reads. */
    static final String TABLE = "customers";

    private String query = "";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public File stage(File home, String fixtures, String lang) throws IOException {
        query = DocsFixtures.text(fixtures, lang, "db", "query");
        File dir = DocsFixtures.projectDir(home);
        Files.createDirectories(dir.toPath());
        File db = new File(dir, DB_FILE);
        Files.deleteIfExists(db.toPath());

        try {
            Class.forName(DbEngine.SQLITE.driverClass());
        } catch (ClassNotFoundException absent) {
            throw new IOException("the bundled SQLite driver is not on the classpath", absent);
        }
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath())) {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("CREATE TABLE " + TABLE + " (name TEXT, city TEXT, orders INTEGER)");
            }
            try (PreparedStatement insert = c.prepareStatement(
                    "INSERT INTO " + TABLE + " (name, city, orders) VALUES (?, ?, ?)")) {
                for (List<String> row : DocsFixtures.rows(fixtures, lang, "db", "rows")) {
                    if (row.size() < 3) {
                        continue;
                    }
                    insert.setString(1, row.get(0));
                    insert.setString(2, row.get(1));
                    insert.setInt(3, Integer.parseInt(row.get(2)));
                    insert.executeUpdate();
                }
            }
        } catch (SQLException | NumberFormatException broken) {
            throw new IOException("could not build the docs database", broken);
        }

        ConnectionSpec spec = new ConnectionSpec(java.util.UUID.randomUUID().toString(),
                DocsFixtures.PROJECT, DbEngine.SQLITE, "", -1, "", "", db.getAbsolutePath());
        DbWorkspaceIO.save(dir, List.of(spec));
        return dir;
    }

    @Override
    public void arrange() {
        TopComponent tc = WindowManager.getDefault().findTopComponent("DbStudioTopComponent");
        if (tc instanceof DbStudioTopComponent db && !query.isEmpty()) {
            db.docsStageRun(query);
        }
    }
}
