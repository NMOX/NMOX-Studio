# NMOX Studio — Benutzerhandbuch

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · [Français](user-guide.fr.md) · **Deutsch** · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> Teilübersetzung: Die Kapitel 1–8 liegen auf Deutsch vor. Für den Rest siehe das [vollständige englische Handbuch](user-guide.md).

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

<a id="4-the-task-rack"></a>
## 4. Das Task-Rack

![Das Task-Rack](images/tabs/the-task-rack.png)

Das Rack ist das Herz des Produkts. Jedes Werkzeug Ihres Arbeitsablaufs — npm, der Bundler, der Testläufer, der Entwicklungsserver, der Linter, git, das Ausrollen — ist ein Gerät im Rack: Drehregler wählen die Aufgabe, GO führt sie aus, LEDs zeigen den Zustand, und ein LCD sagt Ihnen mit Worten, was geschehen ist.

![Das Rack auf eine klassische jQuery-Seite gerichtet — die Vorlage Classic Web Bench: MAESTRO, CRATE, DYNAMO (dessen TASK-Regler das echte Gruntfile gelesen hat), IGNITION liefert statisch aus, VITALS wacht über die Qualität](images/task-rack.png)

**Die Grundlagen:**

- **Geräte hinzufügen**, indem Sie sie aus der Palette ziehen (sie hat Kategorien und einen Suchfilter). Jedes Gerät bringt seine Karte *So wird es benutzt* mit.
- **Etwas ausführen**, indem Sie den GO-Knopf eines Geräts drücken. Fahren Sie vorher darüber: Der Hinweis zeigt die genaue Befehlszeile, die ausgeführt wird. Keine Magie.
- **Eine Kette verkabeln:** Drücken Sie **Tab**, um das Rack auf seine Rückseite zu drehen. Ziehen Sie ein Patchkabel von der **OK**-Buchse eines Geräts zur **GO**-Buchse des nächsten. Jetzt ist `installieren → bauen → testen` ein einziger Tastendruck: Die Kette läuft von selbst und hält beim ersten Fehlschlag an. Die Ausgabe läuft über den Phosphorschirm des MONITOR-Geräts.
- **Jede strukturelle Änderung rückgängig machen** mit **⌘Z** — Hinzufügen, Entfernen, Umverkabeln. Ein laufendes Gerät zu entfernen hält zuerst seinen Prozess an.
- **Vorlagen** geben Ihnen mit einem Klick ein ganzes verkabeltes Rack — Ship Gate, Dev Intelligence, Monorepo Lanes, E2E Loop, LAMP Bench, Web3 Bench, Uptime Watch. Verkabelungen werden je Projekt automatisch gesichert.

![Tab dreht das Rack — Patchkabel führen MAESTRO über CRATE, DYNAMO und IGNITION bis zu VITALS](images/rack-rear.png)

**Koordination, wenn Ihre Kette wächst:**

- **QUORUM** führt Spuren zusammen: Es löst erst aus, wenn *alle* seine verkabelten Eingänge erfolgreich waren — das klassische „warte auf Lint UND Test UND Typprüfung“.
- **ENABLE-Tore** an Langläufern: Der ENABLE-Eingang eines Entwicklungsservers heißt „starte nicht, bevor dies auslöst“.
- **REFLEX** beobachtet Dateien und leitet nach Muster weiter — `src/**/*.css` in die eine Kette, `**/*.ts` in die andere, je Spur in einem Monorepo.
- **ROSETTA** wählt die Werkzeugspur in gemischten Repositories (das Rack erkennt Node/Rust/Go/PHP/… je Verzeichnis und richtet jedes Gerät entsprechend aus).

**Spuren, die Ihre eigene Werkzeugkette sprechen.** Auf AUTO sprechen die Lint- und Format-Geräte (PURITY, GLOSS) die Werkzeugkette des Projekts selbst, statt überall nach Node-Werkzeugen zu greifen: Ein Deno-Arbeitsbereich nutzt `deno lint` und `deno fmt`, ein Cargo-Projekt `cargo clippy` und `cargo fmt`, ein Go-Modul `go vet` (oder `golangci-lint`, wenn das Projekt seine Konfiguration mitbringt) und `gofmt`. Eine `biome.json` schaltet die Node-Spuren auf Biome um, und ausdrückliche Reglerstellungen schlagen AUTO immer.

