# Ang Agent Port (MCP)

<!-- languages -->
[English](agent-port.md) · [Español](agent-port.es.md) · [Français](agent-port.fr.md) · [Deutsch](agent-port.de.md) · [Русский](agent-port.ru.md) · [Українська](agent-port.uk.md) · [Polski](agent-port.pl.md) · [Português (Brasil)](agent-port.pt.md) · [Bahasa Indonesia](agent-port.id.md) · **Filipino** · [Tiếng Việt](agent-port.vi.md) · [简体中文](agent-port.zh.md) · [हिन्दी](agent-port.hi.md) · [עברית](agent-port.he.md) · [العربية](agent-port.ar.md)
<!-- /languages -->

*Itutok ang isang AI agent sa iyong IDE — at hayaan itong BUMASA, hindi kailanman magpatakbo.*

![Ang dialog ng Agent Port — ang loopback endpoint, ang token kada pagsisimula (isang placeholder sa kuhang ito), at ang handang config ng client na kokopyahin](../images/tabs/agent-port.png)

May kasamang Model Context Protocol server ang NMOX Studio. Anumang agent
na nagsasalita ng MCP (Claude Code, isang katulong sa editor, ang iyong
sariling script) ay maaaring kumonekta rito at tanungin ang IDE tungkol sa
kung ano ang kilala nito: aling proyekto ang nakatutok, ano ang naghahain,
ano ang tumatakbo, ano ang iyong ini-edit, saan idineklara ang isang
pangalan, ano ang huling bumagsak. Ito ay **basahin-lamang ayon sa
pagkakagawa**: bumabagsak ang build kung ang anumang class sa package ng
Agent Port ay magpangalan mismo ng paraan para magsimula ng proseso,
sumulat ng file, o magpahinto ng run.

## 1. Simulan ito

**Gawin:** Kasangkapan ▸ **Agent Port (MCP)…** ▸ **Simulan**, saka **Kopyahin ang Config**.

**Makikita:** Isang dialog na may endpoint (loopback lamang, bagong port),
isang bearer token kada pagsisimula, at isang handang configuration ng client:

```json
{
  "mcpServers": {
    "nmox-studio": {
      "type": "http",
      "url": "http://127.0.0.1:PORT/mcp",
      "headers": { "Authorization": "Bearer TOKEN" }
    }
  }
}
```

Idikit ito sa `.mcp.json` ng iyong agent. Umiiral ang token sa dialog na
iyon lamang — hindi kailanman itinatala o iniimbak — at namamatay kasama ang
port. Tinatapos ito ng **Ihinto ang Agent Port**; gayon din ang pagsasara ng
IDE. Habang nakikinig, ipinapakita ng status line ang **⌁ agent port :N** —
ang port na makakabasa sa iyong IDE ay hindi kailanman di-nakikita; binibilang
ng tooltip ng chip ang mga agent na nag-stream, at muling binubuksan ng isang
pindot ang dialog (config, o Ihinto).

## 2. Ang mga tool

Sumasagot ang bawat tool ng tekstong pantao AT ng may-uring
`structuredContent` sa ilalim ng idineklarang `outputSchema` (sinusuri ng build
ang schema laban sa tunay na output), at may anotasyong `readOnlyHint: true`.

| Tool | Kung ano ang sinasagot nito | Mga argumento |
|------|-----------------|-----------|
| `ide_context` | Ang buong snapshot para sa oryentasyon sa iisang tawag: proyekto, toolchain, mga server, mga run, ang file na ini-edit, ang huling pagkabigo, bilang ng mga diagnostic | — |
| `project_state` | Ang nakatutok na proyekto: pangalan, direktoryo, git branch, natukoy na uri, Node package manager | — |
| `run_history` | Ang mga paglulunsad at paglabas mula sa flight recorder, pinakabago muna, bawat paglabas may command, code at tagal; ang run na ihininto mo mismo ay mababasang `stopped`, hindi kailanman `failed` | `limit` |
| `live_servers` | Bawat dev server na kilala ng IDE na naghahain, kasama ang URL nito | — |
| `live_runs` | Bawat command na tumatakbo ngayon (ang ihihinto ng ■ sa toolbar), kasama kung kailan nagsimula | — |
| `last_failure` | Ang pinakahuling bumagsak na run: kagamitan, command, exit code, hanggang limang linya ng error | — |
| `diagnostics` | Ang kasalukuyang iniuulat ng mga linter at checker | `file` (substring na salaan) |
| `find_symbol` | Kung saan idineklara ang isang pangalan — ang parehong index ng Pumunta sa simbolo (⌥⇧⌘O) | `query`, `limit` |
| `outline` | Ang istruktura ng iisang file — ang mga item ng Navigator mismo | `file` |
| `search_text` | Mga linyang may literal, hindi sensitibo sa malalaking titik, may hangganan at iniuulat ang bawat takda; hindi kailanman hinahanapan ang mga file na `.env`, ang mga rc file ng package manager, at ang mga private key | `query`, `limit` |
| `editor_state` | Ang file na ini-edit (ang naka-focus na tab ng editor, kung hindi ay ang nakikita sa lugar ng editor) at ang bawat bukás na tab, may marka ang hindi naka-save | — |
| `rack_devices` | Ang mga kagamitang naka-mount sa rack ng gawain, ayon sa pagkakasunod | — |

