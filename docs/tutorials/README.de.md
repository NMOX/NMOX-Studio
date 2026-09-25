# NMOX Studio — Tutorials

<!-- languages -->
[English](README.md) · [Español](README.es.md) · [Français](README.fr.md) · **Deutsch** · [Русский](README.ru.md) · [Українська](README.uk.md) · [Polski](README.pl.md) · [Português (Brasil)](README.pt.md) · [Bahasa Indonesia](README.id.md) · [Filipino](README.tl.md) · [Tiếng Việt](README.vi.md) · [简体中文](README.zh.md) · [हिन्दी](README.hi.md) · [עברית](README.he.md) · [العربية](README.ar.md)
<!-- /languages -->

Kurze Rundgänge zum Selbermachen durch die Systeme, die NMOX Studio von
einer gewöhnlichen IDE unterscheiden. Jeder passt in eine Sitzung —
Fenster öffnen, den Schritten folgen, und Sie haben die Funktion
wirklich benutzt.

Für die breite Referenz (Installation, jedes Menü, jedes Sicherheitsnetz)
siehe das [Benutzerhandbuch](../user-guide.de.md). Die vollständige
Geräteliste steht in [devices.md](../devices.md).

## Die Systeme

| Tutorial | Was Sie tun | Öffnen mit |
|----------|-------------|------------|
| [Das Task-Rack](the-task-rack.de.md) | Eine Verkabelung Ausführen→Monitor stecken und auslösen sehen | ⌘9 / Tab Task-Rack |
| [Ein eigenes Gerät schreiben](your-own-device.de.md) | Ein Rack-Gerät mit einem Texteditor hinzufügen — kein Java, kein Neustart | `~/.nmox/devices.d/` |
| [Arbeitsplatz](workbench.de.md) | Vom Heimathafen aus zwischen Projekten und Werkzeugen springen | ⌥⌘0 |
| [Projekt-Studio](project-studio.de.md) | Ein Projekt anlegen und ohne Terminal ausführen | Tab Projekt-Studio |
| [API-Studio](api-studio.de.md) | Eine Anfrage senden, prüfen und die Sicherheitsnote lesen | ⌥⌘8 |
| [Datenbank-Studio](db-studio.de.md) | Mit SQLite verbinden und eine Zeile im Gitter bearbeiten | ⌥⌘7 |
| [Smart-Contract-Studio](contract-studio.de.md) | Einen Vertrag kompilieren, auf eine lokale Chain ausrollen und aufrufen | ⌥⌘6 (Web3) |
| [Infrastruktur-Designer](infra-designer.de.md) | Ein Droplet mit Firewall zeichnen und das Ausrollen probeweise durchspielen | ⌥⌘9 |
| [Block-Studio](block-studio.de.md) | Eine Web Component aus ineinandergreifenden Blöcken bauen | ⌥⌘5 |
| [Mehrsprachig bearbeiten und debuggen](polyglot-editing-and-debugging.de.md) | Einen Haltepunkt in einer Node-App setzen und treffen | ein beliebiges Projekt öffnen |
| [Vom Browser zum Quelltext](browser-to-source.de.md) | Ein Element in der Seite anklicken, in seinem Quelltext landen und es aus den DevTools umgestalten | ⌥⌘4 → DevTools → DOM |
| [Der Agent Port (MCP)](agent-port.de.md) | Einen KI-Agenten auf den Live-Zustand der IDE richten — von Bauart nur lesend | Extras ▸ Agent Port (MCP)… |
| [Die zweite Woche](the-second-week.de.md) | Committen, einen Diff prüfen, einen Konflikt auflösen, einen Pull Request eröffnen und einem Stacktrace folgen — gits eigene Schritte, in dem Fenster, in dem Sie arbeiten | Team ▸ NMOX Studio mit Git verwenden… |
| [Das Docker-Panel](docker-panel.de.md) | Container untersuchen und ein Projekt dockerisieren | Tab Docker-Panel |
| [Das Aufgaben-Board und Sprints](task-board.de.md) | Ein Kanban mit Stechuhr, Standup per Klick und Sprint-Burndown aus einer eingecheckten Datei führen | ⌥⌘1 |
| [Vor Publikum zeigen](show-it-to-a-room.de.md) | Aus der IDE heraus vorführen, teilen und Screenshots machen — vom Präsentationsmodus bis zu „Projektbaum als Markdown kopieren“ | Ansicht ▸ Präsentationsmodus |
| [KVASIR](kvasir.de.md) | Die KI fragen, warum ein Lauf fehlschlug | Rack → KVASIR |
| [Alles erklären lassen](explain-anything.de.md) | KVASIRs vier Gesichter nutzen: Läufe, Code, API-Antworten, Datenbankfehler | überall, wo etwas fehlschlägt |
| [Umstieg von Postman](migrating-from-postman.de.md) | Sammlungen, HAR-Mitschnitte und mehr importieren — Geheimnisse wandern in den Schlüsselbund | ⌥⌘8 → Importieren… |
| [Image Kit (Web)](image-kit.de.md) | Die Bilder eines Projekts pressen: kleinere JPEGs, WebP-Dateien daneben, ehrlicher Bericht | Datei ▸ Zum Projekt hinzufügen ▸ Image Kit (Web)… |
| [Lernräume](learning-spaces.de.md) | Eine geführte Sandbox mit Live-REPL starten | Neuer Lernraum… |
| [Assistenten und Kits](wizards-and-kits.de.md) | Eine PWA, Standarddateien oder Gerüste fürs klassische Web hinzufügen | Datei ▸ Zum Projekt hinzufügen |

> **Ein Hinweis zu den Tastenkürzeln.** Die Studios liegen auf macOS in
> der `⌥⌘`-Familie (Wahl-Befehl) — `⌥⌘6`–`⌥⌘9`, `⌥⌘5`, `⌥⌘0` —, weil die
> einfachen `⇧⌘`-Kürzel von der Plattform belegt sind. Unter Linux/Windows
> ist der Modifikator `Alt+`; die Menüs (Fenster ▸ …) funktionieren in
> jedem Fall.

Beim ersten Start sehen Sie drei Tabs — Willkommen, das Task-Rack und den
Browser —, daneben angedockt das Projekt-Studio, den Arbeitsplatz und den
NPM-Explorer. Jedes weitere Fenster ist ein Tastenkürzel entfernt und steht
in der Spalte WERKZEUGE der Willkommensseite.
