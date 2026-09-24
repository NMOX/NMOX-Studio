# Glossaire

<!-- languages -->
[English](glossary.md) · [Español](glossary.es.md) · **Français** · [Deutsch](glossary.de.md) · [Русский](glossary.ru.md) · [Українська](glossary.uk.md) · [Polski](glossary.pl.md) · [Português (Brasil)](glossary.pt.md) · [Bahasa Indonesia](glossary.id.md) · [Filipino](glossary.tl.md) · [Tiếng Việt](glossary.vi.md) · [简体中文](glossary.zh.md) · [हिन्दी](glossary.hi.md) · [עברית](glossary.he.md) · [العربية](glossary.ar.md)
<!-- /languages -->

Les mots qu’emploie NMOX Studio et qu’un autre IDE n’emploie pas, plus les termes NetBeans qui transparaissent. Chaque entrée dit ce que le mot veut dire ici et où en lire davantage.

<a id="the-rack"></a>
## Le rack

**Rack de tâches** (⌘9) — La fenêtre où tournent vos outils. Chaque tâche (installer, compiler, tester, servir, analyser, déployer) est un *appareil* monté dans un rack, comme le matériel d’un studio. [Guide de l’utilisateur §4](user-guide.fr.md#4-the-task-rack).

**Appareil** — Un outil du rack, par exemple VELOCITY (Vite), VERITAS (tests) ou PURITY (lint). Un appareil a une face avant (la *façade*) avec des boutons rotatifs, des boutons, des voyants et un petit écran, et une face arrière avec des *jacks*. Il y a 53 appareils intégrés, listés dans [la référence des appareils](devices.md). Vous pouvez ajouter les vôtres sous forme de fichier JSON dans `~/.nmox/devices.d/` ([fichiers d’appareil](device-files.md)).

**Façade** — La face avant d’un appareil. Appuyez sur **Tab** dans le rack pour le retourner et voir la face arrière. Les libellés des façades (GO, STOP, EXPLAIN) restent en anglais dans toutes les langues, comme sur un matériel de studio.

**Jack** — Une prise sur la face arrière d’un appareil. Les jacks de sortie envoient des signaux et les jacks d’entrée les reçoivent. Il y a trois sortes de signal :
- Un **déclencheur** (*trigger*) est une impulsion unique : « la compilation est terminée », « OK », « FAIL ».
- Une **porte** (*gate*) reste allumée ou éteinte : « le serveur tourne ».
- Les **données** (*data*) transportent du texte, comme une URL ou une ligne de sortie.

**Câble** — Une connexion d’un jack de sortie vers un jack d’entrée. Reliez le jack OK d’une compilation au jack RUN du lanceur de tests, et les tests tournent dès qu’une compilation réussit. Pour relier deux jacks, faites glisser de l’un à l’autre, ou cliquez sur l’un puis sur l’autre.

**Patch** — Un rack entier : ses appareils, leurs réglages et leurs câbles. Il est enregistré à côté du projet sous le nom `.nmoxrack.json`, et mérite donc d’être versionné.

**Préréglage** — Un patch tout prêt que vous chargez depuis le menu **Préréglages ▾** du rack, par exemple *Ship Gate* ou *E2E Loop*. Enregistrez n’importe quel patch dans `~/.nmox/presets.d/` et il apparaît aussi dans le menu.

**Rack de départ** — Le patch qu’un projet reçoit la première fois que vous l’ouvrez, choisi selon le genre de projet : une console Vite pour une application Vite, des voies pour exécuter, déboguer et tester pour une crate Cargo, et ainsi de suite. La Galerie de racks les classe sous *départ*.

**Voie** — Deux sens, qui parlent tous deux de faire tourner des choses :
- Une **chaîne** : des appareils reliés par des câbles, comme installer → compiler → tester. Plusieurs voies peuvent tourner côte à côte, et QUORUM attend qu’elles aient toutes fini.
- La **voie AUTO** d’un appareil : la commande qu’il choisit pour ce projet. En AUTO, l’appareil de tests lance `npm test` dans un projet Node et `cargo test` dans un projet Rust.

**Partager… / Importer…** — Enregistrer un rack dans un fichier pour quelqu’un d’autre, ou charger celui qu’on vous envoie. Avant que quoi que ce soit ne soit monté, l’import montre tout ce que contient le fichier, et chaque appareil arrive éteint.

**Galerie de racks** — **Outils ▸ Galerie de racks…** liste les racks de la communauté, les préréglages, les racks de départ et vos propres racks enregistrés. Chaque entrée dit à quoi elle sert et de quels outils elle a besoin. [Racks de la communauté](racks.md).

<a id="projects-and-running"></a>
## Projets et exécution

**Visée** / **projet visé** — Le projet sur lequel l’IDE travaille en ce moment. Ouvrir un projet le vise : le rack, les studios, la barre d’état et l’exécution suivent le projet visé. En viser un autre les fait tous changer, et ce qui tourne encore est d’abord arrêté, après vous l’avoir demandé.

**Confiance de l’espace de travail** — La question que NMOX Studio pose avant d’exécuter pour la première fois le code propre d’un projet (scripts, compilations, tests). Si vous répondez **Rester prudent**, rien du projet ne s’exécute ; **Approuver l’espace de travail** l’autorise. Votre réponse est mémorisée par dossier.

**▶ et ■** — Exécuter et Arrêter, dans la barre d’outils. ▶ (F6) exécute le projet visé. ■ (⌥⌘.) arrête toutes les commandes que NMOX Studio a lancées pour vous.

**Pastille ⇄** / **en service** — Quand ce que vous lancez imprime une adresse locale, comme `http://localhost:5173/`, l’adresse apparaît dans la barre d’état après un symbole ⇄ (*⇄ en service*). Ce serveur qui tourne est dit *en service*. Cliquez sur l’adresse pour l’ouvrir dans le Navigateur web. La Recherche rapide liste les serveurs en service sous *Serveurs actifs*.

**Expérience** — Un projet jetable créé à partir d’un gabarit dans `~/.nmox/experiments`. Ses dépendances sont déjà installées et il est déjà approuvé. **Promouvez**-le pour le garder, ou **abandonnez**-le. **Fichier ▸ Nouvelle expérience…**.

**Espace d’apprentissage** — Un tutoriel guidé pour un langage ou un cadriciel. Il crée un vrai projet, un parcours et un rack préparé avec un REPL vivant, et **Fichier ▸ Vérifier mon travail** vérifie vos exercices. Il y en a 93. **Fichier ▸ Nouvel espace d’apprentissage…**.

**PREFLIGHT** — L’appareil de vérification avant expédition. Il lance les vérifications que votre projet définit (lint, types, tests, compilation) en un seul passage, réussi ou échoué.

**Premiers pas** — La liste de contrôle de l’onglet Bienvenue (colonne *PREMIERS PAS*). Les étapes se cochent d’elles-mêmes à mesure que vous les faites et ne se décochent jamais.

<a id="the-windows"></a>
## Les fenêtres

**Studio** — Une fenêtre avec son propre outil pour un genre de travail. Il y en a cinq : le **Studio d’API** (⌥⌘8), le **Studio de bases de données** (⌥⌘7), le **Studio de contrats** (⌥⌘6, contrats intelligents), le **Studio de blocs** (⌥⌘5, des composants web construits à partir de blocs) et le **Concepteur d’infrastructure** (⌥⌘9, infrastructure dans le nuage). Chacun enregistre son travail à côté du projet dans un fichier `.nmox*.json`. Le **Studio de projet** porte le même nom mais c’est l’arborescence des fichiers et les gabarits de projet.

**Plan de travail** (⌥⌘0) — Le port d’attache : ce qui tourne, ce qui est ouvert, et vos projets et fichiers récents.

**Tableau des tâches** (⌥⌘1) — Un tableau kanban pour chaque projet, avec des sprints et une horloge, enregistré sous le nom `.nmoxtasks.json`.

**Bienvenue** — L’onglet de départ : des actions pour commencer, les projets récents, la colonne *OUTILS* qui liste chaque fenêtre, et les Premiers pas.

<a id="ai"></a>
## IA

**KVASIR** — Le nom des fonctions d’IA de NMOX Studio : demander, modifier, compléter, expliquer, rédiger un message de commit. Il fonctionne avec Claude, ChatGPT ou Gemini en utilisant votre propre clé d’API, rangée dans le trousseau du système. Chaque fonction demande votre consentement une fois et nomme exactement ce qu’elle enverra. Rien n’est envoyé tant que vous n’utilisez pas une fonction. Les versions plus anciennes l’appelaient ORACLE.

**Agent Port** — **Outils ▸ Agent Port (MCP)…** donne à un agent d’IA qui tourne sur votre machine, comme un assistant de programmation, un accès en lecture seule à l’état de l’IDE par MCP : fichiers ouverts, diagnostics, exécutions, symboles. Il est en lecture seule par conception et n’écoute que sur votre propre machine. [Tutoriel](tutorials/agent-port.md).

<a id="netbeans-terms-you-may-see"></a>
## Les termes NetBeans que vous pouvez croiser

NMOX Studio est construit sur la plateforme NetBeans, et quelques-uns de ses mots transparaissent.

**Module** / **NBM** — Une partie de l’application. Un *NBM* est le fichier dans lequel un module est livré. **Outils ▸ Plugins** installe les mises à jour module par module.

**Centre de mise à jour** — L’endroit où **Outils ▸ Plugins ▸ Mises à jour** trouve les nouvelles versions des modules de NMOX Studio (il s’appelle *Mises à jour de NMOX Studio*). Il lit un catalogue publié avec chaque version sur GitHub.

**userdir** — Le dossier où NMOX Studio garde ses réglages, la disposition des fenêtres, les journaux et les mises à jour installées. Pour le trouver, ouvrez la boîte *About* : sous Windows et Linux elle est dans le menu Aide, sous macOS dans le menu NMOX Studio. Son journal est `var/log/messages.log`. Pour repartir de réglages neufs, lancez avec `--userdir <an empty folder>`.

**Options** / **Settings…** — La boîte des préférences. C’est **Outils ▸ Options** sous Windows et Linux, et **NMOX Studio ▸ Settings…** sous macOS.

**Éléments à traiter** (*Action Items*) — La fenêtre qui liste les problèmes trouvés dans le projet, y compris les résultats de lint et de vérification des types du rack. Cliquez sur un problème pour aller à cette ligne.
