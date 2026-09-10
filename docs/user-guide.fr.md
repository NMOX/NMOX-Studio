# NMOX Studio — Guide de l’utilisateur

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · **Français** · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

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

<a id="5-the-editor"></a>
## 5. L’éditeur

![Du code jQuery dans la palette NMOX Phosphor, la structure dans le Navigateur](images/editor.png)

Plus de 70 langages sont colorés comme il faut — la pile moderne, la pile classique (CoffeeScript compris) et toute la couche de configuration, jusqu’aux `.env`, `.editorconfig`, configurations nginx et Apache, Dockerfiles et fichiers de verrouillage.

- **La complétion** connaît le contexte et aussi *les bibliothèques classiques* : si votre projet porte jQuery, MooTools, Prototype, Backbone/Underscore ou Knockout (par des dépendances npm *ou* de simples balises `<script>`), leurs API apparaissent à la complétion. Les projets en jQuery 1.x ou 2.x reçoivent une pastille honnête de fin de vie, pas un rappel insistant.
- **Le plan du Navigateur (⌘7)** montre la structure du fichier pour 58 types ; cliquez pour y sauter.
- **La minicarte** — une silhouette du fichier entier le long de la barre de défilement de chaque éditeur ; cliquez ou faites glisser pour défiler. Le document entier tient toujours dans la bande : les lignes rétrécissent à mesure que le fichier grandit. Affichage ▸ Minicarte l’allume et l’éteint d’un coup pour tous les éditeurs ouverts.
- **Le défilement collant** — les déclarations qui englobent le haut de la vue (la classe, puis la méthode dans laquelle vous êtes descendu) restent épinglées au-dessus du texte, jusqu’à trois lignes du code lui-même ; cliquez-en une pour y sauter. La barre disparaît quand rien n’englobe la première ligne visible.
- **Aller au symbole (⌥⇧⌘O)** saute vers n’importe quelle fonction, classe, règle ou titre de tout le projet en tapant son nom, avec correspondance par préfixe, par majuscules internes ou par joker. L’index est borné et honnête : `node_modules` est ignoré, et sur un très gros projet la boîte de dialogue dit qu’elle a indexé les 2 000 premiers fichiers plutôt que de faire croire qu’elle a tout lu.
- **La fenêtre des tests (⌥⌘2)** montre tous les tests du projet *avant que quoi que ce soit ne s’exécute*, et lance un test, un fichier ou la totalité.

### Développer l’abréviation (⌥⌘E)

Tapez une abréviation Emmet et pressez **⌥⌘E** : `ul>li*3` devient la liste complète. Cela marche en HTML, dans les gabarits Angular et — dans sa forme CSS — à l’intérieur des blocs `<style>` et des attributs `style`, où la découpe est bornée à la région pour qu’elle n’avale jamais le balisage autour. Une abréviation que le produit ne reconnaît pas est refusée et laisse votre texte intact.

### Jetons de conception (propriétés personnalisées)

Taper `var(` propose les jetons déclarés dans les vraies feuilles de style de votre projet, chacun avec sa pastille de couleur et l’endroit où il est déclaré. **⌘-clic** sur un usage de `var(--jeton)` saute à sa déclaration. Les couleurs sont peintes telles qu’elles sont — hex, `rgb()`, `hsl()`, noms, et aussi `oklch()`, `lab()` et `color-mix()` — et **⌘-clic** sur un littéral de couleur ouvre un sélecteur qui le remplace dans la forme où vous l’avez écrit.

### L’attribut class connaît vos feuilles de style

Taper dans `class="…"` propose les classes que votre projet définit réellement, en indiquant de quelle feuille elles viennent ; **⌘-clic** sur une classe saute à sa règle, et **⌘-clic** sur un sélecteur `.classe` saute à son premier usage dans le balisage. **Renommer la classe…** renomme dans tout le projet — jetons entiers seulement, avec le compte par fichier — et refuse à voix haute si le nouveau nom existe déjà ou s’il reste des modifications non enregistrées.

### Lancer le script, depuis le curseur

