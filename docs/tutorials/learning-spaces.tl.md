# Tutorial: Mga Lugar ng Pag-aaral

<!-- languages -->
[English](learning-spaces.md) · [Español](learning-spaces.es.md) · [Français](learning-spaces.fr.md) · [Deutsch](learning-spaces.de.md) · [Русский](learning-spaces.ru.md) · [Українська](learning-spaces.uk.md) · [Polski](learning-spaces.pl.md) · [Português (Brasil)](learning-spaces.pt.md) · [Bahasa Indonesia](learning-spaces.id.md) · **Filipino** · [Tiếng Việt](learning-spaces.vi.md) · [简体中文](learning-spaces.zh.md) · [हिन्दी](learning-spaces.hi.md) · [עברית](learning-spaces.he.md) · [العربية](learning-spaces.ar.md)
<!-- /languages -->

![Ang pampili ng Bagong Lugar ng Pag-aaral — maghanap sa mga built-in na tutorial, habang sinasabi ng pagsisiyasat mula sa simula kung may kasangkapan ng lugar ang makinang ito](../images/tabs/learning-spaces.png)

Ang Lugar ng Pag-aaral ay isang sandbox na nakatayo mag-isa para
matutunan ang isang wika, framework, o library: lumilikha ang NMOX Studio
ng sample code, tutorial na may hakbang-hakbang na gabay, at rack na
nakakabit na ang **tunay na REPL sa loob ng rack** na tinitipahan mo.
93 ang built-in.

<!-- screenshot: a learning space open — sample code, the tutorial pane, and the REPL device with typed input -->

## Buksan ito

`Talaksan ▸ Bagong Lugar ng Pag-aaral…` (inililista ng launcher ang bawat
built-in na lugar).

## Mga hakbang

1. **Pumili ng lugar.** Pumili ng isa — Python, Rust, Solid, htmx,
   Solidity, Elm, REPL para sa isang systems language, lugar para sa
   E2E/Playwright, at iba pa. Sinusuri muna ng pampili kung available ang
   interpreter o toolchain.

2. **Hayaan itong lumikha.** Nilikha ng NMOX Studio ang lugar sa
   `~/.nmox/learn/<slug>`: isang minimal na sample na gumagana, at
   tutorial na gumagabay sa iyo rito, na tumuturo sa angkop na console o
   device.

3. **Magtipa sa REPL.** May device na **REPL** ang nakakabit na rack, na
   nakatakda ang ENGINE knob sa wika ng lugar (iisang engine para sa bawat wikang may REPL sa
   katalogo, bawat isa may
   nakahandang flag para sapilitang interactive). Magtipa ng expression,
   pindutin ang Enter — umaagos ang output sa screen ng REPL. Walang
   interpreter? Ini-install ito ng pindutang **INSTALL** mula sa rack.

4. **Sundin ang tutorial.** Dumaan sa mga hakbang; tunay at
   napapatakbo ang sample code, at malaya mong baguhin ang lugar.

## Ang iyong natutunan

- Ang Lugar ng Pag-aaral ay buong proyekto + tutorial + nakakabit na
  rack, hindi lang snippet.
- Tunay na interactive na proseso ang REPL, hindi naka-record na playback.
- Maaari kang magdagdag ng sarili mo: maglagay ng `*.json` sa
  `~/.nmox/learn-catalog.d/` at sumasama ito sa pampili (tingnan ang
  [learning-spaces.md](../learning-spaces.md) para sa schema).

## Susunod

- Ang mga lugar ng framework (Astro/SvelteKit/Nuxt/Next) ay tumuturo sa
  kanilang console sa rack (COSMOS/KINETIC/NIMBUS/NEXUS).
