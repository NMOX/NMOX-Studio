# Tutoriel : écrire votre propre appareil de rack

<!-- languages -->
[English](your-own-device.md) · [Español](your-own-device.es.md) · **Français** · [Deutsch](your-own-device.de.md) · [Русский](your-own-device.ru.md) · [Українська](your-own-device.uk.md) · [Polski](your-own-device.pl.md) · [Português (Brasil)](your-own-device.pt.md) · [Bahasa Indonesia](your-own-device.id.md) · [Filipino](your-own-device.tl.md) · [Tiếng Việt](your-own-device.vi.md) · [简体中文](your-own-device.zh.md) · [हिन्दी](your-own-device.hi.md) · [עברית](your-own-device.he.md) · [العربية](your-own-device.ar.md)
<!-- /languages -->

*Une seule séance. Vous allez ajouter un appareil au rack avec un éditeur
de texte, presser son bouton, le regarder exécuter une vraie commande et
câbler sa sortie vers MONITOR — sans écrire une ligne de Java.*

Nouveau dans la 2.0.0. Le rack était livré avec cinquante-trois appareils
et, jusqu’ici, une seule façon d’en ajouter un cinquante-quatrième : écrire
un greffon NetBeans. Voici l’autre façon.

![Le Rack de tâches : l’étagère d’appareils à gauche, où apparaît un appareil venu de ~/.nmox/devices.d, à côté des appareils intégrés](../images/tabs/the-task-rack.png)

## 1. Créer le dossier

```bash
mkdir -p ~/.nmox/devices.d
```

C’est toute l’installation. Le rack lit le dossier à la demande : il n’y a
rien à redémarrer.

## 2. Écrire l’appareil

Mettez ceci dans `~/.nmox/devices.d/counter.json` :

```json
{
  "id": "com.example.counter",
  "title": "COUNTER",
  "tagline": "counts the files in the project",
  "accent": "#7FB3D5",
  "category": "OBSERVE",
  "usage": "COUNT lists the project's files of the dialled KIND and shows how many.\nPatch OUT into MONITOR to read the list, or DONE onward to chain.",
  "knobs": [
    { "key": "kind", "label": "KIND", "options": ["js", "ts", "css", "md"] }
  ],
  "ports": [
    { "id": "count", "label": "COUNT", "direction": "IN", "signal": "TRIGGER" },
    { "id": "done", "label": "DONE", "direction": "OUT", "signal": "TRIGGER" },
    { "id": "out", "label": "OUT", "direction": "OUT", "signal": "DATA" }
  ],
  "buttons": [
    { "label": "COUNT", "role": "QUERY",
      "command": ["git", "ls-files", "*.{{kind}}"],
      "emit": "done", "trigger": "count" }
  ]
}
```

Tout ce qui s’y trouve a un rôle : le **bouton rotatif** devient `{{kind}}`
dans la commande, le rôle **QUERY** peint le bouton en bleu (la loi des
couleurs : le bleu demande, le vert agit, le rouge arrête), et les deux
prises le rendent câblable.

## 3. Le monter

Ouvrez le **Rack de tâches** (`⌘9`, ou l’onglet Rack de tâches) et regardez
dans le tiroir **Observer** de l’étagère. COUNTER y est, avec votre accroche
en dessous. Faites-le glisser sur un rail.

Survolez sa fiche « Comment utiliser » : c’est votre texte `usage`, et c’est
pourquoi le format exige deux vraies lignes.

## 4. Le presser

> Remarquez qu’il n’y a pas de ligne `units` : l’étagère mesure la façade et
> choisit la plus petite hauteur qui convient (celui-ci demande 2U pour le
> bouton rotatif). Ne déclarez `units` que si vous voulez plus de place.

Pointez le rack sur un projet git, tournez **KIND** sur `js` et pressez
**COUNT**.

La première pression affiche la demande **Confiance de l'espace de travail**,
car un fichier d’appareil exécute de vraies commandes et l’hôte garde chaque
lancement exactement comme pour un appareil intégré. Accordez-la, et
l’afficheur montre la commande, puis la dernière ligne de sortie. La prise
DONE s’allume en vert.

Refusez-la plutôt et rien ne se lance — le refus est la fonctionnalité même.

## 5. Le câbler

Tirez un cordon de la prise **OUT** de COUNTER jusqu’à la prise **TAP** de
MONITOR. Pressez COUNT à nouveau : chaque ligne arrive sur le moniteur, car
une prise `OUT`/`DATA` déclarée reçoit la sortie de l’exécution sans aucune
configuration supplémentaire.

Tirez maintenant un cordon depuis le tick de TEMPO vers l’entrée **COUNT** de
COUNTER. L’appareil que vous avez écrit dans un éditeur de texte tourne
désormais à l’horloge.

## 6. Le casser exprès

Modifiez le fichier et remplacez la commande par quelque chose qui contient un
tube :

```json
"command": ["sh", "-c", "git ls-files | wc -l"]
```

Enregistrez, et COUNTER *disparaît* de l’étagère. C’est le format qui refuse
une ligne de shell : une commande est un tableau d’arguments (argv), pour
qu’un lecteur — vous dans six mois, ou un collègue qui relit le fichier —
voie exactement ce qui sera exécuté. Le journal de l’IDE dit quel fichier a
été écarté et pourquoi :

```
device file counter.json skipped: button "COUNT" command token
"git ls-files | wc -l" contains "|" — commands are argv, never a shell line
```

Remettez la forme tableau et il revient. Il en va de même pour un outil
désigné par un chemin (`./x.sh`), une `{{variable}}` inconnue ou un `usage`
d’une seule ligne : le fichier est écarté en entier plutôt qu’à moitié
chargé, car un appareil dont l’étiquette ment est pire que pas d’appareil du
tout.

## Ce que vous venez d’apprendre

- Un appareil est un **fichier** : `~/.nmox/devices.d/*.json`, lu à la
  demande, sans redémarrage ni compilation.
- Les boutons rotatifs deviennent des `{{variables}}` ; les rôles choisissent
  les couleurs ; les prises le rendent câblable et sa sortie lisible.
- **L’hôte garde les lois** — la confiance de l’espace de travail à chaque
  lancement, la loi des couleurs, le lexique des prises, la loi de
  l’étagère —, si bien qu’un fichier d’appareil ne peut exprimer ni une
  commande non gardée ni un GO rouge, même s’il essaie.
- Les refus sont bruyants dans le journal et totaux dans leur effet.

## Pour aller plus loin

- [device-files.md](../device-files.md) — la référence complète
- [Le Rack de tâches](the-task-rack.fr.md) — câblage, portes et préréglages
- [device-spi.md](../device-spi.md) — la SPI Java, pour les appareils qui ont
  besoin d’un vrai état : dessin personnalisé, interrogation périodique,
  connexions de longue durée
