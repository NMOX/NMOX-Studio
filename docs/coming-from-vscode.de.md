# Von VS Code kommend

<!-- languages -->
[English](coming-from-vscode.md) · [Español](coming-from-vscode.es.md) · [Français](coming-from-vscode.fr.md) · **Deutsch** · [Русский](coming-from-vscode.ru.md) · [Українська](coming-from-vscode.uk.md) · [Polski](coming-from-vscode.pl.md) · [Português (Brasil)](coming-from-vscode.pt.md) · [Bahasa Indonesia](coming-from-vscode.id.md) · [Filipino](coming-from-vscode.tl.md) · [Tiếng Việt](coming-from-vscode.vi.md) · [简体中文](coming-from-vscode.zh.md) · [हिन्दी](coming-from-vscode.hi.md) · [עברית](coming-from-vscode.he.md) · [العربية](coming-from-vscode.ar.md)
<!-- /languages -->

Ihre Hände wissen schon, wo alles ist. Diese Seite ordnet diese Gewohnheiten NMOX Studio zu: zuerst die Tastenkürzel, dann wo jede Idee aus VS Code hier wohnt, dann was ehrlich anders ist.

Die ersten vier Kürzel, die jemand aus VS Code drückt, tun, was er erwartet: **⇧⌘P** öffnet die Befehlspalette, **⇧⌘E** den Dateibaum, **⇧⌘X** die Plugins und **⌃\`** das Terminal. Sie sind in allen fünf Tastaturbelegungsprofilen eingetragen, die die Plattform mitbringt, und eine Prüfung beim Bauen löst jedes davon über die zusammengesetzte Tastaturbelegung unter macOS, Windows und Linux auf, damit an seiner Stelle nichts anderes ausgelöst wird.

<a id="the-chords"></a>
## Die Tastenkürzel

Die macOS-Spalten benutzen die Zeichen der Menüleiste (⌃ Control, ⌥ Wahltaste, ⇧ Umschalt, ⌘ Befehlstaste); die Spalten für Windows und Linux sind dasselbe Kürzel auf einer PC-Tastatur.

| Sie wollen | VS Code, macOS | NMOX, macOS | VS Code, Win/Linux | NMOX, Win/Linux |
|---|---|---|---|---|
| Befehlspalette | ⇧⌘P | **⇧⌘P** (oder ⌘I) — Schnellsuche | Strg+Umschalt+P | **Strg+Umschalt+P** (oder Strg+I) |
| Eine Datei nach Namen öffnen | ⌘P | **⌘P** — Gehe zu Datei | Strg+P | **Strg+P** |
| Der Dateibaum | ⇧⌘E | **⇧⌘E** — Projekt-Studio | Strg+Umschalt+E | **Strg+Umschalt+E** |
| Erweiterungen | ⇧⌘X | **⇧⌘X** — Extras ▸ Plugins | Strg+Umschalt+X | **Strg+Umschalt+X** |
| Das Terminal, im Projektordner | ⌃\` | **⌃\`** | Strg+\` | **Strg+\`** |
| Ein zuletzt benutztes Projekt öffnen | ⌃R | **⌥⌘P** — Projekt wechseln… | Strg+R | **Strg+Alt+P** |
| Zu einem Symbol im Projekt springen | ⌘T | **⌥⇧⌘O** | Strg+T | **Strg+Alt+Umschalt+O** |
| Zur Definition springen | F12 | **⌘B** | F12 | **Strg+B** |
| Ein Symbol umbenennen | F2 | **⌃R** | F2 | **Strg+R** |
| Zu einer Zeile springen | ⌃G | **⌃G** | Strg+G | **Strg+G** |
| Zeilenkommentar umschalten | ⌘/ | **⌘/** | Strg+/ | **Strg+/** |
| Vorschläge zeigen | ⌃Space | **⌃Space** | Strg+Leertaste | **Strg+Leertaste** |
| Das nächste Vorkommen zur Auswahl hinzufügen | ⌘D | **⌘D** oder ⌘J | Strg+D | **Strg+D** oder Strg+J |
| Jedes Vorkommen auswählen | ⇧⌘L | **⌃⇧⌘J** | Strg+Umschalt+L | **Strg+Alt+Umschalt+J** |
| Einen Cursor darüber / darunter hinzufügen | ⌥⌘↑ / ⌥⌘↓ | **⌥⌘↑ / ⌥⌘↓** | Strg+Alt+↑ / ↓ | **Alt+Umschalt+[ / ]** |
| Die Zeile nach oben / unten verschieben | ⌥↑ / ⌥↓ | **⌃⇧↑ / ⌃⇧↓** | Alt+↑ / ↓ | **Alt+Umschalt+↑ / ↓** |
| Die Zeile nach unten kopieren | ⇧⌥↓ | **⌥⇧↓** | Umschalt+Alt+↓ | **Strg+Umschalt+↓** |
| Die Zeile löschen | ⇧⌘K | **⌘E** | Strg+Umschalt+K | **Strg+E** |
| Das Dokument formatieren | ⇧⌥F | **⌃⇧F** | Umschalt+Alt+F | **Alt+Umschalt+F** |
| Den Editor-Tab schließen | ⌘W | **⌘W** | Strg+W | **Strg+W** |
| Das Problemfenster | ⇧⌘M | **⌘6** — Aufgabenliste (⇧⌘M setzt hier ein Lesezeichen oder entfernt es) | Strg+Umschalt+M | **Strg+6** |
| Einen Haltepunkt umschalten | F9 | **⌘F8** | F9 | **Strg+F8** |
| Das Debugging starten | F5 | **⇧⌘F5** — Datei debuggen | F5 | **Strg+Umschalt+F5** |
| Ohne Debugging ausführen | ⌃F5 | **F6** — Ausführen (▶) | Strg+F5 | **F6** |
| Einstellungen | ⌘, | **⌘,** — NMOX Studio ▸ Einstellungen… | Strg+, | Extras ▸ Optionen (kein Kürzel) |

Jedes NMOX-Kürzel in der Tabelle wurde aus der ausgelieferten Tastaturbelegung gelesen, nicht aus dem Gedächtnis (⌘, gehört dem App-Menü von macOS). Ein paar Dinge kann die Tabelle nicht in einer Zelle sagen:

- **F5 ist beim Debuggen belegt.** Hier bedeutet es *Fortsetzen*, wie in jeder IDE aus der NetBeans-Familie, daher startet ein Debug-Lauf mit **⇧⌘F5** (Strg+Umschalt+F5) und läuft mit F5 weiter.
- **⌃R ist hier Umbenennen**, deshalb liegt *Projekt wechseln* auf ⌥⌘P statt auf dem Kürzel, mit dem VS Code zuletzt Geöffnetes öffnet. Umbenennen funktioniert dort, wo die Sprache hinter der Datei es unterstützt.
- **Strg+, unter Windows und Linux** geht in Ihrem Bearbeitungsverlauf zurück, wie es in NetBeans schon immer war; die Einstellungen liegen unter Extras ▸ Optionen (unter macOS die **Einstellungen…** im App-Menü, ⌘,).

**Hilfe ▸ Tastenkürzel…** listet jedes NMOX-Kürzel Ihrer aktiven Tastaturbelegung auf, die vier Kürzel aus VS Code eingeschlossen, gelesen aus der laufenden Tastaturbelegung, sodass die Liste nicht von dem abweichen kann, was die Tasten tun.


<a id="from-the-terminal"></a>
## Aus dem Terminal

`code .` ist `nmox .`:

```bash
cd ~/code/my-app
nmox .          # open this folder (manifest or not) and aim the IDE at it
nmox src/app.ts # open one file
nmox src/app.ts:42  # open it at line 42 (code -g's form; -g itself is accepted)
nmox            # just start the IDE
```

Der Befehl kehrt sofort zurück, und ein zweites `nmox` übergibt seinen Ordner an die IDE, die schon läuft. Eine Spalte (`src/app.ts:42:7`) wird akzeptiert, und der Editor öffnet am Anfang der Zeile; ein Name, den es nicht gibt, wird im Terminal abgelehnt, statt irgendetwas zu starten. `-r` wird akzeptiert, `-n` öffnet im einzigen Fenster, und `--wait`, `--diff` und die übrigen VS-Code-eigenen Optionen werden mit ihrem Namen abgelehnt. Homebrew, das Windows-Installationsprogramm (*„nmox“ zum PATH hinzufügen*) und die Linux-Pakete legen ihn in Ihren PATH; für eine Installation aus dem DMG zeigt das [Benutzerhandbuch](user-guide.de.md#2-first-launch) den einzeiligen Link.

<a id="where-each-vs-code-idea-lives"></a>
## Wo jede Idee aus VS Code wohnt

| In VS Code | In NMOX Studio |
|---|---|
| **Explorer** | **Projekt-Studio** (⇧⌘E) — der Dateibaum (Rechtsklick auf eine Datei für Pfad kopieren, Relativen Pfad kopieren und Im Finder anzeigen), die Vorlagen und der Editor für die `package.json` des Projekts. Der **Arbeitsplatz** (⌥⌘0) ist die Basis: offene Dateien, zuletzt benutzte Dateien, zuletzt benutzte Projekte und alles, was läuft. |
| **Befehlspalette** | **Schnellsuche** (⇧⌘P oder ⌘I) — Aktionen, Dateien, zuletzt benutzte Projekte, Rack-Geräte, aktive Server, Anfragen des API-Studios, Symbole. Auch die Befehlsnamen von VS Code funktionieren: *Format Document*, *Toggle Terminal*, *Git: Commit* oder *Open Settings* listet die Aktion, die hier dasselbe tut, unter **VS-Code-Befehle**, mit ihrem eigenen Namen und Kürzel. |
| **Erweiterungen** | **Extras ▸ Plugins** installiert und aktualisiert Module, die eigenen Aktualisierungen von NMOX eingeschlossen. Vieles, was in VS Code eine Erweiterung hinzufügt, ist hier ein **Rack-Gerät** — und eines davon können Sie als JSON-Datei in `~/.nmox/devices.d` schreiben ([Gerätedateien](device-files.md)). |
| **`tasks.json`** | Die `.vscode/tasks.json` Ihres Repositorys wird gelesen: Tippen Sie den Namen einer Aufgabe in die Schnellsuche (⇧⌘P oder ⌘I), und Eingabe auf *Aufgabe ausführen: build — make all* führt sie aus — bei einem Projekt, dem Sie noch nicht vertraut haben, fragt zuerst das Arbeitsbereichsvertrauen, die Ausgabe erscheint im Fenster Output, und das ■ der Werkzeugleiste hält sie an. Daneben die eigenen Skripte Ihres Projekts, ausgeführt so, wie sie geschrieben sind: Ausführen, Erstellen und Testen in der Werkzeugleiste (F6, F11, ⌃F6), **Skript ausführen** auf einer Zeile im `scripts`-Abschnitt der `package.json`, der **NPM-Explorer** und das **Task-Rack** (⌘9), wo Aufgaben Geräte sind, die Sie miteinander verkabeln. |
| **`launch.json`** | Die `.vscode/launch.json` Ihres Repositorys wird gelesen: Tippen Sie den Namen einer Konfiguration in die Schnellsuche (⇧⌘P oder ⌘I), und Eingabe auf *Debuggen: Launch Program — ${workspaceFolder}/server.js* startet den Haltepunkt-Debugger für dieses Programm, nachdem zuerst das Arbeitsbereichsvertrauen gefragt hat. Node-Konfigurationen (`node`, `pwa-node`) und Python-Konfigurationen (`python`, `debugpy`) debuggen ihr `program` in ihrem `cwd`, mit ihren `args` und ihrem `env`; Chrome-Konfigurationen (`chrome`, `pwa-chrome`) öffnen ihre `url` (oder `file`) mit ihrem `webRoot`. Ohne `launch.json` ermitteln **Datei debuggen** (⇧⌘F5) und der Debug-Knopf der Werkzeugleiste aus dem Projekt selbst, was zu starten ist — den Einstieg des `start`-Skripts, `main`, `index.js` —, und das Rack-Gerät **INSPECTOR** startet einen Debugger als Schritt in einer Pipeline. |
| **Integriertes Terminal** | Das Fenster **Terminal** (⌃\`): Der erste Druck startet eine Shell im Projektordner, spätere Drücke holen sie zurück. |
| **`settings.json`** | Extras ▸ Optionen (unter macOS NMOX Studio ▸ Einstellungen…). Auch die `.vscode/settings.json` eines Repositorys wird gelesen: `editor.tabSize`, `editor.insertSpaces` und `editor.indentSize` legen seine Einrückung beim Tippen fest, `files.trimTrailingWhitespace` und `files.insertFinalNewline` (wenn `true`) greifen beim Speichern, und ein Sprachblock wie `"[typescript]"` überschreibt sie für seine Sprache. Hat das Repository außerdem eine `.editorconfig`, gewinnt die `.editorconfig` überall, wo beide etwas sagen. |
| **Problemfenster** | **Aufgabenliste** (⌘6), oder ein Klick auf die Zahl **✕ ⚠** in der Statuszeile: die Fehler und Warnungen der Sprachserver und die Lint- und Typbefunde der Rack-Geräte PURITY und TYPEGUARD. Wie in VS Code melden manche Server nur die Dateien, die Sie geöffnet haben; gopls meldet das ganze Paket. |
| **Gliederung** | Der **Navigator** (⌘7). |
| **Quellcodeverwaltung** | Das Git-Zeichen in der Statuszeile (Zweig und Änderungen, ein Klick zum Verlauf) und das Menü **Team**. |
| **Arbeitsbereichsvertrauen** | Dieselbe Idee, durchgesetzt, bevor irgendetwas läuft, das ein Repository ausgesucht hat: Ein geklontes Projekt zu öffnen führt nichts aus, bis Sie ihm vertrauen. |
| **Editor für Tastenkombinationen** | Extras ▸ Optionen ▸ Tastaturbelegung (unter macOS Einstellungen… ▸ Tastaturbelegung) — ändern Sie jedes Kürzel, oder schalten Sie das ganze Profil auf Eclipse, Emacs oder IntelliJ um. |

Wenn Sie zum ersten Mal ein Repository öffnen, das `.vscode/tasks.json`, `launch.json` oder `settings.json` mitbringt, sagt eine Meldung, was gefunden wurde und wo es liegt; ein Klick darauf öffnet die Schnellsuche. Sie sagt es einmal je Projekt.

<a id="what-is-honestly-different"></a>
## Was ehrlich anders ist

- **⌘D fügt in der Standardbelegung das nächste Vorkommen hinzu, nicht in jedem Profil.** Das Profil Eclipse behält ⌘D als *Delete Line* von Eclipse und das Profil NetBeans 5.5 als *Shift Line Left*; dort ist ⌘J (Strg+J) dieselbe Geste.
- **⌃\` öffnet das Terminal und gibt ihm den Fokus; es blendet es nicht aus.** Und solange das Terminal den Fokus hat, gehören die Tasten Ihrer Shell, sodass ein zweiter Druck bei der Shell ankommt, statt Sie zurück in den Editor zu bringen.
- **`launch.json` wird gelesen, und was der Debugger nicht einhalten kann, wird abgelehnt.** Der Debugger hier übergibt ein Programm, seinen Arbeitsordner, seine `args` (eine Liste von Zeichenketten) und sein `env` (Zeichenketten, die zur geerbten Umgebung hinzukommen); eine Konfiguration, die `envFile`, `runtimeExecutable`, `runtimeArgs`, `preLaunchTask` oder ein anderes Feld setzt, das ihm nicht beigebracht wurde, wird deshalb aufgelistet, aber nicht gestartet: Eingabe nennt die Felder in der Statuszeile. Das Programm ohne sie zu starten, würde etwas anderes debuggen, als die Datei sagt. `args` als eine einzige Zeichenkette (VS Code gibt sie an eine Shell) und ein `env`-Wert `null` (der eine Variable entfernt) werden genauso abgelehnt, ebenso `"request": "attach"`, ein Eintrag in `compounds`, ein Typ ohne Adapter hier (`go`, `msedge`, `cppdbg` und die übrigen), ein Wert, den nur VS Code liefern kann (`${file}`, `${input:…}`), und ein Pfad außerhalb des Projekts. Felder, die nur gestalten, was der Debugger zeigt — `skipFiles`, `outFiles`, `sourceMaps`, `console`, `justMyCode`, `presentation` —, werden akzeptiert, aber nicht angewendet; die Ausgabe des Programms geht ins Fenster Output.
- **`tasks.json` wird gelesen, und was so, wie es geschrieben steht, nicht laufen kann, wird abgelehnt.** Eine Aufgabe, die einen Wert verwendet, den nur VS Code liefern kann (`${input:…}`, `${file}`, `${config:…}`, `${command:…}`), oder die per `dependsOn` von einer anderen Aufgabe abhängt, wird aufgelistet, aber nicht ausgeführt: Eingabe nennt in der Statuszeile die Variable oder die Aufgabe. Sie mit leer gelassenem Wert oder ohne die Aufgabe, von der sie abhängt, auszuführen, würde etwas anderes ausführen, als die Datei sagt. Ebenso ein Aufgabentyp, den eine Erweiterung mitbringt (`gulp`, `typescript`), und ein Arbeitsordner außerhalb des Projekts.
- **Eine Aufgabe mit `"type": "shell"` läuft in der Shell, die VS Code verwenden würde.** Unter macOS und Linux ist das Ihre `$SHELL` mit `-c` (eine zsh, bash oder fish unter macOS startet als Login-Shell, `-l`, wie es die Standardprofile von VS Code tun); unter Windows ist es PowerShell, `pwsh`, wenn es installiert ist. `options.shell` wird so beachtet wie in VS Code: Nennen Sie ein `executable`, und es läuft mit genau den `args`, die Sie angeben, sodass eine bash `"args": ["-c"]` braucht. Unter Windows werden nur PowerShell (Argumente, die auf `-Command` enden) und `cmd.exe` (Argumente, die auf `/c` enden) ausgeführt; jede andere Shell wird dort mit Namen abgelehnt, statt eine auf gut Glück gequotete Befehlszeile zu bekommen.
- **Es gibt kein Tastaturbelegungsprofil „VS Code“.** Die Kürzel oben liegen auf dem Standardprofil und den vier anderen. Eine gewollte Ausnahme: Im Profil **Eclipse** bleibt ⇧⌘E das eigene *Switch to Editor* von Eclipse, und im Editor behalten ⇧⌘P und ⇧⌘X ihre Bedeutung aus Eclipse (passende Klammer, Großbuchstaben) — wer Eclipse gewählt hat, erwartet Eclipse.
- **Unter Linux öffnet Strg+\` das Terminal, keinen Fensterwechsler.** Der Wechsler liegt auf Strg+Tab. In einer Arbeitsumgebung, die Strg+Tab für sich beansprucht (KDE zum Beispiel), listet stattdessen **Fenster ▸ Dokumente…** die offenen Dateien.
- **Die Kürzel mit Strg+Alt können mit AltGr kollidieren.** Unter Windows senden Tastaturlayouts, die Zeichen mit AltGr tippen (Polnisch zum Beispiel), dafür Strg+Alt. Tippt Strg+Alt+P oder Strg+Alt+K bei Ihnen ein Zeichen, legen Sie *Projekt wechseln* oder die Experiment-Kürzel unter Tastaturbelegung auf andere Tasten.
- **Erweiterungen für VS Code lassen sich hier nicht installieren.** Die Sprachintelligenz kommt von den Sprachservern, die NMOX kennt (der Environment Doctor zeigt, was fehlt und wie man es installiert), von den eigenen Grammatiken des Editors und von Plugins, die für die NetBeans-Plattform gebaut sind.
