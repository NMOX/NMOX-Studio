# Tutorial: Alles erklären lassen mit KVASIR

<!-- languages -->
[English](explain-anything.md) · [Español](explain-anything.es.md) · [Français](explain-anything.fr.md) · **Deutsch** · [Русский](explain-anything.ru.md) · [Українська](explain-anything.uk.md) · [Polski](explain-anything.pl.md) · [Português (Brasil)](explain-anything.pt.md) · [Bahasa Indonesia](explain-anything.id.md) · [Filipino](explain-anything.tl.md) · [Tiếng Việt](explain-anything.vi.md) · [简体中文](explain-anything.zh.md) · [हिन्दी](explain-anything.hi.md) · [עברית](explain-anything.he.md) · [العربية](explain-anything.ar.md)
<!-- /languages -->

KVASIR begann als Rack-Gerät, das fehlgeschlagene Läufe erklärt. Heute
erreicht es vier Orte — das Rack, den Editor, das API-Studio und das
Datenbank-Studio —, und jedes Gesicht folgt denselben drei Gesetzen:
**Sie sehen genau, was Ihren Rechner verlassen würde, bevor irgendetwas
geht**, **jede Oberfläche braucht ihre eigene Einwilligung** (ein Ja zu
Build-Fehlern erlaubt nie das Senden von Code oder SQL), und **Geheimnisse
können von Bauart nicht mitreisen** (die Offenlegung stellt das Studio
zusammen, dem die Daten gehören, mit entfernten Headern für Zugangsdaten
und Kennwörtern, die gar nicht erst in Reichweite sind).

![KVASIR erklärt einen echten fehlgeschlagenen Lauf](../images/kvasir-explain.png)

## Bevor Sie beginnen

Ein Schlüssel deckt alle vier Gesichter ab — von dem Anbieter, den Sie
wählen: Claude (Anthropic), ChatGPT (OpenAI) oder Gemini (Google). Drücken
Sie **KEY…** auf der Frontplatte von KVASIR, um den Anbieter zu wählen und
seinen Schlüssel im Schlüsselbund Ihres Systems abzulegen, oder
exportieren Sie `ANTHROPIC_API_KEY`, `OPENAI_API_KEY` oder
`GEMINI_API_KEY`. Kein Schlüssel, kein Aufruf — jedes Gesicht sagt das
ehrlich.

## Die vier Gesichter

1. **Ein fehlgeschlagener Lauf (das Rack).** Setzen Sie KVASIR ein, führen
   Sie etwas aus, das fehlschlägt, und drücken Sie **EXPLAIN**. Gesendet
   werden: der Befehl, der Exit-Code und bis zu fünf ausgewählte
   Fehlerzeilen. Den vollständigen Rundgang, samt dem Kabel, das einen
   Fehlschlag von VERITAS freihändig erklärt, finden Sie im
   [KVASIR-Tutorial](kvasir.de.md).

2. **Ihr Code (der Editor).** Markieren Sie Code in beliebiger Sprache →
   Rechtsklick → **KVASIR zur Auswahl fragen…** und tippen Sie eine Frage.
   Gesendet werden: die begrenzte Auswahl, der Dateiname und die Sprache —
   sonst nichts aus Ihrem Projekt. Dieses Gesicht hat seine *eigene*
   Einwilligungsschranke, denn die Einwilligung für Fehlschläge verspricht
   ausdrücklich, dass kein Quelltext den Rechner verlässt.

3. **Eine API-Antwort (API-Studio).** Drücken Sie nach einer Sendung **Mit
   KVASIR erklären…**. Gesendet werden: Methode, URL mit maskierten
   Query-Werten, Status, Header ohne Zugangsdaten (entfernt und gezählt)
   und ein begrenzter Body. Nützlich, sobald ein 401 oder ein seltsamer
   CORS-Header auftaucht.

4. **Ein Datenbankfehler (Datenbank-Studio).** Unter der Fehlermeldung
   einer fehlgeschlagenen Anweisung erscheint ein Knopf **Erklären…**.
   Gesendet werden: das SQL, das Sie ausgeführt haben — *einschließlich
   seiner Literalwerte, und die Einwilligungszeile sagt das*, weil es im
   Fehler meist um ein Literal geht —, dazu die Fehlermeldung und die Art
   der Maschine. Nie die Verbindung, das Kennwort oder Zeilen.

Jedes Gesicht öffnet ein Gesprächsfenster: Fragen Sie nach, und das Modell
sieht den gesamten Verlauf dieses Austauschs (begrenzt auf zehn Runden,
was im Verlauf auch steht). Die Wahl **Fast/Deep** (Haiku/Sonnet) wird
gemerkt und bleibt je Gespräch fest, damit der Verlauf nie darüber lügt,
wer geantwortet hat.

## In zwei Minuten ausprobieren

Das Datenbank-Studio ist das schnellste Gesicht zum Vorführen: Öffnen Sie
⌥⌘7, legen Sie eine SQLite-Verbindung an, führen Sie `SELECT * FROM user;`
gegen eine Datenbank aus, deren Tabelle `users` heißt, und drücken Sie
beim Fehler **Erklären…**. Lesen Sie den Einwilligungsdialog, bevor Sie
zustimmen — er ist das Versprechen des Produkts, in einem Satz.

## Was Sie gerade gelernt haben

- Vier Oberflächen, eine Naht: Jedes Studio stellt seine eigene
  Offenlegung zusammen, und der Einwilligungsdialog zitiert sie wörtlich.
- Eine Ablehnung wird still und vollständig respektiert — kein Fenster,
  kein Aufruf.
- Ein Ergebnis gehört zu dem Arbeitsbereich, der es erzeugt hat: Ein
  Projektwechsel räumt Antworten und Ergebnis-Tabs ab, damit „Erklären“
  nie die Daten eines vorigen Projekts offenlegen kann.