Dans la section `scripts` d’un `package.json`, **Lancer le script** exécute la ligne où se trouve le curseur — via la même confirmation de confiance de l’espace de travail et le même ■ que n’importe quelle autre exécution.

### Les clés d’environnement, de plein droit

Taper `process.env.` ou `import.meta.env.` propose les clés que votre famille de fichiers `.env` définit vraiment, et **⌘-clic** saute à la ligne qui déclare la clé. Les valeurs sont affichées tronquées : le rappel est là, le secret non.

### Les gabarits Angular, de plein droit

Les fichiers `.component.html` s’ouvrent avec leur propre coloration de gabarit, les blocs `@if`/`@for` et les directives structurelles à la complétion. Installez l’Angular Language Service et le typage des gabarits arrive vraiment : écrivez mal un nom de propriété et le compilateur d’Angular lui-même vous propose le bon. **⌘B** dans un gabarit saute à la définition, et le menu contextuel passe entre le composant, son gabarit, ses styles et son test.

### Les composants Vue et Svelte, de plein droit

Les fichiers `.vue` et `.svelte` s’ouvrent avec leur propre coloration, leur propre complétion (les runes pointées de Svelte 5 comprises) et Emmet dans leurs blocs de gabarit. Les diagnostics de Vue arrivent réellement dans l’éditeur, par le serveur de langage de Vue.

### Le débogage avec de vrais points d’arrêt

Cliquez dans la marge, choisissez **Déboguer le fichier (points d’arrêt)** et le programme s’arrête là — avec la pile, les variables et l’évaluation d’expressions. JavaScript et TypeScript marchent d’origine grâce à l’adaptateur embarqué ; Python passe par debugpy et Go par delve, que vous installez vous-même. **Déboguer dans Chrome** fait de même pour une page : les points d’arrêt de votre source s’arrêtent dans l’IDE pendant que le navigateur tourne sur un profil jetable. Tout passe d’abord par la confirmation de confiance de l’espace de travail.

### Présenter et partager

**Affichage ▸ Mode présentation** agrandit d’un coup tous les éditeurs, la page du navigateur intégré, la fenêtre de sortie et le terminal — et remet tout exactement comme c’était en sortant. **Affichage ▸ Afficher les frappes** montre en grand l’accord que vous venez de presser, mais jamais ce que vous tapez. **Édition ▸ Copier comme Markdown** copie la sélection en bloc délimité avec la bonne étiquette de langage, et sa variante **avec lien** ajoute le lien GitHub vers ces mêmes lignes. **Outils ▸ Enregistrer une capture…** peint la fenêtre entière au double de la taille, avec des variantes pour l’onglet d’édition seul, pour le presse-papiers, et pour copier l’arborescence du projet en Markdown.

<a id="6-the-studios"></a>
## 6. Les studios

### Accès au clavier et au lecteur d’écran

Chaque commande du rack porte un nom accessible, et cela est vérifié à chaque compilation. Les boutons rotatifs sont des curseurs qui répondent aux flèches, à Début et à Fin ; les boutons répondent à Espace et Entrée, y compris ceux qui sont grisés, qui disent pourquoi ils refusent ; les LED et les afficheurs annoncent leur état. Tab retourne le rack, sauf quand le focus est sur une commande, où il cède la place au parcours normal.

### Git, sur la barre d’état

La pastille **⎇ branche** montre sur quelle branche vous êtes et combien de fichiers ont changé ; elle se lit sur le disque, donc elle ne coûte aucun processus. Un clic ouvre l’historique complet, et le menu porte **Différences du projet**, **Annoter**, les demandes de tirage via votre propre `gh`, et **Rédiger le message de commit avec KVASIR**.

### Tableau de tâches (⌥⌘1)

Un kanban par projet enregistré dans `.nmoxtasks.json`, à côté de votre code et versionné avec lui. Faites glisser les cartes ou déplacez-les au clavier : **⌘↑/⌘↓** les réordonne et la carte déplacée garde le focus. Les limites d’en-cours sont des conseils, pas des barrières : l’en-tête rougit et rien ne vous arrête. Le bouton **Vue d’ensemble** échange les colonnes contre un tableau de bord — en-cours, terminé aujourd’hui et cette semaine, flux par jour, cartes qui vieillissent — et l’horloge (**Pointer**) mesure le temps réel par carte, avec une seule horloge en marche sur tout le tableau. **Standup** transforme tout cela en un rapport prêt à coller.

