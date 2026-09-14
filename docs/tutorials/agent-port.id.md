# Agent Port (MCP)

<!-- languages -->
[English](agent-port.md) · [Español](agent-port.es.md) · [Français](agent-port.fr.md) · [Deutsch](agent-port.de.md) · [Русский](agent-port.ru.md) · [Українська](agent-port.uk.md) · [Polski](agent-port.pl.md) · [Português (Brasil)](agent-port.pt.md) · **Bahasa Indonesia** · [Filipino](agent-port.tl.md) · [Tiếng Việt](agent-port.vi.md) · [简体中文](agent-port.zh.md) · [हिन्दी](agent-port.hi.md) · [עברית](agent-port.he.md) · [العربية](agent-port.ar.md)
<!-- /languages -->

*Arahkan agen AI ke IDE Anda — dan biarkan ia MEMBACA, tidak pernah
menjalankan.*

![Dialog Agent Port — titik akhir loopback, token per penyalaan (placeholder di bidikan ini), dan konfigurasi klien siap salin](../images/tabs/agent-port.png)

NMOX Studio membawa server Model Context Protocol. Agen apa pun yang
berbicara MCP (Claude Code, asisten penyunting, skrip Anda sendiri)
bisa tersambung dan menanyakan apa yang diketahui IDE: proyek mana yang
dituju, apa yang sedang disajikan, apa yang berjalan, apa yang Anda
sunting, di mana sebuah nama dideklarasikan, apa yang terakhir gagal.
Ia **hanya-baca menurut rancangannya**: build gagal bila ada kelas di
paket Agent Port yang sekadar menyebut cara meluncurkan proses,
menulis berkas, atau menghentikan sebuah jalannya.

## 1. Nyalakan

**Lakukan:** Alat ▸ **Agent Port (MCP)…** (memilihnya langsung menyalakan
porta), lalu **Salin Konfigurasi**.

**Lihat:** Sebuah dialog dengan titik akhirnya (hanya loopback, porta
baru), token bearer per penyalaan, dan konfigurasi klien yang siap
pakai:

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

Tempelkan ke `.mcp.json` agen Anda. Tokennya hanya ada di dialog itu —
tidak pernah dicatat atau disimpan — dan mati bersama portanya.
**Hentikan Agent Port** mengakhirinya; begitu pula menutup IDE. Selama
ia mendengarkan, baris status menampilkan **⌁ agent port :N** — porta
yang bisa membaca IDE Anda tidak pernah tersembunyi; keterangan tanda
itu menghitung agen yang sedang mengalirkan data, dan sekali klik
membuka lagi dialognya (konfigurasi, atau Hentikan).

## 2. Alat-alatnya

Setiap alat menjawab dengan teks untuk manusia DAN `structuredContent`
bertipe di bawah `outputSchema` yang dinyatakan (skemanya divalidasi
terhadap keluaran sungguhan oleh build), dan dianotasi
`readOnlyHint: true`.

| Alat | Apa yang dijawabnya | Argumen |
|------|-----------------|-----------|
| `ide_context` | Seluruh gambaran orientasi dalam satu panggilan: proyek, perkakas, server, jalannya, berkas yang disunting, kegagalan terakhir, jumlah diagnostik | — |
| `project_state` | Proyek yang dituju: nama, direktori, cabang git, jenis yang terdeteksi, manajer paket Node | — |
| `run_history` | Peluncuran dan keluaran dari perekam penerbangan, terbaru lebih dulu, setiap keluaran dengan perintah, kode, dan durasinya; jalannya yang Anda hentikan sendiri terbaca `stopped`, tidak pernah `failed` | `limit` |
| `live_servers` | Setiap server pengembangan yang diketahui IDE sedang menyajikan, dengan URL-nya | — |
| `live_runs` | Setiap perintah yang sedang berjalan saat ini (yang akan dihentikan ■ di bilah alat), dengan waktu mulainya | — |
| `last_failure` | Jalannya gagal yang paling baru: perangkat, perintah, kode keluar, sampai lima baris galat | — |
| `diagnostics` | Apa yang saat ini dilaporkan linter dan pemeriksa | `file` (penyaring substring) |
| `find_symbol` | Di mana sebuah nama dideklarasikan — indeks yang sama dengan Ke simbol (⌥⇧⌘O) | `query`, `limit` |
| `outline` | Struktur satu berkas — butir-butir milik Navigator sendiri | `file` |
| `search_text` | Baris yang memuat sebuah literal, tanpa membedakan huruf besar-kecil, terbatas dan setiap batasnya dilaporkan; berkas `.env`, berkas rc manajer paket, dan kunci privat tidak pernah dicari | `query`, `limit` |
| `editor_state` | Berkas yang sedang disunting (tab penyunting yang terfokus, atau yang tampil di area penyunting) dan setiap tab yang terbuka, yang belum disimpan ditandai | — |
| `rack_devices` | Perangkat yang terpasang di rak tugas, berurutan | — |

