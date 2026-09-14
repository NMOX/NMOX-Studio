# Tutorial: Ipakita ito sa isang silid

<!-- languages -->
[English](show-it-to-a-room.md) · [Español](show-it-to-a-room.es.md) · [Français](show-it-to-a-room.fr.md) · [Deutsch](show-it-to-a-room.de.md) · [Русский](show-it-to-a-room.ru.md) · [Українська](show-it-to-a-room.uk.md) · [Polski](show-it-to-a-room.pl.md) · [Português (Brasil)](show-it-to-a-room.pt.md) · [Bahasa Indonesia](show-it-to-a-room.id.md) · **Filipino** · [Tiếng Việt](show-it-to-a-room.vi.md) · [简体中文](show-it-to-a-room.zh.md) · [हिन्दी](show-it-to-a-room.hi.md) · [עברית](show-it-to-a-room.he.md) · [العربية](show-it-to-a-room.ar.md)
<!-- /languages -->

May mga araw na ang code ay hindi ang produkto — ang *pagpapakita* ay:
isang projector, isang README, isang comment sa isyu, isang slide. May
maliit na kit ng pagtatanghal ang NMOX Studio para sa taong iyon, at ang
bawat bahagi nito ay nakasakay sa isang bagay na mayroon na ang IDE sa
halip na isang idinugtong: ang sariling text zoom ng editor, ang iisang
bokabularyo ng wika na pumapangalan sa mga code fence, ang sariling
pagpipinta ng docs forge. Dinadaanan ng tutorial na ito ang lahat nang
isang upuan, mula sa huling hanay ng upuan hanggang sa clipboard.

![Naka-on ang Mode ng presentasyon: isang template ng Angular at ang window ng Output, parehong +10 pt, eksaktong ibinabalik kapag pinatay ang mode](../images/presentation-mode.png)

![Ang tab ng editor lamang, na-save ng I-save ang Screenshot ng Editor… sa 2x](../images/editor-screenshot-2x.png)

## Bago magsimula

Magbukas ng proyektong nakatira sa isang repositoryo sa GitHub (kailangan
ng link gesture ang `origin` sa GitHub — ang anumang iba ay tinatanggihan
nang malakas, hindi hinuhulaan) at magbukas ng isa sa mga source file nito.
I-save ito: tinatanggihan din ng link gesture ang buffer na may hindi pa
naka-save na pagbabago, dahil ang blokeng hindi tugma sa link nito ay
isang kasinungalingan. Para sa hakbang 2, patakbuhin din ang proyekto (ang
▶ sa toolbar o ang rack) upang may pahina sa Browser sa loob ng app at
output sa window ng Output.

## Mga hakbang

1. **Gawing nababasa ito ng silid.** `Tingnan ▸ Mode ng presentasyon`.
   Nagiging sampung punto mas malaki ang bawat bukás na editor, nang
   buhay, gayon din ang anumang editor na binubuksan mo habang naka-on ang
   mode. Nagpapakita ng tsek ang menu item at pinapangalanan ng status line
   ang paglaki. Walang isinusulat sa iyong mga setting — patayin ito (o
   i-restart) at ang font ay eksaktong kung ano ito dati, kasama ang anumang
   pinong pagsasaayos gamit ang ⌥-wheel na idinagdag mo sa ibabaw.

2. **Masdan ang pagsunod ng natitirang IDE.** Habang naka-on ang mode,
   ang pahina ng Browser sa loob ng app ay naka-zoom sa 150% ng anumang zoom
   na dati mayroon mo, lumalaki ng parehong sampung punto ang teksto ng
   window ng Output, at pinalalaki din ang bawat bukás na Terminal — at
   bawat isa ay ibinabalik sa sariling laki pagkalabas. Isang demo ng
   tumatakbong app, ang output nito, at ang shell na tinitipa mo — lahat ay
   nababasa mula sa huling hanay, hindi ang code lamang.

3. **Ipakita ang iyong mga kamay.** `Tingnan ▸ Ipakita ang mga keystroke`,
   saka pindutin ang `⌘S`. Isang madilim na pill na nagsasabing `⌘S` ang
   lumilitaw nang malaki sa ibaba ng window sandali (ang pag-ulit ay
   `⌘Z ×3`). Ngayon mag-type ng isang salita: walang lumilitaw. Tanging mga
   chord na may ⌘, ⌃ o ⌥ at ang mga function key at Escape ay ipinapakita —
   ang karaniwang pag-type ay hindi kailanman, kaya ang password na tinipa
   sa terminal ay hindi makakarating sa projector.

