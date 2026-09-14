# Tutorial: Ang Panel ng Docker

<!-- languages -->
[English](docker-panel.md) · [Español](docker-panel.es.md) · [Français](docker-panel.fr.md) · [Deutsch](docker-panel.de.md) · [Русский](docker-panel.ru.md) · [Українська](docker-panel.uk.md) · [Polski](docker-panel.pl.md) · [Português (Brasil)](docker-panel.pt.md) · [Bahasa Indonesia](docker-panel.id.md) · **Filipino** · [Tiếng Việt](docker-panel.vi.md) · [简体中文](docker-panel.zh.md) · [हिन्दी](docker-panel.hi.md) · [עברית](docker-panel.he.md) · [العربية](docker-panel.ar.md)
<!-- /languages -->

Ang Panel ng Docker ay isang control surface para sa lokal na Docker
engine — mga container, image, volume, network — kasama ang tab na
**Dockerize** na lumilikha ng production Dockerfile para sa iyong
proyekto. Ang katapat nito sa rack ay ang device na **HARBOR**.

![Gumagana ang engine, tumatakbo ang postgres container — tuldok ng status, mga port, at hanay ng mga aksyon: start, stop, logs, inspect](../images/docker-panel.png)

## Bago magsimula

Kailangang tumatakbo ang Docker sa iyong makina (dapat magtagumpay ang
`docker version`; kinukumpirma ito ng `Kasangkapan ▸ Doktor ng Environment…`).

## Mga hakbang

1. **Buksan ang panel.** Pindutin ang `⌘8`, o i-click ang **Panel ng
   Docker** sa hanay na MGA KASANGKAPAN ng Maligayang Pagdating.
   Ipinapakita ng tab na **Engine** kung gumagana ang daemon.

2. **Suriin ang mga container.** Inililista ng tab na **Mga Container**
   ang tumatakbo — mga pangalan, image, port, status. May sarili nitong
   tab ang **Mga Image**, **Mga Volume**, at **Mga Network**.

3. **I-dockerize ang proyekto.** Buksan ang tab na **Dockerize** habang
   nakatutok ang isang proyekto. Lumilikha ito ng production `Dockerfile`,
   `.dockerignore`, at `compose` file na akma sa iyong toolchain (Node
   multi-stage, PHP `php-fpm` + nginx sidecar, at iba pa) — hindi
   kailanman pinapatungan ang mga file na mayroon (nagsusulat ito ng
   `.suggested` sa tabi kapag mayroon na).

4. **Tanggapin ang alok na koneksyon sa DB.** Kung may tumatakbong
   container ng database, kusang nag-aalok ang Studio ng Database ng
   koneksyon para dito — hinuhulaan mula sa pangalan ng image at saka sa
   port, minsan bawat container.

## Ang iyong natutunan

- Ang panel ay tunay na asynchronous na pambalot sa `docker` CLI; ang
  daemon na nabitin ay iniuulat, hindi pinapabitin ang IDE.
- Kilala ng Dockerize ang toolchain at idempotent ito.

## Susunod

- Ilagay ang **HARBOR** sa rack para sa PANEL/PRUNE/REFRESH mula sa
  faceplate.
- Kumonekta sa database na nasa container sa
  [Studio ng Database](db-studio.tl.md).
