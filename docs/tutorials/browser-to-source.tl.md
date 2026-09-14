# Mula Browser tungo sa Source: pumili, tumalon, baguhin ang estilo

<!-- languages -->
[English](browser-to-source.md) · [Español](browser-to-source.es.md) · [Français](browser-to-source.fr.md) · [Deutsch](browser-to-source.de.md) · [Русский](browser-to-source.ru.md) · [Українська](browser-to-source.uk.md) · [Polski](browser-to-source.pl.md) · [Português (Brasil)](browser-to-source.pt.md) · [Bahasa Indonesia](browser-to-source.id.md) · **Filipino** · [Tiếng Việt](browser-to-source.vi.md) · [简体中文](browser-to-source.zh.md) · [हिन्दी](browser-to-source.hi.md) · [עברית](browser-to-source.he.md) · [العربية](browser-to-source.ar.md)
<!-- /languages -->

*Isang upuan. Magpipindot ka ng elemento sa Browser sa loob ng app, dadapo
sa file na lumikha nito, babaguhin ang estilo nito mula sa DevTools, at
makikita ang pagbabagong dumating sa iyong stylesheet — nang walang
anumang muling pag-type.*

Ang pinakalumang hati sa web development ay ang magkaibang kaalaman ng
browser at ng editor: kilala ng browser *kung aling elemento ang iyong
tinutukoy*, kilala ng editor *kung saan nakatira ang code*, at ikaw ang
nagbubuhat ng impormasyon sa pagitan ng dalawa. Isinasara ng Browser ng
NMOX Studio ang hating iyon. Dinadaanan ng tutorial na ito ang buong loop
sa isang pahinang gagawin mo sa loob ng dalawang minuto.

![Ang DOM pane ng DevTools na may napiling h1 sa pahina: Pumili ng elemento, Buksan ang Source, at I-edit ang Style… sa tabi ng buhay na tree](../images/story-06-devtools-pick.png)

## 1. Gumawa ng pahina

Gumawa ng folder na may dalawang file (gumagana ang Bagong File ng Studio
ng Proyekto, o anumang paraang gusto mo):

`index.html`

```html
<!doctype html>
<html>
<head>
    <title>Loop Demo</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <header class="hero">
        <h1 id="headline">Hello, loop</h1>
        <p class="tagline">watch this paragraph change color</p>
    </header>
</body>
</html>
```

`style.css`

```css
.hero {
    background: #222;
    color: white;
    padding: 2rem;
}

.tagline {
    color: gray;
    font-style: italic;
}
```

## 2. Buksan ito sa Browser

Buksan ang tab na **Browser** (⌥⌘4), i-type ang landas ng file sa URL bar
bilang `file://` na URL — halimbawa
`file:///Users/you/NMOX/loopdemo/index.html` — at pindutin ang Return.

> Ang pahinang inihahain ng isa sa mga naghahaing kagamitan ng rack
> (IGNITION, VELOCITY, HALO, at mga kauri) ay gumagana nang eksaktong
> gayon — kilala ng Browser kung saang proyekto nabibilang ang isang
> buhay na serving. Ang **hindi** gumagana ay isang malayong site:
> nagtitiwala ang loop lamang sa mga pahinang matutunton nito sa mga file
> sa iyong disk, at sasabihin ito sa halip na manghula.

Pindutin ang **DevTools** sa toolbar ng Browser at piliin ang tab na
**DOM**.

## 3. Pumili ng elemento sa pahina

Pindutin ang **Pumili ng elemento**. Nagiging crosshair ang cursor sa
pahina. Ngayon pindutin ang headline sa pahina mismo.

Tatlong bagay ang sabay na nangyayari: nilulunok ang pindot (walang
pag-navigate), pinipili ng DOM tree ang `h1#headline`, at pinapaligiran ng
asul na guhit ang elemento sa pahina. Napupuno ang detail pane ng mga
attribute at computed style nito — kasama ang hatol ng WCAG contrast
kapag kilala ang dalawang kulay.

## 4. Tumalon sa source

Habang nakapili ang elemento, pindutin ang **Buksan ang Source** (gayon din
ang dobleng pindot sa node ng tree). Binubuksan ng editor ang `index.html`
na nasa eksaktong linyang lumikha ng elemento ang caret.

