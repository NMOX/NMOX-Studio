# Tutoriel : le Panneau Docker

<!-- languages -->
[English](docker-panel.md) · [Español](docker-panel.es.md) · **Français** · [Deutsch](docker-panel.de.md) · [Русский](docker-panel.ru.md) · [Українська](docker-panel.uk.md) · [Polski](docker-panel.pl.md) · [Português (Brasil)](docker-panel.pt.md) · [Bahasa Indonesia](docker-panel.id.md) · [Filipino](docker-panel.tl.md) · [Tiếng Việt](docker-panel.vi.md) · [简体中文](docker-panel.zh.md) · [हिन्दी](docker-panel.hi.md) · [עברית](docker-panel.he.md) · [العربية](docker-panel.ar.md)
<!-- /languages -->

Le Panneau Docker est une surface de contrôle pour votre moteur Docker
local — conteneurs, images, volumes, réseaux — avec en plus un onglet
**Dockerize** qui génère un Dockerfile de production pour votre projet.
Son pendant dans le rack est l’appareil **HARBOR**.

![Moteur démarré, un conteneur postgres en marche — pastille d’état, ports et la rangée de verbes : démarrer, arrêter, journaux, inspecter](../images/docker-panel.png)

## Avant de commencer

Docker doit tourner en local (`docker version` doit réussir ;
`Outils ▸ Docteur de l'environnement…` le confirmera).

## Étapes

1. **Ouvrez le panneau.** Pressez `⌘8`, ou cliquez **Panneau Docker** dans
   la colonne OUTILS de l’onglet Bienvenue. La vue d’ensemble **Moteur**
   indique si le démon tourne.

2. **Inspectez les conteneurs.** L’onglet **Conteneurs** liste ce qui
   tourne — noms, images, ports, état. **Images**, **Volumes** et
   **Réseaux** ont chacun leur onglet.

3. **Dockerisez un projet.** Ouvrez l’onglet **Dockerize** avec un projet
   visé. Il génère un `Dockerfile` de production, un `.dockerignore` et un
   fichier `compose` taillés pour votre chaîne d’outils (Node en plusieurs
   étapes, PHP `php-fpm` avec un nginx à côté, etc.) — sans jamais écraser
   les fichiers existants (s’il en existe un, il écrit un voisin
   `.suggested`).

4. **Recevez une offre de connexion.** Si un conteneur de base de données
   tourne, le Studio de bases de données propose automatiquement une
   connexion vers lui — déduite du nom de l’image puis du port, une fois
   par conteneur.

## Ce que vous venez d’apprendre

- Le panneau est une vraie enveloppe asynchrone autour de la CLI `docker` ;
  un démon bloqué est signalé, pas figé.
- Dockerize connaît la chaîne d’outils et est idempotent.

## Et ensuite

- Montez **HARBOR** dans le rack pour PANEL/PRUNE/REFRESH depuis une façade.
- Connectez-vous à une base conteneurisée dans le
  [Studio de bases de données](db-studio.fr.md).
