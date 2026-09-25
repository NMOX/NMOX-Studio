# Die zweite Woche

<!-- languages -->
[English](the-second-week.md) · [Español](the-second-week.es.md) · [Français](the-second-week.fr.md) · **Deutsch** · [Русский](the-second-week.ru.md) · [Українська](the-second-week.uk.md) · [Polski](the-second-week.pl.md) · [Português (Brasil)](the-second-week.pt.md) · [Bahasa Indonesia](the-second-week.id.md) · [Filipino](the-second-week.tl.md) · [Tiếng Việt](the-second-week.vi.md) · [简体中文](the-second-week.zh.md) · [हिन्दी](the-second-week.hi.md) · [עברית](the-second-week.he.md) · [العربية](the-second-week.ar.md)
<!-- /languages -->

*Committen, prüfen, auflösen, vorschlagen — ohne in ein anderes Werkzeug zu wechseln.*

Die erste Stunde heißt: ein Projekt öffnen und es laufen lassen. Die
zweite Woche ist alles rund um den Code: zwanzig Commits am Tag, vor
jedem ein Diff zum Lesen, nach einem Pull ein Konflikt, nach einem Push
ein Pull Request, ein Stacktrace zum Verfolgen, eine README, die ehrlich
bleiben soll. Dieser Rundgang ist eine Sitzung in einem git-Repository,
das Sie schon haben, und jeder Schritt ist etwas, das Sie morgen wieder
tun werden.

## 1. NMOX Studio zum Editor von git machen

**Tun:** Team ▸ **NMOX Studio mit Git verwenden…**

**Sehen:** Die sechs globalen git-Einstellungen, die NMOX Studio zum
Editor, Difftool und Mergetool von git machen, jede neben dem Wert, den
sie **jetzt** hat, damit nichts ungesehen ersetzt wird. **Anwenden**
setzt sie (**Schließen** ist die Standardschaltfläche, weil dies Ihre
globale git-Konfiguration schreibt); **Befehle kopieren** legt stattdessen
die `git config`-Zeilen in die Zwischenablage. Verwendet git NMOX Studio
bereits, sagt der Dialog das und bietet kein Anwenden an.

Dieselben Zeilen, wenn Sie ein Terminal vorziehen:

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
git config --global merge.tool nmox
git config --global mergetool.nmox.cmd 'nmox -w "$MERGED"'
git config --global mergetool.nmox.trustExitCode false
```

## 2. Committen

**Tun:** Ändern Sie eine Datei, dann in einem Terminal:

```bash
git commit -a
```

**Sehen:** Die Commit-Nachricht öffnet sich in NMOX Studio, und die
Statuszeile sagt, dass ein Terminal auf sie wartet. Die `#`-Zeilen von git
sind Kommentare; nur was Sie schreiben, wird auf Rechtschreibung geprüft;
eine Zusammenfassungszeile, die länger ist als die 72 Zeichen, bei denen
gits eigene Werkzeuge abschneiden, bekommt ab dem 73. Zeichen eine
Warnung. Speichern, Tab schließen, und der Commit landet — das Terminal
hat gewartet, bis Sie das getan haben. Beenden Sie die IDE, während die
Nachricht noch offen ist, bekommt git sie ebenfalls zurück, mit dem, was
gespeichert war.

`git rebase -i` öffnet seine Liste auf dieselbe Weise: jeder Befehl und
jeder Commit hervorgehoben, und **Kommentar umschalten** nimmt eine Zeile
heraus, ohne sie zu löschen.

## 3. Wissen, wo Sie stehen

**Sehen:** Das **⎇-Zeichen** in der Statuszeile — `⎇ main ±3 ↑2 ↓1` ist
Ihr Branch, drei geänderte Dateien, zwei Commits zum Pushen und einer zum
Pullen (die Pfeile erscheinen nur, wenn es etwas zu pushen oder zu pullen
gibt). Sein Menü beginnt mit **Branch wechseln…**, **Commit…**, **Pull…** und **Push…**.

**Tun:** Setzen Sie den Cursor auf eine beliebige Zeile einer
versionierten Datei.

**Sehen:** Neben dem Zeichen, wer diese Zeile zuletzt geändert hat, wie
lange das her ist und warum: `Ada Lovelace, vor 3 Tagen · Fix the parser`.
Eine Zeile, die Sie noch nicht committet haben, sagt das, und eine Datei
mit ungespeicherten Änderungen sagt eben das, statt den falschen Autor zu
nennen. Ein Klick auf die Notiz zeigt die Anmerkungen der ganzen Datei, den
Commit auf GitHub oder seine ID; **Ansicht ▸ Zeilenautor** schaltet sie aus.

