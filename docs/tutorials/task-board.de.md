# Tutorial: Das Aufgaben-Board und Sprints

<!-- languages -->
[English](task-board.md) · [Español](task-board.es.md) · [Français](task-board.fr.md) · **Deutsch** · [Русский](task-board.ru.md) · [Українська](task-board.uk.md) · [Polski](task-board.pl.md) · [Português (Brasil)](task-board.pt.md) · [Bahasa Indonesia](task-board.id.md) · [Filipino](task-board.tl.md) · [Tiếng Việt](task-board.vi.md) · [简体中文](task-board.zh.md) · [हिन्दी](task-board.hi.md) · [עברית](task-board.he.md) · [العربية](task-board.ar.md)
<!-- /languages -->

Das Aufgaben-Board ist ein Kanban je Projekt, das in einer einzigen
Datei lebt — `.nmoxtasks.json` neben Ihrem Code —, und alles andere,
was das Board tut, wird aus dieser Datei abgeleitet: eine Übersicht,
eine Stechuhr, ein tägliches Standup und ein Sprint-Burndown. Nichts
davon ist Buchhaltung, die Sie von Hand führen; die Zeitstempel der
Karten selbst sind die Aufzeichnung. Dieses Tutorial bringt ein Board in
einer Sitzung von drei Karten bis zu einem abgeschlossenen Sprint.

![Das Aufgaben-Board: drei Spalten, eine eingestempelte Karte und die laufende Uhr in der Kopfzeile](../images/task-board.png)

![Ein Sprint in der Board-Übersicht — der Burndown über der Ideallinie](../images/sprint-overview.png)

## Bevor Sie beginnen

Öffnen Sie ein Projekt (irgendeines — dem Board ist die Toolchain
gleich). Ist das Projekt ein Git-Repository, kann das Standup auch Ihre
Commits lesen; wenn nicht, erscheint dieser Abschnitt einfach nie.

## Schritte

1. **Das Board öffnen.** `⌥⌘1` (oder `Fenster ▸ Aufgaben-Board`).
   Drücken Sie dreimal **Neue Karte…** und geben Sie jeder Karte einen
   Titel. Karten bewegen Sie per Ziehen oder mit der Tastatur: Bei
   ausgewählter Karte schiebt **⌘←/⌘→** sie eine Spalte weiter und
   **⌘↑/⌘↓** ordnet sie um; **Enter** bearbeitet, **Delete** entfernt
   (nach Rückfrage, mit Nein als Voreinstellung), **N** beginnt eine neue
   Karte in dieser Spalte. Das Menü jeder Spaltenkopfzeile benennt die
   Spalte um, setzt ein **WIP-Limit als Empfehlung** (die Kopfzeile wird
   rot, sobald es überschritten ist — es blockiert nie eine Bewegung),
   mischt sie oder löscht sie.

2. **Einstempeln.** Ziehen Sie eine Karte in die mittlere Spalte und
   wählen Sie im Kontextmenü **Einstempeln**. Auf der Karte erscheint ein
   ⏱, und die Kopfzeile des Boards zeigt die laufende Zeit. Es läuft
   immer nur eine Uhr — Einstempeln auf einer anderen Karte beendet diese
   Sitzung —, und eine Sitzung unter einer Minute wird ganz verworfen,
   damit ein versehentlicher Klick nie als Arbeit zählt. **Ausstempeln**
   hält die Uhr an.

3. **Die Details ergänzen, die ein Standup braucht.** Kontextmenü einer
   Karte → **Label setzen…**, um sie einem Epic zuzuordnen, und auf einer
   anderen Karte **Als blockiert markieren…** — eine verantwortliche
   Person und die Aktion, die die Blockade auflöst (die Aktion ist
   Pflicht: Ein Blocker ohne sie ist eine Klage, kein Plan). Die Karte
   trägt ⛔; **Blockierung aufheben** entfernt das, ebenso das
   Fertigstellen der Karte.

4. **Die Übersicht lesen.** Drücken Sie **Übersicht** in der
   Werkzeugleiste. Dieselbe Datei wird zum Dashboard: Karten auf dem
   Board, **WIP JETZT** (nur die mittleren Spalten), heute und diese
   Woche erledigt, ein WIP-Register je Spalte mit rotem Urteil bei
   Überschreitung, ein **Flussstreifen** über 14 Tage, die ältesten
   unfertigen Karten mit ihrem Alter, die Legende **EPICS**, abgeleitet
   aus den verwendeten Labels, das **Blocker-Register** (am längsten
   Festsitzende zuerst), **RETRO**-Notizen für das ganze Board
   (**Retro bearbeiten…**) und der **ZEIT**-Bericht — heute und in den
   letzten sieben Tagen erfasst, dann eine Zeile je Karte, die meiste
   Zeit heute zuerst. Eine Sitzung über Mitternacht wird je
   Kalendertag aufgeteilt, die Zahl für heute ist also die Arbeit von
   heute.

