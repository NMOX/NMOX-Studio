# Ang Ikalawang Linggo

<!-- languages -->
[English](the-second-week.md) · [Español](the-second-week.es.md) · [Français](the-second-week.fr.md) · [Deutsch](the-second-week.de.md) · [Русский](the-second-week.ru.md) · [Українська](the-second-week.uk.md) · [Polski](the-second-week.pl.md) · [Português (Brasil)](the-second-week.pt.md) · [Bahasa Indonesia](the-second-week.id.md) · **Filipino** · [Tiếng Việt](the-second-week.vi.md) · [简体中文](the-second-week.zh.md) · [हिन्दी](the-second-week.hi.md) · [עברית](the-second-week.he.md) · [العربية](the-second-week.ar.md)
<!-- /languages -->

*Mag-commit, magsuri, lumutas, magmungkahi — nang hindi lumilipat sa ibang tool.*

Ang unang oras ay pagbukas ng isang proyekto at pagpapatakbo nito. Ang
ikalawang linggo ay ang lahat ng nakapaligid sa code: dalawampung commit
bawat araw, isang diff na babasahin bago ang bawat isa, isang conflict
pagkatapos ng pull, isang pull request pagkatapos ng push, isang stack
trace na susundan, isang README na dapat manatiling tapat. Ang lakad na ito
ay isang upuan sa git repository na mayroon ka na, at bawat hakbang ay
bagay na gagawin mo ulit bukas.

## 1. Gawing editor ng git ang NMOX Studio

**Gawin:** Pangkat ▸ **Gamitin ang NMOX Studio sa Git…**

**Makikita:** Ang anim na global na setting ng git na ginagawang editor,
difftool, at mergetool ng git ang NMOX Studio, bawat isa katabi ng halaga
nito **ngayon**, para walang napapalitan nang hindi mo nakikita.
**Ilapat** ang nagtatakda sa mga ito (**Isara** ang default na button,
dahil isinusulat nito ang iyong global na configuration ng git);
**Kopyahin ang mga Command** naman ang naglalagay ng mga linyang
`git config` sa clipboard. Kapag gumagamit na ng NMOX Studio ang git,
sinasabi ito ng dialog at hindi na nag-aalok ng Ilapat.

Ang parehong mga linya, kung mas gusto mo ang terminal:

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
git config --global merge.tool nmox
git config --global mergetool.nmox.cmd 'nmox -w "$MERGED"'
git config --global mergetool.nmox.trustExitCode false
```

## 2. Mag-commit

**Gawin:** Baguhin ang isang file, saka sa isang terminal:

```bash
git commit -a
```

**Makikita:** Nagbubukas sa NMOX Studio ang commit message, at sinasabi ng
status line na may terminal na naghihintay rito. Mga comment ang mga
linyang `#` ng git; ang isinusulat mo lamang ang sinusuri ang spelling;
ang summary line na lampas sa 72 character, kung saan ito pinuputol ng
mga tool mismo ng git, ay nagkakaroon ng babala lampas sa ika-72. I-save,
isara ang tab, at pumapasok ang commit — naghihintay ang terminal hanggang
gawin mo iyon. Ang pag-quit sa IDE habang bukas pa ang message ay
nagbabalik din nito sa git, kasama ang anumang na-save.

Binubuksan ng `git rebase -i` ang listahan nito sa parehong paraan: may
highlight ang bawat command at commit, at inaalis ng **I-toggle ang
comment** ang isang linya nang hindi ito binubura.

## 3. Alamin kung nasaan ka

**Makikita:** Ang **tandang ⎇** sa status line — ang `⎇ main ±3 ↑2 ↓1` ay
ang iyong branch, tatlong binagong file, dalawang commit na ipu-push at
isang ipu-pull (lumalabas lamang ang mga arrow kapag may ipu-push o
ipu-pull). Nagsisimula ang menu nito sa **Lumipat ng Branch…**, **I-commit…**, **I-pull…** at
**I-push…**.

**Gawin:** Ilagay ang caret sa anumang linya ng isang sinusubaybayang file.

**Makikita:** Katabi ng tanda, kung sino ang huling nagbago ng linyang iyon,
gaano katagal na, at bakit:
`Ada Lovelace, 3 araw na ang nakalipas · Fix the parser`. Sinasabi ng
isang linyang hindi mo pa naicommit ang ganoon, at sinasabi iyon ng isang
file na may hindi pa na-save na pagbabago sa halip na pangalanan ang
maling may-akda. I-click ang tala para sa mga anotasyon ng buong file;
pinapatay ito ng **Tingnan ▸ May-akda ng linya**.

## 4. Magsuri ng diff