May hangganan ang bawat listahan at sinasabi ito: iniuulat ng `find_symbol` at
`outline` ang bahagyang index, iniuulat ng `search_text` ang `truncated` lamang
kapag may karagdagang tugma, iniuulat ng `run_history` kapag iniwan ang mas
lumang mga pangyayari.

## 3. Mga resource, prompt, at ang stream

Nabasa rin ang parehong mga sagot bilang mga resource na ikinakabit ng agent
bilang konteksto — `nmox://context`, `nmox://project`, `nmox://history`,
`nmox://servers`, `nmox://runs`, `nmox://editor`,
`nmox://last-failure`, `nmox://diagnostics`, `nmox://devices` — dagdag ang
dalawang template para sa mga tool na tumatanggap ng argumento:
`nmox://outline/{file}` at `nmox://search/{query}` (percent-encoded).
Ang teksto ng isang resource ay ang structured JSON ng tool nito, byte kada byte.

Ang agent na mas gusto na sabihan kaysa magtanong muli ay maaaring
**mag-subscribe**: `resources/subscribe` sa alinman sa mga URI na iyon, at ang
GET stream ng port (ang daluyang server-to-client ng Streamable HTTP,
`Accept: text/event-stream`, parehong token, walang `Origin`) ay nagdadala ng
frame na `notifications/resources/updated` sa sandaling magbago ang bagay sa
likod nito — nagsisimula ang isang run at inaanunsyo ang `nmox://runs`, nag-live
ang isang server at gayon ang `nmox://servers`, nag-ulat ang isang linter at
gayon ang `nmox://diagnostics`, nagbago ang tab o na-save ang file at gayon ang
`nmox://editor`; sinusundan ng `nmox://context` ang lahat ng mga ito.
Pinapangalanan ng frame ang URI at wala nang iba; muling binabasa ng agent ang
may kinalaman dito. Sinusundan din ng outline na ikinabit ng agent ang file nito:
mag-subscribe sa `nmox://outline/src/app.ts` at inaanunsyo ng port ang URI na
iyon kapag nagbago ang file sa disk (isang save, isang format, isang generator),
at minsan muli kung nawala ito — isang regular na file sa loob ng nakatutok na
proyekto, hanggang tatlumpu’t dalawa, sinusuri bawat dalawang segundo; ang
landas sa labas ng proyekto ay `-32002`, hindi kailanman binabasa.

Tatlong prompt ang nagtutupi ng buhay na kalagayan sa isang tanong:
`diagnose_failure` (ang huling pagkabigo), `review_setup` (ang buong konteksto),
at `where_is` — ang iisang tumatanggap ng argumento, `name` — na nagtutupi ng
mga hit ng simbolo para sa pangalang iyon.

Ang agent na pinupunan ang argumentong iyon, o ang `{file}` ng template ng
outline, ay maaaring magtanong muna: sinasagot ng `completion/complete` (ang
ikaapat na primitive ng spec) ang `name` ng `where_is` mula sa index ng simbolo
(ang parehong mga hit na ibinabalik ng `find_symbol`, walang doble, mga hit sa
unlapi muna) at ang `{file}` mula sa sariling mga file ng proyekto (mga hit sa
unlapi, saka ang naglalaman; nalalapat ang listahan ng nilalaktawan ng search
walk, kaya hindi kailanman nakukumpleto ang `node_modules`) — hanggang 100 halaga,
`hasMore` kapag pinutol ng takda, at `total` lamang kapag eksakto ang bilang
(laging eksakto ang listahan ng file; lampas sa takda ay sahig ang sinasagot ng
index ng simbolo, kaya walang bilang na ibinibigay sa halip ng maling bilang).
Anumang bagay ang literal ng template ng search, kaya walang nakukumpleto rito;
ang hindi kilalang pangalan ng prompt, template o argumento ay tinatanggihan
bilang `-32602`.

Nagdadala ang parehong stream ng **mga log message**: bawat linyang
ipinapi-print ng bawat run ay dumarating bilang `notifications/message` na may
run bilang `logger` nito — ang lifecycle sa `info` (`$ npm run build`, `[exit 0]`,
`[exit 143] stopped`; ang bumagsak na paglabas sa `error`), stderr sa `warning`,
karaniwang output sa `debug`. Nagsisimula ang antas sa `info`, kaya naririnig
ng agent ang pagsisimula at pagtatapos ng mga run at wala nang iba hanggang
magtanong: binubuksan ng `logging/setLevel` na may `debug` ang buong agos. Ang
build na nagpi-print nang mas bilis kaysa pagbasa ng client ay hindi kailanman
nagpapalaki sa memorya ng port — lampas sa isang libong hindi naisulat na linya,
binibilang ang umapaw at inaanunsyo bilang iisang linyang `warning`, hindi
kailanman tahimik na nawawala. Ang antas na hindi pinapangalanan ng spec ay
tinatanggihan bilang `-32602`.

