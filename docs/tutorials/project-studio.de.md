# Tutorial: Projekt-Studio

<!-- languages -->
[English](project-studio.md) · [Español](project-studio.es.md) · [Français](project-studio.fr.md) · **Deutsch** · [Русский](project-studio.ru.md) · [Українська](project-studio.uk.md) · [Polski](project-studio.pl.md) · [Português (Brasil)](project-studio.pt.md) · [Bahasa Indonesia](project-studio.id.md) · [Filipino](project-studio.tl.md) · [Tiếng Việt](project-studio.vi.md) · [简体中文](project-studio.zh.md) · [हिन्दी](project-studio.hi.md) · [עברית](project-studio.he.md) · [العربية](project-studio.ar.md)
<!-- /languages -->

Im Projekt-Studio entstehen und leben Projekte: Vorlagen, ein
plattformeigener Dateibaum, ein Editor für package.json und
Rack-Vorlagen — dazu die IDE-eigenen Aktionen **Ausführen / Erstellen /
Testen / Bereinigen**, die funktionieren, ohne dass Sie je ein Terminal
öffnen.

![Das Projekt-Studio im linken Dock — der plattformeigene Dateibaum und die Projekt-Werkzeugleiste, daneben das offene Task-Rack](../images/tabs/project-studio.png)

## Öffnen

Der Tab **Projekt-Studio**, angedockt neben dem Arbeitsplatz, oder `Datei ▸ Neues Projekt…`.

## Schritte

1. **Legen Sie ein Projekt an.** `Datei ▸ Neues Projekt…` → wählen Sie eine
   Vorlage (Angular, Vue, Vanilla Web, Elixir/Phoenix, PHP LEMP und weitere).
   Wählen Sie einen Ort (Vorgabe ist `~/NMOX`) und schließen Sie ab. Das
   Projekt öffnet sich, und das Rack richtet sich darauf aus.

2. **Durchstöbern Sie den Baum.** Der Dateibaum ist ein echter
   Plattform-Baum — passende Dateisymbole, eine git-Anmerkung `[branch]`
   an der Wurzel und das vollständige Menü
   Öffnen/Ausschneiden/Kopieren/Löschen/Umbenennen/Werkzeuge/Eigenschaften.
   Schwere Ordner (`node_modules`, `.git`, `dist`) erscheinen ohne Kinder,
   damit auch ein riesiges Repository schnell bleibt.

3. **Ausführen — ohne Terminal.** Nutzen Sie **Ausführen** der IDE (oder
   drücken Sie IGNITE an IGNITION im Rack). Es bestimmt Ihren Paketmanager aus
   der Lock-Datei bzw. dem corepack-Eintrag des Projekts und führt den
   richtigen Befehl aus; die Ausgabe läuft ins Rack. **Erstellen**,
   **Testen** und **Bereinigen** funktionieren genauso.

4. **Bearbeiten Sie package.json.** Der eingebaute Editor bearbeitet
   Skripte und Abhängigkeiten strukturiert.

5. **Laden Sie eine Vorlage.** Das Menü **Presets ▾** des Task-Racks verdrahtet ein fertiges
   Rack für einen Arbeitsablauf — Uptime Watch, Ship Gate, Modern Web,
   Monorepo Lanes, Web3 Bench und weitere —, damit Sie die Verkabelung
   nicht von Hand bauen müssen.

## Was Sie gerade gelernt haben

- Neue Projekte werden an jedem von 60 Manifestnamen erkannt (package.json,
  Cargo.toml, go.mod, pom.xml, gleam.toml, …) plus vier über Muster
  (`.csproj`, `.fsproj`, `.sln`, `.nimble`) — selbst eine Seite mit
  `<script>`-Tags und ohne Manifest öffnet sich als STATIC-Projekt.
- Ausführen/Erstellen/Testen/Bereinigen und das Rack sind **ein
  Mechanismus**; wenn sie zum ersten Mal Projektcode ausführen, erscheint
  die Abfrage zum Arbeitsbereichs-Vertrauen.

## Weiter

- Öffnen Sie das [Task-Rack](the-task-rack.de.md), um zu sehen, was die
  Vorlage verdrahtet hat.
- Probieren Sie einen [Lernraum](learning-spaces.de.md) als geführte
  Sandbox.