### Studio de blocs (⌥⌘5)

Composez de vrais Web Components avec des pièces typées qui s’emboîtent, à la manière de Scratch : les imbrications illégales sont refusées, le code est engendré en un élément personnalisé autonome, et cliquer une pièce surligne ses lignes. Un serveur d’aperçu en mémoire montre le composant pour de vrai, composé avec les autres composants valides de votre bibliothèque. L’aller-retour est exact : réengendrer ce qui vient d’être lu redonne le même fichier, octet pour octet.

### Studio d’API (⌥⌘8)

Collections, requêtes, environnements à `{{variables}}` et tests, enregistrés dans `.nmoxapi.json` — les secrets restant dans le trousseau, jamais dans ce fichier. Chaque réponse reçoit une note de sécurité tirée de ses en-têtes. Importez depuis curl, `.http`, OpenAPI, Postman, Insomnia et HAR ; exportez en `.http` et copiez en curl ou en `fetch`, variables déjà résolues.

### Studio de bases de données (⌥⌘7)

SQLite, PostgreSQL, MySQL, MariaDB, MongoDB et CouchDB, pilotes inclus et mots de passe uniquement dans le trousseau. La console connaît le moteur, chaque instruction a sa propre grille de résultats, et les lignes s’éditent dans la grille même lorsqu’il y a une clé primaire — avec un aperçu des UPDATE exactes avant de les appliquer, et une raison honnête quand quelque chose est en lecture seule. Export en CSV ou JSON, formules neutralisées.

### Studio de contrats (⌥⌘6)

L’arbre des artefacts Foundry et Hardhat, un **Interagir** guidé par l’ABI avec retours et annulations décodés, un panneau **Surveiller** qui suit blocs et événements, et **Supervision** avec la table de gaz, les verdicts de taille EIP-170 et le carnet d’adresses. **Jamais de clés privées** : les envois passent par les comptes déverrouillés d’un réseau local, et les URL secrètes vivent dans le trousseau.

### Concepteur d’infrastructure (⌥⌘9)

Une toile pour DigitalOcean, Hetzner et Cloudflare : synchronisez ce qui existe réellement, rafraîchissez pour voir les écarts, détruisez une pile avec son coût sous les yeux. Les câbles illégaux refusent à voix haute en expliquant pourquoi, et pendant qu’une opération dans le nuage tourne, la toile se verrouille avec un bandeau qui le dit.

### IRC (⌥⌘3)

Un client complet dans l’IDE : TLS avec vraie vérification du nom, SASL, extensions IRCv3, complétion par tabulation, surlignages, URL qui s’ouvrent dans le navigateur intégré, journalisation sur disque, filtres à vous, et une liste de canaux que vous filtrez en tapant.

### Le site web embarqué

**Aide ▸ Site NMOX Studio (local)** sert le site du produit depuis son propre rack, sur l’interface locale. Il parle les treize langues que parle l’IDE ; le sélecteur est en pied de page.

### Navigateur (⌥⌘4)

Un vrai navigateur dans l’IDE, avec ses propres outils de développement — console, DOM, réseau, stockage, et des panneaux pour Vue, Svelte et Angular — parce que le moteur n’embarque aucun inspecteur et que celui-ci est le nôtre. Il connaît vos sources : désignez un élément, ouvrez la ligne qui l’a produit, restylez-le sur place, et la déclaration atterrit dans la feuille de style d’origine. Enregistrer un fichier recharge la page, et des tailles d’appareil réelles servent à éprouver votre mise en page adaptative.

<a id="7-docker"></a>
## 7. Docker

