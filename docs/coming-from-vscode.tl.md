# Galing sa VS Code

<!-- languages -->
[English](coming-from-vscode.md) · [Español](coming-from-vscode.es.md) · [Français](coming-from-vscode.fr.md) · [Deutsch](coming-from-vscode.de.md) · [Русский](coming-from-vscode.ru.md) · [Українська](coming-from-vscode.uk.md) · [Polski](coming-from-vscode.pl.md) · [Português (Brasil)](coming-from-vscode.pt.md) · [Bahasa Indonesia](coming-from-vscode.id.md) · **Filipino** · [Tiếng Việt](coming-from-vscode.vi.md) · [简体中文](coming-from-vscode.zh.md) · [हिन्दी](coming-from-vscode.hi.md) · [עברית](coming-from-vscode.he.md) · [العربية](coming-from-vscode.ar.md)
<!-- /languages -->

Alam na ng iyong mga kamay kung nasaan ang mga bagay. Ang pahinang ito ang mapa mula sa mga nakasanayang iyon papunta sa NMOX Studio: una ang mga kombinasyon ng key, pagkatapos kung saan nakatira rito ang bawat ideya ng VS Code, at pagkatapos kung ano ang tapat na naiiba.

Ginagawa ng unang apat na kombinasyong pinipindot ng isang gumagamit ng VS Code ang inaasahan niya: binubuksan ng **⇧⌘P** ang command palette, ng **⇧⌘E** ang puno ng file, ng **⇧⌘X** ang mga plugin, at ng **⌃\`** ang terminal. Nakarehistro ang mga ito sa lahat ng limang keymap profile na kasama ng plataporma, at nireresolba ng isang build gate ang bawat isa sa pamamagitan ng binuong keymap sa macOS, Windows at Linux para walang ibang tumakbo sa lugar nito.

<a id="the-chords"></a>
## Ang mga kombinasyon

Ginagamit ng mga hanay para sa macOS ang mga glyph ng menu bar (⌃ Control, ⌥ Option, ⇧ Shift, ⌘ Command); ang mga hanay para sa Windows at Linux ay ang parehong kombinasyon sa keyboard ng PC.

| Ang gusto mo | VS Code, macOS | NMOX, macOS | VS Code, Win/Linux | NMOX, Win/Linux |
|---|---|---|---|---|
| Command palette | ⇧⌘P | **⇧⌘P** (o ⌘I) — Mabilis na Paghahanap | Ctrl+Shift+P | **Ctrl+Shift+P** (o Ctrl+I) |
| Magbukas ng file ayon sa pangalan | ⌘P | **⌘P** — Pumunta sa file | Ctrl+P | **Ctrl+P** |
| Ang puno ng file | ⇧⌘E | **⇧⌘E** — Studio ng Proyekto | Ctrl+Shift+E | **Ctrl+Shift+E** |
| Mga extension | ⇧⌘X | **⇧⌘X** — Kasangkapan ▸ Mga Plugin | Ctrl+Shift+X | **Ctrl+Shift+X** |
| Ang terminal, sa folder ng proyekto | ⌃\` | **⌃\`** | Ctrl+\` | **Ctrl+\`** |
| Magbukas ng kamakailang proyekto | ⌃R | **⌥⌘P** — Lumipat ng Proyekto… | Ctrl+R | **Ctrl+Alt+P** |
| Pumunta sa isang simbolo sa proyekto | ⌘T | **⌥⇧⌘O** | Ctrl+T | **Ctrl+Alt+Shift+O** |
| Pumunta sa definition | F12 | **⌘B** | F12 | **Ctrl+B** |
| Palitan ang pangalan ng simbolo | F2 | **⌃R** | F2 | **Ctrl+R** |
| Pumunta sa linya | ⌃G | **⌃G** | Ctrl+G | **Ctrl+G** |
| I-toggle ang komento ng linya | ⌘/ | **⌘/** | Ctrl+/ | **Ctrl+/** |
| Ipakita ang mga mungkahi | ⌃Space | **⌃Space** | Ctrl+Space | **Ctrl+Space** |
| Idagdag sa pinili ang susunod na paglitaw | ⌘D | **⌘J** | Ctrl+D | **Ctrl+J** |
| Piliin ang bawat paglitaw | ⇧⌘L | **⌃⇧⌘J** | Ctrl+Shift+L | **Ctrl+Alt+Shift+J** |
| Magdagdag ng cursor sa itaas / sa ibaba | ⌥⌘↑ / ⌥⌘↓ | **⌥⌘↑ / ⌥⌘↓** | Ctrl+Alt+↑ / ↓ | **Alt+Shift+[ / ]** |
| Ilipat pataas / pababa ang linya | ⌥↑ / ⌥↓ | **⌃⇧↑ / ⌃⇧↓** | Alt+↑ / ↓ | **Alt+Shift+↑ / ↓** |
| Kopyahin pababa ang linya | ⇧⌥↓ | **⌥⇧↓** | Shift+Alt+↓ | **Ctrl+Shift+↓** |
| Burahin ang linya | ⇧⌘K | **⌘E** | Ctrl+Shift+K | **Ctrl+E** |
| I-format ang dokumento | ⇧⌥F | **⌃⇧F** | Shift+Alt+F | **Alt+Shift+F** |
| Isara ang tab ng editor | ⌘W | **⌘W** | Ctrl+W | **Ctrl+W** |
| Ang panel ng Problems | ⇧⌘M | **⌘6** — Action Items (dito, nagto-toggle ng bookmark ang ⇧⌘M) | Ctrl+Shift+M | **Ctrl+6** |
| Mag-toggle ng breakpoint | F9 | **⌘F8** | F9 | **Ctrl+F8** |
| Simulan ang pag-debug | F5 | **⇧⌘F5** — I-debug ang file | F5 | **Ctrl+Shift+F5** |
| Patakbuhin nang walang pag-debug | ⌃F5 | **F6** — ang ▶ sa toolbar | Ctrl+F5 | **F6** |
| Mga setting | ⌘, | **⌘,** — NMOX Studio ▸ Settings… | Ctrl+, | Kasangkapan ▸ Mga Opsyon (walang kombinasyon) |

Sa macOS, sariling kombinasyon ng app menu ang ⌘,; ang bawat iba pang kombinasyon ng NMOX sa talahanayan ay binasa mula sa naka-ship na keymap, hindi mula sa alaala. Ilang bagay na hindi masasabi ng talahanayan sa isang cell:

- **Kinukuha ang F5 habang nagde-debug.** Dito, *Continue* ang ibig sabihin nito, gaya sa bawat IDE na kamag-anak ng NetBeans, kaya nagsisimula ang pag-debug sa **⇧⌘F5** (Ctrl+Shift+F5) at nagpapatuloy sa F5.
- **Rename ang ⌃R dito**, kaya nasa ⌥⌘P ang *Lumipat ng Proyekto* sa halip na sa kombinasyong Open Recent ng VS Code. Gumagana ang Rename kung saan sinusuportahan ito ng wikang nasa likod ng file.
- **Ang Ctrl+, sa Windows at Linux** ay bumabalik sa iyong kasaysayan ng pag-edit, gaya ng dati sa NetBeans; nasa ilalim ng Kasangkapan ▸ Mga Opsyon ang mga setting (sa macOS, ang **Settings…** ng app menu, ⌘,).

Inililista ng **Tulong ▸ Mga Keyboard Shortcut…** ang bawat kombinasyon ng NMOX sa iyong aktibong keymap, kasama ang apat na kombinasyon ng VS Code, binasa mula sa tumatakbong keymap kaya hindi ito maaaring lumihis sa ginagawa ng mga key.


<a id="from-the-terminal"></a>
## Mula sa terminal

Ang `code .` ay `nmox .`:

```bash
cd ~/code/my-app
nmox .          # open this folder (manifest or not) and aim the IDE at it
nmox src/app.ts # open one file
nmox            # just start the IDE
```

Agad itong bumabalik, at ibinibigay ng pangalawang `nmox` ang folder nito sa IDE na tumatakbo na. Inilalagay ito sa iyong PATH ng Homebrew, ng Windows installer (*Add "nmox" to PATH*) at ng mga package para sa Linux; para sa install mula sa DMG, ipinapakita ng [gabay ng gumagamit](user-guide.tl.md#2-first-launch) ang link na isang linya lang.

<a id="where-each-vs-code-idea-lives"></a>
## Kung saan nakatira ang bawat ideya ng VS Code

| Sa VS Code | Sa NMOX Studio |
|---|---|
| **Explorer** | **Studio ng Proyekto** (⇧⌘E) — ang puno ng file, ang mga template, at ang editor ng `package.json` ng proyekto. Ang **Lugar ng Trabaho** (⌥⌘0) ang tahanan: mga bukas na file, mga kamakailang file, mga kamakailang proyekto, at lahat ng tumatakbo. |
| **Command Palette** | **Mabilis na Paghahanap** (⇧⌘P o ⌘I) — mga aksyon, file, kamakailang proyekto, device ng rack, live na server, mga request ng Studio ng API, mga simbolo. |
| **Extensions** | Ini-install at ina-update ng **Kasangkapan ▸ Mga Plugin** ang mga modyul, pati ang sariling mga update ng NMOX. Ang malaking bahagi ng idinadagdag ng isang extension sa VS Code ay isang **device ng rack** dito — at makakasulat ka ng isa bilang JSON file sa `~/.nmox/devices.d` ([mga device file](device-files.md)). |
| **`tasks.json`** | Ang sariling mga script ng iyong proyekto, pinapatakbo sa paraang pagkakasulat sa kanila: ang Run / Build / Test ng toolbar (F6, F11, ⌃F6), ang **Patakbuhin ang Script** sa isang linya ng scripts sa `package.json`, ang **Explorer ng NPM**, at ang **Rack ng Gawain** (⌘9), kung saan mga device ang mga gawain na pinagkakabit-kabit mo. |
| **`launch.json`** | Inaalam ng **I-debug ang file** (⇧⌘F5) at ng pindutang pang-debug sa toolbar kung ano ang ilulunsad mula sa proyekto mismo — ang entry ng script na `start`, ang `main`, ang `index.js` — at naglulunsad ng debugger ang device na **INSPECTOR** ng rack bilang isang hakbang sa isang pipeline. |
| **Integrated terminal** | Ang bintanang **Terminal** (⌃\`): nagsisimula ng shell sa folder ng proyekto ang unang pindot, ibinabalik ito ng mga kasunod na pindot. |
| **`settings.json`** | Kasangkapan ▸ Mga Opsyon (sa macOS, NMOX Studio ▸ Settings…). Nalalapat ang `.editorconfig` ng iyong proyekto habang nagta-type ka at kapag nag-save. |
| **Problems panel** | **Mga aksyon** (⌘6): ang mga error at babala ng mga language server, at ang mga natuklasan ng lint at type mula sa mga device na PURITY at TYPEGUARD ng rack. Gaya sa VS Code, may mga server na nag-uulat lamang tungkol sa mga file na bukas mo; ang gopls ay nag-uulat tungkol sa buong package. |
| **Outline** | Ang **Navigator** (⌘7). |
| **Source Control** | Ang git chip sa status line (branch at mga pagbabago, isang click papunta sa kasaysayan) at ang menu na **Pangkat**. |
| **Workspace Trust** | Ang parehong ideya, ipinapatupad bago patakbuhin ang anumang pinili ng isang repository: walang pinapatakbo ang pagbubukas ng na-clone na proyekto hangga’t hindi mo ito pinagkakatiwalaan. |
| **Keyboard Shortcuts editor** | Kasangkapan ▸ Mga Opsyon ▸ Mga keyboard shortcut (sa macOS, Settings… ▸ Mga keyboard shortcut) — baguhin ang anumang kombinasyon, o palitan ang buong profile sa Eclipse, Emacs o IntelliJ. |

<a id="what-is-honestly-different"></a>
## Kung ano ang tapat na naiiba

- **Hindi multi-cursor ang ⌘D dito.** Ang parehong kilos ay **⌘J** (Ctrl+J); walang nakatali sa ⌘D mismo. Itali ito muli sa ilalim ng Mga keyboard shortcut kung ipinipilit ng iyong mga daliri.
- **Binubuksan at tinututukan ng ⌃\` ang Terminal; hindi nito ito itinatago.** At habang nasa Terminal ang pokus, sa iyong shell ang mga key, kaya ang pangalawang pindot ay napupunta sa shell sa halip na ibalik ka sa editor.
- **Hindi binabasa ang `.vscode/tasks.json` at `launch.json`.** Ang task ay isang command na pinili ng repository, at nararapat sa pagbasa nito ang sariling disenyo kaugnay ng Tiwala sa Workspace; hanggang doon, ang sariling mga script ng proyekto at ang mga tuntunin ng debug entry sa itaas ang gumagawa ng trabahong iyon.
- **Walang keymap profile na “VS Code”.** Nakasakay ang mga kombinasyon sa itaas sa default na profile at sa iba pang apat. Isang sadyang eksepsiyon: sa profile na **Eclipse**, nananatiling sariling *Switch to Editor* ng Eclipse ang ⇧⌘E, at sa loob ng editor, pinapanatili ng ⇧⌘P at ⇧⌘X ang kahulugan nila sa Eclipse (katugmang bracket, malalaking titik) — Eclipse ang inaasahan ng taong pumili ng Eclipse.
- **Sa Linux, binubuksan ng Ctrl+\` ang Terminal, hindi ang window switcher.** Naglagay ang plataporma ng pangalawang switcher doon para sa mga desktop (KDE) na umaagaw ng Ctrl+Tab; nasa Ctrl+Tab ang switcher.
- **Maaaring bumangga sa AltGr ang mga kombinasyong Ctrl+Alt.** Sa Windows, nagpapadala ng Ctrl+Alt para sa AltGr ang mga layout ng keyboard na nagta-type ng mga karakter gamit ang AltGr (Polish, halimbawa). Kung nagta-type ng karakter para sa iyo ang Ctrl+Alt+P o Ctrl+Alt+K, ilipat ang *Lumipat ng Proyekto* o ang mga kombinasyon ng eksperimento sa ilalim ng Mga keyboard shortcut.
- **Hindi naiinstall dito ang mga extension ng VS Code.** Nanggagaling ang talino sa wika sa mga language server na kilala ng NMOX (inililista ng Doktor ng Environment ang kulang at kung paano ito i-install), sa sariling mga grammar ng editor, at sa mga plugin na ginawa para sa NetBeans Platform.