**Ihre eigenen Geräte.** Das Regal erweitert sich mit einem Texteditor: Jede `*.json` in `~/.nmox/devices.d/` wird ein echtes Gerät — Regler, Knöpfe, LEDs, Anschlüsse und Kabel, in der Verkabelung gesichert und über ⌘I erreichbar. Erklären Sie einen Befehl als Argumentliste, benennen Sie einen Regler, und `{{regler}}` wird beim Knopfdruck eingesetzt. Die Gesetze bleiben beim Wirt, nicht in Ihrer Datei: **die Arbeitsbereichs-Vertrauensabfrage sichert den ersten Start genau wie bei einem eingebauten Gerät**.

**Qualitätsschranken** machen aus „sieht fertig aus“ ein „ist fertig“:

- **VITALS** lässt Lighthouse gegen Ihren laufenden Server antreten und fordert eine Untergrenze für Leistung, Barrierefreiheit, gute Praxis oder Auffindbarkeit.
- **VERITAS** setzt eine Abdeckungsuntergrenze durch und wiederholt genau die Tests, die fehlgeschlagen sind, namentlich.
- **GAUNTLET** belastet einen Endpunkt und fordert einen Mindestdurchsatz. **PRISM** wacht über die Bündelgröße, **BEACON** über Zertifikat und Erreichbarkeit einer URL, und **PREFLIGHT** ist die Checkliste vor dem Ausliefern — verkabeln Sie sein OK mit Ihrem Ausrollgerät, und ein Ausrollen kann schlicht nicht laufen, solange nicht alles grün ist.
- **GOVERNOR** wacht bei Solidity-Arbeit über Gas-Rückschritte (`.gas-snapshot`).

**Alles andere:** **SOLDER** kleidet jeden Shell-Befehl als vollwertiges Gerät ein — und das ganze Rack **exportiert nach GitHub Actions** (Ihre lokale Kette und Ihre Integration sind dieselbe Verkabelung). **HELM** führt Befehle über ssh auf einem entfernten Server aus, **TAIL** folgt jeder Protokolldatei, und **PHOSPHOR** ist ein Terminal im Rack. Druckt der Befehl eine lokale Adresse, leuchtet das ⇄-Zeichen wie bei jedem ausliefernden Gerät und erlischt, wenn der Lauf endet.

**Das Rack hält sich von selbst im Takt.** Ändern Sie `package.json`, und der Skript-Regler von NPM-9000 aktualisiert sich an Ort und Stelle. Ändern Sie ein `Gruntfile`, und DYNAMO liest seine Aufgaben neu. Fügen Sie eine Abhängigkeit hinzu, und CRATEs Anzeige frischt auf. Kein neues Ausrichten, keine Aktualisierungsknöpfe.

### KVASIR — erklärt den letzten Fehlschlag

![KVASIR erklärt einen echten fehlgeschlagenen Lauf: die eingewilligte Diagnose auf der Frontplatte und die vollständigen Schritte im Betrachter](images/kvasir-explain.png)

**KVASIR** ist KI-Unterstützung nach Art des Racks: ein Gerät, das den Fehler erklärt, der gerade auf dem MONITOR-Bus liegt — keine Chat-Seitenleiste. Schlägt ein Lauf fehl, drücken Sie **EXPLAIN**, und KVASIR fragt Ihre KI, was schiefging und was der konkrete nächste Schritt ist. Ein kurzes Urteil erscheint auf der Anzeige; **VIEW** öffnet die ganze Antwort. **MODEL** wählt zwischen **FAST** (schnell und günstig, die Voreinstellung) und **DEEP** (stärker). EXPLAIN ist blau: Es liest und fragt, es rührt Ihr Projekt nie an.

