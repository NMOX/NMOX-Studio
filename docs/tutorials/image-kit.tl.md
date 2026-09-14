# Tutorial: Image Kit (Web) — pigain ang iyong mga larawan

<!-- languages -->
[English](image-kit.md) · [Español](image-kit.es.md) · [Français](image-kit.fr.md) · [Deutsch](image-kit.de.md) · [Русский](image-kit.ru.md) · [Українська](image-kit.uk.md) · [Polski](image-kit.pl.md) · [Português (Brasil)](image-kit.pt.md) · [Bahasa Indonesia](image-kit.id.md) · **Filipino** · [Tiếng Việt](image-kit.vi.md) · [简体中文](image-kit.zh.md) · [हिन्दी](image-kit.hi.md) · [עברית](image-kit.he.md) · [العربية](image-kit.ar.md)
<!-- /languages -->

Karaniwan, ang mga larawan ang pinakabigat na bagay na ipinapadala ng isang
site. Hinahanap ng Image Kit ang mga JPEG at PNG ng iyong proyekto at
pinipiga ang mga ito para sa web: mas maliit na `.min.jpg` sa tabi sa
pamamagitan ng re-encoding sa purong Java (walang kailangang i-install),
opsyonal na pagpapaliit ng sukat, at `.webp` sa tabi sa pamamagitan ng
sarili mong `cwebp` kapag nakainstall. Sa buhay na pagsubok para sa
bersyong ito, ang 17.8 MB na wallpaper ay naging 347 KB na `.min.jpg` at
342 KB na `.webp` — 98% mas maliit.

## Ang mga batas na tinutupad nito

- **Hindi kailanman hinahawakan ang mga orihinal.** Nasa tabi ang mga
  output (`photo.min.jpg`, `photo.webp`), at ang output na mayroon na ay
  nilalaktawan at sinasabi — hindi kailanman pinapatungan.
- **Itinatapon ang “optimization” na walang naiimbak**: ang pagpiga na
  nakabawi ng mas mababa sa 10% ay binubura at iniuulat bilang *already
  tight*, sa halip na magpadala ng mas malaking “optimized” na file.
  (Iniingatan ang output na pinaliit ang sukat anuman ang resulta — ang
  mas kaunting pixel ay ang layunin.)
- **Tahasang walang PNG re-encoding.** Hindi matalo ng ImageIO ang tunay
  na PNG optimizer, kaya para sa PNG, ang tapat na pakinabang ay ang WebP
  sa tabi.

## Mga hakbang

1. **Itutok ang IDE sa isang proyekto** at piliin ang
   **Talaksan ▸ Idagdag sa Proyekto ▸ Image Kit (Web)…**. Sinasabi ng
   dialog kung ilang larawan ang nahanap at ang kabuuang bigat (nilalaktawan
   ang node_modules at ang mga build output, pati ang sarili nitong mga
   output na `.min.` — ang pagpiga sa napiga ay magpapatong ng pagkawala).

2. **Piliin ang iyong pagpiga.** Kalidad ng JPEG (85 walang nakikitang
   pagkawala / 80 default para sa web / 70 agresibo), opsyonal na
   pinakamalapad na sukat (2560 retina hero / 1600 nilalaman / 800
   thumbnail), at — kung nasa iyong PATH ang `cwebp` — WebP sa tabi. Kung
   hindi, sinasabi ito ng checkbox at kung saan kukunin
   (`brew install webp`); sinisiyasat ito pati ng Doktor ng Environment.

3. **Basahin ang ulat.** Bawat file: kung ano ang isinulat, sukat bago →
   pagkatapos, o ang tapat na dahilan kung bakit walang isinulat (“already
   exists”, “already tight”). Nasa itaas ang kabuuang byte na naimbak,
   kasama ang handang-kopyahing snippet na `<picture>` na naghahain ng WebP
   kung sinusuportahan at bumabalik sa orihinal kung hindi.

## Ang iyong natutunan

- Optimization ng larawan para sa web nang walang kailangang kasangkapan —
  at ang sarili mong `cwebp` kapag mayroon.
- Ang mga batas ng pamilya ng kit, hindi kailanman pagpatong at tapat na
  ulat, ay umiiral pati sa mga pixel.
