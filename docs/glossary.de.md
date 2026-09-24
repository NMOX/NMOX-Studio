# Glossar

<!-- languages -->
[English](glossary.md) · [Español](glossary.es.md) · [Français](glossary.fr.md) · **Deutsch** · [Русский](glossary.ru.md) · [Українська](glossary.uk.md) · [Polski](glossary.pl.md) · [Português (Brasil)](glossary.pt.md) · [Bahasa Indonesia](glossary.id.md) · [Filipino](glossary.tl.md) · [Tiếng Việt](glossary.vi.md) · [简体中文](glossary.zh.md) · [हिन्दी](glossary.hi.md) · [עברית](glossary.he.md) · [العربية](glossary.ar.md)
<!-- /languages -->

Die Wörter, die NMOX Studio benutzt und eine andere IDE nicht, dazu die NetBeans-Begriffe, die durchscheinen. Jeder Eintrag sagt, was das Wort hier bedeutet und wo Sie mehr lesen.

<a id="the-rack"></a>
## Das Rack

**Task-Rack** (⌘9) — Das Fenster, in dem Ihre Werkzeuge laufen. Jede Aufgabe (installieren, bauen, testen, ausliefern, prüfen, ausrollen) ist ein *Gerät*, das in einem Rack sitzt wie die Hardware in einem Tonstudio. [Benutzerhandbuch §4](user-guide.de.md#4-the-task-rack).

**Gerät** — Ein Werkzeug im Rack, zum Beispiel VELOCITY (Vite), VERITAS (Tests) oder PURITY (Lint). Ein Gerät hat eine Vorderseite (die *Frontplatte*) mit Reglern, Knöpfen, Lämpchen und einer kleinen Anzeige und eine Rückseite mit *Buchsen*. Es gibt 53 eingebaute Geräte, aufgeführt in [der Gerätereferenz](devices.md). Eigene fügen Sie als JSON-Datei in `~/.nmox/devices.d/` hinzu ([Gerätedateien](device-files.md)).

**Frontplatte** — Die Vorderseite eines Geräts. Drücken Sie im Rack **Tab**, um es umzudrehen und die Rückseite zu sehen.

**Buchse** — Ein Anschluss auf der Rückseite eines Geräts. Ausgangsbuchsen senden Signale, Eingangsbuchsen empfangen sie. Es gibt drei Arten von Signal:
- Ein **Trigger** ist ein einzelner Impuls: „der Build ist fertig“, „OK“, „FAIL“.
- Ein **Gate** ist an oder aus: „der Server läuft“.
- **Daten** tragen Text, etwa eine URL oder eine Ausgabezeile.

**Kabel** — Eine Verbindung von einer Ausgangsbuchse zu einer Eingangsbuchse. Verbinden Sie die OK-Buchse eines Builds mit der RUN-Buchse des Testläufers, und die Tests laufen jedes Mal, wenn ein Build gelingt. Um zwei Buchsen zu verbinden, ziehen Sie von der einen zur anderen oder klicken erst die eine und dann die andere an.

**Patch** — Ein ganzes Rack: seine Geräte, ihre Einstellungen und ihre Kabel. Gespeichert neben dem Projekt als `.nmoxrack.json`, es lohnt sich also, es einzuchecken.

**Preset** — Ein fertiger Patch, den Sie aus dem Menü **Presets** des Racks laden, zum Beispiel *Ship Gate* oder *E2E Loop*. Speichern Sie einen beliebigen Patch in `~/.nmox/presets.d/`, und er erscheint ebenfalls im Menü.

**Starter-Rack** — Der Patch, den ein Projekt beim ersten Öffnen bekommt, gewählt nach der Art des Projekts: eine Vite-Konsole für eine Vite-App, Spuren zum Ausführen, Debuggen und Testen für ein Cargo-Crate, und so weiter.

**Spur** — Zwei Bedeutungen, beide ums Ausführen:
- Eine **Pipeline**: eine Kette von Geräten, durch Kabel verbunden, etwa installieren → bauen → testen. Mehrere Spuren können nebeneinander laufen, und QUORUM wartet, bis alle fertig sind.
- Die **AUTO-Spur** eines Geräts: der Befehl, den es für dieses Projekt wählt. Auf AUTO führt das Testgerät in einem Node-Projekt `npm test` aus und in einem Rust-Projekt `cargo test`.

**Teilen… / Importieren…** — Ein Rack für jemand anderen in eine Datei speichern oder eines von jemandem laden. Bevor irgendetwas eingebaut wird, zeigt Importieren alles, was die Datei enthält, und jedes Gerät kommt ausgeschaltet an.

**Rack-Galerie** — **Extras ▸ Rack-Galerie…** listet Racks der Gemeinschaft, Presets, Starter-Racks und Ihre eigenen gespeicherten Racks. Jeder Eintrag zeigt, wofür er da ist und welche Werkzeuge er braucht. [Racks der Gemeinschaft](racks.md).

<a id="projects-and-running"></a>
## Projekte und Ausführen

**Anpeilen** / **angepeiltes Projekt** — Das Projekt, an dem die IDE gerade arbeitet. Ein Projekt zu öffnen peilt es an: Das Rack, die Studios, die Statuszeile und Ausführen folgen dem angepeilten Projekt. Ein anderes anzupeilen schaltet sie alle um, und was noch läuft, wird vorher angehalten, nach Rückfrage.

**Arbeitsbereichsvertrauen** — Die Frage, die NMOX Studio stellt, bevor es zum ersten Mal den eigenen Code eines Projekts ausführt (Skripte, Builds, Tests). Antworten Sie **Sicher bleiben**, läuft nichts aus dem Projekt. Mit **Arbeitsbereich vertrauen** erlauben Sie es. Ihre Antwort wird je Ordner gemerkt.

**▶ und ■** — Ausführen und Anhalten in der Werkzeugleiste. ▶ (F6) führt das angepeilte Projekt aus. ■ (⌥⌘.) hält jeden Befehl an, den NMOX Studio für Sie gestartet hat.

**⇄-Zeichen** / **bereitgestellt** — Wenn etwas, das Sie ausführen, eine lokale Adresse ausgibt, etwa `http://localhost:5173/`, erscheint die Adresse in der Statuszeile nach einem ⇄ und dem Wort *bereitgestellt*. Dieser laufende Server ist eine *Bereitstellung*. Klicken Sie auf die Adresse, um sie im Browser zu öffnen. Die Schnellsuche führt Bereitstellungen unter *Aktive Server*.

**Experiment** — Ein Wegwerfprojekt aus einer Vorlage in `~/.nmox/experiments`. Seine Abhängigkeiten sind schon installiert, und ihm wird schon vertraut. **Befördern** Sie es, um es zu behalten, oder **verwerfen** Sie es. **Datei ▸ Neues Experiment…**.

**Lernraum** — Eine geführte Anleitung für eine Sprache oder ein Rahmenwerk. Er legt ein echtes Projekt an, einen Rundgang und ein Rack mit einem lebendigen REPL, und **Datei ▸ Arbeit prüfen** prüft Ihre Übungen. Es gibt 93. **Datei ▸ Neuer Lernraum…**.

**PREFLIGHT** — Das Gerät für die Prüfung vor dem Ausliefern. Es führt die Prüfungen, die Ihr Projekt festlegt (Lint, Typen, Tests, Build), als ein einziges Bestanden oder Durchgefallen aus.

**Erste Schritte** — Die Checkliste auf dem Tab Willkommen (Spalte ERSTE SCHRITTE). Die Schritte haken sich selbst ab, sobald Sie sie tun, und ein Haken verschwindet nie wieder.

<a id="the-windows"></a>
## Die Fenster

**Studio** — Ein Fenster mit einem eigenen Werkzeug für eine Art von Arbeit. Es gibt fünf: **API-Studio** (⌥⌘8), **Datenbank-Studio** (⌥⌘7), **Smart-Contract-Studio** (⌥⌘6, Smart Contracts), **Block-Studio** (⌥⌘5, Web Components aus Bausteinen) und den **Infrastruktur-Designer** (⌥⌘9, Cloud-Infrastruktur). Jedes speichert seine Arbeit neben dem Projekt in einer `.nmox*.json`-Datei. Das **Projekt-Studio** teilt den Namen, ist aber der Dateibaum und die Projektvorlagen.

**Arbeitsplatz** (⌥⌘0) — Die Basis: was läuft, was offen ist, und Ihre zuletzt benutzten Projekte und Dateien.

**Aufgaben-Board** (⌥⌘1) — Ein Kanban-Board je Projekt, mit Sprints und einer Stechuhr, gespeichert als `.nmoxtasks.json`.

**Willkommen** — Der Start-Tab: Aktionen für den Anfang, zuletzt benutzte Projekte, die Spalte *WERKZEUGE*, die jedes Fenster aufführt, und Erste Schritte.

<a id="ai"></a>
## KI

**KVASIR** — Der Name der KI-Funktionen von NMOX Studio: Fragen, Bearbeiten, Vervollständigen, Erklären, Commit-Nachricht entwerfen. Es arbeitet mit Claude, ChatGPT oder Gemini und Ihrem eigenen API-Schlüssel, der im Schlüsselbund des Betriebssystems liegt. Jede Funktion bittet einmal um Ihre Zustimmung und nennt genau, was sie senden wird. Nichts wird gesendet, bevor Sie eine Funktion benutzen. Frühere Versionen nannten es ORACLE.

**Agent Port** — **Extras ▸ Agent Port (MCP)…** gibt einem KI-Agenten, der auf Ihrem Rechner läuft, etwa einem Programmierassistenten, lesenden Zugriff auf den Zustand der IDE über MCP: offene Dateien, Diagnosen, Läufe, Symbole. Er ist von Bauart nur lesend und lauscht nur auf Ihrem eigenen Rechner. [Anleitung](tutorials/agent-port.md).

<a id="netbeans-terms-you-may-see"></a>
## NetBeans-Begriffe, die Ihnen begegnen können

NMOX Studio ist auf der NetBeans-Plattform gebaut, und einige ihrer Wörter scheinen durch.

**Modul** / **NBM** — Ein Teil der Anwendung. Ein *NBM* ist die Datei, in der ein Modul ausgeliefert wird. **Extras ▸ Plugins** installiert Aktualisierungen Modul für Modul.

**Aktualisierungszentrum** — Woher **Extras ▸ Plugins ▸ Aktualisierungen** neue Versionen der Module von NMOX Studio bezieht. Es liest einen Katalog, der mit jeder GitHub-Version veröffentlicht wird.

**userdir** — Der Ordner, in dem NMOX Studio seine Einstellungen, die Fensteranordnung, Protokolle und installierte Aktualisierungen aufbewahrt. Der Dialog „Über“ zeigt, wo er liegt (unter Windows und Linux im Menü Hilfe, unter macOS im Menü NMOX Studio). Sein Protokoll liegt unter `var/log/messages.log`. Um mit frischen Einstellungen zu beginnen, starten Sie mit `--userdir <ein leerer Ordner>`.

**Optionen** / **Einstellungen…** — Der Einstellungsdialog. Unter Windows und Linux ist es **Extras ▸ Optionen**, unter macOS **NMOX Studio ▸ Einstellungen…**.

**Aufgabenliste** — Das Fenster, das die im Projekt gefundenen Probleme auflistet: die Fehler und Warnungen der Sprachserver und die Lint- und Typprüfungsergebnisse des Racks. Klicken Sie auf ein Problem, um zu dieser Zeile zu springen.