**Wählen Sie Ihre KI, hinterlegen Sie Ihren Schlüssel.** KVASIR arbeitet mit **Claude (Anthropic)**, **ChatGPT (OpenAI)** oder **Gemini (Google)** — Ihr Schlüssel, Ihre Wahl. Drücken Sie **KEY…**, um den Anbieter zu wählen und seinen Schlüssel einzufügen; die Wahl wird gemerkt, und der Schlüssel wohnt allein im Schlüsselbund des Systems. Die üblichen Umgebungsvariablen jedes Anbieters werden ebenfalls gelesen, und ein hinterlegter Schlüssel schlägt einen aus der Umgebung.

**Was KVASIR sendet, und alles, was es sendet.** Beim ersten Druck auf EXPLAIN führt ein Dialog genau auf, was Ihren Rechner verlässt und was nicht; ohne diese Einwilligung wird nichts gesendet, und die Einwilligung gilt je Anbieter. Nach einem erfolgreichen EXPLAIN öffnet **VIEW** die Antwort als Gespräch — Sie können zum selben Fehlschlag weiterfragen.

**Fragen Sie KVASIR zu Ihrem Code.** Derselbe Assistent erreicht den Editor: Markieren Sie Code und wählen Sie **KVASIR zur Auswahl fragen…**, oder **Mit KVASIR bearbeiten…**, um zu sagen, was sich ändern soll, und ein Vorher und Nachher zu sehen, bevor irgendetwas angewandt wird. **⌥⌘G** vervollständigt an der Schreibmarke als Geistertext, der sich nur mit Tab einfügt, und das git-Zweigzeichen kann Ihre Commit-Nachricht entwerfen.

**Richten Sie einen Agenten auf Ihre IDE.** Werkzeuge ▸ Agent Port (MCP)… öffnet einen MCP-Zugang, den ein fremder Assistent abfragen kann: **von Bauart nur lesend**, aus, bis Sie ihn einschalten, nur auf der lokalen Schnittstelle lauschend und auf das beim Start erzeugte Token angewiesen.

Das Rack ist erweiterbar: Erweiterungen von Dritten können Geräte hinzufügen (deren NBM über Werkzeuge ▸ Plugins installieren). Zum Selberschreiben siehe [device-spi.md](device-spi.md).

<a id="5-the-editor"></a>
## 5. Der Editor

![jQuery-Code in der NMOX-Phosphor-Palette, die Struktur im Navigator](images/editor.png)

Über 70 Sprachen werden richtig eingefärbt — der moderne Stapel, der klassische (CoffeeScript eingeschlossen) und die ganze Konfigurationsschicht bis hinunter zu `.env`, `.editorconfig`, nginx- und Apache-Konfigurationen, Dockerfiles und Sperrdateien.

- **Die Vervollständigung** kennt den Zusammenhang und auch *die klassischen Bibliotheken*: Trägt Ihr Projekt jQuery, MooTools, Prototype, Backbone/Underscore oder Knockout (über npm-Abhängigkeiten *oder* schlichte `<script>`-Tags), erscheinen deren APIs beim Vervollständigen. Projekte mit jQuery 1.x oder 2.x bekommen einen ehrlichen Hinweis auf das Lebensende, keine Ermahnung.
- **Die Navigator-Gliederung (⌘7)** zeigt die Struktur der Datei für 58 Dateitypen; ein Klick springt hin.
- **Die Übersichtskarte** — ein Umriss der ganzen Datei neben der Bildlaufleiste jedes Editors; klicken oder ziehen bewegt den Ausschnitt. Das ganze Dokument passt immer in den Streifen: Die Zeilen schrumpfen, während die Datei wächst. Ansicht ▸ Übersichtskarte schaltet sie für alle offenen Editoren auf einmal.
- **Die klebende Gliederung** — die Deklarationen, die den oberen Rand der Ansicht umschließen (die Klasse, dann die Methode, in die Sie gescrollt sind), bleiben über dem Text angeheftet, bis zu drei Zeilen des Quelltexts selbst; ein Klick springt dorthin. Die Leiste verschwindet, wenn nichts die oberste sichtbare Zeile umschließt.
- **Gehe zu Symbol (⌥⇧⌘O)** springt zu jeder Funktion, Klasse, Regel oder Überschrift im ganzen Projekt, indem Sie den Namen tippen — mit Treffern nach Präfix, Binnenmajuskel oder Platzhalter. Der Index ist begrenzt und ehrlich: `node_modules` wird übersprungen, und bei einem sehr großen Projekt sagt der Dialog, dass er die ersten 2.000 Dateien erfasst hat, statt vorzugeben, alles gelesen zu haben.
- **Das Testfenster (⌥⌘2)** zeigt jeden Test des Projekts, *bevor irgendetwas läuft*, und startet einen, eine Datei oder alle.

