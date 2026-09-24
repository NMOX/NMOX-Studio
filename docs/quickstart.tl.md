# Mabilisang simula: limang minuto hanggang tumakbo ang iyong proyekto

<!-- languages -->
[English](quickstart.md) · [Español](quickstart.es.md) · [Français](quickstart.fr.md) · [Deutsch](quickstart.de.md) · [Русский](quickstart.ru.md) · [Українська](quickstart.uk.md) · [Polski](quickstart.pl.md) · [Português (Brasil)](quickstart.pt.md) · [Bahasa Indonesia](quickstart.id.md) · **Filipino** · [Tiếng Việt](quickstart.vi.md) · [简体中文](quickstart.zh.md) · [हिन्दी](quickstart.hi.md) · [עברית](quickstart.he.md) · [العربية](quickstart.ar.md)
<!-- /languages -->

Pinapatakbo ng pahinang ito ang isa sa sarili mong mga proyekto sa loob ng NMOX Studio. Sinasaklaw lang nito ang kailangan mo para roon. Ang [gabay ng gumagamit](user-guide.tl.md) ang buong manwal. Kung gumagamit ka ng VS Code, basahin ang [galing sa VS Code](coming-from-vscode.tl.md) pagkatapos nito.

<a id="1-install-one-minute"></a>
## 1. Mag-install (isang minuto)

**macOS, gamit ang Homebrew:**

