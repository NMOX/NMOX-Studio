# La deuxième semaine

<!-- languages -->
[English](the-second-week.md) · [Español](the-second-week.es.md) · **Français** · [Deutsch](the-second-week.de.md) · [Русский](the-second-week.ru.md) · [Українська](the-second-week.uk.md) · [Polski](the-second-week.pl.md) · [Português (Brasil)](the-second-week.pt.md) · [Bahasa Indonesia](the-second-week.id.md) · [Filipino](the-second-week.tl.md) · [Tiếng Việt](the-second-week.vi.md) · [简体中文](the-second-week.zh.md) · [हिन्दी](the-second-week.hi.md) · [עברית](the-second-week.he.md) · [العربية](the-second-week.ar.md)
<!-- /languages -->

*Valider, relire, résoudre, proposer — sans partir vers un autre outil.*

La première heure, c’est ouvrir un projet et le lancer. La deuxième
semaine, c’est tout ce qui entoure le code : vingt commits par jour, un
diff à lire avant chacun, un conflit après un pull, une pull request après
un push, une trace de pile à remonter, un README à garder honnête. Ce
parcours tient en une séance, dans un dépôt git que vous avez déjà, et
chaque étape est une chose que vous referez demain.

## 1. Faire de NMOX Studio l’éditeur de git

**Faites :** Équipe ▸ **Utiliser NMOX Studio avec Git…**

**Vous voyez :** les six réglages git globaux qui font de NMOX Studio
l’éditeur, le difftool et le mergetool de git, chacun à côté de la valeur
qu’il a **maintenant**, pour que rien ne soit remplacé à votre insu.
**Appliquer** les définit (**Fermer** est le bouton par défaut, parce que
cela écrit votre configuration git globale) ; **Copier les commandes** met
plutôt les lignes `git config` dans le presse-papiers. Quand git utilise
déjà NMOX Studio, la boîte de dialogue le dit et ne propose pas Appliquer.

Les mêmes lignes, si vous préférez un terminal :

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
git config --global merge.tool nmox
git config --global mergetool.nmox.cmd 'nmox -w "$MERGED"'
git config --global mergetool.nmox.trustExitCode false
```

## 2. Valider

**Faites :** modifiez un fichier, puis dans un terminal :

```bash
git commit -a
```

**Vous voyez :** le message de commit s’ouvre dans NMOX Studio, et la ligne
d’état indique qu’un terminal l’attend. Les lignes `#` de git sont des
commentaires ; seul ce que vous écrivez est vérifié par le correcteur
orthographique ; une ligne de résumé de plus de 72 caractères, là où les
outils de git eux-mêmes la coupent, reçoit un avertissement au-delà du
72e. Enregistrez, fermez l’onglet, et le commit est fait — le terminal
attendait que vous le fassiez. Quitter l’IDE avec le message encore ouvert
le rend aussi à git, avec ce qui avait été enregistré.

`git rebase -i` ouvre sa liste de la même façon : chaque commande et
chaque commit mis en évidence, et **Commenter/décommenter** écarte une
ligne sans la supprimer.

## 3. Savoir où vous en êtes

**Vous voyez :** la **pastille ⎇** dans la ligne d’état — `⎇ main ±3 ↑2 ↓1`
est votre branche, trois fichiers modifiés, deux commits à pousser et un à
tirer (les flèches n’apparaissent que s’il y a quelque chose à pousser ou
à tirer). Son menu commence par **Changer de branche…**, **Valider…**, **Tirer (pull)…** et **Pousser (push)…**.

**Faites :** placez le curseur sur n’importe quelle ligne d’un fichier
suivi.

**Vous voyez :** à côté de la pastille, qui a modifié cette ligne en
dernier, il y a combien de temps et pourquoi :
`Ada Lovelace, il y a 3 jours · Fix the parser`. Une ligne que vous n’avez
pas encore validée le dit, et un fichier aux modifications non
enregistrées dit cela plutôt que de nommer le mauvais auteur. Cliquez sur
la note pour les annotations de tout le fichier, le commit sur GitHub ou son
identifiant ; **Affichage ▸ Auteur de la ligne** la désactive.