### Abkürzung ausschreiben (⌥⌘E)

Tippen Sie eine Emmet-Abkürzung und drücken Sie **⌥⌘E**: Aus `ul>li*3` wird die fertige Liste. Das gilt in HTML, in Angular-Vorlagen und — in seiner CSS-Form — auch in `<style>`-Blöcken und `style`-Attributen, wo der Ausschnitt auf die Region begrenzt bleibt und deshalb nie das umgebende Markup verschlingt. Eine Abkürzung, die das Produkt nicht kennt, wird abgelehnt und lässt Ihren Text unberührt.

### Gestaltungsmarken (eigene Eigenschaften)

`var(` zu tippen bietet die Marken an, die in den echten Stilvorlagen Ihres Projekts erklärt sind — jede mit ihrem Farbfeld und dem Ort ihrer Erklärung. **⌘-Klick** auf eine Verwendung von `var(--marke)` springt zu ihrer Erklärung. Farben werden als die Farbe gemalt, die sie sind — hex, `rgb()`, `hsl()`, Namen und auch `oklch()`, `lab()` und `color-mix()` — und **⌘-Klick** auf ein Farbliteral öffnet einen Wähler, der es in genau der Schreibweise ersetzt, in der Sie es geschrieben haben.

### Das class-Attribut kennt Ihre Stilvorlagen

In `class="…"` zu tippen bietet die Klassen an, die Ihr Projekt wirklich erklärt, samt der Stilvorlage, aus der sie stammen; **⌘-Klick** auf eine Klasse springt zu ihrer Regel, **⌘-Klick** auf einen `.klasse`-Selektor zu ihrer ersten Verwendung im Markup. **Klasse umbenennen…** benennt im ganzen Projekt um — nur ganze Bezeichner, mit der Anzahl je Datei — und verweigert laut, wenn der neue Name schon vergeben ist oder ungespeicherte Änderungen offen sind.

### Skript ausführen, von der Schreibmarke aus

Im `scripts`-Abschnitt einer `package.json` führt **Skript ausführen** die Zeile aus, in der die Schreibmarke steht — durch dieselbe Arbeitsbereichs-Vertrauensabfrage und dasselbe ■ wie jeder andere Lauf.

### Umgebungsschlüssel, vollwertig

`process.env.` oder `import.meta.env.` zu tippen bietet die Schlüssel an, die Ihre `.env`-Familie tatsächlich erklärt, und **⌘-Klick** springt zu der Zeile, die den Schlüssel erklärt. Werte werden gekürzt angezeigt: Die Erinnerung ist da, das Geheimnis nicht.

### Angular-Vorlagen, vollwertig

`.component.html`-Dateien öffnen mit eigener Vorlagen-Einfärbung, mit `@if`/`@for`-Blöcken und Strukturdirektiven in der Vervollständigung. Installieren Sie den Angular Language Service, und die Typprüfung der Vorlagen kommt wirklich an: Schreiben Sie einen Eigenschaftsnamen falsch, schlägt Angulars eigener Compiler den richtigen vor. **⌘B** in einer Vorlage springt zur Deklaration, und das Kontextmenü wechselt zwischen der Komponente, ihrer Vorlage, ihren Stilen und ihrem Test.

