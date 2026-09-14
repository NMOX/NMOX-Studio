# Mga Tutorial ng NMOX Studio

<!-- languages -->
[English](README.md) · [Español](README.es.md) · [Français](README.fr.md) · [Deutsch](README.de.md) · [Русский](README.ru.md) · [Українська](README.uk.md) · [Polski](README.pl.md) · [Português (Brasil)](README.pt.md) · [Bahasa Indonesia](README.id.md) · **Filipino** · [Tiếng Việt](README.vi.md) · [简体中文](README.zh.md) · [हिन्दी](README.hi.md) · [עברית](README.he.md) · [العربية](README.ar.md)
<!-- /languages -->

Maiikling gabay na sinusundan mismo, para sa mga sistemang nagpapaiba sa
NMOX Studio mula sa karaniwang IDE. Isang upuan lang ang bawat isa —
buksan ang window, sundin ang mga hakbang, at tunay mong nagamit ang
feature.

Para sa malawak na sanggunian (pag-install, bawat menu, bawat panangga),
tingnan ang [Gabay ng gumagamit](../user-guide.tl.md). Para sa buong
listahan ng mga device, tingnan ang [devices.md](../devices.md).

## Ang mga sistema

| Tutorial | Kung ano ang gagawin mo | Bumubukas sa |
|----------|----------------|------------|
| [Ang Rack ng Gawain](the-task-rack.tl.md) | Magkabit ng patch na run→monitor at pagmasdan itong umandar | ⌘9 / tab na Rack ng Gawain |
| [Sumulat ng sarili mong device](your-own-device.tl.md) | Magdagdag ng device sa rack gamit ang text editor — walang Java, walang restart | `~/.nmox/devices.d/` |
| [Lugar ng Trabaho](workbench.tl.md) | Gamitin ang tahanan para tumalon sa pagitan ng mga proyekto at kasangkapan | ⌥⌘0 |
| [Studio ng Proyekto](project-studio.tl.md) | Lumikha ng proyekto at patakbuhin ito nang walang terminal | tab na Studio ng Proyekto |
| [Studio ng API](api-studio.tl.md) | Magpadala ng request, suriin ito, basahin ang marka ng seguridad | ⌥⌘8 |
| [Studio ng Database](db-studio.tl.md) | Kumonekta sa SQLite at baguhin ang isang hilera sa grid | ⌥⌘7 |
| [Studio ng Kontrata](contract-studio.tl.md) | I-compile, i-deploy sa lokal na chain, at tawagin ang isang kontrata | ⌥⌘6 (Web3) |
| [Taga-disenyo ng Infrastructure](infra-designer.tl.md) | Gumuhit ng droplet + firewall at subukan ang deploy nang dry run | ⌥⌘9 |
| [Studio ng Block](block-studio.tl.md) | Bumuo ng Web Component mula sa magkakakabit na block | ⌥⌘5 |
| [Pag-edit at pag-debug sa maraming wika](polyglot-editing-and-debugging.tl.md) | Maglagay ng breakpoint sa Node app at huminto roon | buksan ang anumang proyekto |
| [Mula browser tungo sa source](browser-to-source.tl.md) | I-click ang elemento sa pahina, dumapo sa source nito, baguhin ang estilo mula sa DevTools | ⌥⌘4 → DevTools → DOM |
| [Ang Agent Port (MCP)](agent-port.tl.md) | Itutok ang isang AI agent sa buhay na kalagayan ng IDE — read-only ayon sa pagkakagawa | Kasangkapan ▸ Agent Port (MCP)… |
| [Ang Panel ng Docker](docker-panel.tl.md) | Suriin ang mga container at i-dockerize ang isang proyekto | tab na Panel ng Docker |
| [Ang Task Board at mga sprint](task-board.tl.md) | Magpatakbo ng kanban na may time clock, standup sa isang click, at sprint burndown mula sa iisang file sa repo | ⌥⌘1 |
| [Ipakita sa isang silid](show-it-to-a-room.tl.md) | Magpresenta, magbahagi, at kumuha ng screenshot mula sa loob ng IDE — mula Mode ng presentasyon hanggang Kopyahin ang Project Tree bilang Markdown | Tingnan ▸ Mode ng presentasyon |
| [KVASIR](kvasir.tl.md) | Tanungin ang AI kung bakit nabigo ang isang pagtakbo | Rack → KVASIR |
| [Ipaliwanag ang anuman](explain-anything.tl.md) | Gamitin ang apat na mukha ng KVASIR: mga pagtakbo, code, API response, DB error | kahit saan may nabigo |
| [Paglipat mula sa Postman](migrating-from-postman.tl.md) | I-import ang iyong mga collection, HAR capture, at iba pa — sa keychain ang mga lihim | ⌥⌘8 → Mag-import… |
| [Image Kit (Web)](image-kit.tl.md) | Pigain ang mga larawan ng proyekto: mas maliit na JPEG, WebP sa tabi, tapat na ulat | Talaksan ▸ Idagdag sa Proyekto ▸ Image Kit (Web)… |
| [Mga Lugar ng Pag-aaral](learning-spaces.tl.md) | Magbukas ng ginagabayang sandbox na may buhay na REPL | Bagong Lugar ng Pag-aaral… |
| [Mga Wizard at Kit](wizards-and-kits.tl.md) | Magdagdag ng PWA, mga file ng pamantayan, o scaffold ng klasikong web | Talaksan ▸ Idagdag sa Proyekto |

> **Tungkol sa mga shortcut.** Nasa pamilyang `⌥⌘` (Option-Command) ang
> mga studio sa macOS — `⌥⌘6`–`⌥⌘9`, `⌥⌘5`, `⌥⌘0` — dahil inaangkin ng
> plataporma ang payak na `⇧⌘`. Sa Linux/Windows, `Alt+` ang modifier;
> laging gumagana ang mga menu (Bintana ▸ …) anuman ang platform.

Sa unang pagbukas, tatlong tab ang makikita — Maligayang Pagdating, Rack
ng Gawain at Browser — at nakadock sa tabi ng mga ito ang Studio ng
Proyekto, ang Lugar ng Trabaho at ang Explorer ng NPM. Isang shortcut
lang ang layo ng bawat ibang window, at nakalista ang mga ito sa hanay na
MGA KASANGKAPAN ng Maligayang Pagdating.
