# Tutorial: Das Task-Rack

<!-- languages -->
[English](the-task-rack.md) · [Español](the-task-rack.es.md) · [Français](the-task-rack.fr.md) · **Deutsch** · [Русский](the-task-rack.ru.md) · [Українська](the-task-rack.uk.md) · [Polski](the-task-rack.pl.md) · [Português (Brasil)](the-task-rack.pt.md) · [Bahasa Indonesia](the-task-rack.id.md) · [Filipino](the-task-rack.tl.md) · [Tiếng Việt](the-task-rack.vi.md) · [简体中文](the-task-rack.zh.md) · [हिन्दी](the-task-rack.hi.md) · [עברית](the-task-rack.he.md) · [العربية](the-task-rack.ar.md)
<!-- /languages -->

Das Task-Rack ist die Kernidee von NMOX Studio: Ihre Werkzeuge zum Bauen,
Testen und Ausliefern, aufgebaut als Rack aus Hardware-Geräten, die Sie
mit Patchkabeln verbinden. Ein Gerät führt einen echten Befehl aus; ein
Kabel trägt ein echtes Signal. Dieses Tutorial baut eine winzige
Verkabelung — etwas ausführen und seine Ausgabe auf dem Monitor
verfolgen —, damit die Metapher greift.

![Das Rack auf ein echtes Projekt ausgerichtet — Geräte eingebaut und in Betrieb](../images/task-rack.png)

![Tab dreht das Rack — Patchkabel verbinden die Geräte auf der Rückseite](../images/rack-rear.png)

## Bevor Sie beginnen

Öffnen Sie ein Projekt (jedes Node-Projekt geht; `Datei ▸ Neues Projekt…` →
„Vanilla Web“, falls Sie eines brauchen). Ein Projekt zu öffnen **richtet**
das Rack darauf aus, sodass jedes Gerät im Verzeichnis dieses Projekts
läuft.

## Schritte

1. **Öffnen Sie das Rack.** Klicken Sie den Tab **Task-Rack** (oder drücken
   Sie `⌘9`). Das Start-Rack hat einen einzigen **MONITOR** — das
   Konsolengerät, das Befehlsausgabe und Fehlerzeilen zeigt.

2. **Fügen Sie einen Starter hinzu.** Ziehen Sie **IGNITION** aus dem
   Geräteregal links auf das Rack. IGNITION ist das mehrsprachige
   „Ausführen“-Gerät; auf ein Node-Projekt ausgerichtet führt es
   `npm run dev` aus (Paketmanager und Toolchain erkennt es selbst).

3. **Verbinden Sie es mit dem Monitor.** Klicken Sie **Rückseite (Tab)** (oder
   drücken Sie Tab), um die Rückseite zu sehen, klicken Sie dann die Buchse
   **OUT** von IGNITION und die Buchse **IN** von MONITOR an — ein
   Patchkabel verbindet sie. (Ziehen zwischen den Buchsen geht auch; Klicken
   ist einfacher, wenn das Rack breit ist.)

4. **Lösen Sie es aus.** Drehen Sie zurück auf die Vorderseite und drücken
   Sie den Knopf **IGNITE** von IGNITION. Er startet den Prozess; die Ausgabe
   läuft in MONITOR, und die Status-LEDs leuchten. Ist das Projekt noch
   nicht vertrauenswürdig, erscheint zuerst eine einmalige Abfrage zum
   Arbeitsbereichs-Vertrauen — die Schranke, die verhindert, dass ein
   geklontes Repository ohne Ihr Einverständnis seine Skripte ausführt.

5. **Speichern Sie die Verkabelung.** Der Knopf **Patch speichern**
   schreibt `.nmoxrack.json` neben Ihr Projekt. Öffnen Sie das
   Projekt später wieder, kommt die Verkabelung — Geräte, Kabel,
   Reglerstellungen — genau so zurück.

## Was Sie gerade gelernt haben

- **Geräte sind Werkzeuge mit Frontplatten.** Regler wählen Optionen,
  GO-Knöpfe führen aus, LEDs und LCDs melden den Zustand — und jedes
  Bedienelement ist echt (keine toten Regler; ein Vertragstest setzt das
  durch).
- **Kabel koordinieren Spuren.** OUT→IN ist die einfachste Verbindung;
  Bereitschaftstore (`ENABLE`), Sammelschranken (`QUORUM`) und
  Auslöserkabel lassen Sie eine ganze Pipeline zusammensetzen, die auf
  sich selbst reagiert.
- **Alles bleibt erhalten.** Die Verkabelung ist eine Datei zum Einchecken;
  das Rack stellt nach einem Absturz sogar eine laufende Sitzung wieder her.

## Weiter

- Es gibt 53 Geräte — stöbern Sie in [devices.md](../devices.md) oder im
  Geräteregal (Rechtsklick auf ein eingebautes Gerät öffnet seine
  **Verwendung von …**).
- Fragen Sie [KVASIR](kvasir.de.md), warum ein Lauf fehlschlug.
- Exportieren Sie eine Verkabelung als GitHub-Actions-Workflow: der
  **CI-Export** des Racks.