**Gawin:**

```bash
git difftool
```

**Makikita:** Bawat binagong file na magkatabi sa diff view ng NMOX Studio,
na may **Nakaraang Pagkakaiba / Susunod na Pagkakaiba** at
“Pagkakaiba 2 ng 5” sa itaas. Ipinapakita ng idinagdag o binurang file ang
nawawala nitong panig bilang walang-lamang pane (“walang file”);
ipinapakita ang binary na file bilang binary, at sinasabi ng bar kung
magkaiba ang dalawang binary. Isara ang tab at lilipat ang git sa susunod
na file.

## 5. Lutasin ang isang conflict

**Gawin:** I-merge ang isang branch na nagkakaroon ng conflict, saka:

```bash
git mergetool
```

**Makikita:** Ang file na may conflict sa editor, may kulay ang kasalukuyan
at papasok na panig, at may babala sa bawat linyang `<<<<<<<`. Ilagay ang
caret doon at pindutin ang ⌘. (Alt+Enter sa iba), o gamitin ang
**Pinagmulan ▸ Ayusin ang code…**: **Tanggapin ang kasalukuyang
pagbabago**, **Tanggapin ang papasok na pagbabago**, o **Tanggapin ang
parehong pagbabago**, bawat isa ay iisang edit na maaaring i-undo. Ang
block na nagbago mula nang ialok ito ay tinatanggihan sa halip na hulaan.
I-save, isara ang tab, at sagutin ang git.

## 6. Imungkahi ito

**Gawin:** Mag-push, saka Pangkat ▸ **Bagong Pull Request sa GitHub**
(nasa menu rin ng tanda).

**Makikita:** Ang mismong pahinang New Pull Request ng GitHub para sa iyong
branch, sa sarili mong browser, kung saan ka naka-sign in. Mula sa isang
editor, ibinibigay ng **Baguhin ▸ Buksan sa GitHub** at **Kopyahin ang
GitHub link** ang linya o mga linyang kinaroroonan mo; sa puno ng Studio
ng Proyekto, isang file o folder ang ibinibigay nila.

## 7. Habulin ang isang pagbagsak

**Gawin:** Patakbuhin ang iyong mga test sa Terminal (⌃\`) hanggang may
bumagsak.

**Makikita:** Ang isang lokasyon sa output — `src/app.ts:42:7`, isang stack
frame na `(/abs/app.js:10:5)`, `--> src/main.rs:3:5`,
`File "x.py", line 12` — ay nagbubukas sa linya at column na iyon sa
⌘-click (Ctrl-click sa Windows at Linux). Hindi kailanman link ang isang
URL o ang `localhost:3000`, at ang path na wala roon ay tinatanggihan sa
pangalan nito sa halip na hulaan.

## 8. Panatilihing tapat ang README

**Gawin:** Kasangkapan ▸ **Suriin ang mga Link ng Markdown…**

**Makikita:** Bawat relative na link at larawan sa Markdown ng proyekto na
sinuri sa paraan ng pagpapakita ng GitHub — dapat umiiral ang file, at
dapat ay heading ng file na iyon ang isang `#heading`. Error ang patay na
link, babala ang nawawalang heading, parehong bilang squiggle at sa Mga
aksyon, na may isang pangungusap sa status line. Walang lumalabas sa iyong
makina: hindi sinusuri ang link na may scheme.

## 9. Ipasa ito sa isang agent

**Gawin:** Kasangkapan ▸ **Agent Port (MCP)…**, i-tsek ang **Panatilihin
ang address at token na ito**, saka **Kopyahin para sa Claude Code**, at
patakbuhin nang isang beses ang kinopyang linya.

**Makikita:** Isang agent na nakakabasa ng alam ng IDE — ang nakatutok na
proyekto, ang naghahain at tumatakbo, ang ini-edit mo, ang huling pagbagsak
— at nakakakonekta pa rin bukas, dahil nakatago ang token sa keychain ng
iyong system at muling ginagamit ang port. Nananatiling basahin-lamang ang
port ayon sa pagkakagawa. Ang pag-alis ng tsek sa **Panatilihin ang
address at token na ito** ay nagbubura ng entry sa keychain.

## Ang ginawa mo

Sumulat ka ng commit message, nagbasa ng diff, lumutas ng conflict,
nagbukas ng pull request, inalam kung sino ang sumulat ng isang linya,
sinundan ang isang stack trace, at sinuri ang isang README — lahat sa
window na kinaroroonan mo na. Wala sa mga ito ang pumalit sa git: bawat
hakbang ay sariling hakbang ng git, binuksan kung saan ka nagtatrabaho.
