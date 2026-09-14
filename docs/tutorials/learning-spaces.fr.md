# Tutoriel : les espaces d’apprentissage

<!-- languages -->
[English](learning-spaces.md) · [Español](learning-spaces.es.md) · **Français** · [Deutsch](learning-spaces.de.md) · [Русский](learning-spaces.ru.md) · [Українська](learning-spaces.uk.md) · [Polski](learning-spaces.pl.md) · [Português (Brasil)](learning-spaces.pt.md) · [Bahasa Indonesia](learning-spaces.id.md) · [Filipino](learning-spaces.tl.md) · [Tiếng Việt](learning-spaces.vi.md) · [简体中文](learning-spaces.zh.md) · [हिन्दी](learning-spaces.hi.md) · [עברית](learning-spaces.he.md) · [العربية](learning-spaces.ar.md)
<!-- /languages -->

![Le sélecteur d’espaces d’apprentissage — la recherche dans les tutoriels livrés, avec la sonde qui vous dit d’emblée si cette machine a l’outil de l’espace](../images/tabs/learning-spaces.png)

Un espace d’apprentissage est un bac à sable autonome pour apprendre un
langage, un cadriciel ou une bibliothèque : NMOX Studio génère du code
d’exemple, un tutoriel guidé et un rack déjà câblé avec un **vrai REPL
dans le rack**, où vous tapez. Il y en a 93 de livrés.

<!-- screenshot: a learning space open — sample code, the tutorial pane, and the REPL device with typed input -->

## L’ouvrir

`Fichier ▸ Nouvel espace d'apprentissage…` (le lanceur liste tous les espaces livrés).

## Étapes

1. **Choisissez un espace.** Prenez-en un — Python, Rust, Solid, htmx,
   Solidity, Elm, un REPL pour un langage système, un espace E2E/Playwright,
   etc. Le sélecteur vérifie d’abord que l’interpréteur ou la chaîne
   d’outils est disponible.

2. **Laissez-le générer.** NMOX Studio crée l’espace sous
   `~/.nmox/learn/<slug>` : un exemple minimal qui marche et un tutoriel
   qui vous le fait parcourir, en désignant la console ou l’appareil utile.

3. **Tapez dans le REPL.** Le rack câblé comprend un appareil **REPL** dont
   le bouton ENGINE est réglé sur le langage de l’espace (26 moteurs, chacun
   avec ses options d’interactivité forcée déjà renseignées). Tapez une
   expression, pressez Entrée — la sortie défile sur l’écran du REPL.
   L’interpréteur manque ? Le bouton **INSTALL** l’installe depuis le rack.

4. **Suivez le tutoriel.** Déroulez les étapes ; le code d’exemple est
   réel et exécutable, et l’espace est à vous pour le modifier.

## Ce que vous venez d’apprendre

- Un espace d’apprentissage est un projet complet, un tutoriel et un rack
  câblé, pas un simple extrait.
- Le REPL est un vrai processus interactif, pas une lecture enregistrée.
- Vous pouvez ajouter les vôtres : déposez un `*.json` dans
  `~/.nmox/learn-catalog.d/` et il rejoint le sélecteur (voyez
  [learning-spaces.md](../learning-spaces.md) pour le schéma).

## Et ensuite

- Les espaces des cadriciels (Astro/SvelteKit/Nuxt/Next) désignent leur
  console dans le rack (COSMOS/KINETIC/NIMBUS/NEXUS).