L’onglet Docker est un tableau de bord : état du moteur, conteneurs, images, volumes et réseaux, avec démarrer, arrêter, consulter les journaux et nettoyer. Le module HARBOR du rack vous donne la même chose d’un coup d’œil. Et comme dit plus haut : lancez un conteneur Postgres, MySQL ou Mongo, et le Studio de bases de données vous propose une connexion toute prête.

L’onglet **Dockerize** génère un `Dockerfile` de qualité production, un `.dockerignore` et un fichier de composition taillés pour la chaîne d’outils de votre projet — Node, PHP-FPM avec nginx, et d’autres.

<a id="8-wizards-and-kits"></a>
## 8. Assistants et kits

Tous se trouvent dans *Nouveau fichier…* et dans le menu contextuel du projet, et tous sont **idempotents et n’écrasent jamais rien** : relancer l’un d’eux met à jour ce qui lui appartient et laisse vos modifications tranquilles ; ce qu’il ne peut pas réécrire atterrit à côté sous la forme d’un fichier `.suggested`.

### Kit des standards

`robots.txt`, `sitemap.xml`, le manifeste web, le `security.txt` du RFC 9116 et `humans.txt`, générés à partir de vos réponses.

### Kit PWA

Un jeu complet d’icônes forgé à partir d’une seule image, variantes masquables comprises ; un service worker lisible — coquille d’application ou réseau d’abord, à vous de choisir —, une page hors ligne, et le câblage de `index.html` qui relie le tout.

### Kit d’accessibilité

L’accessibilité comme point de départ, pas comme audit d’après-coup : `a11y.css` (un anneau de focus visible, un utilitaire pour le texte réservé aux lecteurs d’écran, des styles de lien d’évitement et un bloc pour qui préfère moins de mouvement), `A11Y-NOTES.md` avec le parcours au clavier et les questions qu’aucune automatisation ne tranche, et le câblage idempotent de `index.html` — la langue, le lien d’évitement, la feuille de style. Un viewport qui empêche le zoom est signalé, jamais réécrit ; ce que le kit ne peut pas réparer, il le dit sans y toucher.

### Kit d’internationalisation

Traduisible dès le premier jour, le frère du kit d’accessibilité : `locales/en.json` et `locales/es.json` (un catalogue par langue, les mêmes clés), un `i18n.js` sans dépendances qui applique le catalogue au balisage `data-i18n`, garde `<html lang>` honnête et affiche une clé manquante telle quelle plutôt qu’un blanc silencieux ; plus `I18N-NOTES.md` — pas de fragments concaténés, `Intl` pour les dates et les nombres, le parcours de droite à gauche et la pseudo-localisation.

### Kit de contrats (Web3)

Choisissez une chaîne — Solidity avec Foundry, Soroban, Solana, CosmWasm, ink!, Cairo, Move, Bitcoin avec Miniscript, Clarity sur Stacks, Cardano avec Aiken ou TON avec Tact — et un nom de contrat, et le kit échafaude le démarrage éprouvé en conditions réelles : manifeste, contrat, test natif et un CONTRACT-NOTES.md qui nomme les modules du rack et les étapes à faire une seule fois. Les clés ne touchent jamais l’IDE.

### Kit classique

Ajoutez à n’importe quel code jQuery, MooTools, Prototype, Backbone avec Underscore ou Knockout, soit versionnés dans le dépôt (versions figées, sha256 consigné), soit en dépendances npm ; plus les échafaudages webpack, grunt, gulp ou bower.

<a id="9-quick-search-status-line-and-staying-oriented"></a>
## 9. Recherche rapide, barre d’état et garder le cap

### La pastille ⇄ sert

Une pastille **⇄ sert** apparaît dans la barre d’état dès que des serveurs tournent : l’exécution de l’IDE lui-même, les modules qui servent, et toute commande ayant imprimé une adresse locale. Cliquez et choisissez-en une : elle s’ouvre dans le navigateur intégré, ou dans celui du système quand cet onglet ne peut pas la prendre.

### ⌘I, le chercheur universel

Une seule boîte atteint vos projets (récents et connus), chaque module du rack — en sautant droit à ses commandes —, les **serveurs en marche** (Entrée l’ouvre dans le navigateur), les requêtes du Studio d’API, les connexions et les tables du Studio de bases de données, les contrats, les nœuds d’infrastructure et les cartes du Tableau des tâches, dont le résultat nomme la colonne où elles se trouvent.