Kung paano nito hinahanap ang linya, at kapag tumatanggi:

- Ang elementong **may id** ay hinahanap sa id na iyon — natatangi ang mga
  id, kaya eksakto ito.
- Ang elementong **walang id** ay hinahanap bilang ika-N na pagkakataon ng
  tag nito ayon sa pagkakasunod sa dokumento, na hindi binibilang ang mga
  comment at ang katawan ng `<script>`/`<style>` (ang `<div>` sa loob ng
  comment o ng JS string ay hindi elemento).
- Ang elementong **umiiral lamang dahil nilikha ito ng script** ay hindi
  sa iyong source kailanman — sinasabi ng status bar ang “malamang na
  script-generated” sa halip na tumalon sa maling lugar.
- Ang pahinang hindi sinusuportahan ng lokal na file — isang malayong
  site, isang hindi kilalang dev server — ay tumatanggi ng “hindi inihahain
  mula sa isang proyekto rito”.

Ang mga pagtanggi ang punto: ang pagtalong maaaring mali ay mas masama
kaysa walang pagtalon.

## 5. Baguhin ang estilo — at masdan ang pagbabago ng source

Piliin ang tagline (`p.tagline`) — piliin ito sa pahina o pindutin ito sa
tree — at pindutin ang **I-edit ang Style…**. Sa dialog piliin ang property
`color`, i-type ang halaga `tomato`, at pindutin ang OK.

Dalawang bagay ang nangyayari, ayon sa pagkakasunod:

1. **Agad na muling ipininta ang pahina.** Inilalapat muna inline ang
   pagbabago, kaya laging nakikita mo ang iyong hiniling.
2. **Nagbabago ang source stylesheet.** Iniuulat ng status bar ang
   `Na-save sa style.css (.tagline)` — buksan ang `style.css` at ang
   `color: gray;` ay naging `color: tomato;`, sa kinalalagyan, at hindi
   nagalaw ang bawat ibang byte.

Pinipili ang panuntunang babaguhin sa pagtatanong sa *pahina* kung aling
mga panuntunan ng stylesheet ang tumugma sa elemento — ang sagot ng
cascade mismo, nananaig ang huling tugma — kaya dumadapo ang pagsulat sa
panuntunang tunay na nagbibigay-estilo sa iyong nakikita, kahit dalawang
beses na lumitaw ang parehong selector sa isang file.

## 6. Ang tapat na mga hangganan

Tumatanggi ang I-edit ang Style…, may dahilan sa status bar, tuwing ang
pagsulat ay panghuhula o sisira ng gawa. Nananatiling inilalapat ang
inline preview sa bawat kaso — nakikita mo ang pagbabago; sinasabi ng
mensahe kung bakit hindi ito na-save.

| Kalagayan | Kung ano ang sinasabi nito |
|-----------|--------------|
| Nasa inline na blokeng `<style>` ang panuntunan | “nasa inline `<style>` ang panuntunan, hindi sa isang stylesheet file” |
| Malayo ang stylesheet o mula sa hindi kilalang server | “hindi inihahain … mula sa isang proyekto rito” |
| May kapatid na `.scss`/`.less`/`.sass` ang `.css` | “Compiled output … — i-edit na lang ang source ng preprocessor” (mawawala ang pagsulat dito sa susunod na compile) |
| May hindi pa naka-save na pagbabago ang file sa isang editor | “may hindi pa naka-save na pagbabago sa editor … — i-save muna ito” |
| Walang anumang panuntunan sa stylesheet na tumutugma sa elemento | “Inilapat sa pahina lamang — walang panuntunan sa stylesheet na tugma sa elementong ito” |

## 7. Isara ang loop

Kung inihahain ng isang kagamitan ng rack ang pahina, hindi na kailangan
mag-reload: binabantayan ng save-to-reload ng Browser ang pag-save ng mga
web file at kusang sinasariwa ang mga lokal na pahina. Pumili → baguhin →
na-update ang source → muling nag-load ang pahina mula sa source na iyon.
Ang browser at ang editor, iisang ibabaw.
