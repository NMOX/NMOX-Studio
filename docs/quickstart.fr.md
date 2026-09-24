# Démarrage rapide : votre projet en marche en cinq minutes

<!-- languages -->
[English](quickstart.md) · [Español](quickstart.es.md) · **Français** · [Deutsch](quickstart.de.md) · [Русский](quickstart.ru.md) · [Українська](quickstart.uk.md) · [Polski](quickstart.pl.md) · [Português (Brasil)](quickstart.pt.md) · [Bahasa Indonesia](quickstart.id.md) · [Filipino](quickstart.tl.md) · [Tiếng Việt](quickstart.vi.md) · [简体中文](quickstart.zh.md) · [हिन्दी](quickstart.hi.md) · [עברית](quickstart.he.md) · [العربية](quickstart.ar.md)
<!-- /languages -->

Cette page fait tourner l’un de vos propres projets dans NMOX Studio. Elle ne couvre que ce dont vous avez besoin pour cela. [Le guide de l’utilisateur](user-guide.fr.md) est le manuel complet. Si vous utilisez VS Code, lisez ensuite [Venir de VS Code](coming-from-vscode.fr.md).

<a id="1-install-one-minute"></a>
## 1. Installer (une minute)

**macOS, avec Homebrew :**

```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

Homebrew vous demande de lancer `brew trust` une seule fois pour tout tap tiers. Il ne le redemandera pas lors des mises à jour.

**macOS, Windows, Linux, sans Homebrew :** téléchargez la dernière version pour votre système depuis [la page des versions](https://github.com/NMOX/NMOX-Studio/releases/latest) :

| Système | Fichier | Ensuite |
|---|---|---|
| macOS | `NMOX-Studio-<version>-macos.dmg` | Faites glisser l’application dans Applications. |
| Windows | `NMOX-Studio-<version>-windows-setup.exe` | Lancez l’installateur. |
| Debian, Ubuntu | `nmox-studio_<version>_amd64.deb` | `sudo apt install ./nmox-studio_<version>_amd64.deb` |
| Autre Linux | `NMOX-Studio-<version>-linux.tar.gz` | Décompressez-le et lancez `bin/nmoxstudio`. |

Chacun de ces fichiers contient son propre environnement Java : vous n’avez rien d’autre à installer. Seul le zip portable exige que Java 21 ou plus récent soit déjà présent sur la machine.

Sous macOS, l’application est notariée par Apple. La première fois que vous l’ouvrez, macOS demande s’il faut ouvrir une application téléchargée depuis Internet : cliquez sur **Ouvrir**.

<a id="2-open-your-project-one-minute"></a>
## 2. Ouvrir votre projet (une minute)

Lancez **NMOX Studio**. Il ouvre trois onglets : **Bienvenue**, **Rack de tâches** et **Navigateur web**.

Pour ouvrir votre projet, choisissez **Fichier ▸ Ouvrir un dossier…** (⌥⌘O sous macOS, Ctrl+Alt+O sous Windows et Linux) et désignez son dossier. Vous pouvez aussi le faire depuis un terminal, comme vous le feriez avec `code .` :

```bash
cd ~/code/my-app
nmox .
```

La commande rend la main aussitôt. Si NMOX Studio tourne déjà, il reçoit le dossier ; sinon, il démarre. Homebrew, l’installateur Windows et les paquets Linux mettent `nmox` dans votre PATH. Pour une installation depuis le DMG, voyez [mettre `nmox` dans votre PATH](user-guide.fr.md#2-first-launch).

Un dossier compte comme un projet s’il contient un `package.json`, un `Cargo.toml`, un `go.mod`, un `pom.xml`, un `composer.json`, un `pyproject.toml` ou l’un des 57 autres fichiers de projet reconnus. Un dossier de simples fichiers HTML compte aussi.

Trois choses se produisent quand vous ouvrez un projet :

- Le **Studio de projet**, à gauche, montre vos fichiers.
- La barre d’état, en bas, montre votre branche git et le nombre de fichiers modifiés.
- Le **Rack de tâches** est préparé pour le genre de projet que c’est. Un projet Vite reçoit une console Vite, un projet Cargo reçoit des voies pour exécuter, déboguer et tester, et ainsi de suite.

<a id="3-run-it-one-minute"></a>
## 3. L’exécuter (une minute)

Appuyez sur **▶** dans la barre d’outils, ou sur F6. Il exécute votre projet comme ses outils l’exécutent : le script `dev`, `start` ou `serve` de `package.json`, `cargo run`, `go run`. Il utilise le gestionnaire de paquets de votre projet : npm, pnpm ou yarn, ou bun pour un projet Bun.

La première fois que vous exécutez quoi que ce soit dans un projet, NMOX Studio vous demande si vous faites confiance au dossier. Un projet que vous n’avez pas approuvé n’exécute rien de son propre code : ni scripts, ni compilations, ni tests. Pour votre propre code, cliquez sur **Approuver l’espace de travail**.

Si votre projet est un serveur de développement, son adresse apparaît dans la barre d’état à côté d’un symbole **⇄**, et la page s’ouvre dans l’onglet **Navigateur web**. Modifiez un fichier et enregistrez : la page se recharge.

Pour arrêter tout ce qui tourne, appuyez sur **■** à côté de ▶, ou sur ⌥⌘. (Option, Commande et point).

S’il ne se passe rien, regardez l’onglet **Output** en bas. Il explique pourquoi l’exécution n’a pas pu démarrer, par exemple qu’un outil n’est pas installé ou que les dépendances ne le sont pas encore, et propose de corriger cela. **Outils ▸ Docteur de l’environnement…** liste tous les outils que NMOX Studio sait utiliser et montre lesquels sont installés.

<a id="4-find-anything-thirty-seconds"></a>
## 4. Tout trouver (trente secondes)

Appuyez sur **⌘I** (Ctrl+I sous Windows et Linux) et tapez. La **Recherche rapide** trouve les fichiers, les actions des menus, les symboles, les appareils du rack, les serveurs et les commandes en cours, ainsi que les scripts de votre `package.json`. Appuyez sur Entrée pour ouvrir ou exécuter le résultat.

Appuyez sur **⌘P** pour ouvrir un fichier par son nom (**Aller au fichier…**).

<a id="5-test-it-thirty-seconds"></a>
## 5. Le tester (trente secondes)

Appuyez sur **⌃F6** (Ctrl+F6) pour lancer les tests de votre projet. Pour voir tous les tests du projet avant d’en lancer un seul, ouvrez la fenêtre **Tests** avec ⌥⌘2.

<a id="if-you-have-no-project-handy"></a>
## Si vous n’avez pas de projet sous la main

- **Fichier ▸ Nouveau projet…** crée un vrai projet à partir d’un gabarit (Angular, Vue, Svelte, React avec Vite, JavaScript sans cadriciel, PHP, Phoenix et d’autres). Il crée les fichiers, prépare git et installe les dépendances.
- **Fichier ▸ Nouvel espace d’apprentissage…** ouvre un tutoriel guidé. *Votre première page web* vient en tête de liste.

<a id="where-to-go-next"></a>
## Et ensuite

- **[Le rack de tâches](user-guide.fr.md#4-the-task-rack)**. Chaque outil que vous lancez est un appareil du rack, et des câbles entre les appareils les enchaînent : par exemple, lancer les tests dès que la compilation réussit.
- **[L’éditeur](user-guide.fr.md#5-the-editor)**. Avec Emmet, les pastilles de couleur, le débogage avec points d’arrêt pour Node et Chrome, et les gabarits Angular.
- **[Les studios](user-guide.fr.md#6-the-studios)**. Les studios d’API, de bases de données, de contrats et de blocs, et le Tableau des tâches.
- **[Le glossaire](glossary.fr.md)** explique les mots propres au produit : rack, patch, jack, voie, visée, KVASIR.