### La barre d’état dit ce qui est vivant

À côté de la pastille des serveurs : le projet visé et sa chaîne d’outils, et la branche Git avec le nombre de fichiers modifiés. Tout cela se lit sur le disque ou dans des registres que le produit tient déjà — regarder ne coûte aucun processus.

### Le Plan de travail

C’est le port d’attache : projet courant, fichiers ouverts et récents, projets récents, et un lanceur pour chaque surface. Tant que quelque chose tourne, la section **EN COURS** ouvre la page — chaque commande que le produit a lancée pour vous, avec son adresse si elle en a annoncé une et depuis quelle heure elle tourne, plus chaque serveur qu’un module du rack fait tourner. Chaque ligne porte de vrais boutons **Ouvrir** et **Arrêter**, accessibles au clavier et au lecteur d’écran, si bien qu’une exécution peut être arrêtée sans abattre les autres. Tous les titres du Plan de travail sont de vrais boutons : Tab y arrive, Entrée ouvre. ⌘I atteint les mêmes exécutions : tapez « arrêter » et Entrée arrête exactement celle-là. Ce que vous avez arrêté vous-même se lit *arrêté* partout où son issue est rapportée, jamais comme un échec.

### Les raccourcis d’Emacs (et d’Eclipse, et d’IntelliJ)

Outils ▸ Options ▸ Raccourcis clavier change tout le profil : les déplacements et le couper-coller d’Emacs dans chaque éditeur, ou les jeux Eclipse et IDEA si c’est votre mémoire des doigts. Chaque raccourci NMOX est enregistré dans les cinq profils, si bien que changer de profil ne vous coûte jamais les raccourcis des studios.

<a id="10-the-safety-nets-things-you-dont-have-to-do-anything-for"></a>
## 10. Les filets de sécurité (ce que vous n’avez rien à faire pour avoir)

### La résurrection de la session

Le rack photographie ce qui tourne toutes les quelques secondes. Un arrêt forcé, un plantage, un `kill -9` — au redémarrage, une bulle vous propose de relancer exactement la session perdue, en un clic.

### La garantie anti-orphelins

Quitter l’IDE tue tous les processus qu’il a lancés — serveurs de développement, interpréteurs, chaînes, surveillants —, TERM d’abord, KILL s’ils résistent, descendants compris.

### BLACKBOX et SONAR

Ajoutez **BLACKBOX** à votre rack et vous avez une boîte noire : chaque lancement et chaque sortie, avec les durées, les tendances et ce qui a changé depuis la dernière compilation au vert. Ce que vous avez arrêté vous-même se lit ARRÊTÉ — ni vert ni échec, et jamais ce qu’on demande à KVASIR d’expliquer. **SONAR** montre qui occupe vos ports, recoupé avec Docker, et tue d’un clic le squatteur du 3000.

### Des fichiers jamais écrasés

Les quatre fichiers de travail des studios (`.nmoxapi.json`, `.nmoxdb.json`, `.nmoxweb3.json`, `.nmoxinfra.json`) se rechargent quand vous les modifiez hors de l’IDE — mais si vous avez des changements non enregistrés, on vous demande ; rien n’est écrasé. Un fichier corrompu est mis de côté en `.bak` et signalé, jamais remplacé en silence.

### TypeScript sans compilation

Un projet dont l’entrée est `index.ts`, `main.ts` ou `src/index.ts` s’exécute depuis IGNITION avec le retrait de types de Node lui-même (`--experimental-strip-types`, dès Node 22.6 ; par défaut depuis 23.6 et 22.18 LTS). Le refus d’un Node plus ancien est traduit en la phrase qui nomme ce plancher.

### Votre langue

