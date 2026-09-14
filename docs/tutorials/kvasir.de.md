# Tutorial: KVASIR — erklärt Fehler mit KI

<!-- languages -->
[English](kvasir.md) · [Español](kvasir.es.md) · [Français](kvasir.fr.md) · **Deutsch** · [Русский](kvasir.ru.md) · [Українська](kvasir.uk.md) · [Polski](kvasir.pl.md) · [Português (Brasil)](kvasir.pt.md) · [Bahasa Indonesia](kvasir.id.md) · [Filipino](kvasir.tl.md) · [Tiếng Việt](kvasir.vi.md) · [简体中文](kvasir.zh.md) · [हिन्दी](kvasir.hi.md) · [עברית](kvasir.he.md) · [العربية](kvasir.ar.md)
<!-- /languages -->

KVASIR ist ein Rack-Gerät, das Ihren letzten fehlgeschlagenen Lauf liest
und Ihre KI — Claude, ChatGPT oder Gemini — fragt, was schiefging. Es ist
KI-Unterstützung nach Art des Racks: ein Knopf, eine klare
Einwilligungsschranke und ein ehrliches LCD — es werden keine
Projektdateien oder Geheimnisse gesendet, nur der begrenzte Kontext des
Fehlschlags.

![KVASIR erklärt einen echten fehlgeschlagenen Lauf: die eingewilligte Diagnose auf der Frontplatte und die vollständigen Schritte zur Behebung im Betrachter](../images/kvasir-explain.png)

## Bevor Sie beginnen

Sie brauchen einen API-Schlüssel von einem der drei Anbieter, die KVASIR
spricht: Anthropic (Claude), OpenAI (ChatGPT) oder Google (Gemini).
Drücken Sie **KEY…** auf der Frontplatte, um den Anbieter zu wählen und
seinen Schlüssel im Schlüsselbund des Systems abzulegen, oder exportieren
Sie die Umgebungsvariable des Anbieters —
`ANTHROPIC_API_KEY` / `CLAUDE_API_KEY`, `OPENAI_API_KEY` /
`CHATGPT_API_KEY` oder `GEMINI_API_KEY` / `GOOGLE_API_KEY`. Die Wahl des
Anbieters gilt für jedes Gesicht von KVASIR und steht auch unter
Optionen ▸ Rack & Cloud.

## Schritte

1. **Verursachen Sie einen Fehlschlag.** Führen Sie etwas aus, das
   fehlschlägt — einen Build mit Syntaxfehler, einen Test, der eine Ausnahme
   wirft. Der Flugschreiber des Racks hält den Befehl, den Exit-Code und bis
   zu fünf ausgewählte Fehlerzeilen fest.

2. **Setzen Sie KVASIR ein** — aus der Palette (Kategorie Beobachten) —
   und drücken Sie **EXPLAIN**.

3. **Willigen Sie ein (beim ersten Mal).** KVASIR hat seinen eigenen,
   einmaligen Einwilligungsdialog, je Anbieter; er nennt den Anbieter, der
   die Daten erhält, und führt genau auf, was Ihren Rechner verlässt: den
   fehlgeschlagenen Befehl, seinen Exit-Code, ≤5 Fehlerzeilen, den
   Gerätenamen und den Projektnamen — und sonst nichts (kein Quelltext,
   keine Umgebung, keine Geheimnisse). Das Arbeitsbereichs-Vertrauen
   sichert das *Ausführen* von Code ab; dieser nach außen gerichtete
   Datenfluss bekommt seine eigene Schranke.

4. **Lesen Sie das Urteil.** Eine kurze Diagnose erscheint auf dem
   mehrzeiligen LCD; die vollständige Erklärung öffnet sich in einem
   Fenster. Der Regler **MODEL** wählt FAST (Vorgabe) oder DEEP — Haiku /
   Sonnet, GPT-5 mini / GPT-5 oder Gemini Flash / Pro, je nachdem, welchen
   Anbieter Sie gewählt haben.

## Was Sie gerade gelernt haben

- KVASIR kostet beim Start nichts und geht ohne Knopfdruck nie ins Netz —
  sowohl die Schlüssel- als auch die Einwilligungsschranke werden
  durchgesetzt.
- Der Schlüssel reist nur im Auth-Header des Anbieters (`x-api-key`,
  `Authorization: Bearer`, `x-goog-api-key`) — nie in einer URL, einem
  Body oder einem Protokoll.
- Schlüssel wechseln nie den Anbieter, und die Einwilligung gilt je
  Anbieter: Ein Ja für Anthropic ist kein Ja für Google oder OpenAI.
- Das Verhalten im Fehlerfall ist ehrlich: kein Schlüssel, keine
  Einwilligung, nichts zu erklären, offline und Ablehnung zeigen jeweils
  eine klare Meldung auf dem LCD.

## Weiter

- Verkabeln Sie es für den freihändigen Betrieb: Ein Kabel
  `VERITAS FAIL → KVASIR EXPLAIN` erklärt einen fehlgeschlagenen Testlauf
  automatisch (der Kabelweg fragt nie nach und ist auf einen Aufruf je 30 s
  begrenzt); sein OUT speist MONITOR/PHOSPHOR.
