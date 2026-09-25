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
| Idagdag sa pinili ang susunod na paglitaw | ⌘D | **⌘D** o ⌘J | Ctrl+D | **Ctrl+D** o Ctrl+J |
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
nmox src/app.ts:42  # open it at line 42 (code -g's form; -g itself is accepted)
nmox            # just start the IDE
```

Agad itong bumabalik, at ibinibigay ng pangalawang `nmox` ang folder nito sa IDE na tumatakbo na. Tinatanggap ang column (`src/app.ts:42:7`), at bumubukas ang editor sa simula ng linya; ang pangalang wala roon ay tinatanggihan sa terminal sa halip na may simulan. Tinatanggap ang `-r`, binubuksan ng `-n` sa iisang bintana, at tinatanggihan ayon sa pangalan ang `-a` at `-v`.

Binubuksan ng `-w` (`--wait`) ang isang file at naghihintay hanggang isara mo ang tab nito, at ikinukumpara ng `-d` (`--diff`) ang dalawang file nang magkatabi, kaya maaaring maging editor at difftool ng git ang NMOX Studio, gaya ng `code --wait`:

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
```

Pagkatapos, binubuksan ng `git commit` ang mensahe sa IDE; i-save ito at isara ang tab, at magpapatuloy ang git. Ang pag-quit sa IDE habang bukas pa ang isang file ay ibinabalik din ito, kasama ang anumang na-save. Inilalagay ito sa iyong PATH ng Homebrew, ng Windows installer (*Add "nmox" to PATH*) at ng mga package para sa Linux; para sa install mula sa DMG, ipinapakita ng [gabay ng gumagamit](user-guide.tl.md#2-first-launch) ang link na isang linya lang.

<a id="where-each-vs-code-idea-lives"></a>
## Kung saan nakatira ang bawat ideya ng VS Code

| Sa VS Code | Sa NMOX Studio |
|---|---|
| **Explorer** | **Studio ng Proyekto** (⇧⌘E) — ang puno ng file (i-right-click ang isang file para sa Kopyahin ang Path, Kopyahin ang Relative Path at Ipakita sa Finder), ang mga template, at ang editor ng `package.json` ng proyekto. Ang **Lugar ng Trabaho** (⌥⌘0) ang tahanan: mga bukas na file, mga kamakailang file, mga kamakailang proyekto, at lahat ng tumatakbo. |
| **Command Palette** | **Mabilis na Paghahanap** (⇧⌘P o ⌘I) — mga aksyon, file, kamakailang proyekto, device ng rack, live na server, mga request ng Studio ng API, mga simbolo. Gumagana rin ang sariling mga pangalan ng utos ng VS Code: inililista ng *Format Document*, *Toggle Terminal*, *Git: Commit* o *Open Settings* ang aksyong gumagawa ng parehong bagay dito, sa ilalim ng **Mga utos ng VS Code**, kasama ang sarili nitong pangalan at kombinasyon. |
| **Extensions** | Ini-install at ina-update ng **Kasangkapan ▸ Mga Plugin** ang mga modyul, pati ang sariling mga update ng NMOX. Ang malaking bahagi ng idinadagdag ng isang extension sa VS Code ay isang **device ng rack** dito — at makakasulat ka ng isa bilang JSON file sa `~/.nmox/devices.d` ([mga device file](device-files.md)). |
| **`tasks.json`** | Binabasa ang `.vscode/tasks.json` ng iyong repository: itipa ang pangalan ng isang task sa Mabilis na Paghahanap (⇧⌘P o ⌘I) at pinapatakbo ito ng Enter sa *Patakbuhin ang task: build — make all*, habang nagtatanong muna ang Tiwala sa Workspace sa proyektong hindi mo pa pinagkakatiwalaan; nasa bintana ng Output ang output nito at pinapatigil ito ng ■ ng toolbar. Katabi nito, ang sariling mga script ng proyekto ay pinapatakbo sa paraang pagkakasulat sa kanila: ang Run / Build / Test ng toolbar (F6, F11, ⌃F6), ang **Patakbuhin ang Script** sa isang linya ng scripts sa `package.json`, ang **Explorer ng NPM**, at ang **Rack ng Gawain** (⌘9), kung saan mga device ang mga gawain na pinagkakabit-kabit mo. |
| **`launch.json`** | Binabasa ang `.vscode/launch.json` ng iyong repository: itipa ang pangalan ng isang configuration sa Mabilis na Paghahanap (⇧⌘P o ⌘I) at sinisimulan ng Enter sa *I-debug: Launch Program — ${workspaceFolder}/server.js* ang breakpoint debugger sa programang iyon, habang nagtatanong muna ang Tiwala sa Workspace. Dine-debug ng mga configuration na Node (`node`, `pwa-node`) at Python (`python`, `debugpy`) ang kanilang `program` sa kanilang `cwd`, kasama ang kanilang `args` at `env`; binubuksan ng mga configuration na Chrome (`chrome`, `pwa-chrome`) ang kanilang `url` (o `file`) kasama ang kanilang `webRoot`. Kapag walang `launch.json`, inaalam ng **I-debug ang file** (⇧⌘F5) at ng pindutang pang-debug sa toolbar kung ano ang ilulunsad mula sa proyekto mismo — ang entry ng script na `start`, ang `main`, ang `index.js` — at naglulunsad ng debugger ang device na **INSPECTOR** ng rack bilang isang hakbang sa isang pipeline. |
| **Integrated terminal** | Ang bintanang **Terminal** (⌃\`): nagsisimula ng shell sa folder ng proyekto ang unang pindot, ibinabalik ito ng mga kasunod na pindot. |
| **`settings.json`** | Kasangkapan ▸ Mga Opsyon (sa macOS, NMOX Studio ▸ Settings…). Binabasa rin ang `.vscode/settings.json` ng isang repository: itinatakda ng `editor.tabSize`, `editor.insertSpaces` at `editor.indentSize` ang pag-indent nito habang nagta-type ka, inilalapat ang `files.trimTrailingWhitespace` at `files.insertFinalNewline` (kapag `true`) kapag nag-save ka, at pinapalitan ng isang bloke ng wika gaya ng `"[typescript]"` ang mga iyon para sa wika nito. Kung may `.editorconfig` din ang repository, ang `.editorconfig` ang nananaig saanman parehong may sinasabi ang dalawa. |
| **Problems panel** | **Mga aksyon** (⌘6), o i-click ang bilang na **✕ ⚠** sa status line: ang mga error at babala ng mga language server, at ang mga natuklasan ng lint at type mula sa mga device na PURITY at TYPEGUARD ng rack. Gaya sa VS Code, may mga server na nag-uulat lamang tungkol sa mga file na bukas mo; ang gopls ay nag-uulat tungkol sa buong package. |
| **Outline** | Ang **Navigator** (⌘7). |
| **Source Control** | Ang git chip sa status line (branch at mga pagbabago, isang click papunta sa kasaysayan) at ang menu na **Pangkat**. |
| **Workspace Trust** | Ang parehong ideya, ipinapatupad bago patakbuhin ang anumang pinili ng isang repository: walang pinapatakbo ang pagbubukas ng na-clone na proyekto hangga’t hindi mo ito pinagkakatiwalaan. |
| **Keyboard Shortcuts editor** | Kasangkapan ▸ Mga Opsyon ▸ Mga keyboard shortcut (sa macOS, Settings… ▸ Mga keyboard shortcut) — baguhin ang anumang kombinasyon, o palitan ang buong profile sa Eclipse, Emacs o IntelliJ. |

Sa unang pagbukas mo ng repository na may `.vscode/tasks.json`, `launch.json` o `settings.json`, may abisong nagsasabi kung ano ang natagpuan at kung saan ito naroroon; i-click ito para sa Mabilis na Paghahanap. Minsan lamang ito sinasabi sa bawat proyekto.

<a id="what-is-honestly-different"></a>
## Kung ano ang tapat na naiiba

- **Idinadagdag ng ⌘D ang susunod na paglitaw sa default na keymap, hindi sa bawat profile.** Pinapanatili ng profile na Eclipse ang ⌘D bilang *Delete Line* ng Eclipse, at ng profile na NetBeans 5.5 bilang *Shift Line Left*; doon, ang ⌘J (Ctrl+J) ang parehong kilos.
- **Binubuksan at tinututukan ng ⌃\` ang Terminal; hindi nito ito itinatago.** At habang nasa Terminal ang pokus, sa iyong shell ang mga key, kaya ang pangalawang pindot ay napupunta sa shell sa halip na ibalik ka sa editor.
- **Binabasa ang `launch.json`, at tinatanggihan ang hindi kayang sundin ng debugger.** Isang programa, ang working folder nito, ang `args` nito (listahan ng mga string) at ang `env` nito (mga string na idinadagdag sa minanang environment) ang ipinapasa ng debugger dito, kaya ang configuration na nagtatakda ng `envFile`, `runtimeExecutable`, `runtimeArgs`, `preLaunchTask` o anumang ibang field na hindi pa naituturo rito ay nakalista pero hindi sinisimulan: pinangangalanan ng Enter ang mga field sa status line. Ang pagsisimula ng programa nang wala ang mga iyon ay magde-debug ng ibang bagay kaysa sa sinasabi ng file. Tinatanggihan din sa parehong paraan ang `args` na isinulat bilang iisang string (ibinibigay iyon ng VS Code sa isang shell) at ang halagang `null` sa `env` (na nag-aalis ng isang variable), at ganoon din ang `"request": "attach"`, ang isang entry na `compounds`, ang isang type na walang adapter dito (`go`, `msedge`, `cppdbg` at ang iba pa), ang isang halagang VS Code lamang ang makapagbibigay (`${file}`, `${input:…}`), at ang isang path sa labas ng proyekto. Ang mga field na humuhubog lamang sa ipinapakita ng debugger — `skipFiles`, `outFiles`, `sourceMaps`, `console`, `justMyCode`, `presentation` — ay tinatanggap at hindi inilalapat; napupunta sa bintana ng Output ang output ng programa.
- **Binabasa ang `tasks.json`, at tinatanggihan ang hindi mapapatakbo ayon sa pagkakasulat.** Ang task na gumagamit ng halagang VS Code lamang ang makapagbibigay (`${input:…}`, `${file}`, `${config:…}`, `${command:…}`) o may `dependsOn` sa ibang task ay nakalista pero hindi pinapatakbo: sinasabi ng Enter sa status line kung aling variable o aling task. Ang pagpapatakbo nito nang blangko ang halaga, o nang wala ang task na inaasahan nito, ay magpapatakbo ng ibang bagay kaysa sa sinasabi ng file. Ganoon din ang uri ng task na ibinibigay ng isang extension (`gulp`, `typescript`), at ang working folder sa labas ng proyekto.
- **Tumatakbo ang task na `"type": "shell"` sa shell na gagamitin ng VS Code.** Sa macOS at Linux, iyon ang iyong `$SHELL` na may `-c` (nagsisimula ang zsh, bash o fish sa macOS bilang login shell, `-l`, gaya ng ginagawa ng mga default na profile ng VS Code); sa Windows, PowerShell ito, `pwsh` kapag naka-install. Sinusunod ang `options.shell` sa paraan ng VS Code: pangalanan ang isang `executable` at tatakbo ito nang eksakto sa mga `args` na ibinigay mo, kaya kailangan ng bash ang `"args": ["-c"]`. Sa Windows, PowerShell lamang (mga args na nagtatapos sa `-Command`) at `cmd.exe` (mga args na nagtatapos sa `/c`) ang pinapatakbo; tinatanggihan doon ayon sa pangalan ang anumang ibang shell sa halip na abutan ng command line na hula-hula ang pagka-quote.
- **Walang keymap profile na “VS Code”.** Nakasakay ang mga kombinasyon sa itaas sa default na profile at sa iba pang apat. Isang sadyang eksepsiyon: sa profile na **Eclipse**, nananatiling sariling *Switch to Editor* ng Eclipse ang ⇧⌘E, at sa loob ng editor, pinapanatili ng ⇧⌘P at ⇧⌘X ang kahulugan nila sa Eclipse (katugmang bracket, malalaking titik) — Eclipse ang inaasahan ng taong pumili ng Eclipse.
- **Sa Linux, binubuksan ng Ctrl+\` ang Terminal, hindi ang window switcher.** Nasa Ctrl+Tab ang switcher. Sa desktop na umaagaw ng Ctrl+Tab para sa sarili nito (KDE, halimbawa), ang **Bintana ▸ Mga dokumento…** ang naglilista ng mga bukas na file sa halip.
- **Maaaring bumangga sa AltGr ang mga kombinasyong Ctrl+Alt.** Sa Windows, nagpapadala ng Ctrl+Alt para sa AltGr ang mga layout ng keyboard na nagta-type ng mga karakter gamit ang AltGr (Polish, halimbawa). Kung nagta-type ng karakter para sa iyo ang Ctrl+Alt+P o Ctrl+Alt+K, ilipat ang *Lumipat ng Proyekto* o ang mga kombinasyon ng eksperimento sa ilalim ng Mga keyboard shortcut.
- **Hindi naiinstall dito ang mga extension ng VS Code.** Nanggagaling ang talino sa wika sa mga language server na kilala ng NMOX (inililista ng Doktor ng Environment ang kulang at kung paano ito i-install), sa sariling mga grammar ng editor, at sa mga plugin na ginawa para sa NetBeans Platform.
