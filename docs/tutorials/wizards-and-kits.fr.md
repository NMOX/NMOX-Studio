# Tutoriel : assistants et kits

<!-- languages -->
[English](wizards-and-kits.md) · [Español](wizards-and-kits.es.md) · **Français** · [Deutsch](wizards-and-kits.de.md) · [Русский](wizards-and-kits.ru.md) · [Українська](wizards-and-kits.uk.md) · [Polski](wizards-and-kits.pl.md) · [Português (Brasil)](wizards-and-kits.pt.md) · [Bahasa Indonesia](wizards-and-kits.id.md) · [Filipino](wizards-and-kits.tl.md) · [Tiếng Việt](wizards-and-kits.vi.md) · [简体中文](wizards-and-kits.zh.md) · [हिन्दी](wizards-and-kits.hi.md) · [עברית](wizards-and-kits.he.md) · [العربية](wizards-and-kits.ar.md)
<!-- /languages -->

![L’assistant du kit des standards — robots.txt, sitemap, manifeste web, security.txt du RFC 9116 et humans.txt générés à partir de vos réponses](../images/tabs/wizards-and-kits.png)

NMOX Studio livre plusieurs générateurs à usage unique qui ajoutent un
échafaudage de qualité production à un projet existant sans écraser vos
fichiers. Ce tutoriel ajoute une PWA à un projet web ; les autres
fonctionnent de la même façon.

<!-- screenshot: the PWA Kit wizard, then the generated icons/manifest/sw.js in the tree -->

## Les kits

- **Kit PWA** — l’échafaudage d’une application installable : une
  **forge d’icônes** Java2D (variantes masquables comprises), un service
  worker lisible (coquille d’application / réseau d’abord), une page hors
  ligne et le câblage idempotent de `index.html`.
- **Kit des standards** — le minimum vital du web : `robots.txt`,
  `sitemap.xml`, le `manifest` d’application web, le `security.txt` du
  RFC 9116 et `humans.txt`.
- **Kit classique** — étend n’importe quel code avec jQuery / MooTools /
  Prototype / Backbone / Knockout, versionnés dans le dépôt ou via npm,
  plus des échafaudages webpack/grunt/gulp/bower.

## Étapes (Kit PWA)

1. **Visez un projet web** (qui a un `index.html`).

2. **Lancez l’assistant.** `Fichier ▸ Ajouter au projet ▸ PWA Kit…`.
   Indiquez la racine web, puis un nom d’application et une couleur de thème.

3. **Terminez.** L’assistant génère le jeu d’icônes, `manifest.webmanifest`,
   `sw.js` et `offline.html`, et les câble dans `index.html` — et il
   **n’écrase jamais rien** : si un fichier existe, il écrit un voisin
   `.suggested` à la place.

4. **Vérifiez.** Servez le projet (IGNITION dans le rack) et chargez-le —
   l’application est maintenant installable et marche hors ligne.

## Ce que vous venez d’apprendre

- Les kits produisent une sortie réelle et lisible qui vous appartient —
  pas une boîte noire.
- Chaque générateur est idempotent et n’écrase jamais votre travail.
- Le même civisme à l’enregistrement vaut ailleurs : `.editorconfig` est
  respecté à l’enregistrement dans tout l’éditeur.

## Et ensuite

- Le kit des standards pour `security.txt` et `robots`/`sitemap`.
- Notez les en-têtes du résultat dans l’onglet Standards du
  [Studio d’API](api-studio.fr.md).
