# Tutoriel : le Rack de tâches

<!-- languages -->
[English](the-task-rack.md) · [Español](the-task-rack.es.md) · **Français** · [Deutsch](the-task-rack.de.md) · [Русский](the-task-rack.ru.md) · [Українська](the-task-rack.uk.md) · [Polski](the-task-rack.pl.md) · [Português (Brasil)](the-task-rack.pt.md) · [Bahasa Indonesia](the-task-rack.id.md) · [Filipino](the-task-rack.tl.md) · [Tiếng Việt](the-task-rack.vi.md) · [简体中文](the-task-rack.zh.md) · [हिन्दी](the-task-rack.hi.md) · [עברית](the-task-rack.he.md) · [العربية](the-task-rack.ar.md)
<!-- /languages -->

Le Rack de tâches est l’idée signature de NMOX Studio : votre outillage de
construction, de test et de service disposé comme un rack d’appareils
matériels que vous reliez avec des cordons. Un appareil exécute une vraie
commande ; un câble transporte un vrai signal. Ce tutoriel construit un
tout petit montage — exécuter quelque chose et allumer un voyant quand
c’est fini — pour que la métaphore prenne tout son sens.

![Le rack visant un vrai projet — appareils en place et en marche](../images/task-rack.png)

![Tab retourne le rack — les cordons relient les appareils à l’arrière](../images/rack-rear.png)

## Avant de commencer

Ouvrez un projet (n’importe quel projet Node fait l’affaire ;
`Fichier ▸ Nouveau projet…` → « Vanilla JS » s’il vous en faut un).
Ouvrir un projet **vise** le rack sur lui, si bien que chaque appareil
s’exécute dans le répertoire de ce projet.

## Étapes

1. **Ouvrez le rack.** Cliquez l’onglet **Rack de tâches** (ou pressez
   `⌘9`). Le rack de départ contient un seul **MONITOR** — l’appareil
   console qui montre la sortie des commandes et les lignes d’erreur.

2. **Ajoutez un exécuteur.** Faites glisser **IGNITION** de la palette de
   gauche sur l’étagère. IGNITION est l’appareil « exécuter » polyglotte ;
   visant un projet Node, il lance `npm run dev` (il détecte tout seul
   votre gestionnaire de paquets et votre chaîne d’outils).

3. **Reliez-le au moniteur.** Cliquez la commande qui **retourne** le rack
   pour voir l’arrière, puis cliquez la prise **OUT** d’IGNITION et la
   prise **TAP** de MONITOR — un cordon les relie. (Tirer d’une prise à
   l’autre marche aussi ; cliquer est plus simple quand le rack est large.)

4. **Déclenchez-le.** Revenez à l’avant et pressez le bouton **GO**
   d’IGNITION. Il lance le processus ; la sortie défile dans MONITOR et
   les LED d’état s’allument. Si le projet n’est pas encore approuvé, une
   confirmation unique de confiance de l’espace de travail arrive d’abord —
   c’est la garde qui empêche un dépôt cloné d’exécuter ses scripts sans
   votre accord.

5. **Enregistrez le montage.** `⌘S` (ou le bouton **Enregistrer le patch**)
   écrit `.nmoxrack.json` à côté de votre projet. Rouvrez le projet plus
   tard et le montage — appareils, câbles, positions des boutons —
   revient à l’identique.

## Ce que vous venez d’apprendre

- **Les appareils sont des outils avec une façade.** Les boutons rotatifs
  choisissent les options, les boutons GO exécutent, les LED et les
  afficheurs rendent compte de l’état — et chaque commande est réelle (pas
  de bouton mort ; un test de contrat l’impose).
- **Les câbles coordonnent les voies.** OUT→TAP est le câblage le plus
  simple ; les portes de disponibilité (`ENABLE`), les barrières de
  jonction (`QUORUM`) et les câbles de déclenchement permettent de composer
  tout un pipeline qui réagit à lui-même.
- **Tout est conservé.** Le montage est un fichier que l’on versionne ; le
  rack ressuscite même une session en cours après un plantage.

## Et ensuite

- Il y a 53 appareils — parcourez-les dans [devices.md](../devices.md) ou
  dans les fiches « Comment s’en servir » de la palette.
- Demandez à [KVASIR](kvasir.fr.md) d’expliquer une exécution échouée.
- Exportez un montage en workflow GitHub Actions : l’**export CI** du rack.