### Vue- und Svelte-Komponenten, vollwertig

`.vue`- und `.svelte`-Dateien öffnen mit eigener Einfärbung, eigener Vervollständigung (die gepunkteten Runen von Svelte 5 eingeschlossen) und Emmet in ihren Vorlagenblöcken. Vues Diagnosen erreichen den Editor wirklich, über Vues eigenen Sprachserver.

### Fehlersuche mit echten Haltepunkten

Klicken Sie in den Rand, wählen Sie **Datei debuggen (Haltepunkte)**, und das Programm hält dort an — mit Aufrufliste, Variablen und Ausdrucksauswertung. JavaScript und TypeScript laufen ab Werk über den mitgelieferten Adapter; Python nutzt debugpy und Go delve, die Sie selbst installieren. **In Chrome debuggen** tut dasselbe für eine Seite: Die Haltepunkte in Ihrem Quelltext halten in der IDE, während der Browser mit einem Wegwerfprofil läuft. Alles geht zuerst durch die Arbeitsbereichs-Vertrauensabfrage.

### Vorführen und Weitergeben

**Ansicht ▸ Präsentationsmodus** vergrößert auf einen Schlag jeden Editor, die Seite im eingebauten Browser, das Ausgabefenster und das Terminal — und stellt beim Verlassen alles genau wieder her. **Ansicht ▸ Tastengriffe zeigen** blendet den eben gedrückten Tastengriff groß ein, aber nie das, was Sie tippen. **Bearbeiten ▸ Als Markdown kopieren** kopiert die Auswahl als abgegrenzten Block mit der richtigen Sprachmarke, und die Variante **mit Link** hängt den GitHub-Link auf dieselben Zeilen an. **Werkzeuge ▸ Bildschirmfoto sichern…** malt das ganze Fenster in doppelter Größe, mit Varianten für den Editor-Tab allein, für die Zwischenablage und für den Projektbaum als Markdown.

<a id="6-the-studios"></a>
## 6. Die Studios

### Zugang über Tastatur und Bildschirmleser

Jedes Bedienelement im Rack trägt einen zugänglichen Namen, und das wird bei jedem Bauen geprüft. Drehregler sind Schieberegler, die auf Pfeiltasten, Pos1 und Ende hören; Knöpfe hören auf Leertaste und Eingabe, auch die abgeblendeten, die sagen, warum sie ablehnen; LEDs und Anzeigen melden ihren Zustand. Tab dreht das Rack — außer wenn der Fokus auf einem Bedienelement liegt, wo es dem gewohnten Weiterspringen den Vortritt lässt.

### Git, in der Statusleiste

Das Zeichen **⎇ Zweig** zeigt, auf welchem Zweig Sie sind und wie viele Dateien sich geändert haben; es wird von der Platte gelesen und kostet daher keinen Prozess. Ein Klick öffnet die vollständige Geschichte, und das Menü trägt **Projektunterschiede**, **Anmerken**, die Pull Requests über Ihr eigenes `gh` und **Commit-Nachricht mit KVASIR entwerfen**.

### Aufgabenbrett (⌥⌘1)

Ein Kanban je Projekt, gesichert in `.nmoxtasks.json`, neben Ihrem Code und mit ihm versioniert. Ziehen Sie Karten oder bewegen Sie sie mit der Tastatur: **⌘↑/⌘↓** ordnet um, und die bewegte Karte behält den Fokus. Grenzen für angefangene Arbeit sind Ratschläge, keine Schranken: Die Kopfzeile wird rot, und nichts hält Sie auf. Der Knopf **Überblick** tauscht die Spalten gegen eine Übersicht — angefangen, heute und diese Woche fertig, Fluss je Tag, alternde Karten — und die Stechuhr (**Einstempeln**) misst die wirkliche Zeit je Karte, mit genau einer laufenden Uhr auf dem ganzen Brett. **Standup** macht daraus einen Bericht zum Einfügen.

### Block-Studio (⌥⌘5)

