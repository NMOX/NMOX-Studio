# NMOX Studio — Gabay ng gumagamit

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · [Français](user-guide.fr.md) · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · [Bahasa Indonesia](user-guide.id.md) · **Filipino** · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> Bahagyang salin: nasa Filipino ang mga kabanata 1–2. Para sa iba pa, tingnan ang [buong gabay sa Ingles](user-guide.md).

Kung paano gamitin ang produkto. Dinadaanan ng gabay na ito ang mga tampok sa pagkakasunod-sunod na makakaharap mo: pag-install, unang pagbukas, mga proyekto, ang rack, ang mga studio, ang mga wizard at ang mga panangga.

---

<a id="1-install"></a>
## 1. Pag-install

**macOS (inirerekomenda):**
```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

Ang linyang `brew trust` ang minsanang kumpirmasyon ng Homebrew para sa anumang third-party na tap — hindi ka na tatanungin muli sa mga update. Ang app ay ad-hoc na nilagdaan ngunit hindi notarized, kaya tatanggihan ng Gatekeeper ang kopyang naka-quarantine sa unang pagbukas: inaalis mismo ng cask ang quarantine attribute sa isang hakbang na `postflight` at sinasabi iyon sa output ng pag-install. Walang tahimik na nangyayari.

**Lahat ng iba pa:** kumuha ng file mula sa [pinakabagong bersyon](https://github.com/NMOX/NMOX-Studio/releases/latest) — `.dmg` para sa macOS, `-setup.exe` para sa Windows, `.deb` para sa Debian/Ubuntu, karaniwang `.tar.gz` para sa Linux. Lahat ng apat ay may sariling Java runtime; walang kailangang i-install nang maaga. Ang `-portable.zip` lamang ang artefact na gumagamit ng sarili mong Java (kailangan ng Java 21+ sa PATH, o patakbuhin gamit ang `--jdkhome <landas-ng-jdk>`).

> **macOS, unang pagbukas:** ang app ay ad-hoc na nilagdaan ngunit hindi notarized, kaya nagtatanong ang Gatekeeper bago ito patakbuhin. Sa unang pagkakataon, **i-right-click ang app → Buksan** at kumpirmahin, o patakbuhin ang
> `xattr -d com.apple.quarantine "/Applications/NMOX Studio.app"`. Alinman sa dalawa ay panghabambuhay na ang bisa.

### Pag-update

Ini-update ng IDE ang sarili nito: nag-aalok ang **Mga Kasangkapan ▸ Mga Plugin ▸ Mga Update** ng mga modyul mula sa anumang mas bagong bersyon. I-install, i-restart kapag hiniling, tapos na — walang muling pag-download ng buong app. Isang tapat na paalala: ang kasamang Java runtime at ang launcher ay nagbabago lamang sa isang buong installer, kaya para sa malalaking paglipat ng plataporma, tama pa ring mag-install muli mula sa isang file ng bersyon.

<a id="2-first-launch"></a>
## 2. Unang pagbukas

Mula sa terminal, sinisimulan ng `nmoxstudio --open <folder>` ang app kasama ang folder na iyon na bukas bilang proyekto at nakaturo dito ang rack — ang parehong pinto na binubuksan ng “Buksan ang folder…” sa welcome page.

Bumubukas ang IDE kasama ang lahat ng tab ng suite sa tabi ng lugar ng editor: **Maligayang Pagdating → Rack ng Gawain → Studio ng Database → Studio ng Kontrata → Taga-disenyo ng Infrastructure → Studio ng API → Docker Panel** — isang click ang layo ng bawat pangunahing bahagi mula sa unang minuto. Sa kaliwang dock: **Studio ng Proyekto** (puno ng mga file at mga template), ang batayang **Lugar ng Trabaho** at ang **Explorer ng NPM**. Gumagawa ng folder na `~/NMOX` bilang default na workspace; doon nakaturo ang rack hanggang magbukas ka ng proyekto.

![Unang pagbukas — ang welcome page na bukas ang lahat ng tab](images/welcome.png)

Mga shortcut na sulit matutunan sa unang araw (nakalista rin silang lahat sa welcome tab):

| Shortcut | Binubuksan |
|---|---|
| **⌘I** | Mabilisang paghahanap — naaabot ang lahat |
| **⌘9** | Rack ng Gawain |
| **⌥⌘0** | Lugar ng Trabaho |
| **⌥⌘3** | Kliyente ng chat na IRC |
| **⌥⌘4** | Browser (nakapaloob na WebKit, may DevTools) |
| **⌥⌘5** | Studio ng Block |
| **⌥⌘6** | Studio ng Kontrata |
| **⌥⌘7** | Studio ng Database |
| **⌥⌘8** | Studio ng API |
| **⌥⌘9** | Taga-disenyo ng Infrastructure |
| **⌘8** | Docker Panel |
| **⌘7** | Balangkas ng kasalukuyang file |
| **⇧⌘N / ⌥⌘O** | Bagong proyekto… / Buksan ang folder… |
| **⇧⌘E / ⇧⌘L** | Bagong eksperimento… / Bagong espasyo ng pagkatuto… |
