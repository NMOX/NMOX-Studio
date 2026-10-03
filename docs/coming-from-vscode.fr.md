# Venir de VS Code

<!-- languages -->
[English](coming-from-vscode.md) · [Español](coming-from-vscode.es.md) · **Français** · [Deutsch](coming-from-vscode.de.md) · [Русский](coming-from-vscode.ru.md) · [Українська](coming-from-vscode.uk.md) · [Polski](coming-from-vscode.pl.md) · [Português (Brasil)](coming-from-vscode.pt.md) · [Bahasa Indonesia](coming-from-vscode.id.md) · [Filipino](coming-from-vscode.tl.md) · [Tiếng Việt](coming-from-vscode.vi.md) · [简体中文](coming-from-vscode.zh.md) · [हिन्दी](coming-from-vscode.hi.md) · [עברית](coming-from-vscode.he.md) · [العربية](coming-from-vscode.ar.md)
<!-- /languages -->

Vos mains savent déjà où sont les choses. Cette page fait le lien entre ces habitudes et NMOX Studio : d’abord les accords, puis l’endroit où vit ici chaque idée de VS Code, puis ce qui est franchement différent.

Les quatre premiers accords qu’un utilisateur de VS Code presse font ce qu’il attend : **⇧⌘P** ouvre la palette de commandes, **⇧⌘E** l’arborescence des fichiers, **⇧⌘X** les plugins, et **⌃\`** le terminal. Ils sont enregistrés dans les cinq profils de raccourcis que livre la plateforme, et une vérification de compilation résout chacun d’eux dans les raccourcis assemblés, sous macOS, Windows et Linux, pour que rien d’autre ne se déclenche à leur place.

<a id="the-chords"></a>
## Les accords

Les colonnes macOS utilisent les symboles de la barre des menus (⌃ Contrôle, ⌥ Option, ⇧ Majuscule, ⌘ Commande) ; les colonnes Windows et Linux donnent le même accord sur un clavier de PC.

| Vous voulez | VS Code, macOS | NMOX, macOS | VS Code, Win/Linux | NMOX, Win/Linux |
|---|---|---|---|---|
| La palette de commandes | ⇧⌘P | **⇧⌘P** (ou ⌘I) — Recherche rapide | Ctrl+Shift+P | **Ctrl+Shift+P** (ou Ctrl+I) |
| Ouvrir un fichier par son nom | ⌘P | **⌘P** — Aller au fichier | Ctrl+P | **Ctrl+P** |
| L’arborescence des fichiers | ⇧⌘E | **⇧⌘E** — Studio de projet | Ctrl+Shift+E | **Ctrl+Shift+E** |
| Les extensions | ⇧⌘X | **⇧⌘X** — Outils ▸ Plugins | Ctrl+Shift+X | **Ctrl+Shift+X** |
| Le terminal, dans le dossier du projet | ⌃\` | **⌃\`** | Ctrl+\` | **Ctrl+\`** |
| Ouvrir un projet récent | ⌃R | **⌥⌘P** — Changer de projet… | Ctrl+R | **Ctrl+Alt+P** |
| Aller à un symbole du projet | ⌘T | **⌥⇧⌘O** | Ctrl+T | **Ctrl+Alt+Shift+O** |
| Aller à la définition | F12 | **F12** ou ⌘B | F12 | **F12** ou Ctrl+B |
| Trouver les références | ⇧F12 | **⇧F12** — Rechercher les utilisations | Shift+F12 | **Shift+F12** |
| Renommer un symbole | F2 | **F2** ou ⌃R | F2 | **F2** ou Ctrl+R |
| Correction rapide | ⌘. | **⌘.** ou ⌃↩ | Ctrl+. | **Alt+Enter** |
| Aller à la ligne | ⌃G | **⌃G** | Ctrl+G | **Ctrl+G** |
| Revenir en arrière / aller en avant | ⌃- / ⌃⇧- | **⌃- / ⌃⇧-** | Alt+← / Alt+→ | **Alt+← / Alt+→** |
| Commenter ou décommenter la ligne | ⌘/ | **⌘/** | Ctrl+/ | **Ctrl+/** |
| Commenter ou décommenter un bloc | ⇧⌥A | **⇧⌥A** | Shift+Alt+A (Ctrl+Shift+A sous Linux) | **Shift+Alt+A** (et Ctrl+Shift+A sous Linux) |
| Activer ou désactiver le retour automatique à la ligne | ⌥Z | **⌥Z** — Affichage ▸ Retour automatique à la ligne | Alt+Z | **Alt+Z** |
| Afficher les suggestions | ⌃Space | **⌃Space** | Ctrl+Space | **Ctrl+Space** |
| Ajouter l’occurrence suivante à la sélection | ⌘D | **⌘D** ou ⌘J | Ctrl+D | **Ctrl+D** ou Ctrl+J |
| Sélectionner toutes les occurrences | ⇧⌘L | **⌃⇧⌘J** | Ctrl+Shift+L | **Ctrl+Alt+Shift+J** |
| Ajouter un curseur au-dessus / en dessous | ⌥⌘↑ / ⌥⌘↓ | **⌥⌘↑ / ⌥⌘↓** | Ctrl+Alt+↑ / ↓ | **Alt+Shift+[ / ]** |
| Déplacer la ligne vers le haut / le bas | ⌥↑ / ⌥↓ | **⌃⇧↑ / ⌃⇧↓** | Alt+↑ / ↓ | **Alt+Shift+↑ / ↓** |
| Copier la ligne vers le bas | ⇧⌥↓ | **⌥⇧↓** | Shift+Alt+↓ | **Ctrl+Shift+↓** |
| Supprimer la ligne | ⇧⌘K | **⌘E** | Ctrl+Shift+K | **Ctrl+E** |
| Indenter la ligne | ⌘] | **⌘]** | Ctrl+] | **Alt+Shift+→** |
| Remplacer | ⌥⌘F | **⌥⌘F** ou ⌘R | Ctrl+H | **Ctrl+H** |
| Mettre le document en forme | ⇧⌥F | **⇧⌥F** ou ⌃⇧F | Shift+Alt+F | **Alt+Shift+F** |
| Fermer l’onglet de l’éditeur | ⌘W | **⌘W** | Ctrl+W | **Ctrl+W** |
| Copier le chemin du fichier en cours d’édition | ⌥⌘C | **⌥⌘C** — Édition ▸ Copier le chemin | Shift+Alt+C | **Ctrl+Alt+C** |
| Copier son chemin relatif | ⇧⌥⌘C | Édition ▸ Copier le chemin relatif (pas d’accord) | Ctrl+K Ctrl+Shift+Alt+C | Édition ▸ Copier le chemin relatif (pas d’accord) |
| Le panneau Problèmes | ⇧⌘M | **⌘6** — Éléments à traiter (⇧⌘M y bascule un signet) | Ctrl+Shift+M | **Ctrl+6** |
| Poser ou retirer un point d’arrêt | F9 | **⌘F8** | F9 | **Ctrl+F8** |
| Lancer le débogage | F5 | **⇧⌘F5** — Déboguer le fichier | F5 | **Ctrl+Shift+F5** |
| Exécuter sans débogage | ⌃F5 | **F6** — Exécuter Projet | Ctrl+F5 | **F6** |
| Réglages | ⌘, | **⌘,** — NMOX Studio ▸ Settings… | Ctrl+, | Outils ▸ Options (pas d’accord) |

Sous macOS, ⌘, est l’accord du menu de l’application lui-même ; chaque autre accord NMOX du tableau a été lu dans les raccourcis livrés, pas de mémoire. Quelques points qu’une cellule ne peut pas dire :

- **F5 est pris pendant le débogage.** Ici il veut dire *Continuer*, comme dans tout IDE de la famille NetBeans : une session de débogage démarre donc par **⇧⌘F5** (Ctrl+Shift+F5) et reprend par F5.
- **⌃R, c’est Renommer ici**, et c’est pourquoi *Changer de projet* vit sur ⌥⌘P au lieu de l’accord Open Recent de VS Code. Renommer fonctionne là où le langage du fichier le permet.
- **Copier le chemin relatif n’a pas d’accord.** Le ⇧⌥⌘C de VS Code est Ctrl+Alt+Shift+C sur un PC, c’est-à-dire le *Clear Split* de la plateforme dans tous les profils de raccourcis ; la ligne est dans le menu Édition, et ⇧⌘P la trouve par le titre même que lui donne VS Code, *File: Copy Relative Path of Active File*.
- **Ctrl+, sous Windows et Linux** recule dans l’historique de vos modifications, comme depuis toujours dans NetBeans ; les réglages sont sous Outils ▸ Options (sous macOS, **Settings…** dans le menu de l’application, ⌘,).

**Aide ▸ Raccourcis clavier…** liste chaque accord NMOX de votre profil de raccourcis actif, les quatre accords de VS Code compris, lu dans les raccourcis en marche pour qu’il ne puisse pas s’écarter de ce que font les touches.

### Chaque accord d’édition, mesuré

Les accords que cherchent vos mains habituées à VS Code pendant que vous éditez, chacun recherché dans les raccourcis livrés du profil par défaut sous macOS. Là où l’accord de VS Code était libre ici, il fait désormais ce que fait VS Code (les lignes marquées **Pareil :**) ; là où il signifiait déjà quelque chose sur quoi comptent les utilisateurs de NetBeans, il garde ce sens et la ligne dit où se trouve l’action de VS Code.

| VS Code, macOS | Ce que fait VS Code | Dans NMOX Studio |
|---|---|---|
| F12 | Go to Definition | **Pareil :** Aller à la déclaration, comme ⌘B |
| ⇧F12 | Go to References | **Pareil :** Rechercher les utilisations, comme ⌃F7 |
| F2 | Rename Symbol | **Pareil :** Renommer, comme ⌃R |
| ⌘. | Quick Fix | **Pareil :** les corrections de la ligne, telles que ⌃↩ les affiche |
| ⌥↑ / ⌥↓ | Move Line Up / Down | Occurrence marquée précédente / suivante ; déplacer la ligne, c’est ⌃⇧↑ / ⌃⇧↓ |
| ⇧⌥↑ / ⇧⌥↓ | Copy Line Up / Down | Pareil, comme depuis toujours |
| ⇧⌘K | Delete Line | Insérer le mot correspondant suivant (complète le mot à partir du fichier) ; supprimer la ligne, c’est ⌘E |
| ⌘L | Expand Line Selection | Sélectionner l’identifiant, qui garde l’accord dans le profil par défaut ; Étendre la sélection de ligne est dans la Recherche rapide sous ce nom, et sur ⌘L lui-même dans les profils de raccourcis IntelliJ et Emacs, qui laissent l’accord libre |
| ⇧⌘L | Select All Occurrences | Coller comme lignes dans l’éditeur ; sélectionner toutes les occurrences, c’est ⌃⇧⌘J |
| ⌘/ | Toggle Line Comment | Pareil, comme depuis toujours |
| ⇧⌥A | Toggle Block Comment | **Pareil :** entoure la sélection, ou la ligne du curseur, des délimiteurs de bloc du langage, et les retire à la pression suivante ; une sélection qui contient déjà un délimiteur est refusée plutôt que cassée |
| ⌘] | Indent Line | **Pareil :** Décaler à droite |
| ⌘[ | Outdent Line | Aller à l’accolade correspondante, comme depuis toujours ; désindenter, c’est ⇧Tab ou ⌃⇧← |
| ⌥Z | Toggle Word Wrap | **Pareil :** Affichage ▸ Retour automatique à la ligne. Il bascule le retour à la ligne pour tous les éditeurs du langage du fichier, pas pour un seul onglet, et le choix est enregistré |
| ⌘B | Toggle Sidebar | Aller à la déclaration ; ⇧⌘↩ n’affiche que l’éditeur, ⇧Esc agrandit la fenêtre où vous êtes |
| ⌘J | Toggle Panel | Ajoute l’occurrence suivante dans l’éditeur (comme ⌘D) ; la fenêtre Output, c’est ⌘4 |
| ⌘\ | Split Editor | Compléter le code dans l’éditeur ; scinder l’éditeur, c’est ⌃⇧⌘V |
| ⇧⌘T | Reopen Closed Editor | Pareil, comme depuis toujours : l’accord d’Ouvrir un fichier récent rouvre le dernier fichier fermé |
| ⌃- / ⌃⇧- | Go Back / Go Forward | **Pareil :** Précédent et Suivant parmi les endroits où vous avez modifié, comme ⌃← / ⌃→ (des accords que macOS garde d’habitude pour changer de bureau) |
| ⌘G / ⇧⌘G | Find Next / Previous | Pareil, comme depuis toujours |
| ⌥⌘F | Replace | **Pareil :** Remplacer, comme ⌘R |
| ⇧⌘F | Find in Files | Pareil, comme depuis toujours : Rechercher dans les projets |
| ⇧⌘O | Go to Symbol in Editor | Ouvrir un projet ; les symboles du fichier sont dans la Recherche rapide : ⌘I, puis `@name` comme dans VS Code (Naviguer ▸ Aller au symbole dans ce fichier… tape le `@` pour vous), et sous forme d’arbre dans le Navigateur (⌘7) |
| ⌘T | Go to Symbol in Workspace | Dans l’éditeur, échange les deux lettres autour du curseur ; les symboles du projet, c’est ⌥⇧⌘O |
| ⌃G | Go to Line | Pareil, comme depuis toujours |
| ⌘K ⌘S | Keyboard Shortcuts | ⌘K est Insérer le mot correspondant précédent ; la liste est **Aide ▸ Raccourcis clavier…** |
| ⌘, | Settings | Pareil, comme depuis toujours : NMOX Studio ▸ Settings… |
| ⇧⌥F | Format Document | **Pareil :** Mettre en forme, comme ⌃⇧F |

Les accords marqués **Pareil :** sont posés dans chaque profil de raccourcis qui les laisse libres, et un profil qui donne à l’un d’eux son propre sens le garde : F12 dans les profils Eclipse, Emacs et NetBeans 5.5, F2 dans tous les profils sauf celui par défaut, ⇧F12 dans Emacs et NetBeans 5.5, ⌃- et ⌃⇧- dans Emacs et IntelliJ, ⇧⌥F dans IntelliJ. Sous Windows et Linux, F12, ⇧F12 et F2 fonctionnent de la même façon ; les autres accords de VS Code y sont différents, et le tableau plus haut donne les deux.

*Activer/désactiver le commentaire de bloc* et *Activer/désactiver le retour automatique à la ligne* portent aussi leurs accords de VS Code sous Windows et Linux. Le retour automatique à la ligne est sur Alt+Z dans tous les profils. Le commentaire de bloc est sur Shift+Alt+A dans les profils par défaut, Emacs et IntelliJ (les profils Eclipse et NetBeans 5.5 y gardent leur propre Alt+Shift+A), et sous Linux il est aussi sur Ctrl+Shift+A, l’accord de VS Code sous Linux, dans les cinq. *Étendre la sélection de ligne* est sur Ctrl+L sous Windows et Linux dans le seul profil IntelliJ ; dans le profil Emacs, Ctrl+L y reste le *recenter* d’Emacs.

Un langage sans commentaire de bloc, Python par exemple, le dit dans la barre d’état. Dans un fichier HTML, Vue ou Svelte, un bloc `<script>` ou `<style>` est commenté dans son propre langage. Sans sélection, l’accord commente ou décommente la ligne ; il ne cherche pas un commentaire qui ne ferait qu’entourer le curseur : pour retirer un commentaire de plusieurs lignes, sélectionnez-le.

Dans la Recherche rapide, les symboles du fichier forment la catégorie **Symboles de ce fichier**. Taper `@` seul les liste depuis le haut du fichier ; `m name` (la lettre, un espace, puis le nom) cherche dans cette catégorie et dans aucune autre.


<a id="from-the-terminal"></a>
## Depuis le terminal

`code .` devient `nmox .` :

```bash
cd ~/code/my-app
nmox .          # open this folder (manifest or not) and aim the IDE at it
nmox src/app.ts # open one file
nmox src/app.ts:42  # open it at line 42 (code -g's form; -g itself is accepted)
nmox            # just start the IDE
```

La commande rend la main aussitôt, et un deuxième `nmox` confie son dossier à l’IDE déjà lancé. Une colonne (`src/app.ts:42:7`) est acceptée et l’éditeur s’ouvre au début de la ligne ; un nom qui n’existe pas est refusé dans le terminal au lieu de démarrer quoi que ce soit. `-r` est accepté, `-n` ouvre dans l’unique fenêtre, et les options `-a` et `-v` sont refusées par leur nom.

`-w` (`--wait`) ouvre un fichier et attend que vous fermiez son onglet, et `-d` (`--diff`) compare deux fichiers côte à côte : NMOX Studio peut donc être l’éditeur, le difftool et le mergetool de git, comme l’est `code --wait` :

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
git config --global merge.tool nmox
git config --global mergetool.nmox.cmd 'nmox -w "$MERGED"'
git config --global mergetool.nmox.trustExitCode false
```