Setzen Sie echte Web Components aus typisierten, ineinandergreifenden Teilen zusammen, nach Art von Scratch: unerlaubte Schachtelungen werden abgelehnt, der Code entsteht als eigenständiges Custom Element, und ein Klick auf ein Teil hebt seine Zeilen hervor. Ein Vorschauserver im Speicher zeigt die Komponente wirklich, zusammengesetzt mit den anderen gültigen Komponenten Ihrer Bibliothek. Der Hin- und Rückweg ist genau: das eben Gelesene neu zu erzeugen ergibt Byte für Byte dieselbe Datei.

### API-Studio (⌥⌘8)

Sammlungen, Anfragen, Umgebungen mit `{{Variablen}}` und Tests, gesichert in `.nmoxapi.json` — die Geheimnisse allein im Schlüsselbund, nie in dieser Datei. Jede Antwort bekommt eine Sicherheitsnote aus ihren Kopfzeilen. Einlesen aus curl, `.http`, OpenAPI, Postman, Insomnia und HAR; Ausgabe als `.http` und Kopieren als curl oder als `fetch`, mit bereits aufgelösten Variablen.

### Datenbank-Studio (⌥⌘7)

SQLite, PostgreSQL, MySQL, MariaDB, MongoDB und CouchDB, mit mitgelieferten Treibern und Kennwörtern allein im Schlüsselbund. Die Konsole kennt die Maschine, jede Anweisung hat ihr eigenes Ergebnisgitter, und Zeilen lassen sich im Gitter selbst bearbeiten, sofern ein Primärschlüssel da ist — mit einer Vorschau der genauen UPDATEs vor dem Anwenden und einem ehrlichen Grund, wenn etwas nur lesbar ist. Ausgabe als CSV oder JSON, Formeln entschärft.

### Smart-Contract-Studio (⌥⌘6)

Der Artefaktbaum von Foundry und Hardhat, ein von der ABI geführtes **Interagieren** mit entschlüsselten Rückgaben und Rücknahmen, eine **Beobachten**-Ansicht für Blöcke und Ereignisse, und **Aufsicht** mit der Gastabelle, den EIP-170-Größenurteilen und dem Adressbuch. **Niemals private Schlüssel**: Sendungen laufen über die entsperrten Konten eines lokalen Netzes, und geheime URLs wohnen im Schlüsselbund.

### Infrastruktur-Designer (⌥⌘9)

Eine Fläche für DigitalOcean, Hetzner und Cloudflare: gleichen Sie ab, was wirklich existiert, frischen Sie auf, um Abweichungen zu sehen, und zerstören Sie einen Stapel mit seinen Kosten vor Augen. Unerlaubte Verbindungen lehnen laut ab und sagen warum, und solange eine Wolkenoperation läuft, sperrt sich die Fläche mit einem Band, das genau das sagt.

### IRC (⌥⌘3)

Ein vollständiger Client in der IDE: TLS mit echter Namensprüfung, SASL, IRCv3-Erweiterungen, Vervollständigung per Tabulator, Hervorhebungen, URLs, die im eingebauten Browser öffnen, Mitschrift auf Platte, eigene Filter und eine Kanalliste, die Sie beim Tippen filtern.

### Die mitgelieferte Website

**Hilfe ▸ NMOX-Studio-Website (lokal)** liefert die Seite des Produkts aus seinem eigenen Rack aus, auf der lokalen Schnittstelle. Sie spricht die dreizehn Sprachen, die die IDE spricht; der Wähler steht im Fuß der Seite.

### Browser (⌥⌘4)

Ein echter Browser in der IDE, mit eigenen Entwicklerwerkzeugen — Konsole, DOM, Netzwerk, Speicher und Ansichten für Vue, Svelte und Angular — weil die Maschine keinen Inspektor mitbringt und dieser hier unserer ist. Er kennt Ihre Quellen: Wählen Sie ein Element, öffnen Sie die Zeile, die es erzeugt hat, gestalten Sie es an Ort und Stelle um, und die Deklaration landet in der Quell-Stilvorlage. Eine gesicherte Datei lädt die Seite neu, und echte Gerätegrößen dienen dazu, Ihr anpassungsfähiges Layout zu erproben.

