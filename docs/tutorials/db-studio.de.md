# Tutorial: Datenbank-Studio

<!-- languages -->
[English](db-studio.md) · [Español](db-studio.es.md) · [Français](db-studio.fr.md) · **Deutsch** · [Русский](db-studio.ru.md) · [Українська](db-studio.uk.md) · [Polski](db-studio.pl.md) · [Português (Brasil)](db-studio.pt.md) · [Bahasa Indonesia](db-studio.id.md) · [Filipino](db-studio.tl.md) · [Tiếng Việt](db-studio.vi.md) · [简体中文](db-studio.zh.md) · [हिन्दी](db-studio.hi.md) · [עברית](db-studio.he.md) · [العربية](db-studio.ar.md)
<!-- /languages -->

Das Datenbank-Studio ist eine Datenbank-Suite für SQLite, PostgreSQL,
MySQL/MariaDB, MongoDB und CouchDB — mit mitgelieferten Treibern, einer
Konsole, die die Maschine kennt, und Ergebnisgittern, die Sie direkt
bearbeiten. Dieses Tutorial nutzt SQLite, weil es keinen Server braucht.

![Eine SQLite-Verbindung, eine Abfrage, Live-Zeilen im Gitter — und die Statusleiste nennt den ehrlichen Grund, wenn ein Gitter nur lesbar ist](../images/db-studio.png)

## Öffnen

`⌥⌘7`, oder der Tab **Datenbank-Studio**.

## Schritte

1. **Legen Sie eine SQLite-Verbindung an.** Klicken Sie
   **Hinzufügen**, wählen Sie **SQLite** und einen
   Dateipfad (ein Speichern-Dialog lässt Sie eine neue `.db` anlegen). Sie
   erscheint im Verbindungsbaum.

2. **Führen Sie etwas SQL aus.** Tippen Sie in der Konsole und führen Sie
   aus:

   ```sql
   CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, active BOOLEAN);
   INSERT INTO users (name, active) VALUES ('Ada', 1), ('Bob', 0);
   SELECT * FROM users;
   ```

   Jede Anweisung bekommt darunter ihr eigenes Ergebnisgitter, mit
   Zeitmessung.

3. **Bearbeiten Sie eine Zeile im Gitter.** Doppelklicken Sie Bobs
   `name`-Zelle, ändern Sie sie und drücken Sie **Anwenden…**. Das
   Datenbank-Studio erlaubt Änderungen im Gitter nur, wenn es ein sicheres
   `UPDATE` für genau eine Zeile bauen kann (eine Tabelle, Primärschlüssel
   vorhanden) — es zeigt Ihnen das genaue SQL, bevor es läuft, und fragt
   danach erneut ab, um den tatsächlichen Stand zu zeigen. Lässt sich eine
   Zeile nicht sicher bearbeiten, sagt es Ihnen warum.

4. **Exportieren.** Drücken Sie **CSV** oder **JSON** an einem
   Ergebnisgitter. Der CSV-Export entschärft Formel-Injection für
   Tabellenkalkulationen automatisch.

5. **Lassen Sie eine Abfrage mit EXPLAIN erklären.** Markieren Sie ein
   `SELECT` und drücken Sie **EXPLAIN** für den Abfrageplan der jeweiligen
   Maschine.

6. **Lassen Sie KVASIR einen Fehlschlag erklären.** Führen Sie
   `SELECT * FROM user;` aus (beachten Sie den Tippfehler). Unter der
   Fehlermeldung erscheint ein Knopf **Erklären…**. Drücken Sie ihn, und
   ein Einwilligungsdialog nennt genau, was gesendet würde — das SQL, das
   Sie ausgeführt haben (einschließlich darin enthaltener Literalwerte),
   die Fehlermeldung und die Art der Maschine; nie die Verbindung, das
   Kennwort oder irgendwelche Zeilen. Stimmen Sie zu, und KVASIR erklärt
   den Fehler und schlägt die Korrektur vor, in einem Gesprächsfenster,
   das Rückfragen annimmt.

## Was Sie gerade gelernt haben

- Kennwörter liegen allein im Schlüsselbund des Systems, nie in
  `.nmoxdb.json`.
- Die Konsole kennt die Maschine: SQL für SQL-Datenbanken, eine
  JSON-Dokumentkonsole für MongoDB/CouchDB.
- Verlauf und gespeicherte Abfragen bleiben je Projekt erhalten;
  `.env`-Dateien bieten ihre `DATABASE_URL`/`DB_*`-Verbindungen
  automatisch an.

## Weiter

- Läuft eine Datenbank in Docker? Das Datenbank-Studio bietet dafür eine
  Verbindung an — siehe das [Docker-Panel](docker-panel.de.md).