## 4. Ang lakad, sa kamay

Nasa isang shell variable ang token (hindi kailanman sa command line na
maaaring idikit mo saanman):

```bash
curl -s -X POST "$URL" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"find_symbol","arguments":{"query":"checkout"}}}'
```

**Makikita:** `checkout (function) — src/cart.js:12`, at gayon din sa
`structuredContent.hits[0]`.

Ang stream, sa kamay: buksan ito sa isang shell at mag-subscribe mula sa isa —

```bash
curl -N -s "$URL" -H "Authorization: Bearer $TOKEN" -H "Accept: text/event-stream"
```

```bash
curl -s -X POST "$URL" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"resources/subscribe","params":{"uri":"nmox://runs"}}'
curl -s -X POST "$URL" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"logging/setLevel","params":{"level":"debug"}}'
```

**Makikita:** `: connected`, saka `: keepalive` bawat labinlimang segundo;
pindutin ang ▶ at ipinapi-print ng unang shell ang
`notifications/resources/updated` para sa `nmox://runs` at ang bawat linyang
ipinapi-print ng run bilang `notifications/message` (`$ npm run dev` sa `info`,
ang output sa `debug`); pindutin ang ■ at dumarating ang `[exit 143] stopped`
sa `info`.

Ang parehong lakad gamit ang **opisyal na client**, ang bawat primitive nang
sabay, ay nasa repo: `scripts/agent-port-walk.mjs` (sinasabi ng header nito kung
paano i-install ang `@modelcontextprotocol/sdk` sa isang scratch directory at
kung saan inilalagay ang URL at token — mga shell variable, hindi kailanman
command line). Nagpi-print ito ng iisang linya kada hakbang at nagtatapos sa
WALK CLEAN o sa bilang ng mga sorpresa bilang exit code nito (sa hakbang ng
pagtanggi, ang SAGOT ay binibilang na sorpresa), kaya mababasa ito ng isang CI
job; pindutin ang ▶ at ■ sa IDE habang nakikinig ito at dumarating ang mga log
message.

## 5. Ang mga pagtanggi ay mga tampok

| Kung ano ang gagawin mo | Ang sagot ng port |
|--------|---------------|
| Tumawag nang walang token, o gamit ang lipas na token | `401` — wala nang iba, hindi mismo ang listahan ng tool |
| Tumawag mula sa isang pahina sa browser (anumang `Origin`) | `403` |
| Isang payak na `GET` | `405` — hindi pahina ang port; tanging ang SSE `GET` (na may `Accept: text/event-stream`) ang inihahain, bilang stream ng subscription |
| Mag-subscribe sa `nmox://nonesuch`, o sa outline sa labas ng proyekto | JSON-RPC `-32002` (resource not found) |
| Mag-subscribe sa ika-33 outline | `-32602`, na pinapangalanan ang takda |
| Basahin ang `nmox://nonesuch` | JSON-RPC `-32002` (resource not found) |
| Tanungin ang `where_is` nang walang `name` | `-32602`, na pinapangalanan ang nawawalang argumento |
| Humingi ng file sa labas ng proyekto (`../../.zshrc`) | tumatanggi ang `outline` — *outside the aimed project* — at hindi kailanman binabasa ito |
| Maghanap ng halagang nakatira sa `.env` (o `app.env`), `.npmrc`, `.htpasswd`, `secrets.yaml`, `credentials.json`, o isang `.pem` — o humingi ng outline ng mga ito | wala — hindi kailanman hinahanapan, binibilang, o kinukumpleto ang mga file na iyon, at tinatanggihan ng `outline` ang mga ito ayon sa pangalan; ang batas ng env ng IDE mismo (ang pangalan ng susi, hindi kailanman ang halaga nito) ay nalalapat din sa mga agent |
| Itakda ang antas ng log sa `loud` | `-32602`, na pinapangalanan ang walong antas |
| Hilingin dito na magpatakbo, sumulat, o magpahinto ng anumang bagay | walang tool para rito; pinananatili ito ng ledger test |

Ang huling hanay ay ang disenyo. Ang agent na maaaring magpatakbo ng iyong
server ay maaari ding pahintuin ito, at ang agent na maaaring sumulat ay maaari
ding magtanggal; nananatiling paraan para MAGTANONG ang Agent Port. Kung
magdagdag ang isang bersyon sa hinaharap ng ibabaw ng pagpapatakbo, darating ito
na may sariling disenyo ng pahintulot, gaya ng palabas na daloy ng datos ng
KVASIR.

Tingnan din: ang istasyon 24 ng Kitchen Sink at ang talata tungkol sa Agent
Port sa gabay ng gumagamit.