`git commit` ouvre alors le message dans l’IDE ; enregistrez-le, fermez l’onglet, et git poursuit. Quitter l’IDE alors qu’un fichier est encore ouvert le rend aussi, avec ce qui a été enregistré. `git mergetool` ouvre chaque fichier en conflit de la même façon. Là où VS Code place *Accept Current Change | Accept Incoming Change | Accept Both Changes* au-dessus d’un conflit, NMOX Studio teinte les deux côtés et pose un avertissement sur la ligne `<<<<<<<` ; l’ampoule dans la marge, ou le correctif rapide avec le curseur sur cette ligne (⌘. sur Mac, Alt+Enter ailleurs), propose les trois mêmes, chacun une seule modification annulable. Enregistrez, fermez l’onglet, et git passe au fichier suivant. Les teintes et les trois choix sont là dans tout fichier qui porte des marqueurs de conflit, avec ou sans `git mergetool`. **Équipe ▸ Utiliser NMOX Studio avec Git…** règle ces mêmes lignes pour vous, après avoir montré la valeur actuelle de chacune. Homebrew, l’installateur Windows (*Ajouter « nmox » au PATH*) et les paquets Linux la mettent dans votre PATH ; pour une installation depuis le DMG, le [guide de l’utilisateur](user-guide.fr.md#2-first-launch) donne le lien en une ligne.

<a id="where-each-vs-code-idea-lives"></a>
## Où vit chaque idée de VS Code

| Dans VS Code | Dans NMOX Studio |
|---|---|
| **Explorer** | Le **Studio de projet** (⇧⌘E) — l’arborescence des fichiers (clic droit sur un fichier pour Copier le chemin, Copier le chemin relatif et Afficher dans le Finder), les gabarits et l’éditeur du `package.json` du projet. Le **Plan de travail** (⌥⌘0) est le port d’attache : fichiers ouverts, fichiers récents, projets récents et tout ce qui tourne. |
| **Command Palette** | La **Recherche rapide** (⇧⌘P ou ⌘I) — actions, fichiers, projets récents, appareils du rack, serveurs actifs, requêtes du Studio d’API, symboles. Les noms de commandes de VS Code marchent aussi : *Format Document*, *Toggle Terminal*, *Git: Commit* ou *Open Settings* liste l’action qui fait la même chose ici, sous **Commandes VS Code**, avec son propre nom et son accord. |
| **Extensions** | **Outils ▸ Plugins** installe et met à jour les modules, y compris les mises à jour de NMOX lui-même. Les extensions VS Code ne s’installent pas ici ; **Outils ▸ Extensions VS Code recommandées…** répond donc à la question que pose le `.vscode/extensions.json` d’un dépôt : pour chaque extension qu’il recommande, ce qui fait ce travail dans NMOX Studio — une fonction intégrée, une fenêtre qu’il peut ouvrir, un appareil du rack, un serveur de langage (et si ce serveur est installé), ou rien — et une extension qu’il ne connaît pas est dite inconnue plutôt que devinée. Une bonne part de ce qu’une extension ajoute dans VS Code est ici un **appareil du rack** — et vous pouvez en écrire un sous forme de fichier JSON dans `~/.nmox/devices.d` ([fichiers d’appareil](device-files.md)). |
| **`tasks.json`** | Le `.vscode/tasks.json` de votre dépôt est lu : tapez le nom d’une tâche dans la Recherche rapide (⇧⌘P ou ⌘I), et Entrée sur *Exécuter la tâche : build — make all* l’exécute (ou choisissez-la dans la liste que montre **Exécuter ▸ Exécuter une tâche…**) — la confiance de l’espace de travail est demandée d’abord sur un projet que vous n’avez pas approuvé, sa sortie va dans la fenêtre Output et le ■ de la barre d’outils l’arrête. Les tâches dont elle dépend par `dependsOn` s’exécutent d’abord, `${file}` est le fichier ouvert dans l’éditeur, et `${input:…}` vous pose sa question avant que quoi que ce soit ne démarre. À côté, les scripts de votre projet, exécutés tels qu’ils sont écrits : Exécuter / Compiler / Tester dans la barre d’outils (F6, F11, ⌃F6), **Exécuter le script** sur une ligne de la section scripts d’un `package.json`, l’**Explorateur NPM**, et le **Rack de tâches** (⌘9), où les tâches sont des appareils que vous câblez entre eux. |
| **`launch.json`** | Le `.vscode/launch.json` de votre dépôt est lu : tapez le nom d’une configuration dans la Recherche rapide (⇧⌘P ou ⌘I), et Entrée sur *Déboguer : Launch Program — ${workspaceFolder}/server.js* démarre le débogueur à points d’arrêt sur ce programme (**Déboguer ▸ Démarrer le débogage…** liste les mêmes configurations), après la question de la confiance de l’espace de travail. Les configurations Node (`node`, `pwa-node`) déboguent leur `program` dans leur `cwd`, avec leurs `args`, leur `env` et leur `envFile`, sous leur `runtimeExecutable` et leurs `runtimeArgs` — si bien qu’une configuration `npm run dev`, `tsx` ou `--experimental-strip-types` démarre telle qu’elle est écrite — et un `"request": "attach"` de Node s’attache à un processus `node --inspect` de cette machine. Les configurations Python (`python`, `debugpy`) déboguent leur `program` avec `args`, `env`, `envFile` et l’interpréteur que nomme leur `python` ; les configurations Chrome (`chrome`, `pwa-chrome`) ouvrent leur `url` (ou `file`) avec leur `webRoot`. `"program": "${file}"` débogue le fichier que montre votre éditeur, et une `preLaunchTask` qui nomme une tâche de votre `tasks.json` s’exécute d’abord : le débogueur démarre quand la tâche a réussi. Sans `launch.json`, **Déboguer le fichier** (⇧⌘F5) et le bouton de débogage de la barre d’outils déduisent quoi lancer du projet lui-même — l’entrée du script `start`, `main`, `index.js` — et l’appareil **INSPECTOR** du rack lance un débogueur comme une étape d’une chaîne. |
| **Terminal intégré** | La fenêtre **Terminal** (⌃\`) : la première pression démarre un shell dans le dossier du projet, les suivantes le ramènent. |
| **`settings.json`** | Outils ▸ Options (sous macOS, NMOX Studio ▸ Settings…). Le `.vscode/settings.json` d’un dépôt est lu lui aussi : `editor.tabSize`, `editor.insertSpaces` et `editor.indentSize` règlent son indentation pendant la frappe, `files.trimTrailingWhitespace` et `files.insertFinalNewline` (quand ils valent `true`) s’appliquent à l’enregistrement, `files.eol` est la fin de ligne avec laquelle les fichiers sont écrits, `"editor.formatOnSave": false` empêche un enregistrement de remettre le fichier en forme, et un bloc de langage comme `"[typescript]"` les remplace pour son langage. Là où le dépôt a aussi un `.editorconfig`, c’est le `.editorconfig` qui l’emporte partout où les deux s’expriment. |
| **Problems panel** | **Éléments à traiter** (⌘6), ou un clic sur le compte **✕ ⚠** de la barre d’état : les erreurs et avertissements des serveurs de langage, et les constats de lint et de typage des appareils PURITY et TYPEGUARD du rack. Comme dans VS Code, certains serveurs ne signalent que les fichiers que vous avez ouverts ; gopls signale tout le paquet. |
| **Search view** (`search.useIgnoreFiles`) | **Rechercher dans les projets** (⇧⌘F). Comme dans VS Code, la recherche passe ce qu’ignorent les fichiers `.gitignore` du dépôt et `.git/info/exclude` : `node_modules` et `dist/` restent donc hors des résultats quand le `.gitignore` les liste ; hors d’un dépôt, elle passe par leur nom `node_modules`, `dist`, `build` et les autres dossiers de build. Cochez **Rechercher dans les sources générées** dans sa boîte de dialogue pour y chercher aussi. Votre fichier global d’exclusions git n’est pas lu. |
| **Outline** | Le **Navigateur** (⌘7). |
| **Snippets** (`.vscode/*.code-snippets`) | Les fichiers d’extraits (snippets) que votre équipe a mis dans le dépôt sont lus tels quels : tapez un préfixe, pressez ⌃Space, et l’extrait est listé sous la forme *préfixe — Nom (description)* avec son fichier à côté, limité aux langages que nomme son `scope`. L’accepter insère le corps avec ses taquets de tabulation, ses miroirs, ses variables (`TM_FILENAME`, `CURRENT_YEAR`, `UUID` et les autres) et ses transformations `/regex/format/` ; Tab passe d’un taquet au suivant, et Entrée avance et se pose sur `$0` après le dernier. |
| **Auto Save** (`files.autoSave`) | **Fichier ▸ Enregistrement automatique** l’active et le désactive ; la fréquence des enregistrements, et le fait d’enregistrer aussi quand un fichier perd le focus, se règlent dans l’onglet Enregistrement automatique de la catégorie Éditeur des réglages. C’est votre propre préférence : le `files.autoSave` d’un dépôt n’est pas lu. |
| **Markdown preview** | L’onglet **Aperçu** en haut d’un éditeur Markdown, à côté de **Source**. |
| **Timeline** (historique local) | L’onglet **Historique** en haut de chaque éditeur : les versions du fichier que l’IDE a gardées au fil de vos enregistrements, chacune comparable avec le fichier tel qu’il est maintenant et restaurable. |
| **Breadcrumbs** | **Affichage ▸ Afficher le fil d’Ariane**. |
| **Source Control** | La pastille git de la barre d’état (branche et modifications, un clic vers l’historique) et le menu **Équipe**. |
| **Workspace Trust** | La même idée, appliquée avant que quoi que ce soit choisi par un dépôt ne s’exécute : ouvrir un projet cloné n’exécute rien tant que vous ne l’avez pas approuvé (**Confiance de l’espace de travail**). |
| **Keyboard Shortcuts editor** | Outils ▸ Options ▸ Raccourcis clavier (sous macOS, Settings… ▸ Raccourcis clavier) — modifiez n’importe quel accord, ou basculez tout le profil vers Eclipse, Emacs ou IntelliJ. |

La première fois que vous ouvrez un dépôt qui porte `.vscode/tasks.json`, `launch.json`, `settings.json` ou `extensions.json`, une notification dit ce qui a été trouvé et où cela se trouve ; cliquez dessus pour la Recherche rapide (ou, pour les extensions, pour le tableau qui dit ce qui couvre chacune). Elle ne le dit qu’une fois par projet.

<a id="what-is-honestly-different"></a>
## Ce qui est franchement différent

- **⌘D ajoute l’occurrence suivante dans le profil par défaut, pas dans tous les profils.** Le profil Eclipse garde ⌘D pour le *Delete Line* d’Eclipse, le profil NetBeans 5.5 pour *Shift Line Left* et le profil Emacs pour *kill word* (et Ctrl+D pour *delete character* sous Windows et Linux) ; le profil IntelliJ a ⌘D sous macOS et garde Ctrl+D pour *Duplicate Line* sous Windows et Linux. L’autre accord du geste change aussi selon le profil : ⌘J (Ctrl+J) dans le profil par défaut, ⌃J (Alt+J) dans Eclipse et IntelliJ, et aucun dans Emacs et NetBeans 5.5, où Raccourcis clavier peut lui en donner un.
- **⌃\` ouvre le Terminal et lui donne le focus ; il ne le masque pas.** Et tant que le Terminal a le focus, les touches appartiennent à votre shell : la deuxième pression atteint le shell au lieu de vous ramener dans l’éditeur.
- **`launch.json` est lu, et ce que le débogueur ne peut pas honorer est refusé.** Le débogueur transmet ici un programme, son dossier de travail, ses `args` (une liste de chaînes), son `env` et son `envFile`, et le runtime qui le démarre (`runtimeExecutable` et `runtimeArgs` pour Node, `python` pour Python) ; pour Node, il s’attache aussi à un processus déjà en cours. Une configuration qui fixe `postDebugTask`, `restart` ou tout autre champ qu’on ne lui a pas appris est listée mais pas démarrée : Entrée nomme ces champs dans la barre d’état. Démarrer le programme sans eux déboguerait autre chose que ce que dit le fichier. Des `args` ou des `runtimeArgs` écrits en une seule chaîne (VS Code la confie à un shell) et une valeur `null` dans `env` (qui supprime une variable) sont refusés de la même façon, tout comme une entrée `compounds`, un type sans adaptateur ici (`go`, `msedge`, `cppdbg` et les autres), un attachement à autre chose que Node, une valeur que seul VS Code peut fournir (`${input:…}`, `${command:…}`) et un programme, un dossier de travail ou un `envFile` hors du projet. Les champs qui ne font que façonner ce que montre le débogueur — `skipFiles`, `outFiles`, `sourceMaps`, `console`, `justMyCode`, `presentation` — sont acceptés mais pas appliqués ; la sortie du programme va dans la fenêtre Output. Cinq choses valent d’être sues avant de presser Entrée :
  - **Une `preLaunchTask` s’exécute d’abord, et doit réussir.** La tâche s’exécute comme le ferait Entrée sur elle dans la Recherche rapide — les tâches dont elle dépend par `dependsOn`, ses questions, son propre onglet Output — et le débogueur démarre quand elle s’est terminée avec le code de sortie 0 ; là où VS Code demande *Debug Anyway?* après une tâche en échec, ici la barre d’état dit que la configuration n’a pas été démarrée. Un `program` que la tâche construit (`dist/server.js`) est cherché après la tâche, pas avant. Un libellé que `tasks.json` ne définit pas, un libellé que deux tâches partagent, la forme objet (`{"type": "npm", "script": "build"}`) et une tâche d’arrière-plan (`"isBackground": true`, un observateur qui ne se termine jamais) sont refusés par leur nom avant que quoi que ce soit ne s’exécute.
  - **Un `envFile` qui n’existe pas est refusé**, là où VS Code démarre le programme sans lui. Ses variables s’ajoutent à l’environnement et une entrée de `env` l’emporte sur le fichier, comme dans VS Code. Le fichier est lu comme de simples lignes `NAME=value` (les lignes de commentaire, `export` et une paire de guillemets autour d’une valeur passent) ; une ligne que VS Code lirait autrement — un échappement entre guillemets doubles, un `#` après une valeur, un accent grave, et dans une configuration Python un `export` ou un `${NAME}` — est refusée en nommant le fichier et le numéro de ligne, jamais la valeur.
  - **`${file}`, `${relativeFile}`, `${fileBasename}`, `${fileBasenameNoExtension}`, `${fileExtname}`, `${fileDirname}` et les autres variables de fichier désignent le fichier de l’éditeur actif** : l’onglet qui a le focus quand c’est un éditeur, sinon l’onglet affiché dans la zone d’édition — le même fichier que désigne le `${file}` d’une tâche. Sans fichier ouvert, la configuration est refusée en nommant la variable, et un `${file}` hors du projet est refusé comme tout autre programme qui s’y trouverait.
  - **Un `runtimeExecutable` est un nom ou un chemin absolu.** Un nom (`npm`, `tsx`, `nodemon`) est cherché dans votre PATH puis dans le `node_modules/.bin` du projet ; `${workspaceFolder}/node_modules/.bin/tsx` doit exister ; un chemin relatif est refusé, parce que VS Code le chercherait comme un nom. Quand le runtime est toute la commande (`npm run dev` sans `program`), le script qu’il démarre est débogué comme une session à part, listée dans la fenêtre Sessions.
  - **Un attachement se fait sur cette machine.** `port` (9229 s’il n’est pas écrit) et une `address` valant `localhost`, `127.0.0.1` ou `::1` ; toute autre adresse est refusée, parce que ce n’est pas du développement à distance. Si rien n’écoute, la barre d’état le dit, et terminer la session laisse votre programme tourner.
- **`tasks.json` est lu, et ce qui ne peut pas s’exécuter tel qu’il est écrit est refusé.** Une tâche s’exécute après les tâches dont elle dépend par `dependsOn` (ensemble, ou l’une après l’autre avec `"dependsOrder": "sequence"`), avec `${file}`, `${relativeFile}`, `${lineNumber}`, `${selectedText}` et le reste de cette famille remplis d’après le fichier ouvert dans l’éditeur, et avec ses questions `${input:…}` (`promptString`, `pickString`) posées avant que quoi que ce soit ne démarre. Toute l’exécution est décidée d’abord : si l’une de ses tâches ne peut pas s’exécuter telle qu’elle est écrite, rien ne s’exécute, et Entrée dit dans la barre d’état quelle tâche et pourquoi. Cela couvre un libellé `dependsOn` que le fichier ne définit pas, une dépendance qui est une tâche d’arrière-plan (les problem matchers ne sont pas lus, donc rien ne dit quand elle est prête), `${file}` sans fichier ouvert, et une question que vous annulez. Une dépendance qui échoue arrête l’exécution à cet endroit. Restent refusés par leur nom : une valeur que seul VS Code peut fournir (`${config:…}`, `${command:…}`, une entrée de `"type": "command"`), une dépendance écrite comme un objet (`{"type": "npm", …}`) au lieu d’un libellé, un type de tâche fourni par une extension (`gulp`, `typescript`) et un dossier de travail hors du projet.
- **Les extraits sont ceux de votre dépôt, et trois choses diffèrent.** Un choix (`${1|a,b|}`) commence par sa première option, sans liste où choisir ; un emplacement imbriqué dans la valeur par défaut d’un autre devient une partie de cette valeur par défaut ; et un extrait dont la transformation ne peut pas être exécutée sans risque (une expression régulière que Java ne peut pas compiler, ou qui pourrait s’emballer) est laissé de côté et nommé dans le journal plutôt qu’inséré à moitié. Taper un préfixe n’ouvre pas la liste tout seul : c’est ⌃Space qui le fait. Les extraits sans `prefix`, les extraits de niveau utilisateur et `isFileTemplate` ne sont pas lus.
- **Une tâche `"type": "shell"` s’exécute dans le shell que VS Code utiliserait.** Sous macOS et Linux, c’est votre `$SHELL` avec `-c` (un zsh, bash ou fish de macOS démarre en shell de connexion, `-l`, comme le font les profils par défaut de VS Code) ; sous Windows, c’est PowerShell, `pwsh` s’il est installé. `options.shell` est respecté à la manière de VS Code : nommez un `executable` et il s’exécute avec exactement les `args` que vous donnez, si bien qu’un bash a besoin de `"args": ["-c"]`. Sous Windows, seuls PowerShell (des args qui finissent par `-Command`) et `cmd.exe` (des args qui finissent par `/c`) sont exécutés ; tout autre shell y est refusé par son nom plutôt que de recevoir une ligne de commande citée au jugé.
- **Il n’y a pas de profil de raccourcis « VS Code ».** Les accords ci-dessus s’appuient sur le profil par défaut et sur les quatre autres. Une exception voulue : dans le profil **Eclipse**, ⇧⌘E reste le *Switch to Editor* d’Eclipse, et dans l’éditeur ⇧⌘P et ⇧⌘X gardent leurs sens d’Eclipse (accolade correspondante, majuscules) — quelqu’un qui a choisi Eclipse s’attend à Eclipse.
- **Sous Linux, Ctrl+\` ouvre le Terminal, pas un sélecteur de fenêtres.** Le sélecteur est sur Ctrl+Tab. Sur un bureau qui prend Ctrl+Tab pour lui-même (KDE, par exemple), **Fenêtre ▸ Documents…** liste les fichiers ouverts à la place.
- **Les accords Ctrl+Alt peuvent entrer en collision avec AltGr.** Sous Windows, les dispositions de clavier qui tapent des caractères avec AltGr (le polonais, par exemple) envoient Ctrl+Alt pour cette touche. Si Ctrl+Alt+P ou Ctrl+Alt+K tape un caractère chez vous, déplacez *Changer de projet* ou les accords des expériences dans les raccourcis clavier.
- **Les extensions VS Code ne s’installent pas ici.** L’intelligence de langage vient des serveurs de langage que NMOX connaît (le Docteur de l’environnement liste ceux qui manquent et comment les installer), des grammaires propres à l’éditeur, et des plugins conçus pour la plateforme NetBeans.
