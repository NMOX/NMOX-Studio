# Tutoriel : le Studio de blocs

<!-- languages -->
[English](block-studio.md) · [Español](block-studio.es.md) · **Français** · [Deutsch](block-studio.de.md) · [Русский](block-studio.ru.md) · [Українська](block-studio.uk.md) · [Polski](block-studio.pl.md) · [Português (Brasil)](block-studio.pt.md) · [Bahasa Indonesia](block-studio.id.md) · [Filipino](block-studio.tl.md) · [Tiếng Việt](block-studio.vi.md) · [简体中文](block-studio.zh.md) · [हिन्दी](block-studio.hi.md) · [עברית](block-studio.he.md) · [العربية](block-studio.ar.md)
<!-- /languages -->

Le Studio de blocs est un compositeur à la Scratch pour de **vrais** Web
Components. Vous assemblez des blocs typés et il génère un élément
personnalisé autonome (shadow DOM, état, écouteurs) — plus un serveur
d’aperçu vivant pour le voir tourner. Cliquez un bloc pour surligner les
lignes exactes qu’il a produites.

![Le Studio de blocs — la palette des pièces, la toile avec la racine d’un composant, et l’élément personnalisé généré avec la correspondance pièce-code au clic](../images/tabs/block-studio.png)

## L’ouvrir

`⌥⌘5`, ou l’onglet **Studio de blocs**.

## Étapes

1. **Nommez votre élément.** Tout élément personnalisé exige une balise
   avec un trait d’union. Commencez un composant et donnez-lui une balise
   comme `hello-badge`.

2. **Ajoutez des blocs depuis la palette.** Faites glisser un bloc
   **Élément** (un nœud DOM) et donnez-lui un texte ; ajoutez un champ
   **État** ; ajoutez un écouteur qui bascule une classe au clic. Seules
   les imbrications légales sont permises — la toile prévisualise les
   emplacements de dépôt valides et refuse les autres, même au chargement.

3. **Lisez le code.** Le panneau du milieu montre le `text/javascript`
   généré — un élément personnalisé complet. Cliquez n’importe quel bloc
   et les lignes qu’il a produites se surlignent ; la correspondance est
   exacte.

4. **Voyez-le vivre.** Pressez **Aperçu**. Le Studio de blocs sert le
   composant depuis un serveur en mémoire et l’affiche ; `⇄` et la
   recherche rapide montrent l’URL vivante. Les composants d’un même
   espace de travail peuvent même s’utiliser les uns les autres.

5. **Enregistrez-le.** **Enregistrer le composant** écrit
   `src/components/<tag>.js` — de façon atomique, sans jamais écraser un
   fichier modifié à la main. Tout l’espace de travail vit dans
   `.nmoxblocks.json` ; **Ouvrir un composant…** réimporte un fichier écrit
   par vous (ou par le studio), tant qu’il reste dans le dialecte des blocs.

## Ce que vous venez d’apprendre

- La sortie est un vrai élément personnalisé, sans cadriciel, que vous
  pouvez livrer.
- La correspondance bloc↔code va dans les deux sens : les modifications
  dans le dialecte se réimportent proprement.
- Un espace de travail contient plusieurs composants ; en changer est une
  frontière d’annulation.

## Et ensuite

- Composez des composants avec des composants — un bloc qui nomme la
  balise d’un voisin l’affiche imbriqué dans l’aperçu.
