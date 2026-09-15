# Tutorial: Ang Rack ng Gawain

<!-- languages -->
[English](the-task-rack.md) · [Español](the-task-rack.es.md) · [Français](the-task-rack.fr.md) · [Deutsch](the-task-rack.de.md) · [Русский](the-task-rack.ru.md) · [Українська](the-task-rack.uk.md) · [Polski](the-task-rack.pl.md) · [Português (Brasil)](the-task-rack.pt.md) · [Bahasa Indonesia](the-task-rack.id.md) · **Filipino** · [Tiếng Việt](the-task-rack.vi.md) · [简体中文](the-task-rack.zh.md) · [हिन्दी](the-task-rack.hi.md) · [עברית](the-task-rack.he.md) · [العربية](the-task-rack.ar.md)
<!-- /languages -->

Ang Rack ng Gawain ay ang tatak na ideya ng NMOX Studio: ang iyong mga
kasangkapan sa build/test/serve ay nakalatag bilang rack ng mga hardware
device na ikinakabit sa isa’t isa gamit ang patch cable. Tunay na command
ang pinapatakbo ng device; tunay na signal ang dinadala ng cable. Bumubuo
ang tutorial na ito ng maliit na patch — magpatakbo ng bagay at pagmasdan
ang output nito sa monitor — para maunawaan ang talinghaga.

![Nakatutok ang rack sa tunay na proyekto — mga device na nakalagay at tumatakbo](../images/tl/task-rack.png)

![Ibinabaliktad ng Tab ang rack — ikinakabit ng mga patch cable ang mga device sa likod](../images/tl/rack-rear.png)

## Bago magsimula

Magbukas ng proyekto (anumang Node project ay gumagana;
`Talaksan ▸ Bagong Proyekto…` → “Vanilla Web” kung kailangan). Ang
pagbubukas ng proyekto ay **itinututok** ang rack dito, kaya ang bawat
device ay tumatakbo sa direktoryo ng proyektong iyon.

## Mga hakbang

1. **Buksan ang rack.** I-click ang tab na **Rack ng Gawain** (o pindutin
   ang `⌘9`). May iisang **MONITOR** ang panimulang rack — ang device na
   console na nagpapakita ng output ng command at ng mga linya ng error.

2. **Magdagdag ng runner.** Ihila ang **IGNITION** mula sa **Istante ng device**
   sa kaliwa papunta sa rack. Ang IGNITION ay ang polyglot na device para
   magpatakbo; nakatutok sa Node project, pinapatakbo nito ang
   `npm run dev` (kusang tinutukoy nito ang iyong package manager at
   toolchain).

3. **Ikabit ito sa monitor.** I-click ang **Likod (Tab)** (o pindutin ang Tab)
   para makita ang likuran, saka i-click ang jack na **OUT** ng IGNITION at
   i-click ang jack na **IN** ng MONITOR — ikinokonekta ng patch cable ang
   mga ito. (Gumagana pati ang paghila sa pagitan ng mga jack; mas madali
   ang pag-click kapag malapad ang rack.)

4. **Paandarin.** Bumalik sa harapan at pindutin ang pindutang **IGNITE** ng
   IGNITION. Sinisimulan nito ang proseso; umaagos ang output sa MONITOR,
   at nagsisindi ang mga LED ng status. Kung hindi pa pinagkakatiwalaan
   ang proyekto, may minsanang tanong ng Tiwala sa Workspace muna — iyon
   ang bantay na pumipigil sa isang clone na repo na patakbuhin ang mga
   script nito nang walang iyong pahintulot.

5. **I-save ang patch.** Ang pindutang **I-save ang Patch** ay
   isinusulat ang `.nmoxrack.json` sa tabi ng iyong proyekto. Buksang muli
   ang proyekto sa ibang araw, at bumabalik nang eksakto ang patch — mga
   device, cable, posisyon ng knob.

## Ang iyong natutunan

- **Mga kasangkapang may faceplate ang mga device.** Pumipili ng opsyon
  ang mga knob, nagpapatakbo ang mga pindutang GO, nag-uulat ng kalagayan
  ang mga LED at LCD — at tunay ang bawat kontrol (walang patay na knob;
  ipinapatupad ito ng contract test).
- **Pinag-uugnay ng mga cable ang mga linya.** Ang OUT→IN ay ang
  pinakapayak na kabit; hinahayaan ka ng mga tarangkahan ng kahandaan
  (`ENABLE`), mga hadlang ng pagtatagpo (`QUORUM`), at mga trigger cable
  na bumuo ng buong pipeline na tumutugon sa sarili nito.
- **Naka-imbak ang lahat.** File na maii-commit ang patch; pati, ibinabalik
  ng rack ang tumatakbong session matapos ang crash.

## Susunod

- 53 device ang mayroon — lakarin ang mga ito sa
  [devices.md](../devices.md) o sa Istante ng device (i-right-click ang
  nakalagay na device para sa **Paano gamitin…** nito).
- Hilingin sa [KVASIR](kvasir.tl.md) na ipaliwanag ang nabigong pagtakbo.
- I-export ang patch sa isang workflow ng GitHub Actions: ang
  **I-export ang CI…** ng rack.