## 4. Relire un diff

**Faites :**

```bash
git difftool
```

**Vous voyez :** chaque fichier modifié côte à côte dans la vue de
différences de NMOX Studio, avec **Différence précédente / Différence
suivante** et « Différence 2 sur 5 » au-dessus. Un fichier ajouté ou
supprimé montre son côté absent comme un volet vide (« aucun fichier ») ;
un fichier binaire s’affiche comme binaire, et la barre dit si deux
binaires diffèrent. Fermez l’onglet et git passe au fichier suivant.

## 5. Résoudre un conflit

**Faites :** fusionnez une branche qui entre en conflit, puis :

```bash
git mergetool
```

**Vous voyez :** le fichier en conflit dans l’éditeur, le côté actuel et le
côté entrant teintés, et un avertissement sur chaque ligne `<<<<<<<`.
Placez-y le curseur et appuyez sur ⌘. (Alt+Entrée ailleurs), ou utilisez
**Source ▸ Corriger le code…** : **Accepter la modification actuelle**,
**Accepter la modification entrante** ou **Accepter les deux
modifications**, chacune une seule modification annulable. Un bloc qui a
changé depuis la proposition est refusé plutôt que deviné. Enregistrez,
fermez l’onglet et répondez à git.

## 6. Le proposer

**Faites :** poussez, puis Équipe ▸ **Nouvelle pull request sur GitHub**
(aussi dans le menu de la pastille).

**Vous voyez :** la page New Pull Request de GitHub elle-même pour votre
branche, dans votre propre navigateur, là où vous êtes connecté. Depuis un
éditeur, **Édition ▸ Ouvrir sur GitHub** et **Copier le lien GitHub**
donnent la ligne ou les lignes où vous êtes ; dans l’arbre du Studio de
projet, ils donnent un fichier ou un dossier.

## 7. Remonter un échec

**Faites :** lancez vos tests dans le Terminal (⌃\`) jusqu’à ce que l’un
d’eux échoue.

**Vous voyez :** un emplacement dans la sortie — `src/app.ts:42:7`, un
cadre de pile `(/abs/app.js:10:5)`, `--> src/main.rs:3:5`,
`File "x.py", line 12` — s’ouvre à cette ligne et cette colonne d’un
⌘-clic (Ctrl-clic sous Windows et Linux). Une URL ou `localhost:3000`
n’est jamais un lien, et un chemin qui n’existe pas est refusé par son nom
plutôt que deviné.

## 8. Garder le README honnête

**Faites :** Outils ▸ **Vérifier les liens Markdown…**

**Vous voyez :** chaque lien relatif et chaque image du Markdown du projet
vérifiés comme GitHub les affiche — le fichier doit exister, et un
`#heading` doit être un titre de ce fichier. Un lien mort est une erreur,
un titre manquant un avertissement, tous deux en soulignés ondulés et dans
les Éléments à traiter, avec une phrase dans la ligne d’état. Rien ne
quitte votre machine : un lien avec un schéma n’est pas vérifié.

## 9. Le confier à un agent

**Faites :** Outils ▸ **Agent Port (MCP)…**, cochez **Conserver cette
adresse et ce jeton**, puis **Copier pour Claude Code**, et exécutez une
fois la ligne copiée.

**Vous voyez :** un agent qui peut lire ce que l’IDE sait — le projet
visé, ce qui est servi et ce qui tourne, ce que vous éditez, le dernier
échec — et qui se connecte encore demain, parce que le jeton est gardé
dans le trousseau de votre système et que le port est réutilisé. Le port
reste en lecture seule par construction. Décocher **Conserver cette
adresse et ce jeton** supprime l’entrée du trousseau.

## Ce que vous avez fait

Vous avez écrit un message de commit, lu un diff, résolu un conflit,
ouvert une pull request, trouvé qui avait écrit une ligne, suivi une trace
de pile et vérifié un README — le tout dans la fenêtre où vous étiez déjà.
Rien de tout cela n’a remplacé git : chaque étape est celle de git, ouverte
là où vous travaillez.
