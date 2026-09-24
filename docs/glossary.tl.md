# Talahuluganan

<!-- languages -->
[English](glossary.md) · [Español](glossary.es.md) · [Français](glossary.fr.md) · [Deutsch](glossary.de.md) · [Русский](glossary.ru.md) · [Українська](glossary.uk.md) · [Polski](glossary.pl.md) · [Português (Brasil)](glossary.pt.md) · [Bahasa Indonesia](glossary.id.md) · **Filipino** · [Tiếng Việt](glossary.vi.md) · [简体中文](glossary.zh.md) · [हिन्दी](glossary.hi.md) · [עברית](glossary.he.md) · [العربية](glossary.ar.md)
<!-- /languages -->

Ang mga salitang ginagamit ng NMOX Studio na hindi ginagamit ng ibang IDE, pati ang mga termino ng NetBeans na lumilitaw pa rin. Sinasabi ng bawat entry kung ano ang ibig sabihin ng salita rito at kung saan ka pa makakabasa tungkol dito.

<a id="the-rack"></a>
## Ang rack

**Rack ng Gawain** (Task Rack, ⌘9) — Ang bintana kung saan tumatakbo ang iyong mga tool. Ang bawat gawain (install, build, test, serve, lint, deploy) ay isang *device* na nakakabit sa isang rack, gaya ng hardware sa isang studio. [Gabay ng gumagamit §4](user-guide.tl.md#4-the-task-rack).

**Device** — Isang tool sa rack, halimbawa ang VELOCITY (Vite), VERITAS (mga test) o PURITY (lint). May harapang panel ang device (ang *faceplate*) na may mga knob, pindutan, ilaw at maliit na display, at likurang panel na may mga *jack*. May 53 built-in na device, nakalista sa [sanggunian ng mga device](devices.md). Makakapagdagdag ka ng sarili mo bilang JSON file sa `~/.nmox/devices.d/` ([mga device file](device-files.md)).

**Faceplate** — Ang harapang panel ng isang device. Pindutin ang **Tab** sa rack para ibaliktad ito at makita ang likurang panel.

**Jack** — Isang saksakan sa likurang panel ng device. Nagpapadala ng signal ang mga output jack at tumatanggap ang mga input jack. May tatlong uri ng signal:
- Ang **trigger** ay isang pulso: “tapos na ang build”, “OK”, “FAIL”.
- Ang **gate** ay nananatiling bukas o sarado: “gising ang server”.
- Ang **data** ay nagdadala ng teksto, gaya ng URL o isang linya ng output.

**Cable** — Isang koneksyon mula sa output jack papunta sa input jack. Ikabit ang OK jack ng isang build sa RUN jack ng test runner, at tatakbo ang mga test tuwing pumapasa ang build. Para ikabit ang dalawang jack, i-drag mula sa isa papunta sa isa pa, o i-click ang isa at pagkatapos ang isa pa.

**Patch** — Isang buong rack: ang mga device nito, ang mga setting nila at ang mga cable nila. Naka-save sa tabi ng proyekto bilang `.nmoxrack.json`, kaya sulit itong i-commit.

**Preset** — Isang handang patch na mailo-load mo mula sa menu na **Mga Preset ▾** ng rack, halimbawa ang *Ship Gate* o ang *E2E Loop*. I-save ang anumang patch sa `~/.nmox/presets.d/` at lalabas din ito sa menu.

**Panimulang rack** (starter rack) — Ang patch na nakukuha ng isang proyekto sa unang beses na buksan mo ito, pinili ayon sa uri ng proyekto: Vite console para sa Vite app, mga lane para sa pagpapatakbo, pag-debug at pagsubok para sa Cargo crate, at iba pa.

**Lane** — Dalawang kahulugan, parehong tungkol sa pagpapatakbo:
- Isang **pipeline**: isang tanikala ng mga device na pinagdugtong ng mga cable, gaya ng install → build → test. Maraming lane ang puwedeng tumakbo nang magkatabi, at hinihintay ng QUORUM na matapos silang lahat.
- Ang **AUTO lane** ng isang device: ang command na pinipili nito para sa proyektong ito. Sa AUTO, pinapatakbo ng test device ang `npm test` sa proyektong Node at ang `cargo test` sa proyektong Rust.

**Ibahagi… / I-import…** (Share… / Import…) — I-save ang isang rack sa file para sa ibang tao, o i-load ang ipinadala nila. Bago may maikabit, ipinapakita ng I-import… ang lahat ng laman ng file, at dumarating na nakapatay ang bawat device.

**Gallery ng Rack** (Rack Gallery) — Inililista ng **Kasangkapan ▸ Gallery ng Rack…** ang mga rack ng komunidad, mga preset, mga panimulang rack at ang sarili mong mga naka-save na rack. Ipinapakita ng bawat entry kung para saan ito at kung aling mga tool ang kailangan nito. [Mga rack ng komunidad](racks.md).

<a id="projects-and-running"></a>
## Mga proyekto at pagpapatakbo

**Pagtutok** (aim) / **tinutukang proyekto** — Ang proyektong kasalukuyang pinagtatrabahuhan ng IDE. Kapag nagbukas ka ng proyekto, itinututok ang IDE doon: sinusundan ng rack, ng mga studio, ng status line at ng Patakbuhin ang tinutukang proyekto. Kapag itinutok sa iba, lumilipat silang lahat, at ang anumang tumatakbo pa ay inihihinto muna, pagkatapos magtanong.

**Tiwala sa Workspace** (Workspace Trust) — Ang tanong ng NMOX Studio bago nito unang patakbuhin ang sariling code ng isang proyekto (mga script, build, test). Kung sasagot ka ng **Panatilihing Ligtas**, walang tatakbo mula sa proyekto. Naaalala ang iyong sagot kada folder.

**▶ at ■** — Patakbuhin at Ihinto sa toolbar. Pinapatakbo ng ▶ (F6) ang tinutukang proyekto. Inihihinto ng ■ (⌥⌘.) ang bawat command na sinimulan ng NMOX Studio para sa iyo.

**Tandang ⇄** / **naghahain** (serving) — Kapag nagpalimbag ng lokal na address ang isang bagay na pinapatakbo mo, gaya ng `http://localhost:5173/`, lilitaw ang address sa status line pagkatapos ng simbolong ⇄. Ang tumatakbong server na iyon ay isang *serving*. I-click ang address para buksan ito sa Browser. Inililista ng Mabilis na Paghahanap ang mga serving sa ilalim ng *Mga Live na Server*.

**Eksperimento** (experiment) — Isang pansamantalang proyekto na gawa mula sa isang template sa `~/.nmox/experiments`. Naka-install na ang mga dependency nito at pinagkakatiwalaan na ito. **I-promote…** ito para itago, o **Itapon…** ito. **Talaksan ▸ Bagong Eksperimento…**.

**Lugar ng pag-aaral** (learning space) — Isang may-gabay na tutorial para sa isang wika o framework. Gumagawa ito ng tunay na proyekto, isang gabay na lakad at isang rack na may buhay na REPL, at sinusuri ng **Talaksan ▸ Suriin ang aking gawa** ang iyong mga ehersisyo. May 93. **Talaksan ▸ Bagong Lugar ng Pag-aaral…**.

**PREFLIGHT** — Ang device na pansuri bago magpadala. Pinapatakbo nito ang mga pagsusuring itinakda ng iyong proyekto (lint, types, test, build) bilang iisang pasado o bagsak.

**Unang mga Hakbang** (First Steps) — Ang checklist sa tab na Maligayang Pagdating. Kusang natsetsek ang mga hakbang habang ginagawa mo ang mga ito at hindi na natatanggal ang tsek.

<a id="the-windows"></a>
## Ang mga bintana

**Studio** — Isang bintanang may sariling tool para sa isang uri ng gawain. Lima ang mga ito: **Studio ng API** (⌥⌘8), **Studio ng Database** (⌥⌘7), **Studio ng Kontrata** (⌥⌘6, mga smart contract), **Studio ng Block** (⌥⌘5, mga web component na binuo mula sa mga block) at ang **Taga-disenyo ng Infrastructure** (⌥⌘9, cloud infrastructure). Sine-save ng bawat isa ang gawa nito sa tabi ng proyekto sa isang file na `.nmox*.json`. Kapareho ng pangalan ang **Studio ng Proyekto** pero ito ang puno ng file at ang mga template ng proyekto.

**Lugar ng Trabaho** (Workbench, ⌥⌘0) — Ang tahanan: kung ano ang tumatakbo, kung ano ang bukas, at ang iyong mga kamakailang proyekto at file.

**Task Board** (⌥⌘1) — Isang kanban board para sa bawat proyekto, may mga sprint at orasan, naka-save bilang `.nmoxtasks.json`.

**Maligayang Pagdating** (Welcome) — Ang panimulang tab: mga aksyon para magsimula, mga kamakailang proyekto, ang hanay na *MGA KASANGKAPAN* na naglilista ng bawat bintana, at ang Unang mga Hakbang.

<a id="ai"></a>
## AI

**KVASIR** — Ang pangalan ng mga tampok na AI ng NMOX Studio: Ask, Edit, Complete, Explain, Draft Commit Message. Gumagana ito sa Claude, ChatGPT o Gemini gamit ang sarili mong API key, na nakatago sa keychain ng OS. Minsang humihingi ng pahintulot mo ang bawat tampok at pinapangalanan nang eksakto kung ano ang ipapadala nito. Walang ipinapadala hangga’t hindi mo ginagamit ang isang tampok. ORACLE ang tawag dito sa mga naunang release.

**Agent Port** — Binibigyan ng **Kasangkapan ▸ Agent Port (MCP)…** ang isang AI agent na tumatakbo sa iyong makina, gaya ng coding assistant, ng read-only na access sa kalagayan ng IDE sa pamamagitan ng MCP: mga bukas na file, diagnostics, mga pagtakbo, mga simbolo. Read-only ito ayon sa disenyo at nakikinig lamang sa sarili mong makina. [Tutorial](tutorials/agent-port.md).

<a id="netbeans-terms-you-may-see"></a>
## Mga termino ng NetBeans na maaari mong makita

Binuo ang NMOX Studio sa NetBeans Platform, at lumilitaw pa rin ang ilan sa mga salita nito.

**Modyul** (module) / **NBM** — Isang bahagi ng application. Ang *NBM* ay ang file na pinaghahatiran ng isang modyul. Ini-install ng **Kasangkapan ▸ Mga Plugin** ang mga update nang modyul kada modyul.

**Update center** — Kung saan kinukuha ng **Kasangkapan ▸ Mga Plugin ▸ Mga Update** ang mga bagong bersyon ng mga modyul ng NMOX Studio. Binabasa nito ang isang katalogong inilalathala kasama ng bawat release sa GitHub.

**userdir** — Ang folder kung saan itinatago ng NMOX Studio ang mga setting nito, ang ayos ng mga bintana, ang mga log at ang mga naka-install na update. Para mahanap ito, buksan ang **Tulong ▸ About** (sa macOS, nasa menu na NMOX Studio ito). Nasa `var/log/messages.log` ang log nito. Para magsimula sa bagong mga setting, ilunsad gamit ang `--userdir <an empty folder>`.

**Mga Opsyon** / **Settings…** (Options) — Ang dialog ng mga kagustuhan. **Kasangkapan ▸ Mga Opsyon** ito sa Windows at Linux, at **NMOX Studio ▸ Settings…** sa macOS.

**Action Items** — Ang bintanang naglilista ng mga problemang natagpuan sa proyekto, kasama ang mga resulta ng lint at type-check ng rack. I-click ang isang problema para pumunta sa linyang iyon.
