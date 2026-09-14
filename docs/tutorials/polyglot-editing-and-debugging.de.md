# Tutorial: Mehrsprachig bearbeiten und debuggen

<!-- languages -->
[English](polyglot-editing-and-debugging.md) · [Español](polyglot-editing-and-debugging.es.md) · [Français](polyglot-editing-and-debugging.fr.md) · **Deutsch** · [Русский](polyglot-editing-and-debugging.ru.md) · [Українська](polyglot-editing-and-debugging.uk.md) · [Polski](polyglot-editing-and-debugging.pl.md) · [Português (Brasil)](polyglot-editing-and-debugging.pt.md) · [Bahasa Indonesia](polyglot-editing-and-debugging.id.md) · [Filipino](polyglot-editing-and-debugging.tl.md) · [Tiếng Việt](polyglot-editing-and-debugging.vi.md) · [简体中文](polyglot-editing-and-debugging.zh.md) · [हिन्दी](polyglot-editing-and-debugging.hi.md) · [עברית](polyglot-editing-and-debugging.he.md) · [العربية](polyglot-editing-and-debugging.ar.md)
<!-- /languages -->

NMOX Studio bearbeitet über 70 Sprachen mit echter Syntaxhervorhebung,
einer Gliederung im Navigator und der Intelligenz von Sprachservern — und
es debuggt JavaScript/TypeScript (und den Browser) ab Werk, mit
Haltepunkten, die wirklich anhalten. Dieses Tutorial trifft einen
Haltepunkt in einer Node-App.

![Ein JavaScript-Haltepunkt ist erreicht: die Ausführung angehalten, der Node-Aufrufstapel und live V8-Variablen](../images/debug-javascript.png)

## Bevor Sie beginnen

Öffnen Sie ein kleines Node-Projekt mit einem ausführbaren Skript (oder
legen Sie eines an), etwa eine Express-Route oder ein schlichtes
`node server.js`.

## Schritte

1. **Öffnen Sie eine Quelldatei.** Hervorhebung, Klammerpaare,
   Code-Faltung und das Markieren von Vorkommen sind sofort da. Der
   **Navigator** zeigt die Gliederung der Datei; Sprachserver (installiert
   nach den Hinweisen von `Extras ▸ Environment Doctor…`) bringen
   Vervollständigung und Diagnosen dazu.

2. **Setzen Sie einen Haltepunkt.** Klicken Sie im Editorrand auf eine
   Zeile in Ihrem Handler — ein Haltepunkt-Punkt erscheint.

3. **Debuggen Sie die Datei.** Starten Sie **Datei debuggen (Haltepunkte)**
   (oder „In Chrome debuggen (Haltepunkte)“ für eine HTML/JS-Seite). Eine
   einmalige Abfrage zum Arbeitsbereichs-Vertrauen sichert den Start ab;
   danach startet der mitgelieferte Adapter `js-debug` Ihr Programm.

4. **Treffen Sie den Haltepunkt.** Lösen Sie den Codepfad aus (die Anfrage
   senden, oder das Skript die Zeile erreichen lassen). Die Ausführung
   **hält** an Ihrem Haltepunkt — untersuchen Sie Variablen, gehen Sie den
   Aufrufstapel durch, springen Sie über oder hinein. Beim Debuggen im
   Browser öffnet sich ein Chrome mit Wegwerfprofil auf der URL Ihres
   laufenden Entwicklungsservers, und die Haltepunkte der Seite werden in
   die IDE zurückgeführt.

## Was Sie gerade gelernt haben

- Der Editor behandelt über 70 Sprachen als vollwertig (TextMate-Grammatiken
  + CSL + LSP); auch Konfigurationsdateien (YAML, TOML, Dockerfile, nginx…)
  sind abgedeckt.
- Debuggen von JS/TS ist eingebaut — ein Sitzungs-Multiplexer fasst die
  Kind-Sitzungen von js-debug zusammen, damit der Einzelsitzungs-Debugger
  der Plattform ihn steuern kann.
- Jeder Debug-Start geht durch die Vertrauensabfrage und wird beim Anhalten
  als ganzer Prozessbaum beendet (keine Waisen).

## Weiter

- **Fokussierten Test ausführen** debuggt eine einzelne Testmethode, je
  Sprache.
- Diagnosen von Rack-Werkzeugen (eslint/tsc/phpstan) landen im Fenster
  **Aufgabenliste** der Plattform.
