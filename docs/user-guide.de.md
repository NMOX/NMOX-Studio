# NMOX Studio — Benutzerhandbuch

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · [Français](user-guide.fr.md) · **Deutsch** · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> Teilübersetzung: Die Kapitel 1–2 liegen auf Deutsch vor. Für den Rest siehe das [vollständige englische Handbuch](user-guide.md).

Wie man das Produkt tatsächlich benutzt. Dieses Handbuch geht die Funktionen in der Reihenfolge durch, in der Sie ihnen begegnen: Installation, erster Start, Projekte, das Rack, die Studios, die Assistenten und die Sicherheitsnetze.

---

## 1. Installation

**macOS (empfohlen):**
```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

Die Zeile `brew trust` ist Homebrews einmalige Bestätigung für jeden Tap von Dritten — bei Aktualisierungen werden Sie nicht erneut gefragt. Die Anwendung ist ad-hoc signiert, aber nicht notarisiert, daher würde eine Kopie in Quarantäne beim ersten Start von Gatekeeper abgelehnt: Der Cask entfernt das Quarantäne-Attribut in einem `postflight`-Schritt selbst und schreibt das in die Installationsausgabe. Nichts geschieht im Stillen.

**Alles andere:** Laden Sie eine Datei der [neuesten Version](https://github.com/NMOX/NMOX-Studio/releases/latest) herunter — `.dmg` für macOS, `-setup.exe` für Windows, `.deb` für Debian/Ubuntu, generisches `.tar.gz` für Linux. Alle vier bringen ihre eigene Java-Laufzeit mit; vorher ist nichts zu installieren. Die `-portable.zip` ist das einzige Artefakt mit eigenem Java (benötigt Java 21+ im PATH oder Start mit `--jdkhome <Pfad-zum-JDK>`).

> **macOS, erster Start:** Die Anwendung ist ad-hoc signiert, aber nicht notarisiert, daher fragt Gatekeeper vor der Ausführung. **Rechtsklick auf die App → Öffnen** beim ersten Mal und bestätigen, oder
> `xattr -d com.apple.quarantine "/Applications/NMOX Studio.app"` ausführen. Beides erledigt es dauerhaft.

### Aktualisieren

Die IDE aktualisiert sich selbst: **Werkzeuge ▸ Plugins ▸ Aktualisierungen** bietet die Module jeder neueren Version an. Installieren, bei Aufforderung neu starten, fertig — ohne die ganze Anwendung erneut zu laden. Eine ehrliche Einschränkung: Die mitgelieferte Java-Laufzeit und der Starter ändern sich nur mit einem vollständigen Installationsprogramm, daher ist bei größeren Plattformsprüngen eine Neuinstallation aus einer Release-Datei weiterhin richtig.

## 2. Erster Start

Aus einem Terminal startet `nmoxstudio --open <Ordner>` die Anwendung mit diesem Ordner als geöffnetem Projekt und dem darauf gerichteten Rack — dieselbe Tür, die „Ordner öffnen…“ auf der Willkommensseite öffnet.

Die IDE öffnet sich mit allen Tabs der Suite am Editorbereich: **Willkommen → Task-Rack → Datenbank-Studio → Smart-Contract-Studio → Infrastruktur-Designer → API-Studio → Docker-Panel** — jede wichtige Oberfläche ist ab der ersten Minute einen Klick entfernt. Im linken Dock: **Projekt-Studio** (Dateibaum und Vorlagen), die Basis **Arbeitsplatz** und der **NPM-Explorer**. Ein Ordner `~/NMOX` wird als Standardarbeitsbereich angelegt; das Rack zeigt dorthin, bis Sie ein Projekt öffnen.

![Erster Start — die Willkommensseite mit allen geöffneten Tabs](images/welcome.png)

Tastenkürzel, die sich am ersten Tag lohnen (sie stehen auch alle auf dem Willkommens-Tab):

| Kürzel | Öffnet |
|---|---|
| **⌘I** | Schnellsuche — erreicht alles |
| **⌘9** | Task-Rack |
| **⌥⌘0** | Arbeitsplatz |
| **⌥⌘3** | IRC-Chat-Client |
| **⌥⌘4** | Browser (integriertes WebKit, mit DevTools) |
| **⌥⌘5** | Block-Studio |
| **⌥⌘6** | Smart-Contract-Studio |
| **⌥⌘7** | Datenbank-Studio |
| **⌥⌘8** | API-Studio |
| **⌥⌘9** | Infrastruktur-Designer |
| **⌘8** | Docker-Panel |
| **⌘7** | Gliederung der aktuellen Datei |
| **⇧⌘N / ⌥⌘O** | Neues Projekt… / Ordner öffnen… |
| **⇧⌘E / ⇧⌘L** | Neues Experiment… / Neuer Lernraum… |
