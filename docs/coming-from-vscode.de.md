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
| Das nächste Vorkommen zur Auswahl hinzufügen | ⌘D | **⌘J** | Strg+D | **Strg+J** |
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
nmox            # just start the IDE
```

Der Befehl kehrt sofort zurück, und ein zweites `nmox` übergibt seinen Ordner an die IDE, die schon läuft. Homebrew, das Windows-Installationsprogramm (*„nmox“ zum PATH hinzufügen*) und die Linux-Pakete legen ihn in Ihren PATH; für eine Installation aus dem DMG zeigt das [Benutzerhandbuch](user-guide.de.md#2-first-launch) den einzeiligen Link.

<a id="where-each-vs-code-idea-lives"></a>
## Wo jede Idee aus VS Code wohnt

| In VS Code | In NMOX Studio |
|---|---|
| **Explorer** | **Projekt-Studio** (⇧⌘E) — der Dateibaum, die Vorlagen und der Editor für die `package.json` des Projekts. Der **Arbeitsplatz** (⌥⌘0) ist die Basis: offene Dateien, zuletzt benutzte Dateien, zuletzt benutzte Projekte und alles, was läuft. |
| **Befehlspalette** | **Schnellsuche** (⇧⌘P oder ⌘I) — Aktionen, Dateien, zuletzt benutzte Projekte, Rack-Geräte, aktive Server, Anfragen des API-Studios, Symbole. |
| **Erweiterungen** | **Extras ▸ Plugins** installiert und aktualisiert Module, die eigenen Aktualisierungen von NMOX eingeschlossen. Vieles, was in VS Code eine Erweiterung hinzufügt, ist hier ein **Rack-Gerät** — und eines davon können Sie als JSON-Datei in `~/.nmox/devices.d` schreiben ([Gerätedateien](device-files.md)). |
| **`tasks.json`** | Die eigenen Skripte Ihres Projekts, ausgeführt so, wie sie geschrieben sind: Ausführen, Erstellen und Testen in der Werkzeugleiste (F6, F11, ⌃F6), **Skript ausführen** auf einer Zeile im `scripts`-Abschnitt der `package.json`, der **NPM-Explorer** und das **Task-Rack** (⌘9), wo Aufgaben Geräte sind, die Sie miteinander verkabeln. |
| **`launch.json`** | **Datei debuggen** (⇧⌘F5) und der Debug-Knopf der Werkzeugleiste ermitteln aus dem Projekt selbst, was zu starten ist — den Einstieg des `start`-Skripts, `main`, `index.js` —, und das Rack-Gerät **INSPECTOR** startet einen Debugger als Schritt in einer Pipeline. |
| **Integriertes Terminal** | Das Fenster **Terminal** (⌃\`): Der erste Druck startet eine Shell im Projektordner, spätere Drücke holen sie zurück. |
| **`settings.json`** | Extras ▸ Optionen (unter macOS NMOX Studio ▸ Einstellungen…). Die `.editorconfig` Ihres Projekts wirkt beim Tippen und beim Speichern. |
| **Problemfenster** | **Aufgabenliste** (⌘6): die Fehler und Warnungen der Sprachserver und die Lint- und Typbefunde der Rack-Geräte PURITY und TYPEGUARD. Wie in VS Code melden manche Server nur die Dateien, die Sie geöffnet haben; gopls meldet das ganze Paket. |
| **Gliederung** | Der **Navigator** (⌘7). |
| **Quellcodeverwaltung** | Das Git-Zeichen in der Statuszeile (Zweig und Änderungen, ein Klick zum Verlauf) und das Menü **Team**. |
| **Arbeitsbereichsvertrauen** | Dieselbe Idee, durchgesetzt, bevor irgendetwas läuft, das ein Repository ausgesucht hat: Ein geklontes Projekt zu öffnen führt nichts aus, bis Sie ihm vertrauen. |
| **Editor für Tastenkombinationen** | Extras ▸ Optionen ▸ Tastaturbelegung (unter macOS Einstellungen… ▸ Tastaturbelegung) — ändern Sie jedes Kürzel, oder schalten Sie das ganze Profil auf Eclipse, Emacs oder IntelliJ um. |

<a id="what-is-honestly-different"></a>
## Was ehrlich anders ist

- **⌘D ist hier kein Mehrfachcursor.** Dieselbe Geste ist **⌘J** (Strg+J); ⌘D selbst ist nicht belegt. Legen Sie es unter Tastaturbelegung neu fest, wenn Ihre Finger darauf bestehen.
- **⌃\` öffnet das Terminal und gibt ihm den Fokus; es blendet es nicht aus.** Und solange das Terminal den Fokus hat, gehören die Tasten Ihrer Shell, sodass ein zweiter Druck bei der Shell ankommt, statt Sie zurück in den Editor zu bringen.
- **`.vscode/tasks.json` und `launch.json` werden nicht gelesen.** Eine Aufgabe ist ein Befehl, den ein Repository ausgesucht hat, und sie zu lesen verdient einen eigenen Entwurf rund um das Arbeitsbereichsvertrauen; bis dahin übernehmen die eigenen Skripte des Projekts und die Regeln für den Debug-Einstieg oben diese Aufgabe.
- **Es gibt kein Tastaturbelegungsprofil „VS Code“.** Die Kürzel oben liegen auf dem Standardprofil und den vier anderen. Eine gewollte Ausnahme: Im Profil **Eclipse** bleibt ⇧⌘E das eigene *Switch to Editor* von Eclipse, und im Editor behalten ⇧⌘P und ⇧⌘X ihre Bedeutung aus Eclipse (passende Klammer, Großbuchstaben) — wer Eclipse gewählt hat, erwartet Eclipse.
- **Unter Linux öffnet Strg+\` das Terminal, keinen Fensterwechsler.** Die Plattform hatte dort einen zweiten Wechsler für Arbeitsumgebungen (KDE), die Strg+Tab für sich beanspruchen; der Wechsler liegt auf Strg+Tab.
- **Die Kürzel mit Strg+Alt können mit AltGr kollidieren.** Unter Windows senden Tastaturlayouts, die Zeichen mit AltGr tippen (Polnisch zum Beispiel), dafür Strg+Alt. Tippt Strg+Alt+P oder Strg+Alt+K bei Ihnen ein Zeichen, legen Sie *Projekt wechseln* oder die Experiment-Kürzel unter Tastaturbelegung auf andere Tasten.
- **Erweiterungen für VS Code lassen sich hier nicht installieren.** Die Sprachintelligenz kommt von den Sprachservern, die NMOX kennt (der Environment Doctor zeigt, was fehlt und wie man es installiert), von den eigenen Grammatiken des Editors und von Plugins, die für die NetBeans-Plattform gebaut sind.
