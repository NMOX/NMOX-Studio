# Tutorial: Studio ng Database

<!-- languages -->
[English](db-studio.md) · [Español](db-studio.es.md) · [Français](db-studio.fr.md) · [Deutsch](db-studio.de.md) · [Русский](db-studio.ru.md) · [Українська](db-studio.uk.md) · [Polski](db-studio.pl.md) · [Português (Brasil)](db-studio.pt.md) · [Bahasa Indonesia](db-studio.id.md) · **Filipino** · [Tiếng Việt](db-studio.vi.md) · [简体中文](db-studio.zh.md) · [हिन्दी](db-studio.hi.md) · [עברית](db-studio.he.md) · [العربية](db-studio.ar.md)
<!-- /languages -->

Ang Studio ng Database ay suite para sa SQLite, PostgreSQL, MySQL/MariaDB,
MongoDB, at CouchDB — may kasamang driver, console na kilala ang uri ng
engine, at mga grid ng resulta na maaari mong baguhin sa kinaroroonan.
SQLite ang gamit ng tutorial na ito dahil walang kailangang server.

![Isang koneksyon sa SQLite, isang query, buhay na mga hilera sa grid — at ang status bar na nagsasabi ng tapat na dahilan kapag read-only ang isang grid](../images/db-studio.png)

## Buksan ito

`⌥⌘7`, o ang tab na **Studio ng Database**.

## Mga hakbang

1. **Lumikha ng koneksyon sa SQLite.** I-click ang **Idagdag**, piliin
   ang **SQLite**, at pumili ng path ng file (hinahayaan ka ng chooser na
   estilong save na lumikha ng bagong `.db`). Lumilitaw ito sa puno ng mga
   koneksyon.

2. **Magpatakbo ng SQL.** Sa console, itipa at patakbuhin:

   ```sql
   CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, active BOOLEAN);
   INSERT INTO users (name, active) VALUES ('Ada', 1), ('Bob', 0);
   SELECT * FROM users;
   ```

   May sarili nitong grid ng resulta sa ibaba ang bawat statement, kasama
   ang tagal.

3. **Baguhin ang isang hilera sa grid.** I-double-click ang cell na `name`
   ni Bob, baguhin ito, at pindutin ang **Ilapat…**. Pinapayagan ng Studio
   ng Database ang pag-edit sa grid lamang kapag kayang bumuo ng ligtas na
   `UPDATE` para sa iisang hilera (iisang table, may primary key) —
   ipinapakita nito ang eksaktong SQL bago patakbuhin, saka muling
   nag-query para sa katotohanan. Kung hindi ligtas na baguhin ang isang
   hilera, sinasabi nito kung bakit.

4. **I-export.** Pindutin ang **CSV** o **JSON** sa anumang grid ng resulta. Kusang
   pinawawalang-bisa ng export sa CSV ang formula injection ng
   spreadsheet.

5. **I-EXPLAIN ang isang query.** Pumili ng `SELECT` at pindutin ang
   **EXPLAIN** para sa query plan ng engine mismo.

6. **Hayaang ipaliwanag ng KVASIR ang pagkabigo.** Patakbuhin ang
   `SELECT * FROM user;` (pansinin ang typo). Sa ilalim ng mensahe ng
   error, lumilitaw ang pindutang **Ipaliwanag…**. Pindutin ito, at
   pinapangalanan ng dialog ng pahintulot ang eksaktong ipapadala — ang
   SQL na pinatakbo mo (kasama ang anumang literal na halaga rito), ang
   mensahe ng error, at ang uri ng engine; hindi kailanman ang koneksyon,
   password, o anumang hilera. Pumayag, at ipinapaliwanag ng KVASIR ang
   error at nagmumungkahi ng pag-aayos, sa window ng usapan na tumatanggap
   ng mga karugtong na tanong.

## Ang iyong natutunan

- Nasa keychain ng OS lamang ang mga password, hindi kailanman sa
  `.nmoxdb.json`.
- Kilala ng console ang uri: SQL para sa mga SQL engine, console ng JSON
  document para sa MongoDB/CouchDB.
- Naka-imbak kada proyekto ang history at mga naka-save na query; kusang
  inaalok ng mga file na `.env` ang kanilang mga koneksyong
  `DATABASE_URL`/`DB_*`.

## Susunod

- Nagpapatakbo ng database sa Docker? Nag-aalok ng koneksyon para dito
  ang Studio ng Database — tingnan ang [Panel ng Docker](docker-panel.tl.md).
