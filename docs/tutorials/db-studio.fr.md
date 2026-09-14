# Tutoriel : le Studio de bases de données

<!-- languages -->
[English](db-studio.md) · [Español](db-studio.es.md) · **Français** · [Deutsch](db-studio.de.md) · [Русский](db-studio.ru.md) · [Українська](db-studio.uk.md) · [Polski](db-studio.pl.md) · [Português (Brasil)](db-studio.pt.md) · [Bahasa Indonesia](db-studio.id.md) · [Filipino](db-studio.tl.md) · [Tiếng Việt](db-studio.vi.md) · [简体中文](db-studio.zh.md) · [हिन्दी](db-studio.hi.md) · [עברית](db-studio.he.md) · [العربية](db-studio.ar.md)
<!-- /languages -->

Le Studio de bases de données est une suite pour SQLite, PostgreSQL,
MySQL/MariaDB, MongoDB et CouchDB — pilotes inclus, une console qui
connaît le moteur et des grilles de résultats modifiables sur place. Ce
tutoriel utilise SQLite, qui n’a besoin d’aucun serveur.

![Une connexion SQLite, une requête, des lignes vivantes dans la grille — et la barre d’état qui donne la raison honnête quand une grille est en lecture seule](../images/db-studio.png)

## L’ouvrir

`⌥⌘7`, ou l’onglet **Studio de bases de données**.

## Étapes

1. **Créez une connexion SQLite.** Cliquez **Ajouter**, choisissez
   **SQLite** et indiquez un chemin de fichier (un sélecteur de type
   « enregistrer » vous permet de créer un nouveau `.db`). La connexion
   apparaît dans l’arborescence des connexions.

2. **Lancez un peu de SQL.** Dans la console, tapez puis exécutez :

   ```sql
   CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, active BOOLEAN);
   INSERT INTO users (name, active) VALUES ('Ada', 1), ('Bob', 0);
   SELECT * FROM users;
   ```

   Chaque instruction reçoit sa propre grille de résultats en dessous,
   avec sa durée.

3. **Modifiez une ligne dans la grille.** Double-cliquez la cellule `name`
   de Bob, changez-la et pressez **Appliquer…**. Le Studio de bases de
   données n’autorise l’édition dans la grille que s’il peut construire un
   `UPDATE` sûr sur une seule ligne (une seule table, clé primaire
   présente) — il vous montre le SQL exact avant de l’exécuter, puis
   relance la requête pour vérifier. Si une ligne ne peut pas être
   modifiée sans risque, il vous dit pourquoi.

4. **Exportez.** Pressez **CSV** ou **JSON** sur n’importe quelle grille de
   résultats. L’export CSV neutralise automatiquement
   l’injection de formules de tableur.

5. **Passez une requête à EXPLAIN.** Sélectionnez un `SELECT` et pressez
   **EXPLAIN** pour obtenir le plan d’exécution natif du moteur.

6. **Laissez KVASIR expliquer un échec.** Lancez `SELECT * FROM user;`
   (remarquez la faute de frappe). Sous le message d’erreur, un bouton
   **Expliquer…** apparaît. Pressez-le : une boîte de dialogue de
   consentement nomme exactement ce qui serait envoyé — le SQL que vous
   avez exécuté (valeurs littérales comprises), le message d’erreur et le
   type de moteur ; jamais la connexion, le mot de passe ni aucune ligne.
   Acceptez, et KVASIR explique l’erreur et propose la correction, dans une
   fenêtre de conversation qui accepte les questions de suivi.

## Ce que vous venez d’apprendre

- Les mots de passe vivent uniquement dans le trousseau du système, jamais
  dans `.nmoxdb.json`.
- La console connaît le moteur : du SQL pour les moteurs SQL, une console
  de documents JSON pour MongoDB/CouchDB.
- L’historique et les requêtes enregistrées sont conservés par projet ; les
  fichiers `.env` proposent automatiquement leurs connexions
  `DATABASE_URL`/`DB_*`.

## Et ensuite

- Une base de données tourne dans Docker ? Le Studio de bases de données
  propose une connexion vers elle — voyez le
  [Panneau Docker](docker-panel.fr.md).
