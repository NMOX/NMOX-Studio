# NMOX Studio — Guide de l’utilisateur

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · **Français** · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> Traduction partielle : les chapitres 1–4 sont en français. Pour le reste, voir le [guide complet en anglais](user-guide.md).

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

<a id="4-the-task-rack"></a>
## 4. Le rack de tâches

![Le rack de tâches](images/tabs/the-task-rack.png)

Le rack est le cœur du produit. Chaque outil de votre flux de travail — npm, l’empaqueteur, le lanceur de tests, le serveur de développement, le linter, git, le déploiement — est un appareil matériel dans un rack : les boutons rotatifs choisissent la tâche, GO l’exécute, les LED montrent l’état, et un afficheur vous dit avec des mots ce qui s’est passé.

![Le rack pointé sur un site jQuery classique — le préréglage Classic Web Bench : MAESTRO, CRATE, DYNAMO (son bouton TASK a analysé le vrai Gruntfile), IGNITION servant du statique, VITALS veillant sur la qualité](images/task-rack.png)

**Les bases :**

- **Ajoutez des appareils** en les faisant glisser depuis la palette (elle a des catégories et un filtre de recherche). Chaque appareil porte sa fiche *Comment s’en servir*.
- **Lancez quelque chose** en pressant le bouton GO d’un appareil. Survolez-le d’abord : l’infobulle montre la ligne de commande exacte qui sera exécutée. Aucune magie.
- **Câblez un pipeline :** pressez **Tab** pour retourner le rack et voir sa face arrière. Tirez un cordon depuis la prise **OK** d’un appareil vers la prise **GO** du suivant. Désormais `installer → construire → tester` tient en une frappe : la chaîne se déroule seule et s’arrête au premier échec. La sortie défile sur l’écran phosphore de l’appareil MONITOR.
- **Annulez toute modification de structure** avec **⌘Z** — ajouts, retraits et recâblages. Retirer un appareil en marche arrête d’abord son processus.
- **Les préréglages** vous donnent un rack entier déjà câblé en un clic — Ship Gate, Dev Intelligence, Monorepo Lanes, E2E Loop, LAMP Bench, Web3 Bench, Uptime Watch. Les montages sont enregistrés par projet automatiquement.

![Tab retourne le rack — les cordons relient MAESTRO à travers CRATE, DYNAMO et IGNITION jusqu’à VITALS](images/rack-rear.png)

**La coordination, quand votre pipeline grandit :**

- **QUORUM** réunit des voies : il ne se déclenche que lorsque *toutes* ses entrées câblées ont réussi — le classique « attends le lint ET les tests ET le typage ».
- **Les portes ENABLE** sur les processus longs : l’entrée ENABLE d’un serveur de développement veut dire « ne démarre pas avant que ceci se déclenche ».
- **REFLEX** surveille les fichiers et route par motif — `src/**/*.css` vers une chaîne, `**/*.ts` vers une autre, par voie dans un monorepo.
- **ROSETTA** choisit la voie d’outillage dans les dépôts mixtes (le rack détecte Node/Rust/Go/PHP/… par répertoire et pointe chaque appareil en conséquence).

**Des voies natives à votre outillage.** En AUTO, les appareils de lint et de format (PURITY, GLOSS) parlent l’outillage du projet lui-même plutôt que de recourir partout aux outils Node : un espace de travail Deno utilise `deno lint` et `deno fmt`, un projet Cargo `cargo clippy` et `cargo fmt`, un module Go `go vet` (ou `golangci-lint` si le projet porte sa configuration) et `gofmt`. Un `biome.json` bascule les voies Node vers Biome, et les positions explicites du bouton l’emportent toujours sur AUTO.

**Vos propres appareils.** L’étagère s’étend avec un éditeur de texte : n’importe quel `*.json` dans `~/.nmox/devices.d/` devient un véritable appareil — boutons, LED, prises et cordons, enregistré dans le montage et accessible depuis ⌘I. Déclarez une commande comme un tableau d’arguments, nommez un bouton, et `{{bouton}}` s’y substitue à la pression. Les lois restent chez l’hôte, pas dans votre fichier : **la confiance de l’espace de travail garde le premier lancement exactement comme pour un appareil intégré**.

**Les portes de qualité** transforment le « ça a l’air fini » en « c’est fini » :

