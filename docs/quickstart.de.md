# Schnellstart: In fünf Minuten läuft Ihr Projekt

<!-- languages -->
[English](quickstart.md) · [Español](quickstart.es.md) · [Français](quickstart.fr.md) · **Deutsch** · [Русский](quickstart.ru.md) · [Українська](quickstart.uk.md) · [Polski](quickstart.pl.md) · [Português (Brasil)](quickstart.pt.md) · [Bahasa Indonesia](quickstart.id.md) · [Filipino](quickstart.tl.md) · [Tiếng Việt](quickstart.vi.md) · [简体中文](quickstart.zh.md) · [हिन्दी](quickstart.hi.md) · [עברית](quickstart.he.md) · [العربية](quickstart.ar.md)
<!-- /languages -->

Diese Seite bringt eines Ihrer eigenen Projekte in NMOX Studio zum Laufen. Sie behandelt nur, was Sie dafür brauchen. [Das Benutzerhandbuch](user-guide.de.md) ist das vollständige Handbuch. Wenn Sie VS Code benutzen, lesen Sie als Nächstes [Von VS Code kommend](coming-from-vscode.de.md).

<a id="1-install-one-minute"></a>
## 1. Installieren (eine Minute)

**macOS, mit Homebrew:**

```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

Homebrew bittet Sie für jeden Tap von Dritten einmal, `brew trust` auszuführen. Bei Aktualisierungen fragt es nicht erneut.

**macOS, Windows, Linux, ohne Homebrew:** Laden Sie die neueste Version für Ihr Betriebssystem von [der Seite mit den Versionen](https://github.com/NMOX/NMOX-Studio/releases/latest) herunter:

| Betriebssystem | Datei | Dann |
|---|---|---|
| macOS | `NMOX-Studio-<version>-macos.dmg` | Ziehen Sie die App in den Ordner „Programme“. |
| Windows | `NMOX-Studio-<version>-windows-setup.exe` | Führen Sie das Installationsprogramm aus. |
| Debian, Ubuntu | `nmox-studio_<version>_amd64.deb` | `sudo apt install ./nmox-studio_<version>_amd64.deb` |
| Anderes Linux | `NMOX-Studio-<version>-linux.tar.gz` | Entpacken Sie es und starten Sie `bin/nmoxstudio`. |

Jede dieser Dateien bringt ihre eigene Java-Laufzeit mit, Sie müssen also nichts weiter installieren. Nur die portable ZIP-Datei braucht Java 21 oder neuer, das schon auf dem Rechner ist.

Unter macOS ist die App von Apple notarisiert. Beim ersten Öffnen fragt macOS, ob eine aus dem Internet geladene App geöffnet werden soll: Klicken Sie auf **Öffnen**.

<a id="2-open-your-project-one-minute"></a>
## 2. Ihr Projekt öffnen (eine Minute)

Starten Sie **NMOX Studio**. Es öffnet drei Tabs: **Willkommen**, **Task-Rack** und **Browser**.

Um Ihr Projekt zu öffnen, wählen Sie **Datei ▸ Ordner öffnen…** (⌥⌘O unter macOS, Strg+Alt+O unter Windows und Linux) und wählen seinen Ordner aus. Das geht auch aus einem Terminal, so wie mit `code .`:

```bash
cd ~/code/my-app
nmox .
```

Der Befehl kehrt sofort zurück. Läuft NMOX Studio schon, bekommt es den Ordner; wenn nicht, startet es. Homebrew, das Windows-Installationsprogramm und die Linux-Pakete legen `nmox` in Ihren PATH. Für eine Installation aus dem DMG siehe [So kommt `nmox` in Ihren PATH](user-guide.de.md#2-first-launch).

Ein Ordner gilt als Projekt, wenn er eine `package.json`, `Cargo.toml`, `go.mod`, `pom.xml`, `composer.json`, `pyproject.toml` oder eine von 57 weiteren Projektdateien enthält. Auch ein Ordner mit schlichten HTML-Dateien zählt.

Beim Öffnen eines Projekts geschehen drei Dinge:

- Das **Projekt-Studio** links zeigt Ihre Dateien.
- Die Statuszeile unten zeigt Ihren Git-Zweig und die Zahl der geänderten Dateien.
- Das **Task-Rack** wird für die Art des Projekts eingerichtet. Ein Vite-Projekt bekommt eine Vite-Konsole, ein Cargo-Projekt Spuren zum Ausführen, Debuggen und Testen, und so weiter.

<a id="3-run-it-one-minute"></a>
## 3. Ausführen (eine Minute)

Drücken Sie **▶** in der Werkzeugleiste oder F6. Das führt Ihr Projekt so aus, wie seine Werkzeuge es ausführen: das Skript `dev`, `start` oder `serve` aus der `package.json`, `cargo run`, `go run`. Dabei wird der Paketmanager Ihres Projekts benutzt: npm, pnpm oder yarn, oder bun für ein Bun-Projekt.

Wenn Sie in einem Projekt zum ersten Mal etwas ausführen, fragt NMOX Studio, ob Sie dem Ordner vertrauen. Ein Projekt, dem Sie nicht vertraut haben, führt nichts von seinem eigenen Code aus: keine Skripte, keine Builds, keine Tests. Klicken Sie bei Ihrem eigenen Code auf **Arbeitsbereich vertrauen**.

Ist Ihr Projekt ein Entwicklungsserver, erscheint seine Adresse in der Statuszeile neben einem **⇄**, und die Seite öffnet sich im Tab **Browser**. Ändern Sie eine Datei und speichern Sie, und die Seite lädt neu.

Um alles anzuhalten, was läuft, drücken Sie **■** neben ▶ oder ⌥⌘. (Wahltaste, Befehlstaste und Punkt).

Passiert nichts, sehen Sie in den Tab **Output** unten. Er erklärt, warum der Lauf nicht starten konnte, zum Beispiel weil ein Werkzeug nicht installiert ist oder die Abhängigkeiten noch nicht installiert sind, und bietet an, das zu beheben. **Extras ▸ Environment Doctor…** listet jedes Werkzeug, das NMOX Studio benutzen kann, und zeigt, welche installiert sind.

<a id="4-find-anything-thirty-seconds"></a>
## 4. Alles finden (dreißig Sekunden)

Drücken Sie **⌘I** (Strg+I unter Windows und Linux) und tippen Sie. Die Schnellsuche findet Dateien, Menüaktionen, Symbole, Rack-Geräte, laufende Server und Befehle sowie die Skripte Ihrer `package.json`. Drücken Sie Eingabe, um das Ergebnis zu öffnen oder auszuführen.

Drücken Sie **⌘P**, um eine Datei nach Namen zu öffnen.

<a id="5-test-it-thirty-seconds"></a>
## 5. Testen (dreißig Sekunden)

Drücken Sie **⌃F6** (Strg+F6), um die Tests Ihres Projekts auszuführen. Um jeden Test des Projekts zu sehen, bevor Sie einen ausführen, öffnen Sie das Fenster **Tests** mit ⌥⌘2.

<a id="if-you-have-no-project-handy"></a>
## Wenn Sie gerade kein Projekt zur Hand haben

- **Datei ▸ Neues Projekt…** legt aus einer Vorlage ein echtes Projekt an (Angular, Vue, Svelte, React mit Vite, reines JavaScript, PHP, Phoenix und mehr). Es erzeugt die Dateien, richtet Git ein und installiert die Abhängigkeiten.
- **Datei ▸ Neuer Lernraum…** öffnet eine geführte Anleitung. *Ihre erste Webseite* steht in der Liste ganz oben.

<a id="where-to-go-next"></a>
## Wie es weitergeht

- **[Das Task-Rack](user-guide.de.md#4-the-task-rack)**. Jedes Werkzeug, das Sie ausführen, ist ein Gerät im Rack, und Kabel zwischen den Geräten verketten sie: zum Beispiel die Tests ausführen, sobald der Build gelingt.
- **[Der Editor](user-guide.de.md#5-the-editor)**. Mit Emmet, Farbfeldern, Debugging mit Haltepunkten für Node und Chrome sowie Angular-Vorlagen.
- **[Die Studios](user-guide.de.md#6-the-studios)**. Das API-, das Datenbank-, das Smart-Contract- und das Block-Studio sowie das Aufgaben-Board.
- **[Das Glossar](glossary.de.md)** erklärt die eigenen Wörter des Produkts: Rack, Patch, Buchse, Spur, Anpeilen, KVASIR.
