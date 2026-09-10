# NMOX Studio — Gabay ng gumagamit

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · [Français](user-guide.fr.md) · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · [Bahasa Indonesia](user-guide.id.md) · **Filipino** · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> Bahagyang salin: nasa Filipino ang mga kabanata 1–3. Para sa iba pa, tingnan ang [buong gabay sa Ingles](user-guide.md).

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

<a id="3-projects"></a>
## 3. Mga Proyekto

**Pagbubukas:** anumang folder na may isa sa 60 kinikilalang manifest ay bumubukas bilang tunay na proyekto — `package.json`, `Cargo.toml`, `go.mod`, `pom.xml`, `composer.json`, `foundry.toml`, `bower.json`, `Gruntfile.js`, at mga kauri — kasama na ang mga manifest ng mga contract chain: ang isang Aiken (`aiken.toml`) o Clarinet (`Clarinet.toml`) na repositoryo ay bumubukas na nakakabit na ang tunay nitong mga linya. Bumubukas din ang payak na folder ng HTML na may `<script>` at **walang** manifest, bilang proyektong STATIC: ang klasikong web ay first-class dito, hindi isang mali.

**Paglikha:** nag-aalok ang *Bagong proyekto…* ng tunay na mga balangkas — Angular, Vue, Svelte, payak na JavaScript, Elixir/Phoenix, PHP Web (LEMP), at Klasikong web (jQuery). Bawat isa ay dumarating na nakakabit na ang mga setting para sa lint, format at pagsusulit, at may nakahandang git na repositoryo: iisang commit ng balangkas na, kapag pinatakbo ng wizard ang pag-install para sa iyo, dala rin ang lockfile — kaya malinis ang iyong unang `git status`.

**Ligtas ang paglipat:** kung may tumatakbong mga kagamitan (isang development server, isang tagamasid), nagtatanong ang IDE bago lumipat at malinis itong pinapatay. Walang patuloy na tumatakbo sa likod mo — kailanman. Kahit ang sapilitang pagsasara ng IDE ay hindi makakaiwan ng ulilang proseso.

**Ang mga eksperimento** ang pinakamabilis na paraan para subukan ang isang stack. Ang **File ▸ Bagong eksperimento…** (⇧⌘E) ay pumipili ng template at gumagawa ng pansamantalang proyekto sa `~/.nmox/experiments`: walang git, walang kamakailan, pinagkakatiwalaan na, nakainstall na ang mga dependency — para **gumana agad ang unang Patakbuhin**. Bumubukas ito sa sarili nitong gabay na `EXPERIMENT.md`, na nagsasabi kung ano ang pipindutin, aling file ang babaguhin, at kung saan naroon ang talino ng IDE para sa stack na iyon. Itago ang nagiging kapaki-pakinabang: **File ▸ Mga eksperimento…** ▸ **Itaas** ang naglalabas nito at nag-uumpisa ng git, **Doblehin** ang gumagawa ng kopya sa tabi para sa pangalawang paraan, at **Itapon** ang nag-aalis ng iba. Ipinapakita ng istante ang edad ng bawat isa at ang nasukat nitong laki sa disk. Mas gusto mo ang gabay na landas? Inuuna ng dialog ang 93 espasyo ng pagkatuto.

![Ang istante ng mga espasyo ng pagkatuto — bilang, laki sa disk, edad, at ang buong siklo](images/spaces-shelf.png)

![Isang bagong eksperimentong Express: bukas ang gabay, nakainstall ang mga dependency, naghahain na ang API](images/experiment-walkthrough.png)

**Patakbuhin, buuin, subukin — at itigil:** ang ▶ sa toolbar (F6) ay pinapatakbo ang proyekto gaya ng pagpapatakbo ng sarili nitong mga kasangkapan: isang `start` na script kung mayroon ang package.json, `cargo run`, `go run`, `dotnet run`, at para sa folder ng HTML ay isang maliit na static na server sa unang bakanteng port mula 8080. Katabi nito at nasa menu ng Patakbuhin ang Buuin, Subukin at Linisin. Ang development server na nag-aanunsyo ng address nito ay nagpapailaw sa ⇄ sa status bar at binubuksan ang pahina sa nakapaloob na browser. Ang lahat ay dumadaan muna sa pagtatanong ng tiwala sa workspace. Ang pagtakbong hindi makasimula ay tapat na sinasabi ito at nag-aalok buksan ang Doktor ng kapaligiran. Para huminto: ang ■ sa kanan ng Debug (⌥⌘.) ay pinapatigil ang lahat ng tumatakbong utos nang sabay at sinasabi kung ano ang pinatigil; ang **Patakbuhin ▸ Itigil** ay pinapatigil ang isa at pagkatapos ay nag-aalok ng **Ulitin**. Nakikita ng ■ ang lahat ng sinisimulan ng produkto para sa iyo, pati na ang mga pag-install; kapag inilapit ang cursor, pinangangalanan ng tooltip nang eksakto kung ano ang ititigil ng isang pindot, at kung mula kailan tumatakbo ang bawat isa.

**`.env` saanman:** kung may `.env` ang iyong proyekto, natatanggap ng mga kagamitang inilunsad mula sa rack ang mga variable na iyon. Baguhin ito at tatalâ ang status bar na kukunin ito ng mga muling pagsisimula — tapat na pinapanatili ng tumatakbong proseso ang lumang kapaligiran nito.