- **VITALS** lance Lighthouse contre votre serveur vivant et exige un plancher de performance, d’accessibilité, de bonnes pratiques ou de référencement.
- **VERITAS** impose un plancher de couverture et relance exactement les tests qui ont échoué, nommément.
- **GAUNTLET** met un point d’entrée en charge et exige un débit minimal. **PRISM** veille sur la taille du paquet, **BEACON** sur le certificat et la disponibilité d’une URL, et **PREFLIGHT** est la liste de contrôle avant expédition — câblez son OK vers votre appareil de déploiement et les déploiements ne peuvent physiquement pas partir tant que tout n’est pas au vert.
- **GOVERNOR** surveille les régressions de gaz sur le travail Solidity (`.gas-snapshot`).

**Tout le reste :** **SOLDER** habille n’importe quelle commande shell en appareil à part entière — et le rack entier **s’exporte vers GitHub Actions** (votre pipeline local et votre intégration continue sont le même câblage). **HELM** exécute des commandes sur un serveur distant en ssh, **TAIL** suit n’importe quel journal, et **PHOSPHOR** est un terminal dans le rack. Si la commande imprime une adresse locale, le témoin ⇄ s’allume comme pour tout appareil qui sert, et s’éteint à la fin de l’exécution.

**Le rack reste synchronisé tout seul.** Modifiez `package.json` et le bouton de scripts de NPM-9000 se met à jour sur place. Modifiez un `Gruntfile` et DYNAMO relit ses tâches. Ajoutez une dépendance et l’affichage de CRATE se rafraîchit. Sans repointer, sans bouton de rafraîchissement.

### KVASIR — expliquer le dernier échec

![KVASIR expliquant un échec réel : le diagnostic consenti sur la façade et les étapes complètes de correction dans la visionneuse](images/kvasir-explain.png)

**KVASIR** est l’assistance par IA à la manière du rack : un appareil qui explique l’erreur présente sur le bus MONITOR, pas une barre latérale de discussion. Quand une exécution échoue, pressez **EXPLAIN** et KVASIR demande à votre IA ce qui a mal tourné et quelle est l’étape suivante concrète. Un verdict court s’affiche ; **VIEW** ouvre la réponse entière. **MODEL** choisit entre **FAST** (rapide et économique, par défaut) et **DEEP** (plus puissant). EXPLAIN est bleu : il lit et demande, il ne touche jamais à votre projet.

**Choisissez votre IA, posez votre clé.** KVASIR fonctionne avec **Claude (Anthropic)**, **ChatGPT (OpenAI)** ou **Gemini (Google)** — votre clé, votre choix. Pressez **KEY…** pour choisir le fournisseur et coller sa clé ; le choix est mémorisé et la clé ne vit que dans le trousseau du système. Les variables d’environnement habituelles de chaque fournisseur sont lues aussi, et une clé enregistrée l’emporte sur une clé d’environnement.

**Ce que KVASIR envoie, et tout ce qu’il envoie.** La première fois que vous pressez EXPLAIN, une boîte de dialogue énumère exactement ce qui quittera votre machine et ce qui n’en sortira pas ; rien n’est envoyé sans ce consentement, et le consentement vaut par fournisseur. Après un EXPLAIN réussi, le bouton **VIEW** ouvre la réponse comme une conversation : vous pouvez continuer à poser des questions sur le même échec.

**Interrogez KVASIR sur votre code.** Le même assistant atteint l’éditeur : sélectionnez du code et choisissez **Interroger KVASIR sur la sélection…**, ou **Modifier avec KVASIR…** pour dire quoi changer et voir un avant et un après avant d’appliquer quoi que ce soit. **⌥⌘G** complète au curseur en texte fantôme qui ne s’insère que si vous pressez Tab, et la pastille de branche git peut rédiger votre message de commit.

**Pointez un agent sur votre IDE.** Outils ▸ Agent Port (MCP)… ouvre un point d’accès MCP qu’un assistant extérieur peut interroger : il est **en lecture seule par construction**, éteint tant que vous ne l’allumez pas, à l’écoute de la seule interface locale, et exige le jeton engendré à son démarrage.

Le rack est extensible : des greffons tiers peuvent ajouter des appareils (installez leur NBM depuis Outils ▸ Greffons). Pour en écrire un, voyez [device-spi.md](device-spi.md).
