# Tutoriel : présenter devant une salle

<!-- languages -->
[English](show-it-to-a-room.md) · [Español](show-it-to-a-room.es.md) · **Français** · [Deutsch](show-it-to-a-room.de.md) · [Русский](show-it-to-a-room.ru.md) · [Українська](show-it-to-a-room.uk.md) · [Polski](show-it-to-a-room.pl.md) · [Português (Brasil)](show-it-to-a-room.pt.md) · [Bahasa Indonesia](show-it-to-a-room.id.md) · [Filipino](show-it-to-a-room.tl.md) · [Tiếng Việt](show-it-to-a-room.vi.md) · [简体中文](show-it-to-a-room.zh.md) · [हिन्दी](show-it-to-a-room.hi.md) · [עברית](show-it-to-a-room.he.md) · [العربية](show-it-to-a-room.ar.md)
<!-- /languages -->

Certains jours, le livrable n’est pas le code mais la *démonstration* : un
projecteur, un README, un commentaire de ticket, une diapositive. NMOX Studio
a un petit kit de présentation pensé pour ce moment-là, et chacune de ses
pièces repose sur quelque chose que l’IDE avait déjà plutôt que sur un ajout
plaqué : le zoom de texte de l’éditeur lui-même, le vocabulaire unique des
langages qui étiquette les blocs de code, le dessin de la forge de
documentation. Ce tutoriel parcourt le tout en une séance, du dernier rang
au presse-papiers.

![Mode présentation activé : un gabarit Angular et la fenêtre Output tous deux à +10 pt, rétablis exactement quand le mode est désactivé](../images/presentation-mode.png)

![L’onglet d’édition seul, enregistré à 2x par Enregistrer la capture de l’éditeur…](../images/editor-screenshot-2x.png)

## Avant de commencer

Ouvrez un projet qui vit dans un dépôt GitHub (le geste de lien a besoin d’un
`origin` sur GitHub — tout le reste est refusé à voix haute, jamais deviné)
et ouvrez l’un de ses fichiers sources. Enregistrez-le : le geste de lien
refuse aussi un tampon aux modifications non enregistrées, car un bloc qui
ne correspond pas à son lien est un mensonge. Pour l’étape 2, lancez aussi le
projet (le ▶ de la barre d’outils ou le rack), afin d’avoir une page dans le
Navigateur web intégré et de la sortie dans la fenêtre Output.

## Étapes

1. **Rendez le tout lisible pour la salle.** `Affichage ▸ Mode présentation`.
   Chaque éditeur ouvert grandit de dix points, à chaud, tout comme chaque
   éditeur que vous ouvrez tant que le mode est actif. L’élément de menu
   affiche une coche et la barre d’état nomme l’agrandissement. Rien n’est
   écrit dans vos réglages — désactivez-le (ou redémarrez) et la police
   redevient exactement ce qu’elle était, y compris tout réglage fin à la
   molette avec ⌥ ajouté par-dessus.

2. **Regardez le reste de l’IDE suivre.** Le mode actif, la page du Navigateur web
   intégré passe à 150 % du zoom que vous aviez, le texte de la fenêtre
   Output grandit des mêmes dix points, et chaque Terminal ouvert est agrandi
   aussi — chacun retrouvant sa propre taille à la sortie. Une démonstration
   de l’application en marche, de sa sortie et du shell où vous tapez se lit
   ainsi depuis le dernier rang, et pas seulement le code.

3. **Montrez vos mains.** `Affichage ▸ Afficher les frappes`, puis pressez
   `⌘S`. Une pastille sombre indiquant `⌘S` apparaît en grand en bas de la
   fenêtre pendant un instant (une répétition affiche `⌘Z ×3`). Tapez
   maintenant un mot : rien n’apparaît. Seules les combinaisons avec ⌘, ⌃ ou
   ⌥, les touches de fonction et Échap s’affichent — la frappe ordinaire
   jamais, si bien qu’un mot de passe tapé dans un terminal ne peut pas finir
   sur le projecteur.

