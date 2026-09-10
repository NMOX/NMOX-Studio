# NMOX Studio — Guide de l’utilisateur

> Traduction partielle : les chapitres 1–2 sont en français. Pour le reste, voir le [guide complet en anglais](user-guide.md).

Comment se servir du produit. Ce guide parcourt les fonctions dans l’ordre où vous les rencontrerez : installation, premier lancement, projets, le rack, les studios, les assistants et les filets de sécurité.

---

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
