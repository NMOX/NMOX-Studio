/**
 * DB Studio's database engines behind one seam:
 * {@code DbBackend.create()} dispatches to {@code DbClient} (JDBC —
 * SQLite/PostgreSQL/MySQL/MariaDB with bundled drivers),
 * {@code MongoBackend} (Extended-JSON runCommand) or
 * {@code CouchBackend} (pure HTTP Mango), so the console and grids
 * never care which engine answers.
 *
 * <p>This package is a catalog of defensive-SQL craft worth reading
 * slowly: identifier quoting per dialect (backslash handling differs
 * on MySQL — v1.101.0), 64k LOB caps so a giant cell cannot OOM the
 * grid, {@code allowLoadLocalInfile=false} so a hostile server cannot
 * read client files, EXPLAIN refusing multi-statement text, and
 * passwords zeroed on close. Keyring-only credentials throughout.
 */
package org.nmox.studio.dbstudio.engine;
