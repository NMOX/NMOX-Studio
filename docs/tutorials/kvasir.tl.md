# Tutorial: KVASIR — ang AI na nagpapaliwanag ng error

<!-- languages -->
[English](kvasir.md) · [Español](kvasir.es.md) · [Français](kvasir.fr.md) · [Deutsch](kvasir.de.md) · [Русский](kvasir.ru.md) · [Українська](kvasir.uk.md) · [Polski](kvasir.pl.md) · [Português (Brasil)](kvasir.pt.md) · [Bahasa Indonesia](kvasir.id.md) · **Filipino** · [Tiếng Việt](kvasir.vi.md) · [简体中文](kvasir.zh.md) · [हिन्दी](kvasir.hi.md) · [עברית](kvasir.he.md) · [العربية](kvasir.ar.md)
<!-- /languages -->

Ang KVASIR ay device sa rack na binabasa ang iyong huling nabigong
pagtakbo at tinatanong ang iyong AI — Claude, ChatGPT o Gemini — kung ano
ang nagkamali. Tulong ng AI ayon sa talinghaga ng rack: iisang pindutan,
malinaw na tarangkahan ng pahintulot, at tapat na LCD — walang file ng
proyekto o lihim na ipinapadala, tanging ang may hangganang konteksto ng
pagkabigo.

![KVASIR na nagpapaliwanag ng tunay na nabigong pagtakbo: ang diyagnosis na dumaan sa pahintulot sa faceplate, at ang buong hakbang ng pag-aayos sa viewer](../images/kvasir-explain.png)

## Bago magsimula

Kailangan ng API key mula sa isa sa tatlong provider na sinasalita ng
KVASIR: Anthropic (Claude), OpenAI (ChatGPT) o Google (Gemini). Pindutin ang
**KEY…** sa faceplate para piliin ang provider at iimbak ang key nito sa
keychain ng OS, o i-export ang environment variable ng provider —
`ANTHROPIC_API_KEY` / `CLAUDE_API_KEY`, `OPENAI_API_KEY` /
`CHATGPT_API_KEY`, o `GEMINI_API_KEY` / `GOOGLE_API_KEY`. Ang piniling
provider ay para sa bawat mukha ng KVASIR, at nasa
Mga Pagpipilian ▸ Rack at Cloud pati.

## Mga hakbang

1. **Magdulot ng pagkabigo.** Magpatakbo ng bagay na nabibigo — build na
   may syntax error, test na naghahagis ng exception. Itinatala ng flight
   recorder ng rack ang command, exit code, at hanggang limang halimbawang
   linya ng error.

2. **Ilagay ang KVASIR** mula sa palette (kategoryang OBSERVE) at pindutin
   ang **EXPLAIN**.

3. **Magbigay ng pahintulot (unang pagkakataon).** May sarili nitong
   minsanang dialog ng pahintulot ang KVASIR, kada provider, na
   pinapangalanan ang vendor na tatanggap ng data at inilalahad nang
   eksakto kung ano ang umaalis sa iyong makina: ang nabigong command, ang
   exit code nito, ≤5 linya ng error, ang pangalan ng device, at ang
   pangalan ng proyekto — at walang iba (walang source, walang environment,
   walang lihim). Binabantayan ng Tiwala sa Workspace ang *pagpapatakbo* ng
   code; may sarili nitong tarangkahan ang palabas na daloy ng data na ito.

4. **Basahin ang hatol.** Lumilitaw ang maikling diyagnosis sa LCD na may
   maraming linya; pindutin ang **VIEW** para buksan ang buong paliwanag
   sa window ng usapan. Pumipili ang knob
   na **MODEL** ng FAST (default) o DEEP — Haiku / Sonnet, GPT-5 mini /
   GPT-5, o Gemini Flash / Pro, depende sa piniling provider.

## Ang iyong natutunan

- Walang gastos ang KVASIR sa boot at walang network call kung walang
  pindot — ipinapatupad ang tarangkahan ng key at ang tarangkahan ng
  pahintulot.
- Sumasakay ang key sa auth header ng provider lamang (`x-api-key`,
  `Authorization: Bearer`, `x-goog-api-key`) — hindi kailanman sa URL,
  body, o log.
- Hindi tumatawid ang mga key sa pagitan ng provider, at kada provider ang
  pahintulot: ang pagpayag para sa Anthropic ay hindi pagpayag para sa
  Google o OpenAI.
- Tapat ang pagkukulang: walang key, walang pahintulot, walang
  ipapaliwanag, offline, at pagtanggi — may malinaw na mensahe sa LCD ang
  bawat isa.

## Susunod

- Ikabit ito nang walang kamay: kusang ipinapaliwanag ng cable na
  `VERITAS FAIL → KVASIR EXPLAIN` ang nabigong pagtakbo ng test (hindi
  kailanman nagtatanong ang landas ng cable, at nililimitahan sa bawat 30s);
  dinadala ng OUT nito ang resulta sa MONITOR/PHOSPHOR.