NMOX Studio parle treize langues : English, Español, Français, Deutsch, Русский, Українська, Polski, Português (Brasil), Bahasa Indonesia, Filipino, Tiếng Việt, 简体中文 et हिन्दी. Choisissez la vôtre dans **Options ▸ Général ▸ Langue** — chacune est écrite dans son propre nom, pour que vous trouviez toujours la vôtre. Le choix est inscrit dans vos réglages de lancement (`etc/nmoxstudio.conf`, sous forme d’argument `--locale`) et s’applique aussi à chaud. Ce qui change : les menus, les boîtes de dialogue, les infobulles, les barres d’état, l’accueil et les Options. Ce qui reste : le vocabulaire des façades du rack (GO, STOP, EXPLAIN — des étiquettes d’appareil, comme sur un synthétiseur), et les dialogues plus profonds de la plateforme, qui n’ont pas encore de traduction.

### La vérification quotidienne des mises à jour

Discrète, une fois par jour : s’il existe une version plus récente, une notification vous mène au gestionnaire de modules, sur son onglet des mises à jour, où le centre de mise à jour installe les nouveaux modules sur place. Se coupe dans Options ▸ Général.

<a id="11-learning-spaces"></a>
## 11. Espaces d’apprentissage

### Vérifier votre travail

Certains espaces portent des points de contrôle : choisissez-en un et **Fichier ▸ Vérifier mon travail** vérifie vraiment les exercices — ce que disent les fichiers est contrôlé en Java pur, y compris les contrôles d’*absence*, qui sont la façon de vérifier « vous avez changé le titre » : le texte original de l’exemple doit avoir disparu. Ce que disent les commandes passe par la chaîne d’outils de l’espace lui-même. Chaque ✗ répond avec l’indice de l’espace, et en cas d’échec le rapport propose **Expliquer avec KVASIR…** : les points échoués et, pour un contrôle de fichier, votre propre fichier, plafonnés, sous un consentement qui dit exactement ce qui part. La réponse se lit comme celle d’un tuteur : quoi changer, puis relancez les contrôles.

### Vos propres tutoriels

Déposez un fichier `*.json` dans `~/.nmox/learn-catalog.d/` et il rejoint le sélecteur, avec le même schéma que ceux livrés ; un `slug` identique remplace celui de la maison. Vous enseignez ? Écrivez en construisant : faites de l’exercice un projet normal, et **Fichier ▸ Exporter comme espace d’apprentissage…** produit ce fichier pour vous — les fichiers d’exemple, votre `TUTORIAL.md`, le pilote d’exécution et vos points de contrôle —, validé contre l’analyseur du sélecteur avant d’être écrit, si bien que ce que vous remettez à vos élèves est exactement ce que leur sélecteur chargera.

### Le catalogue

*Nouvel espace d’apprentissage…* propose 93 tutoriels livrés — langages, cadres et bibliothèques. Chacun engendre un petit projet d’exemple, un tutoriel guidé et un rack déjà câblé avec un **vrai interpréteur** : vous tapez dans le rack et un interpréteur vivant répond. Le bouton ENGINE choisit parmi 37 interpréteurs ; s’il en manque un, le bouton INSTALL l’installe sur place en affichant l’avancement à l’écran. Les espaces vivent dans `~/.nmox/learn`, à l’écart de votre vrai travail.

### Premiers pas, sur l’accueil

Une quatrième colonne liste les six premiers gestes — ouvrir un projet, lancer quelque chose dans le rack, voir un serveur s’allumer, interroger KVASIR sur du code, essayer un espace d’apprentissage, pointer un agent vers l’IDE — et coche chacun à partir de registres que le produit tient déjà. Chaque ligne est une porte : un clic ouvre la fenêtre ou l’action. Une coche ne se décoche jamais ; la colonne disparaît quand les six sont faits, ou quand vous appuyez sur **Masquer cette liste**.

### Les trois réponses du menu Aide

