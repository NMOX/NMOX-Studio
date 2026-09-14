# Tutorial: Lernräume

<!-- languages -->
[English](learning-spaces.md) · [Español](learning-spaces.es.md) · [Français](learning-spaces.fr.md) · **Deutsch** · [Русский](learning-spaces.ru.md) · [Українська](learning-spaces.uk.md) · [Polski](learning-spaces.pl.md) · [Português (Brasil)](learning-spaces.pt.md) · [Bahasa Indonesia](learning-spaces.id.md) · [Filipino](learning-spaces.tl.md) · [Tiếng Việt](learning-spaces.vi.md) · [简体中文](learning-spaces.zh.md) · [हिन्दी](learning-spaces.hi.md) · [עברית](learning-spaces.he.md) · [العربية](learning-spaces.ar.md)
<!-- /languages -->

![Die Auswahl „Neuer Lernraum“ — Suche über die mitgelieferten Anleitungen, und die Verfügbarkeitsprüfung sagt Ihnen vorab, ob dieser Rechner das Werkzeug des Raums hat](../images/tabs/learning-spaces.png)

Ein Lernraum ist eine abgeschlossene Sandbox, um eine Sprache, ein
Framework oder eine Bibliothek zu lernen: NMOX Studio erzeugt Beispielcode,
eine begleitete Anleitung und ein vorverdrahtetes Rack mit einer **echten
REPL im Rack**, in die Sie tippen. Mitgeliefert sind 93 Räume.

<!-- screenshot: a learning space open — sample code, the tutorial pane, and the REPL device with typed input -->

## Öffnen

`Datei ▸ Neuer Lernraum…` (die Auswahl listet jeden mitgelieferten Raum).

## Schritte

1. **Wählen Sie einen Raum.** Nehmen Sie einen — Python, Rust, Solid, htmx,
   Solidity, Elm, eine REPL für eine Systemsprache, einen Raum für
   E2E/Playwright und so weiter. Die Auswahl prüft zuerst, ob der
   Interpreter bzw. die Toolchain verfügbar ist.

2. **Lassen Sie ihn erzeugen.** NMOX Studio legt den Raum unter
   `~/.nmox/learn/<slug>` an: ein minimales, lauffähiges Beispiel und eine
   Anleitung, die Sie hindurchführt und auf die passende Konsole oder das
   passende Gerät zeigt.

3. **Tippen Sie in die REPL.** Das vorverdrahtete Rack enthält ein
   **REPL**-Gerät, dessen ENGINE-Regler auf die Sprache des Raums steht
   (eine Engine je REPL-Sprache im Katalog, jede mit voreingestellten Flags
   für den interaktiven Modus).
   Tippen Sie einen Ausdruck, drücken Sie die Eingabetaste — die Ausgabe
   läuft auf den Schirm der REPL. Fehlt der Interpreter? Der Knopf
   **INSTALL** installiert ihn direkt aus dem Rack.

4. **Folgen Sie der Anleitung.** Arbeiten Sie die Schritte durch; der
   Beispielcode ist echt und lauffähig, und der Raum gehört Ihnen zum
   Verändern.

## Was Sie gerade gelernt haben

- Ein Lernraum ist ein vollständiges Projekt mit Anleitung und verdrahtetem
  Rack, nicht bloß ein Schnipsel.
- Die REPL ist ein echter interaktiver Prozess, keine abgespielte
  Aufzeichnung.
- Sie können eigene hinzufügen: Legen Sie eine `*.json` in
  `~/.nmox/learn-catalog.d/`, und sie erscheint in der Auswahl (das Schema
  steht in [learning-spaces.md](../learning-spaces.md)).

## Weiter

- Framework-Räume (Astro/SvelteKit/Nuxt/Next) zeigen auf ihre Konsole im
  Rack (COSMOS/KINETIC/NIMBUS/NEXUS).
