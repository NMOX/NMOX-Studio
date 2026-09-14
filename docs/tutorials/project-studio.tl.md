# Tutorial: Studio ng Proyekto

<!-- languages -->
[English](project-studio.md) · [Español](project-studio.es.md) · [Français](project-studio.fr.md) · [Deutsch](project-studio.de.md) · [Русский](project-studio.ru.md) · [Українська](project-studio.uk.md) · [Polski](project-studio.pl.md) · [Português (Brasil)](project-studio.pt.md) · [Bahasa Indonesia](project-studio.id.md) · **Filipino** · [Tiếng Việt](project-studio.vi.md) · [简体中文](project-studio.zh.md) · [हिन्दी](project-studio.hi.md) · [עברית](project-studio.he.md) · [العربية](project-studio.ar.md)
<!-- /languages -->

Ang Studio ng Proyekto ay kung saan isinisilang at pinamamahalaan ang mga
proyekto: mga template, puno ng file na katutubo sa plataporma, editor ng
package.json, at mga preset ng rack — kasama ang katutubong
**Patakbuhin / Buuin / Subukin / Linisin** ng IDE na gumagana nang hindi
kailanman nagbubukas ng terminal.

![Studio ng Proyekto sa kaliwang dock — ang puno ng file na katutubo sa plataporma at ang toolbar ng proyekto, at nakabukas sa tabi ang Rack ng Gawain](../images/tabs/project-studio.png)

## Buksan ito

Ang tab na **Studio ng Proyekto**, nakadock sa tabi ng Lugar ng Trabaho,
o `Talaksan ▸ Bagong Proyekto…`.

## Mga hakbang

1. **Lumikha ng proyekto.** `Talaksan ▸ Bagong Proyekto…` → pumili ng
   template (Angular, Vue, Vanilla Web, Elixir/Phoenix, PHP LEMP, at iba
   pa). Pumili ng lokasyon (default ay `~/NMOX`) at tapusin. Bumubukas ang
   proyekto at nakatutok dito ang rack.

2. **Lakarin ang puno.** Ang puno ng file ay tunay na puno ng plataporma —
   wastong icon ayon sa uri ng file, anotasyong git na `[branch]` sa ugat,
   at ang buong menu para buksan, gupitin, kopyahin, tanggalin, palitan
   ang pangalan, mga kasangkapan, at mga katangian. Walang anak na
   ipinapakita ang mga bigat na folder (`node_modules`, `.git`, `dist`),
   kaya nananatiling mabilis ang malaking repo.

3. **Patakbuhin ito — walang terminal.** Gamitin ang **Patakbuhin** ng IDE
   (o pindutin ang IGNITE ng IGNITION sa rack). Tinutukoy nito ang iyong
   package manager mula sa lockfile o corepack pin ng proyekto, at
   pinapatakbo ang wastong command; umaagos ang output sa rack. Gumagana
   sa parehong paraan ang **Buuin**, **Subukin**, at **Linisin**.

4. **I-edit ang package.json.** Nagbibigay ang built-in na editor ng
   nakabalangkas na pag-edit ng mga script at dependency.

5. **Mag-load ng preset.** Ikinakabit ng menu na **Mga Preset ▾** sa
   toolbar ng Rack ng Gawain ang handang rack para sa isang daloy ng trabaho — Uptime Watch, Ship Gate,
   Modern Web, Monorepo Lanes, Web3 Bench, at iba pa — para hindi mo
   kailanganing buuin ang patch nang mano-mano.

## Ang iyong natutunan

- Kinikilala ang mga bagong proyekto sa alinman sa 60 pangalan ng manifest
  (package.json, Cargo.toml, go.mod, pom.xml, gleam.toml, …) at apat na
  tinutukoy ayon sa glob (`.csproj`, `.fsproj`, `.sln`, `.nimble`) — pati
  ang site na may script tag at walang manifest ay bumubukas bilang
  proyektong STATIC.
- Ang Patakbuhin/Buuin/Subukin/Linisin at ang rack ay **iisang
  mekanismo**; sa unang pagpapatakbo ng mga ito ng code ng proyekto,
  tatanungin ka ng Tiwala sa Workspace.

## Susunod

- Buksan ang [Rack ng Gawain](the-task-rack.tl.md) para makita kung ano
  ang ikinabit ng preset.
- Subukan ang isang [Lugar ng Pag-aaral](learning-spaces.tl.md) para sa
  ginagabayang sandbox.
