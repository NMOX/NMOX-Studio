# Tutorial: Das Docker-Panel

<!-- languages -->
[English](docker-panel.md) · [Español](docker-panel.es.md) · [Français](docker-panel.fr.md) · **Deutsch** · [Русский](docker-panel.ru.md) · [Українська](docker-panel.uk.md) · [Polski](docker-panel.pl.md) · [Português (Brasil)](docker-panel.pt.md) · [Bahasa Indonesia](docker-panel.id.md) · [Filipino](docker-panel.tl.md) · [Tiếng Việt](docker-panel.vi.md) · [简体中文](docker-panel.zh.md) · [हिन्दी](docker-panel.hi.md) · [עברית](docker-panel.he.md) · [العربية](docker-panel.ar.md)
<!-- /languages -->

Das Docker-Panel ist eine Schaltzentrale für Ihre lokale Docker-Engine —
Container, Images, Volumes, Netzwerke — und dazu der Tab **Dockerize**, der
ein produktionstaugliches Dockerfile für Ihr Projekt erzeugt. Sein
Gegenstück im Rack ist das Gerät **HARBOR**.

![Engine läuft, ein postgres-Container ist aktiv — Statuspunkt, Ports und die Reihe der Aktionen: starten, stoppen, Protokolle, untersuchen](../images/docker-panel.png)

## Bevor Sie beginnen

Docker muss lokal laufen (`docker version` sollte gelingen;
`Extras ▸ Environment Doctor…` bestätigt es).

## Schritte

1. **Öffnen Sie das Panel.** Drücken Sie `⌘8`, oder klicken Sie
   **Docker-Panel** in der Spalte WERKZEUGE der Willkommensseite. Die
   Übersicht **Engine** zeigt, ob der Daemon läuft.

2. **Untersuchen Sie Container.** Der Tab **Container** listet, was läuft —
   Namen, Images, Ports, Status. **Images**, **Volumes** und **Netzwerke**
   haben jeweils ihren eigenen Tab.

3. **Dockerisieren Sie ein Projekt.** Öffnen Sie den Tab **Dockerize**,
   während ein Projekt ausgerichtet ist. Er erzeugt ein produktionstaugliches
   `Dockerfile`, eine `.dockerignore` und eine `compose`-Datei, passend zu
   Ihrer Toolchain (Node mehrstufig, PHP `php-fpm` mit nginx als Sidecar
   usw.) — und überschreibt nie vorhandene Dateien (existiert eine, schreibt
   er eine `.suggested`-Datei daneben).

4. **Lassen Sie sich eine Datenbankverbindung anbieten.** Läuft ein
   Datenbank-Container, bietet das Datenbank-Studio automatisch eine
   Verbindung dafür an — erschlossen erst aus dem Image-Namen, dann aus dem
   Port, einmal je Container.

## Was Sie gerade gelernt haben

- Das Panel ist ein echter asynchroner Aufsatz auf die `docker`-CLI; ein
  hängender Daemon wird gemeldet, statt die IDE aufzuhalten.
- Dockerize kennt die Toolchain und ist idempotent.

## Weiter

- Setzen Sie **HARBOR** ins Rack, um PANEL/PRUNE/REFRESH von einer
  Frontplatte aus zu bedienen.
- Verbinden Sie sich im [Datenbank-Studio](db-studio.de.md) mit einer
  Datenbank im Container.
