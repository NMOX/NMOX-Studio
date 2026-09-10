# NMOX Studio — Benutzerhandbuch

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · [Français](user-guide.fr.md) · **Deutsch** · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> Teilübersetzung: Die Kapitel 1–3 liegen auf Deutsch vor. Für den Rest siehe das [vollständige englische Handbuch](user-guide.md).

Wie man das Produkt tatsächlich benutzt. Dieses Handbuch geht die Funktionen in der Reihenfolge durch, in der Sie ihnen begegnen: Installation, erster Start, Projekte, das Rack, die Studios, die Assistenten und die Sicherheitsnetze.

---

<a id="1-install"></a>
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

<a id="2-first-launch"></a>
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

<a id="3-projects"></a>
## 3. Projekte

**Öffnen:** Jeder Ordner mit einem der 60 erkannten Manifeste öffnet sich als echtes Projekt — `package.json`, `Cargo.toml`, `go.mod`, `pom.xml`, `composer.json`, `foundry.toml`, `bower.json`, `Gruntfile.js` und Verwandte — einschließlich der Manifeste der Contract-Chains: Ein Aiken-Repository (`aiken.toml`) oder ein Clarinet-Repository (`Clarinet.toml`) öffnet sich mit seinen echten, verdrahteten Spuren. Auch ein bloßer Ordner mit HTML und `<script>`-Tags und **ohne** Manifest öffnet sich, als STATIC-Projekt: Das klassische Web ist erstklassig, kein Fehler.

**Anlegen:** *Neues Projekt…* bietet echte Gerüste — Angular, Vue, Svelte, reines JavaScript, Elixir/Phoenix, PHP Web (LEMP) und Klassisches Web (jQuery). Jedes kommt mit fertig verdrahteten Lint-, Format- und Testkonfigurationen und einem initialisierten Git-Repository: ein einziger Gerüst-Commit, der die Lock-Datei mit enthält, wenn der Assistent die Installation für Sie ausführt — Ihr erstes `git status` ist damit sauber.

**Der Projektwechsel ist sicher:** Laufen Geräte (ein Entwicklungsserver, ein Watcher), fragt die IDE vor dem Wechsel und fährt sie sauber herunter. Nichts läuft hinter Ihrem Rücken weiter, nie. Selbst ein erzwungenes Beenden der IDE kann keinen Prozess verwaisen lassen.

**Experimente** sind der schnellste Weg, einen Stack auszuprobieren. **Datei ▸ Neues Experiment…** (⇧⌘E) wählt eine Vorlage und erzeugt ein Wegwerfprojekt unter `~/.nmox/experiments`: kein Git, keine zuletzt geöffneten Einträge, bereits vertraut, Abhängigkeiten installiert — damit der **erste Start einfach funktioniert**. Es öffnet sich mit seinem eigenen `EXPERIMENT.md`-Rundgang, der sagt, was zu drücken ist, welche Datei zu ändern ist und wo die IDE-Intelligenz für diesen Stack wohnt. Behalten Sie, woraus etwas wird: **Datei ▸ Experimente…** ▸ **Übernehmen** holt es heraus und legt ein Git-Repository an, **Duplizieren** legt eine Kopie für einen zweiten Ansatz daneben, **Verwerfen** räumt den Rest weg. Das Regal zeigt das Alter jedes Eintrags und seine gemessenen Speicherkosten. Lieber der geführte Weg? Der Dialog stellt die 93 Lernräume voran.

![Das Regal der Lernräume — Anzahl, Speicherkosten, Alter und der gesamte Lebenszyklus](images/spaces-shelf.png)

![Ein frisches Express-Experiment: der Rundgang offen, die Abhängigkeiten installiert, die API bereits erreichbar](images/experiment-walkthrough.png)

**Ausführen, Bauen, Testen — und Anhalten:** Das ▶ der Werkzeugleiste (F6) führt das Projekt so aus, wie seine Toolchain es ausführt: ein `start`-Skript, sofern package.json eines hat, `cargo run`, `go run`, `dotnet run`, und für einen Ordner mit HTML einen kleinen statischen Server auf dem ersten freien Port ab 8080. Bauen, Testen und Aufräumen liegen daneben und im Menü Ausführen. Ein Entwicklungsserver, der seine Adresse ankündigt, lässt das ⇄-Zeichen in der Statusleiste aufleuchten und öffnet die Seite im eingebauten Browser. Alles läuft beim ersten Mal hinter der Arbeitsbereichs-Vertrauensabfrage. Ein Start, der nicht gelingen konnte, sagt das und bietet an, den Umgebungs-Doktor zu öffnen. Zum Anhalten: Das ■ rechts von Debuggen (⌥⌘.) hält jeden laufenden Befehl auf einmal an und sagt, was es angehalten hat; **Ausführen ▸ Anhalten** hält einen an und bietet danach **Wiederholen**. Das ■ sieht alles, was das Produkt für Sie startet, Installationen eingeschlossen; beim Überfahren nennt der Hinweis genau, was ein Druck anhalten würde, und seit wann jedes läuft.

**`.env` überall:** Hat Ihr Projekt eine `.env`, erhalten die aus dem Rack gestarteten Geräte diese Variablen. Bearbeiten Sie sie, vermerkt die Statusleiste, dass Neustarts sie übernehmen — laufende Prozesse behalten ehrlicherweise ihre alte Umgebung.