**Nouveautés…** donne les notes de la version que vous exécutez, incluses dans la compilation ; au premier démarrage après une mise à jour, elles s’ouvrent seules sur ce que votre installation n’avait pas vu. **Signaler un problème…** compose un rapport avec votre environnement et les quarante dernières lignes du journal, déjà caviardées — votre dossier personnel devient `~`, votre identifiant `<user>`, tout ce qui ressemble à un secret `[redacted]` — ; vous l’éditez, puis **Ouvrir sur GitHub** préremplit un ticket que vous envoyez vous-même, ou vous le copiez. Le produit n’envoie jamais rien de lui-même. **Raccourcis clavier…** liste chaque raccourci NMOX de votre profil actif, lu sur le clavier en marche, de sorte qu’il ne peut pas dériver de ce que font les menus.

<a id="12-when-somethings-wrong"></a>
## 12. Quand quelque chose ne va pas

### Le Docteur de l’environnement

Dans le menu Outils, il sonde en direct 66 outils externes — node, npm, docker, forge, composer, gopls… — et montre la version trouvée ainsi que la commande d’installation de ce qui manque.

### Des murs avec une porte

S’il manque un serveur de langage ou un outil, l’IDE vous dit quelle commande lancer, ou propose de la lancer ; jamais un échec sec. Un mur a sa propre porte : TypeScript 7 ne livre pas de tsserver, donc si le TypeScript trouvé est le 7, l’éditeur le dit une fois et propose d’installer la série 5 — celle qu’il installe lui-même pour la même raison. Si un port est pris, l’erreur nomme le processus qui s’y est installé, et SONAR le déloge.

### Un GO qui ne fait rien

Regardez son écran : les modules s’expliquent avec des mots, et l’infobulle du bouton GO montre la commande exacte qu’il lancerait, pour que vous puissiez l’essayer dans un terminal.

### L’application s’ouvre sur rien (macOS)

Ni fenêtre ni erreur, au premier lancement après l’installation : c’est la quarantaine de Gatekeeper — voyez la note du chapitre 1. Un clic droit puis Ouvrir, une seule fois, et c’est réglé pour de bon. Les journaux vivent sous `~/Library/Application Support/nmoxstudio/…/var/log/` si vous devez ouvrir un ticket.

<a id="appendix-the-files-nmox-studio-writes-and-what-to-commit"></a>
## Annexe : les fichiers que NMOX Studio écrit (et ceux à versionner)

Tout ce que l’IDE conserve d’un projet est un fichier JSON lisible à la racine du projet, conçu pour être partagé avec votre équipe.

| Fichier | Ce qu’il contient | Le versionner ? |
|---|---|---|
| `.nmoxapi.json` | Collections, requêtes, environnements et tests du Studio d’API | **Oui** — votre collègue reçoit tout votre plan de travail |
| `.nmoxdb.json` | Connexions, requêtes enregistrées et historique | **Oui** — les mots de passe n’y sont *jamais* (trousseau seulement) |
| `.nmoxweb3.json` | Réseaux et carnet d’adresses du Studio de contrats | **Oui** — les URL secrètes n’y sont *jamais* (trousseau seulement) |
| `.nmoxinfra.json` | La toile d’infrastructure : nœuds, câblage, propriétés | **Oui** — les jetons n’y sont *jamais* (trousseau seulement) |
| `.nmoxtasks.json` | Le Tableau des tâches : colonnes, cartes, limites | **Oui** — l’équipe partage un tableau ; ignorez-le pour le garder personnel |
| `.gas-snapshot` | Les références de gaz de Foundry (GOVERNOR y veille) | **Oui** — c’est ainsi que les régressions de gaz sont vues en revue |
| `.env` | Vos variables d’environnement | **Non** — c’est tout l’objet de `.env` |
| `*.bak` | Un fichier de travail illisible, gardé pour vous | Non — récupérez ce qu’il vous faut, puis supprimez |

Modifiez l’un des quatre fichiers `.nmox*.json` hors de l’IDE, ou tirez les changements d’un collègue, et le studio correspondant se recharge tout seul — sauf si vous avez des changements non enregistrés, auquel cas il demande d’abord.

Hors du projet : `~/NMOX` est le plan de travail par défaut, les expériences vivent dans `~/.nmox/experiments`, les espaces d’apprentissage dans `~/.nmox/learn`, et l’état de l’IDE lui-même — disposition des fenêtres, patches du rack, préférences — dans le dossier utilisateur de la plateforme.