## 4. Einen Diff prüfen

**Tun:**

```bash
git difftool
```

**Sehen:** Jede geänderte Datei nebeneinander in der Diff-Ansicht von
NMOX Studio, mit **Vorheriger Unterschied / Nächster Unterschied** und
„Unterschied 2 von 5“ darüber. Eine hinzugefügte oder gelöschte Datei
zeigt ihre fehlende Seite als leeren Bereich („keine Datei“); eine
Binärdatei erscheint als binär, und die Leiste sagt, ob sich zwei
Binärdateien unterscheiden. Schließen Sie den Tab, und git geht zur
nächsten Datei.

## 5. Einen Konflikt auflösen

**Tun:** Mergen Sie einen Branch, der kollidiert, dann:

```bash
git mergetool
```

**Sehen:** Die Datei mit dem Konflikt im Editor, die aktuelle und die
eingehende Seite getönt, und eine Warnung auf jeder `<<<<<<<`-Zeile.
Setzen Sie den Cursor darauf und drücken Sie ⌘. (anderswo Alt+Enter),
oder verwenden Sie **Quelltext ▸ Code korrigieren…**: **Aktuelle Änderung
übernehmen**, **Eingehende Änderung übernehmen** oder **Beide Änderungen
übernehmen**, jede eine einzige rückgängig zu machende Bearbeitung. Ein
Block, der sich seit dem Angebot geändert hat, wird abgelehnt, statt
erraten zu werden. Speichern, Tab schließen und git antworten.

## 6. Vorschlagen

**Tun:** Pushen Sie, dann Team ▸ **Neuer Pull Request auf GitHub** (auch
im Menü des Zeichens).

**Sehen:** Die eigene Seite „New Pull Request“ von GitHub für Ihren
Branch, in Ihrem eigenen Browser, in dem Sie angemeldet sind. Aus einem
Editor heraus liefern **Bearbeiten ▸ Auf GitHub öffnen** und
**GitHub-Link kopieren** die Zeile oder die Zeilen, auf denen Sie stehen;
im Baum des Projekt-Studios liefern sie eine Datei oder einen Ordner.

## 7. Einem Fehler nachgehen

**Tun:** Führen Sie Ihre Tests im Terminal (⌃\`) aus, bis einer
fehlschlägt.

**Sehen:** Ein Ort in der Ausgabe — `src/app.ts:42:7`, ein Stack-Frame
`(/abs/app.js:10:5)`, `--> src/main.rs:3:5`, `File "x.py", line 12` —
öffnet sich per ⌘-Klick an dieser Zeile und Spalte (Strg-Klick unter
Windows und Linux). Eine URL oder `localhost:3000` ist nie ein Link, und
ein Pfad, den es nicht gibt, wird mit seinem Namen abgelehnt, statt
erraten zu werden.

## 8. Die README ehrlich halten

**Tun:** Extras ▸ **Markdown-Links prüfen…**

**Sehen:** Jeder relative Link und jedes Bild im Markdown des Projekts,
geprüft so, wie GitHub es darstellt — die Datei muss existieren, und ein
`#heading` muss eine Überschrift dieser Datei sein. Ein toter Link ist ein
Fehler, eine fehlende Überschrift eine Warnung, beide als Wellenlinien und
in der Aufgabenliste, mit einem Satz in der Statuszeile. Nichts verlässt
Ihren Rechner: Ein Link mit einem Schema wird nicht geprüft.

## 9. An einen Agenten übergeben

**Tun:** Extras ▸ **Agent Port (MCP)…**, haken Sie **Diese Adresse und
diesen Token behalten** an, dann **Für Claude Code kopieren**, und führen
Sie die kopierte Zeile einmal aus.

**Sehen:** Ein Agent, der lesen kann, was die IDE weiß — das angepeilte
Projekt, was ausgeliefert wird und was läuft, was Sie bearbeiten, den
letzten Fehlschlag — und sich auch morgen noch verbindet, weil der Token
im Schlüsselbund Ihres Systems liegt und der Port wiederverwendet wird.
Der Port bleibt von Bauart nur lesend. Das Abwählen von **Diese Adresse
und diesen Token behalten** löscht den Eintrag im Schlüsselbund.

## Was Sie getan haben

Sie haben eine Commit-Nachricht geschrieben, einen Diff gelesen, einen
Konflikt aufgelöst, einen Pull Request eröffnet, herausgefunden, wer eine
Zeile geschrieben hat, einem Stacktrace gefolgt und eine README geprüft —
alles in dem Fenster, in dem Sie ohnehin waren. Nichts davon hat git
ersetzt: Jeder Schritt ist gits eigener, geöffnet dort, wo Sie arbeiten.
