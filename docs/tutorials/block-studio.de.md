# Tutorial: Block-Studio

<!-- languages -->
[English](block-studio.md) · [Español](block-studio.es.md) · [Français](block-studio.fr.md) · **Deutsch** · [Русский](block-studio.ru.md) · [Українська](block-studio.uk.md) · [Polski](block-studio.pl.md) · [Português (Brasil)](block-studio.pt.md) · [Bahasa Indonesia](block-studio.id.md) · [Filipino](block-studio.tl.md) · [Tiếng Việt](block-studio.vi.md) · [简体中文](block-studio.zh.md) · [हिन्दी](block-studio.hi.md) · [עברית](block-studio.he.md) · [العربية](block-studio.ar.md)
<!-- /languages -->

Das Block-Studio ist ein Baukasten nach Art von Scratch für **echte** Web
Components. Sie stecken typisierte Blöcke zusammen, und es erzeugt ein
eigenständiges Custom Element (Shadow DOM, State, Listener) — dazu einen
Vorschauserver, damit Sie es laufen sehen. Ein Klick auf einen Block hebt
genau die Zeilen hervor, die er erzeugt hat.

![Block-Studio — die Teilepalette, die Arbeitsfläche mit einer Komponentenwurzel und das erzeugte Custom Element, bei dem ein Klick auf ein Teil seinen Code zeigt](../images/tabs/block-studio.png)

## Öffnen

`⌥⌘5`, oder der Tab **Block-Studio**.

## Schritte

1. **Benennen Sie Ihr Element.** Jedes Custom Element braucht einen Tag
   mit Bindestrich. Legen Sie eine Komponente an und geben Sie ihr einen Tag
   wie `hello-badge`.

2. **Fügen Sie Blöcke aus der Palette hinzu.** Ziehen Sie einen
   **Element**-Block (einen DOM-Knoten) herein und geben Sie ihm Text; fügen
   Sie ein **State**-Feld hinzu; fügen Sie einen **Listener** hinzu, der beim
   Klick eine Klasse umschaltet. Nur erlaubte Schachtelungen gehen durch —
   die Arbeitsfläche zeigt gültige Ablageplätze an und lehnt ungültige ab,
   sogar beim Laden.

3. **Lesen Sie den Code.** Der mittlere Bereich zeigt das erzeugte
   `text/javascript` — ein vollständiges Custom Element. Klicken Sie auf
   einen Block, und die Zeilen, die er erzeugt hat, werden hervorgehoben;
   die Zuordnung ist exakt.

4. **Sehen Sie es live.** Drücken Sie **Vorschau**. Das Block-Studio liefert
   die Komponente aus einem Server im Speicher aus und rendert sie; `⇄` und
   die Schnellsuche zeigen die Live-URL. Komponenten im selben
   Arbeitsbereich können einander sogar verwenden.

5. **Speichern Sie sie.** **Komponente speichern** schreibt
   `src/components/<tag>.js` — atomar und ohne eine von Hand bearbeitete
   Datei zu überschreiben. Der ganze Arbeitsbereich liegt in
   `.nmoxblocks.json`; **Komponente öffnen…** liest eine Datei wieder ein,
   die Sie (oder das Studio) geschrieben haben, solange sie noch im
   Block-Dialekt steht.

## Was Sie gerade gelernt haben

- Das Ergebnis ist ein echtes Custom Element ohne Framework, das Sie
  ausliefern können.
- Die Zuordnung Block↔Code gilt in beide Richtungen: Änderungen im Dialekt
  lassen sich sauber wieder einlesen.
- Ein Arbeitsbereich hält viele Komponenten; ein Wechsel ist eine Grenze
  für das Rückgängigmachen.

## Weiter

- Setzen Sie Komponenten aus Komponenten zusammen — ein Block, der den Tag
  einer Geschwisterkomponente nennt, rendert diese verschachtelt in der
  Vorschau.