5. **Etwas fertigstellen.** Schalten Sie **Übersicht** aus und schieben
   Sie eine Karte in die letzte Spalte. Dieser Moment wird als
   Erledigt-Zeitpunkt der Karte gestempelt (schieben Sie sie wieder
   heraus, gilt sie als unfertig, und die Historie vergisst sie). Jede
   Erledigt-Zahl in der Übersicht stammt aus diesen Stempeln.

6. **Einen Sprint starten.** Drücken Sie **Sprint… ▸ Sprint starten…**,
   geben Sie ihm einen Namen und übernehmen Sie das Zwei-Wochen-Fenster
   (Daten im Format `YYYY-MM-DD`; ein rückwärts laufendes Fenster oder
   etwas, das kein Datum ist, wird laut abgelehnt, und nichts ändert
   sich). Wechseln Sie zur **Übersicht**: Sie bekommt eine Sprint-Kopfzeile
   und einen **Burndown**, rekonstruiert aus den Erledigt-Stempeln der
   Karten — die blasse Linie ist das Ideal, die helle Linie das, was
   passiert ist, und die Zukunft bleibt ungezeichnet.

7. **Das Standup schreiben.** Drücken Sie **Standup…**. Der Bericht
   öffnet sich als Markdown mit einem Knopf **In die Zwischenablage
   kopieren**: **Gestern** und **Heute** aus den Erledigt-Stempeln und
   den nach Tagen aufgeteilten Sitzungen (eine laufende Uhr liest sich
   „Uhr läuft“), **Blocker** aus dem Register, **Commits (seit
   gestern)** aus `git log`. Abschnitte ohne Inhalt werden weggelassen,
   nie leer dargestellt, und die Kopfzeile beginnt mit dem Sprint und
   seinem Tageszähler („Sprint 8 · Tag 3 von 14“).

   ![Ein Klick macht aus dem Board den täglichen Bericht](../images/standup.png)

8. **Den Sprint abschließen.** **Sprint… ▸ Sprint-Bericht…** ist das
   Gegenstück des Standups für das Review — erledigt, offen beim
   Abschluss, noch blockiert, erfasste Zeit im Fenster, Retro-Notizen —,
   und **Sprint… ▸ Sprint abschließen…** archiviert Fenster, Anzahl der
   erledigten Karten und Retro für die Velocity. Die Karten bleiben genau,
   wo sie sind: Abschließen ist Buchhaltung, kein Aufräumen. Danach bietet
   der Abschluss den nächsten Sprint vorausgefüllt an (Name hochgezählt,
   gleich langes Fenster ab dem Folgetag), vollständig bearbeitbar, und
   Abbrechen startet nichts. Sobald es eine Historie gibt, zeigt der
   Sprint-Dialog die Planungszahl — „Velocity — letzte 3 Sprints: …“ —,
   und der Bericht bekommt seine Velocity-Zeile.

## Was Sie gerade gelernt haben

- **Eine Datei ist die ganze Aufzeichnung.** Committen Sie
  `.nmoxtasks.json`, und das Team teilt Board, Retro und
  Sprint-Historie; ignorieren Sie sie, und sie bleibt persönlich.
  Kartentitel werden immer als schlichte Zeichen dargestellt, ein
  eingechecktes Board kann also kein Markup einschmuggeln.
- **Das Board folgt der Datei in beide Richtungen.** Bearbeiten Sie sie
  von Hand, ziehen Sie den Push einer Kollegin oder checken Sie einen
  anderen Branch aus, und das sichtbare Board aktualisiert sich binnen
  etwa anderthalb Sekunden — eine Änderung von außen gewinnt gegen eine
  veraltete Geste, und die Statuszeile sagt das.
- **Merge-Schäden heilen beim Laden.** Doppelte Karten-ids, verwaiste
  offene Uhrsitzungen und ein verstümmeltes Sprint-Fenster werden beim
  Lesen der Datei repariert, ein Merge, der beide Seiten behält, kann
  also keinen Bericht aufblähen und keine Zeremonie vergiften.
- **Alles Abgeleitete ist ausdrücklich definiert.** WIP, die
  Erledigt-Fenster, der Burndown und die Tagesaufteilung der ZEIT sind
  Definitionen, die Sie im Benutzerhandbuch nachlesen können, keine
  Heuristiken.

## Weiter

- Kartentitel, Epic-Labels und die wörtliche Suche `blocked` sind alle
  über `⌘I` erreichbar — siehe das [Tutorial zum
  Arbeitsplatz](workbench.de.md) für die Gewohnheit, alles zu
  durchsuchen.
- Fügen Sie das Standup in einen Chat ein und machen Sie weiter: [Vor
  Publikum zeigen](show-it-to-a-room.de.md) behandelt Als Markdown
  kopieren und die Screenshot-Familie.
- Die vollständigen Definitionen stehen im [Abschnitt zum
  Aufgaben-Board im Benutzerhandbuch](../user-guide.de.md).
