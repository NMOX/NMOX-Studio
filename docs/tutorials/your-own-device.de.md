# Tutorial: ein eigenes Rack-Gerät schreiben

<!-- languages -->
[English](your-own-device.md) · [Español](your-own-device.es.md) · [Français](your-own-device.fr.md) · **Deutsch** · [Русский](your-own-device.ru.md) · [Українська](your-own-device.uk.md) · [Polski](your-own-device.pl.md) · [Português (Brasil)](your-own-device.pt.md) · [Bahasa Indonesia](your-own-device.id.md) · [Filipino](your-own-device.tl.md) · [Tiếng Việt](your-own-device.vi.md) · [简体中文](your-own-device.zh.md) · [हिन्दी](your-own-device.hi.md) · [עברית](your-own-device.he.md) · [العربية](your-own-device.ar.md)
<!-- /languages -->

*In einer Sitzung. Sie fügen dem Rack mit einem Texteditor ein Gerät
hinzu, drücken seinen Knopf, sehen zu, wie es einen echten Befehl
ausführt, und verkabeln seine Ausgabe mit MONITOR — ohne eine Zeile
Java.*

Neu in 2.0.0. Das Rack kam mit dreiundfünfzig Geräten und bisher mit
genau einem Weg zu einem vierundfünfzigsten: ein NetBeans-Plugin
schreiben. Dies ist der andere Weg.

![Das Task-Rack: Links im Geräteregal erscheint ein Gerät aus ~/.nmox/devices.d, neben den eingebauten](../images/tabs/the-task-rack.png)

## 1. Den Ordner anlegen

```bash
mkdir -p ~/.nmox/devices.d
```

Das ist der ganze Installationsschritt. Das Rack liest den Ordner erst
bei Bedarf, es muss also nichts neu gestartet werden.

## 2. Das Gerät schreiben

Legen Sie Folgendes in `~/.nmox/devices.d/counter.json` ab:

```json
{
  "id": "com.example.counter",
  "title": "COUNTER",
  "tagline": "counts the files in the project",
  "accent": "#7FB3D5",
  "category": "OBSERVE",
  "usage": "COUNT lists the project's files of the dialled KIND and shows how many.\nPatch OUT into MONITOR to read the list, or DONE onward to chain.",
  "knobs": [
    { "key": "kind", "label": "KIND", "options": ["js", "ts", "css", "md"] }
  ],
  "ports": [
    { "id": "count", "label": "COUNT", "direction": "IN", "signal": "TRIGGER" },
    { "id": "done", "label": "DONE", "direction": "OUT", "signal": "TRIGGER" },
    { "id": "out", "label": "OUT", "direction": "OUT", "signal": "DATA" }
  ],
  "buttons": [
    { "label": "COUNT", "role": "QUERY",
      "command": ["git", "ls-files", "*.{{kind}}"],
      "emit": "done", "trigger": "count" }
  ]
}
```

Jede Zeile darin hat eine Aufgabe: Der **Regler** wird im Befehl zu
`{{kind}}`, die Rolle **QUERY** färbt den Knopf blau (das Farbgesetz:
Blau fragt, Grün handelt, Rot hält an), und die drei Anschlüsse
machen das Gerät verkabelbar.

## 3. Einbauen

Öffnen Sie das **Task-Rack** (`⌘9` oder den Tab Task-Rack) und sehen
Sie in der Schublade **Beobachten** des Geräteregals nach. Dort steht
COUNTER, mit Ihrer Tagline darunter. Ziehen Sie es auf eine Schiene.

Klicken Sie es mit der rechten Maustaste an und wählen Sie
**Verwendung von COUNTER…** — das ist Ihr `usage`-Text,
und deshalb verlangt das Format zwei echte Zeilen.

## 4. Drücken

> Beachten Sie: Es gibt keine `units`-Zeile. Das Regal misst die
> Frontplatte und wählt die kleinste Höhe, die passt (dieses Gerät
> braucht wegen des Reglers 2U). Geben Sie `units` nur an, wenn Sie
> zusätzlichen Platz wollen.

Richten Sie das Rack auf ein Git-Projekt, stellen Sie **KIND** auf `js`
und drücken Sie **COUNT**.

Der erste Druck öffnet die Abfrage **Arbeitsbereichsvertrauen**, denn
eine Gerätedatei führt echte Befehle aus, und der Wirt sichert jeden
Prozessstart genauso wie bei einem eingebauten Gerät. Vertrauen Sie dem
Arbeitsbereich, und das LCD zeigt den Befehl, dann die letzte
Ausgabezeile. Die Buchse DONE pulsiert grün.

Wählen Sie stattdessen „Sicher bleiben“, startet nichts — die Ablehnung
ist die Funktion.

## 5. Verkabeln

Ziehen Sie ein Kabel von **OUT** an COUNTER zu **IN** an MONITOR.
Drücken Sie COUNT noch einmal: Jede Zeile landet auf dem Monitor, denn
ein erklärter `OUT`/`DATA`-Anschluss erhält die Ausgabe des Laufs ohne
weitere Einstellungen.

Ziehen Sie jetzt ein Kabel vom Takt von TEMPO zum Eingang **COUNT** an
COUNTER. Das Gerät, das Sie in einem Texteditor geschrieben haben, läuft
nun nach der Uhr.

## 6. Mit Absicht kaputtmachen

Bearbeiten Sie die Datei und ändern Sie den Befehl in etwas mit einer
Pipe:

```json
"command": ["sh", "-c", "git ls-files | wc -l"]
```

Speichern Sie, und COUNTER *verschwindet* aus dem Regal. So lehnt das
Format eine Shell-Zeile ab: Ein Befehl ist ein argv-Array, damit jeder,
der die Datei liest — Sie in sechs Monaten oder eine Kollegin beim
Review —, genau sieht, was ausgeführt wird. Das IDE-Protokoll nennt die
übersprungene Datei und den Grund:

```
device file counter.json skipped: button "COUNT" command token
"git ls-files | wc -l" contains "|" — commands are argv, never a shell line
```

Stellen Sie die Array-Form wieder her, und das Gerät kehrt zurück.
Dasselbe gilt für ein Werkzeug, das über einen Pfad benannt ist
(`./x.sh`), für eine unbekannte `{{variable}}` oder für eine einzeilige
`usage`: Die Datei wird ganz übersprungen statt halb geladen, denn ein
Gerät, dessen Beschriftung lügt, ist schlimmer als gar keins.

## Was Sie gerade gelernt haben

- Ein Gerät ist eine **Datei**: `~/.nmox/devices.d/*.json`, bei Bedarf
  gelesen, ohne Neustart, ohne Build.
- Regler werden zu `{{variables}}`, Rollen wählen die Farben, Anschlüsse
  machen das Gerät verkabelbar und seine Ausgabe lesbar.
- Die **Gesetze bleiben beim Wirt** — Arbeitsbereichsvertrauen vor jedem
  Prozessstart, das Farbgesetz, das Anschlussvokabular, das
  Regalgesetz —, also kann eine Gerätedatei weder einen ungesicherten
  Befehl noch ein rotes GO ausdrücken, selbst wenn sie es versucht.
- Ablehnungen sind laut im Protokoll und vollständig in der Wirkung.

## Weiter

- [device-files.md](../device-files.md) — die vollständige Referenz
- [Das Task-Rack](the-task-rack.de.md) — Verkabelung, Gates und Vorlagen
- [device-spi.md](../device-spi.md) — die Java-SPI, für Geräte mit
  echtem Zustand: eigenes Zeichnen, Abfragen in Intervallen,
  langlebige Verbindungen
