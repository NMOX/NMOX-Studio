# Tutoriel : Image Kit (Web) — compressez vos images

<!-- languages -->
[English](image-kit.md) · [Español](image-kit.es.md) · **Français** · [Deutsch](image-kit.de.md) · [Русский](image-kit.ru.md) · [Українська](image-kit.uk.md) · [Polski](image-kit.pl.md) · [Português (Brasil)](image-kit.pt.md) · [Bahasa Indonesia](image-kit.id.md) · [Filipino](image-kit.tl.md) · [Tiếng Việt](image-kit.vi.md) · [简体中文](image-kit.zh.md) · [हिन्दी](image-kit.hi.md) · [עברית](image-kit.he.md) · [العربية](image-kit.ar.md)
<!-- /languages -->

Les images sont en général ce qu’un site livre de plus lourd. L’Image Kit
trouve les JPEG et les PNG de votre projet et les compresse pour le web :
des voisins `.min.jpg` plus légers par réencodage en Java pur (rien à
installer), une réduction de taille facultative, et des voisins `.webp`
grâce à votre propre `cwebp` quand il est installé. Dans la preuve en
conditions réelles de cette version, un fond d’écran de 17,8 Mo est
devenu un `.min.jpg` de 347 Ko et un `.webp` de 342 Ko — 98 % de moins.

## Les lois qu’il respecte

- **Les originaux ne sont jamais touchés.** Les sorties sont des voisins
  (`photo.min.jpg`, `photo.webp`), et une sortie qui existe déjà est
  sautée en le disant — jamais écrasée.
- **Une « optimisation » qui ne gagne rien est écartée** : une compression
  qui récupère moins de 10 % est supprimée et signalée comme *déjà
  compact*, plutôt que de livrer un fichier « optimisé » plus gros. (Une
  sortie redimensionnée est conservée quoi qu’il arrive — des pixels en
  moins, c’était le but.)
- **Le réencodage PNG est volontairement absent.** ImageIO ne bat pas un
  vrai optimiseur PNG ; pour les PNG, le gain honnête est le voisin WebP.

## Étapes

1. **Visez un projet** et choisissez **Fichier ▸ Ajouter au projet ▸ Image Kit (Web)…**.
   La boîte de dialogue indique combien d’images elle a trouvées et leur
   poids total (node_modules et les sorties de construction sont ignorés,
   de même que ses propres sorties `.min.` — compresser une compression
   accumulerait les pertes).

2. **Choisissez votre compression.** La qualité JPEG (85 visuellement sans
   perte / 80 par défaut pour le web / 70 agressive), une largeur maximale
   facultative (2560 bannière retina / 1600 contenu / 800 vignettes), et —
   si `cwebp` est dans votre PATH — les voisins WebP. Sinon, la case le dit
   et indique où l’obtenir (`brew install webp`) ; le Docteur de
   l’environnement le sonde aussi.

3. **Lisez le rapport.** Par fichier : ce qui a été écrit, les tailles
   avant → après, ou la raison honnête pour laquelle rien ne l’a été
   (« existe déjà », « déjà compact »). Le total des octets gagnés figure
   en tête, avec un extrait `<picture>` prêt à copier qui sert le WebP là
   où il est pris en charge et retombe sur l’original ailleurs.

## Ce que vous venez d’apprendre

- L’optimisation des images pour le web sans aucun outil obligatoire — et
  votre propre `cwebp` quand vous l’avez.
- Les lois de la famille des kits, ne jamais écraser et rapporter
  honnêtement, valent aussi pour les pixels.
