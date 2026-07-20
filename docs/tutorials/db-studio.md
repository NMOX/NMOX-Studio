# Tutorial: DB Studio

DB Studio is a database suite for SQLite, PostgreSQL, MySQL/MariaDB,
MongoDB, and CouchDB — bundled drivers, a kind-aware console, and result
grids you can edit in place. This tutorial uses SQLite because it needs
no server.

<!-- screenshot: DB Studio with a connection tree, a SELECT result grid, and a cell being edited -->

## Open it

`⌥⌘7`, or the **DB Studio** tab.

## Steps

1. **Create a SQLite connection.** Click **New Connection**, choose
   **SQLite**, and pick a file path (a save-style chooser lets you make a
   new `.db`). It appears in the connection tree.

2. **Run some SQL.** In the console type and run:

   ```sql
   CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, active BOOLEAN);
   INSERT INTO users (name, active) VALUES ('Ada', 1), ('Bob', 0);
   SELECT * FROM users;
   ```

   Each statement gets its own result grid below, with timing.

3. **Edit a row in the grid.** Double-click Bob's `name` cell, change it,
   and press **Apply**. DB Studio only allows in-grid edits when it can
   build a safe single-row `UPDATE` (single table, primary key present) —
   it shows you the exact SQL before it runs, then re-queries for truth.
   If a row can't be edited safely it tells you why.

4. **Export.** Right-click any grid → **Export CSV / JSON**. CSV export
   neutralizes spreadsheet formula-injection automatically.

5. **EXPLAIN a query.** Select a `SELECT` and press **EXPLAIN** for the
   engine-native query plan.

## What you just learned

- Passwords are OS-keychain only, never in `.nmoxdb.json`.
- The console is kind-aware: SQL for SQL engines, a JSON document
  console for MongoDB/CouchDB.
- History and saved queries persist per project; `.env` files offer
  their `DATABASE_URL`/`DB_*` connections automatically.

## Next

- Running a database in Docker? DB Studio offers a connection for it —
  see the [Docker panel](docker-panel.md).
