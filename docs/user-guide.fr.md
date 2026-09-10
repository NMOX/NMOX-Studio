# NMOX Studio — Guide de l’utilisateur

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · **Français** · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> Traduction partielle : les chapitres 1–3 sont en français. Pour le reste, voir le [guide complet en anglais](user-guide.md).

Comment se servir du produit. Ce guide parcourt les fonctions dans l’ordre où vous les rencontrerez : installation, premier lancement, projets, le rack, les studios, les assistants et les filets de sécurité.

---

<a id="1-install"></a>
## 1. Installation

**macOS (recommandé) :**
```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

La ligne `brew trust` est la confirmation unique de Homebrew pour tout tap tiers — on ne vous la redemandera pas lors des mises à jour. L’application est signée en ad-hoc mais non notariée, donc une copie en quarantaine serait refusée par Gatekeeper au premier lancement : le cask retire lui-même l’attribut de quarantaine dans une étape `postflight` et l’indique dans la sortie d’installation. Rien de silencieux.

**Tout le reste :** récupérez un fichier de la [dernière version](https://github.com/NMOX/NMOX-Studio/releases/latest) — `.dmg` pour macOS, `-setup.exe` pour Windows, `.deb` pour Debian/Ubuntu, `.tar.gz` générique pour Linux. Les quatre embarquent leur propre environnement Java ; rien à installer d’abord. Le `-portable.zip` est le seul artefact qui utilise votre propre Java (nécessite Java 21+ dans le PATH, ou lancez-le avec `--jdkhome <chemin-du-jdk>`).

> **macOS, premier lancement :** l’application est signée en ad-hoc mais non notariée, donc Gatekeeper demande confirmation avant de l’exécuter. **Clic droit sur l’application → Ouvrir** la première fois et confirmez, ou exécutez
> `xattr -d com.apple.quarantine "/Applications/NMOX Studio.app"`. L’un ou l’autre règle la question définitivement.

### Mise à jour

L’IDE se met à jour lui-même : **Outils ▸ Plugins ▸ Mises à jour** propose les modules de toute version plus récente. Installez, redémarrez quand on vous le demande, c’est fait — sans retélécharger l’application entière. Une réserve honnête : l’environnement Java embarqué et le lanceur ne changent qu’avec un installateur complet, donc pour les sauts de plateforme importants, une installation neuve depuis un fichier de version reste la bonne solution.

<a id="2-first-launch"></a>
## 2. Premier lancement

Depuis un terminal, `nmoxstudio --open <dossier>` lance l’application avec ce dossier ouvert comme projet et le rack pointé dessus — la même porte que « Ouvrir un dossier… » sur la page d’accueil.

L’IDE s’ouvre avec toutes les onglets de la suite le long de la zone d’édition : **Bienvenue → Rack de tâches → Studio de bases de données → Studio de contrats → Concepteur d’infrastructure → Studio d’API → Panneau Docker** — chaque surface principale est à un clic dès la première minute. Dans le dock de gauche : **Studio de projet** (arborescence et modèles), la base **Plan de travail** et l’**Explorateur NPM**. Un dossier `~/NMOX` est créé comme espace de travail par défaut ; le rack y pointe jusqu’à ce que vous ouvriez un projet.

![Premier lancement — la page d’accueil avec tous les onglets ouverts](images/welcome.png)

Raccourcis à apprendre dès le premier jour (ils sont aussi tous listés sur l’onglet d’accueil) :

| Raccourci | Ouvre |
|---|---|
| **⌘I** | Recherche rapide — atteint tout |
| **⌘9** | Rack de tâches |
| **⌥⌘0** | Plan de travail |
| **⌥⌘3** | Client de discussion IRC |
| **⌥⌘4** | Navigateur (WebKit intégré, avec DevTools) |
| **⌥⌘5** | Studio de blocs |
| **⌥⌘6** | Studio de contrats |
| **⌥⌘7** | Studio de bases de données |
| **⌥⌘8** | Studio d’API |
| **⌥⌘9** | Concepteur d’infrastructure |
| **⌘8** | Panneau Docker |
| **⌘7** | Plan du fichier courant |
| **⇧⌘N / ⌥⌘O** | Nouveau projet… / Ouvrir un dossier… |
| **⇧⌘E / ⇧⌘L** | Nouvelle expérience… / Nouvel espace d’apprentissage… |

<a id="3-projects"></a>
## 3. Projets

**Ouvrir :** tout dossier portant l’un des 60 manifestes reconnus s’ouvre comme un vrai projet — `package.json`, `Cargo.toml`, `go.mod`, `pom.xml`, `composer.json`, `foundry.toml`, `bower.json`, `Gruntfile.js` et compagnie — y compris les manifestes des chaînes de contrats : un dépôt Aiken (`aiken.toml`) ou Clarinet (`Clarinet.toml`) s’ouvre avec ses vraies voies câblées. Un simple dossier de HTML avec des balises `<script>` et **sans** manifeste s’ouvre aussi, comme projet STATIC : le web classique est de premier rang, pas une erreur.

**Créer :** *Nouveau projet…* propose de vrais gabarits — Angular, Vue, Svelte, JavaScript sans cadriciel, Elixir/Phoenix, PHP Web (LEMP) et Web classique (jQuery). Chacun arrive avec ses configurations de lint, de format et de tests déjà câblées et un dépôt git initialisé : un seul commit d’échafaudage qui, lorsque l’assistant lance l’installation pour vous, contient aussi le fichier de verrouillage, si bien que votre premier `git status` est propre.

**Changer de projet est sûr :** si des appareils tournent (un serveur de développement, un observateur), l’IDE demande avant de changer et les arrête proprement. Rien ne continue à tourner dans votre dos, jamais. Même forcer la fermeture de l’IDE ne peut pas laisser un processus orphelin.

**Les expériences** sont le moyen le plus rapide d’essayer une technologie. **Fichier ▸ Nouvelle expérience…** (⇧⌘E) choisit un gabarit et génère un projet jetable sous `~/.nmox/experiments` : pas de git, pas de récents, déjà approuvé, dépendances installées, pour que la **première exécution marche**. Il s’ouvre sur son propre parcours `EXPERIMENT.md`, qui dit quoi presser, quel fichier modifier et où vit l’intelligence de l’IDE pour cette technologie. Gardez ce qui prend forme : **Fichier ▸ Expériences…** ▸ **Promouvoir** l’en sort et initialise git, **Dupliquer** en crée une copie pour tenter une autre approche, **Écarter** supprime le reste. L’étagère montre l’âge de chacune et son coût disque mesuré. Vous préférez le chemin guidé ? La boîte de dialogue met en avant les 93 espaces d’apprentissage.

![L’étagère des espaces d’apprentissage — nombre, coût disque, âge et tout le cycle de vie](images/spaces-shelf.png)

![Une expérience Express toute neuve : le parcours ouvert, les dépendances installées, l’API déjà servie](images/experiment-walkthrough.png)

**Exécuter, construire, tester — et arrêter :** le ▶ de la barre (F6) exécute le projet comme sa chaîne d’outils l’exécute : un script `start` si package.json en a un, `cargo run`, `go run`, `dotnet run`, et pour un dossier de HTML un petit serveur statique sur le premier port libre à partir de 8080. Construire, Tester et Nettoyer sont à côté et dans le menu Exécuter. Un serveur de développement qui annonce son adresse allume le témoin ⇄ de la barre d’état et ouvre la page dans le navigateur intégré. Tout passe la première fois par la confirmation de confiance de l’espace de travail. Une exécution qui n’a pas pu démarrer le dit et propose d’ouvrir le Docteur d’environnement. Pour arrêter : le ■ à droite de Déboguer (⌥⌘.) arrête d’un coup toutes les commandes en cours et dit ce qu’il a arrêté ; **Exécuter ▸ Arrêter** en arrête une et propose ensuite **Répéter**. Le ■ voit tout ce que le produit lance pour vous, installations comprises ; au survol, l’infobulle nomme exactement ce qu’une pression arrêterait, et depuis quand chaque chose tourne.

**`.env` partout :** si votre projet a un `.env`, les appareils lancés depuis le rack reçoivent ces variables. Modifiez-le et la barre d’état note que les redémarrages le prendront en compte — les processus en cours gardent honnêtement leur ancien environnement.