Setiap daftar terbatas dan mengatakannya: `find_symbol` dan `outline`
melaporkan indeks yang parsial, `search_text` melaporkan `truncated`
hanya bila memang ada kecocokan lebih lanjut, `run_history` melaporkan
bila peristiwa yang lebih lama ditinggalkan.

## 3. Sumber daya, prompt, dan aliran

Jawaban yang sama bisa dijelajahi sebagai sumber daya yang dilampirkan
agen sebagai konteks — `nmox://context`, `nmox://project`,
`nmox://history`, `nmox://servers`, `nmox://runs`, `nmox://editor`,
`nmox://last-failure`, `nmox://diagnostics`, `nmox://devices` — ditambah
dua templat untuk alat yang menerima argumen: `nmox://outline/{file}`
dan `nmox://search/{query}` (dengan percent-encoding). Teks sebuah
sumber daya adalah JSON terstruktur milik alatnya, bita demi bita.

Agen yang lebih suka diberi tahu daripada bertanya lagi bisa
**berlangganan**: `resources/subscribe` pada URI mana pun di atas, dan
aliran GET milik porta (kanal server-ke-klien dari Streamable HTTP,
`Accept: text/event-stream`, token yang sama, tanpa `Origin`) membawa
bingkai `notifications/resources/updated` saat hal di baliknya berubah
— sebuah jalannya dimulai dan `nmox://runs` diumumkan, sebuah server
menyala dan `nmox://servers` diumumkan, linter melapor dan
`nmox://diagnostics` diumumkan, tab berganti atau berkas disimpan dan
`nmox://editor` diumumkan; `nmox://context` mengikuti semuanya.
Bingkainya menyebut URI dan tidak ada yang lain; agen membaca ulang apa
yang ia pedulikan. Kerangka yang dilampirkan agen juga mengikuti
berkasnya: berlangganan ke `nmox://outline/src/app.ts` dan porta
mengumumkan URI itu ketika berkasnya berubah di disk (disimpan,
diformat, dibangkitkan), sekali lagi bila berkasnya lenyap — berkas
biasa di dalam proyek yang dituju, paling banyak tiga puluh dua, diperiksa
setiap dua detik; jalur di luar proyek adalah `-32002`, tidak pernah
dibaca.

Tiga prompt melipat keadaan hidup ke dalam sebuah pertanyaan:
`diagnose_failure` (kegagalan terakhir), `review_setup` (seluruh
konteks), dan `where_is` — satu-satunya yang menerima argumen, `name` —
yang melipat hasil simbol untuk nama itu.

Agen yang mengisi argumen itu, atau `{file}` pada templat kerangka,
bisa bertanya dulu: `completion/complete` (primitif keempat dalam
spesifikasi) menjawab `name` milik `where_is` dari indeks simbol (hasil
yang sama dengan `find_symbol`, tanpa duplikat, kecocokan awalan lebih
dulu) dan `{file}` dari berkas proyek itu sendiri (kecocokan awalan,
lalu yang memuat; daftar lewati milik penelusuran pencarian berlaku,
jadi `node_modules` tidak pernah dilengkapi) — paling banyak 100 nilai,
`hasMore` bila batasnya memotong, dan `total` hanya bila hitungannya
tepat (daftar berkas selalu tepat; melewati batas, indeks simbol hanya
menjawab batas bawah, jadi tidak ada angka yang diberikan alih-alih
angka yang salah). Literal pada templat pencarian bisa apa saja, jadi ia
tidak dilengkapi apa pun; nama prompt, templat, atau argumen yang tidak
dikenal ditolak sebagai `-32602`.

