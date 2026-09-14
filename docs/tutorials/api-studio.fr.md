# Tutoriel : le Studio d’API

<!-- languages -->
[English](api-studio.md) · [Español](api-studio.es.md) · **Français** · [Deutsch](api-studio.de.md) · [Русский](api-studio.ru.md) · [Українська](api-studio.uk.md) · [Polski](api-studio.pl.md) · [Português (Brasil)](api-studio.pt.md) · [Bahasa Indonesia](api-studio.id.md) · [Filipino](api-studio.tl.md) · [Tiếng Việt](api-studio.vi.md) · [简体中文](api-studio.zh.md) · [हिन्दी](api-studio.hi.md) · [עברית](api-studio.he.md) · [العربية](api-studio.ar.md)
<!-- /languages -->

Le Studio d’API est un atelier REST à la Postman intégré à l’IDE. Vous
construisez des requêtes, vous vérifiez la réponse par des assertions et —
c’est unique — chaque réponse reçoit une note selon les standards des
en-têtes de sécurité du web.

![Un 200 réel en 331 ms — et l’onglet Standards qui note les en-têtes de sécurité de la réponse](../images/api-studio.png)

## L’ouvrir

`⌥⌘8`, ou la ligne **Studio d’API** dans la colonne OUTILS de l’onglet Bienvenue.

## Étapes

1. **Faites une requête.** Dans le constructeur de requêtes, réglez la
   méthode sur `GET` et l’URL sur `https://httpbin.org/json`. Pressez
   **Envoyer**. Le corps de la réponse arrive mis en forme ; la ligne
   d’état montre le code, la durée et la taille. (Une réponse qui s’emballe
   ne peut pas vous nuire — les corps sont lus en flux sous un plafond de
   8 Mo.)

2. **Ajoutez une assertion.** Dans l’onglet **Tests**, ajoutez
   « Status is 200 » et « Body contains slideshow ». Envoyez à nouveau —
   chaque assertion affiche un ✓ vert ou un ✗ rouge avec la valeur réelle.

3. **Lisez la note de sécurité.** Ouvrez l’onglet **Standards**. Le Studio
   d’API note HSTS, CSP, X-Content-Type-Options, la protection contre le
   clickjacking, Referrer-Policy et d’autres, et donne une note en lettre —
   la vérification qu’un développeur web de 2026 fait sur
   securityheaders.com, intégrée à chaque envoi.

4. **Utilisez une variable.** Créez un environnement avec `base =
   https://httpbin.org`, puis donnez à une requête l’URL `{{base}}/get`.
   Changez d’environnement pour rediriger toutes les requêtes d’un coup.
   Si le rack a un serveur de développement vivant, le Studio d’API
   propose même son URL comme `{{baseUrl}}`.

5. **Ajoutez l’authentification en toute sûreté.** Dans l’onglet **Auth**,
   choisissez Bearer ou Basic et saisissez un jeton. Le jeton n’est
   **jamais** écrit dans le `.nmoxapi.json` versionnable — il vit dans le
   trousseau du système, rattaché à la requête.

6. **Importez ce que vous avez déjà.** Le bouton **Importer…** lit une
   commande curl collée (le « Copy as cURL » des outils de développement du
   navigateur), un fichier de requêtes `.http`/`.rest` ou une spécification
   OpenAPI 3 (JSON ou YAML) — chacun devient de vraies requêtes, et un
   en-tête `Authorization` passe directement dans le champ Auth adossé au
   trousseau au lieu d’atterrir dans votre fichier d’espace de travail.
   **Copier curl** fait le chemin inverse : la commande exacte que lancerait
   Envoyer, dans votre presse-papiers.

7. **Interrogez KVASIR sur une mauvaise réponse.** Quand un envoi revient
   de travers, pressez **Expliquer…**. Une boîte de dialogue de
   consentement vous dit d’abord exactement ce qui quitterait votre
   machine — la méthode, l’URL avec les *valeurs* de la chaîne de requête
   masquées, le statut, les en-têtes sûrs (les en-têtes d’identification
   déjà retirés et comptés) et un corps plafonné — et rien n’est envoyé
   tant que vous ne l’avez pas dit. Refusez, et rien ne tourne ; acceptez,
   et l’explication s’ouvre comme une conversation où poser des questions
   de suivi.

## Ce que vous venez d’apprendre

- Les requêtes, les environnements et les assertions sont conservés par
  projet dans `.nmoxapi.json` (secrets exclus).
- La note de sécurité transforme « est-ce que ça marche » en « est-ce que
  c’est sûr ».
- Les envois sont annulables (le bouton Envoyer devient **Annuler**) et ne
  bloquent jamais le reste de l’IDE.

## Et ensuite

- Dirigez une requête vers un serveur du rack en marche grâce à l’offre
  `{{baseUrl}}`.
- Voyez le [Studio de bases de données](db-studio.fr.md) pour l’équivalent
  côté bases de données.
