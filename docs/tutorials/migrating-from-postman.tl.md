# Tutorial: Paglipat mula sa Postman (at Insomnia, at ang browser)

<!-- languages -->
[English](migrating-from-postman.md) · [Español](migrating-from-postman.es.md) · [Français](migrating-from-postman.fr.md) · [Deutsch](migrating-from-postman.de.md) · [Русский](migrating-from-postman.ru.md) · [Українська](migrating-from-postman.uk.md) · [Polski](migrating-from-postman.pl.md) · [Português (Brasil)](migrating-from-postman.pt.md) · [Bahasa Indonesia](migrating-from-postman.id.md) · **Filipino** · [Tiếng Việt](migrating-from-postman.vi.md) · [简体中文](migrating-from-postman.zh.md) · [हिन्दी](migrating-from-postman.hi.md) · [עברית](migrating-from-postman.he.md) · [العربية](migrating-from-postman.ar.md)
<!-- /languages -->

Binabasa ng Studio ng API ang mga file na mayroon mo na: collection o
environment ng Postman, export ng Insomnia v4 (isinasalin ang estruktura
ng workspace at ang `{{ _.templates }}`), HAR capture mula sa devtools,
curl command, file na `.http`, spec ng OpenAPI.
Dinadala ng gabay na ito ang isang tunay na export ng Postman mula simula
hanggang wakas — at ipinapakita ang iisang bagay na tahasang ginagawa ng
NMOX Studio sa ibang paraan: **dumadapo ang mga lihim sa keychain ng
iyong OS, hindi kailanman sa file na maii-commit.**

![Studio ng API, kung saan dumadapo ang mga import: ang puno ng collection, isang request na naipadala, at ang marka nito sa security header](../images/tl/api-studio.png)

## Bago magsimula

I-export ang iyong collection mula sa Postman: collection ▸ … ▸ Export ▸
**Collection v2.1**. (Tinatanggihan ang export na v1, na sinasabi ang
pag-aayos — i-export muli bilang v2.1.) Hiwalay na ini-export ang mga
environment at ini-import sa **Mag-import… ▸ Postman Environment…** —
pumapasok ang mga payak na halaga, pinagsasama ang mga import na may
parehong pangalan nang hindi pinapatungan ang iyong itinakda na, at ang
mga halagang minarkahan ng Postman bilang *secret* ay nananatili sa labas,
may paalalang tumuturo sa field ng Auth na nasa keychain, dahil nakatira
ang mga environment ng Studio ng API sa `.nmoxapi.json` na maii-commit.

## Mga hakbang

1. **Buksan ang Studio ng API** (⌥⌘8) at pindutin ang **Mag-import… ▸
   Postman Collection…**. Piliin ang iyong na-export na `.json`.

2. **Suriin ang dumating.** Iniingatan ng mga folder ang kanilang
   pagkakakilanlan bilang mga pangalang “Folder / Request”. Ang mga
   `{{variables}}` ng Postman ay ini-import *nang literal* — sila ang
   sarili ng syntax ng Studio ng API — at sumasama ang mga variable ng
   collection sa iyong aktibong environment nang hindi pinapatungan ang
   anumang itinakda mo na. Nagiging `{{id}}` ang mga path variable na `:id`.

3. **Tingnan ang tab na Auth ng request na may bearer token.** *Naroroon*
   ang token — ngunit pumasok sa field ng Auth na nasa keychain, hindi sa
   hanay ng header. Malaya mong i-commit ang `.nmoxapi.json`; hindi rito
   ang lihim. Anumang hindi kayang ilarawan ng import (multipart body,
   script) ay pinapangalanan sa status line, hindi kailanman tahimik na
   sinisira.

4. **I-import ang capture ng browser.** Sa tab na Network ng devtools,
   “Save all as HAR”, saka **Mag-import… ▸ HAR capture…**. Ang iyong
   trapiko ng XHR/fetch lamang ay ini-import (ang mga asset ng pahina ay
   binibilang nang malakas), inaalis ang mga session cookie — credential
   ang nahuling cookie — at ang naitalang `Authorization` ay inililipat sa
   keychain (Bearer/Basic) o inaalis at binibilang (anumang malabo).

5. **Ipadala ang isa.** Pumili ng request na na-import, lutasin ang
   `{{baseUrl}}` sa iyong environment kung kailangan, pindutin ang
   **Ipadala** — at basahin ang marka ng security header sa tab na
   Mga Pamantayan habang naroroon.

6. **Baligtarin ang direksyon.** Isinusulat ng
   **Mag-import… ▸ I-export ang koleksyon sa .http…** ang buong collection
   sa dialect ng REST Client para sa anumang editor o CI runner. Tahasang
   hindi nasa file ang auth; may komentong nagsasabi kung ano ang idagdag
   muli ang bawat request na may auth.

## Ang iyong natutunan

- Iisang menu ang paglipat: curl / `.http` / OpenAPI / Postman / HAR
  papasok, `.http` palabas.
- Tinutupad ang batas ng mga lihim sa bawat hangganan: papasok sa keychain,
  nananatili sa keychain.
- Pinapangalanan ang mga pagtanggi, hindi kailanman tahimik — kung may
  hindi na-import, sinasabi ng status line kung ano at kung bakit.