Aliran yang sama membawa **pesan log**: setiap baris yang dicetak
setiap jalannya tiba sebagai `notifications/message` dengan jalannya
itu sebagai `logger` — siklus hidup pada `info` (`$ npm run build`,
`[exit 0]`, `[exit 143] stopped`; keluaran gagal pada `error`), stderr
pada `warning`, keluaran biasa pada `debug`. Tingkatnya dimulai dari
`info`, jadi agen mendengar jalannya dimulai dan berakhir dan tidak ada
yang lain sampai ia meminta: `logging/setLevel` dengan `debug` membuka
semburannya. Build yang mencetak lebih cepat daripada yang dibaca klien
tidak pernah membengkakkan memori porta — lewat seribu baris yang belum
tertulis, luapannya dihitung dan diumumkan sebagai satu baris
`warning`, tidak pernah hilang diam-diam. Tingkat yang tidak disebut
spesifikasi ditolak sebagai `-32602`.

## 4. Penelusuran, dengan tangan

Dengan token di variabel shell (jangan pernah di baris perintah yang
mungkin Anda tempel di mana saja):

```bash
curl -s -X POST "$URL" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"find_symbol","arguments":{"query":"checkout"}}}'
```

**Lihat:** `checkout (function) — src/cart.js:12`, dan hal yang sama
sebagai `structuredContent.hits[0]`.

Alirannya, dengan tangan: buka di satu shell dan berlangganan dari shell
lain —

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

**Lihat:** `: connected`, lalu `: keepalive` setiap lima belas detik;
tekan ▶ dan shell pertama mencetak `notifications/resources/updated`
untuk `nmox://runs` serta setiap baris yang dicetak jalannya sebagai
`notifications/message` (`$ npm run dev` pada `info`, keluarannya pada
`debug`); tekan ■ dan `[exit 143] stopped` tiba pada `info`.

Penelusuran yang sama dengan **klien resmi**, semua primitif sekaligus,
ada di repositori: `scripts/agent-port-walk.mjs` (kepalanya menjelaskan
cara memasang `@modelcontextprotocol/sdk` di direktori coretan dan ke
mana URL serta tokennya — variabel shell, tidak pernah baris perintah).
Ia mencetak satu baris per langkah dan berakhir dengan WALK CLEAN atau
jumlah kejutan sebagai kode keluarnya (langkah penolakan menghitung
sebuah JAWABAN sebagai kejutan), sehingga job CI bisa membacanya; tekan
▶ dan ■ di IDE selama ia mendengarkan, dan pesan log pun tiba.

## 5. Penolakan adalah fitur

| Anda melakukan | Porta menjawab |
|--------|---------------|
| Memanggil tanpa token, atau dengan token basi | `401` — tidak ada yang lain, bahkan daftar alat pun tidak |
| Memanggil dari halaman di peramban (`Origin` apa pun) | `403` |
| `GET` biasa | `405` — porta ini bukan halaman; hanya `GET` SSE (dengan `Accept: text/event-stream`) yang dilayani, sebagai aliran langganan |
| Berlangganan ke `nmox://nonesuch`, atau ke kerangka di luar proyek | JSON-RPC `-32002` (sumber daya tidak ditemukan) |
| Berlangganan ke kerangka ketiga puluh tiga | `-32602`, menyebut batasnya |
| Membaca `nmox://nonesuch` | JSON-RPC `-32002` (sumber daya tidak ditemukan) |
| Meminta `where_is` tanpa `name` | `-32602`, menyebut argumen yang hilang |
| Meminta berkas di luar proyek (`../../.zshrc`) | `outline` menolak — *outside the aimed project* (di luar proyek yang dituju) — dan tidak pernah membacanya |
| Mencari nilai yang tinggal di `.env` (atau `app.env`), `.npmrc`, `.htpasswd`, `secrets.yaml`, `credentials.json`, atau `.pem` — atau meminta kerangkanya | tidak ada apa-apa — berkas-berkas itu tidak pernah dicari, tidak pernah dihitung, tidak pernah dilengkapi, dan `outline` menolaknya dengan menyebut namanya; hukum env milik IDE sendiri (nama kunci, tidak pernah nilainya) berlaku juga untuk agen |
| Menyetel tingkat log ke `loud` | `-32602`, menyebut delapan tingkatnya |
| Memintanya menjalankan, menulis, atau menghentikan apa pun | tidak ada alat semacam itu; uji buku besar menjaganya tetap begitu |

Baris terakhir itulah rancangannya. Agen yang bisa menjalankan server
Anda juga bisa menghentikannya, dan agen yang bisa menulis juga bisa
menghapus; Agent Port tetap menjadi cara untuk BERTANYA. Bila versi
mendatang menambahkan permukaan eksekusi, ia akan datang dengan
rancangan persetujuannya sendiri, seperti aliran data keluar milik
KVASIR dulu.

Lihat juga: stasiun 24 di Kitchen Sink dan paragraf Agent Port di
panduan pengguna.
