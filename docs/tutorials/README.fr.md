# Tutoriels NMOX Studio

<!-- languages -->
[English](README.md) · [Español](README.es.md) · **Français** · [Deutsch](README.de.md) · [Русский](README.ru.md) · [Українська](README.uk.md) · [Polski](README.pl.md) · [Português (Brasil)](README.pt.md) · [Bahasa Indonesia](README.id.md) · [Filipino](README.tl.md) · [Tiếng Việt](README.vi.md) · [简体中文](README.zh.md) · [हिन्दी](README.hi.md) · [עברית](README.he.md) · [العربية](README.ar.md)
<!-- /languages -->

Des parcours courts, à faire soi-même, pour les systèmes qui distinguent
NMOX Studio d’un IDE générique. Chacun tient en une séance : ouvrez la
fenêtre, suivez les étapes, et vous vous serez vraiment servi de la fonction.

Pour la référence complète (installation, chaque menu, chaque filet de
sécurité), voyez le [Guide de l’utilisateur](../user-guide.fr.md). Pour la
liste complète des appareils, voyez [devices.md](../devices.md).

## Les systèmes

| Tutoriel | Ce que vous ferez | S’ouvre avec |
|----------|-------------------|--------------|
| [Le Rack de tâches](the-task-rack.fr.md) | Câbler un montage exécution→moniteur et le voir se déclencher | ⌘9 / onglet Rack de tâches |
| [Écrire votre propre appareil](your-own-device.fr.md) | Ajouter un appareil au rack avec un éditeur de texte — sans Java, sans redémarrage | `~/.nmox/devices.d/` |
| [Plan de travail](workbench.fr.md) | Passer d’un projet et d’un outil à l’autre depuis le port d’attache | ⌥⌘0 |
| [Studio de projet](project-studio.fr.md) | Échafauder un projet et l’exécuter sans terminal | onglet Studio de projet |
| [Studio d’API](api-studio.fr.md) | Envoyer une requête, poser une assertion dessus, lire la note de sécurité | ⌥⌘8 |
| [Studio de bases de données](db-studio.fr.md) | Se connecter à SQLite et modifier une ligne dans la grille | ⌥⌘7 |
| [Studio de contrats](contract-studio.fr.md) | Compiler, déployer sur une chaîne locale et appeler un contrat | ⌥⌘6 (Web3) |
| [Concepteur d’infrastructure](infra-designer.fr.md) | Dessiner un droplet et un pare-feu, puis simuler le déploiement | ⌥⌘9 |
| [Studio de blocs](block-studio.fr.md) | Construire un Web Component avec des blocs qui s’emboîtent | ⌥⌘5 |
| [Édition polyglotte et débogage](polyglot-editing-and-debugging.fr.md) | Poser un point d’arrêt dans une application Node et l’atteindre | ouvrez n’importe quel projet |
| [Du navigateur au code source](browser-to-source.fr.md) | Cliquer un élément de la page, arriver dans son code source, le restyler depuis DevTools | ⌥⌘4 → DevTools → DOM |
| [L’Agent Port (MCP)](agent-port.fr.md) | Brancher un agent IA sur l’état vivant de l’IDE — en lecture seule par construction | `Outils ▸ Agent Port (MCP)…` |
| [Le Panneau Docker](docker-panel.fr.md) | Inspecter les conteneurs et dockeriser un projet | onglet Docker |
| [Le Tableau des tâches et les sprints](task-board.fr.md) | Mener un kanban avec pointeuse, standup en un clic et burndown de sprint, le tout dans un seul fichier versionné | ⌥⌘1 |
| [Montrer à une salle](show-it-to-a-room.fr.md) | Présenter, partager et faire des captures depuis l’IDE — du Mode présentation à la copie de l’arborescence en Markdown | `Affichage ▸ Mode présentation` |
| [KVASIR](kvasir.fr.md) | Demander à l’IA pourquoi une exécution a échoué | Rack → KVASIR |
| [Tout expliquer](explain-anything.fr.md) | Utiliser les quatre visages de KVASIR : exécutions, code, réponses d’API, erreurs de base de données | partout où quelque chose échoue |
| [Migrer depuis Postman](migrating-from-postman.fr.md) | Importer vos collections, captures HAR et le reste — les secrets vont dans le trousseau | ⌥⌘8 → Importer… |
| [Image Kit (Web)](image-kit.fr.md) | Compresser les images d’un projet : JPEG plus légers, variantes WebP, rapport honnête | `Fichier ▸ Ajouter au projet ▸ Image Kit (Web)…` |
| [Espaces d’apprentissage](learning-spaces.fr.md) | Lancer un bac à sable guidé avec un REPL vivant | `Nouvel espace d'apprentissage…` |
| [Assistants et kits](wizards-and-kits.fr.md) | Ajouter une PWA, les fichiers de standards ou des échafaudages web classiques | `Fichier ▸ Ajouter au projet` |

> **Un mot sur les raccourcis.** Sur macOS, les studios vivent dans la
> famille `⌥⌘` (Option-Commande) — `⌥⌘6`–`⌥⌘9`, `⌥⌘5`, `⌥⌘0` — parce que
> les accords `⇧⌘` simples sont pris par la plateforme. Sous Linux et
> Windows, le modificateur est `Alt+` ; les menus (Fenêtre ▸ …) marchent
> toujours, quoi qu’il arrive.

Au premier lancement, trois onglets s’affichent — Bienvenue, le Rack de
tâches et le Navigateur web — avec le Studio de projet, le Plan de travail et
l’Explorateur NPM ancrés à côté. Chaque autre fenêtre est à un raccourci
et figure dans la colonne OUTILS de l’onglet Bienvenue.