4. **Ibahagi ang code.** Pumili ng ilang linya at piliin ang `Baguhin ▸ Kopyahin bilang Markdown`
   (o i-right-click sa editor). Idikit sa isang README, isyu, o chat: isang
   fenced block na may tatak ng wika ng file
   (` ```html `, ` ```typescript `, ` ```bash `…), na nagtatapos sa eksaktong
   isang newline, na may mas mahabang fence kung ang snippet mismo ay may
   tatlong backtick upang buong mag-render. Kung walang pinili, kinokopya ang
   buong file. Sinasabi ng status line kung ilang linya at aling tatak.

5. **Sabihin kung saan ito nakatira.** Parehong pinili,
   `Baguhin ▸ Kopyahin bilang Markdown na may link` (o i-right-click). Ang
   idinikit ay ang parehong bloke na sinusundan ng
   `[src/app/app.ts#L3-L12](https://github.com/you/repo/blob/main/src/app/app.ts#L3-L12)`
   — ang branch na naka-check out sa iyo (ang detached HEAD ay nag-link ayon
   sa commit), dahil ang lokal na commit na hindi kailanman na-push ay isang
   404 na nagbabalatkayong permalink. Isang file sa labas ng repositoryo,
   repositoryong walang `origin`, origin na hindi GitHub, o hindi pa naka-save
   na pagbabago: tumatanggi ang status line at walang kinokopya.

6. **Kunin ang larawan.** Ang `Kasangkapan ▸ Kopyahin ang Screenshot ng Editor`
   ay inilalagay ang napiling tab ng lugar ng editor — toolbar, gutter, code,
   mga sidebar, walang chrome ng IDE — sa clipboard bilang 2x na larawan;
   idikit ito tuwiran sa chat o slide. Kinukuha nito ang tab na iyong
   tinitingnan kahit nasa Navigator ang focus, at kung walang bukás sa lugar
   ng editor, sinasabi ito sa halip na kopyahin ang blangko. Ang
   `Kasangkapan ▸ I-save ang Screenshot ng Editor…` ay nag-save ng parehong
   kuha bilang PNG na pinangalanan ayon sa dokumento (`app.ts-<stamp>.png`),
   at ang `Kasangkapan ▸ I-save ang Screenshot…` ay nag-save ng buong window
   ng IDE (`nmox-studio-<stamp>.png`, sa Pictures bilang default). Dahil
   ipininta ng IDE ang sarili nito, walang pahintulot sa screen recording na
   kailangang ibigay, walang desktop sa frame, at walang kailangang i-crop.

7. **Idikit ang tree.** `Kasangkapan ▸ Kopyahin ang Project Tree bilang Markdown`.
   Dumadapo ang layout ng nakatutok na proyekto bilang fenced box-drawing tree
   na ipinapakita ng isang README: mga direktoryo muna, ang `node_modules/ …`
   at ang mga bigat na kapatid nito ay pinapangalanan ngunit hindi kailanman
   pinapasok, ang malalim o napakalaking tree ay may takda at ang natitira ay
   binibilang sa halip na tahimik na inaalis, at iniiwan ang sariling mga file
   na `.nmox*.json` ng IDE dahil pag-aari ng produkto ang mga ito, hindi ng
   proyekto.

8. **Bumaba sa entablado.** `Tingnan ▸ Mode ng presentasyon` muli. Ang mga
   editor, Browser, Output, at ang bawat terminal ay bumabalik sa eksaktong
   dating kalagayan; patayin ang `Tingnan ▸ Ipakita ang mga keystroke`, at
   nawawala ang pill.

## Ang iyong natutunan

- **Ang pagtatanghal ay isang kalagayan, hindi isang setting.** Buhay at
  hindi kailanman iniimbak ang Mode ng presentasyon — ang restart ay pagbalik
  sa karaniwan — at ito ay iisang kalagayan sa buong produkto na pinapalitan
  ng editor at maaaring sundan ng anumang window.
- **Tahasang makitid ang overlay.** Ipinapakita ng Ipakita ang mga keystroke
  ang mga chord at function key lamang; ang iyong tinitipa ay hindi kailanman
  ipinapakita.
- **Ang kopyang hindi makapagpatunay sa sarili ay walang kinokopya.**
  Tinatanggihan ng Kopyahin bilang Markdown na may link ang bawat baitang na
  hindi nito mapatunayan — walang origin, hindi GitHub, hindi naka-save na
  buffer — sa status line, sa halip na magdikit ng link na nagsisinungaling.
- **May hangganan at payak ang bawat pagbabahagi.** Hindi kailanman sumusunod
  ang tree sa symlink, hindi kailanman pumapasok sa bigat na direktoryo, may
  takda sa inililista at binibilang ang natitira; ang screenshot ng editor ay
  isang larawan at larawan lamang.

## Susunod

- Ipakita din ang tumatakbong app mula sa huling hanay: dinadaanan ng
  [Mula Browser tungo sa Source](browser-to-source.tl.md) ang Browser sa loob
  ng app at ang DevTools nito.
- Ang Standup na idinidikit mo sa chat ay mula sa [Ang Task Board at mga
  sprint](task-board.tl.md).
- Ang mga release note para sa isang post ay nagsisimula sa
  `Tulong ▸ Ano ang Bago…`, saka ang pindutang **Kopyahin bilang Markdown**
  nito; ang buong bahagi tungkol sa pagtatanghal ay nasa [Gabay ng
  gumagamit](../user-guide.tl.md).