<a id="7-docker"></a>
## 7. Docker

Der Docker-Reiter ist eine Schaltzentrale: Zustand der Engine, Container, Images, Volumes und Netzwerke, mit Starten, Stoppen, Protokollen und Aufräumen. Das Rack-Gerät HARBOR zeigt dasselbe auf einen Blick. Und wie schon gesagt: Startest du einen Postgres-, MySQL- oder Mongo-Container, bietet dir das DB-Studio eine fertige Verbindung an.

Der Reiter **Dockerize** erzeugt ein produktionstaugliches `Dockerfile`, eine `.dockerignore` und eine Compose-Datei, zugeschnitten auf die Werkzeugkette deines Projekts — Node, PHP-FPM mit nginx und weitere.

<a id="8-wizards-and-kits"></a>
## 8. Assistenten und Kits

Alle liegen unter *Neue Datei…* und im Kontextmenü des Projekts, und alle sind **idempotent und überschreiben nie**: ein zweiter Lauf aktualisiert, was ihm gehört, und lässt deine Änderungen in Ruhe; was er nicht überschreiben darf, landet daneben als `.suggested`-Datei.

### Standards-Kit

`robots.txt`, `sitemap.xml`, das Web-Manifest, die `security.txt` nach RFC 9116 und `humans.txt`, erzeugt aus deinen Antworten.

### PWA-Kit

Ein vollständiger Satz Symbole, geschmiedet aus einem einzigen Bild, maskierbare Varianten eingeschlossen; ein lesbarer Service Worker — App-Shell oder Netz zuerst, deine Wahl —, eine Offline-Seite und die Verdrahtung in `index.html`, die alles zusammenhält.

### Barrierefreiheits-Kit

Barrierefreiheit als Ausgangspunkt, nicht als nachträgliche Prüfung: `a11y.css` (ein sichtbarer Fokusring, ein Hilfsmittel für Text, den nur Screenreader lesen, Stile für den Sprunglink und ein Block für alle, die weniger Bewegung wünschen), `A11Y-NOTES.md` mit dem Tastaturdurchgang und den Fragen, die keine Automatik beantwortet, sowie die idempotente Verdrahtung in `index.html` — Sprache, Sprunglink, Stylesheet. Ein Viewport, der das Vergrößern verbietet, wird gemeldet, nie umgeschrieben; was das Kit nicht beheben kann, sagt es, statt es anzufassen.

### Internationalisierungs-Kit

Übersetzbar vom ersten Tag an, das Geschwister des Barrierefreiheits-Kits: `locales/en.json` und `locales/es.json` (ein Katalog je Sprache, dieselben Schlüssel), ein `i18n.js` ohne Abhängigkeiten, das den Katalog auf `data-i18n`-Markup anwendet, `<html lang>` ehrlich hält und einen fehlenden Schlüssel als sich selbst zeigt statt als stille Lücke; dazu `I18N-NOTES.md` — keine zusammengesetzten Fragmente, `Intl` für Datum und Zahlen, der Durchgang von rechts nach links und die Pseudolokalisierung.

### Vertrags-Kit (Web3)

Wähle eine Kette — Solidity mit Foundry, Soroban, Solana, CosmWasm, ink!, Cairo, Move, Bitcoin mit Miniscript, Clarity auf Stacks, Cardano mit Aiken oder TON mit Tact — und einen Vertragsnamen, und das Kit legt den live erprobten Anfang an: Manifest, Vertrag, nativer Test und eine CONTRACT-NOTES.md, die die Rack-Geräte und die einmaligen Schritte benennt. Schlüssel berühren die IDE nie.

### Klassik-Kit

Ergänze beliebigen Code um jQuery, MooTools, Prototype, Backbone mit Underscore oder Knockout, entweder mitgeliefert im Repository (feste Versionen, sha256 vermerkt) oder als npm-Abhängigkeiten; dazu Gerüste für webpack, grunt, gulp oder bower.