```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

Minsang ipinapatakbo sa iyo ng Homebrew ang `brew trust` para sa anumang third-party na tap. Hindi na ito magtatanong muli kapag nag-update ka.

**macOS, Windows, Linux, nang walang Homebrew:** i-download ang pinakabagong release para sa iyong OS mula sa [pahina ng mga release](https://github.com/NMOX/NMOX-Studio/releases/latest):

| OS | File | Pagkatapos |
|---|---|---|
| macOS | `NMOX-Studio-<version>-macos.dmg` | Hilahin ang app papunta sa Applications. |
| Windows | `NMOX-Studio-<version>-windows-setup.exe` | Patakbuhin ang installer. |
| Debian, Ubuntu | `nmox-studio_<version>_amd64.deb` | `sudo apt install ./nmox-studio_<version>_amd64.deb` |
| Ibang Linux | `NMOX-Studio-<version>-linux.tar.gz` | I-unpack ito at patakbuhin ang `bin/nmoxstudio`. |

May sariling Java runtime ang bawat isa sa mga file na ito, kaya wala ka nang ibang kailangang i-install. Ang portable zip lang ang nangangailangan ng Java 21 o mas bago na nasa makina na.

Sa macOS, notarized ng Apple ang app. Sa unang beses na buksan mo ito, itatanong ng macOS kung bubuksan ang isang app na na-download mula sa internet: i-click ang **Open**.

<a id="2-open-your-project-one-minute"></a>
## 2. Buksan ang iyong proyekto (isang minuto)

Buksan ang **NMOX Studio**. Nagbubukas ito ng tatlong tab: **Maligayang Pagdating**, **Rack ng Gawain** at **Browser**.

Para buksan ang iyong proyekto, piliin ang **Talaksan ▸ Buksan ang Folder…** (⌥⌘O sa macOS, Ctrl+Alt+O sa Windows at Linux) at piliin ang folder nito. Magagawa mo rin ito mula sa terminal, gaya ng gagawin mo gamit ang `code .`:

```bash
cd ~/code/my-app
nmox .
```

Agad bumabalik ang command. Kung tumatakbo na ang NMOX Studio, sa kanya mapupunta ang folder; kung hindi, magsisimula ito. Inilalagay ng Homebrew, ng Windows installer at ng mga package para sa Linux ang `nmox` sa iyong PATH. Para sa install mula sa DMG, tingnan ang [paglalagay ng `nmox` sa iyong PATH](user-guide.tl.md#2-first-launch).

Itinuturing na proyekto ang isang folder kung mayroon itong `package.json`, `Cargo.toml`, `go.mod`, `pom.xml`, `composer.json`, `pyproject.toml` o isa sa 57 pang file ng proyekto. Itinuturing ding proyekto ang isang folder ng payak na mga HTML file.

Tatlong bagay ang nangyayari kapag nagbukas ka ng proyekto:

- Ipinapakita ng **Studio ng Proyekto**, sa kaliwa, ang iyong mga file.
- Ipinapakita ng status line, sa ibaba, ang iyong git branch at ang bilang ng mga binagong file.
- Inihahanda ang **Rack ng Gawain** ayon sa uri ng proyekto. Vite console ang nakukuha ng proyektong Vite, mga lane para sa pagpapatakbo, pag-debug at pagsubok ang nakukuha ng proyektong Cargo, at iba pa.

<a id="3-run-it-one-minute"></a>
## 3. Patakbuhin ito (isang minuto)

Pindutin ang **▶** sa toolbar, o ang F6. Pinapatakbo nito ang iyong proyekto gaya ng pagpapatakbo ng mga tool nito: ang script na `dev`, `start` o `serve` mula sa `package.json`, `cargo run`, `go run`. Ginagamit nito ang sariling package manager ng iyong proyekto: npm, pnpm o yarn, o bun para sa proyektong Bun.

Sa unang beses na magpatakbo ka ng anuman sa isang proyekto, itatanong ng NMOX Studio kung pinagkakatiwalaan mo ang folder. Walang pinapatakbong sariling code ang proyektong hindi mo pa pinagkakatiwalaan: walang script, build o test. I-click ang **Pagkatiwalaan ang Workspace** para sa sarili mong code.

Kung dev server ang iyong proyekto, lilitaw ang address nito sa status line sa tabi ng simbolong **⇄**, at bubukas ang pahina sa tab na **Browser**. Baguhin ang isang file at i-save, at muling maglo-load ang pahina.

Para ihinto ang lahat ng tumatakbo, pindutin ang **■** sa tabi ng ▶, o ang ⌥⌘. (Option, Command at tuldok).

Kung walang nangyari, tingnan ang tab na **Output** sa ibaba. Ipinapaliwanag nito kung bakit hindi makapagsimula ang pagtakbo, halimbawa na hindi naka-install ang isang tool o hindi pa naka-install ang mga dependency, at nag-aalok itong ayusin iyon. Inililista ng **Kasangkapan ▸ Doktor ng Environment…** ang bawat tool na magagamit ng NMOX Studio at ipinapakita kung alin ang naka-install.

<a id="4-find-anything-thirty-seconds"></a>
## 4. Hanapin ang kahit ano (tatlumpung segundo)

Pindutin ang **⌘I** (Ctrl+I sa Windows at Linux) at mag-type. Hinahanap ng **Mabilis na Paghahanap** ang mga file, mga aksyon sa menu, mga simbolo, mga device ng rack, mga tumatakbong server at command, at ang mga script sa iyong `package.json`. Pindutin ang Enter para buksan o patakbuhin ang resulta.

Pindutin ang **⌘P** para magbukas ng file ayon sa pangalan.

<a id="5-test-it-thirty-seconds"></a>
## 5. Subukin ito (tatlumpung segundo)

Pindutin ang **⌃F6** (Ctrl+F6) para patakbuhin ang mga test ng iyong proyekto. Para makita ang bawat test sa proyekto bago mo patakbuhin ang alinman, buksan ang bintanang **Mga Test** gamit ang ⌥⌘2.

<a id="if-you-have-no-project-handy"></a>
## Kung wala kang proyektong handa

- Gumagawa ang **Talaksan ▸ Bagong Proyekto…** ng tunay na proyekto mula sa isang template (Angular, Vue, Svelte, React na may Vite, payak na JavaScript, PHP, Phoenix at iba pa). Nililikha nito ang mga file, inihahanda ang git at ini-install ang mga dependency.
- Nagbubukas ang **Talaksan ▸ Bagong Lugar ng Pag-aaral…** ng isang may-gabay na tutorial. Nauuna sa listahan ang *Ang Iyong Unang Web Page*.

<a id="where-to-go-next"></a>
## Saan susunod

- **[Ang Rack ng Gawain](user-guide.tl.md#4-the-task-rack)**. Isang device sa rack ang bawat tool na pinapatakbo mo, at pinagdudugtong sila ng mga kable sa pagitan ng mga device: halimbawa, patakbuhin ang mga test tuwing pumapasa ang build.
- **[Ang editor](user-guide.tl.md#5-the-editor)**. Kasama ang Emmet, mga patse ng kulay, pag-debug gamit ang breakpoint para sa Node at Chrome, at mga template ng Angular.
- **[Ang mga studio](user-guide.tl.md#6-the-studios)**. Ang Studio ng API, Studio ng Database, Studio ng Kontrata at Studio ng Block, at ang Task Board.
- Ipinapaliwanag ng **[talahuluganan](glossary.tl.md)** ang sariling mga salita ng produkto: rack, patch, jack, lane, pagtutok (aim), KVASIR.