4. **Partagez le code.** Sélectionnez quelques lignes et choisissez
   `Édition ▸ Copier en Markdown` (ou faites un clic droit dans l’éditeur).
   Collez dans un README, un ticket ou une discussion : un bloc délimité
   étiqueté avec le langage du fichier (` ```html `, ` ```typescript `,
   ` ```bash `…), terminé par exactement un saut de ligne, avec une clôture
   plus longue si l’extrait contient lui-même trois accents graves, pour
   qu’il s’affiche en entier. Sans sélection, le fichier entier est copié. La
   barre d’état dit combien de lignes et quelle étiquette.

5. **Dites où il vit.** Même sélection,
   `Édition ▸ Copier en Markdown avec lien` (ou clic droit). Le collage est le
   même bloc suivi de
   `[src/app/app.ts#L3-L12](https://github.com/you/repo/blob/main/src/app/app.ts#L3-L12)`
   — la branche que vous avez extraite (une HEAD détachée pointe vers le
   commit), car un commit local jamais poussé serait une 404 déguisée en
   permalien. Un fichier hors de tout dépôt, un dépôt sans `origin`, une
   origine qui n’est pas GitHub, ou des modifications non enregistrées : la
   barre d’état refuse et rien n’est copié.

6. **Prenez l’image.** `Outils ▸ Copier la capture de l'éditeur` met l’onglet
   sélectionné de la zone d’édition — barre d’outils, marge, code, barres
   latérales, sans le reste de l’interface de l’IDE — dans le presse-papiers
   sous forme d’image 2x ; collez-la directement dans une discussion ou une
   diapositive. Elle prend l’onglet que vous regardez même quand le focus est
   dans le Navigateur du plan de fichier, et quand rien n’est ouvert dans la
   zone d’édition, elle le dit au lieu de copier une image vide.
   `Outils ▸ Enregistrer la capture de l'éditeur…` enregistre la même vue en
   PNG nommé d’après le document (`app.ts-<stamp>.png`), et
   `Outils ▸ Enregistrer la capture d'écran…` enregistre toute la fenêtre de
   l’IDE (`nmox-studio-<stamp>.png`, dans Images par défaut). Comme l’IDE se
   dessine lui-même, il n’y a aucune autorisation d’enregistrement d’écran à
   accorder, aucun bureau dans le cadre et rien à recadrer.

7. **Collez l’arborescence.**
   `Outils ▸ Copier l'arborescence du projet au format Markdown`. La structure
   du projet visé arrive sous la forme de l’arbre en caractères de dessin de
   boîte, dans un bloc délimité, que montre un README : les répertoires
   d’abord, `node_modules/ …` et ses lourds voisins nommés mais jamais
   parcourus, les arbres profonds ou énormes plafonnés avec le reste compté
   plutôt qu’abandonné en silence, et les fichiers `.nmox*.json` de l’IDE
   lui-même laissés de côté parce qu’ils appartiennent au produit, pas au
   projet.

8. **Quittez la scène.** `Affichage ▸ Mode présentation` à nouveau. Éditeurs,
   Navigateur web, Output et chaque terminal reviennent exactement où ils
   étaient ; `Affichage ▸ Afficher les frappes` désactivé, et la pastille
   disparaît.

## Ce que vous venez d’apprendre

- **Présenter est un état, pas un réglage.** Le Mode présentation agit à
  chaud et n’est jamais enregistré — un redémarrage ramène tout à la
  normale —, et c’est un état unique pour tout le produit, que l’éditeur
  bascule et que n’importe quelle fenêtre peut suivre.
- **La surimpression est volontairement étroite.** Afficher les frappes ne
  montre que les combinaisons et les touches de fonction ; ce que vous tapez
  n’est jamais affiché.
- **Une copie qui ne peut pas se porter garante ne copie rien.** Copier en
  Markdown avec lien refuse, dans la barre d’état, chaque échelon qu’il ne
  peut pas vérifier — pas d’origin, pas GitHub, tampon non enregistré —
  plutôt que de coller un lien qui ment.
- **Chaque partage est borné et brut.** L’arborescence ne suit jamais un lien
  symbolique, n’entre jamais dans un répertoire lourd, plafonne ce qu’elle
  liste et compte le reste ; la capture de l’éditeur est une image, et
  seulement une image.

## Pour aller plus loin

- Montrez aussi l’application en marche depuis le dernier rang : [Du
  Navigateur web à la source](browser-to-source.fr.md) parcourt le Navigateur web
  intégré et ses DevTools.
- Le Standup que vous collez dans une discussion vient du tutoriel [Le
  Tableau des tâches et les sprints](task-board.fr.md).
- Les notes de version d’un billet commencent à `Aide ▸ Nouveautés…`, puis à
  son bouton **Copier au format Markdown** ; toute la section sur la
  présentation se trouve dans le [Guide de
  l’utilisateur](../user-guide.fr.md).
